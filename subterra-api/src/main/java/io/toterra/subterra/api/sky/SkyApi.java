package io.toterra.subterra.api.sky;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.concurrent.ConcurrentHashMap;

/**
 * p.2.0.13 对外的天空域契约门面（final class、静态方法、纯 JDK）：云层高度按维度覆盖注册表。
 * 语义钉死 vanilla 渲染事实：客户端 {@code LevelRenderer.renderClouds} 经
 * {@code DimensionSpecialEffects#getCloudHeight()} 取云层高度 Y，返回 {@code NaN} 表示该维度不渲染
 * 云层（原版下界/末地即如此，主世界固定 {@code 192.0}）。本契约面据此提供两件事：
 * <ol>
 *   <li><b>常量契约</b>：{@link #NO_CLOUDS}（{@code NaN}，隐藏云层哨兵）、
 *       {@link #VANILLA_OVERWORLD_CLOUD_HEIGHT}（{@code 192.0}，主世界原版云高）、
 *       {@link #DEFAULT_NAMESPACE}（{@code "minecraft"}，缺省命名空间）。</li>
 *   <li><b>按维度覆盖存储</b>：{@link #setCloudHeight} / {@link #clearCloudHeight} / {@link #clearAll} /
 *       {@link #cloudHeight} / {@link #isOverridden} / {@link #overrides()}——以规范化维度 id
 *       （{@code namespace:path}，缺省命名空间补 {@code minecraft:}）为键的线程安全覆盖表；写入
 *       {@code NaN} 等价于「该维度隐藏云层」，清空后回落 vanilla 原值。</li>
 * </ol>
 * runtime 层（{@code runtime.client.sky.mixin.CloudHeightMixin}）在云渲染取高处读本表：有覆盖即取覆盖，
 * 否则回落原版。纯客户端渲染语义——不触服务端逻辑、不改世界数据。<b>诚实界限</b>：生效点是客户端
 * 云层渲染一行取值调用，覆盖对云层形状/颜色/移动速度（均 vanilla 常量）无影响。
 * <p>
 * p.2.0.13 the external sky-domain contract facade (final class, static methods, pure JDK): the per-dimension
 * cloud-height override registry. Semantics are pinned to vanilla render facts: the client
 * {@code LevelRenderer.renderClouds} takes the cloud-height Y from
 * {@code DimensionSpecialEffects#getCloudHeight()}, where {@code NaN} means "no clouds rendered for this
 * dimension" (vanilla Nether/End behave so; the Overworld is fixed at {@code 192.0}). This contract surface
 * provides: (1) constant contracts — {@link #NO_CLOUDS} ({@code NaN}, hide-clouds sentinel),
 * {@link #VANILLA_OVERWORLD_CLOUD_HEIGHT} ({@code 192.0}, vanilla overworld cloud height) and
 * {@code DEFAULT_NAMESPACE}; (2) the per-dimension override store — a thread-safe map keyed by normalized
 * dimension id ({@code namespace:path}, default namespace filled with {@code minecraft:}); writing {@code NaN}
 * equals "hide clouds for this dimension", clearing falls back to the vanilla value. The runtime layer
 * ({@code runtime.client.sky.mixin.CloudHeightMixin}) reads this table at the cloud-render height lookup:
 * override wins, otherwise vanilla falls through. Pure client-side render semantics — no server logic, no
 * world data touched. <b>Honest boundary</b>: the single injection point is the client cloud-render height
 * lookup; cloud shape/color/drift speed (all vanilla constants) are unaffected.
 */
public final class SkyApi {

    /** The hide-clouds sentinel ({@code NaN}): mirroring vanilla {@code DimensionSpecialEffects} semantics,
     *  a dimension whose cloud height is {@code NaN} renders no clouds. / 隐藏云层哨兵（{@code NaN}）：
     *  镜像 vanilla {@code DimensionSpecialEffects} 语义——云高为 {@code NaN} 的维度不渲染云层。 */
    public static final double NO_CLOUDS = Double.NaN;

    /** The vanilla overworld cloud height ({@code 192.0}), pinned from vanilla
     *  {@code DimensionSpecialEffects.OverworldEffects}. / 主世界原版云高（{@code 192.0}），钉死自 vanilla
     *  {@code DimensionSpecialEffects.OverworldEffects}。 */
    public static final double VANILLA_OVERWORLD_CLOUD_HEIGHT = 192.0D;

    /** The default namespace filled in when a dimension id carries none ({@code "minecraft"}), mirroring
     *  vanilla {@code ResourceLocation} default-namespace semantics. / 维度 id 未携带命名空间时补入的缺省
     *  命名空间（{@code "minecraft"}），镜像 vanilla {@code ResourceLocation} 缺省命名空间语义。 */
    public static final String DEFAULT_NAMESPACE = "minecraft";

    /** Per-dimension override table, keyed by normalized dimension id. Values may be {@code NaN}
     *  (hide clouds). Thread-safe; reads are lock-free. / 按维度覆盖表，键为规范化维度 id；值可为
     *  {@code NaN}（隐藏云层）。线程安全；读取无锁。 */
    private static final ConcurrentHashMap<String, Double> OVERRIDES = new ConcurrentHashMap<>();

    private SkyApi() {
    }

    /**
     * Canonicalizes a dimension-id registration form into its fixed {@code namespace:path} lowercase form:
     * trim + lowercase, default namespace {@code minecraft:} filled when none is carried, then both sides
     * validated ({@code [a-z0-9_.-]+} for the namespace, {@code [a-z0-9_./-]+} for the path) with
     * deterministic {@link IllegalArgumentException} rejection. Pure; same-input-same-output.
     * / 将维度 id 注册形式规范化为固定 {@code namespace:path} 小写形式：trim + 小写，缺命名空间补
     * {@code minecraft:}，随后双侧校验（命名空间 {@code [a-z0-9_.-]+}、路径 {@code [a-z0-9_./-]+}），
     * 非法确定性拒绝（{@link IllegalArgumentException}）。纯函数；同输入同输出。
     *
     * @param form the dimension-id registration form (non-null, whitespace-tolerant, case-insensitive).
     * @return the canonical {@code namespace:path} form.
     * @throws NullPointerException     if {@code form} is null.
     * @throws IllegalArgumentException if the form is blank, carries an empty side, or an illegal character.
     */
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

    /**
     * Whether the form is a legal dimension-id registration form (pure; complements
     * {@link #normalizeDimensionId}). / 形式是否为合法维度 id 注册形式（纯函数；与
     * {@link #normalizeDimensionId} 互补）。
     *
     * @param form the candidate form (nullable).
     * @return {@code true} iff normalization would succeed.
     */
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

    /**
     * Sets the cloud-height override for the dimension: subsequent client cloud renders take the clouds at
     * {@code height} Y. {@code height} must be finite, or exactly {@link #NO_CLOUDS} ({@code NaN}) to hide
     * clouds for this dimension; other non-finite values are rejected deterministically. Thread-safe.
     * / 设置该维度的云高覆盖：此后客户端云渲染将云层置于 {@code height} Y。{@code height} 必须为有限值，
     * 或恰为 {@link #NO_CLOUDS}（{@code NaN}，该维度隐藏云层）；其余非有限值确定性拒绝。线程安全。
     *
     * @param dimensionId the dimension id form (normalized via {@link #normalizeDimensionId}).
     * @param height      the override cloud height Y (finite), or {@link #NO_CLOUDS} to hide clouds.
     * @throws NullPointerException     if {@code dimensionId} is null.
     * @throws IllegalArgumentException if the id is illegal, or the height is infinite but not NaN.
     */
    public static void setCloudHeight(String dimensionId, double height) {
        if (!Double.isNaN(height) && Double.isInfinite(height)) {
            throw new IllegalArgumentException("cloud height must be finite or NO_CLOUDS(NaN): " + height);
        }
        OVERRIDES.put(normalizeDimensionId(dimensionId), height);
    }

    /**
     * Removes the override for the dimension: cloud rendering falls back to the vanilla value. Absent ids
     * are a no-op (idempotent). / 移除该维度的覆盖：云渲染回落 vanilla 原值。对不存在的 id 是幂等空操作。
     *
     * @param dimensionId the dimension id form.
     * @throws NullPointerException if {@code dimensionId} is null.
     */
    public static void clearCloudHeight(String dimensionId) {
        OVERRIDES.remove(normalizeDimensionId(dimensionId));
    }

    /** Removes every override (full vanilla fallback). / 移除全部覆盖（完全回落 vanilla）。 */
    public static void clearAll() {
        OVERRIDES.clear();
    }

    /**
     * The override cloud height for the dimension, or empty when no override is set (pure read, lock-free).
     * / 该维度的覆盖云高，未设置时为空（纯读，无锁）。
     *
     * @param dimensionId the dimension id form.
     * @return {@code OptionalDouble} carrying the override ({@code NaN} = hide clouds), or empty.
     * @throws NullPointerException if {@code dimensionId} is null.
     */
    public static OptionalDouble cloudHeight(String dimensionId) {
        Double value = OVERRIDES.get(normalizeDimensionId(dimensionId));
        return value == null ? OptionalDouble.empty() : OptionalDouble.of(value);
    }

    /** Whether the dimension carries an override. / 该维度是否带覆盖。 */
    public static boolean isOverridden(String dimensionId) {
        return OVERRIDES.containsKey(normalizeDimensionId(dimensionId));
    }

    /** The sorted list of overridden dimension ids (deterministic snapshot). /
     *  被覆盖维度的排序列表（确定性快照）。 */
    public static List<String> overrides() {
        return OVERRIDES.keySet().stream().sorted().toList();
    }
}
