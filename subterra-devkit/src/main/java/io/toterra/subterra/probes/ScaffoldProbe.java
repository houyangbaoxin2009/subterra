// Deterministic schema-first scaffolding probe for p.2.12.5: the p.2.12.1 schema codec
// (parse settlement / violation rejection / deterministic validate verdicts) and the three
// p.2.12.2--.4 generators (Register / Stub / Doc / FormData) — byte identity, round-trip
// stability and shape. Pure JVM — no MC, no wall-clock, no timestamps, no random seeds.
// NOT shipped in the mod jar. Every assertion is deterministic; byte equality is the
// strongest judgement.
package io.toterra.subterra.probes;

import io.toterra.subterra.engine.config.TdTable;
import io.toterra.subterra.engine.config.TdValue;
import io.toterra.subterra.engine.schema.Schema;
import io.toterra.subterra.engine.schema.SchemaCodec;
import io.toterra.subterra.engine.schema.SchemaField;
import io.toterra.subterra.engine.schema.SchemaKind;
import io.toterra.subterra.engine.schema.SchemaReport;
import io.toterra.subterra.engine.schema.SchemaViolationException;
import io.toterra.subterra.engine.scaffold.FormDataGen;
import io.toterra.subterra.engine.scaffold.RegisterCodeGen;
import io.toterra.subterra.engine.scaffold.SchemaStubGen;

/**
 * p.2.12.5 确定性「脚手架探针」—— 覆盖 p.2.12.1–.4 被测面的全部确定性断言：
 * schema td 解析定型（TABLE/LIST/标量三个样例的 root / fields 文档序 / listOf 形态、
 * {@code parse(toTd(s))} 再 toTd 逐字节一致、两次 parse 同输入结果相等）、违约拒绝
 * （缺 root → MISSING_ROOT、未知 kind → UNKNOWN_KIND、重复字段 → DUPLICATE_FIELD）、
 * {@code validate} 确定性判定（合法 TABLE 数据→通过、字段类型不符→{@code field type mismatch}、
 * 缺字段→{@code missing field}、未知字段→{@code unknown field}、LIST/scalar 样例各一）、
 * 以及三生成器确定性（同一 schema 两次生成逐字节一致、{@code gen(parse(toTd(s)))==gen(s)}
 * 往返稳定、每生成器至少一格输出形态断言），外加 {RegisterCodeGen.generate(String)} 与
 * parse+generate 贯通一致。全为确定性断言、禁时间戳/随机/时序；退出码 0 = PASS。
 *
 * <p>p.2.12.5 deterministic schema-first scaffolding probe — the full deterministic surface of
 * the p.2.12.1–.4 code under test: schema td parse settlement (TABLE / LIST / scalar samples with
 * root / document-order fields / listOf shapes, {@code parse(toTd(s))} re-encoded byte-identical,
 * two parses of the same input equal), violation rejection (missing root → MISSING_ROOT, unknown
 * kind → UNKNOWN_KIND, duplicate field → DUPLICATE_FIELD), {@code validate} deterministic verdicts
 * (valid TABLE data → pass, field type mismatch → {@code field type mismatch}, missing field →
 * {@code missing field}, unknown field → {@code unknown field}; one LIST and one scalar sample),
 * and generator determinism (two generations for the same schema byte-identical,
 * {@code gen(parse(toTd(s)))==gen(s)} round-trip stable, at least one shape assertion per
 * generator), plus {RegisterCodeGen.generate(String)} agreeing with parse + generate. All assertions
 * deterministic, no timestamp / random / timing; exit 0 = PASS.
 */
public final class ScaffoldProbe {

    private ScaffoldProbe() {
    }

    // ---- fixed schema td sources (the deterministic single source of truth) ----
    private static final String TABLE_SRC =
            "[\n"
            + "  root = \"table\",\n"
            + "  fields = [\n"
            + "    name = \"string\",\n"
            + "    level = \"int\",\n"
            + "    enabled = \"bool\",\n"
            + "    stats = \"table\",\n"
            + "  ],\n"
            + "]";

    private static final String LIST_SRC =
            "[\n"
            + "  root = \"list\",\n"
            + "  listOf = \"string\",\n"
            + "]";

    private static final String SCALAR_SRC =
            "[\n"
            + "  root = \"int\",\n"
            + "]";

    private static final String DUP_SRC =
            "[\n"
            + "  root = \"table\",\n"
            + "  fields = [\n"
            + "    name = \"string\",\n"
            + "    level = \"int\",\n"
            + "    name = \"string\",\n"
            + "  ],\n"
            + "]";

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

    /** 捕获文档级违规定因；可解析返回 null。Fixed violation reason, or null if it parses. */
    private static String violationReasonOf(String src) {
        try {
            SchemaCodec.parse(src);
            return null;
        } catch (SchemaViolationException e) {
            return e.reason();
        }
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
            System.out.println("ScaffoldProbe: " + checks + " checks, 0 failures");
            System.exit(0);
        } else {
            System.out.println("ScaffoldProbe: " + checks + " checks, " + failures + " failures");
            System.exit(1);
        }
    }

    private static void run() throws Exception {
        // ================= 1. schema 解析定型 / schema parse settlement =================
        Schema table = SchemaCodec.parse(TABLE_SRC);
        check("定型 TABLE: root == table", table.root() == SchemaKind.TABLE);
        check("定型 TABLE: listOf 为空", table.listOf() == null);
        check("定型 TABLE: fields 文档序 [name,level,enabled,stats]",
                fieldsNames(table).equals(java.util.List.of("name", "level", "enabled", "stats")));
        check("定型 TABLE: 字段种类 [string,int,bool,table]",
                fieldsKinds(table).equals(java.util.List.of(
                        SchemaKind.STRING, SchemaKind.INT, SchemaKind.BOOL, SchemaKind.TABLE)));

        Schema list = SchemaCodec.parse(LIST_SRC);
        check("定型 LIST: root == list", list.root() == SchemaKind.LIST);
        check("定型 LIST: listOf == string", list.listOf() == SchemaKind.STRING);
        check("定型 LIST: fields 为空", list.fields().isEmpty());

        Schema scalar = SchemaCodec.parse(SCALAR_SRC);
        check("定型 标量: root == int", scalar.root() == SchemaKind.INT);
        check("定型 标量: fields 与 listOf 皆为空",
                scalar.fields().isEmpty() && scalar.listOf() == null);

        // ---- 往返逐字节一致 + 两次 parse 同输入相等 ----
        check("往返: TABLE parse(toTd(s)) 后 toTd 逐字节一致",
                SchemaCodec.toTd(SchemaCodec.parse(SchemaCodec.toTd(table))).equals(SchemaCodec.toTd(table)));
        check("往返: LIST parse(toTd(s)) 后 toTd 逐字节一致",
                SchemaCodec.toTd(SchemaCodec.parse(SchemaCodec.toTd(list))).equals(SchemaCodec.toTd(list)));
        check("往返: 标量 parse(toTd(s)) 后 toTd 逐字节一致",
                SchemaCodec.toTd(SchemaCodec.parse(SchemaCodec.toTd(scalar))).equals(SchemaCodec.toTd(scalar)));
        check("往返: parse(toTd(s)) 与 s 相等（记录级别）",
                SchemaCodec.parse(SchemaCodec.toTd(table)).equals(table));
        check("确定性: 同输入两次 parse 结果相等",
                SchemaCodec.parse(TABLE_SRC).equals(SchemaCodec.parse(TABLE_SRC)));

        // ================= 2. 违约拒绝 / violation rejection =================
        String noRoot = violationReasonOf(
                "[\n  fields = [\n    name = \"string\",\n  ],\n]");
        check("违约: 缺 root → MISSING_ROOT",
                SchemaViolationException.MISSING_ROOT.equals(noRoot));

        String unknownRoot = violationReasonOf(
                "[\n  root = \"bogus\",\n]");
        check("违约: 未知 kind(root) → UNKNOWN_KIND",
                SchemaViolationException.UNKNOWN_KIND.equals(unknownRoot));

        String unknownFieldKind = violationReasonOf(
                "[\n  root = \"table\",\n  fields = [\n    name = \"widget\",\n  ],\n]");
        check("违约: 字段未知 kind → FIELD_KIND",
                SchemaViolationException.FIELD_KIND.equals(unknownFieldKind));

        String dup = violationReasonOf(DUP_SRC);
        check("违约: 重复字段 → DUPLICATE_FIELD",
                SchemaViolationException.DUPLICATE_FIELD.equals(dup));

        // ================= 3. validate 确定性判定 / deterministic validate verdicts =================
        TdTable validTable = TdTable.builder()
                .put("name", TdValue.str("alpha"))
                .put("level", TdValue.of(7L))
                .put("enabled", TdValue.of(true))
                .put("stats", TdTable.builder().put("hp", TdValue.of(10L)).build())
                .build();
        SchemaReport okReport = table.validate(validTable);
        check("validate: 合法 TABLE 数据 → valid 且 reason 空",
                okReport.valid() && okReport.reason().isEmpty() && okReport.fieldPath().isEmpty());

        TdTable badType = TdTable.builder()
                .put("name", TdValue.str("alpha"))
                .put("level", TdValue.str("oops"))
                .put("enabled", TdValue.of(true))
                .put("stats", TdTable.builder().put("hp", TdValue.of(1L)).build())
                .build();
        SchemaReport typeR = table.validate(badType);
        check("validate: 字段类型不符 → invalid 且 reason 含 type mismatch",
                !typeR.valid() && typeR.reason().contains("type mismatch") && "level".equals(typeR.fieldPath()));

        TdTable missing = TdTable.builder()
                .put("name", TdValue.str("alpha"))
                .put("level", TdValue.of(7L))
                // enabled deliberately absent
                .put("stats", TdTable.builder().put("hp", TdValue.of(1L)).build())
                .build();
        SchemaReport missR = table.validate(missing);
        check("validate: 缺字段 → invalid 且 reason=='missing field'",
                !missR.valid() && "missing field".equals(missR.reason()) && "enabled".equals(missR.fieldPath()));

        TdTable unknown = TdTable.builder()
                .put("name", TdValue.str("alpha"))
                .put("level", TdValue.of(7L))
                .put("enabled", TdValue.of(true))
                .put("stats", TdTable.builder().build())
                .put("intruder", TdValue.of(1L))
                .build();
        SchemaReport unkR = table.validate(unknown);
        check("validate: 未知字段 → invalid 且 reason=='unknown field'",
                !unkR.valid() && "unknown field".equals(unkR.reason()) && "intruder".equals(unkR.fieldPath()));

        TdTable listOk = TdTable.builder().element(TdValue.str("x")).element(TdValue.str("y")).build();
        check("validate: LIST 合法元素 → valid",
                list.validate(listOk).valid());

        TdTable listBad = TdTable.builder().element(TdValue.str("x")).element(TdValue.of(5L)).build();
        SchemaReport listR = list.validate(listBad);
        check("validate: LIST 元素类型不符 → invalid 且 reason 含 type mismatch、path==element[1]",
                !listR.valid() && listR.reason().contains("type mismatch") && "element[1]".equals(listR.fieldPath()));

        TdTable scalarData = TdTable.builder().put("irrelevant", TdValue.of(1L)).build();
        check("validate: 标量根始终通过",
                scalar.validate(scalarData).valid());

        // ================= 4. 三生成器确定性 / generator determinism =================
        String reg1 = RegisterCodeGen.generate(table);
        String reg2 = RegisterCodeGen.generate(table);
        check("RegisterCodeGen: 同 schema 两次生成逐字节一致", reg1.equals(reg2));
        check("RegisterCodeGen: 往返稳定 gen(parse(toTd(s)))==gen(s)",
                RegisterCodeGen.generate(SchemaCodec.parse(SchemaCodec.toTd(table))).equals(reg1));
        check("RegisterCodeGen: TABLE 形态含 register(", reg1.contains("register("));
        check("RegisterCodeGen: LIST 形态含 registerListOf(",
                RegisterCodeGen.generate(list).contains("registerListOf("));
        check("RegisterCodeGen: 标量形态含 registerScalar=",
                RegisterCodeGen.generate(scalar).contains("registerScalar="));

        String stub1 = SchemaStubGen.generateProbeStub(table);
        String stub2 = SchemaStubGen.generateProbeStub(table);
        check("SchemaStubGen.probeStub: 同 schema 两次生成逐字节一致", stub1.equals(stub2));
        check("SchemaStubGen.probeStub: 往返稳定 gen(parse(toTd(s)))==gen(s)",
                SchemaStubGen.generateProbeStub(SchemaCodec.parse(SchemaCodec.toTd(table))).equals(stub1));
        check("SchemaStubGen.probeStub: TABLE 形态含 check(", stub1.contains("check("));

        String doc1 = SchemaStubGen.generateDoc(table);
        String doc2 = SchemaStubGen.generateDoc(table);
        check("SchemaStubGen.doc: 同 schema 两次生成逐字节一致", doc1.equals(doc2));
        check("SchemaStubGen.doc: 往返稳定 gen(parse(toTd(s)))==gen(s)",
                SchemaStubGen.generateDoc(SchemaCodec.parse(SchemaCodec.toTd(table))).equals(doc1));
        check("SchemaStubGen.doc: TABLE 文档行含 '| '", doc1.contains("| "));

        String form1 = FormDataGen.generateFormData(table);
        String form2 = FormDataGen.generateFormData(table);
        check("FormDataGen: 同 schema 两次生成逐字节一致", form1.equals(form2));
        check("FormDataGen: 往返稳定 gen(parse(toTd(s)))==gen(s)",
                FormDataGen.generateFormData(SchemaCodec.parse(SchemaCodec.toTd(table))).equals(form1));
        check("FormDataGen: TABLE 形态含 'field ' 且 widget 映射 check",
                form1.contains("field ") && form1.contains("enabled kind bool widget check"));

        // ================= 5. generate(String) 贯通 / generate(String) passthrough =================
        check("RegisterCodeGen.generate(String): 与 parse+generate 一致",
                RegisterCodeGen.generate(TABLE_SRC).equals(RegisterCodeGen.generate(SchemaCodec.parse(TABLE_SRC))));
    }

    /** 文档序字段名列表。Document-order field names. */
    private static java.util.List<String> fieldsNames(Schema s) {
        return s.fields().stream().map(SchemaField::name).toList();
    }

    /** 文档序字段种类列表。Document-order field kinds. */
    private static java.util.List<SchemaKind> fieldsKinds(Schema s) {
        return s.fields().stream().map(SchemaField::kind).toList();
    }
}