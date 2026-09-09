package io.toterra.subterra.engine.pcg;

import io.toterra.subterra.engine.schema.Schema;
import io.toterra.subterra.engine.schema.SchemaField;
import io.toterra.subterra.engine.schema.SchemaKind;
import io.toterra.subterra.engine.config.Td;
import io.toterra.subterra.engine.config.TdTable;
import io.toterra.subterra.engine.config.TdValue;

import java.util.List;
import java.util.Objects;

/**
 * p.2.13.4 schema 驱动生成计划（final 工具类，静态入口）——把 p.2.12 的 td schema 作为生成器
 * 输入（「schema 定义数据形状，PCG 按形状生成实例」）：按 {@link Schema} 的形状确定性填充一份
 * 合法实例，输出 td 文本。只在 p.2.13.1/2/3 的确定性基座（{@link PcgSource}/{@link PcgPick}）
 * 之上叠加一层「按字段形状做值」的编译器式遍历，不自行造 PRNG、不重写随机源。
 * <p>
 * <b>验收判据（写死）</b>：
 * <ul>
 *   <li>合法实例：对任意 TABLE 根 schema，{@code Schema.validate(Td.parse(generate(seed, schema)))}
 *       {@code valid=true} 恒成立（生成值严格落在对应字段 kind 的固定相容形态内）；</li>
 *   <li>确定性：同 {@code (seed, schema)} 两次独立调用 → 两次返回的 td 文本逐字节一致
 *       （同 seed → 同输出）。</li>
 * </ul>
 * 输出经 {@link TdTable} 模型构建 + {@link Td#write} 序列化，天然是合法 td 文本，可被 {@link Td#parse}
 * 无损反解析（round-trip）。
 * <p>
 * <b>确定性契约</b>（各字段取值全部由
 * {@code new PcgSource(seed).fork("schema")} 派生基线源，再逐字段独立
 * {@code fork("field:"+字段名)} 派生——字段间相互隔离、父源不受影响，取值只由种子、schema 字段
 * 文档序与字段名决定，绝无时间戳/随机/时序）。
 * <p>
 * <b>字段生成规则</b>（写死区间）：
 * <ul>
 *   <li>STRING → 内部固定小词池经 {@link PcgPick#pick} 选词 + 固定下划线数字后缀
 *       {@code "-" + [0, 1000)}（确定性）；</li>
 *   <li>INT → 固定闭区间 {@code [0, 1024)}；</li>
 *   <li>FLOAT → 固定闭区间 {@code [0.0, 1.0)}；</li>
 *   <li>BOOL → {@link PcgPick#pick} 从 {@code ["true","false"]} 二值池确定性选一；</li>
 *   <li>TABLE → 生成嵌套 td 表（子表含 {@code [1,4]} 条确定性命名标量项，静态深度≥1，确定性）；
 *       </li>
 *   <li>LIST → 生成元素表（{@link TdTable#elements()} 含固定数量 {@code [1,4]} 条确定性整型项）。
 *       </li>
 * </ul>
 * <b>仅处理 TABLE 根</b>：标量(STRING/INT/FLOAT/BOOL)/LIST 根抛受检 {@link PcgException#SCHEMA_ROOT}。
 * 纯 JDK、无 O(n²)（单趟遍历字段，每字段一次 fork + 常数次抽取；局部字池固定规模）、无全局状态。
 * <p>
 * p.2.13.4 schema-driven generation plan (final utility class, static entry) — feeds a p.2.12 td
 * schema into the generator ("the schema defines the data shape; PCG fills instances by that shape"):
 * deterministically fills one valid instance from the {@link Schema} shape and emits td text. It only
 * layers a compiler-style "value per field shape" traversal on top of the p.2.13.1/.2/.3 deterministic
 * base ({@link PcgSource}/{@link PcgPick}) — it never re-invents a PRNG and never re-writes the source.
 * <p>
 * <b>Acceptance criteria (hard-coded)</b>:
 * <ul>
 *   <li>valid instance: for any TABLE-root schema {@code Schema.validate(Td.parse(generate(seed, schema)))}
 *       always holds with {@code valid=true} (generated values fall inside the fixed compatible form of
 *       their field kind);</li>
 *   <li>determinism: two independent calls on the same {@code (seed, schema)} → byte-identical td text
 *       (same seed → same output).</li>
 * </ul>
 * Output is assembled as a {@link TdTable} model and serialised via {@link Td#write}, so it is natively
 * valid td text and losslessly re-parseable via {@link Td#parse} (round-trip).
 * <p>
 * <b>Determinism contract</b>: every field value is derived via
 * {@code new PcgSource(seed).fork("schema")} as the baseline, then an independent per-field
 * {@code fork("field:"+fieldName)} — fields are mutually isolated, the parent is unaffected, and each
 * value depends only on the seed, the schema field document order and the field name; never on
 * timestamp / random / timing.
 * <p>
 * <b>Per-field generation rules</b> (hard-coded intervals):
 * <ul>
 *   <li>STRING → {@link PcgPick#pick} from an internal fixed small word pool + a fixed underscore-digit
 *       suffix {@code "-" + [0, 1000)} (deterministic);</li>
 *   <li>INT → fixed closed interval {@code [0, 1024)};</li>
 *   <li>FLOAT → fixed closed interval {@code [0.0, 1.0)};</li>
 *   <li>BOOL → {@link PcgPick#pick} from a two-value pool {@code ["true","false"]};</li>
 *   <li>TABLE → a nested td table (a sub-table with {@code [1,4]} deterministic named scalar entries,
 *       static depth ≥ 1, deterministic);</li>
 *   <li>LIST → an element table ({@link TdTable#elements()} holds a fixed count of {@code [1,4]}
 *       deterministic integer items).</li>
 * </ul>
 * <b>Only a TABLE root is handled</b>: a scalar (STRING/INT/FLOAT/BOOL) or LIST root throws the checked
 * {@link PcgException#SCHEMA_ROOT}. Pure JDK, no O(n²) (single-pass field sweep, one fork + constant draws
 * per field; word pools are fixed-size), no global state.
 */
public final class PcgSchemaGen {

    /** STRING 词池规模（固定）/ STRING word-pool size (fixed). */
    private static final String[] STRING_POOL = {
            "root", "branch", "vein", "core", "spine", "anchor", "shard", "shaft",
            "rift", "moss", "glyph", "seam", "crest", "lode", "keel", "mote"
    };
    /** BOOL 二值池（固定序）/ the two-value BOOL pool (fixed order). */
    private static final String[] BOOL_POOL = {"true", "false"};
    /** 嵌套 TABLE 项键词池（固定）/ the nested-TABLE entry key pool (fixed). */
    private static final String[] KEY_POOL = {
            "id", "name", "value", "slot", "tier", "node", "edge", "label"
    };

    /** INT 闭区间上界（下界定 0，值域 [0, 1024)）。/ INT closed-upper-bound (lower fixed 0, range [0, 1024)). */
    private static final int INT_BOUND = 1024;
    /** 数字后缀闭区间上界（值域 [0, 1000)）。/ digit-suffix closed-upper-bound (range [0, 1000)). */
    private static final int SUFFIX_BOUND = 1000;
    /** 嵌套 TABLE / LIST 项数闭区间上界（下界定 1，值域 [1, 4]）。/ nested TABLE/LIST item-count upper bound (lower fixed 1, range [1, 4]). */
    private static final int ITEM_BOUND = 4;

    private PcgSchemaGen() {
        // utility class; no instantiation / 工具类，禁止实例化
    }

    /**
     * 按 schema 确定性生成一份合法实例 td 文本。根必须是 TABLE（标量/LIST 根抛
     * {@link PcgException#SCHEMA_ROOT}）。输出保证
     * {@code Schema.validate(Td.parse(output)).valid() == true}，且同 {@code (seed, schema)}
     * 两次调用逐字节一致。见类级 Javadoc。
     * <p>
     * Deterministically generates a valid-instance td document from the schema. The root must be TABLE
     * (a scalar/LIST root throws {@link PcgException#SCHEMA_ROOT}). The output guarantees
     * {@code Schema.validate(Td.parse(output)).valid() == true}, and two calls on the same
     * {@code (seed, schema)} are byte-identical. See the class-level Javadoc.
     *
     * @param seed   主种子，所有取值的确定性基底 / the master seed, the deterministic substrate of every draw.
     * @param schema 生成输入的 td schema / the td schema driving the shape.
     * @return 合法实例 td 文本 / a valid-instance td document.
     * @throws PcgException 根非 TABLE（{@link PcgException#SCHEMA_ROOT}）/ if the root is not TABLE
     *                      ({@link PcgException#SCHEMA_ROOT}).
     */
    public static String generate(long seed, Schema schema) throws PcgException {
        Objects.requireNonNull(schema, "schema must not be null");
        if (schema.root() != SchemaKind.TABLE) {
            throw new PcgException(PcgException.SCHEMA_ROOT,
                    "schema-driven generation only supports a TABLE root, got " + schema.root().tdName());
        }
        PcgSource base = new PcgSource(seed).fork("schema");
        TdTable.Builder b = TdTable.builder();
        for (SchemaField f : schema.walk()) {
            b.put(f.name(), genValue(base.fork("field:" + f.name()), f.kind()));
        }
        return Td.write(b.build());
    }

    /** 递归：按字段 kind 确定性生成其值 / recursion: deterministically produces the value for a field kind. */
    private static TdValue genValue(PcgSource fs, SchemaKind kind) throws PcgException {
        return switch (kind) {
            case STRING -> genString(fs);
            case INT -> TdValue.of((long) fs.fork("int").nextInt(INT_BOUND));
            case FLOAT -> TdValue.of(fs.fork("float").nextDouble());
            case BOOL -> TdValue.of(Boolean.parseBoolean(
                    PcgPick.pick(fs.fork("bool"), List.of(BOOL_POOL))));
            case TABLE -> genNestedTable(fs);
            case LIST -> genList(fs);
        };
    }

    /** STRING：词池选词 + 固定数字后缀 / STRING: pool pick + fixed digit suffix. */
    private static TdValue genString(PcgSource fs) throws PcgException {
        String word = PcgPick.pick(fs.fork("string"), List.of(STRING_POOL));
        int suffix = fs.fork("suffix").nextInt(SUFFIX_BOUND);
        return TdValue.str(word + "-" + suffix);
    }

    /** TABLE：嵌套 td 表（[1,4] 条确定性命名标量项，静态深度≥1）/ TABLE: nested td table ([1,4] named scalar entries, depth ≥ 1). */
    private static TdValue genNestedTable(PcgSource fs) throws PcgException {
        int n = 1 + fs.fork("n").nextInt(ITEM_BOUND);
        TdTable.Builder b = TdTable.builder();
        for (int i = 0; i < n; i++) {
            String key = PcgPick.pick(fs.fork("key:" + i), List.of(KEY_POOL));
            long v = fs.fork("val:" + i).nextInt(INT_BOUND);
            b.put(key, TdValue.of(v));
        }
        return b.build();
    }

    /** LIST：元素表（[1,4] 条确定性整型元素）/ LIST: element table ([1,4] deterministic integer elements). */
    private static TdValue genList(PcgSource fs) throws PcgException {
        int n = 1 + fs.fork("count").nextInt(ITEM_BOUND);
        TdTable.Builder b = TdTable.builder();
        for (int i = 0; i < n; i++) {
            b.element(TdValue.of((long) fs.fork("elem:" + i).nextInt(INT_BOUND)));
        }
        return b.build();
    }
}