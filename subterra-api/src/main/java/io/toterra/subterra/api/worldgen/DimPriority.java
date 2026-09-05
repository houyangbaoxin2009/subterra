package io.toterra.subterra.api.worldgen;

import java.util.ArrayList;
import java.util.List;

/**
 * Dimension priority for conflict arbitration (architecture §5.2): when a
 * requested profile violates the coexistence chain, the resolver relaxes the
 * lowest-priority dimensions first. Terrain (high) is preserved last; relic
 * (low) yields first. Pure data; deterministic order.
 */
public final class DimPriority {

    private DimPriority() {
    }

    /** High -> low; the first dimension is preserved most strictly. */
    public static final List<EcoDim> HIGH_FIRST = List.of(
            EcoDim.of("terrain"),
            EcoDim.of("climate"),
            EcoDim.of("vegetation"),
            EcoDim.of("hydro"),
            EcoDim.of("surface"),
            EcoDim.of("litho"),
            EcoDim.of("mineral"),
            EcoDim.of("fauna"),
            EcoDim.of("relic"));

    /** Low -> high, the order used when relaxing conflicts. */
    public static final List<EcoDim> FROM_LOW = reversed(HIGH_FIRST);

    private static List<EcoDim> reversed(List<EcoDim> order) {
        List<EcoDim> out = new ArrayList<>(order);
        java.util.Collections.reverse(out);
        return List.copyOf(out);
    }
}