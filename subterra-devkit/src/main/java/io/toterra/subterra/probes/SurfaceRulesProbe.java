package io.toterra.subterra.probes;

import io.toterra.subterra.optim.worldgen.pipeline.surface.SurfaceAction;
import io.toterra.subterra.optim.worldgen.pipeline.surface.SurfaceActions;
import io.toterra.subterra.optim.worldgen.pipeline.surface.SurfaceCondition;
import io.toterra.subterra.optim.worldgen.pipeline.surface.SurfaceConditions;
import io.toterra.subterra.optim.worldgen.pipeline.surface.SurfaceContext;
import io.toterra.subterra.optim.worldgen.pipeline.surface.SurfaceEvaluator;
import io.toterra.subterra.optim.worldgen.pipeline.surface.SurfaceRule;

import java.util.List;

/**
 * Deterministic acceptance probe for the p.1.8.5 surface-rules core:
 * conditions (height-range/below-Y/biome/density), boolean combinators and the
 * ordered evaluator (first-hit priority, decline-on-null, default fallback).
 * Pure JVM: no randomness, no timing assertions.
 */
public final class SurfaceRulesProbe {

    private SurfaceRulesProbe() {
    }

    private static int failures = 0;

    private static void check(String name, boolean ok) {
        if (ok) {
            System.out.println("[PASS] " + name);
        } else {
            failures++;
            System.out.println("[FAIL] " + name);
        }
    }

    public static void main(String[] args) {
        // heightRange: inclusive min bound -> hit.
        check("heightRange min hit", SurfaceConditions.heightRange(3, 8).test(new SurfaceContext(0, 3, 0)));
        // heightRange: inclusive max bound -> hit.
        check("heightRange max hit", SurfaceConditions.heightRange(3, 8).test(new SurfaceContext(0, 8, 0)));
        // heightRange: one below min -> miss.
        check("heightRange below-min miss", !SurfaceConditions.heightRange(3, 8).test(new SurfaceContext(0, 2, 0)));
        // heightRange: one above max -> miss.
        check("heightRange above-max miss", !SurfaceConditions.heightRange(3, 8).test(new SurfaceContext(0, 9, 0)));

        // belowY: y-1 -> hit.
        check("belowY y-1 hit", SurfaceConditions.belowY(10).test(new SurfaceContext(0, 9, 0)));
        // belowY: y -> miss (strict <).
        check("belowY y miss", !SurfaceConditions.belowY(10).test(new SurfaceContext(0, 10, 0)));

        // biome: equal tag -> hit.
        check("biome equal hit", SurfaceConditions.biome("minecraft:forest").test(new SurfaceContext(1, 2, 3, "minecraft:forest")));
        // biome: different tag -> miss.
        check("biome different miss", !SurfaceConditions.biome("minecraft:forest").test(new SurfaceContext(1, 2, 3, "minecraft:desert")));
        // biome: two tags via or -> second tag hits. (双 tag 组合)
        check("biome double-tag or hits",
                SurfaceConditions.or(SurfaceConditions.biome("minecraft:forest"),
                        SurfaceConditions.biome("minecraft:plains"))
                        .test(new SurfaceContext(1, 2, 3, "minecraft:plains")));
        // biome: null tag vs non-null condition -> miss (null-safe equal).
        check("biome null-safe miss", !SurfaceConditions.biome("a").test(new SurfaceContext(1, 2, 3, null)));

        // densityAbove: strictly greater than threshold hits.
        check("densityAbove above hit", SurfaceConditions.densityAbove(0.5).test(new SurfaceContext(0, 0, 0, null, 0.75)));
        // densityAbove: exactly equal to threshold -> miss (strict >).
        check("densityAbove equal miss", !SurfaceConditions.densityAbove(0.5).test(new SurfaceContext(0, 0, 0, null, 0.5)));

        // Boolean combinators.
        SurfaceCondition t = SurfaceConditions.alwaysTrue();
        SurfaceCondition f = SurfaceConditions.never();
        // and: both true -> true.
        check("and both-true", SurfaceConditions.and(t, t).test(new SurfaceContext(0, 0, 0)));
        // and: one false -> false.
        check("and one-false", !SurfaceConditions.and(t, f).test(new SurfaceContext(0, 0, 0)));
        // or: one true -> true.
        check("or one-true", SurfaceConditions.or(f, t).test(new SurfaceContext(0, 0, 0)));
        // or: both false -> false.
        check("or none-true", !SurfaceConditions.or(f, f).test(new SurfaceContext(0, 0, 0)));
        // not: inverts true to false.
        check("not inverts", !SurfaceConditions.not(t).test(new SurfaceContext(0, 0, 0)));

        // alwaysTrue / never.
        check("alwaysTrue", t.test(new SurfaceContext(0, 0, 0)));
        check("never", !f.test(new SurfaceContext(0, 0, 0)));

        // Actions.
        SurfaceAction stone = SurfaceActions.state("minecraft:stone");
        // state: constant regardless of context.
        check("state constant", "minecraft:stone".equals(stone.apply(new SurfaceContext(9, 9, 9))));
        // byDensity: below split -> lowId.
        check("byDensity below low", "minecraft:dirt"
                .equals(SurfaceActions.byDensity("minecraft:dirt", "minecraft:stone", 0.5).apply(new SurfaceContext(0, 0, 0, null, 0.3))));
        // byDensity: at split -> highId (>= goes high).
        check("byDensity equal high", "minecraft:stone"
                .equals(SurfaceActions.byDensity("minecraft:dirt", "minecraft:stone", 0.5).apply(new SurfaceContext(0, 0, 0, null, 0.5))));

        // Evaluator: first matching rule returns its action output.
        List<SurfaceRule> rules = List.of(
                new SurfaceRule(SurfaceConditions.belowY(5), SurfaceActions.state("minecraft:stone")),
                new SurfaceRule(SurfaceConditions.alwaysTrue(), SurfaceActions.state("minecraft:grass_block")));
        check("evaluate first-hit output",
                "minecraft:stone".equals(SurfaceEvaluator.evaluate(new SurfaceContext(0, 3, 0), rules, "minecraft:air")));
        // Evaluator: no match -> default state. This rule set has NO always-true
        // clause, so this context genuinely falls through to the default.
        List<SurfaceRule> missRules = List.of(
                new SurfaceRule(SurfaceConditions.belowY(5), SurfaceActions.state("minecraft:stone")),
                new SurfaceRule(SurfaceConditions.biome("minecraft:desert"), SurfaceActions.state("minecraft:sand")));
        check("evaluate no-match default",
                "minecraft:air".equals(SurfaceEvaluator.evaluate(new SurfaceContext(0, 9, 0, "minecraft:ocean"), missRules, "minecraft:air")));
        // Evaluator: action returning null declines -> next matching rule applies.
        List<SurfaceRule> declineRules = List.of(
                new SurfaceRule(SurfaceConditions.alwaysTrue(),
                        c -> null),                                  // decline at match
                new SurfaceRule(SurfaceConditions.alwaysTrue(), SurfaceActions.state("minecraft:sand")));
        check("evaluate decline continues",
                "minecraft:sand".equals(SurfaceEvaluator.evaluate(new SurfaceContext(0, 0, 0), declineRules, "minecraft:air")));
        // Evaluator: all matches decline -> default.
        check("evaluate all-decline default",
                "minecraft:air".equals(SurfaceEvaluator.evaluate(new SurfaceContext(0, 0, 0),
                        declineRules.subList(0, 1), "minecraft:air")));
        // Evaluator: multi-rule priority: first matching condition wins even if a later one also matches.
        List<SurfaceRule> priorityRules = List.of(
                new SurfaceRule(SurfaceConditions.biome("minecraft:desert"), SurfaceActions.state("minecraft:sand")),
                new SurfaceRule(SurfaceConditions.alwaysTrue(), SurfaceActions.state("minecraft:stone")));
        check("evaluate multi-rule priority",
                "minecraft:sand".equals(SurfaceEvaluator.evaluate(new SurfaceContext(0, 0, 0, "minecraft:desert"),
                        priorityRules, "minecraft:air")));
        // Evaluator: empty rule list -> all default.
        check("evaluate empty rules default",
                "minecraft:air".equals(SurfaceEvaluator.evaluate(new SurfaceContext(0, 0, 0), List.of(), "minecraft:air")));

        // SurfaceContext record completeness & defaults.
        // density defaults to 0 via position-only constructor.
        check("context density default 0", new SurfaceContext(1, 2, 3).density() == 0.0);
        // biomeTag defaults to null via position-only constructor.
        check("context biomeTag default null", new SurfaceContext(1, 2, 3).biomeTag() == null);
        // record equality on all fields.
        check("context record equals",
                new SurfaceContext(1, 2, 3, "a", 2.5).equals(new SurfaceContext(1, 2, 3, "a", 2.5)));
        // field accessors preserve supplied values.
        check("context fields preserved",
                new SurfaceContext(4, 5, 6, "minecraft:ocean", -1.25).density() == -1.25
                        && new SurfaceContext(4, 5, 6, "minecraft:ocean", -1.25).biomeTag().equals("minecraft:ocean")
                        && new SurfaceContext(4, 5, 6, "minecraft:ocean", -1.25).y() == 5);

        if (failures == 0) {
            System.out.println("[SurfaceRulesProbe] PASS (surface-rules core, " + 32 + " checks)");
            System.exit(0);
        } else {
            System.out.println("[SurfaceRulesProbe] FAIL: " + failures + " assertion(s)");
            System.exit(1);
        }
    }
}