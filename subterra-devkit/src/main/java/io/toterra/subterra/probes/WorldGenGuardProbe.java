package io.toterra.subterra.probes;

import io.toterra.subterra.engine.worldgen.guard.StructureFootprint;
import io.toterra.subterra.engine.worldgen.guard.StructureLayout;

import java.util.List;
import java.util.Optional;

/**
 * Deterministic acceptance probe for the worldgen structure-collision guard
 * (p.1.8.1): clearance-aware overlap, spatial-hash indices, conflict listing,
 * and nearest-free-spot placement. Pure JVM — no Minecraft runtime.
 */
public final class WorldGenGuardProbe {

    private WorldGenGuardProbe() {
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
        overlaps();
        layout();
        placement();
        density();

        if (failures == 0) {
            System.out.println("[WorldGenGuardProbe] PASS (structure collision guard)");
            System.exit(0);
        } else {
            System.out.println("[WorldGenGuardProbe] FAIL: " + failures + " assertion(s)");
            System.exit(1);
        }
    }

    private static void overlaps() {
        StructureFootprint a = new StructureFootprint("a", 0, 0, 10, 10, 0);
        StructureFootprint b = new StructureFootprint("b", 9, 0, 10, 10, 0);   // touches by 1
        StructureFootprint c = new StructureFootprint("c", 10, 0, 10, 10, 0);  // adjacent, gap 0
        check("overlap on touch", StructureFootprint.overlaps(a, b));
        check("no overlap on adjacency", !StructureFootprint.overlaps(a, c));

        StructureFootprint tight = new StructureFootprint("t", 0, 0, 10, 10, 0);
        StructureFootprint near = new StructureFootprint("n", 8, 0, 10, 10, 4); // clearance 4 -> inflated to 22 wide
        check("clearance inflates bounds", near.maxX() == 8 + 10 - 1 + 4);
        check("clearance conflict", StructureFootprint.overlaps(tight, near));
        StructureFootprint far = new StructureFootprint("f", 22, 0, 10, 10, 4);
        check("clearance respected at exact gap", !StructureFootprint.overlaps(tight, far));
    }

    private static void layout() {
        StructureLayout layout = new StructureLayout();
        layout.insert(new StructureFootprint("a", 500, 500, 100, 100, 10)); // crosses cell 0/1
        check("layout size", layout.size() == 1);
        check("twin in same spot conflicts",
                layout.conflictsWith(new StructureFootprint("a", 500, 500, 100, 100, 10)));
        check("conflict detected", layout.conflictsWith(new StructureFootprint("x", 550, 550, 30, 30, 0)));
        check("cell-crossing footprint conflict",
                layout.conflictsWith(new StructureFootprint("y", 505, 505, 30, 30, 0)));

        StructureLayout sparse = new StructureLayout();
        sparse.insert(new StructureFootprint("p1", 0, 0, 50, 50, 0));
        sparse.insert(new StructureFootprint("p2", 60, 0, 50, 50, 0));
        sparse.insert(new StructureFootprint("p3", 5000, 5000, 20, 20, 0)); // far cell
        check("all-pairs conflicts", sparse.conflicts().isEmpty());
    }

    private static void placement() {
        StructureLayout layout = new StructureLayout();
        layout.insert(new StructureFootprint("a", 0, 0, 100, 100, 10));
        StructureFootprint probe = new StructureFootprint("probe", 0, 0, 20, 20, 5);

        Optional<StructureFootprint> same = layout.findSpot(probe, 0, 0, 0);
        check("occupied origin rejected", same.isEmpty());

        Optional<StructureFootprint> near = layout.findSpot(probe, 0, 0, 1);
        check("nearest free ring found", near.isPresent());
        StructureFootprint found = near.orElseThrow();
        check("found spot conflict-free", !layout.conflictsWith(found));
        boolean accepted = layout.insertIfFree(found);
        check("found spot insertable", accepted);
        check("near ring width", Math.abs(found.minX()) == 512 || Math.abs(found.minZ()) == 512);

        check("far empty location accepted instantly",
                layout.findSpot(probe, 9000, 9000, 0).isPresent());
    }

    private static void density() {
        // 800 footprints across many cells; every insert stays O(neighbourhood).
        StructureLayout layout = new StructureLayout();
        long start = System.nanoTime();
        for (int i = 0; i < 800; i++) {
            int x = (i * 7919) % 40000 - 20000;
            int z = (i * 104729) % 40000 - 20000;
            layout.insertIfFree(new StructureFootprint("f" + i, x, z, 16, 16, 8));
        }
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        check("dense placement fast", elapsedMs < 2000);
        // Deliberately insert a conflicting twin (f0 landed at -20000,-20000);
        // all-pairs must report the overlap.
        layout.insert(new StructureFootprint("twin", -20000, -20000, 16, 16, 8));
        check("all-pairs reports deliberate overlap", !layout.conflicts().isEmpty());
    }
}