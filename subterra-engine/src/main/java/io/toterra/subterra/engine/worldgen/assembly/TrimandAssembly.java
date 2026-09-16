package io.toterra.subterra.engine.worldgen.assembly;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * p.2.35 / p.2.29 Trimand 三定调场的装配面（纯 JDK、确定性）：把 tie 侧 Trimand
 * 微型扩散 DLL（经 {@code subterra-tie} 的 FFM 桥）吐出的三个宏观粗场——气候 / 海拔 / 山带——
 * 收敛为<b>可供密度装配消费的确定性标量</b>。
 *
 * <p>规则键与 {@link DensityAssembly} 保持同一体系与风格（{@code subterra.worldgen.*} 前缀、
 * 字面量 double 载体、固定规范序）：
 * <ul>
 *   <li>{@link #RULE_TRIMAND_ENABLED} —— 布尔开关，缺省 {@code false}；</li>
 *   <li>{@link #RULE_TRIMAND_WEIGHT} —— 融合权重，缺省 {@code 0.0}。</li>
 * </ul>
 * 两条缺省合起来即<b>恒等</b>：{@link #DEFAULT_PARAMS} 下 {@link #apply} 逐位返回原始密度，
 * 缺省预设零行为变化。
 *
 * <p>确定性纪律：{@link #of(Map)} 对同一输入恒得同一输出（未知键忽略、缺失键取缺省）；字段序固定；
 * {@link #render(Params)} 产规范串（同参即同字节）；非法值（空白 / 非 true-false 布尔、非有限或
 * 越界权重）确定性拒绝（{@link IllegalArgumentException}）。
 *
 * <p>粗场归一：tie 侧契约声明三场值域 ∈ [-1,1]；{@link Fields} 对非有限值确定性拒绝，对越界值按
 * {@link #FIELD_MIN}/{@link #FIELD_MAX} 硬钳位（确定性、不放大），因此 {@link #hint} 的产物恒
 * ∈ [-1,1]。{@link #apply} 对非有限原始密度<b>确定性直通</b>（不抛、不伪造值）——热路径降级纪律：
 * 上游 DLL 失败时密度树仍逐位保持原样。
 * <p>
 * p.2.35 / p.2.29 the Trimand three-tone assembly surface (pure JDK, deterministic): converges the
 * three macroscopic coarse fields emitted by the tie-side Trimand micro-diffusion DLL (through the
 * {@code subterra-tie} FFM bridge) — climate / elevation / mountain — into a <b>deterministic scalar
 * consumable by the density assembly</b>.
 *
 * <p>Rule keys share the {@link DensityAssembly} family and style ({@code subterra.worldgen.*}
 * prefix, double-literal carriers, fixed canonical order): {@link #RULE_TRIMAND_ENABLED} (boolean,
 * default {@code false}) and {@link #RULE_TRIMAND_WEIGHT} (blend weight, default {@code 0.0}). Both
 * defaults compose to <b>identity</b>: under {@link #DEFAULT_PARAMS} {@link #apply} returns the raw
 * density bit-for-bit, so the default preset changes nothing.
 *
 * <p>Determinism: {@link #of(Map)} yields the same output for the same input (unknown keys ignored,
 * missing keys take defaults); field order is fixed; {@link #render(Params)} produces a canonical
 * string (same params → same bytes); invalid values (blank / non-boolean, non-finite or out-of-range
 * weight) are deterministically rejected. Coarse fields: the tie-side contract declares a [-1,1]
 * range; {@link Fields} rejects non-finite values and hard-clamps out-of-range ones to
 * {@link #FIELD_MIN}/{@link #FIELD_MAX}, so {@link #hint} is always in [-1,1]. {@link #apply}
 * deterministically passes a non-finite raw density through (never throws, never fabricates a value).
 */
public final class TrimandAssembly {

    /** Trimand 启用规则键：布尔，缺省 {@code "false"}（关闭 = 恒等）。 /
     *  The Trimand-enable rule key: boolean, default {@code "false"} (off = identity). */
    public static final String RULE_TRIMAND_ENABLED = "subterra.worldgen.trimand_enabled";

    /** Trimand 权重规则键：有限 double ∈ [0,1]，缺省 {@code "0.0"}（0 = 恒等）。 /
     *  The Trimand-weight rule key: finite double in [0,1], default {@code "0.0"} (0 = identity). */
    public static final String RULE_TRIMAND_WEIGHT = "subterra.worldgen.trimand_weight";

    /** 三场融合权重（固定、规范、和 = 1.0）：climate {@code 0.25} / elevation {@code 0.50} /
     *  mountain {@code 0.25}。海拔主导、山带与气候对称收束——规范常量，不是可调参数。 /
     *  The canonical three-field blend weights (fixed, sum = 1.0): climate {@code 0.25} /
     *  elevation {@code 0.50} / mountain {@code 0.25}. Elevation-led, mountain and climate
     *  symmetric — canonical constants, not tunables. */
    public static final double HINT_WEIGHT_CLIMATE = 0.25;
    /** See {@link #HINT_WEIGHT_CLIMATE}. */
    public static final double HINT_WEIGHT_ELEVATION = 0.50;
    /** See {@link #HINT_WEIGHT_CLIMATE}. */
    public static final double HINT_WEIGHT_MOUNTAIN = 0.25;

    /** 粗场下界（tie 侧契约值域下确界）。The coarse-field contract lower bound. */
    public static final double FIELD_MIN = -1.0;
    /** 粗场上界（tie 侧契约值域上确界）。The coarse-field contract upper bound. */
    public static final double FIELD_MAX = 1.0;

    /**
     * 三个归一化粗场（climate / elevation / mountain），不可变、构造即规范化。 /
     *  The three normalised coarse fields (climate / elevation / mountain), immutable, normalised on
     *  construction.
     */
    public record Fields(double climate, double elevation, double mountain) {

        /** 非有限 → 确定性拒绝；越界 → 硬钳位到 {@code [-1,1]}。 /
         *  Non-finite → deterministically rejected; out-of-range → hard-clamped to {@code [-1,1]}. */
        public Fields {
            climate = normalise("climate", climate);
            elevation = normalise("elevation", elevation);
            mountain = normalise("mountain", mountain);
        }

        /** 规范单行渲染（固定字段序）。 / Canonical one-line render (fixed field order). */
        @Override
        public String toString() {
            return "climate=" + climate + "; elevation=" + elevation + "; mountain=" + mountain;
        }
    }

    /**
     * 已解析的 Trimand 装配参数：固定字段序、不可变、规范。 /
     *  The parsed Trimand assembly parameters: fixed field order, immutable, canonical.
     */
    public record Params(boolean enabled, double weight) {

        /** 校验并归一化载体：权重必须有限且 ∈ [0,1]。 /
         *  Validates + normalises the carrier: the weight must be finite and in [0,1]. */
        public Params {
            if (!Double.isFinite(weight) || weight < 0.0 || weight > 1.0) {
                throw new IllegalArgumentException("trimand weight must be finite in [0, 1], got " + weight);
            }
        }

        /** 规范单行渲染（同参即同字节）。 / Canonical one-line render (same params → same bytes). */
        @Override
        public String toString() {
            return "trimand_enabled=" + enabled + "; trimand_weight=" + weight;
        }
    }

    /** Trimand 等价缺省参数（恒等；缺省预设零行为变化）。 /
     *  The Trimand-equivalent default parameters (identity; zero behaviour change on the default
     *  preset). */
    public static final Params DEFAULT_PARAMS = new Params(false, 0.0);

    private TrimandAssembly() {
    }

    /**
     * 解析有效规则表为规范 {@link Params}；未知键忽略、缺失键取缺省。纯函数且确定性：同表恒得同参；
     * 非法值抛异常。 / Parses an effective rule table into canonical {@link Params}; unknown keys are
     * ignored, missing keys take defaults. Pure and deterministic: the same table always yields the
     * same params; invalid values throw.
     *
     * @param rules 有效规则表（可为 null → 缺省） / the effective rule table (may be null → defaults)
     * @return 规范参数 / the canonical params
     * @throws IllegalArgumentException 值非法时 / when a value is invalid
     */
    public static Params of(Map<String, String> rules) {
        Map<String, String> r = rules == null ? Map.of() : rules;
        boolean enabled = DEFAULT_PARAMS.enabled;
        double weight = DEFAULT_PARAMS.weight;
        for (Map.Entry<String, String> e : r.entrySet()) {
            String k = e.getKey();
            if (k == null) {
                continue;
            }
            switch (k) {
                case RULE_TRIMAND_ENABLED -> enabled = parseBoolean(RULE_TRIMAND_ENABLED, e.getValue());
                case RULE_TRIMAND_WEIGHT -> weight = parseFinite(RULE_TRIMAND_WEIGHT, e.getValue());
                default -> { /* unknown keys ignored deterministically */ }
            }
        }
        return new Params(enabled, weight);
    }

    /** 参数的规范渲染（固定字段序）。 / The canonical render of the params (fixed field order). */
    public static String render(Params params) {
        Objects.requireNonNull(params, "params must not be null");
        return params.toString();
    }

    /** 该参数是否恒等（关闭，或权重为 {@code 0.0}）——恒等时 {@link #apply} 逐位直通。 /
     *  Whether the params are identity (disabled, or weight {@code 0.0}) — identity {@link #apply}
     *  passes through bit-for-bit. */
    public static boolean isIdentity(Params params) {
        Objects.requireNonNull(params, "params must not be null");
        return !params.enabled || params.weight == 0.0;
    }

    /**
     * 三场 → 单一密度提示的规范融合：{@code clamp(0.25*climate + 0.50*elevation +
     * 0.25*mountain, -1, 1)}。纯函数且确定性；{@link Fields} 已保证输入 ∈ [-1,1]，故产物恒 ∈
     * [-1,1]。 / The canonical three-field → single density-hint blend:
     * {@code clamp(0.25*climate + 0.50*elevation + 0.25*mountain, -1, 1)}. Pure and deterministic;
     * {@link Fields} already guarantees inputs in [-1,1], so the product is always in [-1,1].
     */
    public static double hint(Fields fields) {
        Objects.requireNonNull(fields, "fields must not be null");
        double h = HINT_WEIGHT_CLIMATE * fields.climate
                + HINT_WEIGHT_ELEVATION * fields.elevation
                + HINT_WEIGHT_MOUNTAIN * fields.mountain;
        return Math.max(FIELD_MIN, Math.min(FIELD_MAX, h));
    }

    /**
     * 把 Trimand 提示施加到原始密度：恒等参数或非有限原始值 → 逐位直通；否则
     * {@code raw + weight * hint(fields)}。纯函数且确定性。 /
     *  Applies the Trimand hint to a raw density: identity params or a non-finite raw value → pass
     *  through bit-for-bit; otherwise {@code raw + weight * hint(fields)}. Pure and deterministic.
     */
    public static double apply(Params params, double rawDensity, Fields fields) {
        Objects.requireNonNull(params, "params must not be null");
        Objects.requireNonNull(fields, "fields must not be null");
        if (isIdentity(params) || !Double.isFinite(rawDensity)) {
            return rawDensity;
        }
        double v = rawDensity + params.weight * hint(fields);
        return Double.isFinite(v) ? v : rawDensity;
    }

    /** 语义解析布尔字面量（去空白、大小写不敏感；接受 {@code true/false/1/0}）。 /
     *  Semantically parses a boolean literal (trimmed, case-insensitive; accepts
     *  {@code true/false/1/0}). */
    private static boolean parseBoolean(String key, String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("rule '" + key + "' requires a boolean, got <blank>");
        }
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "true", "1" -> true;
            case "false", "0" -> false;
            default -> throw new IllegalArgumentException(
                    "rule '" + key + "' requires true/false (or 1/0), got '" + raw + "'");
        };
    }

    /** 解析有限范围内的 double 字面量（去空白）；null/空白/非有限抛异常。 /
     *  Parses a finite double literal (trimmed); null/blank/non-finite throw. */
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

    /** 粗场归一：非有限拒绝、越界硬钳位。 / Coarse-field normalisation: reject non-finite, hard-clamp
     *  out-of-range. */
    private static double normalise(String what, double v) {
        if (!Double.isFinite(v)) {
            throw new IllegalArgumentException("trimand coarse field '" + what + "' must be finite, got " + v);
        }
        return Math.max(FIELD_MIN, Math.min(FIELD_MAX, v));
    }
}
