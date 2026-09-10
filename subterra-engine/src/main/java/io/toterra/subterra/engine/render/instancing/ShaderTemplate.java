package io.toterra.subterra.engine.render.instancing;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Deterministic GLSL shader template model (p.2.27.1.1). A template is assembled from
 * ordered parts — {@code headerLines}, a single {@code body}, {@code footerLines} —
 * and compiled into canonical GLSL source text. Assembly is fixed-order:
 * {@link #fromParts(List, String, List)} joins the header lines in order (each
 * terminated by {@code '\n'}), then the body verbatim, then the footer lines in order
 * (each terminated by {@code '\n'}), guaranteeing a single trailing newline. Named
 * placeholders use the deterministic {@code {{name}}} token syntax
 * ({@code [A-Za-z0-9_]} names); substitution via {@link #substituteAll(Map)} runs in
 * the fixed first-occurrence order of {@link #placeholderNames()} and replaces every
 * occurrence of each placeholder, so identical inputs always produce byte-identical
 * {@link #compile()} output (same input, same bytes). Missing values and unresolved
 * leftovers fail loudly with {@link IllegalArgumentException} — deterministic
 * failure, never silent variation. Clean-room simplified model: the Flywheel 1.0.x
 * shader-pipeline sources (backend source set) could not be fully retrieved online,
 * so this class is original Subterra code modeled on the task shape, and does not
 * claim to be a direct inclusion (see
 * {@code META-INF/third-party/flywheel-1.0.6/NOTICE.md}). Pure JDK; no MC, no OpenGL.
 *
 * <p>确定性 GLSL 着色器模板模型（p.2.27.1.1）。模板由有序部件拼装——{@code headerLines}、
 * 单个 {@code body}、{@code footerLines}——并编译为规范 GLSL 源文本。拼装固定序：
 * {@link #fromParts(List, String, List)} 按序拼接头行（每行以 {@code '\n'} 结尾）、
 * 原样拼接 body、按序拼接尾行（每行以 {@code '\n'} 结尾），并保证单个结尾换行。命名占位符采用
 * 确定性 {@code {{name}}} 词法（名字取 {@code [A-Za-z0-9_]}）；{@link #substituteAll(Map)}
 * 按 {@link #placeholderNames()} 的首次出现固定序执行替换，且替换每个占位符的全部出现，因此相同
 * 输入恒产生逐字节一致的 {@link #compile()} 输出（同输入、同字节）。缺失值与未消解残留以
 * {@link IllegalArgumentException} 显式失败——确定性失败，绝不静默变化。clean-room 简化模型：
 * Flywheel 1.0.x 着色器管线源码（backend source set）未能在线上完整取得，本类为按任务形态原创的
 * Subterra 代码，不宣称直接包含（见 {@code META-INF/third-party/flywheel-1.0.6/NOTICE.md}）。
 * 纯 JDK；无 MC、无 OpenGL。
 */
public final class ShaderTemplate {

    /** Placeholder opening token. 占位符开 token。 */
    public static final String PLACEHOLDER_OPEN = "{{";
    /** Placeholder closing token. 占位符闭 token。 */
    public static final String PLACEHOLDER_CLOSE = "}}";

    private final List<String> headerLines;
    private final String body;
    private final List<String> footerLines;
    private final String rawSource;
    private final List<String> placeholderNames;
    private final String finalSource;

    private ShaderTemplate(List<String> headerLines, String body, List<String> footerLines,
                           String rawSource, List<String> placeholderNames, String finalSource) {
        this.headerLines = headerLines;
        this.body = body;
        this.footerLines = footerLines;
        this.rawSource = rawSource;
        this.placeholderNames = placeholderNames;
        this.finalSource = finalSource;
    }

    /**
     * Assembles a template from ordered parts. {@code headerLines} / {@code footerLines}
     * are copied in order (may be {@code null} for empty); {@code body} must be
     * non-null. Canonical assembly rule: header lines in order each terminated by
     * {@code '\n'}, then the body verbatim, then footer lines in order each terminated
     * by {@code '\n'}, with a single trailing newline guaranteed. Fixed order,
     * byte-deterministic: identical parts always assemble to identical source.
     *
     * 由有序部件拼装模板。{@code headerLines} / {@code footerLines} 按序拷贝（可为 {@code null}
     * 表示空）；{@code body} 必须非空。规范拼装规则：头行按序各以 {@code '\n'} 结尾、原样 body、
     * 尾行按序各以 {@code '\n'} 结尾，并保证单个结尾换行。固定序、逐字节确定：相同部件恒拼装出
     * 相同源码。
     */
    public static ShaderTemplate fromParts(List<String> headerLines, String body, List<String> footerLines) {
        Objects.requireNonNull(body, "body must be non-null");
        List<String> header = headerLines == null ? List.of() : List.copyOf(headerLines);
        List<String> footer = footerLines == null ? List.of() : List.copyOf(footerLines);
        StringBuilder sb = new StringBuilder();
        for (String line : header) {
            sb.append(line).append('\n');
        }
        sb.append(body);
        if (sb.length() > 0 && sb.charAt(sb.length() - 1) != '\n') {
            sb.append('\n');
        }
        for (String line : footer) {
            sb.append(line).append('\n');
        }
        if (sb.length() > 0 && sb.charAt(sb.length() - 1) != '\n') {
            sb.append('\n');
        }
        String raw = sb.toString();
        return new ShaderTemplate(header, body, footer, raw, scanPlaceholders(raw), null);
    }

    /**
     * All placeholder names in fixed first-occurrence order (unmodifiable, distinct).
     * Empty on a fully substituted template.
     *
     * 全部占位符名，按固定首次出现序（不可变、去重）。完全替换后的模板为空。
     */
    public List<String> placeholderNames() {
        return placeholderNames;
    }

    /**
     * Whether the template contains the given placeholder. 模板是否含指定占位符。
     */
    public boolean hasPlaceholder(String name) {
        return name != null && placeholderNames.contains(name);
    }

    /**
     * Returns a new template with every occurrence of every placeholder replaced by
     * its value, in the fixed first-occurrence order of {@link #placeholderNames()}
     * (map iteration order is irrelevant — deterministic). A null value for any
     * declared placeholder is rejected with {@link IllegalArgumentException}, as is a
     * leftover {@code {{…}}} token after substitution (values must be final GLSL
     * fragments; they are not re-scanned). Same template + same values → same bytes.
     *
     * 返回一个新模板：按 {@link #placeholderNames()} 的固定首次出现序替换每个占位符的全部出现
     * （map 迭代序无关——确定性）。任何已声明占位符取值为 null 以 {@link IllegalArgumentException}
     * 拒绝；替换后仍残留 {@code {{…}}} token 亦拒绝（取值必须是最终 GLSL 片段，不再扫描）。
     * 同模板 + 同取值 → 同字节。
     */
    public ShaderTemplate substituteAll(Map<String, String> values) {
        String substituted = rawSource;
        for (String name : placeholderNames) {
            String value = values == null ? null : values.get(name);
            if (value == null) {
                throw new IllegalArgumentException("no value for placeholder: " + name);
            }
            substituted = replaceAll(substituted, PLACEHOLDER_OPEN + name + PLACEHOLDER_CLOSE, value);
        }
        if (substituted.indexOf(PLACEHOLDER_OPEN) >= 0) {
            throw new IllegalArgumentException("unresolved placeholder remains after substitution: " + substituted);
        }
        return new ShaderTemplate(headerLines, body, footerLines, rawSource, List.of(), substituted);
    }

    /**
     * The canonical GLSL source text of this template. For an unsubstituted template
     * this is the fixed-order assembled source (with any {@code {{name}}} tokens
     * intact); for a substituted template it is the final text. Templates that still
     * declare placeholders cannot compile — use {@link #substituteAll(Map)} first
     * (deterministic failure via {@link IllegalStateException}). Fixed order,
     * byte-identical for identical inputs.
     *
     * 本模板的规范 GLSL 源文本。未替换模板为固定序拼装源码（保留任何 {@code {{name}}} token）；
     * 已替换模板为最终文本。仍声明占位符的模板不可编译——请先
     * {@link #substituteAll(Map)}（确定性失败，抛 {@link IllegalStateException}）。
     * 固定序，相同输入逐字节一致。
     */
    public String compile() {
        if (finalSource != null) {
            return finalSource;
        }
        if (!placeholderNames.isEmpty()) {
            throw new IllegalStateException("template still has placeholders; substituteAll first: " + placeholderNames);
        }
        return rawSource;
    }

    /**
     * Convenience: {@code substituteAll(values).compile()} — deterministic single
     * step producing the canonical final GLSL text. 便捷方法：{@code substituteAll(values).compile()}
     * ——确定性一步产出规范最终 GLSL 文本。
     */
    public String compile(Map<String, String> values) {
        return substituteAll(values).compile();
    }

    /**
     * Canonical byte encoding of {@link #compile()} (UTF-8). Same input, same bytes.
     * {@link #compile()} 的规范字节编码（UTF-8）。同输入、同字节。
     */
    public byte[] compileBytes() {
        return compile().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Deterministic equality on the compiled source. 基于编译源码的确定性相等。
     */
    @Override
    public boolean equals(Object o) {
        return this == o || (o instanceof ShaderTemplate that
                && compile().equals(that.compile()));
    }

    @Override
    public int hashCode() {
        return compile().hashCode();
    }

    @Override
    public String toString() {
        return compile();
    }

    private static List<String> scanPlaceholders(String raw) {
        LinkedHashSet<String> names = new LinkedHashSet<>();
        int i = 0;
        while (true) {
            int open = raw.indexOf(PLACEHOLDER_OPEN, i);
            if (open < 0) {
                break;
            }
            int close = raw.indexOf(PLACEHOLDER_CLOSE, open + PLACEHOLDER_OPEN.length());
            if (close < 0) {
                throw new IllegalArgumentException("unclosed placeholder at index " + open);
            }
            String name = raw.substring(open + PLACEHOLDER_OPEN.length(), close);
            if (name.isEmpty()) {
                throw new IllegalArgumentException("empty placeholder name at index " + open);
            }
            for (int c = 0; c < name.length(); c++) {
                char ch = name.charAt(c);
                if (!(ch >= 'a' && ch <= 'z') && !(ch >= 'A' && ch <= 'Z')
                        && !(ch >= '0' && ch <= '9') && ch != '_') {
                    throw new IllegalArgumentException("invalid placeholder name: " + name);
                }
            }
            names.add(name);
            i = close + PLACEHOLDER_CLOSE.length();
        }
        return List.copyOf(names);
    }

    private static String replaceAll(String source, String token, String value) {
        int idx = source.indexOf(token);
        if (idx < 0) {
            return source;
        }
        StringBuilder sb = new StringBuilder(source.length() + value.length());
        int cursor = 0;
        while (idx >= 0) {
            sb.append(source, cursor, idx).append(value);
            cursor = idx + token.length();
            idx = source.indexOf(token, cursor);
        }
        sb.append(source, cursor, source.length());
        return sb.toString();
    }
}
