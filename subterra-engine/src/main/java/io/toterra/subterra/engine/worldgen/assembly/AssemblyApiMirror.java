package io.toterra.subterra.engine.worldgen.assembly;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * p.2.29 引擎侧镜像实现（api 定义形状 / engine 镜像实现范式，对应 {@code api.worldgen.assembly.
 * WorldgenAssemblyApi}）：以 {@link DensityAssembly} 的<b>真实常量</b>为唯一来源，暴露与 api 契约
 * 同语义的只读解析面——偏移/缩放/palette/成矿四个参数的缺省、范围、resolve 与规范渲染均同输入同输出
 * （探针就两侧实例化后逐位对照）。本镜像<em>不 import</em> api 包，仅消费 engine 自身类型并把 api
 * 契约值逐字导出。
 * <p>
 * p.2.29 engine-side mirror implementation (api-shapes / engine-mirrors pattern, counterpart of
 * {@code api.worldgen.assembly.WorldgenAssemblyApi}): using the <b>actual constants</b> of
 * {@link DensityAssembly} as the single source of truth, it exposes a read-only surface with the same
 * semantics as the api contract — defaults, bounds, resolve and the canonical render are all
 * same-input-same-output (the probe asserts both sides). This mirror does <em>not</em> import the api
 * package; it consumes engine types only and exports the contract values verbatim.
 */
public final class AssemblyApiMirror {

    /** The mirror record, field-identical to {@code api WorldgenAssemblyApi.Resolved}. /
     *  镜像 record，字段与 {@code api WorldgenAssemblyApi.Resolved} 一致。 */
    public record Resolved(double densityOffset, double densityScale, String surfacePalette, double oreDensity) {

        /** Validates + normalises the carrier. / 校验并归一化载体。 */
        public Resolved {
            if (!Double.isFinite(densityOffset) || Math.abs(densityOffset) > DensityAssembly.MAX_ABS_OFFSET) {
                throw new IllegalArgumentException("density offset must be finite within ±"
                        + DensityAssembly.MAX_ABS_OFFSET);
            }
            if (!Double.isFinite(densityScale) || densityScale < 0.0 || densityScale > 4.0) {
                throw new IllegalArgumentException("density scale must be finite in [0, 4]");
            }
            Objects.requireNonNull(surfacePalette, "surfacePalette must not be null");
            if (!DensityAssembly.SURFACE_PALETTES.contains(surfacePalette)) {
                throw new IllegalArgumentException("unknown surface palette '" + surfacePalette + "'");
            }
            if (!Double.isFinite(oreDensity) || oreDensity < 0.0 || oreDensity > 100.0) {
                throw new IllegalArgumentException("ore density must be finite in [0, 100]");
            }
        }
    }

    private AssemblyApiMirror() {
    }

    /** The allowed surface-palette ids, sourced verbatim from {@link DensityAssembly#SURFACE_PALETTES}. /
     *  表面材质面 id 集合，逐字源自 {@link DensityAssembly#SURFACE_PALETTES}。 */
    public static List<String> palettes() {
        return List.copyOf(DensityAssembly.SURFACE_PALETTES);
    }

    /** The vanilla-equivalent defaults, sourced from {@link DensityAssembly#DEFAULT_PARAMS}. /
     *  vanilla 等价缺省，源自 {@link DensityAssembly#DEFAULT_PARAMS}。 */
    public static Resolved defaults() {
        return toResolved(DensityAssembly.DEFAULT_PARAMS);
    }

    /** Parses an effective rule table via {@link DensityAssembly#of} and maps it to a mirror {@link Resolved}
     *  (pure, deterministic, same input → same output). / 经 {@link DensityAssembly#of} 解析有效规则表并映射
     *  为镜像 {@link Resolved}（纯函数、确定性、同输入同输出）。 */
    public static Resolved resolve(Map<String, String> rules) {
        return toResolved(DensityAssembly.of(rules));
    }

    /** The canonical one-line render (same values → same bytes). / 规范单行渲染（同值即同字节）。 */
    public static String render(Resolved resolved) {
        Objects.requireNonNull(resolved, "resolved must not be null");
        return "density_offset=" + resolved.densityOffset + "; density_scale=" + resolved.densityScale
                + "; surface_palette=" + resolved.surfacePalette + "; ore_density=" + resolved.oreDensity;
    }

    /** Applies the density assembly: {@code value * scale + offset}. / 应用密度装配：{@code value*scale+offset}。 */
    public static double applyDensity(Resolved resolved, double value) {
        Objects.requireNonNull(resolved, "resolved must not be null");
        return value * resolved.densityScale + resolved.densityOffset;
    }

    private static Resolved toResolved(DensityAssembly.Params p) {
        return new Resolved(p.densityOffset(), p.densityScale(), p.surfacePalette(), p.oreDensity());
    }
}