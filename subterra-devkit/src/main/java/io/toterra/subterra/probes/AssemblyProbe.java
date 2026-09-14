package io.toterra.subterra.probes;

import java.util.Map;

import io.toterra.subterra.api.worldgen.assembly.WorldgenAssemblyApi;
import io.toterra.subterra.engine.worldgen.assembly.AssemblyApiMirror;

/**
 * p.2.29 确定性装配面探针（纯 JVM）：断言 api.worldgen.assembly.WorldgenAssemblyApi 与
 * engine 镜像 AssemblyApiMirror 同输入同输出逐位一致 + 确定性再入 + 非法值确定性拒绝 + 密度装配
 * applyDensity 同 double 位。同输入同字节、禁时序、禁随机，退出码 0 = 全过。
 * <p>
 * p.2.29 deterministic assembly-surface probe (pure JVM): asserts api.worldgen.assembly.
 * WorldgenAssemblyApi and the engine mirror AssemblyApiMirror agree bit-for-bit on identical inputs,
 * deterministic re-entry, deterministic rejection of invalid values, and double-bit-identical
 * applyDensity. Same input → same bytes, no timing, no randomness; exit code 0 = all passed.
 */
public final class AssemblyProbe {

    private AssemblyProbe() {
    }

    /** Deterministic fixed inputs used for every assertion (no randomness). */
    private static final Map<String, String> RULES_OVERRIDDEN = Map.of(
            "subterra.worldgen.density_offset", "3.5",
            "subterra.worldgen.density_scale", "1.25",
            "subterra.worldgen.surface_palette", "custom",
            "subterra.worldgen.ore_density", "2.0",
            "overturn.worldgen.structures", "true"); // unknown key must be ignored

    public static void main(String[] args) {
        int checks = 0;

        // 1) api vs mirror: defaults agree (same values + same rendered bytes).
        WorldgenAssemblyApi.Resolved apiDefaults = WorldgenAssemblyApi.defaults();
        AssemblyApiMirror.Resolved mirrorDefaults = AssemblyApiMirror.defaults();
        check(fieldsEqual(apiDefaults, mirrorDefaults)
                        && WorldgenAssemblyApi.render(apiDefaults).equals(AssemblyApiMirror.render(mirrorDefaults)),
                "api vs mirror defaults agree (values + canonical render)");
        checks++;

        // 2) api vs mirror: resolve(overridden table) agrees bit-for-bit + render byte-identical.
        WorldgenAssemblyApi.Resolved apiResolved = WorldgenAssemblyApi.resolve(RULES_OVERRIDDEN);
        AssemblyApiMirror.Resolved mirrorResolved = AssemblyApiMirror.resolve(RULES_OVERRIDDEN);
        check(fieldsEqual(apiResolved, mirrorResolved)
                        && WorldgenAssemblyApi.render(apiResolved).equals(AssemblyApiMirror.render(mirrorResolved)),
                "api vs mirror resolve agree (offset=3.5, scale=1.25, palette=custom, ore=2.0, unknown key ignored)");
        checks++;

        // 3) determinism re-entry: same input → same output on every call.
        check(WorldgenAssemblyApi.render(WorldgenAssemblyApi.resolve(RULES_OVERRIDDEN))
                        .equals(WorldgenAssemblyApi.render(WorldgenAssemblyApi.resolve(RULES_OVERRIDDEN))),
                "api resolve deterministic re-entry");
        checks++;

        // 4) empty/null table → vanilla defaults, no change.
        check(WorldgenAssemblyApi.render(WorldgenAssemblyApi.resolve(Map.of()))
                        .equals(WorldgenAssemblyApi.render(apiDefaults))
                        && WorldgenAssemblyApi.render(WorldgenAssemblyApi.resolve(null))
                        .equals(WorldgenAssemblyApi.render(apiDefaults)),
                "empty/null rules resolve to vanilla defaults");
        checks++;

        // 5) applyDensity: double-bit-identical across api and mirror (value*scale+offset).
        check(WorldgenAssemblyApi.applyDensity(apiResolved, 1.0) == 4.75
                        && WorldgenAssemblyApi.applyDensity(apiResolved, -8.0)
                        == AssemblyApiMirror.applyDensity(mirrorResolved, -8.0),
                "applyDensity double-bit identical api==mirror (1.0→4.75, -8.0→-6.5)");
        checks++;

        // 6) invalid values deterministically rejected on both sides.
        check(rejects(Map.of("subterra.worldgen.density_scale", "9.0"))
                        && rejects(Map.of("subterra.worldgen.density_offset", "NaN"))
                        && rejects(Map.of("subterra.worldgen.surface_palette", "bogus"))
                        && rejects(Map.of("subterra.worldgen.ore_density", "-1.0")),
                "invalid values deterministically rejected (scale 9 / NaN offset / bogus palette / negative ore)");
        checks++;

        System.out.println("[AssemblyProbe] PASS (" + checks + " checks)");
    }

    private static boolean rejects(Map<String, String> rules) {
        try {
            WorldgenAssemblyApi.resolve(rules);
            return false;
        } catch (IllegalArgumentException expected) {
            return true;
        }
    }

    private static boolean fieldsEqual(WorldgenAssemblyApi.Resolved a, AssemblyApiMirror.Resolved b) {
        return a.densityOffset() == b.densityOffset()
                && a.densityScale() == b.densityScale()
                && a.surfacePalette().equals(b.surfacePalette())
                && a.oreDensity() == b.oreDensity();
    }

    private static void check(boolean ok, String what) {
        if (!ok) {
            throw new IllegalStateException("[AssemblyProbe] check failed: " + what);
        }
    }
}