package io.toterra.subterra.api.worldgen;

/**
 * One value of a biosphere {@link EcoDim} (e.g. terrain = "mountain").
 * Immutable value object; identity is (dim, name). Names are case-sensitive
 * and must be stable once published in td rule files.
 */
public record EcoDimValue(EcoDim dim, String name) {

    public static EcoDimValue of(EcoDim dim, String name) {
        return new EcoDimValue(dim, name);
    }

    @Override
    public String toString() {
        return dim.id() + '=' + name;
    }
}