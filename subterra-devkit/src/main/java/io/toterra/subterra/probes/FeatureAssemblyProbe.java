package io.toterra.subterra.probes;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.toterra.subterra.engine.worldgen.feature.FeatureAssembly;
import io.toterra.subterra.engine.worldgen.guard.StructureFootprint;

/**
 * p.2.29.3 确定性「规则 → 特征/成矿」装配探针（纯 JVM）：断言 engine.worldgen.feature.FeatureAssembly 的
 * 规则解析 / 规范渲染 / 恒等缺省 / 组内规范序 / 母岩前置 / 矿脉走向 / 结构护栏 / 非法值确定性拒绝，
 * 全部同输入同输出、禁时序、禁随机；退出码 0 = 全过。
 *
 * <p>p.2.29.3 deterministic "rules → feature/ore" assembly probe (pure JVM): asserts
 * engine.worldgen.feature.FeatureAssembly rule parsing / canonical render / identity defaults /
 * canonical in-group order / host-rock prerequisite / vein trend / structure guard / deterministic
 * rejection of invalid values — all same-input-same-output, no timing, no randomness; exit code 0 = all
 * passed.
 */
public final class FeatureAssemblyProbe {

    private FeatureAssemblyProbe() {
    }

    /** 一个确定性的、声明齐全的规则表（含一个被忽略的外来 key）。 /
     *  A deterministic, fully-declared rule table (with one ignored foreign key). */
    private static final Map<String, String> DECLARED = rules(
            "subterra.worldgen.feature.enable", "true",
            "subterra.worldgen.feature.mineral.iron.block", "minecraft:iron_ore",
            "subterra.worldgen.feature.mineral.iron.host_rock", "minecraft:granite,#minecraft:base_stone_overworld",
            "subterra.worldgen.feature.mineral.iron.vein_trend", "vertical",
            "subterra.worldgen.feature.mineral.iron.count", "2",
            "subterra.worldgen.feature.mineral.iron.min_y", "-48",
            "subterra.worldgen.feature.mineral.iron.max_y", "16",
            "subterra.worldgen.feature.vegetation.fern.block", "minecraft:fern",
            "subterra.worldgen.feature.vegetation.fern.climate", "temperate",
            "subterra.worldgen.feature.vegetation.fern.count", "4",
            "subterra.worldgen.feature.vegetation.fern.min_y", "60",
            "subterra.worldgen.feature.vegetation.fern.max_y", "80",
            "subterra.worldgen.feature.guard.box.temple.box", "100,100,32,32,8",
            "subterra.worldgen.feature.guard.clearance", "4",
            "overturn.litho.sandstone_depth", "12"); // foreign / unknown key must be ignored

    public static void main(String[] args) {
        int checks = 0;

        // 1) default / null tables resolve to the identity default plan.
        FeatureAssembly.Plan defaulted = FeatureAssembly.of(Map.of());
        check(defaulted.equals(FeatureAssembly.DEFAULT_PLAN) && defaulted.isIdentity(),
                "empty table resolves to the identity default plan");
        check(FeatureAssembly.of(null).equals(FeatureAssembly.DEFAULT_PLAN),
                "null table resolves to the identity default plan");
        check(FeatureAssembly.DEFAULT_PLAN.mounts().isEmpty()
                        && !FeatureAssembly.DEFAULT_PLAN.blocked(0, 0)
                        && !FeatureAssembly.DEFAULT_PLAN.blocked(1_000_000, -1_000_000),
                "identity default: no mounts, nothing blocked");
        checks++;

        // 2) declared plan: master switch + count/y/mineral/vegetation/guard/clearance parsed.
        FeatureAssembly.Plan plan = FeatureAssembly.of(DECLARED);
        check(plan.enabled() && plan.guardClearance() == 4, "master switch + global guard clearance parsed");
        check(plan.minerals().size() == 1 && plan.vegetation().size() == 1 && plan.guards().size() == 1,
                "one mineral + one vegetation + one guard box parsed");
        check(plan.mounts().size() == 2, "two mounts (mineral first, vegetation second)");
        check("mineral".equals(plan.mounts().get(0).group()) && "vegetation".equals(plan.mounts().get(1).group()),
                "mount order: minerals before vegetation");
        checks++;

        // 3) mineral: block / host-rock prerequisite / vein trend / count / y band.
        FeatureAssembly.Mineral iron = plan.mineral("iron").orElseThrow();
        check("minecraft:iron_ore".equals(iron.block()), "mineral block parsed");
        check(iron.trend() == FeatureAssembly.VeinTrend.VERTICAL, "vein trend parsed (vertical)");
        check(iron.count() == 2 && iron.minY() == -48 && iron.maxY() == 16, "count / y band parsed");
        check(iron.hostRock().blocks().equals(List.of("minecraft:granite"))
                        && iron.hostRock().tags().equals(List.of("minecraft:base_stone_overworld")),
                "host-rock prerequisite split into block id + #tag (canonical order)");
        check(!iron.hostRock().isAny(), "declared host rock is not any");
        check(plan.mineral("gold").isEmpty(), "undeclared mineral is absent");
        checks++;

        // 4) vegetation: block / climate prerequisite.
        FeatureAssembly.Vegetation fern = plan.vegetation("fern").orElseThrow();
        check("minecraft:fern".equals(fern.block()) && "temperate".equals(fern.climate()) && fern.count() == 4,
                "vegetation block / climate / count parsed");
        checks++;

        // 5) structure guard: blocked inside the (clearance-inflated) box, free outside; guard geometry reuses
        //    the structure-guard module.
        check(plan.blocked(100, 100) && plan.blocked(131, 131) && plan.blocked(140, 140),
                "guard blocks origins inside the box and its global clearance ring");
        check(!plan.blocked(99 - 5, 100) && !plan.blocked(200, 200),
                "guard leaves origins outside the clearance free");
        StructureFootprint f = plan.guards().get(0).footprint();
        check(f.minX() == 100 && f.minZ() == 100 && f.sizeX() == 32 && f.sizeZ() == 32 && f.clearance() == 8,
                "guard footprint reuses the structure-guard geometry (StructureFootprint)");
        check(FeatureAssembly.guardConflicts(plan).isEmpty(), "no self-overlapping guard boxes");
        FeatureAssembly.Plan two = FeatureAssembly.of(rules(
                "subterra.worldgen.feature.guard.box.a.box", "0,0,16,16",
                "subterra.worldgen.feature.guard.box.b.box", "8,8,16,16"));
        check(FeatureAssembly.guardConflicts(two).equals(List.of("a x b")),
                "overlapping guard boxes reported deterministically (a x b)");
        checks++;

        // 6) determinism: same input → same plan + same canonical bytes; key order independent.
        check(FeatureAssembly.render(FeatureAssembly.of(DECLARED)).equals(FeatureAssembly.render(FeatureAssembly.of(DECLARED))),
                "canonical render deterministic re-entry");
        Map<String, String> reversed = new LinkedHashMap<>();
        DECLARED.entrySet().stream().sorted((a, b) -> b.getKey().compareTo(a.getKey()))
                .forEach(e -> reversed.put(e.getKey(), e.getValue()));
        check(FeatureAssembly.render(FeatureAssembly.of(reversed)).equals(FeatureAssembly.render(plan)),
                "plan is independent of rule-table insertion order (canonical in-group order)");
        check(FeatureAssembly.render(plan).startsWith("enable=true; guard_clearance=4; minerals=[mineral.iron = {"),
                "canonical render has a fixed field order");
        checks++;

        // 7) identity short-circuit: disabled plan declares no mounts even with rules present.
        check(FeatureAssembly.of(Map.of(
                        "subterra.worldgen.feature.mineral.iron.block", "minecraft:iron_ore"))
                .mounts().isEmpty(),
                "disabled master switch yields no mounts (identity)");
        checks++;

        // 8) deterministic rejection of every invalid value / unknown field.
        check(rejects(Map.of("subterra.worldgen.feature.enable", "yes"))
                        && rejects(Map.of("subterra.worldgen.feature.mineral.iron.block", "minecraft:iron_ore",
                                "subterra.worldgen.feature.mineral.iron.count", "-1"))
                        && rejects(Map.of("subterra.worldgen.feature.mineral.iron.block", "minecraft:iron_ore",
                                "subterra.worldgen.feature.mineral.iron.count", "65"))
                        && rejects(Map.of("subterra.worldgen.feature.mineral.iron.block", "minecraft:iron_ore",
                                "subterra.worldgen.feature.mineral.iron.min_y", "40",
                                "subterra.worldgen.feature.mineral.iron.max_y", "10"))
                        && rejects(Map.of("subterra.worldgen.feature.mineral.iron.block", "minecraft:iron_ore",
                                "subterra.worldgen.feature.mineral.iron.vein_trend", "spiral"))
                        && rejects(Map.of("subterra.worldgen.feature.mineral.iron.count", "3"))
                        && rejects(Map.of("subterra.worldgen.feature.vegetation.fern.count", "3"))
                        && rejects(Map.of("subterra.worldgen.feature.mineral.iron.block", "minecraft:iron_ore",
                                "subterra.worldgen.feature.mineral.iron.bogus", "1"))
                        && rejects(Map.of("subterra.worldgen.feature.guard.box.a.box", "0,0,0,16"))
                        && rejects(Map.of("subterra.worldgen.feature.guard.clearance", "-1")),
                "invalid values deterministically rejected (bool / count / y / trend / missing block / unknown field / bad box)");
        checks++;

        System.out.println("[FeatureAssemblyProbe] PASS (" + checks + " checks)");
    }

    private static Map<String, String> rules(String... kv) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put(kv[i], kv[i + 1]);
        }
        return m;
    }

    private static boolean rejects(Map<String, String> rules) {
        try {
            FeatureAssembly.of(rules);
            return false;
        } catch (IllegalArgumentException expected) {
            return true;
        }
    }

    private static void check(boolean ok, String what) {
        if (!ok) {
            throw new IllegalStateException("[FeatureAssemblyProbe] check failed: " + what);
        }
    }
}
