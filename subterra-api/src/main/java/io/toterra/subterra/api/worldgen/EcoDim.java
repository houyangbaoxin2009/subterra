package io.toterra.subterra.api.worldgen;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One biosphere dimension of the nine-dimension model (architecture §5.2):
 * terrain / climate / vegetation / hydro / surface / litho / mineral / fauna /
 * relic. Dimensions are registered here; the ecosystem adds values through
 * {@link EcoDimValue}. Pure and deterministic; no Minecraft runtime.
 */
public record EcoDim(String id) {

    /** All built-in dimensions, in canonical order. */
    public static final List<EcoDim> ALL = List.of(
            new EcoDim("terrain"),
            new EcoDim("climate"),
            new EcoDim("vegetation"),
            new EcoDim("hydro"),
            new EcoDim("surface"),
            new EcoDim("litho"),
            new EcoDim("mineral"),
            new EcoDim("fauna"),
            new EcoDim("relic"));

    /** Lookup by id; null when unknown. */
    public static EcoDim of(String id) {
        for (EcoDim d : ALL) {
            if (d.id().equals(id)) {
                return d;
            }
        }
        return null;
    }

    /** The registered dimensions as a quick id map (each id -> dimension). */
    public static Map<String, EcoDim> byId() {
        Map<String, EcoDim> map = new LinkedHashMap<>();
        for (EcoDim d : ALL) {
            map.put(d.id(), d);
        }
        return map;
    }

    /**
     * Registers an extension dimension, preserving registration order for
     * deterministic resolution. Unknown ids are only possible via extensions.
     */
    public static List<EcoDim> extendWith(List<EcoDim> extra) {
        List<EcoDim> out = new ArrayList<>(ALL);
        for (EcoDim d : extra) {
            if (!out.contains(d)) {
                out.add(d);
            }
        }
        return List.copyOf(out);
    }
}