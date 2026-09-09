package io.toterra.subterra.engine.pcg;

import io.toterra.subterra.engine.config.Td;
import io.toterra.subterra.engine.config.TdTable;
import io.toterra.subterra.engine.config.TdValue;
import io.toterra.subterra.engine.save.doc.LedgerDoc;
import io.toterra.subterra.engine.save.doc.RelicDoc;
import io.toterra.subterra.engine.datapack.LangDatum;
import io.toterra.subterra.engine.datapack.RecipeDatum;

import java.util.List;
import java.util.Map;

/**
 * p.2.13.3 内容生成器（final 工具类，静态入口）——在 p.2.13.1/2 的确定性基座
 * （{@link PcgSource} / {@link PcgPick} / {@link PcgTextSample}）之上，为五类领域内容
 * 提供确定性生成器：遗物（对齐 {@link RelicDoc} 条目的 count/rarity/seed 字段集）、藏录条目
 * （对齐 {@link LedgerDoc} 条目的 count/rarity/seed 字段集）、配方（对齐 {@link RecipeDatum}
 * 的 smoking 形态）、本地化（对齐 {@link LangDatum} 的 [k,v] 元素形态）、群系变种
 * （自定的 biome/variant 形态）。
 * <p>
 * 确定性判据（每一类都满足）：同一个 {@code (seed, 参数)} 两次独立调用 -> 两次返回的 td 文本
 * 逐字节一致（同 seed -> 同文本）；不同 seed（大致）产生不同文本；无时间戳/随机/时序。内部所有
 * 数值一律由 {@code new PcgSource(seed).fork(salt)} 派生，每个字段一个独立 salt（salt 含参数
 * 字符串，故不同参数 -> 不同派生），全部分布落在写死的固定闭区间内（见各类方法 Javadoc）。
 * 全部字段以 {@link TdTable} 模型构建并经 {@link Td#write} 序列化，输出天然可被 {@link Td#parse}
 * 反解析（round-trip 无损），且模型序固定——同一输入两次 {@link Td#write} 的字节完全一致。
 * <p>
 * 复用纪律：本类只调用 p.2.13.1/2 的既有 {@link PcgSource}{@code fork/nextInt/nextDouble} 与
 * {@link PcgTextSample#generate}，不重复造 PRNG、不复写随机源。纯 JDK、无全局状态、
 * 无 O(n²)（局部字池固定规模 + 线性构建，每字段一次 fork + 一次抽取）。
 * <p>
 * p.2.13.3 content generators (final utility class, static entry) - built on the
 * p.2.13.1/.2 deterministic base ({@link PcgSource} / {@link PcgPick} /
 * {@link PcgTextSample}), this class provides deterministic generators for five domain
 * content shapes: relic (aligned to {@link RelicDoc} entry fields), ledger entry (aligned to
 * {@link LedgerDoc} entry fields), recipe (aligned to {@link RecipeDatum} smoking shape),
 * localization (aligned to {@link LangDatum}'s [k,v] element shape), and biome variant
 * (a bespoke biome/variant shape).
 * <p>
 * <b>Determinism criteria</b> (satisfied by every shape): two independent calls on the same
 * {@code (seed, argument)} -> byte-identical td text (same seed -> same text); distinct seeds
 * (roughly) -> distinct text; no timestamp / random / timing. Internally every numeric field is
 * derived via {@code new PcgSource(seed).fork(salt)} - one dedicated salt per field (salts embed
 * the argument string, so distinct arguments -> distinct derivation) - and every distribution
 * lives in a written-hard fixed closed interval (see each method's Javadoc). All fields are
 * assembled as a {@link TdTable} model and serialised through {@link Td#write}, so the output is
 * natively re-parseable via {@link Td#parse} (lossless round-trip) and the model order is fixed -
 * two {@link Td#write} calls on the same input yield identical bytes.
 * <p>
 * Reuse discipline: this class only calls the existing {@link PcgSource}{@code fork/nextInt/nextDouble}
 * and {@link PcgTextSample#generate} from p.2.13.1/.2 - it never re-invents a PRNG and never
 * re-writes the random source. Pure JDK, no global state, no O(n²) (word pools are fixed-size and
 * each field is one fork + one draw).
 */
public final class PcgContentGen {

    /** 遗物/藏录数量闭区间下界。Lower bound of the count closed interval. */
    private static final long COUNT_MIN = 1L;
    /** 遗物/藏录数量闭区间上界。Upper bound of the count closed interval. */
    private static final long COUNT_MAX = 64L;
    /** 稀有度闭区间下界。Lower bound of the rarity closed interval. */
    private static final long RARITY_MIN = 1L;
    /** 稀有度闭区间上界。Upper bound of the rarity closed interval. */
    private static final long RARITY_MAX = 6L;

    /** 配方烹饪时长闭区间下界。Lower bound of the cooking-time closed interval. */
    private static final long COOK_MIN = 1L;
    /** 配方烹饪时长闭区间上界。Upper bound of the cooking-time closed interval. */
    private static final long COOK_MAX = 1200L;
    /** 经验系数分母（值 = draw/EXP_DIV，落在 [0, 1]，再乘上界）。Experience scale denominator. */
    private static final long EXP_DIV = 400L;

    /** 群系变种高度扰动闭区间上界（下界固定 0）。Upper bound of the height-modify closed interval (lower bound fixed at 0). */
    private static final long HEIGHT_MAX = 16L;

    private PcgContentGen() {
        // utility class; no instantiation / 工具类，禁止实例化
    }

    // ---------- 1) relic 遗物 ----------

    /**
     * 确定性生成一条遗物 td 文本（对齐 {@link RelicDoc} 条目的 count/rarity/seed 字段集，
     * 与 p.2.10 SaveVerifyRuntime 的 relic 形态一致）。返回可被 {@link Td#parse} 解析的裸表
     * （不含文档级头）：
     * <pre>
     * [
     *   id = "&lt;name&gt;",
     *   count = &lt;n&gt;,            // n 在 [1, 64]，seed 派生
     *   rarity = &lt;r&gt;,           // r 在 [1, 6]，seed 派生
     *   seed = "&lt;seedSalt&gt;"   // 确定性派生盐串
     * ]
     * </pre>
     * 确定性判据：同 {@code (seed, name)} 两次调用 -> 字节一致；不同 seed（大致）不同。字段由
     * {@code PcgSource(seed).fork("relic:"+name+":...")} 派生，落在上述固定闭区间。
     * <p>
     * Deterministically generates one relic td document (aligned to {@link RelicDoc}'s entry
     * count/rarity/seed field set, matching p.2.10's relic form) as a bare {@link Td#parse}-able
     * table (no doc-level header): see the shape above, with {@code n} in [1, 64] and {@code r}
     * in [1, 6] derived from the seed. Determinism: same {@code (seed, name)} twice ->
     * byte-identical output; distinct seeds (roughly) differ.
     *
     * @param seed 主种子 / the master seed.
     * @param name 遗物标识 / the relic id.
     * @return 可解析的遗物 td 文本 / a re-parseable relic td document.
     */
    public static String generateRelic(long seed, String name) {
        String safe = name == null ? "" : name;
        PcgSource base = new PcgSource(seed).fork("relic:" + safe);
        long count = COUNT_MIN + range(base, "count", COUNT_MAX - COUNT_MIN + 1);
        long rarity = RARITY_MIN + range(base, "rarity", RARITY_MAX - RARITY_MIN + 1);
        return Td.write(TdTable.builder()
                .put("id", safe)
                .put("count", TdValue.of(count))
                .put("rarity", TdValue.of(rarity))
                .put("seed", seedSalt(base, "seed"))
                .build());
    }

    // ---------- 2) ledger 藏录条目 ----------

    /**
     * 确定性生成一条藏录（台账）条目 td 文本（对齐 {@link LedgerDoc} 条目的 count/rarity/seed
     * 字段集，与 p.2.10 SaveVerifyRuntime 的 ledger 形态一致）。返回可被 {@link Td#parse}
     * 解析的裸表：
     * <pre>
     * [
     *   id = "&lt;key&gt;",
     *   count = &lt;n&gt;,            // n 在 [1, 64]
     *   rarity = &lt;r&gt;,           // r 在 [1, 6]
     *   seed = "&lt;seedSalt&gt;"
     * ]
     * </pre>
     * 确定性判据同 {@link #generateRelic}；派生盐为 {@code "ledger:"+key+":..."}。
     * <p>
     * Deterministically generates one ledger-entry td document (aligned to {@link LedgerDoc}'s
     * entry count/rarity/seed field set, matching p.2.10's ledger form) as a bare
     * {@link Td#parse}-able table with the shape above. Determinism as in {@link #generateRelic};
     * derivation salts are {@code "ledger:"+key+":..."}.
     *
     * @param seed 主种子 / the master seed.
     * @param key  藏录主题键 / the ledger topic key.
     * @return 可解析的藏录条目 td 文本 / a re-parseable ledger-entry td document.
     */
    public static String generateLedgerEntry(long seed, String key) {
        String safe = key == null ? "" : key;
        PcgSource base = new PcgSource(seed).fork("ledger:" + safe);
        long count = COUNT_MIN + range(base, "count", COUNT_MAX - COUNT_MIN + 1);
        long rarity = RARITY_MIN + range(base, "rarity", RARITY_MAX - RARITY_MIN + 1);
        return Td.write(TdTable.builder()
                .put("id", safe)
                .put("count", TdValue.of(count))
                .put("rarity", TdValue.of(rarity))
                .put("seed", seedSalt(base, "seed"))
                .build());
    }

    // ---------- 3) recipe 配方 ----------

    /**
     * 确定性生成一条配方 td 文本（对齐 {@link RecipeDatum} 的 smoking 形态：单材料 + 结果）。
     * 返回可被 {@link Td#parse} 解析的裸表：
     * <pre>
     * [
     *   type = "minecraft:smoking",
     *   ingredient = [ item = "&lt;material&gt;" ],
     *   result = [
     *     item = "&lt;productItem&gt;",
     *     experience = &lt;e&gt;,          // e 在 [0, 4]，seed 派生
     *     cooking_time = &lt;t&gt;         // t 在 [1, 1200]，seed 派生
     *   ]
     * ]
     * </pre>
     * 确定性判据：同 {@code (seed, id)} 两次调用 -> 字节一致；不同 seed（大致）不同。材料/产物由
     * 内部固定候选池经 {@link PcgPick#pick} 抽取；时长/经验由
     * {@code PcgSource(seed).fork("recipe:"+id+":...")} 派生，落在上述固定闭区间。
     * <p>
     * Deterministically generates one recipe td document (aligned to {@link RecipeDatum}'s
     * smoking shape: single ingredient + result) as a bare {@link Td#parse}-able table with the
     * shape above. Determinism: same {@code (seed, id)} twice -> byte-identical output; distinct
     * seeds (roughly) differ. The material/product are picked from internal fixed candidate pools
     * via {@link PcgPick#pick}; cooking time &amp; experience derive from
     * {@code PcgSource(seed).fork("recipe:"+id+":...")}.
     *
     * @param seed 主种子 / the master seed.
     * @param id   配方标识（参与派生盐）/ the recipe id (participates in derivation salts).
     * @return 可解析的配方 td 文本 / a re-parseable recipe td document.
     */
    public static String generateRecipe(long seed, String id) throws PcgException {
        String safe = id == null ? "" : id;
        PcgSource base = new PcgSource(seed).fork("recipe:" + safe);
        List<String> materials = List.of(
                "minecraft:coal", "minecraft:iron_ingot", "minecraft:gold_ingot",
                "minecraft:raw_copper", "minecraft:emerald", "minecraft:quartz");
        List<String> products = List.of(
                "minecraft:iron_nugget", "minecraft:gold_nugget", "minecraft:copper_ingot",
                "minecraft:charcoal", "minecraft:diamond", "minecraft:redstone");
        String material = PcgPick.pick(base.fork("material"), materials);
        String product = PcgPick.pick(base.fork("product"), products);
        long cookTime = COOK_MIN + range(base, "cook", COOK_MAX - COOK_MIN + 1);
        double exp = (double) range(base, "exp", EXP_DIV + 1) / (double) EXP_DIV;
        return Td.write(TdTable.builder()
                .put("type", "minecraft:smoking")
                .put("ingredient", TdTable.builder().put("item", material).build())
                .put("result", TdTable.builder()
                        .put("item", product)
                        .put("experience", TdValue.of(exp))
                        .put("cooking_time", TdValue.of(cookTime))
                        .build())
                .build());
    }

    // ---------- 4) localization 本地化 ----------

    /**
     * 确定性生成一条本地化 td 文本（对齐 {@link LangDatum} 的 [k,v] 元素形态）。返回可被
     * {@link Td#parse} 解析的裸表：
     * <pre>
     * [
     *   [ k = "&lt;key&gt;", v = "&lt;generated-text&gt;" ]
     * ]
     * </pre>
     * 其中文本由 {@link PcgTextSample#generate} 用内部小词池 + {@code PcgSource(seed).fork(key)}
     * 确定性生成。确定性判据：同 {@code (seed, key)} 两次调用 -> 字节一致；不同 seed（大致）不同。
     * <p>
     * Deterministically generates one localization td document (aligned to {@link LangDatum}'s
     * [k,v] element shape) as a bare {@link Td#parse}-able table: one {@code [ k = key,
     * v = text ]} element, where the text is deterministically produced by
     * {@link PcgTextSample#generate} from internal small word pools + {@code PcgSource(seed).fork(key)}.
     * Determinism: same {@code (seed, key)} twice -> byte-identical output; distinct seeds
     * (roughly) differ.
     *
     * @param seed 主种子 / the master seed.
     * @param key  本地化键 / the localization key.
     * @return 可解析的本地化 td 文本 / a re-parseable localization td document.
     * @throws PcgException 理论不会触发（内部词池非空），仅供签名完整 / never thrown in practice
     *                      (internal pools are non-empty); retained for signature completeness.
     */
    public static String generateLocalization(long seed, String key) throws PcgException {
        String safe = key == null ? "" : key;
        String text = PcgTextSample.generate("loc:" + safe, seed, LOCALIZATION_TEMPLATE, LOCALIZATION_POOLS);
        return Td.write(TdTable.builder()
                .element(TdTable.builder()
                        .put("k", safe)
                        .put("v", text)
                        .build())
                .build());
    }

    // ---------- 5) biome variant 群系变种 ----------

    /**
     * 确定性生成一条群系变种 td 文本（biome/variant 形态，含确定性变体属性）。返回可被
     * {@link Td#parse} 解析的裸表：
     * <pre>
     * [
     *   biome = "&lt;base&gt;",
     *   variant = [
     *     name = "&lt;generated-name&gt;",
     *     heightMod = &lt;m&gt;,       // m 在 [0, 16]，seed 派生
     *     wetness = &lt;w&gt;,         // w 在 [0.0, 16.0]，seed 派生
     *     rarity = &lt;r&gt;           // r 在 [1, 6]，seed 派生
     *   ]
     * ]
     * </pre>
     * 确定性判据：同 {@code (seed, base)} 两次调用 -> 字节一致；不同 seed（大致）不同。变体名由
     * {@link PcgTextSample#generate} 生成；heightMod/wetness/rarity 由
     * {@code PcgSource(seed).fork("biome:"+base+":...")} 派生，落在上述固定闭区间。
     * <p>
     * Deterministically generates one biome-variant td document (a bespoke biome/variant shape
     * with deterministic variant attributes) as a bare {@link Td#parse}-able table with the shape
     * above. Determinism: same {@code (seed, base)} twice -> byte-identical output; distinct
     * seeds (roughly) differ. The variant name is generated via {@link PcgTextSample#generate};
     * heightMod/wetness/rarity derive from {@code PcgSource(seed).fork("biome:"+base+":...")}.
     *
     * @param seed 主种子 / the master seed.
     * @param base 基类群系标识 / the base biome id.
     * @return 可解析的群系变种 td 文本 / a re-parseable biome-variant td document.
     * @throws PcgException 理论不会触发（内部词池非空），仅供签名完整 / never thrown in practice
     *                      (internal pools are non-empty); retained for signature completeness.
     */
    public static String generateBiomeVariant(long seed, String base) throws PcgException {
        String safe = base == null ? "" : base;
        PcgSource src = new PcgSource(seed).fork("biome:" + safe);
        String name = PcgTextSample.generate("bvar:" + safe, seed, VARIANT_TEMPLATE, VARIANT_POOLS);
        long height = range(src, "height", HEIGHT_MAX + 1);
        long rarity = RARITY_MIN + range(src, "rarity", RARITY_MAX - RARITY_MIN + 1);
        double wetness = (double) range(src, "wetness", HEIGHT_MAX + 1);
        return Td.write(TdTable.builder()
                .put("biome", safe)
                .put("variant", TdTable.builder()
                        .put("name", name)
                        .put("heightMod", TdValue.of(height))
                        .put("wetness", TdValue.of(wetness))
                        .put("rarity", TdValue.of(rarity))
                        .build())
                .build());
    }

    // ---------- 内部辅助 ----------

    /**
     * 在当前基源上按盐独立 fork，取 [0, bound) 内的确定性整数。
     * Draws a deterministic integer in [0, bound) from an isolated salt fork of the base.
     */
    private static long range(PcgSource base, String salt, long bound) {
        return base.fork(salt).nextInt((int) bound);
    }

    /** 确定性派生盐串 {@code "seed-"+31 位确定性值}。A deterministic seed salt string. */
    private static String seedSalt(PcgSource base, String salt) {
        return "seed-" + base.fork(salt).nextInt(1_000_000);
    }

    /** 本地化文本模板 / localization text template. */
    private static final String LOCALIZATION_TEMPLATE = "{the} {adj} {noun} {verb}";
    /** 本地化文本小词池 / localization small word pools. */
    private static final Map<String, List<String>> LOCALIZATION_POOLS = Map.of(
            "the", List.of("The", "A"),
            "adj", List.of("ancient", "crimson", "hidden", "forgotten", "silver", "dusk"),
            "noun", List.of("temple", "sigil", "shrine", "vault", "runestone", "gate"),
            "verb", List.of("appears", "weeps", "guards", "dreams", "whispers", "watches"));

    /** 群系变体名模板 / biome-variant name template. */
    private static final String VARIANT_TEMPLATE = "{adj}-{noun}";
    /** 群系变体名小词池 / biome-variant name small word pools. */
    private static final Map<String, List<String>> VARIANT_POOLS = Map.of(
            "adj", List.of("frost", "ember", "moss", "sand", "vine", "ash"),
            "noun", List.of("hills", "delta", "reach", "shelf", "strand", "moor"));
}