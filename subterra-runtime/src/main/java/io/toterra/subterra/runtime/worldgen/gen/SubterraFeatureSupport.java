package io.toterra.subterra.runtime.worldgen.gen;

import io.toterra.subterra.engine.worldgen.feature.FeatureAssembly;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * p.2.29.3 特征/成矿装配面的 MC 侧纯查询助手（无状态、确定性）：把 engine 规则计划里的<b>字符串 id</b>
 * （方块 id / 方块标签）解析为 MC 对象，并实现<b>矿物 × 母岩前置</b>的判定。不修改世界、不注册、无副作用。
 *
 * <p>p.2.29.3 stateless, deterministic MC-side query helpers for the feature/ore assembly surface:
 * resolve the engine rule plan's <b>string ids</b> (block id / block tag) into MC objects and implement
 * the <b>mineral × host-rock prerequisite</b>. Never mutates the world, never registers, no side effects.
 */
final class SubterraFeatureSupport {

    private SubterraFeatureSupport() {
    }

    /** 解析方块 id 为方块（未知/坏 id → null）。 / Resolves a block id (unknown / bad id → null). */
    static Block block(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        ResourceLocation loc = ResourceLocation.tryParse(id.trim());
        return loc == null ? null : BuiltInRegistries.BLOCK.get(loc);
    }

    /** 解析方块 id 为其默认方块状态（未知 → null）。 / Resolves a block id to its default state (unknown → null). */
    static BlockState blockState(String id) {
        Block b = block(id);
        return b == null ? null : b.defaultBlockState();
    }

    /**
     * 母岩前置判定：不限母岩 → true；否则状态属于所列方块之一或所列标签之一。纯查询、确定性。 /
     * Host-rock prerequisite: any → true; otherwise the state is one of the listed blocks or matches one
     * of the listed tags. Pure query, deterministic.
     */
    static boolean hostRockMatches(BlockState state, FeatureAssembly.HostRock rock) {
        if (rock == null || rock.isAny()) {
            return true;
        }
        if (state == null) {
            return false;
        }
        for (String id : rock.blocks()) {
            Block b = block(id);
            if (b != null && state.is(b)) {
                return true;
            }
        }
        for (String tag : rock.tags()) {
            ResourceLocation loc = ResourceLocation.tryParse(tag);
            if (loc == null) {
                continue;
            }
            if (state.is(TagKey.create(Registries.BLOCK, loc))) {
                return true;
            }
        }
        return false;
    }

    /**
     * 是否可被矿脉替换：不限母岩时取石头系（{@code minecraft:base_stone_overworld}）；否则要求命中母岩。
     * 两者都天然排除了空气 / 水 / 矿石本身。纯查询、确定性。 /
     * Whether a block is vein-replaceable: stone-family ({@code minecraft:base_stone_overworld}) when the
     * host rock is unconstrained, otherwise the host-rock prerequisite must hit. Both naturally exclude
     * air / water / existing ore. Pure query, deterministic.
     */
    static boolean replaceable(BlockState state, FeatureAssembly.HostRock rock) {
        if (state == null) {
            return false;
        }
        if (rock == null || rock.isAny()) {
            return state.is(BlockTags.BASE_STONE_OVERWORLD);
        }
        return hostRockMatches(state, rock);
    }
}
