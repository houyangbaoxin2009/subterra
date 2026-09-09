package io.toterra.subterra.engine.scaffold;

import io.toterra.subterra.engine.schema.Schema;
import io.toterra.subterra.engine.schema.SchemaCodec;
import io.toterra.subterra.engine.schema.SchemaField;
import io.toterra.subterra.engine.schema.SchemaKind;
import io.toterra.subterra.engine.schema.SchemaViolationException;

import java.util.Objects;

/**
 * p.2.12.4 表单结构 / 渲染数据生成器 —— 由定型 {@code Schema} 确定性生成「表单结构 / 渲染数据」纯文本。
 * 纯 JDK，只复用 {@code engine.schema.*}，不依赖 p.2.12.2 的 {@link RegisterCodeGen} / p.2.12.3 的
 * {@link SchemaStubGen}（各生成器独立）。本子项仅产出确定性纯数据（表单结构 / 控件提示），
 * <b>不接任何 MC UI</b>：游戏内 Hub 表单属 p.2.23 之后置（届时另文对接）。
 * <p>
 * <b>确定性契约（Determinism contract，写死在本 Javadoc）：</b>
 * <ol>
 *   <li>同一 {@code Schema} 两次生成逐字节一致（fixed field order + fixed header，禁忌时间戳/随机/时序；
 *       输出头为固定文本，绝不内嵌 schema 名或时间戳）。</li>
 *   <li>往返稳定：{@code generateFormData(parse(toTd(schema))) == generateFormData(schema)}
 *       （两条 {@code generateFormData(Schema)} 路径；p.2.12.1 的编解码保序往返，本生成器只依赖
 *       {@code root}/{@code listOf}/文档序 {@code fields}，因此即使 codec 把 {@code name} 往返成 {@code ""}
 *       也不影响输出）。</li>
 *   <li>字段名一律按外部输入{@code 原样照抄}，不做转义 / 规范化之外的处理；kind 用 {@code SchemaKind.tdName()}
 *       规范名。</li>
 * </ol>
 * <b>widget 固定映射（fixed widget-hint map，写死在本 Javadoc）：</b>经 {@code SchemaKind} 指向对应的
 * {@code widgetHint} 固定文本：
 * <ul>
 *   <li>string → {@code text}（单行文本输入）</li>
 *   <li>int → {@code number}（数字输入）</li>
 *   <li>float → {@code number}（数字输入）</li>
 *   <li>bool → {@code check}（勾选开关）</li>
 *   <li>table → {@code group}（字段分组）</li>
 *   <li>list → {@code repeat}（可重复列表）</li>
 * </ul>
 * 形契约（per-root shape）：
 * <ul>
 *   <li><b>TABLE 根：</b>固定头部后按 {@code fields} 文档序每字段一行
 *       {@code field <name> kind <kind> widget <widgetHint>}。</li>
 *   <li><b>LIST 根：</b>单行 {@code field[] kind <kind> widget repeat}，{@code <kind>} 为
 *       {@code schema.listOf()} 的规范名。</li>
 *   <li><b>标量根：</b>单行 {@code field kind <kind> widget <widgetHint>}，{@code <kind>} 为
 *       {@code schema.root()} 的规范名。</li>
 * </ul>
 * 非 O(n²)：TABLE 根一次 {@code fields} 线性扫描拼行。
 * <p>
 * p.2.12.4 form-structure / render-data generator — deterministically derives a plain-text
 * {@code form structure / render data} from a settled {@code Schema}. Pure JDK. It reuses only
 * {@code engine.schema.*} and does not depend on p.2.12.2's {@link RegisterCodeGen} nor p.2.12.3's
 * {@link SchemaStubGen} (each generator stands alone). This sub-item only emits deterministic pure data
 * (form structure / widget hints) and <b>connects no MC UI</b>: the in-game Hub form is a post-p.2.23
 * concern (to be wired there).
 * <p>
 * <b>Determinism contract (written into this Javadoc):</b>
 * <ol>
 *   <li>Two generations from the same {@code Schema} are byte-identical (fixed field order + fixed header;
 *       no timestamp / random / timing; the output header is fixed text, never embedding the schema name or a
 *       timestamp).</li>
 *   <li>Round-trip stable: {@code generateFormData(parse(toTd(schema))) == generateFormData(schema)} (both
 *       are {@code generateFormData(Schema)} calls; p.2.12.1's codec round-trips order, and this generator
 *       depends only on {@code root}/{@code listOf}/document-order {@code fields}, so the codec resetting
 *       {@code name} to {@code ""} does not affect output).</li>
 *   <li>Field names are copied verbatim as external input — no escaping or normalization beyond the
 *       deterministic rules; kinds use the {@code SchemaKind.tdName()} canonical names.</li>
 * </ol>
 * <b>Fixed widget-hint map (written into this Javadoc):</b> via {@code SchemaKind} to a fixed
 * {@code widgetHint} text:
 * <ul>
 *   <li>string → {@code text} (single-line text input)</li>
 *   <li>int → {@code number} (numeric input)</li>
 *   <li>float → {@code number} (numeric input)</li>
 *   <li>bool → {@code check} (checkbox toggle)</li>
 *   <li>table → {@code group} (field group)</li>
 *   <li>list → {@code repeat} (repeatable list)</li>
 * </ul>
 * Per-root shape:
 * <ul>
 *   <li><b>TABLE root:</b> after a fixed header, one line per field in {@code fields} document order,
 *       {@code field <name> kind <kind> widget <widgetHint>}.</li>
 *   <li><b>LIST root:</b> a single line {@code field[] kind <kind> widget repeat}, {@code <kind>} being the
 *       canonical name of {@code schema.listOf()}.</li>
 *   <li><b>Scalar root:</b> a single line {@code field kind <kind> widget <widgetHint>}, {@code <kind>} being
 *       the canonical name of {@code schema.root()}.</li>
 * </ul>
 * Not O(n²): a TABLE root does a single linear scan over {@code fields}.
 */
public final class FormDataGen {

    /** 固定头部第 1 行。Fixed header, line 1. */
    private static final String HEADER_LINE_1 =
            "// FormDataGen — form structure / render data, deterministically generated from a td schema.";
    /** 固定头部第 2 行。Fixed header, line 2. */
    private static final String HEADER_LINE_2 =
            "// 由 schema 生成，p.2.23 前仅纯数据（不接任意 MC UI）— auto-generated; pure data only until p.2.23 (no MC UI wired).";

    private FormDataGen() {
    }

    /**
     * 便捷入口：从 td 文本生成表单结构 / 渲染数据（parse + generateFormData）。文档级违约（未知 kind / 缺 root /
     * 字段 kind / 重复字段）在 parse 层作为受检 {@link SchemaViolationException} 传播。
     * <p>
     * Convenience entry: form structure / render data from td text ({@code parse + generateFormData}).
     * Document-level violations (unknown kind / missing root / field kind / duplicate field) propagate as the
     * checked {@link SchemaViolationException} at the parse layer.
     *
     * @param tdSource schema td 文本 / the schema td text.
     * @return 确定性表单结构文本 / the deterministic form-structure text.
     * @throws SchemaViolationException 文档级违约 / a document-level violation.
     */
    public static String generateFormData(String tdSource) throws SchemaViolationException {
        Objects.requireNonNull(tdSource, "schema td source must not be null");
        return generateFormData(SchemaCodec.parse(tdSource));
    }

    /**
     * 由定型 {@code Schema} 确定性生成「表单结构 / 渲染数据」纯文本。见类 Javadoc 的确定性契约、widget 映射与
     * 形契约；对首选入口（{@code generateFormData(Schema)}）保证往返稳定
     * {@code generateFormData(parse(toTd(schema))) == generateFormData(schema)}。TABLE 根按字段文档序每字段
     * 一行 {@code field <name> kind <kind> widget <widgetHint>}；LIST 根单行
     * {@code field[] kind <kind> widget repeat}；标量根单行 {@code field kind <kind> widget <widgetHint>}；
     * kind 用 {@code SchemaKind.tdName()} 规范名，widgetHint 用类 Javadoc 的固定映射，字段名原样照抄。
     * <p>
     * Deterministically generates a plain-text {@code form structure / render data} from a settled
     * {@code Schema}. See the class Javadoc for the determinism contract, the widget map, and the per-root
     * shapes; for the primary entry ({@code generateFormData(Schema)}) the round-trip is guaranteed:
     * {@code generateFormData(parse(toTd(schema))) == generateFormData(schema)}. A TABLE root emits, per field
     * in document order, one line {@code field <name> kind <kind> widget <widgetHint>}; a LIST root emits one
     * line {@code field[] kind <kind> widget repeat}; a scalar root emits one line
     * {@code field kind <kind> widget <widgetHint>}; kinds use the {@code SchemaKind.tdName()} canonical name,
     * widget hints use the fixed map from the class Javadoc, and field names are copied verbatim.
     *
     * @param schema 定型 schema / the settled schema.
     * @return 确定性表单结构文本（行尾 {@code \n}）/ the deterministic form-structure text (lines end in {@code \n}).
     */
    public static String generateFormData(Schema schema) {
        Objects.requireNonNull(schema, "schema must not be null");
        StringBuilder sb = new StringBuilder();
        sb.append(HEADER_LINE_1).append('\n');
        sb.append(HEADER_LINE_2).append('\n');
        switch (schema.root()) {
            case TABLE -> {
                for (SchemaField f : schema.fields()) {
                    sb.append("field ").append(f.name())
                            .append(" kind ").append(f.kind().tdName())
                            .append(" widget ").append(widgetHint(f.kind())).append('\n');
                }
            }
            case LIST ->
                    sb.append("field[] kind ").append(schema.listOf().tdName()).append(" widget repeat\n");
            default ->
                    sb.append("field kind ").append(schema.root().tdName())
                            .append(" widget ").append(widgetHint(schema.root())).append('\n');
        }
        return sb.toString();
    }

    /** kind → widgetHint 固定映射；TABLE 根字段不会出现，但仍按契约提供。Fixed kind→widgetHint map. */
    private static String widgetHint(SchemaKind kind) {
        return switch (kind) {
            case STRING -> "text";
            case INT, FLOAT -> "number";
            case BOOL -> "check";
            case TABLE -> "group";
            case LIST -> "repeat";
        };
    }
}