package io.toterra.subterra.runtime.datapack;

import io.toterra.subterra.engine.config.TdTable;
import io.toterra.subterra.engine.datapack.RecipeDatum;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import net.minecraft.world.item.crafting.SmokingRecipe;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MC-shell exporter: converts a live vanilla recipe holder back to its canonical
 * td payload (the exact shape {@link RecipeDatum#write()} emits), the shell-side
 * counterpart of the engine recipe schema. Used by the p.2.2 block 5 export
 * round-trip to prove td is the closed-loop home of recipe data.
 */
public final class DatapackRecipeExporter {
    private DatapackRecipeExporter() {
    }

    /** Character pool used to rebuild shaped pattern rows deterministically. */
    private static final String POOL = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

    /**
     * Converts a vanilla recipe holder to its canonical td payload (the shape
     * RecipeDatum.write() emits). Returns null for recipe types that are not
     * exportable. Deterministic: the vanilla ShapedRecipe object retains no
     * source key characters, so pattern rows are reconstructed with characters
     * assigned deterministically by first cell occurrence (A,B,C,...). Re-import
     * through DatapackRegistrar.buildRecipe then re-export must produce the
     * identical td text (byte-exact round trip).
     */
    public static TdTable exportTd(RecipeHolder<?> holder, RegistryAccess ra) {
        Recipe<?> value = holder.value();
        if (value instanceof ShapedRecipe shaped) {
            return exportShaped(shaped, ra);
        } else if (value instanceof SmokingRecipe smoking) {
            return exportSmoking(smoking, ra);
        }
        return null;
    }

    private static TdTable exportShaped(ShapedRecipe shaped, RegistryAccess ra) {
        ShapedRecipePattern p = shaped.pattern;
        int height = p.height();
        if (height == 0) {
            return null;
        }
        Map<String, Character> charFor = new LinkedHashMap<>();
        int next = 0;
        List<String> pattern = new ArrayList<>();
        for (int r = 0; r < height; r++) {
            StringBuilder row = new StringBuilder();
            for (int c = 0; c < p.width(); c++) {
                Ingredient ing = p.ingredients().get(r * p.width() + c);
                if (ing.isEmpty()) {
                    row.append(' ');
                    continue;
                }
                ItemStack[] items = ing.getItems();
                if (items.length == 0) {
                    row.append(' ');
                    continue;
                }
                String itemId = BuiltInRegistries.ITEM.getKey(items[0].getItem()).toString();
                Character existing = charFor.get(itemId);
                if (existing != null) {
                    row.append(existing);
                } else {
                    char ch = POOL.charAt(next++);
                    charFor.put(itemId, ch);
                    row.append(ch);
                }
            }
            pattern.add(row.toString());
        }
        List<RecipeDatum.Keyed> key = new ArrayList<>();
        for (Map.Entry<String, Character> e : charFor.entrySet()) {
            key.add(new RecipeDatum.Keyed(String.valueOf(e.getValue()), e.getKey()));
        }
        ItemStack res = shaped.getResultItem(ra);
        String resultItem = BuiltInRegistries.ITEM.getKey(res.getItem()).toString();
        long resultCount = res.getCount();
        RecipeDatum datum = new RecipeDatum("minecraft:crafting_shaped", pattern, key,
                resultItem, resultCount, null, 0.0, 0L);
        return datum.write();
    }

    private static TdTable exportSmoking(SmokingRecipe smoking, RegistryAccess ra) {
        Ingredient ing = smoking.getIngredients().get(0);
        ItemStack[] items = ing.getItems();
        if (items.length == 0) {
            return null;
        }
        String ingredientItem = BuiltInRegistries.ITEM.getKey(items[0].getItem()).toString();
        ItemStack res = smoking.getResultItem(ra);
        String resultItem = BuiltInRegistries.ITEM.getKey(res.getItem()).toString();
        // same float-precision normalization as the engine read side, so the
        // object-derived export matches the payload-derived export byte-for-byte
        double experience = RecipeDatum.cleanExperience(smoking.getExperience());
        long cookingTime = smoking.getCookingTime();
        RecipeDatum datum = new RecipeDatum("minecraft:smoking", List.of(), List.of(),
                resultItem, 1L, ingredientItem, experience, cookingTime);
        return datum.write();
    }
}