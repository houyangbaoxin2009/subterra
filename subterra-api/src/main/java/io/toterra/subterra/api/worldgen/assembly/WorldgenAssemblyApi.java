package io.toterra.subterra.api.worldgen.assembly;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * p.2.29 对外世界生成装配契约（final class、静态纯函数、纯 JDK）：供领域模组（如 Overturn）把
 * 「规则 → 实际生成路径」的装配参数以值语义 {Map} 驱动——密度偏移/缩放、表面材质面 palette、成矿密度。
 * 语义与 {engine.worldgen.assembly.DensityAssembly}（p.2.29.1-3）一致，本处为契约与数据面注入，
 * engine 为实现镜像（{@code engine.worldgen.assembly.AssemblyApiMirror}），api 不依赖 engine。
 * <p>确定性：{@link #defaults()} 常量固定；{@link #resolve(Map)} 对同一输入恒得同一输出（未知键忽略、
 * 缺失键取缺省）；{@link #render(Resolved)} 产规范串（同输入同字节）；非法值（非有限 double /
 * 未知 palette id）确定性拒绝。scale 语义=先 scale 后 offset（{@code value*scale+offset}）。
 * <p>
 * p.2.29 the external worldgen-assembly contract facade (final class, static pure functions, pure JDK):
 * lets domain mods (e.g. Overturn) drive the "rules → real generation path" assembly parameters as a
 * value-semantic {Map} — density offset/scale, surface palette, ore density. Semantics match
 * {engine.worldgen.assembly.DensityAssembly} (p.2.29.1-3); this is the contract and data-injection surface,
 * the engine mirrors as its implementation ({@code engine.worldgen.assembly.AssemblyApiMirror}), and the api
 * does not depend on the engine.
 * <p>Deterministic: {@link #defaults()} is fixed; {@link #resolve(Map)} yields the same output for the same
 * input (unknown keys ignored, missing keys take defaults); {@link #render(Resolved)} produces a canonical
 * string (same input → same bytes); invalid values (non-finite doubles / an unknown palette id) are
 * deterministically rejected. Scale applies before offset ({@code value*scale+offset}).
 */
public final class WorldgenAssemblyApi {

    /** The resolved assembly parameters: fixed field order, immutable, canonical. /
     *  已解析的装配参数：固定字段序、不可变、规范。 */
    public record Resolved(double densityOffset, double densityScale, String surfacePalette, double oreDensity) {

        /** Validates + normalises the carrier. / 校验并归一化载体。 */
        public Resolved {
            if (!Double.isFinite(densityOffset) || Math.abs(densityOffset) > MAX_ABS_OFFSET) {
                throw new IllegalArgumentException("density offset must be finite within ±" + MAX_ABS_OFFSET);
            }
            if (!Double.isFinite(densityScale) || densityScale < 0.0 || densityScale > 4.0) {
                throw new IllegalArgumentException("density scale must be finite in [0, 4]");
            }
            Objects.requireNonNull(surfacePalette, "surfacePalette must not be null");
            if (!PALETTES.contains(surfacePalette)) {
                throw new IllegalArgumentException("unknown surface palette '" + surfacePalette + "'");
            }
            if (!Double.isFinite(oreDensity) || oreDensity < 0.0 || oreDensity > 100.0) {
                throw new IllegalArgumentException("ore density must be finite in [0, 100]");
            }
        }
    }

    /** The bounds mirror {engine DensityAssembly#MAX_ABS_OFFSET} verbatim. /
     *  上限逐字镜像 {engine DensityAssembly#MAX_ABS_OFFSET}。 */
    public static final double MAX_ABS_OFFSET = 128.0;

    /** The allowed surface-palette ids in the fixed canonical order, mirroring
     *  {engine DensityAssembly#SURFACE_PALETTES}. / 表面材质面 id 集合固定规范序，逐字镜像
     *  {engine DensityAssembly#SURFACE_PALETTES}。 */
    public static final List<String> PALETTES = List.of("vanilla", "custom");

    /** The vanilla-equivalent default assembly. / vanilla 等价缺省装配。 */
    public static final Resolved DEFAULTS = new Resolved(0.0, 1.0, "vanilla", 1.0);

    /** The canonical one-line render (same values → same bytes). / 规范单行渲染（同值即同字节）。 */
    public static String render(Resolved resolved) {
        Objects.requireNonNull(resolved, "resolved must not be null");
        return "density_offset=" + resolved.densityOffset + "; density_scale=" + resolved.densityScale
                + "; surface_palette=" + resolved.surfacePalette + "; ore_density=" + resolved.oreDensity;
    }

    private WorldgenAssemblyApi() {
    }

    /** The vanilla-equivalent defaults. / vanilla 等价缺省。 */
    public static Resolved defaults() {
        return DEFAULTS;
    }

    /**
     * Resolves an effective rule table into canonical {@link Resolved} (unknown keys ignored, missing keys
     * take defaults); invalid values are deterministically rejected. / 把有效规则表解析为规范
     * {@link Resolved}（未知键忽略、缺失键取缺省）；非法值确定性拒绝。
     *
     * @param rules the effective rule table (may be null → defaults) / 有效规则表（可为 null）
     * @return the canonical resolved parameters / 规范解析参数
     * @throws IllegalArgumentException on an invalid value / 值非法时
     */
    public static Resolved resolve(Map<String, String> rules) {
        Map<String, String> r = rules == null ? Map.of() : rules;
        double offset = DEFAULTS.densityOffset;
        double scale = DEFAULTS.densityScale;
        String palette = DEFAULTS.surfacePalette;
        double ore = DEFAULTS.oreDensity;
        for (Map.Entry<String, String> e : r.entrySet()) {
            String k = e.getKey();
            if (k == null) {
                continue;
            }
            switch (k) {
                case "subterra.worldgen.density_offset" -> offset = parseFinite(k, e.getValue());
                case "subterra.worldgen.density_scale" -> scale = parseFinite(k, e.getValue());
                case "subterra.worldgen.surface_palette" -> palette = e.getValue() == null ? DEFAULTS.surfacePalette
                        : e.getValue().trim();
                case "subterra.worldgen.ore_density" -> ore = parseFinite(k, e.getValue());
                default -> { /* unknown keys ignored deterministically */ }
            }
        }
        return new Resolved(offset, scale, palette, ore);
    }

    /**
     * Applies the density assembly to a raw density value: {@code value * scale + offset}. Pure and
     * deterministic. / 对原始密度值应用密度装配：{@code value * scale + offset}。纯函数且确定性。
     */
    public static double applyDensity(Resolved resolved, double value) {
        Objects.requireNonNull(resolved, "resolved must not be null");
        return value * resolved.densityScale + resolved.densityOffset;
    }

    private static double parseFinite(String key, String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("rule '" + key + "' requires a finite double, got <blank>");
        }
        try {
            return Double.parseDouble(raw.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("rule '" + key + "' requires a finite double, got '" + raw + "'", e);
        }
    }
}