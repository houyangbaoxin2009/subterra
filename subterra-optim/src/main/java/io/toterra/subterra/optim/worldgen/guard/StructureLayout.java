package io.toterra.subterra.optim.worldgen.guard;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Spatial index over placed structure footprints. The hot paths (insert and
 * nearest-free-spot search) query only neighbouring hash cells — O(neighbourhood),
 * never O(n²). The all-pairs {@link #conflicts} listing exists only for
 * verification/tooling where quadratic cost is acceptable and explicit.
 * Thread-un-safe by design: worldgen placement runs on its own stage.
 * Pure JDK; deterministic.
 */
public final class StructureLayout {

    private static final int CELL = 512; // > any realistic footprint extent

    private final Map<Long, List<StructureFootprint>> cells = new HashMap<>();
    private final List<StructureFootprint> all = new ArrayList<>();

    /** Number of placed footprints. */
    public int size() {
        return all.size();
    }

    /** Inserts a footprint (no conflict check; see {@link #insertIfFree}). */
    public void insert(StructureFootprint foot) {
        all.add(foot);
        // Index the footprint in every cell it spans so cross-cell lookups hit.
        for (long key : coveredCells(foot)) {
            cells.computeIfAbsent(key, k -> new ArrayList<>()).add(foot);
        }
    }

    /** Inserts only when the footprint does not conflict; returns success. */
    public boolean insertIfFree(StructureFootprint foot) {
        if (conflictsWith(foot)) {
            return false;
        }
        insert(foot);
        return true;
    }

    /** True when the footprint conflicts with any placed footprint. */
    public boolean conflictsWith(StructureFootprint foot) {
        for (StructureFootprint other : nearby(foot)) {
            if (StructureFootprint.overlaps(foot, other)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Finds the nearest origin around {@code (desiredX, desiredZ)} (spiral
     * search, step = {@code CELL}) where the footprint conflicts with nothing,
     * up to {@code maxRadius}. Deterministic; returns {@link Optional#empty()}
     * when no free spot is found within the radius.
     */
    public Optional<StructureFootprint> findSpot(StructureFootprint foot, int desiredX, int desiredZ, int maxRadius) {
        if (maxRadius < 0) {
            maxRadius = 0;
        }
        for (int radius = 0; radius <= maxRadius; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (radius > 0 && Math.max(Math.abs(dx), Math.abs(dz)) != radius) {
                        continue; // ring only
                    }
                    StructureFootprint candidate =
                            new StructureFootprint(foot.id(), desiredX + dx * CELL, desiredZ + dz * CELL,
                                    foot.sizeX(), foot.sizeZ(), foot.clearance());
                    if (!conflictsWith(candidate)) {
                        return Optional.of(candidate);
                    }
                }
            }
        }
        return Optional.empty();
    }

    /** All conflicting pairs among placed footprints (explicit quadratic, tooling only). */
    public List<String> conflicts() {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < all.size(); i++) {
            for (int j = i + 1; j < all.size(); j++) {
                if (StructureFootprint.overlaps(all.get(i), all.get(j))) {
                    out.add(all.get(i).id() + " x " + all.get(j).id());
                }
            }
        }
        return out;
    }

    private List<StructureFootprint> nearby(StructureFootprint foot) {
        List<StructureFootprint> out = new ArrayList<>();
        for (long key : coveredCells(foot)) {
            List<StructureFootprint> bucket = cells.get(key);
            if (bucket != null) {
                out.addAll(bucket);
            }
        }
        return out;
    }

    /** All cell keys a footprint's inflated bounds span. */
    private static List<Long> coveredCells(StructureFootprint foot) {
        int x0 = foot.minX() / CELL;
        int z0 = foot.minZ() / CELL;
        int x1 = foot.maxX() / CELL;
        int z1 = foot.maxZ() / CELL;
        List<Long> keys = new ArrayList<>((x1 - x0 + 1) * (z1 - z0 + 1));
        for (int cx = x0; cx <= x1; cx++) {
            for (int cz = z0; cz <= z1; cz++) {
                keys.add(key(cx, cz));
            }
        }
        return keys;
    }

    private static long key(int cellX, int cellZ) {
        return ((long) cellX << 32) ^ (cellZ & 0xffffffffL);
    }
}