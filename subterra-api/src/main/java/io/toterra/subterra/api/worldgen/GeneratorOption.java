package io.toterra.subterra.api.worldgen;

/**
 * Immutable switch that governs whether the Subterra world generator is the
 * default MC generator (p.1.8.22). Pure and deterministic; no Minecraft runtime.
 * <p>
 * The Subterra generator feature is <b>OFF by default</b>: {@code
 * useSubterraAsDefault=false}. It can be turned into the default generator two
 * ways — both documented entry points, no fragile reflection/mixin:
 * <ul>
 *   <li><b>Config file</b>: the MC-side loader reads
 *       {@code config/subterra/worldgen.td} (see
 *       {@code io.toterra.subterra.worldgen.gen.WorldgenConfig}); set
 *       {@code [ use_subterra_generator = true ]} to make Subterra the default.</li>
 *   <li><b>API</b>: a host/plugin calls {@code WorldgenConfig.enableDefaultGenerator()}
 *       on the MC side (in-memory only, not persisted).</li>
 * </ul>
 * When the switch stays off, the "Subterra" world type is still selectable at
 * world creation (preset {@code subterra:subterra}), it just is not the default.
 * <p>
 * {@link #td()} renders the option as a td table-literal self-description and
 * {@link #fromTd(String)} parses one back ({@code [ use_subterra_generator = true ]}
 * style); unknown keys, malformed values and garbage are rejected with
 * {@link IllegalArgumentException}.
 * <p>
 * Subterra 生成器默认关闭（p.1.8.22）。不可变开关值：默认 {@code
 * useSubterraAsDefault=false}。可通过两种方式把它变成默认生成器——都不涉及脆弱的
 * 反射/接口层注入：
 * <ul>
 *   <li><b>配置文件</b>：MC 侧加载 {@code config/subterra/worldgen.td}（见
 *       {@code io.toterra.subterra.worldgen.gen.WorldgenConfig}），写入
 *       {@code [ use_subterra_generator = true ]} 即把 Subterra 设为默认。</li>
 *   <li><b>API</b>：宿主/插件在 MC 侧调用
 *       {@code WorldgenConfig.enableDefaultGenerator()}（仅内存，不落盘）。</li>
 * </ul>
 * 关闭时，"Subterra" 世界类型仍可在创建世界时选择（预设 {@code subterra:subterra}），
 * 只是不作为默认。
 */
public record GeneratorOption(boolean useSubterraAsDefault) {

    /**
     * The default option: the Subterra generator is off; vanilla remains the
     * default world type until enabled via config or API.
     */
    public static GeneratorOption defaults() {
        return new GeneratorOption(false);
    }

    /**
     * Returns a copy with only {@code useSubterraAsDefault} replaced; the
     * receiver is left unchanged (immutability).
     */
    public GeneratorOption withUseSubterraAsDefault(boolean value) {
        if (useSubterraAsDefault == value) {
            return this;
        }
        return new GeneratorOption(value);
    }

    /**
     * Renders this option as a td table-literal self-description, e.g.
     *
     * <pre>{@code
     * [
     *   use_subterra_generator = true,
     * ]
     * }</pre>
     *
     * Deterministic; {@link #fromTd(String)} parses it back losslessly.
     */
    public String td() {
        return "[\n  use_subterra_generator = " + useSubterraAsDefault + ",\n]";
    }

    /**
     * Parses a td table-literal into a {@link GeneratorOption}. Accepts the
     * {@code [ use_subterra_generator = true ]} key=value style with optional
     * {@code //} comments, a {@code type tie<data>} header and stray/trailing
     * commas. Only the key {@code use_subterra_generator} is recognised; its
     * value must be a boolean ({@code true}/{@code false}) or {@code 1}/{@code 0}.
     * <p>
     * {@code use_subterra_generator} absent from an otherwise empty table
     * yields {@link #defaults()}.
     *
     * @throws IllegalArgumentException on unknown keys, malformed values or
     *         garbage / null input.
     */
    public static GeneratorOption fromTd(String source) {
        if (source == null) {
            throw new IllegalArgumentException("td source must not be null");
        }
        String text = normalize(source);
        if (text.isEmpty()) {
            throw new IllegalArgumentException("malformed td: empty document");
        }
        if (text.charAt(0) != '[') {
            throw new IllegalArgumentException("malformed td: expected leading '['");
        }
        if (text.charAt(text.length() - 1) != ']') {
            throw new IllegalArgumentException("malformed td: expected trailing ']'");
        }
        String body = text.substring(1, text.length() - 1);
        boolean useSubterra = false;
        for (String raw : body.split(",")) {
            String entry = raw.trim();
            if (entry.isEmpty()) {
                continue; // tolerate stray / trailing commas
            }
            int eq = entry.indexOf('=');
            if (eq <= 0) {
                throw new IllegalArgumentException("malformed td entry: '" + entry + "'");
            }
            String key = entry.substring(0, eq).trim();
            String value = entry.substring(eq + 1).trim();
            if (key.isEmpty()) {
                throw new IllegalArgumentException("malformed td entry: empty key");
            }
            if (value.isEmpty()) {
                throw new IllegalArgumentException("malformed td entry: empty value for '" + key + "'");
            }
            switch (key) {
                case "use_subterra_generator" -> useSubterra = parseBoolean(value);
                default -> throw new IllegalArgumentException("unknown td key '" + key + "'");
            }
        }
        return new GeneratorOption(useSubterra);
    }

    private static boolean parseBoolean(String value) {
        String v = value.toLowerCase();
        return switch (v) {
            case "true", "1" -> true;
            case "false", "0" -> false;
            default -> throw new IllegalArgumentException("malformed boolean value '" + value + "'");
        };
    }

    /** Strips {@code //} comments, an optional header line and outer whitespace. */
    private static String normalize(String source) {
        StringBuilder sb = new StringBuilder(source.length());
        String[] lines = source.split("\n", -1);
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("//")) {
                continue;
            }
            if (trimmed.startsWith("type ")) {
                continue; // type tie<data> header
            }
            // Drop a trailing // comment within the line.
            int c = trimmed.indexOf("//");
            if (c >= 0) {
                trimmed = trimmed.substring(0, c).trim();
            }
            if (!trimmed.isEmpty()) {
                if (sb.length() > 0) {
                    sb.append(' ');
                }
                sb.append(trimmed);
            }
        }
        return sb.toString().trim();
    }
}