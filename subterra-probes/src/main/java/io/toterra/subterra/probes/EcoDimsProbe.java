package io.toterra.subterra.probes;

import io.toterra.subterra.api.worldgen.DimPriority;
import io.toterra.subterra.api.worldgen.EcoDim;
import io.toterra.subterra.api.worldgen.EcoDimValue;
import io.toterra.subterra.api.worldgen.EcoProfile;
import io.toterra.subterra.api.worldgen.EcoRelations;
import io.toterra.subterra.api.worldgen.EcoResolver;
import io.toterra.subterra.api.worldgen.VanillaDefaults;
import io.toterra.subterra.api.worldgen.VanillaRules;

import java.util.List;
import java.util.Map;

/**
 * Deterministic acceptance probe for the EcoDims nine-dimension biome model
 * (worldgen prerequisite, p.1.8): dimension registry, coexist chain
 * (default-disallow / allow-exceptions), vanilla-equivalent default legality,
 * rule-set health validation, and priority-fallback resolution. Pure JVM — no
 * Minecraft runtime. Exit 0 = PASS, exit 1 = FAIL.
 */
public final class EcoDimsProbe {

    private EcoDimsProbe() {
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
        dimensions();
        chain();
        defaultsAndHealth();
        resolution();

        if (failures == 0) {
            System.out.println("[EcoDimsProbe] PASS (nine-dimension biome model)");
            System.exit(0);
        } else {
            System.out.println("[EcoDimsProbe] FAIL: " + failures + " assertion(s)");
            System.exit(1);
        }
    }

    private static void dimensions() {
        check("nine dimensions", EcoDim.ALL.size() == 9);
        check("dimension lookup", EcoDim.of("terrain") != null && EcoDim.of("bogus") == null);
        check("dimension id map", EcoDim.byId().size() == 9);
        List<EcoDimValue> pool = VanillaDefaults.allValues();
        check("value pool size", pool.size() >= 40);
        long terrainCount = pool.stream().filter(v -> v.dim().id().equals("terrain")).count();
        check("every dimension has values", terrainCount >= 2
                && pool.stream().map(EcoDimValue::dim).distinct().count() == 9);
    }

    private static void chain() {
        EcoRelations rel = new EcoRelations();
        EcoDimValue terrainPlains = EcoDimValue.of(EcoDim.of("terrain"), "plains");
        EcoDimValue climateArid = EcoDimValue.of(EcoDim.of("climate"), "arid");
        check("same dimension always allowed", rel.isAllowed(terrainPlains, terrainPlains));
        check("default is disallow", !rel.isAllowed(terrainPlains, climateArid));
        rel.allow(terrainPlains, climateArid);
        check("explicit allow wins", rel.isAllowed(terrainPlains, climateArid));
    }

    private static void defaultsAndHealth() {
        EcoRelations chain = VanillaRules.chain();
        Map<String, EcoProfile> defaults = VanillaDefaults.defaults();
        for (Map.Entry<String, EcoProfile> e : defaults.entrySet()) {
            check("default legal: " + e.getKey(), e.getValue().allows(chain));
        }
        check("profile lookup known", VanillaDefaults.profileFor("minecraft:plains") != null);
        check("profile lookup unknown", VanillaDefaults.profileFor("minecraft:nowhere") == null);
        check("rule set healthy", chain.validate(VanillaDefaults.allValues()).isEmpty());

        // The validator itself must flag a fully forbidden value.
        EcoRelations raw = new EcoRelations();
        raw.allow(EcoDimValue.of(EcoDim.of("terrain"), "plains"),
                EcoDimValue.of(EcoDim.of("climate"), "temperate"));
        List<EcoDimValue> rawPool = List.of(
                EcoDimValue.of(EcoDim.of("terrain"), "plains"),
                EcoDimValue.of(EcoDim.of("climate"), "temperate"),
                EcoDimValue.of(EcoDim.of("fauna"), "high")); // no edge -> fully forbidden
        List<String> issues = raw.validate(rawPool);
        check("validator flags forbidden value", issues.stream().anyMatch(i -> i.contains("fauna=high")));
    }

    private static void resolution() {
        EcoRelations chain = VanillaRules.chain();
        var pool = VanillaDefaults.allValues();

        // Legal requests resolve to themselves.
        EcoProfile plains = VanillaDefaults.profileFor("minecraft:plains");
        check("resolve identity", EcoResolver.resolve(plains, chain, DimPriority.FROM_LOW, pool) == plains);

        // Single low-priority concession: relic=rich is not allowed with the
        // plains profile; terrain (highest priority) must not move, relic falls
        // back to the legal "scattered".
        EcoProfile richRequest = EcoProfile.builder()
                .set(EcoDimValue.of(EcoDim.of("terrain"), "plains"))
                .set(EcoDimValue.of(EcoDim.of("climate"), "temperate"))
                .set(EcoDimValue.of(EcoDim.of("vegetation"), "grassland"))
                .set(EcoDimValue.of(EcoDim.of("hydro"), "none"))
                .set(EcoDimValue.of(EcoDim.of("surface"), "soil"))
                .set(EcoDimValue.of(EcoDim.of("litho"), "sedimentary"))
                .set(EcoDimValue.of(EcoDim.of("mineral"), "common"))
                .set(EcoDimValue.of(EcoDim.of("fauna"), "high"))
                .set(EcoDimValue.of(EcoDim.of("relic"), "rich"))
                .build();
        check("rich relic conflicted", !richRequest.allows(chain));
        EcoProfile resolved = EcoResolver.resolve(richRequest, chain, DimPriority.FROM_LOW, pool);
        check("resolve legal after concession", resolved.allows(chain));
        check("terrain preserved", resolved.get(EcoDim.of("terrain")).name().equals("plains"));
        check("relic relaxed", resolved.get(EcoDim.of("relic")).name().equals("scattered"));

        // High-priority concession is the last resort: desert profile with
        // mountains terrain conflicts on hydro; no low-priority single change
        // fixes it, so terrain yields and the arid climate survives.
        EcoProfile desert = VanillaDefaults.profileFor("minecraft:desert");
        EcoProfile mountainDesert = EcoProfile.builder()
                .set(EcoDimValue.of(EcoDim.of("terrain"), "mountains"))
                .set(desert.get(EcoDim.of("climate")))
                .set(desert.get(EcoDim.of("vegetation")))
                .set(desert.get(EcoDim.of("hydro")))
                .set(desert.get(EcoDim.of("surface")))
                .set(desert.get(EcoDim.of("litho")))
                .set(desert.get(EcoDim.of("mineral")))
                .set(desert.get(EcoDim.of("fauna")))
                .set(desert.get(EcoDim.of("relic")))
                .build();
        check("mountain desert conflicted", !mountainDesert.allows(chain));
        EcoProfile settled = EcoResolver.resolve(mountainDesert, chain, DimPriority.FROM_LOW, pool);
        check("mountain desert legal", settled.allows(chain));
        check("mountain desert climate kept", settled.get(EcoDim.of("climate")).name().equals("arid"));

        // Greedy construction proves satisfiability on the same rules.
        check("greedy legal exists", chain.greedyLegalProfile(pool) != null
                && chain.greedyLegalProfile(pool).allows(chain));
    }
}