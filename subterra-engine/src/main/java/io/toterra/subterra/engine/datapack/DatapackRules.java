package io.toterra.subterra.engine.datapack;

import io.toterra.subterra.engine.config.TdTable;
import io.toterra.subterra.engine.config.TdValue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * p.2.2.9 datapack-level rules — pack-level key→td-value pairs declared in
 * {@code pack.td} ({@code rules = [ [ k = "wild", v = true ], ... ]}), intended
 * for datapack-level tuning that can be overridden per save/session. This is the
 * forward hook to the p.2.17 rule system.
 *
 * <p>Resolution precedence (deterministic):
 * <ol>
 * <li>each pack contributes its own rules; a later pack's value for the same key
 * overwrites an earlier one;</li>
 * <li>save/session overrides are then applied and win over every pack.</li>
 * </ol>
 *
 * <p>Schema-free: a malformed or missing element ({@code k}/{@code v} absent,
 * element not a table) is skipped silently rather than raising, consistent with
 * the loader philosophy. First occurrence of a key within one manifest wins.
 */
public final class DatapackRules {

    private DatapackRules() {
    }

    /**
     * Reads the {@code rules} table from a {@code pack.td} manifest. Absent or
     * non-table {@code rules} → an empty map.
     */
    public static Map<String, TdValue> fromManifest(TdTable manifest) {
        TdValue rulesValue = manifest.get("rules");
        if (!(rulesValue instanceof TdTable rulesTable)) {
            return Map.of();
        }
        Map<String, TdValue> out = new LinkedHashMap<>();
        for (TdValue item : rulesTable.elements()) {
            if (!(item instanceof TdTable t)) {
                continue; // skip silently, schema-free
            }
            String k = t.get("k") != null ? t.get("k").asString() : "";
            if (k.isBlank()) {
                continue; // needs a non-blank key
            }
            TdValue v = t.get("v");
            if (v == null) {
                continue; // missing value → skip
            }
            out.putIfAbsent(k, v); // first occurrence wins
        }
        return out;
    }

    /**
     * Resolves the effective rules across the loaded packs (later pack wins for
     * the same key) then applies the save/session overrides (which win over every
     * pack). Deterministic, insertion-ordered map.
     */
    public static Map<String, TdValue> resolve(List<Datapack> packs, Map<String, TdValue> saveOverrides) {
        Map<String, TdValue> out = new LinkedHashMap<>();
        for (Datapack pack : packs) {
            out.putAll(pack.rules());
        }
        out.putAll(saveOverrides);
        return out;
    }

    /**
     * p.2.3.5 two-tier config resolution: the global layer first, then the
     * save-override layer wins on a conflicting key. Both inputs already carry
     * their own precedence, so this is a plain {@code putAll} merge (global
     * first, then overrides). Deterministic, insertion-ordered {@code LinkedHashMap}.
     * 双层配置（p.2.3.5）：先并入全局层，存档覆盖层对冲突 key 全部胜出。两层本身已各自定好序，
     * 这里就是朴素 {@code putAll}（global 入内 → overrides 覆盖），确定、保序。
     */
    public static Map<String, TdValue> resolveTiered(Map<String, TdValue> global,
                                                     Map<String, TdValue> saveOverrides) {
        Map<String, TdValue> out = new LinkedHashMap<>();
        out.putAll(global);
        out.putAll(saveOverrides);
        return out;
    }

    /**
     * Deterministic marker text for the effective rules: keys sorted
     * alphabetically, {@code k=value} joined by {@code "; "}. A scalar renders as
     * {@code value.toString()}; a {@link TdTable} value renders as {@code (table)}
     * (its nested structure is not flattened into the linear marker — a table
     * value is treated opaquely). Empty map → {@code ""}.
     */
    public static String render(Map<String, TdValue> effective) {
        if (effective.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, TdValue> e : new TreeMap<>(effective).entrySet()) {
            if (!first) {
                sb.append("; ");
            }
            first = false;
            TdValue v = e.getValue();
            sb.append(e.getKey()).append('=')
                    .append(v instanceof TdTable ? "(table)" : v.toString());
        }
        return sb.toString();
    }
}