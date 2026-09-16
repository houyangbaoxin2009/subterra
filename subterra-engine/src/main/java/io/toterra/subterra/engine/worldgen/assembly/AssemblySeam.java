package io.toterra.subterra.engine.worldgen.assembly;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * p.2.29.4 / p.2.35 世界生成装配面的单一入口（纯 JDK、确定性）：把<b>一张有效规则表</b>一次解析为
 * 密度面（{@link DensityAssembly.Params}）与 Trimand 三场装配面（{@link TrimandAssembly.Params}）
 * 两个规范载体，并给出组合规范渲染与组合 apply。这就是 runtime 启动快照与领域模组消费的接缝，
 * 密度偏移/缩放/palette/成矿/Trimand 六条规则键共享一次解析、一个渲染、一个 apply 面。
 *
 * <p>规则键集合 {@link #RULE_KEYS} 为固定规范序（四条密度面在前、两条 Trimand 面在后，与
 * runtime-wiring.md 的行序一致）。
 *
 * <p>确定性纪律：{@link #of(Map)} 对同一输入恒得同一输出（未知键忽略、缺失键取缺省）；
 * {@link #render(Resolved)} 是同输入同字节的规范串；任一条规则值非法即<b>整表确定性拒绝</b>
 * （{@link IllegalArgumentException}，all-or-nothing —— 与 p.2.23 hub 配置编辑的类型违约
 * 全有或全无语义一致）；{@link #DEFAULTS} 下 {@link #applyDensity} 与原始密度逐位相同。
 * <p>
 * p.2.29.4 / p.2.35 the single entry point of the worldgen assembly surface (pure JDK,
 * deterministic): parses <b>one effective rule table</b> into the density carrier
 * ({@link DensityAssembly.Params}) and the Trimand three-field carrier
 * ({@link TrimandAssembly.Params}) in one pass, plus a combined canonical render and a combined
 * apply. This is the seam consumed by the runtime boot snapshot and by domain mods; the six rule
 * keys (density offset/scale/palette/ore + Trimand enable/weight) share one parse, one render and
 * one apply surface.
 *
 * <p>{@link #RULE_KEYS} is a fixed canonical order (the four density keys first, the two Trimand
 * keys last, matching the runtime-wiring.md row order). Determinism: {@link #of(Map)} yields the
 * same output for the same input; {@link #render(Resolved)} is a same-input-same-bytes canonical
 * string; any invalid rule value <b>deterministically rejects the whole table</b>
 * ({@link IllegalArgumentException}, all-or-nothing, consistent with the p.2.23 hub config-edit
 * type-violation semantics); under {@link #DEFAULTS} {@link #applyDensity} is bit-identical to the
 * raw density.
 */
public final class AssemblySeam {

    /** 六条装配规则键（固定规范序：密度面四条 + Trimand 面两条）。 /
     *  The six assembly rule keys (fixed canonical order: four density keys + two Trimand keys). */
    public static final List<String> RULE_KEYS = List.of(
            DensityAssembly.RULE_DENSITY_OFFSET,
            DensityAssembly.RULE_DENSITY_SCALE,
            DensityAssembly.RULE_SURFACE_PALETTE,
            DensityAssembly.RULE_ORE_DENSITY,
            TrimandAssembly.RULE_TRIMAND_ENABLED,
            TrimandAssembly.RULE_TRIMAND_WEIGHT);

    /**
     * 组合解析结果：密度面 + Trimand 面两个规范载体，固定字段序、不可变。 /
     *  The combined parse result: the density and Trimand canonical carriers, fixed field order,
     *  immutable.
     */
    public record Resolved(DensityAssembly.Params density, TrimandAssembly.Params trimand) {

        /** 两个载体都不可为 null。 / Neither carrier may be null. */
        public Resolved {
            Objects.requireNonNull(density, "density must not be null");
            Objects.requireNonNull(trimand, "trimand must not be null");
        }
    }

    /** vanilla 等价缺省装配（恒等；缺省预设零行为变化）。 /
     *  The vanilla-equivalent default assembly (identity; zero behaviour change on the default
     *  preset). */
    public static final Resolved DEFAULTS =
            new Resolved(DensityAssembly.DEFAULT_PARAMS, TrimandAssembly.DEFAULT_PARAMS);

    private AssemblySeam() {
    }

    /** vanilla 等价缺省。 / The vanilla-equivalent defaults. */
    public static Resolved defaults() {
        return DEFAULTS;
    }

    /**
     * 一次解析有效规则表为规范 {@link Resolved}（未知键忽略、缺失键取缺省）；任一条规则值非法即整表
     * 确定性拒绝。纯函数且确定性：同表恒得同解。 / Parses an effective rule table into canonical
     *  {@link Resolved} in one pass (unknown keys ignored, missing keys take defaults); any invalid
     *  value rejects the whole table. Pure and deterministic: the same table always yields the same
     *  result.
     *
     * @param rules 有效规则表（可为 null → 缺省） / the effective rule table (may be null → defaults)
     * @return 规范组合解 / the canonical combined result
     * @throws IllegalArgumentException 任一条规则值非法时 / when any rule value is invalid
     */
    public static Resolved of(Map<String, String> rules) {
        Map<String, String> r = rules == null ? Map.of() : rules;
        return new Resolved(DensityAssembly.of(r), TrimandAssembly.of(r));
    }

    /** 组合规范渲染（同解即同字节）：密度面规范串 + Trimand 面规范串。 /
     *  The combined canonical render (same result → same bytes): the density canonical string
     *  followed by the Trimand canonical string. */
    public static String render(Resolved resolved) {
        Objects.requireNonNull(resolved, "resolved must not be null");
        return DensityAssembly.render(resolved.density) + "; " + TrimandAssembly.render(resolved.trimand);
    }

    /** 组合解是否恒等（密度偏移 0 / 缩放 1 / Trimand 关闭）。 /
     *  Whether the combined result is identity (offset 0 / scale 1 / Trimand disabled). */
    public static boolean isIdentity(Resolved resolved) {
        Objects.requireNonNull(resolved, "resolved must not be null");
        DensityAssembly.Params d = resolved.density;
        return d.equals(DensityAssembly.DEFAULT_PARAMS) && TrimandAssembly.isIdentity(resolved.trimand);
    }

    /**
     * 组合 apply：先密度面（{@code value*scale+offset}）再 Trimand 面（{@code raw + weight*hint}）。
     * 纯函数且确定性；缺省解下与 {@code rawDensity} 逐位相同。 /
     *  The combined apply: density first ({@code value*scale+offset}), then Trimand
     *  ({@code raw + weight*hint}). Pure and deterministic; under the default result it is
     *  bit-identical to {@code rawDensity}.
     */
    public static double applyDensity(Resolved resolved, double rawDensity, TrimandAssembly.Fields fields) {
        Objects.requireNonNull(resolved, "resolved must not be null");
        return TrimandAssembly.apply(resolved.trimand,
                DensityAssembly.applyDensity(resolved.density, rawDensity), fields);
    }
}
