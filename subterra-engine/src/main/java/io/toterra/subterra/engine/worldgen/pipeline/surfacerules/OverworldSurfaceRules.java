package io.toterra.subterra.engine.worldgen.pipeline.surfacerules;

import io.toterra.subterra.engine.worldgen.pipeline.surface.SurfaceRule;

import java.util.List;

/**
 * Compiles the vanilla overworld default surface-rule set (p.1.8.15, clean-room)
 * onto the p.1.8.5 {@link SurfaceRule} seam. A faithful material-layering
 * translation of {@code SurfaceRuleData.overworld()} /
 * {@code surface_rule/overworld.json}: the standard {@code bedrock_floor} band,
 * grass/dirt/water/sand/gravel band layers, the eroded (badlands) variant and the
 * subsurface/frozen extras. The rich vanilla conditions (above-preliminary-surface,
 * stone-depth) are lowered to structural y-band + biome guards so the compiled set
 * is a pure function of the p.1.8.5 {@code SurfaceContext}; the faithful predicate
 * forms are exercised as vocabulary in {@link VRuleCondition}.
 * <p>
 * 把 vanilla 主世界默认表面规则集（p.1.8.15，洁净房）编译到 p.1.8.5 {@link SurfaceRule} 接缝。
 * 为 {@code SurfaceRuleData.overworld()} / {@code surface_rule/overworld.json} 的忠实材质分层译本：
 * 标准 {@code bedrock_floor} 带、草丛/泥土/水/沙/砾带层、侵蚀（恶地）变体与地下/冰雪附加规则。
 * 化约以 y 带 + 群系守卫表达，使编译集成为 p.1.8.5 {@code SurfaceContext} 的纯函数。
 */
public final class OverworldSurfaceRules {

    private OverworldSurfaceRules() {
    }

    private static final String[] DEEP_OCEAN = {
            "minecraft:deep_ocean", "minecraft:deep_cold_ocean",
            "minecraft:deep_lukewarm_ocean", "minecraft:deep_frozen_ocean"};
    private static final String[] SHALLOW_WATER = {
            "minecraft:ocean", "minecraft:cold_ocean", "minecraft:lukewarm_ocean",
            "minecraft:warm_ocean", "minecraft:frozen_ocean", "minecraft:river",
            "minecraft:beach", "minecraft:stony_shore"};
    private static final String[] BADLANDS = {
            "minecraft:badlands", "minecraft:eroded_badlands", "minecraft:wooded_badlands"};
    private static final String[] SNOWY = {
            "minecraft:snowy_plains", "minecraft:snowy_taiga", "minecraft:snowy_slopes",
            "minecraft:ice_spikes", "minecraft:grove"};
    private static final String[] LAND = {
            "minecraft:plains", "minecraft:forest", "minecraft:birch_forest",
            "minecraft:dark_forest", "minecraft:old_growth_birch_forest",
            "minecraft:old_growth_pine_taiga", "minecraft:old_growth_spruce_taiga",
            "minecraft:taiga", "minecraft:meadow", "minecraft:sunflower_plains",
            "minecraft:flower_forest", "minecraft:windswept_forest"};

    /**
     * Compiles the overworld default material rules in first-match order
     * (specific biome/y rules first, generic filler bands last). Each rule's
     * vanilla origin is named in the grouping comments.
     * 按首配命中的顺序编译主世界默认材质规则（具体群系/y 规则在前、通用填充带在后），
     * 每条规则的 vanilla 出处标注在分组注释中。
     *
     * @param palette state-name resolver (see {@link OverworldPalette})
     */
    public static List<SurfaceRule> overworld(OverworldPalette palette) {
        return VRuleSource.sequence(
                // -- eroded variant (vanilla eroded_badlands mesa materials) --
                VRuleSource.condition(VRuleCondition.biomeMatch(BADLANDS),
                        VRuleSource.condition(VRuleCondition.yRange(0, 62),
                                VRuleSource.block("minecraft:red_sand"))),
                // -- seafloor gravel (vanilla deep ocean floor) --
                VRuleSource.condition(VRuleCondition.biomeMatch(DEEP_OCEAN),
                        VRuleSource.condition(VRuleCondition.yRange(0, 62),
                                VRuleSource.block("minecraft:gravel"))),
                // -- seafloor sand (vanilla shallow water / river / beach floor) --
                VRuleSource.condition(VRuleCondition.biomeMatch(SHALLOW_WATER),
                        VRuleSource.condition(VRuleCondition.yRange(0, 62),
                                VRuleSource.block("minecraft:sand"))),
                // -- frozen extras: packed ice on the frozen river floor --
                VRuleSource.condition(VRuleCondition.biomeMatch("minecraft:frozen_river"),
                        VRuleSource.condition(VRuleCondition.yRange(0, 62),
                                VRuleSource.block("minecraft:ice"))),
                // -- water extras: swamp (dangerous swamp) surface water --
                VRuleSource.condition(VRuleCondition.biomeMatch("minecraft:swamp"),
                        VRuleSource.condition(VRuleCondition.yRange(60, 63),
                                VRuleSource.block("minecraft:water"))),
                // -- land under-floor dirt layer (grass band foundation) --
                VRuleSource.condition(VRuleCondition.biomeMatch(LAND),
                        VRuleSource.condition(VRuleCondition.yRange(56, 62),
                                VRuleSource.block("minecraft:dirt"))),
                // -- frozen extras: snow blanket on snowy lands --
                VRuleSource.condition(VRuleCondition.biomeMatch(SNOWY),
                        VRuleSource.condition(VRuleCondition.aboveY(63),
                                VRuleSource.block("minecraft:snow_block"))),
                // -- grass band: green surface on tempered lands above sea level --
                VRuleSource.condition(VRuleCondition.biomeMatch(LAND),
                        VRuleSource.condition(VRuleCondition.aboveY(63),
                                VRuleSource.block("minecraft:grass_block"))),
                // -- bedrock_floor band (vanilla 5-layer bottom bedrock floor) --
                VRuleSource.band(-64, -60, VRuleSource.block("minecraft:bedrock")),
                // -- subsurface extras: deep filler transition (deepslate below y<8) --
                VRuleSource.band(-59, 7, VRuleSource.block("minecraft:deepslate")),
                // -- subsurface extras: mid filler stone --
                VRuleSource.band(8, 55, VRuleSource.block("minecraft:stone"))
        ).compile(palette);
    }

    /** Compiles with the identity palette. 以恒等调色板编译。 */
    public static List<SurfaceRule> overworldDefault() {
        return overworld(OverworldPalette.identity());
    }
}