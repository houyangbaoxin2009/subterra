package io.toterra.subterra.engine.scaffold;

import io.toterra.subterra.engine.schema.Schema;
import io.toterra.subterra.engine.schema.SchemaCodec;
import io.toterra.subterra.engine.schema.SchemaField;
import io.toterra.subterra.engine.schema.SchemaKind;
import io.toterra.subterra.engine.schema.SchemaViolationException;

import java.util.Objects;

/**
 * p.2.12.2 注册表接线文本生成器 —— 由定型 {@code Schema} 确定性生成「注册表接线」纯文本骨架。纯 JDK，
 * 只复用 {@code engine.schema.*}，不依赖 p.2.12.3/.4 的后续同包文件，也不实际注册任何东西：输出是后续注册
 * 机制（如 SaveSlot / ExportHub / 注册表 put 行）的语义说明与骨架，本子项仅产出文本。
 * <p>
 * <b>确定性契约（Determinism contract，写死在本 Javadoc）：</b>
 * <ol>
 *   <li>同一 {@code Schema} 两次生成逐字节一致（fixed field order + fixed header，禁忌未时间戳/随机/时序）。</li>
 *   <li>往返稳定：{@code generate(parse(toTd(schema))) == generate(schema)}（两条 {@code generate(Schema)}
 *       路径；p.2.12.1 的编解码保序往返，本生成器只依赖 {@code root}/{@code listOf}/文档序 {@code fields}，
 *       因此即使 codec 把 {@code name} 往返成 {@code ""} 也不影响输出）。</li>
 *   <li>schema 名与字段名一律按外部输入{@code 原样照抄}，不做转义 / 规范化之外的处理（唯一例外：TABLE 根下
 *       空字段名作为可注册性违约抛 {@link GenViolationException#EMPTY_NAME}）。</li>
 * </ol>
 * 形契约（per-root shape）：
 * <ul>
 *   <li><b>TABLE 根：</b>固定头部后按 {@code fields} 文档序每字段一行
 *       {@code register("<name>", <kind>)}；{@code <kind>} 为 {@code SchemaKind} 的规范名
 *       （{@code string}/{@code int}/{@code float}/{@code bool}/{@code table}/{@code list}）。</li>
 *   <li><b>LIST 根：</b>单行 {@code registerListOf(<kind>)}，{@code <kind>} 为
 *       {@code schema.listOf()} 的规范名。</li>
 *   <li><b>标量根：</b>单行 {@code registerScalar=<kind>}，{@code <kind>} 为 {@code schema.root()} 的规范名。</li>
 * </ul>
 * 输出头部为固定文本（注明「由 schema 生成，改文件会被重新生成覆盖」），绝不内嵌 schema 名或时间戳，正是为了
 * 保证上述往返稳定性。
 * <p>
 * p.2.12.2 registration-wiring generator — deterministically derives a plain-text
 * {@code registration-wiring} skeleton from a settled {@code Schema}. Pure JDK. It reuses only
 * {@code engine.schema.*}, does not depend on the later same-package files of p.2.12.3/.4, and registers
 * nothing itself: the output is a semantic specification / skeleton for the downstream registration
 * mechanism (e.g. SaveSlot / ExportHub / registry {@code put} lines), and nothing is actually wired here.
 * <p>
 * <b>Determinism contract (written into this Javadoc):</b>
 * <ol>
 *   <li>Two generations for the same {@code Schema} are byte-identical (fixed field order + fixed header;
 *       no timestamp / random / timing).</li>
 *   <li>Round-trip stable: {@code generate(parse(toTd(schema))) == generate(schema)} (both are
 *       {@code generate(Schema)} calls; p.2.12.1's codec round-trips order, and this generator depends only on
 *       {@code root}/{@code listOf}/document-order {@code fields}, so the codec resetting {@code name} to
 *       {@code ""} does not affect output).</li>
 *   <li>The schema name and field names are copied verbatim as external input — no escaping or
 *       normalization beyond the deterministic rules (the sole exception: an empty field name under a TABLE
 *       root is a registrability violation raised as {@link GenViolationException#EMPTY_NAME}).</li>
 * </ol>
 * Per-root shape:
 * <ul>
 *   <li><b>TABLE root:</b> after a fixed header, one line per field in {@code fields} document order,
 *       {@code register("<name>", <kind>)}; {@code <kind>} is the {@code SchemaKind} canonical name
 *       ({@code string}/{@code int}/{@code float}/{@code bool}/{@code table}/{@code list}).</li>
 *   <li><b>LIST root:</b> a single line {@code registerListOf(<kind>)}, {@code <kind>} being the canonical
 *       name of {@code schema.listOf()}.</li>
 *   <li><b>Scalar root:</b> a single line {@code registerScalar=<kind>}, {@code <kind>} being the canonical
 *       name of {@code schema.root()}.</li>
 * </ul>
 * The header is fixed text (noting that the file is generated from the schema and will be overwritten on
 * regeneration), never embeds the schema name or a timestamp — precisely to preserve the round-trip
 * stability above.
 */
public final class RegisterCodeGen {

    /** 固定头部第 1 行。Fixed header, line 1. */
    private static final String HEADER_LINE_1 =
            "// p.2.12.2 RegisterCodeGen — registration wiring, deterministically generated from a td schema.";
    /** 固定头部第 2 行：由 schema 生成，改文件会被重新生成覆盖。Fixed header, line 2: generated; edits overwritten. */
    private static final String HEADER_LINE_2 =
            "// 由 schema 生成，改文件会被重新生成覆盖 — auto-generated; manual edits will be overwritten.";

    private RegisterCodeGen() {
    }

    /**
     * 从 td 文本便捷生成（parse + generate）。文档级违约（未知 kind / 缺 root / 字段 kind / 重复字段）在
     * parse 层作为受检 {@link SchemaViolationException} 传播；解析成功后的生成层违约
     * （TABLE 根空字段名）抛 {@link GenViolationException}。
     * <p>
     * Convenience entry from td text ({@code parse + generate}). Document-level violations (unknown kind /
     * missing root / field kind / duplicate field) propagate as the checked {@link SchemaViolationException}
     * at the parse layer; a generation-layer violation after a successful parse (an empty TABLE-root field
     * name) raises {@link GenViolationException}.
     *
     * @param tdSource schema td 文本 / the schema td text.
     * @return 确定性接线文本 / the deterministic wiring text.
     * @throws SchemaViolationException 文档级违约 / a document-level violation.
     * @throws GenViolationException   生成层违约（TABLE 根空字段名）/ a generation-layer violation (empty TABLE-root field name).
     */
    public static String generate(String tdSource) throws SchemaViolationException, GenViolationException {
        Objects.requireNonNull(tdSource, "schema td source must not be null");
        return generate(SchemaCodec.parse(tdSource));
    }

    /**
     * 由定型 {@code Schema} 确定性生成接线文本。见类 Javadoc 的确定性契约与形契约；对首选入口
     * （{@code generate(Schema)}）保证往返稳定 {@code generate(parse(toTd(schema))) == generate(schema)}。
     * 非 O(n²)：TABLE 根一次 {@code fields} 线性扫描拼行。
     * <p>
     * Deterministically generates the wiring text from a settled {@code Schema}. See the class Javadoc for
     * the determinism and per-root contracts; for the primary entry ({@code generate(Schema)}) the round-trip
     * is guaranteed: {@code generate(parse(toTd(schema))) == generate(schema)}. Not O(n²): a TABLE root does a
     * single linear scan over {@code fields}.
     *
     * @param schema 定型 schema / the settled schema.
     * @return 确定性接线文本（行尾 {@code \n}）/ the deterministic wiring text (lines end in {@code \n}).
     * @throws GenViolationException TABLE 根下空字段名 / an empty field name under a TABLE root.
     */
    public static String generate(Schema schema) throws GenViolationException {
        Objects.requireNonNull(schema, "schema must not be null");
        StringBuilder sb = new StringBuilder();
        sb.append(HEADER_LINE_1).append('\n');
        sb.append(HEADER_LINE_2).append('\n');
        switch (schema.root()) {
            case TABLE -> appendTable(sb, schema);
            case LIST -> sb.append("registerListOf(").append(schema.listOf().tdName()).append(")\n");
            default -> sb.append("registerScalar=").append(schema.root().tdName()).append('\n');
        }
        return sb.toString();
    }

    private static void appendTable(StringBuilder sb, Schema schema) throws GenViolationException {
        for (SchemaField f : schema.fields()) {
            if (f.name() == null || f.name().isEmpty()) {
                throw new GenViolationException(GenViolationException.EMPTY_NAME, "field name is empty");
            }
            sb.append("register(\"").append(f.name()).append('"')
                    .append(", ").append(f.kind().tdName()).append(")\n");
        }
    }
}