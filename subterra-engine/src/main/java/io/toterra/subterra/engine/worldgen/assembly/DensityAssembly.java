package io.toterra.subterra.engine.worldgen.assembly;

import java.util.Map;
import java.util.Objects;

/**
 * p.2.29.1-3 世界生成装配核心（纯 JDK、确定性）：把「规则 → 真实生成路径」三缝的规则侧收敛为
 * <b>确定性参数解析</b>——密度偏移/缩放、表面材质面 palette、成矿密度。规则载体为值语义
 * {@code Map<String,String>}（对齐 api.config.Rule 与 DatapackRules 双层语义：later-pack-wins →
 * save-override-wins 后的<b>有效</b>规则表），本核心只做纯解析、不读存储、不碰 MC。
 * <p>确定性：{@link #DEFAULT_PARAMS} 常量固定；{@link #of(Map)} 对同一输入恒得同一输出；字段序固定；
 * {@link #render(Params)} 产规范串（同输入同字节）；非法数值（非有限 double / 负 density offset 上限 /
 * 未知 palette id）确定性拒绝（{@link IllegalArgumentException}）。scale 语义=先 scale 后 offset
 * （{@code value*scale+offset}），同 {engine SubterraDensity} 装配语义。
 * <p>
 * p.2.29.1-3 the worldgen-assembly core (pure JDK, deterministic): converges the rule side of the three
 * "rules → real generation path" seams into <b>deterministic parameter parsing</b> — density offset/scale,
 * surface palette, ore density. The rule carrier is the value-semantic {@code Map<String,String>} (aligned
 * with api.config.Rule and the DatapackRules two-tier semantics — the <b>effective</b> rule table after
 * later-pack-wins → save-override-wins); this core only parses, never reads storage, never touches MC.
 * <p>Deterministic: {@link #DEFAULT_PARAMS} is fixed; {@link #of(Map)} yields the same output for the same
 * input; field order is fixed; {@link #render(Params)} produces a canonical string (same input → same bytes);
 * invalid values (non-finite doubles / a palette id outside the allowed set) are deterministically rejected
 * ({@link IllegalArgumentException}). Scale applies before offset ({@code value*scale+offset}), matching the
 * engine SubterraDensity assembly semantics.
 */
public final class DensityAssembly {

    /** The density-offset rule key: shifts the terrain density up/down by a fixed block amount. /
     *  密度偏移规则键：把地形密度整体上移/下沉一个固定量。 */
    public static final String RULE_DENSITY_OFFSET = "subterra.worldgen.density_offset";
    /** The density-scale rule key: multiplies the terrain density (default {@code 1.0}). /
     *  密度缩放规则键：乘以地形密度（缺省 {@code 1.0}）。 */
    public static final String RULE_DENSITY_SCALE = "subterra.worldgen.density_scale";
    /** The surface-palette rule key: selects the surface material palette (default {@code "vanilla"}). /
     *  表面材质面规则键：选择表面材质面 palette（缺省 {@code "vanilla"}）。 */
    public static final String RULE_SURFACE_PALETTE = "subterra.worldgen.surface_palette";
    /** The ore-density rule key: global ore-density multiplier (default {@code 1.0}). /
     *  成矿密度规则键：全局成矿密度倍率（缺省 {@code 1.0}）。 */
    public static final String RULE_ORE_DENSITY = "subterra.worldgen.ore_density";

    /** The only allowed surface-palette id in the fixed canonical order (the vanilla-equivalent default
     *  first; {@code "custom"} routes to the engine custom palette at the MC wiring point). /
     *  唯一的表面材质面 id 集合，固定规范序（vanilla 等价缺省在前；{@code "custom"} 在 MC 装配点路由到
     *  engine 自定义 palette）。 */
    public static final java.util.List<String> SURFACE_PALETTES = java.util.List.of("vanilla", "custom");

    /** Densities must stay finite; offsets beyond this range are rejected. /
     *  密度必须有限；超出此范围的偏移被拒绝。 */
    public static final double MAX_ABS_OFFSET = 128.0;

    /** The parsed assembly parameters: fixed field order, immutable, canonical. /
     *  已解析的装配参数：固定字段序、不可变、规范。 */
    public record Params(double densityOffset, double densityScale, String surfacePalette, double oreDensity) {

        /** Validates + normalises the carrier. / 校验并归一化载体。 */
        public Params {
            if (!Double.isFinite(densityOffset) || Math.abs(densityOffset) > MAX_ABS_OFFSET) {
                throw new IllegalArgumentException("density offset must be finite within ±" + MAX_ABS_OFFSET
                        + ", got " + densityOffset);
            }
            if (!Double.isFinite(densityScale) || densityScale < 0.0 || densityScale > 4.0) {
                throw new IllegalArgumentException("density scale must be finite in [0, 4], got " + densityScale);
            }
            Objects.requireNonNull(surfacePalette, "surfacePalette must not be null");
            if (!SURFACE_PALETTES.contains(surfacePalette)) {
                throw new IllegalArgumentException("unknown surface palette '" + surfacePalette + "'"
                        + " (allowed: " + SURFACE_PALETTES + ")");
            }
            if (!Double.isFinite(oreDensity) || oreDensity < 0.0 || oreDensity > 100.0) {
                throw new IllegalArgumentException("ore density must be finite in [0, 100], got " + oreDensity);
            }
        }

        /** The canonical one-line render (same params → same bytes). / 规范单行渲染（同参即同字节）。 */
        @Override
        public String toString() {
            return "density_offset=" + densityOffset + "; density_scale=" + densityScale
                    + "; surface_palette=" + surfacePalette + "; ore_density=" + oreDensity;
        }
    }

    /** The vanilla-equivalent default parameters (zero behaviour change on the default preset). /
     *  vanilla 等价缺省参数（缺省预设零行为变化）。 */
    public static final Params DEFAULT_PARAMS = new Params(0.0, 1.0, "vanilla", 1.0);

    private DensityAssembly() {
    }

    /**
     * Parses an effective rule table into canonical {@link Params}; unknown keys are ignored, missing keys
     * take the defaults. Pure and deterministic: the same table always yields the same params; invalid
     * values throw. / 把有效规则表解析为规范 {@link Params}；未知键忽略、缺失键取缺省。纯函数且确定性：
     * 同表恒得同参；非法值抛异常。
     *
     * @param rules the effective rule table (may be empty or null → defaults) / 有效规则表（可为空或 null）
     * @return the canonical params / 规范参数
     * @throws IllegalArgumentException on an invalid value / 值非法时
     */
    public static Params of(Map<String, String> rules) {
        Map<String, String> r = rules == null ? Map.of() : rules;
        double offset = DEFAULT_PARAMS.densityOffset;
        double scale = DEFAULT_PARAMS.densityScale;
        String palette = DEFAULT_PARAMS.surfacePalette;
        double ore = DEFAULT_PARAMS.oreDensity;
        for (Map.Entry<String, String> e : r.entrySet()) {
            String k = e.getKey();
            if (k == null) {
                continue;
            }
            switch (k) {
                case RULE_DENSITY_OFFSET -> offset = parseFinite(RULE_DENSITY_OFFSET, e.getValue());
                case RULE_DENSITY_SCALE -> scale = parseFinite(RULE_DENSITY_SCALE, e.getValue());
                case RULE_SURFACE_PALETTE -> palette = e.getValue() == null ? DEFAULT_PARAMS.surfacePalette
                        : e.getValue().trim();
                case RULE_ORE_DENSITY -> ore = parseFinite(RULE_ORE_DENSITY, e.getValue());
                default -> { /* unknown keys ignored deterministically */ }
            }
        }
        return new Params(offset, scale, palette, ore);
    }

    /** The canonical render of the params (fixed field order). / 参数的规范渲染（固定字段序）。 */
    public static String render(Params params) {
        Objects.requireNonNull(params, "params must not be null");
        return params.toString();
    }

    /**
     * Applies the density assembly to a raw density value: {@code value * scale + offset} (scale first,
     * then offset). Pure and deterministic. / 对原始密度值应用密度装配：{@code value * scale + offset}
     * （先 scale 后 offset）。纯函数且确定性。
     */
    public static double applyDensity(Params params, double value) {
        Objects.requireNonNull(params, "params must not be null");
        return value * params.densityScale + params.densityOffset;
    }

    /** Parses a finite-in-range double literal (trimmed); null/blank/non-finite throw. /
     *  解析有限范围内的 double 字面量（去空白）；null/空白/非有限抛异常。 */
    private static double parseFinite(String key, String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("rule '" + key + "' requires a finite double, got <blank>");
        }
        double v;
        try {
            v = Double.parseDouble(raw.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("rule '" + key + "' requires a finite double, got '" + raw + "'", e);
        }
        return v;
    }
}