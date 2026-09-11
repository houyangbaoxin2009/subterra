package io.toterra.subterra.api.lod;

import java.util.List;
import java.util.Objects;

/**
 * p.2.28.6 对外 LOD 契约门面（final class、静态纯函数、纯 JDK）：确定性接口面，供上层/域模组按
 * 「质量档 + 距离档」解析 LOD 配置。语义与 {@code engine.render.lod} 的
 * {@code LodLevel}/{@code LodConfigDoc}/{@code LodDistanceSelector}（p.2.28.1/.3）一致——本处为契约与
 * 数据面注入，engine 为实现镜像（{@code engine.render.lod.LodApiMirror}），api 不依赖 engine。所有契约
 * 常量均系从 engine 实际常量逐字对照落于此，禁止拍脑袋数值。
 * <p>确定性：{@link #tiers()} 返回固定序 {@code STANDARD, HIGH, LOW}；
 * {@link #defaultQuality()} 返回 {@code STANDARD}（engine 默认档）；{@link #resolve} 为纯解析、同输入
 * 恒得同输出；{@link #resolveLevel} 以纯整数方块距离平方执行 {engine {@link
 * LodDistanceSelector#levelForDistanceSq}-同语义的分档（{@code distSqBlocks <= 0} 或小于首档得
 * {@code 0} = 不启 LOD，余者落到距离不超其平方的首档级）。无随机、无墙钟、无迭代序依赖。无状态、无副作用。
 * <p>契约常量：{@link #DEFAULT_QUALITY}={@code STANDARD}；{@link #DEFAULT_MAX_LEVEL}=4（镜像
 * {@code LodConfigDoc} 缺省）；{@link #BLOCKS_PER_CHUNK}=16（镜像 {@code LodConfigDoc} 的
 * {@code blockSpan/chunkSpan}）；缺省距离表 {@link #defaultDistanceSpec()} =
 * {@code L1@64, L2@128, L3@256, L4@512}（镜像 {@code LodConfigDoc.DEFAULT_DISTANCES}）。
 * <p>
 * p.2.28.6 the external LOD contract facade (final class, static pure functions, pure JDK): a deterministic
 * interface surface for upper layers / domain mods to resolve LOD configuration from a "quality tier +
 * distance tier". Semantics match {@code engine.render.lod}'s {@code LodLevel}/{@code LodConfigDoc}/
 * {@code LodDistanceSelector} (p.2.28.1/.3) — this is the contract and data-injection surface for the engine
 * to mirror as its implementation ({@code engine.render.lod.LodApiMirror}), and the api does not depend on the
 * engine. Every contract constant here is pinned verbatim from the engine's actual constants — nothing is
 * guessed.
 * <p>Deterministic: {@link #tiers()} returns the fixed order {@code STANDARD, HIGH, LOW};
 * {@link #defaultQuality()} returns {@code STANDARD} (the engine default tier); {@link #resolve} is a pure
 * resolution, same input always yields the same output; {@link #resolveLevel} executes the same-semantics
 * tiering as the engine {@link LodDistanceSelector} on pure integer squared-block-distance
 * ({@code distSqBlocks <= 0} or below the first cut yields {@code 0} = no LOD, otherwise the level whose
 * cut it falls under). No randomness, no wall-clock, no iteration-order dependence. Stateless, side-effect free.
 * <p>Contract constants: {@link #DEFAULT_QUALITY}={@code STANDARD}; {@link #DEFAULT_MAX_LEVEL}=4 (mirrors
 * {@code LodConfigDoc} default); {@link #BLOCKS_PER_CHUNK}=16 (mirrors {@code LodConfigDoc}'s
 * {@code blockSpan/chunkSpan}); the default distance table {@link #defaultDistanceSpec()} =
 * {@code L1@64, L2@128, L3@256, L4@512} (mirrors {@code LodConfigDoc.DEFAULT_DISTANCES}).
 */
public final class LodApi {

    /** The default max detail level (= 4, mirrors {@code LodConfigDoc} default {@code maxLevel}). /
     *  缺省最大细节层级（= 4，镜像 {@code LodConfigDoc} 缺省 {@code maxLevel}）。 */
    public static final int DEFAULT_MAX_LEVEL = 4;

    /** How many blocks in one chunk (= 16, mirrors {@code LodConfigDoc} {@code blockSpan/chunkSpan}). /
     *  一个区块的方块数（= 16，镜像 {@code LodConfigDoc} {@code blockSpan/chunkSpan}）。 */
    public static final int BLOCKS_PER_CHUNK = 16;

    /** The default quality tier ({@code STANDARD}, mirrors {@code LodConfigDoc.QUALITY_TIERS.get(0)}). /
     *  缺省质量档（{@code STANDARD}，镜像 {@code LodConfigDoc.QUALITY_TIERS.get(0)}）。 */
    public static final LodQualitySpec DEFAULT_QUALITY = LodQualitySpec.STANDARD;

    /** Allowed quality-tier forms in fixed canonical order, mirroring
     *  {@code engine.render.lod.LodConfigDoc.QUALITY_TIERS} verbatim. / 允许的质量档小写注册名，固定规范序，
     *  逐字镜像 {@code engine.render.lod.LodConfigDoc.QUALITY_TIERS}。 */
    public static final List<String> QUALITY_TIER_FORMS =
            List.of("standard", "high", "low");

    /** The resolution product of {@link #resolve}: the effective max detail level plus the distance spec. /
     *  {@link #resolve} 的解析产物：有效最大细节层级 + 距离 spec。 */
    public record Resolved(int maxLevel, LodDistanceSpec distances) {

        /**
         * Validates both components non-null/consistent. / 校验两分量均非 null/一致。
         *
         * @throws NullPointerException if {@code distances} is null.
         */
        public Resolved {
            Objects.requireNonNull(distances, "distances must not be null");
        }
    }

    private LodApi() {
    }

    /**
     * The fixed-order quality tiers ({@code STANDARD, HIGH, LOW}). Deterministic; identical on every call.
     * / 固定序质量档（{@code STANDARD, HIGH, LOW}）。确定性；每次调用均相同。
     */
    public static List<LodQualitySpec> tiers() {
        return List.of(LodQualitySpec.values());
    }

    /** The default quality tier ({@code STANDARD}). / 缺省质量档（{@code STANDARD}）。 */
    public static LodQualitySpec defaultQuality() {
        return DEFAULT_QUALITY;
    }

    /** The default distance spec ({@code L1@64, L2@128, L3@256, L4@512}, mirrors
     *  {@code LodConfigDoc.DEFAULT_DISTANCES}). / 缺省距离 spec（{@code L1@64, L2@128, L3@256, L4@512}，
     *  镜像 {@code LodConfigDoc.DEFAULT_DISTANCES}）。 */
    public static LodDistanceSpec defaultDistanceSpec() {
        return LodDistanceSpec.defaults();
    }

    /**
     * Resolves a quality tier and a distance spec into a {@link Resolved} configuration. The effective max
     * level is taken from the quality tier's {@code maxLevel} ({@code = DEFAULT_MAX_LEVEL}), the distance
     * spec passes through unchanged. Pure and deterministic: same inputs always yield the same result.
     * / 将质量档与距离 spec 解析为 {@link Resolved} 配置。有效最大细节层级取自质量档的 {@code maxLevel}
     * （{@code = DEFAULT_MAX_LEVEL}），距离 spec 原样透传。纯函数且确定性：同输入恒得同结果。
     *
     * @param quality  the quality tier (non-null).
     * @param distance the distance spec (non-null).
     * @return a new {@link Resolved} configuration.
     * @throws NullPointerException if either argument is null.
     */
    public static Resolved resolve(LodQualitySpec quality, LodDistanceSpec distance) {
        Objects.requireNonNull(quality, "quality must not be null");
        return new Resolved(quality.maxLevel(), Objects.requireNonNull(distance, "distance must not be null"));
    }

    /**
     * Projects the expected detail-level index for a squared block-space distance
     * ({@code distSqBlocks = (Δx*16)² + (Δz*16)²}) against a distance spec, with the same integer semantics as
     * {@code engine.render.lod.LodDistanceSelector#levelForDistanceSq}. Monotone non-decreasing: farther →
     * coarser; {@code <= 0} or below the first cut yields {@code 0} (no LOD); the farthest tier absorbs
     * everything beyond it. Returned index ranges {@code 0..5} (level index into {@code LodLevel.values()}).
     * Deterministic. / 对以方块为单位的区块空间距离平方（{@code distSqBlocks}）依据距离 spec 投影期望细节层
     * 级索引，与 {@code engine.render.lod.LodDistanceSelector#levelForDistanceSq} 同整数语义。单调不减：越远
     * 越粗；{@code <= 0} 或小于首档得 {@code 0}（不启 LOD）；最后一档吸收其外一切。返回索引范围 {@code 0..5}
     * （即 {@code LodLevel.values()} 的层级索引）。确定性。
     *
     * @param distance     the distance spec (non-null).
     * @param distSqBlocks the squared block-space distance from the player.
     * @return the expected detail-level index ({@code 0..5}).
     * @throws NullPointerException if {@code distance} is null.
     */
    public static int resolveLevel(LodDistanceSpec distance, long distSqBlocks) {
        Objects.requireNonNull(distance, "distance must not be null");
        if (distSqBlocks <= 0) {
            return 0;
        }
        int chosen = 0;
        for (LodDistanceSpec.Cut c : distance.tiers()) {
            long cutSq = (long) c.distance() * (long) c.distance();
            if (distSqBlocks < cutSq) {
                break; // farther cuts are even larger; stop at the first exceeded cut
            }
            chosen = c.level();
        }
        return chosen;
    }
}