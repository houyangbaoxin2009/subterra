package io.toterra.subterra.engine.sky;

import java.util.Locale;
import java.util.Objects;

/**
 * p.2.0.13 引擎侧镜像实现（api 定义形状 / engine 镜像实现范式，对应 {@code api.sky.SkyApi}）：把
 * {@code api.sky.SkyApi} 的<b>纯语义</b>（常量契约 + 维度 id 规范化/校验）以同输入同输出逐字镜像，
 * 供 p.2.0.13 探针对照断言。本镜像<em>不 import</em> api 包、不复制覆盖存储——存储是 api 侧唯一来源
 * （runtime mixin 直读），此处只镜像确定性纯函数，杜绝两侧漂移。
 * <p>确定性：常量逐位同值（{@code NO_CLOUDS} 按 NaN 位型、{@code 192.0} 逐位）；
 * {@link #normalizeDimensionId} / {@link #isValidDimensionId} 与 api 同形（trim + 小写 + 缺省命名空间
 * 补 {@code minecraft:} + 双侧字符校验 + 单冒号约束）。无随机、无时序、无状态。
 * <p>
 * p.2.0.13 engine-side mirror implementation (api-shapes / engine-mirrors pattern, counterpart of
 * {@code api.sky.SkyApi}): it mirrors the <b>pure semantics</b> of {@code api.sky.SkyApi} (constant contracts
 * + dimension-id normalization/validation) verbatim with same-input-same-output, asserted by the p.2.0.13
 * probe. This mirror does <em>not</em> import the api package and does not duplicate the override store —
 * the store is the api-side single source of truth (read directly by the runtime mixin); only deterministic
 * pure functions are mirrored here, so the two sides cannot drift.
 * <p>Deterministic: constants bitwise-identical ({@code NO_CLOUDS} by NaN bit pattern, {@code 192.0}
 * bitwise); {@link #normalizeDimensionId} / {@link #isValidDimensionId} share the api's exact shape
 * (trim + lowercase + default-namespace {@code minecraft:} fill + per-side character validation + single-colon
 * constraint). No randomness, no timing, no state.
 */
public final class SkyApiMirror {

    /** The hide-clouds sentinel ({@code NaN}), mirroring {@code api.sky.SkyApi#NO_CLOUDS}. /
     *  隐藏云层哨兵（{@code NaN}），镜像 {@code api.sky.SkyApi#NO_CLOUDS}。 */
    public static final double NO_CLOUDS = Double.NaN;

    /** The vanilla overworld cloud height ({@code 192.0}), mirroring
     *  {@code api.sky.SkyApi#VANILLA_OVERWORLD_CLOUD_HEIGHT}. / 主世界原版云高（{@code 192.0}），镜像
     *  {@code api.sky.SkyApi#VANILLA_OVERWORLD_CLOUD_HEIGHT}。 */
    public static final double VANILLA_OVERWORLD_CLOUD_HEIGHT = 192.0D;

    /** The default namespace ({@code "minecraft"}), mirroring {@code api.sky.SkyApi#DEFAULT_NAMESPACE}. /
     *  缺省命名空间（{@code "minecraft"}），镜像 {@code api.sky.SkyApi#DEFAULT_NAMESPACE}。 */
    public static final String DEFAULT_NAMESPACE = "minecraft";

    private SkyApiMirror() {
    }

    /** Canonicalizes a dimension-id form ({@code namespace:path}, default namespace filled, per-side
     *  validated) — verbatim mirror of the api normalization. / 规范化维度 id 形式（{@code namespace:path}，
     *  补缺省命名空间、双侧校验）——api 规范化的逐字镜像。 */
    public static String normalizeDimensionId(String form) {
        Objects.requireNonNull(form, "dimensionId form must not be null");
        String lowered = form.trim().toLowerCase(Locale.ROOT);
        if (lowered.isEmpty()) {
            throw new IllegalArgumentException("dimensionId must not be blank: " + form);
        }
        String namespace = DEFAULT_NAMESPACE;
        String path = lowered;
        int colon = lowered.indexOf(':');
        if (colon >= 0) {
            namespace = lowered.substring(0, colon);
            path = lowered.substring(colon + 1);
            if (lowered.indexOf(':', colon + 1) >= 0) {
                throw new IllegalArgumentException("dimensionId must carry exactly one ':': " + form);
            }
        }
        if (!namespace.matches("[a-z0-9_.-]+") || !path.matches("[a-z0-9_./-]+")) {
            throw new IllegalArgumentException("illegal dimensionId: " + form);
        }
        return namespace + ":" + path;
    }

    /** Whether the form is a legal dimension-id form — verbatim mirror of the api check. /
     *  形式是否为合法维度 id 形式——api 校验的逐字镜像。 */
    public static boolean isValidDimensionId(String form) {
        if (form == null) {
            return false;
        }
        try {
            normalizeDimensionId(form);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
