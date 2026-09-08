package io.toterra.subterra.probes;

import io.toterra.subterra.engine.worldgen.pipeline.density.Density;
import io.toterra.subterra.engine.worldgen.pipeline.surface.SurfaceContext;
import io.toterra.subterra.engine.worldgen.pipeline.surface.SurfaceEvaluator;
import io.toterra.subterra.engine.worldgen.pipeline.surface.SurfaceRule;
import io.toterra.subterra.engine.worldgen.pipeline.surfacerules.CaveSurface;
import io.toterra.subterra.engine.worldgen.pipeline.surfacerules.OverworldPalette;
import io.toterra.subterra.engine.worldgen.pipeline.surfacerules.OverworldSurfaceRules;
import io.toterra.subterra.engine.worldgen.pipeline.surfacerules.VRuleCondition;
import io.toterra.subterra.engine.worldgen.pipeline.surfacerules.VRuleSource;
import io.toterra.subterra.engine.worldgen.pipeline.surfacerules.VSurfaceContext;

import java.util.List;

/**
 * Deterministic acceptance probe for the p.1.8.15 vanilla surface-rules bridge:
 * the {@code SurfaceRules} rule-source vocabulary (band/steep/stone-depth/
 * y-condition/not/biome/noise-threshold/water/vertical-gradient) compiled onto the
 * p.1.8.5 seam, and the overworld default rule set. Pure JVM, no randomness, no
 * timing assertions.
 */
public final class SurfaceBridgeProbe {

    private SurfaceBridgeProbe() {
    }

    private static int failures = 0;
    private static int checks = 0;

    private static void check(String name, boolean ok) {
        checks++;
        if (ok) {
            System.out.println("[PASS] " + name);
        } else {
            failures++;
            System.out.println("[FAIL] " + name);
        }
    }

    public static void main(String[] args) {

        // ---- vocabulary: y-condition inclusive bounds ----
        VRuleCondition yCond = VRuleCondition.yRange(3, 8);
        check("yRange min-edge true", yCond.test(VSurfaceContext.at(0, 3, 0)));
        check("yRange max-edge true", yCond.test(VSurfaceContext.at(0, 8, 0)));
        check("yRange below-min false", !yCond.test(VSurfaceContext.at(0, 2, 0)));
        check("yRange above-max false", !yCond.test(VSurfaceContext.at(0, 9, 0)));

        // ---- vocabulary: band regions (y-band gating) ----
        List<SurfaceRule> bandRules = VRuleSource.band(0, 10, VRuleSource.block("minecraft:stone")).compile(OverworldPalette.identity());
        check("band inner hit", "minecraft:stone".equals(eval(bandRules, 0, 5, 0, null, "minecraft:air")));
        check("band top-edge hit", "minecraft:stone".equals(eval(bandRules, 0, 10, 0, null, "minecraft:air")));
        check("band above miss", "minecraft:air".equals(eval(bandRules, 0, 11, 0, null, "minecraft:air")));
        check("band below miss", "minecraft:air".equals(eval(bandRules, 0, -1, 0, null, "minecraft:air")));

        // ---- vocabulary: biome match (exact + multi OR) ----
        VRuleCondition single = VRuleCondition.biomeMatch("minecraft:forest");
        check("biome exact true", single.test(VSurfaceContext.at(1, 2, 3, "minecraft:forest")));
        check("biome exact miss", !single.test(VSurfaceContext.at(1, 2, 3, "minecraft:desert")));
        VRuleCondition multi = VRuleCondition.biomeMatch("minecraft:forest", "minecraft:plains");
        check("biome multi OR hit", multi.test(VSurfaceContext.at(1, 2, 3, "minecraft:plains")));
        check("biome multi OR miss", !multi.test(VSurfaceContext.at(1, 2, 3, "minecraft:ocean")));
        check("biome null-safe miss", !single.test(VSurfaceContext.at(1, 2, 3, null)));

        // ---- vocabulary: not ----
        VRuleCondition notCond = VRuleCondition.not(VRuleCondition.belowY(5));
        check("not inverts true->false", !notCond.test(VSurfaceContext.at(0, 2, 0)));
        check("not inverts false->true", notCond.test(VSurfaceContext.at(0, 9, 0)));

        // ---- vocabulary: noise threshold vs a tiny Density at (x,0,z) ----
        Density noise = (x, y, z) -> x * 0.5 + z * 1.5;
        VRuleCondition banded = VRuleCondition.noiseThreshold(noise, 2.0, 4.0);
        // x=2,z=1 -> 1+1.5=2.5 within [2,4].
        check("noiseThreshold inside true", banded.test(VSurfaceContext.at(2, 9, 1)));
        // x=0,z=0 -> 0.0 outside [2,4].
        check("noiseThreshold below false", !banded.test(VSurfaceContext.at(0, 9, 0)));
        // x=4,z=1 -> 2+1.5=3.5; use value exactly 4.0 for max-edge: x=5,z=1 -> 2.5+1.5=4.0 (max-edge inclusive).
        check("noiseThreshold max-edge true", banded.test(VSurfaceContext.at(5, 9, 1)));
        VRuleCondition singleT = VRuleCondition.noiseThreshold(noise, 4.0);
        check("noiseThreshold single exact", singleT.test(VSurfaceContext.at(5, 9, 1)));
        check("noiseThreshold single off-by-tiny false", !singleT.test(VSurfaceContext.at(4, 9, 1)));

        // ---- vocabulary: stone depth above (floor) with addSurfaceDepth ----
        VRuleCondition floor0 = VRuleCondition.stoneDepth(0, true, 0, CaveSurface.FLOOR);
        // surfaceDepth=0, stoneDepthAbove=0 <= 1+0+0 -> on floor.
        check("stoneDepth floor top true", floor0.test(VSurfaceContext.at(0, 60, 0).withStoneDepthAbove(0)));
        // stoneDepthAbove=3 <= 1 -> false: three layers down is beyond on-floor.
        check("stoneDepth floor deep false", !floor0.test(VSurfaceContext.at(0, 60, 0).withStoneDepthAbove(3)));
        // addSurfaceDepth: surfaceDepth pushes the boundary outward.
        VRuleCondition floorOffset = VRuleCondition.stoneDepth(1, true, 0, CaveSurface.FLOOR);
        check("stoneDepth floor offset extends", floorOffset.test(VSurfaceContext.at(0, 60, 0).withSurfaceDepth(2).withStoneDepthAbove(1)));

        // ---- vocabulary: stone depth below (ceiling) ----
        VRuleCondition ceil0 = VRuleCondition.stoneDepth(0, false, 0, CaveSurface.CEILING);
        // ceiling reads stoneDepthBelow; depth 0 <= 1+0 -> true.
        check("stoneDepth ceiling top true", ceil0.test(VSurfaceContext.at(0, 30, 0).withStoneDepthBelow(0)));
        check("stoneDepth ceiling deep false", !ceil0.test(VSurfaceContext.at(0, 30, 0).withStoneDepthBelow(7)));

        // ---- vocabulary: water block ----
        VRuleCondition water = VRuleCondition.water();
        // No water source -> true.
        check("water no-source true", water.test(VSurfaceContext.at(1, 30, 1)));
        // With water at 63: y=64 at/above the line -> true.
        check("water above-line true", water.test(VSurfaceContext.at(1, 64, 1).withWaterHeight(63)));
        // y=62 below the line -> false.
        check("water below-line false", !water.test(VSurfaceContext.at(1, 62, 1).withWaterHeight(63)));
        // water at exactly the line (offset 0) -> true (>=).
        check("water at-line true", water.test(VSurfaceContext.at(1, 63, 1).withWaterHeight(63)));

        // ---- vocabulary: steep threshold ----
        VSurfaceContext.HeightAt flat = (x, z) -> 100;
        VSurfaceContext flatCtx = VSurfaceContext.at(8, 100, 8).withHeightAt(flat);
        check("steep flat false", !VRuleCondition.steep().test(flatCtx));
        // rising surface toward +z by 3 per column: at z=5, h(z+1)=118 vs h(z-1)+4=116 -> steep.
        VSurfaceContext.HeightAt rising = (x, z) -> 100 + 3 * z;
        check("steep rising true", VRuleCondition.steep().test(VSurfaceContext.at(8, 100, 5).withHeightAt(rising)));
        // clamped by a steep x-cliff toward -x.
        VSurfaceContext.HeightAt cliffX = (x, z) -> 100 + 5 * (15 - Math.min(x, 8));
        check("steep x-cliff true", VRuleCondition.steep().test(VSurfaceContext.at(6, 100, 8).withHeightAt(cliffX)));
        // no provider -> false (flat default).
        check("steep no-provider false", !VRuleCondition.steep().test(VSurfaceContext.at(8, 100, 8)));

        // ---- vocabulary: vertical gradient (deterministic, monotonic) ----
        VRuleCondition vg = VRuleCondition.verticalGradient("minecraft:test", 0, 100);
        check("verticalGradient below-below true", vg.test(VSurfaceContext.at(3, 0, 7)));
        check("verticalGradient above-above false", !vg.test(VSurfaceContext.at(3, 100, 7)));
        // deterministic: same coordinate, same result.
        check("verticalGradient deterministic", vg.test(VSurfaceContext.at(3, 50, 7)) == vg.test(VSurfaceContext.at(3, 50, 7)));

        // ---- vocabulary: sequence ordering = first-match ----
        List<SurfaceRule> seq = VRuleSource.sequence(
                VRuleSource.condition(VRuleCondition.belowY(5), VRuleSource.block("minecraft:stone")),
                VRuleSource.block("minecraft:grass_block")
        ).compile(OverworldPalette.identity());
        check("sequence first-match hit", "minecraft:stone".equals(eval(seq, 0, 3, 0, null, "minecraft:air")));
        check("sequence later-match fallthrough", "minecraft:grass_block".equals(eval(seq, 0, 9, 0, null, "minecraft:air")));

        // ---- overworld: compiles non-empty + deterministic ----
        List<SurfaceRule> ow = OverworldSurfaceRules.overworldDefault();
        check("overworld non-empty", !ow.isEmpty());
        List<SurfaceRule> ow2 = OverworldSurfaceRules.overworldDefault();
        // Compiled rules hold predicate/action lambdas, which have no value equality across
        // separate compilations; determinism is asserted via identical size + identical column outcomes.
        boolean det = ow.size() == ow2.size();
        det &= eval(ow, 8, -62, 8, null, "minecraft:stone").equals(eval(ow2, 8, -62, 8, null, "minecraft:stone"));
        det &= eval(ow, 8, 0, 8, null, "minecraft:stone").equals(eval(ow2, 8, 0, 8, null, "minecraft:stone"));
        det &= eval(ow, 120, 60, 120, "minecraft:ocean", "minecraft:stone").equals(eval(ow2, 120, 60, 120, "minecraft:ocean", "minecraft:stone"));
        det &= eval(ow, 8, 64, 8, "minecraft:plains", "minecraft:stone").equals(eval(ow2, 8, 64, 8, "minecraft:plains", "minecraft:stone"));
        det &= eval(ow, 70, 40, 70, "minecraft:eroded_badlands", "minecraft:stone").equals(eval(ow2, 70, 40, 70, "minecraft:eroded_badlands", "minecraft:stone"));
        check("overworld deterministic", det);
        check("overworld stable-eval", eval(ow, 8, 64, 8, "minecraft:plains", "minecraft:stone").equals(eval(ow, 8, 64, 8, "minecraft:plains", "minecraft:stone")));

        // ---- overworld: canonical columns ----
        // deep cave near world bottom -> bedrock-floor band.
        check("overworld deep cave bedrock", "minecraft:bedrock".equals(eval(ow, 8, -62, 8, null, "minecraft:stone")));
        // deep subsurface below y=8 -> deepslate.
        check("overworld deep subsurface deepslate", "minecraft:deepslate".equals(eval(ow, 8, 0, 8, null, "minecraft:stone")));
        // mid filler -> stone.
        check("overworld mid filler stone", "minecraft:stone".equals(eval(ow, 8, 30, 8, null, "minecraft:stone")));
        // seafloor (y=60 underwater, sea level 63) shallow ocean -> sand.
        check("overworld seafloor sand", "minecraft:sand".equals(eval(ow, 120, 60, 120, "minecraft:ocean", "minecraft:stone")));
        // deep-ocean floor -> gravel.
        check("overworld deep-ocean floor gravel", "minecraft:gravel".equals(eval(ow, 120, 60, 120, "minecraft:deep_ocean", "minecraft:stone")));
        // surface grass at y=63+ on land -> grass_block.
        check("overworld surface grass", "minecraft:grass_block".equals(eval(ow, 8, 64, 8, "minecraft:plains", "minecraft:stone")));
        // land under-floor -> dirt.
        check("overworld land underfloor dirt", "minecraft:dirt".equals(eval(ow, 8, 60, 8, "minecraft:plains", "minecraft:stone")));
        // snowy land -> snow blanket.
        check("overworld snowy snow_block", "minecraft:snow_block".equals(eval(ow, 8, 64, 8, "minecraft:snowy_taiga", "minecraft:stone")));
        // swamp surface water extras.
        check("overworld swamp water", "minecraft:water".equals(eval(ow, 130, 62, 130, "minecraft:swamp", "minecraft:stone")));
        // eroded badlands differ from flat: badlands -> red_sand, plains -> stone filler.
        check("overworld eroded red_sand", "minecraft:red_sand".equals(eval(ow, 70, 40, 70, "minecraft:eroded_badlands", "minecraft:stone")));
        check("overworld flat-plain filler stone", "minecraft:stone".equals(eval(ow, 70, 40, 70, "minecraft:plains", "minecraft:stone")));

        // ---- overworld: palette completeness (every referenced state resolvable) ----
        boolean allResolvable = true;
        for (String s : OverworldPalette.identity().states()) {
            try {
                OverworldPalette.identity().resolve(s);
            } catch (RuntimeException e) {
                allResolvable = false;
            }
        }
        check("overworld palette all-resolvable", allResolvable && !OverworldPalette.identity().states().isEmpty());
        // the concrete overworld set only references known/id states (compile already resolved).
        List<SurfaceRule> owCustom = OverworldSurfaceRules.overworld(OverworldPalette.custom(
                java.util.Map.ofEntries(
                        java.util.Map.entry("minecraft:bedrock", "id:bedrock"),
                        java.util.Map.entry("minecraft:deepslate", "id:deepslate"),
                        java.util.Map.entry("minecraft:stone", "id:stone"),
                        java.util.Map.entry("minecraft:sand", "id:sand"),
                        java.util.Map.entry("minecraft:red_sand", "id:red_sand"),
                        java.util.Map.entry("minecraft:gravel", "id:gravel"),
                        java.util.Map.entry("minecraft:grass_block", "id:grass"),
                        java.util.Map.entry("minecraft:dirt", "id:dirt"),
                        java.util.Map.entry("minecraft:snow_block", "id:snow"),
                        java.util.Map.entry("minecraft:ice", "id:ice"),
                        java.util.Map.entry("minecraft:water", "id:water"),
                        java.util.Map.entry("minecraft:terracotta", "id:terracotta"))));
        check("overworld custom-palette rewrite", "id:grass".equals(eval(owCustom, 8, 64, 8, "minecraft:plains", "id:stone")));

        // ---- validation ----
        boolean threwInverted = false;
        try {
            VRuleSource.band(5, 3, VRuleSource.block("minecraft:stone"));
        } catch (IllegalArgumentException e) {
            threwInverted = true;
        }
        check("invalid band rejected", threwInverted);

        System.out.println("[SurfaceBridgeProbe] " + (failures == 0 ? "PASS" : "FAIL") + " (" + checks + " checks)");
        System.exit(failures == 0 ? 0 : 1);
    }

    private static String eval(List<SurfaceRule> rules, int x, int y, int z, String biome, String defaultState) {
        return SurfaceEvaluator.evaluate(new SurfaceContext(x, y, z, biome), rules, defaultState);
    }
}