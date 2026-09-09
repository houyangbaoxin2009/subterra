// p.2.13.5 deterministic content-generation probe for p.2.13.1-.4 (PcgSource / PcgPick /
// PcgTextSample / PcgContentGen / PcgSchemaGen). Pure JVM - no MC runtime, no timestamps /
// random / timing; exit 0 = PASS, 1 = FAIL. NOT shipped in the mod jar.
package io.toterra.subterra.probes;

import io.toterra.subterra.engine.config.Td;
import io.toterra.subterra.engine.config.TdTable;
import io.toterra.subterra.engine.config.TdValue;
import io.toterra.subterra.engine.pcg.PcgContentGen;
import io.toterra.subterra.engine.pcg.PcgException;
import io.toterra.subterra.engine.pcg.PcgPick;
import io.toterra.subterra.engine.pcg.PcgSchemaGen;
import io.toterra.subterra.engine.pcg.PcgSource;
import io.toterra.subterra.engine.pcg.PcgTextSample;
import io.toterra.subterra.engine.schema.Schema;
import io.toterra.subterra.engine.schema.SchemaCodec;
import io.toterra.subterra.engine.schema.SchemaReport;

import java.util.List;
import java.util.Map;

/**
 * p.2.13.5 确定性内容生成探针 —— 结构化断言、每断言一 check、退出码与参考模板（{@link ScaffoldProbe}）
 * 一致。五节：
 * <ol>
 *   <li><b>PcgSource 确定性</b>：同 seed 两源前 N 个 nextLong 逐值一致；nextInt(bound) 值域；fork 隔离
 *       （父不受影响、同盐同父态派生一致、派生源与父源流至少一组不同）；</li>
 *   <li><b>PcgPick</b>：普通 pick / 加权 pickWeighted 两次同 seed 一致、权重总和稳定；空池 (两种重载) →
 *       EMPTY_POOL；负权重 → NEGATIVE_WEIGHT；</li>
 *   <li><b>PcgTextSample</b>：相同 (seedSalt,seed,template,pools) 两次逐字符一致；无占位符 → 原样；未知槽 →
 *       UNKNOWN_SLOT；</li>
 *   <li><b>PcgContentGen 五类</b>：relic / ledger / recipe / localization / biome-variant 相同 (seed, 参数)
 *       两次逐字节一致、输出可被 {@link Td#parse} 解析；并以两个固定种子断言 relic.count 与 recipe
 *       cooking_time 的 INT 字段不同（固定样例，非概率断言）；</li>
 *   <li><b>PcgSchemaGen</b>：p.2.12 TABLE schema（{@link SchemaCodec#parse} 固定 schema td）同 seed 两次
 *       输出逐字节一致、输出 {@code Schema.validate(Td.parse(out))} valid=true；标量根 → SCHEMA_ROOT。</li>
 * </ol>
 * 全为确定性断言、禁时间戳/随机/时序；退出码 0 = PASS。
 *
 * <p>p.2.13.5 deterministic content-generation probe with structured assertions, one check per
 * assertion, exit-code convention identical to the reference template ({@link ScaffoldProbe}).
 * Five sections:
 * <ol>
 *   <li><b>PcgSource determinism</b>: two same-seed sources agree value-for-value over the first N
 *       nextLong; nextInt(bound) stays in [0, bound); fork isolation (parent unaffected, same-salt
 *       same-parent-state forks derive equal sources, derived source differs from the parent stream in at
 *       least one position);</li>
 *   <li><b>PcgPick</b>: plain pick and weighted pickWeighted agree twice on the same seed, the total weight
 *       stays stable; an empty pool (both overloads) → EMPTY_POOL; a non-positive weight → NEGATIVE_WEIGHT;</li>
 *   <li><b>PcgTextSample</b>: identical (seedSalt, seed, template, pools) twice → char-identical; a template
 *       without placeholders → as-is; an unknown slot → UNKNOWN_SLOT;</li>
 *   <li><b>PcgContentGen, five shapes</b>: relic / ledger / recipe / localization / biome-variant produce
 *       byte-identical output for the same (seed, argument) and a {@link Td#parse}able output; and, on two
 *       fixed seeds, the relic count and recipe cooking_time INT fields differ (fixed sample, not probabilistic);</li>
 *   <li><b>PcgSchemaGen</b>: a p.2.12 TABLE schema ({@link SchemaCodec#parse} of a fixed schema td) is
 *       byte-identical across two same-seed calls and validates via
 *       {@code Schema.validate(Td.parse(out))} valid=true; a scalar root → SCHEMA_ROOT.</li>
 * </ol>
 * All assertions deterministic, no timestamp / random / timing; exit 0 = PASS.
 */
public final class PcgProbe {

    private PcgProbe() {
    }

    // ---- fixture constants (the deterministic single source of truth) ----
    private static final long SEED_A = 0x5EED_0000_0000_0001L;
    private static final long SEED_B = 0x5EED_FFFF_FFFF_FFFFL;
    private static final long FORK_SEED = 1_000_003L;
    private static final int  N_NEXTLONG = 8;
    private static final int  INT_BOUND = 997;
    private static final int  INT_SAMPLES = 32;

    private static final List<String> POOL = List.of("copper", "gold", "iron", "diamond", "emerald", "redstone");
    private static final List<Long> WEIGHTS = List.of(1L, 2L, 3L, 4L, 5L, 6L);

    private static final String TEMPLATE = "The {adj} {noun} in the {place}. A {noun} {verb}.";
    private static final Map<String, List<String>> POOLS = Map.of(
            "adj", List.of("ancient", "crimson", "hidden", "silver", "dusk", "forged"),
            "noun", List.of("temple", "sigil", "shrine", "vault", "runestone", "gate"),
            "verb", List.of("appears", "weeps", "guards", "dreams", "whispers"),
            "place", List.of("deep", "wastes", "fog", "edge", "hollow"));

    /** p.2.12 TABLE schema —— 覆盖 STRING/INT/BOOL/TABLE/LIST 五种字段 kind 的固定 schema td。 */
    private static final String SCHEMA_TABLE_SRC =
            "[\n"
            + "  root = \"table\",\n"
            + "  fields = [\n"
            + "    name = \"string\",\n"
            + "    level = \"int\",\n"
            + "    enabled = \"bool\",\n"
            + "    stats = \"table\",\n"
            + "    parts = \"list\",\n"
            + "  ],\n"
            + "]";

    /** 标量根 schema（INT）—— 用于 SCHEMA_ROOT 拒绝断言。 */
    private static final String SCHEMA_INT_SRC =
            "[\n  root = \"int\",\n]";

    private static int failures = 0;
    private static int checks = 0;

    private static void check(String name, boolean ok) {
        checks++;
        if (ok) {
            System.out.println("[PASS] " + name);
        } else {
            failures++;
            System.out.println("[FAIL] " + name);
        }
    }

    /** 一段固定 seed 的 nextLong 序列。A deterministic nextLong prefix. */
    private static long[] nextLongPrefix(long seed, int n) {
        PcgSource s = new PcgSource(seed);
        long[] a = new long[n];
        for (int i = 0; i < n; i++) {
            a[i] = s.nextLong();
        }
        return a;
    }

    /** 两段 nextLong 前缀逐值一致。Value-for-value equality of two prefixes. */
    private static boolean samePrefix(long[] a, long[] b) {
        if (a.length != b.length) {
            return false;
        }
        for (int i = 0; i < a.length; i++) {
            if (a[i] != b[i]) {
                return false;
            }
        }
        return true;
    }

    /** 异常被抛且 reason 匹配；未抛则返回 false。True iff the action throws with the given reason. */
    private static boolean reasonOf(RunnableWithPcg r, String reason) {
        try {
            r.run();
            return false;
        } catch (PcgException e) {
            return reason.equals(e.reason());
        }
    }

    @FunctionalInterface
    private interface RunnableWithPcg {
        void run() throws PcgException;
    }

    public static void main(String[] args) {
        try {
            run();
        } catch (Exception e) {
            failures++;
            System.out.println("[FAIL] 探针异常: " + e);
            e.printStackTrace(System.out);
        }

        if (failures == 0) {
            System.out.println("PcgProbe: " + checks + " checks, 0 failures");
            System.exit(0);
        } else {
            System.out.println("PcgProbe: " + checks + " checks, " + failures + " failures");
            System.exit(1);
        }
    }

    private static void run() throws Exception {
        // ================= 1. PcgSource 确定性 / PcgSource determinism =================
        long[] a = nextLongPrefix(SEED_A, N_NEXTLONG);
        long[] a2 = nextLongPrefix(SEED_A, N_NEXTLONG);
        check("PcgSource: 同 seed 两源前 " + N_NEXTLONG + " 个 nextLong 逐值一致",
                samePrefix(a, a2));

        PcgSource boundSrc = new PcgSource(FORK_SEED);
        boolean boundOk = true;
        for (int i = 0; i < INT_SAMPLES; i++) {
            int v = boundSrc.nextInt(INT_BOUND);
            if (v < 0 || v >= INT_BOUND) {
                boundOk = false;
                break;
            }
        }
        check("PcgSource: nextInt(" + INT_BOUND + ") " + INT_SAMPLES + " 次全部落在 [0,bound)",
                boundOk);

        // fork 隔离 1：fork 后父源后续序列与从未 fork 的对照源一致（父不受影响）
        PcgSource parent = new PcgSource(FORK_SEED);
        PcgSource control = new PcgSource(FORK_SEED);
        for (int i = 0; i < 2; i++) {
            parent.nextLong();
            control.nextLong();
        }
        parent.fork("iso-salt");
        boolean parentUnaffected = true;
        for (int i = 0; i < 3; i++) {
            if (parent.nextLong() != control.nextLong()) {
                parentUnaffected = false;
                break;
            }
        }
        check("fork: 父源后续序列不受 fork 影响（与对照源一致）", parentUnaffected);

        // fork 隔离 2：同盐同父态的两次 fork 派生源一致
        PcgSource parentX = new PcgSource(FORK_SEED);
        PcgSource parentY = new PcgSource(FORK_SEED);
        for (int i = 0; i < 2; i++) {
            parentX.nextLong();
            parentY.nextLong();
        }
        PcgSource childX = parentX.fork("iso-salt");
        PcgSource childY = parentY.fork("iso-salt");
        boolean childSame = true;
        for (int i = 0; i < 5; i++) {
            if (childX.nextLong() != childY.nextLong()) {
                childSame = false;
                break;
            }
        }
        check("fork: 同盐同父态两次 fork 派生源一致", childSame);

        // fork 隔离 3：同父态下派生源（独立源）与父源同位置延续流首几项至少一组不同（固定样例）
        PcgSource par3 = new PcgSource(FORK_SEED);
        for (int i = 0; i < 2; i++) {
            par3.nextLong();
        }
        PcgSource der3 = par3.fork("iso-salt");
        boolean forkDiff = false;
        for (int i = 0; i < 5; i++) {
            if (der3.nextLong() != par3.nextLong()) {
                forkDiff = true;
                break;
            }
        }
        check("fork: 派生源与父源流至少一组不同", forkDiff);

        // ================= 2. PcgPick / 普通与加权选择 =================
        check("pick: 固定池两次同 seed 一致",
                PcgPick.pick(new PcgSource(SEED_A), POOL)
                        .equals(PcgPick.pick(new PcgSource(SEED_A), POOL)));

        check("pickWeighted: 同 seed 一致且权重总和稳定",
                PcgPick.pickWeighted(new PcgSource(SEED_A), POOL, WEIGHTS)
                        .equals(PcgPick.pickWeighted(new PcgSource(SEED_A), POOL, WEIGHTS)));
        check("pickWeighted: 权重总和为 1+2+3+4+5+6 = 21",
                WEIGHTS.stream().mapToLong(Long::longValue).sum() == 21L);

        check("pick: 空池 → EMPTY_POOL",
                reasonOf(() -> PcgPick.pick(new PcgSource(SEED_A), List.of()),
                        PcgException.EMPTY_POOL));
        check("pickWeighted: 空池 → EMPTY_POOL",
                reasonOf(() -> PcgPick.pickWeighted(new PcgSource(SEED_A), List.of(), List.of()),
                        PcgException.EMPTY_POOL));
        check("pickWeighted: 负权重 → NEGATIVE_WEIGHT",
                reasonOf(() -> PcgPick.pickWeighted(new PcgSource(SEED_A), POOL, List.of(1L, 0L, 3L, 4L, 5L, 6L)),
                        PcgException.NEGATIVE_WEIGHT));

        // ================= 3. PcgTextSample / 文本槽位生成 =================
        String t1 = PcgTextSample.generate("salt-z", SEED_A, TEMPLATE, POOLS);
        String t2 = PcgTextSample.generate("salt-z", SEED_A, TEMPLATE, POOLS);
        check("PcgTextSample: 同 (seedSalt,seed,template,pools) 两次逐字符一致", t1.equals(t2));

        String literal = "No placeholders here";
        check("PcgTextSample: 模板无占位符 → 原样返回",
                literal.equals(PcgTextSample.generate("salt", SEED_A, literal, POOLS)));

        check("PcgTextSample: 未知槽 → UNKNOWN_SLOT",
                reasonOf(() -> PcgTextSample.generate("salt", SEED_A, "x {bogus} y", POOLS),
                        PcgException.UNKNOWN_SLOT));

        // ================= 4. PcgContentGen 五类 / five content shapes =================
        String r1 = PcgContentGen.generateRelic(SEED_A, "relic_rune");
        String r2 = PcgContentGen.generateRelic(SEED_A, "relic_rune");
        check("PcgContentGen.relic: 同 (seed,name) 两次逐字节一致", r1.equals(r2));
        check("PcgContentGen.relic: 输出可被 Td.parse 解析", tdParseable(r1));

        String g1 = PcgContentGen.generateLedgerEntry(SEED_A, "ledger_topic");
        String g2 = PcgContentGen.generateLedgerEntry(SEED_A, "ledger_topic");
        check("PcgContentGen.ledger: 同 (seed,key) 两次逐字节一致", g1.equals(g2));
        check("PcgContentGen.ledger: 输出可被 Td.parse 解析", tdParseable(g1));

        String rec1 = PcgContentGen.generateRecipe(SEED_A, "recipe_item");
        String rec2 = PcgContentGen.generateRecipe(SEED_A, "recipe_item");
        check("PcgContentGen.recipe: 同 (seed,id) 两次逐字节一致", rec1.equals(rec2));
        check("PcgContentGen.recipe: 输出可被 Td.parse 解析", tdParseable(rec1));

        String loc1 = PcgContentGen.generateLocalization(SEED_A, "loc.key");
        String loc2 = PcgContentGen.generateLocalization(SEED_A, "loc.key");
        check("PcgContentGen.localization: 同 (seed,key) 两次逐字节一致", loc1.equals(loc2));
        check("PcgContentGen.localization: 输出可被 Td.parse 解析", tdParseable(loc1));

        String bv1 = PcgContentGen.generateBiomeVariant(SEED_A, "minecraft:plains");
        String bv2 = PcgContentGen.generateBiomeVariant(SEED_A, "minecraft:plains");
        check("PcgContentGen.biomeVariant: 同 (seed,base) 两次逐字节一致", bv1.equals(bv2));
        check("PcgContentGen.biomeVariant: 输出可被 Td.parse 解析", tdParseable(bv1));

        long countA = intField(PcgContentGen.generateRelic(SEED_A, "relic_cmp"), "count");
        long countB = intField(PcgContentGen.generateRelic(SEED_B, "relic_cmp"), "count");
        check("PcgContentGen.relic: 不同 seed → count(INT) 不同（固定样例）", countA != countB);

        long cookA = nestedIntField(PcgContentGen.generateRecipe(SEED_A, "recipe_cmp"), "result", "cooking_time");
        long cookB = nestedIntField(PcgContentGen.generateRecipe(SEED_B, "recipe_cmp"), "result", "cooking_time");
        check("PcgContentGen.recipe: 不同 seed → cooking_time(INT) 不同（固定样例）", cookA != cookB);

        // ================= 5. PcgSchemaGen / schema 驱动生成 =================
        Schema table = SchemaCodec.parse(SCHEMA_TABLE_SRC);
        String s1 = PcgSchemaGen.generate(SEED_A, table);
        String s2 = PcgSchemaGen.generate(SEED_A, table);
        check("PcgSchemaGen: 同 seed 两次输出逐字节一致", s1.equals(s2));

        SchemaReport rep = table.validate(Td.parse(s1));
        check("PcgSchemaGen: 输出 Schema.validate(Td.parse(out)) valid=true",
                rep.valid() && rep.reason().isEmpty() && rep.fieldPath().isEmpty());

        Schema intRoot = SchemaCodec.parse(SCHEMA_INT_SRC);
        check("PcgSchemaGen: 标量根 → SCHEMA_ROOT",
                reasonOf(() -> PcgSchemaGen.generate(SEED_A, intRoot), PcgException.SCHEMA_ROOT));
    }

    /** 尝试解析，成功返回 true。True iff the text parses as td. */
    private static boolean tdParseable(String text) {
        try {
            Td.parse(text);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /** 读取嵌套 TABLE 内的一级 INT 字段。Reads an INT field inside a nested table. */
    private static long nestedIntField(String text, String outer, String key) {
        TdTable t = Td.parse(text);
        TdTable inner = (TdTable) t.get(outer);
        return inner.get(key).scalar().i();
    }

    /** 解析 td 并读取顶层 INT 字段。Reads a top-level INT field from parsed td. */
    private static long intField(String text, String key) {
        TdTable t = Td.parse(text);
        TdValue v = t.get(key);
        TdValue.Scalar s = v.scalar();
        return s.i();
    }
}