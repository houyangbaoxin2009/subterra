package io.toterra.subterra.engine.render.lod;

import java.util.List;
import java.util.Objects;

/**
 * p.2.28.6 引擎侧镜像实现（api 定义形状 / engine 镜像实现范式，对应 {@code api.lod.LodApi}）：以
 * {@link LodConfigDoc} / {@link LodLevel} / {@link LodDistanceSelector} 的<b>真实常量</b>为唯一来源，
 * 暴露与 {@code api.lod.LodApi} 同语义的只读解析面——档位序、缺省质量档、最大层级、每区块方块数、缺省
 * 距离表与「距离→细节层级」投影均同输入同输出（供 p.2.28.6 探针对照断言）。本镜像<em>不 import</em>
 * api 包，仅消费 engine 自身类型并把 api 契约值逐字导出，从而在两侧分别实例化后完全一致。
 * <p>确定性：全部方法为纯函数；{@link #resolveLevel} 用纯整数方块距离平方执行与
 * {@code api.lod.LodApi#resolveLevel}、{@link LodDistanceSelector#levelForDistanceSq} 相同的单调分档；
 * 无浮点、无随机、无时序。常量来源（勿重猜）：{@link LodConfigDoc#QUALITY_TIERS}、
 * {@link LodConfigDoc#DEFAULT_DISTANCES}、{@link LodConfigDoc#defaults()}、{@link LodLevel}。
 * <p>
 * p.2.28.6 engine-side mirror implementation (api-shapes / engine-mirrors pattern, counterpart of
 * {@code api.lod.LodApi}): using the <b>actual constants</b> of {@link LodConfigDoc} / {@link LodLevel} /
 * {@link LodDistanceSelector} as the single source of truth, it exposes a read-only surface with the same
 * semantics as {@code api.lod.LodApi} — tier order, default quality tier, max level, blocks per chunk, default
 * distance table and the distance→detail-level projection are all same-input-same-output (the p.2.28.6 probe
 * asserts both sides). This mirror does <em>not</em> import the api package; it consumes only engine types and
 * exports the api contract values verbatim, so instantiating both sides yields identical results.
 * <p>Deterministic: every method is a pure function; {@link #resolveLevel} runs the same monotone tiering on
 * pure integer squared-block-distance as {@code api.lod.LodApi#resolveLevel} and
 * {@link LodDistanceSelector#levelForDistanceSq}; no float, no randomness, no timing. Constant sources (do not
 * re-guess): {@link LodConfigDoc#QUALITY_TIERS}, {@link LodConfigDoc#DEFAULT_DISTANCES},
 * {@link LodConfigDoc#defaults()}, {@link LodLevel}.
 */
public final class LodApiMirror {

    /** The resolution product, mirroring {@code api.lod.LodApi.Resolved}: effective max level + distance cuts. /
     *  解析产物，镜像 {@code api.lod.LodApi.Resolved}：有效最大层级 + 距离切分。 */
    public record Resolved(int maxLevel, List<LodConfigDoc.Distance> distances) {

        /**
         * Validates both components. / 校验两分量。
         *
         * @throws NullPointerException if {@code distances} is null.
         */
        public Resolved {
            Objects.requireNonNull(distances, "distances must not be null");
            distances = List.copyOf(distances);
        }
    }

    private LodApiMirror() {
    }

    /** Allowed quality-tier forms in fixed canonical order, sourced verbatim from
     *  {@link LodConfigDoc#QUALITY_TIERS}. / 允许的质量档小写注册名，固定规范序，逐字源自
     *  {@link LodConfigDoc#QUALITY_TIERS}。 */
    public static List<String> qualityTierForms() {
        return List.copyOf(LodConfigDoc.QUALITY_TIERS);
    }

    /** The default quality tier form, sourced from the config default
     *  {@link LodConfigDoc#QUALITY_TIERS get(0)}. / 缺省质量档注册名，源自配置缺省
     *  {@link LodConfigDoc#QUALITY_TIERS get(0)}。 */
    public static String defaultQualityTier() {
        return LodConfigDoc.defaults().qualityTier();
    }

    /** The default max detail level, sourced from {@link LodConfigDoc#defaults()#maxLevel()}. /
     *  缺省最大细节层级，源自 {@link LodConfigDoc#defaults()#maxLevel()}。 */
    public static int defaultMaxLevel() {
        return LodConfigDoc.defaults().maxLevel();
    }

    /** How many blocks in one chunk, derived as {@code blockSpan/chunkSpan} of {@link LodLevel#L0}
     *  (= 16). / 一个区块的方块数，由 {@link LodLevel#L0} 的 {@code blockSpan/chunkSpan} 导出（= 16）。 */
    public static int blocksPerChunk() {
        return LodLevel.L0.blockSpan() / LodLevel.L0.chunkSpan();
    }

    /** The default distance cuts, sourced verbatim from {@link LodConfigDoc#DEFAULT_DISTANCES}
     *  ({@code L1@64, L2@128, L3@256, L4@512}). / 缺省距离切分，逐字源自
     *  {@link LodConfigDoc#DEFAULT_DISTANCES}（{@code L1@64, L2@128, L3@256, L4@512}）。 */
    public static List<LodConfigDoc.Distance> defaultDistances() {
        return List.copyOf(LodConfigDoc.DEFAULT_DISTANCES);
    }

    /**
     * Resolves a quality-tier form and a distance-cut list into a {@link Resolved} configuration, mirroring
     * {@code api.lod.LodApi#resolve}. The effective max level is the config default
     * ({@code defaultMaxLevel()}); the distance cuts pass through unchanged. Pure and deterministic.
     * / 将质量档注册名与距离切分列表解析为 {@link Resolved} 配置，镜像 {@code api.lod.LodApi#resolve}。有效
     * 最大细节层级为配置缺省（{@code defaultMaxLevel()}）；距离切分原样透传。纯函数且确定性。
     *
     * @param qualityTier the allowed quality-tier form (non-null).
     * @param distances   the distance cuts (non-null, valid).
     * @return a new {@link Resolved} configuration.
     * @throws NullPointerException     if either argument is null.
     * @throws IllegalArgumentException if the quality tier is not one of {@link LodConfigDoc#QUALITY_TIERS},
     *     or the distance cuts are empty / not strictly increasing.
     */
    public static Resolved resolve(String qualityTier, List<LodConfigDoc.Distance> distances) {
        Objects.requireNonNull(qualityTier, "qualityTier must not be null");
        if (!LodConfigDoc.QUALITY_TIERS.contains(qualityTier)) {
            throw new IllegalArgumentException("invalid lod qualityTier: " + qualityTier);
        }
        Objects.requireNonNull(distances, "distances must not be null");
        if (distances.isEmpty()) {
            throw new IllegalArgumentException("lod distance tiers must not be empty");
        }
        return new Resolved(defaultMaxLevel(), List.copyOf(distances));
    }

    /**
     * Projects the expected detail-level index for a squared block-space distance against a distance-cut list,
     * with the same integer semantics as {@code api.lod.LodApi#resolveLevel} and
     * {@link LodDistanceSelector#levelForDistanceSq}. Monotone non-decreasing; {@code <= 0} or below the
     * first cut yields {@code 0} (no LOD). Returned index ranges {@code 0..5}. Deterministic.
     * / 对以方块为单位的区块空间距离平方依据距离切分投影期望细节层级索引，与
     * {@code api.lod.LodApi#resolveLevel} 及 {@link LodDistanceSelector#levelForDistanceSq} 同整数语义。单调
     * 不减；{@code <= 0} 或小于首档得 {@code 0}（不启 LOD）。返回索引范围 {@code 0..5}。确定性。
     *
     * @param distances     the distance cuts (non-null).
     * @param distSqBlocks the squared block-space distance from the player.
     * @return the expected detail-level index ({@code 0..5}).
     * @throws NullPointerException if {@code distances} is null.
     */
    public static int resolveLevel(List<LodConfigDoc.Distance> distances, long distSqBlocks) {
        Objects.requireNonNull(distances, "distances must not be null");
        if (distSqBlocks <= 0) {
            return 0;
        }
        int chosen = 0;
        for (LodConfigDoc.Distance d : distances) {
            long cutSq = (long) d.distance() * (long) d.distance();
            if (distSqBlocks < cutSq) {
                break;
            }
            chosen = d.level();
        }
        return chosen;
    }
}