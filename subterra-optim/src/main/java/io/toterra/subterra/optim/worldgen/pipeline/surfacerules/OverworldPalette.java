package io.toterra.subterra.optim.worldgen.pipeline.surfacerules;

import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Tiny resolver mapping vanilla block-state names used by the overworld surface
 * rules to stable string ids (p.1.8.15), so the compiled rule set is testable
 * without MC registry blocks. {@link #identity()} accepts the set of canonical
 * overworld states and returns the vanilla name as the stable id; {@link #custom(Map)}
 * lets callers rebind ids. Unknown states are rejected via IllegalArgumentException.
 * <p>
 * 把主世界表面规则所用 vanilla 方块状态名解析为稳定字符串 id 的迷你解析器（p.1.8.15），使规则集
 * 无需 MC 注册表方块即可测试。{@link #identity()} 接受主世界规范状态集并以 vanilla 名作为稳定 id；
 * {@link #custom(Map)} 允许调用方重绑 id。未知状态经 IllegalArgumentException 拒绝。
 */
@FunctionalInterface
public interface OverworldPalette {

    /** The canonical overworld block-state names referenced by {@link OverworldSurfaceRules}. */
    Set<String> OVERWORLD_STATES = Set.of(
            "minecraft:bedrock",
            "minecraft:deepslate",
            "minecraft:stone",
            "minecraft:sand",
            "minecraft:red_sand",
            "minecraft:gravel",
            "minecraft:grass_block",
            "minecraft:dirt",
            "minecraft:snow_block",
            "minecraft:ice",
            "minecraft:water",
            "minecraft:terracotta");

    /**
     * Resolves a vanilla block-state name to a stable id.
     * @param stateId the vanilla state name (e.g. {@code "minecraft:grass_block"})
     * @return the stable id (never {@code null})
     * @throws IllegalArgumentException if the state is unknown to this palette
     */
    String resolve(String stateId);

    /** The set of deterministic palettes, used to enumerate referenced states. */
    default Set<String> states() {
        return Set.of();
    }

    /** Identity palette: returns the canonical vanilla name as the stable id. */
    static OverworldPalette identity() {
        return new_IdentityPalette();
    }

    /** Custom palette: maps vanilla names to caller-provided ids (unknown keys rejected). */
    static OverworldPalette custom(Map<String, String> mapping) {
        return new_CustomPalette(mapping);
    }

    /** Package-private identity implementation. */
    private static OverworldPalette new_IdentityPalette() {
        return new OverworldPalette() {
            @Override
            public String resolve(String stateId) {
                if (stateId == null || !OVERWORLD_STATES.contains(stateId)) {
                    throw new IllegalArgumentException("unknown overworld state: " + stateId);
                }
                return stateId;
            }

            @Override
            public Set<String> states() {
                return new TreeSet<>(OVERWORLD_STATES);
            }
        };
    }

    /** Package-private custom implementation. */
    private static OverworldPalette new_CustomPalette(Map<String, String> mapping) {
        final Map<String, String> m = Map.copyOf(mapping);
        return new OverworldPalette() {
            @Override
            public String resolve(String stateId) {
                if (stateId == null || !m.containsKey(stateId)) {
                    throw new IllegalArgumentException("unknown state in custom palette: " + stateId);
                }
                return m.get(stateId);
            }

            @Override
            public Set<String> states() {
                return new TreeSet<>(m.keySet());
            }
        };
    }
}