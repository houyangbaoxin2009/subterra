package io.toterra.subterra.runtime.worldgen.gen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.toterra.subterra.engine.worldgen.feature.FeatureAssembly;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;

/**
 * p.2.29.3 <b>规则驱动的矿石特征</b>（{@code subterra:rule_ore}）：真实生成路径上的矿物放置特征，其行为
 * 完全由 td 规则计划（{@link FeatureAssembly.Plan}）决定——所置矿石方块、<b>母岩前置</b>（只在声明的母岩
 * 上成矿）、矿脉走向（决定矿脉团块形状）。配置只携带矿物 id（结构标识），参数一律来自规则计划，缺省
 * （计划关闭 / 该矿物 count=0）即<b>不放置任何方块</b>（恒等，零行为变化）。
 *
 * <p>确定性：形状遍历固定序，随机数只取自 MC 传入的 {@code RandomSource}（由世界种子 + 特征 id + 区块
 * 确定性派生），不引入任何新随机源、不读时序。同一世界种子 + 同一规则计划 ⇒ 同字节地形。
 *
 * <p>p.2.29.3 the <b>rule-driven ore feature</b> ({@code subterra:rule_ore}): a mineral placed feature on
 * the real generation path whose behaviour is fully decided by the td rule plan
 * ({@link FeatureAssembly.Plan}) — the ore block, the <b>host-rock prerequisite</b> (only ore on the
 * declared host rock) and the vein trend (the blob shape). The config only carries the mineral id (the
 * structural identity); every parameter comes from the rule plan, and the default (plan off / that
 * mineral's count = 0) places <b>nothing</b> (identity, zero behaviour change).
 *
 * <p>Deterministic: a fixed-order shape walk and randomness taken only from the MC-provided
 * {@code RandomSource} (deterministically derived from the world seed + feature id + chunk); no new
 * random source, no timing read. Same world seed + same rule plan ⇒ byte-identical terrain.
 */
public final class SubterraRuleOreFeature extends Feature<SubterraRuleOreFeature.Config> {

    /**
     * 配置：只携带矿物 id（规则计划的查表键）。 / Config: only the mineral id (the rule-plan lookup key).
     */
    public record Config(String mineral) implements FeatureConfiguration {

        /** 不可为 null / 空。 / Never null / blank. */
        public Config {
            if (mineral == null || mineral.isBlank()) {
                throw new IllegalArgumentException("rule_ore config requires a non-blank mineral id");
            }
        }
    }

    /** 配置编解码：{@code { "mineral": "<id>" }}。 / The config codec: {@code { "mineral": "<id>" }}. */
    public static final MapCodec<Config> CONFIG_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.STRING.fieldOf("mineral").forGetter(Config::mineral)
    ).apply(instance, Config::new));

    public SubterraRuleOreFeature() {
        super(CONFIG_CODEC.codec());
    }

    @Override
    public boolean place(FeaturePlaceContext<Config> context) {
        FeatureAssembly.Plan plan = SubterraFeaturePlan.plan();
        if (!plan.enabled()) {
            return false; // identity default: nothing is placed
        }
        FeatureAssembly.Mineral rule = plan.mineral(context.config().mineral()).orElse(null);
        if (rule == null || rule.count() <= 0) {
            return false; // not declared / disabled for this mineral
        }
        BlockState ore = SubterraFeatureSupport.blockState(rule.block());
        if (ore == null) {
            return false; // unknown ore block id (deterministic no-op)
        }
        int[] d = dimensions(rule.trend());
        WorldGenLevel level = context.level();
        BlockPos origin = context.origin();
        int placed = 0;
        for (int dx = -d[0]; dx <= d[0]; dx++) {
            for (int dy = -d[1]; dy <= d[1]; dy++) {
                for (int dz = -d[2]; dz <= d[2]; dz++) {
                    double norm = sq(dx, d[0]) + sq(dy, d[1]) + sq(dz, d[2]);
                    if (norm > 1.0) {
                        continue; // outside the ellipsoid
                    }
                    BlockPos p = origin.offset(dx, dy, dz);
                    BlockState current = level.getBlockState(p);
                    if (!SubterraFeatureSupport.replaceable(current, rule.hostRock())) {
                        continue; // mineral × host-rock prerequisite
                    }
                    if (!level.ensureCanWrite(p)) {
                        continue;
                    }
                    level.setBlock(p, ore, 2);
                    placed++;
                }
            }
        }
        return placed > 0;
    }

    /** 矿脉走向 → 椭球半径（{@code {rx, ry, rz}}）；决定成矿团块形状。 /
     *  Vein trend → ellipsoid radii ({@code {rx, ry, rz}}); decides the ore blob shape. */
    private static int[] dimensions(FeatureAssembly.VeinTrend trend) {
        return switch (trend) {
            case CLUSTER -> new int[]{2, 2, 2};
            case VERTICAL -> new int[]{1, 4, 1};
            case HORIZONTAL -> new int[]{3, 1, 3};
            case DIAGONAL -> new int[]{2, 3, 2};
        };
    }

    private static double sq(int v, int r) {
        if (r <= 0) {
            return v == 0 ? 0.0 : Double.POSITIVE_INFINITY;
        }
        double t = (double) v / (double) r;
        return t * t;
    }
}
