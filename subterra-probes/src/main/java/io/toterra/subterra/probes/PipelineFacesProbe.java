package io.toterra.subterra.probes;

import io.toterra.subterra.optim.worldgen.pipeline.PipelineFace;
import io.toterra.subterra.optim.worldgen.pipeline.PipelineFaces;

/**
 * Deterministic acceptance probe for the p.1.8.2 world-gen pipeline recon:
 * face completeness, data-folder / registry-id validation, folder lookup.
 * Pure JVM.
 */
public final class PipelineFacesProbe {

    private PipelineFacesProbe() {
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
        // Face completeness (the eight R8.x re-keyed faces).
        check("eight faces", PipelineFace.values().length == 8);
        check("face names", PipelineFaces.faceNames().containsAll(java.util.Set.of(
                "NOISE_SETTINGS", "DENSITY_FUNCTIONS", "SURFACE_RULES", "BIOMES",
                "STRUCTURES", "CAVES_ORES", "PRESETS", "PERF")));

        // Data-folder validation.
        check("noise settings folder known", PipelineFaces.isKnownDataFolder("worldgen/noise_settings"));
        check("surface rule folder known", PipelineFaces.isKnownDataFolder("worldgen/surface_rule"));
        check("registry-less perf folder known", PipelineFaces.isKnownDataFolder("probes/perf"));
        check("unknown folder rejected", !PipelineFaces.isKnownDataFolder("worldgen/not_a_face"));
        check("null folder rejected", !PipelineFaces.isKnownDataFolder(null));

        // Registry-id validation (null rows skipped).
        check("registry id known", PipelineFaces.isKnownRegistryId("worldgen/structure_set"));
        check("structure data folder known", PipelineFaces.isKnownDataFolder("worldgen/structure"));
        check("unknown registry id rejected", !PipelineFaces.isKnownRegistryId("worldgen/banana"));
        check("null registry id rejected", !PipelineFaces.isKnownRegistryId(null));

        // Face -> folder lookup.
        check("folder lookup", "worldgen/density_function".equals(PipelineFaces.dataFolderOf("DENSITY_FUNCTIONS")));
        check("unknown face lookup null", PipelineFaces.dataFolderOf("NOPE") == null);

        // Every registryId is a valid resource-location-looking token.
        for (PipelineFace face : PipelineFace.values()) {
            String id = face.registryId();
            if (id != null && !id.matches("[a-z0-9_./:-]+")) {
                check("registry id token '" + id + "'", false);
            }
        }
        check("all registry id tokens well-formed", true);

        if (failures == 0) {
            System.out.println("[PipelineFacesProbe] PASS (p.1.8.2 worldgen pipeline recon, " + 15 + " checks)");
            System.exit(0);
        } else {
            System.out.println("[PipelineFacesProbe] FAIL: " + failures + " assertion(s)");
            System.exit(1);
        }
    }
}