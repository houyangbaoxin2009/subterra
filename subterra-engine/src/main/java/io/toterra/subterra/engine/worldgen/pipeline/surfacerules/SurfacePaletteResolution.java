package io.toterra.subterra.engine.worldgen.pipeline.surfacerules;

import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

import io.toterra.subterra.engine.worldgen.assembly.DensityAssembly;

/**
 * p.2.29.2 规则→表面接线的<b>引擎侧解析</b>（纯 JDK、确定性）：把「有效规则表」解析为表面材质面
 * 的规范解析结果 {@link Resolved}——palette 名（沿 {@link DensityAssembly#SURFACE_PALETTES} 的
 * 固定规范序）+ 材质重绑表（状态名 → 方块 id）。规则键命名沿既有风格：
 * <ul>
 *   <li>{@code subterra.worldgen.surface_palette} —— palette 选择子（{@code vanilla} 缺省 /
 *       {@code custom}）；</li>
 *   <li>{@code subterra.worldgen.surface_palette.<state>} —— 材质重绑条目，{@code <state>} 必须是
 *       {@link OverworldPalette#OVERWORLD_STATES} 中的规范主世界状态名，值是该状态的替代方块 id。</li>
 * </ul>
 * <p>确定性：{@link #DEFAULT} 常量固定；{@link #fromRules(Map)} 对同一输入恒得同一输出（未知键忽略、
 * 缺失键取缺省、条目按状态名 TreeMap 定序）；{@link #render(Resolved)} 产规范串（同输入同字节）。
 * 缺省恒等：palette 为 {@code vanilla} 时 {@link Resolved#identity()} 为真且材质重绑表归一化为空
 * ——真实区块表面生成逐位不变（默认预设零变化）。非法值确定性拒绝（{@link IllegalArgumentException}）：
 * 未知 palette id、未知状态名、空白替代方块 id。
 * <p>
 * p.2.29.2 the <b>engine-side resolution</b> of the rules→surface wiring (pure JDK, deterministic):
 * parses an <b>effective rule table</b> into the canonical {@link Resolved} — the palette name
 * (following {@link DensityAssembly#SURFACE_PALETTES}' fixed canonical order) plus the material
 * rebind table (state name → block id). The rule-key naming follows the existing style:
 * {@code subterra.worldgen.surface_palette} (the selector) and
 * {@code subterra.worldgen.surface_palette.<state>} (a rebind entry; {@code <state>} must be a
 * canonical overworld state name from {@link OverworldPalette#OVERWORLD_STATES}).
 * <p>Deterministic: {@link #DEFAULT} is fixed; {@link #fromRules(Map)} yields the same output for the
 * same input (unknown keys ignored, missing keys take defaults, entries ordered by a state-name
 * TreeMap); {@link #render(Resolved)} produces a canonical string (same input → same bytes).
 * Identity by default: when the palette is {@code vanilla}, {@link Resolved#identity()} is true and
 * the rebind table is normalised to empty — real chunk surface generation is bit-for-bit unchanged
 * (zero behaviour change on the default preset). Invalid values (an unknown palette id, an unknown
 * state name, a blank target block id) are deterministically rejected
 * ({@link IllegalArgumentException}).
 */
public final class SurfacePaletteResolution {

    /** The per-state rebind key prefix: {@code subterra.worldgen.surface_palette.<state>}. /
     *  逐状态重绑键前缀：{@code subterra.worldgen.surface_palette.<state>}。 */
    public static final String KEY_PREFIX = DensityAssembly.RULE_SURFACE_PALETTE + ".";

    /** The vanilla-equivalent default resolution (identity). / vanilla 等价缺省解析（恒等）。 */
    public static final Resolved DEFAULT = new Resolved("vanilla", Map.of());

    private SurfacePaletteResolution() {
    }

    /**
     * The canonical surface-palette resolution: a fixed field order, immutable, canonical. /
     * 规范表面材质面解析：固定字段序、不可变、规范。
     *
     * @param palette the palette id (one of {@link DensityAssembly#SURFACE_PALETTES}) / palette id
     * @param mapping the state-name → block-id rebind table (empty when identity) / 重绑表
     */
    public record Resolved(String palette, Map<String, String> mapping) {

        /** Validates + normalises the carrier (deterministic rejection; vanilla ⇒ empty mapping). /
         *  校验并归一化载体（确定性拒绝；vanilla ⇒ 空重绑表）。 */
        public Resolved {
            Objects.requireNonNull(palette, "palette must not be null");
            Objects.requireNonNull(mapping, "mapping must not be null");
            if (!DensityAssembly.SURFACE_PALETTES.contains(palette)) {
                throw new IllegalArgumentException("unknown surface palette '" + palette + "'"
                        + " (allowed: " + DensityAssembly.SURFACE_PALETTES + ")");
            }
            TreeMap<String, String> canonical = new TreeMap<>();
            for (Map.Entry<String, String> e : mapping.entrySet()) {
                String state = Objects.requireNonNull(e.getKey(), "state must not be null");
                if (!OverworldPalette.OVERWORLD_STATES.contains(state)) {
                    throw new IllegalArgumentException("unknown surface state '" + state + "'"
                            + " (allowed: " + new java.util.TreeSet<>(OverworldPalette.OVERWORLD_STATES) + ")");
                }
                String target = e.getValue();
                if (target == null || target.isBlank()) {
                    throw new IllegalArgumentException("surface state '" + state
                            + "' requires a non-blank target block id");
                }
                canonical.put(state, target.trim());
            }
            // Identity normalisation: a vanilla palette never carries a rebind table, so the
            // canonical form is unambiguous (same behaviour → same bytes).
            mapping = "vanilla".equals(palette) ? Map.of() : Map.copyOf(canonical);
        }

        /** True when this resolution is the vanilla-equivalent identity (no material rebind). /
         *  当解析为 vanilla 等价恒等（无材质重绑）时为真。 */
        public boolean identity() {
            return "vanilla".equals(palette) || mapping.isEmpty();
        }
    }

    /**
     * Parses an effective rule table into a canonical {@link Resolved}; unknown keys are ignored,
     * missing keys take the defaults. Pure and deterministic: the same table always yields the same
     * result; invalid values throw (deterministic rejection).
     * <p>把有效规则表解析为规范 {@link Resolved}；未知键忽略、缺失键取缺省。纯函数且确定性：同表恒得
     * 同结果；非法值抛异常（确定性拒绝）。
     *
     * @param rules the effective rule table (may be null → defaults) / 有效规则表（可为 null）
     * @return the canonical resolution / 规范解析
     * @throws IllegalArgumentException on an invalid value / 值非法时
     */
    public static Resolved fromRules(Map<String, String> rules) {
        Map<String, String> r = rules == null ? Map.of() : rules;
        String palette = DEFAULT.palette();
        TreeMap<String, String> mapping = new TreeMap<>();
        for (Map.Entry<String, String> e : r.entrySet()) {
            String k = e.getKey();
            if (k == null) {
                continue;
            }
            if (DensityAssembly.RULE_SURFACE_PALETTE.equals(k)) {
                palette = e.getValue() == null ? DEFAULT.palette() : e.getValue().trim();
            } else if (k.startsWith(KEY_PREFIX)) {
                String state = k.substring(KEY_PREFIX.length());
                mapping.put(state, e.getValue() == null ? "" : e.getValue());
            }
            // any other key is ignored deterministically
        }
        return new Resolved(palette, mapping);
    }

    /** The canonical one-line render (same resolution → same bytes). / 规范单行渲染（同解析即同字节）。 */
    public static String render(Resolved resolved) {
        Objects.requireNonNull(resolved, "resolved must not be null");
        StringBuilder sb = new StringBuilder("palette=").append(resolved.palette())
                .append("; remap=").append(resolved.mapping().size());
        new TreeMap<>(resolved.mapping()).forEach((state, target) ->
                sb.append("; ").append(state).append("->").append(target));
        return sb.toString();
    }
}
