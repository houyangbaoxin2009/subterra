package io.toterra.subterra.api.worldgen;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Vanilla-equivalent default mapping (architecture §5.2): every vanilla biome
 * gets a legal nine-dimension profile so installing Subterra leaves worldgen
 * unchanged. Also exposes the built-in value pool per dimension, used by the
 * rule set and the resolver. Pure data; no Minecraft runtime.
 */
public final class VanillaDefaults {

    private VanillaDefaults() {
    }

    /** Value pool, dimension by dimension. */
    public static List<EcoDimValue> allValues() {
        return List.of(
                v("terrain", "plains"), v("terrain", "plateau"), v("terrain", "hills"),
                v("terrain", "mountains"), v("terrain", "depths"),
                v("climate", "temperate"), v("climate", "arid"), v("climate", "cold"),
                v("climate", "wet"), v("climate", "tropical"),
                v("vegetation", "grassland"), v("vegetation", "forest"), v("vegetation", "dense"),
                v("vegetation", "barren"), v("vegetation", "savanna"),
                v("hydro", "none"), v("hydro", "river"), v("hydro", "lake"),
                v("hydro", "oceanic"), v("hydro", "wetlands"),
                v("surface", "soil"), v("surface", "sand"), v("surface", "stone"),
                v("surface", "snow"), v("surface", "ice"),
                v("litho", "sedimentary"), v("litho", "igneous"),
                v("litho", "metamorphic"), v("litho", "mixed"),
                v("mineral", "sparse"), v("mineral", "common"), v("mineral", "rich"),
                v("fauna", "low"), v("fauna", "medium"), v("fauna", "high"), v("fauna", "dense"),
                v("relic", "none"), v("relic", "scattered"), v("relic", "common"), v("relic", "rich"));
    }

    private static EcoDimValue v(String dim, String name) {
        return EcoDimValue.of(EcoDim.of(dim), name);
    }

    /** Vanilla biome id (resource location) -> legal profile. */
    public static Map<String, EcoProfile> defaults() {
        Map<String, EcoProfile> map = new LinkedHashMap<>();
        map.put("minecraft:plains", p("plains", "temperate", "grassland", "none", "soil", "sedimentary", "common", "high", "scattered"));
        map.put("minecraft:forest", p("plains", "temperate", "forest", "none", "soil", "sedimentary", "common", "high", "scattered"));
        map.put("minecraft:desert", p("plains", "arid", "barren", "none", "sand", "sedimentary", "common", "low", "scattered"));
        map.put("minecraft:ocean", p("depths", "temperate", "barren", "oceanic", "stone", "igneous", "sparse", "dense", "none"));
        map.put("minecraft:mountains", p("mountains", "cold", "barren", "river", "stone", "metamorphic", "rich", "medium", "common"));
        map.put("minecraft:swamp", p("plains", "wet", "dense", "wetlands", "soil", "sedimentary", "common", "high", "scattered"));
        map.put("minecraft:jungle", p("hills", "tropical", "dense", "river", "soil", "mixed", "rich", "dense", "common"));
        map.put("minecraft:tundra", p("plains", "cold", "barren", "none", "snow", "mixed", "sparse", "low", "none"));
        return map;
    }

    /** Profile for a biome id; null when unmapped. */
    public static EcoProfile profileFor(String biomeId) {
        return defaults().get(biomeId);
    }

    private static EcoProfile p(String terrain, String climate, String vegetation, String hydro,
                                 String surface, String litho, String mineral, String fauna, String relic) {
        return EcoProfile.builder()
                .set(v("terrain", terrain))
                .set(v("climate", climate))
                .set(v("vegetation", vegetation))
                .set(v("hydro", hydro))
                .set(v("surface", surface))
                .set(v("litho", litho))
                .set(v("mineral", mineral))
                .set(v("fauna", fauna))
                .set(v("relic", relic))
                .build();
    }
}