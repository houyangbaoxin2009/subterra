package io.toterra.subterra.runtime.worldgen.gen;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.toterra.subterra.engine.worldgen.feature.FeatureAssembly;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;

/**
 * p.2.29.3 <b>规则驱动的矿脉原点放置器</b>（{@code subterra:rule_vein}）：真实生成路径上的
 * {@link PlacementModifier}，其原点数量 / y 带 / <b>矿脉走向</b> / <b>母岩前置</b> / <b>结构护栏</b>
 * 全部取自 td 规则计划（{@link FeatureAssembly.Plan}）。配置只携带矿物 id；缺省（计划关闭 / count=0）
 * 即产出<b>零原点</b>（恒等，零行为变化）。
 *
 * <p>确定性：随机数只取自 MC 传入的 {@code RandomSource}（世界种子 + 特征 id + 区块确定性派生）；
 * 原点遍历固定序；护栏与母岩判定为纯查询。同一世界种子 + 同一规则计划 ⇒ 同字节原点序列。
 *
 * <p>p.2.29.3 the <b>rule-driven vein-origin placer</b> ({@code subterra:rule_vein}): a
 * {@link PlacementModifier} on the real generation path whose origin count / y band / <b>vein trend</b> /
 * <b>host-rock prerequisite</b> / <b>structure guard</b> all come from the td rule plan
 * ({@link FeatureAssembly.Plan}). The config only carries the mineral id; the default (plan off /
 * count = 0) yields <b>zero origins</b> (identity, zero behaviour change).
 *
 * <p>Deterministic: randomness only from the MC-provided {@code RandomSource} (world seed + feature id +
 * chunk); a fixed-order origin walk; the guard and host-rock checks are pure queries. Same world seed +
 * same rule plan ⇒ byte-identical origin sequence.
 */
public final class SubterraRuleVeinPlacement extends PlacementModifier {

    /** 配置编解码：{@code { "mineral": "<id>" }}。 / The config codec: {@code { "mineral": "<id>" }}. */
    public static final MapCodec<SubterraRuleVeinPlacement> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.STRING.fieldOf("mineral").forGetter(SubterraRuleVeinPlacement::mineral)
    ).apply(instance, SubterraRuleVeinPlacement::new));

    private final String mineral;

    public SubterraRuleVeinPlacement(String mineral) {
        if (mineral == null || mineral.isBlank()) {
            throw new IllegalArgumentException("rule_vein config requires a non-blank mineral id");
        }
        this.mineral = mineral;
    }

    /** 矿物 id（规则计划查表键）。 / The mineral id (rule-plan lookup key). */
    public String mineral() {
        return mineral;
    }

    @Override
    public Stream<BlockPos> getPositions(PlacementContext context, RandomSource random, BlockPos pos) {
        FeatureAssembly.Plan plan = SubterraFeaturePlan.plan();
        if (!plan.enabled()) {
            return Stream.empty(); // identity default
        }
        FeatureAssembly.Mineral rule = plan.mineral(mineral).orElse(null);
        if (rule == null || rule.count() <= 0) {
            return Stream.empty();
        }
        int span = rule.maxY() - rule.minY();
        List<BlockPos> out = new ArrayList<>(rule.count());
        for (int i = 0; i < rule.count(); i++) {
            int bx = pos.getX() + random.nextInt(16);
            int bz = pos.getZ() + random.nextInt(16);
            int by = rule.minY() + (span <= 0 ? 0 : random.nextInt(span + 1));
            int x = bx;
            int z = bz;
            int y = by;
            switch (rule.trend()) {
                case VERTICAL -> y = clampY(by + i, rule);
                case HORIZONTAL -> x = bx + i;
                case DIAGONAL -> {
                    x = bx + i;
                    z = bz + i;
                    y = clampY(by + i, rule);
                }
                case CLUSTER -> {
                    x = bx + random.nextInt(5) - 2;
                    z = bz + random.nextInt(5) - 2;
                }
            }
            if (plan.blocked(x, z)) {
                continue; // structure guard: a vein origin never crosses the guard
            }
            BlockPos origin = new BlockPos(x, y, z);
            BlockState state = context.getLevel().getBlockState(origin);
            if (!SubterraFeatureSupport.hostRockMatches(state, rule.hostRock())) {
                continue; // mineral × host-rock prerequisite
            }
            out.add(origin);
        }
        return out.stream();
    }

    @Override
    public PlacementModifierType<?> type() {
        return SubterraFeatures.RULE_VEIN;
    }

    private static int clampY(int y, FeatureAssembly.Mineral rule) {
        return Math.max(rule.minY(), Math.min(rule.maxY(), y));
    }
}
