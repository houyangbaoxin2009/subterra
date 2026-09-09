package io.toterra.subterra.engine.scaffold;

import io.toterra.subterra.engine.schema.Schema;
import io.toterra.subterra.engine.schema.SchemaCodec;
import io.toterra.subterra.engine.schema.SchemaField;
import io.toterra.subterra.engine.schema.SchemaKind;
import io.toterra.subterra.engine.schema.SchemaViolationException;

import java.util.Objects;

/**
 * p.2.12.3 探针与开发文档生成器 —— 由定型 {@code Schema} 确定性生成「探针代码骨架」与「开发文档」两种纯文本。
 * 纯 JDK，只复用 {@code engine.schema.*}，不依赖 p.2.12.2 的 {@link RegisterCodeGen}（各生成器独立）。
 * <p>
 * <b>确定性契约（Determinism contract，写死在本 Javadoc）：</b>
 * <ol>
 *   <li>同一 {@code Schema} 两次生成逐字节一致（fixed field order + fixed header，禁忌时间戳/随机/时序；
 *       输出头为固定文本，绝不内嵌 schema 名或时间戳）。</li>
 *   <li>往返稳定：{@code generateX(parse(toTd(schema))) == generateX(schema)}（两条 {@code generateX(Schema)}
 *       路径；p.2.12.1 的编解码保序往返，本生成器只依赖 {@code root}/{@code listOf}/文档序 {@code fields}，
 *       因此即使 codec 把 {@code name} 往返成 {@code ""} 也不影响输出）。</li>
 *   <li>字段名一律按外部输入{@code 原样照抄}，不做转义 / 规范化之外的处理；kind 用 {@code SchemaKind.tdName()}
 *       规范名。</li>
 * </ol>
 * 形契约（per-root shape）：
 * <ul>
 *   <li><b>TABLE 根：</b>固定头部后按 {@code fields} 文档序每字段一行兼容表单的
 *       {@code check("<name>", "<kind>")} / 文档表格行 {@code | <name> | <kind> |}。</li>
 *   <li><b>LIST 根：</b>探针单行 {@code checkListOf("<kind>")} / 文档单行说明，
 *       {@code <kind>} 为 {@code schema.listOf()} 的规范名。</li>
 *   <li><b>标量根：</b>探针单行 {@code checkScalar=<kind>} / 文档单行说明，
 *       {@code <kind>} 为 {@code schema.root()} 的规范名。</li>
 * </ul>
 * 非 O(n²)：TABLE 根一次 {@code fields} 线性扫描拼行。
 * <p>
 * p.2.12.3 probe &amp; dev-doc generator — deterministically derives two kinds of plain text from a settled
 * {@code Schema}: a {@code probe-code skeleton} and {@code developer documentation}. Pure JDK. It reuses only
 * {@code engine.schema.*} and does not depend on p.2.12.2's {@link RegisterCodeGen} (each generator stands alone).
 * <p>
 * <b>Determinism contract (written into this Javadoc):</b>
 * <ol>
 *   <li>Two generations from the same {@code Schema} are byte-identical (fixed field order + fixed header;
 *       no timestamp / random / timing; the output header is fixed text, never embedding the schema name or a
 *       timestamp).</li>
 *   <li>Round-trip stable: {@code generateX(parse(toTd(schema))) == generateX(schema)} (both are
 *       {@code generateX(Schema)} calls; p.2.12.1's codec round-trips order, and this generator depends only on
 *       {@code root}/{@code listOf}/document-order {@code fields}, so the codec resetting {@code name} to
 *       {@code ""} does not affect output).</li>
 *   <li>Field names are copied verbatim as external input — no escaping or normalization beyond the
 *       deterministic rules; kinds use the {@code SchemaKind.tdName()} canonical names.</li>
 * </ol>
 * Per-root shape:
 * <ul>
 *   <li><b>TABLE root:</b> after a fixed header, one line per field in {@code fields} document order —
 *       probe {@code check("<name>", "<kind>")} / doc table row {@code | <name> | <kind> |}.</li>
 *   <li><b>LIST root:</b> a single probe line {@code checkListOf("<kind>")} / a single doc line, {@code <kind>}
 *       being the canonical name of {@code schema.listOf()}.</li>
 *   <li><b>Scalar root:</b> a single probe line {@code checkScalar=<kind>} / a single doc line, {@code <kind>}
 *       being the canonical name of {@code schema.root()}.</li>
 * </ul>
 * Not O(n²): a TABLE root does a single linear scan over {@code fields}.
 */
public final class SchemaStubGen {

    /** 探针固定头部第 1 行。Fixed probe header, line 1. */
    private static final String PROBE_HEADER_LINE_1 =
            "// SchemaStubGen — probe skeleton, deterministically generated from a td schema. 由 schema 生成。";
    /** 探针固定头部第 2 行：由 schema 生成，改文件会被重新生成覆盖。Fixed probe header, line 2: generated; edits overwritten. */
    private static final String PROBE_HEADER_LINE_2 =
            "// 本文件由 schema 生成，重新生成会被覆盖 — auto-generated; manual edits will be overwritten.";

    /** 文档固定标题第 1 行。Fixed doc title, line 1. */
    private static final String DOC_TITLE_LINE_1 =
            "# SchemaStubGen — developer documentation, deterministically generated from a td schema. 由 schema 生成。";
    /** 文档固定标题第 2 行：由 schema 生成，改文件会被重新生成覆盖。Fixed doc title, line 2: generated; edits overwritten. */
    private static final String DOC_TITLE_LINE_2 =
            "# 本文件由 schema 生成，重新生成会被覆盖 — auto-generated; manual edits will be overwritten.";

    private SchemaStubGen() {
    }

    /**
     * 便捷入口：从 td 文本生成探针骨架（parse + generateProbeStub）。文档级违约（未知 kind / 缺 root /
     * 字段 kind / 重复字段）在 parse 层作为受检 {@link SchemaViolationException} 传播。
     * <p>
     * Convenience entry: probe skeleton from td text ({@code parse + generateProbeStub}). Document-level
     * violations (unknown kind / missing root / field kind / duplicate field) propagate as the checked
     * {@link SchemaViolationException} at the parse layer.
     *
     * @param tdSource schema td 文本 / the schema td text.
     * @return 确定性探针骨架文本 / the deterministic probe-skeleton text.
     * @throws SchemaViolationException 文档级违约 / a document-level violation.
     */
    public static String generateProbeStub(String tdSource) throws SchemaViolationException {
        Objects.requireNonNull(tdSource, "schema td source must not be null");
        return generateProbeStub(SchemaCodec.parse(tdSource));
    }

    /**
     * 由定型 {@code Schema} 确定性生成「探针代码骨架」纯文本。见类 Javadoc 的确定性契约与形契约；对首选入口
     * （{@code generateProbeStub(Schema)}）保证往返稳定
     * {@code generateProbeStub(parse(toTd(schema))) == generateProbeStub(schema)}。TABLE 根按字段文档序每字段
     * 一行 {@code check("<name>", "<kind>")}；LIST 根单行 {@code checkListOf("<kind>")}；标量根单行
     * {@code checkScalar=<kind>}；kind 用 {@code SchemaKind.tdName()} 规范名，字段名原样照抄。
     * <p>
     * Deterministically generates a plain-text {@code probe-code skeleton} from a settled {@code Schema}. See the
     * class Javadoc for the determinism and per-root contracts; for the primary entry
     * ({@code generateProbeStub(Schema)}) the round-trip is guaranteed:
     * {@code generateProbeStub(parse(toTd(schema))) == generateProbeStub(schema)}. A TABLE root emits, per field
     * in document order, one line {@code check("<name>", "<kind>")}; a LIST root emits one line
     * {@code checkListOf("<kind>")}; a scalar root emits one line {@code checkScalar=<kind>}; kinds use the
     * {@code SchemaKind.tdName()} canonical name and field names are copied verbatim.
     *
     * @param schema 定型 schema / the settled schema.
     * @return 确定性探针骨架文本（行尾 {@code \n}）/ the deterministic probe-skeleton text (lines end in {@code \n}).
     */
    public static String generateProbeStub(Schema schema) {
        Objects.requireNonNull(schema, "schema must not be null");
        StringBuilder sb = new StringBuilder();
        sb.append(PROBE_HEADER_LINE_1).append('\n');
        sb.append(PROBE_HEADER_LINE_2).append('\n');
        switch (schema.root()) {
            case TABLE -> {
                for (SchemaField f : schema.fields()) {
                    sb.append("check(\"").append(f.name()).append("\", \"")
                            .append(f.kind().tdName()).append("\")\n");
                }
            }
            case LIST -> sb.append("checkListOf(\"").append(schema.listOf().tdName()).append("\")\n");
            default -> sb.append("checkScalar=").append(schema.root().tdName()).append('\n');
        }
        return sb.toString();
    }

    /**
     * 便捷入口：从 td 文本生成开发文档（parse + generateDoc）。文档级违约（未知 kind / 缺 root / 字段 kind /
     * 重复字段）在 parse 层作为受检 {@link SchemaViolationException} 传播。
     * <p>
     * Convenience entry: developer documentation from td text ({@code parse + generateDoc}). Document-level
     * violations (unknown kind / missing root / field kind / duplicate field) propagate as the checked
     * {@link SchemaViolationException} at the parse layer.
     *
     * @param tdSource schema td 文本 / the schema td text.
     * @return 确定性开发文档文本 / the deterministic dev-doc text.
     * @throws SchemaViolationException 文档级违约 / a document-level violation.
     */
    public static String generateDoc(String tdSource) throws SchemaViolationException {
        Objects.requireNonNull(tdSource, "schema td source must not be null");
        return generateDoc(SchemaCodec.parse(tdSource));
    }

    /**
     * 由定型 {@code Schema} 确定性生成「开发文档」纯文本。固定标题头（不内嵌 schema 名或时间戳）+ 字段表格行：
     * TABLE 根每字段一行 {@code | <name> | <kind> |}；LIST 根单行说明；标量根单行说明。见类 Javadoc 的确定性
     * 契约与形契约；对首选入口（{@code generateDoc(Schema)}）保证往返稳定
     * {@code generateDoc(parse(toTd(schema))) == generateDoc(schema)}。
     * <p>
     * Deterministically generates plain-text {@code developer documentation} from a settled {@code Schema}. A
     * fixed title header (no schema name or timestamp embedded) is followed by a field table: a TABLE root emits
     * one row {@code | <name> | <kind> |} per field; a LIST root and a scalar root each emit a single-line
     * description. See the class Javadoc for the determinism and per-root contracts; for the primary entry
     * ({@code generateDoc(Schema)}) the round-trip is guaranteed:
     * {@code generateDoc(parse(toTd(schema))) == generateDoc(schema)}.
     *
     * @param schema 定型 schema / the settled schema.
     * @return 确定性开发文档文本（行尾 {@code \n}）/ the deterministic dev-doc text (lines end in {@code \n}).
     */
    public static String generateDoc(Schema schema) {
        Objects.requireNonNull(schema, "schema must not be null");
        StringBuilder sb = new StringBuilder();
        sb.append(DOC_TITLE_LINE_1).append('\n');
        sb.append(DOC_TITLE_LINE_2).append('\n');
        switch (schema.root()) {
            case TABLE -> {
                for (SchemaField f : schema.fields()) {
                    sb.append("| ").append(f.name()).append(" | ").append(f.kind().tdName()).append(" |\n");
                }
            }
            case LIST -> sb.append("list of <").append(schema.listOf().tdName()).append("> (auto-generated dev doc — list-of-kind)\n");
            default -> sb.append("scalar <").append(schema.root().tdName()).append("> (auto-generated dev doc — scalar root)\n");
        }
        return sb.toString();
    }
}