package io.toterra.subterra.optim.worldgen.guard;

/**
 * One structure footprint: a two-dimensional area (in structure-space blocks)
 * with an optional safety clearance on all sides. Collision is computed on the
 * inflated bounds, so "no collision" already respects the configured gap.
 * Pure JDK; no Minecraft runtime.
 *
 * @param id        stable structure identifier (e.g. the structure resource id)
 * @param minX      minimum block X of the footprint
 * @param minZ      minimum block Z of the footprint
 * @param sizeX     footprint extent along X
 * @param sizeZ     footprint extent along Z
 * @param clearance extra gap required around the footprint (>= 0)
 */
public record StructureFootprint(String id, int minX, int minZ, int sizeX, int sizeZ, int clearance) {

    public StructureFootprint {
        if (sizeX <= 0 || sizeZ <= 0) {
            throw new IllegalArgumentException("footprint must have positive size");
        }
        if (clearance < 0) {
            throw new IllegalArgumentException("clearance must be >= 0");
        }
    }

    /** Inflated maximum X (inclusive), including clearance. */
    public int maxX() {
        return minX + sizeX - 1 + clearance;
    }

    /** Inflated maximum Z (inclusive), including clearance. */
    public int maxZ() {
        return minZ + sizeZ - 1 + clearance;
    }

    /** True when the two inflated bounds overlap (a conflict). */
    public static boolean overlaps(StructureFootprint a, StructureFootprint b) {
        return a.minX <= b.maxX() && b.minX <= a.maxX()
                && a.minZ <= b.maxZ() && b.minZ <= a.maxZ();
    }

    /** Moves the footprint to a new origin, keeping size and clearance. */
    public StructureFootprint at(int x, int z) {
        return new StructureFootprint(id, x, z, sizeX, sizeZ, clearance);
    }

    @Override
    public String toString() {
        return id + "@(" + minX + ',' + minZ + ", " + sizeX + 'x' + sizeZ + " clr " + clearance + ')';
    }
}