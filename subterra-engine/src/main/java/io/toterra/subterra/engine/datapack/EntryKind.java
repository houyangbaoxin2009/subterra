package io.toterra.subterra.engine.datapack;

/**
 * p.2.2 datapack entry kinds — the td datapack speaks seven entry kinds
 * (mirrors ROAD p.2.2: functions / recipes / loot tables / worldgen /
 * structures / tags / localization). Each maps to a directory segment under
 * {@code data/<namespace>/<kind>/<path>.td}. Schema-free by design: the
 * payload travels as a raw td table and is interpreted where it is consumed.
 * Pure JDK; no JSON anywhere.
 */
public enum EntryKind {

    FUNCTION("function"),
    RECIPE("recipe"),
    LOOT_TABLE("loot_table"),
    WORLDGEN("worldgen"),
    STRUCTURE("structure"),
    TAG("tag"),
    LANG("lang");

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