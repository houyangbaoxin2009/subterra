package io.toterra.subterra.engine.datapack;

import io.toterra.subterra.engine.config.TdTable;
import io.toterra.subterra.engine.config.TdValue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Engine-side schema for vanilla recipe entries re-exported as canonical td
 * (p.2.2 block 5: DataPack → td round-trip), the single source of truth for
 * writing canonical td and reading it back. Two recipe types are supported,
 * mirroring the MC-shell builders {@code DatapackRegistrar.buildShaped} and
 * {@code DatapackRegistrar.buildSmoking} exactly:
 *
 * <ul>
 * <li>{@code minecraft:crafting_shaped}: {@code type}, {@code pattern} (array
 * of row strings), {@code key} (array of {@code [ k = "<char>", v = [ item = "<id>" ] ]}
 * sub-tables), {@code result} ({@code [ item = "<id>", count = <long> ]}) —
 * count optional, default 1, clamped to a minimum of 1.</li>
 * <li>{@code minecraft:smoking}: {@code type}, {@code ingredient}
 * ({@code [ item = "<id>" ]}), {@code result}
 * ({@code [ item = "<id>", experience = <float-ish>, cooking_time = <long> ]}) —
 * cooking_time optional, default 200, clamped to a minimum of 1; experience
 * optional, default 0.0.</li>
 * </ul>
 *
 * <p>Reading normalizes the payload: resultCount = max(1, asInt);
 * cookingTime = max(1, asInt); experience passes through
 * {@link RecipeDatum#cleanExperience} which round-trips float precision
 * ({@code Float.toString} then parse back) so a written value such as
 * {@code 0.35} stays clean instead of drifting into
 * {@code 0.3499999940395355}. {@link #write()} is deterministic and canonical:
 * {@code count} is omitted when it equals 1, while {@code experience} and
 * {@code cooking_time} are always written (they carry defaults). This makes
 * {@code write} idempotent for the canonical seed payloads, satisfying the
 * engine-side round-trip contract.
 */
public record RecipeDatum(String type,
                          List<String> pattern,   // shaped only, else List.of()
                          List<Keyed> key,        // shaped only, else List.of(); ordered (char → item id), char = 1-char String
                          String resultItem,
                          long resultCount,
                          String ingredientItem,  // smoking only, else null
                          double experience,      // smoking only, else 0
                          long cookingTime) {     // smoking only, else 0

    /** A shaped-key mapping: a 1-char key string to its item id. */
    public record Keyed(String k, String item) {
    }

    private static final String SHAPED = "minecraft:crafting_shaped";
    private static final String SMOKING = "minecraft:smoking";

    /**
     * Reads a recipe payload table into a canonical {@link RecipeDatum},
     * normalizing count / cooking_time (clamped ≥ 1) and experience (via
     * {@link #cleanExperience}). Throws {@link IllegalArgumentException} for an
     * unsupported recipe type or a missing required table.
     */
    public static RecipeDatum read(TdTable p) {
        String type = p.get("type") != null ? p.get("type").asString() : "";
        return switch (type) {
            case SHAPED -> readShaped(p);
            case SMOKING -> readSmoking(p);
            default -> throw new IllegalArgumentException("unsupported recipe type: " + type);
        };
    }

    private static RecipeDatum readShaped(TdTable p) {
        List<String> pattern = elementStrings(p.get("pattern"));
        Map<Character, String> keyMap = new LinkedHashMap<>();
        TdValue keyValue = p.get("key");
        if (keyValue instanceof TdTable keys) {
            for (TdValue item : keys.elements()) {
                if (!(item instanceof TdTable kv)) {
                    continue;
                }
                String k = kv.get("k") != null ? kv.get("k").asString() : "";
                TdValue v = kv.get("v");
                if (k.length() != 1 || !(v instanceof TdTable vt)) {
                    continue; // invalid key entry — skip
                }
                String id = vt.get("item") != null ? vt.get("item").asString() : "";
                if (!id.isEmpty()) {
                    keyMap.putIfAbsent(k.charAt(0), id); // first occurrence wins
                }
            }
        }
        List<Keyed> key = new ArrayList<>();
        for (Map.Entry<Character, String> e : keyMap.entrySet()) {
            key.add(new Keyed(String.valueOf(e.getKey()), e.getValue()));
        }
        TdValue resultValue = p.get("result");
        if (!(resultValue instanceof TdTable result)) {
            throw new IllegalArgumentException("shaped recipe missing result table");
        }
        String resultItem = result.get("item") != null ? result.get("item").asString() : "";
        long resultCount = result.get("count") != null ? Math.max(1L, result.get("count").asInt()) : 1L;
        return new RecipeDatum(SHAPED, pattern, List.copyOf(key), resultItem, resultCount, null, 0.0, 0L);
    }

    private static RecipeDatum readSmoking(TdTable p) {
        TdValue ingredientValue = p.get("ingredient");
        TdValue resultValue = p.get("result");
        if (!(ingredientValue instanceof TdTable ingredient) || !(resultValue instanceof TdTable result)) {
            throw new IllegalArgumentException("smoking recipe requires ingredient and result tables");
        }
        String ingredientItem = ingredient.get("item") != null ? ingredient.get("item").asString() : "";
        String resultItem = result.get("item") != null ? result.get("item").asString() : "";
        double experience = result.get("experience") != null
                ? cleanExperience(result.get("experience").asFloat()) : 0.0;
        long cookingTime = result.get("cooking_time") != null
                ? Math.max(1L, result.get("cooking_time").asInt()) : 200L;
        return new RecipeDatum(SMOKING, List.of(), List.of(), resultItem, 1L, ingredientItem, experience, cookingTime);
    }

    /**
     * Writes this datum back to its canonical td table (deterministic field
     * order). Omitted fields differ per type as documented above.
     */
    public TdTable write() {
        TdTable.Builder b = TdTable.builder();
        switch (type) {
            case SHAPED -> {
                b.put("type", type);
                TdTable.Builder patternB = TdTable.builder();
                for (String row : pattern) {
                    patternB.element(TdValue.str(row));
                }
                b.put("pattern", patternB.build());
                TdTable.Builder keyB = TdTable.builder();
                for (Keyed kv : key) {
                    TdTable.Builder subB = TdTable.builder();
                    subB.put("k", TdValue.str(kv.k()));
                    TdTable.Builder vB = TdTable.builder();
                    vB.put("item", TdValue.str(kv.item()));
                    subB.put("v", vB.build());
                    keyB.element(subB.build());
                }
                b.put("key", keyB.build());
                TdTable.Builder resultB = TdTable.builder();
                resultB.put("item", TdValue.str(resultItem));
                if (resultCount != 1) {
                    resultB.put("count", TdValue.of(resultCount));
                }
                b.put("result", resultB.build());
            }
            case SMOKING -> {
                b.put("type", type);
                TdTable.Builder ingredientB = TdTable.builder();
                ingredientB.put("item", TdValue.str(ingredientItem));
                b.put("ingredient", ingredientB.build());
                TdTable.Builder resultB = TdTable.builder();
                resultB.put("item", TdValue.str(resultItem));
                resultB.put("experience", TdValue.of(experience));
                resultB.put("cooking_time", TdValue.of(cookingTime));
                b.put("result", resultB.build());
            }
            default -> throw new IllegalArgumentException("unsupported recipe type: " + type);
        }
        return b.build();
    }

    /** String elements of a table value; empty list when absent or not a table. */
    private static List<String> elementStrings(TdValue v) {
        if (!(v instanceof TdTable t)) {
            return List.of();
        }
        return t.elements().stream().map(TdValue::asString).toList();
    }

    /**
     * Round-trips a double through float precision so a fractional experience
     * like {@code 0.35} is written clean instead of as float→double dust
     * ({@code 0.3499999940395355}).
     */
    static double cleanExperience(double d) {
        return Double.parseDouble(Float.toString((float) d));
    }
}