package io.toterra.subterra.api.worldgen;

/**
 * Default coexistence rule set backing the vanilla-equivalent mapping: every
 * pair inside each default profile is explicitly allowed, plus a few
 * cross-biome allowances (e.g. arid climate on mountain terrain) so the
 * resolver has legal room to work. Pure data builder; deterministic.
 */
public final class VanillaRules {

    private VanillaRules() {
    }

    /** Builds the default rule chain from the vanilla mapping. */
    public static EcoRelations chain() {
        EcoRelations rel = new EcoRelations();
        for (EcoProfile profile : VanillaDefaults.defaults().values()) {
            allowProfilePairs(rel, profile);
        }
        // Cross-profile allowances: legal re-combinations beyond defaults.
        rel.allow(v("terrain", "mountains"), v("climate", "arid"));      // high deserts
        rel.allow(v("terrain", "plains"), v("surface", "stone"));        // rocky plains
        rel.allow(v("hydro", "wetlands"), v("surface", "ice"));          // frozen marshes
        rel.allow(v("terrain", "hills"), v("climate", "cold"));          // cold highlands
        rel.allow(v("vegetation", "savanna"), v("surface", "sand"));     // sandy savanna
        rel.allow(v("climate", "arid"), v("fauna", "medium"));           // hardy fauna
        // Keep every declared value reachable (no "fully forbidden" value):
        rel.allow(v("terrain", "plateau"), v("climate", "arid"));        // barren plateau
        rel.allow(v("hydro", "lake"), v("terrain", "plains"));           // lake plains
        rel.allow(v("surface", "ice"), v("climate", "cold"));            // cold icesheets
        rel.allow(v("relic", "rich"), v("litho", "metamorphic"));      // rich relics in metamorphic rock
        return rel;
    }

    private static void allowProfilePairs(EcoRelations rel, EcoProfile profile) {
        var vs = profile.values();
        for (int i = 0; i < vs.size(); i++) {
            for (int j = i + 1; j < vs.size(); j++) {
                rel.allow(vs.get(i), vs.get(j));
            }
        }
    }

    private static EcoDimValue v(String dim, String name) {
        return EcoDimValue.of(EcoDim.of(dim), name);
    }
}