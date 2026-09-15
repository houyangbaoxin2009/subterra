package io.toterra.subterra.engine.datapack;

/**
 * p.2.2 datapack entry kinds — the td datapack speaks seven base entry kinds
 * (mirrors ROAD p.2.2: functions / recipes / loot tables / worldgen /
 * structures / tags / localization) plus {@link #NOISE_SETTINGS}, the first
 * line of the "td = JSON datapack plus" superset refactor. Each base kind maps
 * to a directory segment under {@code data/<namespace>/<kind>/<path>.td};
 * noise settings live under the worldgen data-dir segment
 * {@code worldgen/noise_settings/} and use a fresh {@code noise_settings}
 * directory to sit alongside the vanilla JSON files but carry the {@code .td}
 * extension. Schema-free by design: the payload travels as a raw td table and
 * is interpreted where it is consumed. Pure JDK; no JSON at the core.
 */
public enum EntryKind {

    FUNCTION("function"),
    RECIPE("recipe"),
    LOOT_TABLE("loot_table"),
    WORLDGEN("worldgen"),
    STRUCTURE("structure"),
    TAG("tag"),
    LANG("lang"),
    /** worldgen noise-settings datum — {@code data/<ns>/worldgen/noise_settings/<path>.td}. */
    NOISE_SETTINGS("noise_settings");

    private final String dir;

    EntryKind(String dir) {
        this.dir = dir;
    }

    /** Directory segment name under {@code data/<namespace>/}. */
    public String dir() {
        return dir;
    }

    /** Resolves a data-dir segment (e.g. {@code "loot_table"}) to a kind. */
    public static EntryKind fromDirectory(String dir) {
        for (EntryKind k : values()) {
            if (k.dir.equals(dir)) {
                return k;
            }
        }
        throw new IllegalArgumentException("unknown datapack kind directory: " + dir);
    }

    @Override
    public String toString() {
        return dir;
    }
}