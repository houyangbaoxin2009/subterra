package io.toterra.subterra.runtime.datapack;

import io.toterra.subterra.engine.config.Td;
import io.toterra.subterra.engine.config.TdTable;
import io.toterra.subterra.engine.config.TdValue;
import io.toterra.subterra.engine.datapack.Datapack;
import io.toterra.subterra.engine.datapack.DatapackEntry;
import io.toterra.subterra.engine.datapack.DatapackLoader;
import io.toterra.subterra.engine.datapack.EntryKind;
import io.toterra.subterra.engine.datapack.TieLogicBundle;
import io.toterra.subterra.engine.datapack.TieLogicLoader;
import io.toterra.subterra.engine.tie.TieFunction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CookingBookCategory;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import net.minecraft.world.item.crafting.SmokingRecipe;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MC-shell datapack registrar (p.2.2 block 2): scans every td pack from the
 * configured datapacks directory through the engine.datapack loader (pure td
 * direct loading), then registers the content per kind:
 *
 * <ul>
 * <li>tag → entry id → payload {@code values} index (+ log marker);</li>
 * <li>lang → td k/v pairs → key/value map (+ log markers);</li>
 * <li>recipe → td {@code recipe = [ type = "minecraft:crafting_shaped", … ]}
 * compiled into a vanilla {@link ShapedRecipe} holder and merged into the live
 * {@link RecipeManager} via {@code replaceRecipes} (+ log markers);</li>
 * <li>function → tie binding resolved through {@link TieLogicLoader} and called
 * via {@link TieFunction#invoke0()} (0-arg seed contract) (+ log markers).</li>
 * </ul>
 *
 * <p>Deterministic markers feed the E2E gate; a broken pack or a malformed
 * recipe never takes the server down — it is named in a warn and skipped.
 * Registry merge order vs the item_control recipe stripper: both hook
 * ServerStarted LOWEST; whichever runs first, banned outputs can only be
 * stripped, never re-added by this registrar.
 */
public final class DatapackRegistrar implements AutoCloseable {

    public static final String MARKER = "[Subterra datapack]";

    private final MinecraftServer server;
    private final List<Datapack> packs = new ArrayList<>();
    private final Map<EntryKind, List<DatapackEntry>> byKind = new LinkedHashMap<>();
    private final Map<Path, Datapack> packsByDir = new LinkedHashMap<>();
    /** Registered tag index: entry id ({@code ns:tag/path}) → payload values. */
    private final Map<String, List<String>> tagValues = new LinkedHashMap<>();
    /** Registered localization index: key → value (from lang k/v pairs). */
    private final Map<String, String> langMap = new LinkedHashMap<>();
    /** Vanilla recipes built from td and merged into the RecipeManager. */
    private final List<RecipeHolder<?>> recipes = new ArrayList<>();
    /** Loaded tie libraries for FUNCTION entries; closed with the registrar. */
    private final List<TieLogicBundle> tieBundles = new ArrayList<>();

    private DatapackRegistrar(MinecraftServer server) {
        this.server = server;
        for (EntryKind k : EntryKind.values()) {
            byKind.put(k, new ArrayList<>());
        }
    }

    public static DatapackRegistrar forServer(MinecraftServer server) {
        return new DatapackRegistrar(server);
    }

    public MinecraftServer server() {
        return server;
    }

    public Map<EntryKind, List<DatapackEntry>> entriesByKind() {
        return byKind;
    }

    /** Loads every td pack under {@code root} (one subdirectory per pack). */
    public void loadPacks(Path root) {
        if (!Files.isDirectory(root)) {
            DatapackRuntime.LOGGER.info("{} datapacks dir absent: {}", MARKER, root);
            return;
        }
        List<Path> packDirs = new ArrayList<>();
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(root)) {
            ds.forEach(p -> {
                if (Files.isDirectory(p)) {
                    packDirs.add(p);
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException("datapacks dir scan failed: " + root, e);
        }
        packDirs.sort(java.util.Comparator.comparing(Path::toString));
        int loaded = 0;
        for (Path dir : packDirs) {
            try {
                Datapack dp = DatapackLoader.load(dir);
                packs.add(dp);
                packsByDir.put(dir, dp);
                loaded++;
                for (DatapackEntry e : dp.entries().values()) {
                    byKind.get(e.kind()).add(e);
                }
            } catch (IllegalArgumentException e) {
                // a broken pack must never take the server down; it is named and skipped
                DatapackRuntime.LOGGER.error("{} skipping broken pack {}: {}", MARKER, dir, e.getMessage());
            }
        }
        logLoadSummary(loaded);
    }

    public void logLoadSummary(int loaded) {
        DatapackRuntime.LOGGER.info("{} loaded {} packs, {} entries (tag={}, lang={}, recipe={}, loot_table={}, "
                        + "worldgen={}, structure={}, function={})",
                MARKER, loaded, byKind.values().stream().mapToInt(List::size).sum(),
                byKind.get(EntryKind.TAG).size(), byKind.get(EntryKind.LANG).size(),
                byKind.get(EntryKind.RECIPE).size(), byKind.get(EntryKind.LOOT_TABLE).size(),
                byKind.get(EntryKind.WORLDGEN).size(), byKind.get(EntryKind.STRUCTURE).size(),
                byKind.get(EntryKind.FUNCTION).size());
    }

    /** Registers tag / lang / recipe / tie content and logs deterministic detail markers. */
    public void registerContent() {
        registerTags();
        registerLang();
        registerRecipes();
        registerTie();
        registerStaged();
        registerTdCanonical();
        buildLootTables();
        for (Map.Entry<String, List<String>> e : tagValues.entrySet()) {
            DatapackRuntime.LOGGER.info("{} tag {} values={}", MARKER, e.getKey(), e.getValue());
        }
        int shown = 0;
        for (Map.Entry<String, String> e : langMap.entrySet()) {
            if (shown < 8) {
                DatapackRuntime.LOGGER.info("{} lang {} = {}", MARKER, e.getKey(), e.getValue());
                shown++;
            }
        }
        if (!langMap.isEmpty()) {
            DatapackRuntime.LOGGER.info("{} lang entries={}", MARKER, langMap.size());
        }
        for (RecipeHolder<?> holder : recipes) {
            DatapackRuntime.LOGGER.info("{} register recipe {}", MARKER, holder.id());
        }
        if (!recipes.isEmpty()) {
            DatapackRuntime.LOGGER.info("{} recipes registered={}", MARKER, recipes.size());
        }
        for (Map.Entry<String, Long> e : functionCalls.entrySet()) {
            DatapackRuntime.LOGGER.info("{} tie {} -> {}", MARKER, e.getKey(), e.getValue());
        }
        if (!tieBundles.isEmpty()) {
            DatapackRuntime.LOGGER.info("{} tie libraries={}", MARKER, tieBundles.size());
        }
    }

    /** tag index: entry id → payload {@code values}. */
    public Map<String, List<String>> tagValues() {
        return tagValues;
    }

    /** localization index: key → value. */
    public Map<String, String> langMap() {
        return langMap;
    }

    /** Vanilla recipes merged into the RecipeManager (holder form). */
    public List<RecipeHolder<?>> recipes() {
        return recipes;
    }

    private void registerTags() {
        for (DatapackEntry e : byKind.get(EntryKind.TAG)) {
            tagValues.put(e.id(), stringList(e.payload().get("values")));
        }
    }

    private void registerLang() {
        for (DatapackEntry e : byKind.get(EntryKind.LANG)) {
            for (TdValue item : e.payload().elements()) {
                if (!(item instanceof TdTable t)) {
                    continue;
                }
                String k = t.get("k") != null ? t.get("k").asString() : "";
                String v = t.get("v") != null ? t.get("v").asString() : "";
                if (!k.isBlank()) {
                    langMap.put(k, v);
                }
            }
        }
    }

    private void registerRecipes() {
        for (DatapackEntry e : byKind.get(EntryKind.RECIPE)) {
            RecipeHolder<?> holder = buildRecipe(e);
            if (holder != null) {
                recipes.add(holder);
            } else {
                DatapackRuntime.LOGGER.warn("{} skip recipe {} (unsupported or malformed)", MARKER, e.id());
            }
        }
        if (recipes.isEmpty()) {
            return;
        }
        RecipeManager manager = server.getRecipeManager();
        Collection<RecipeHolder<?>> existing = manager.getRecipes();
        List<RecipeHolder<?>> merged = new ArrayList<>(existing.size() + recipes.size());
        merged.addAll(existing);
        merged.addAll(recipes);
        manager.replaceRecipes(merged);
    }

    /** Builds a vanilla recipe holder from a td recipe entry; null on any malformation. */
    private RecipeHolder<?> buildRecipe(DatapackEntry e) {
        TdTable p = e.payload();
        String type = p.get("type") != null ? p.get("type").asString() : "";
        switch (type) {
            case "minecraft:crafting_shaped" -> {
                return buildShaped(e);
            }
            case "minecraft:smoking" -> {
                return buildSmoking(e);
            }
            default -> {
                return null;
            }
        }
    }

    private RecipeHolder<?> buildShaped(DatapackEntry e) {
        TdTable p = e.payload();
        List<String> pattern = stringList(p.get("pattern"));
        if (pattern.isEmpty() || pattern.size() > 3) {
            return null;
        }
        Map<Character, Ingredient> key = new HashMap<>();
        TdValue keyValue = p.get("key");
        if (keyValue instanceof TdTable keys) {
            for (TdValue item : keys.elements()) {
                if (!(item instanceof TdTable kv)) {
                    continue;
                }
                String ks = kv.get("k") != null ? kv.get("k").asString() : "";
                TdValue v = kv.get("v");
                if (ks.length() != 1 || !(v instanceof TdTable vt)) {
                    continue;
                }
                String id = vt.get("item") != null ? vt.get("item").asString() : "";
                Item ingredient = itemById(id);
                if (ingredient == null || ingredient == Items.AIR) {
                    continue;
                }
                key.put(ks.charAt(0), Ingredient.of(ingredient));
            }
        }
        TdValue resultValue = p.get("result");
        if (!(resultValue instanceof TdTable result)) {
            return null;
        }
        String rid = result.get("item") != null ? result.get("item").asString() : "";
        Item resultItem = itemById(rid);
        if (resultItem == null || resultItem == Items.AIR) {
            return null;
        }
        int count = (int) (result.get("count") != null ? Math.max(1, result.get("count").asInt()) : 1L);
        ShapedRecipePattern patternData;
        try {
            patternData = ShapedRecipePattern.of(key, pattern);
        } catch (Throwable t) {
            return null;
        }
        ShapedRecipe recipe = new ShapedRecipe("", CraftingBookCategory.MISC, patternData,
                new ItemStack(resultItem, count), true);
        ResourceLocation id = ResourceLocation.tryParse(e.namespace() + ":" + e.path());
        return id == null ? null : new RecipeHolder<>(id, recipe);
    }

    /** td smoking recipe → vanilla {@link SmokingRecipe} (ingredient/result/experience/cooking_time). */
    private RecipeHolder<?> buildSmoking(DatapackEntry e) {
        TdTable p = e.payload();
        TdValue ingredientValue = p.get("ingredient");
        TdValue resultValue = p.get("result");
        if (!(ingredientValue instanceof TdTable ingredient) || !(resultValue instanceof TdTable result)) {
            return null;
        }
        String iid = ingredient.get("item") != null ? ingredient.get("item").asString() : "";
        String rid = result.get("item") != null ? result.get("item").asString() : "";
        Item input = itemById(iid);
        Item output = itemById(rid);
        if (input == null || input == Items.AIR || output == null || output == Items.AIR) {
            return null;
        }
        float experience = result.get("experience") != null
                ? (float) result.get("experience").asFloat() : 0.0f;
        int cookingTime = (int) (result.get("cooking_time") != null
                ? Math.max(1, result.get("cooking_time").asInt()) : 200L);
        SmokingRecipe recipe = new SmokingRecipe("", CookingBookCategory.FOOD,
                Ingredient.of(input), new ItemStack(output), experience, cookingTime);
        ResourceLocation id = ResourceLocation.tryParse(e.namespace() + ":" + e.path());
        return id == null ? null : new RecipeHolder<>(id, recipe);
    }

    private static Item itemById(String id) {
        ResourceLocation loc = id == null ? null : ResourceLocation.tryParse(id);
        return loc == null ? null : BuiltInRegistries.ITEM.get(loc);
    }

    /** A canonical-input check: derive a deterministic canonical string from the
     *  built vanilla recipe and from the source td payload — the td -> vanilla
     *  translation must agree exactly (marker ok/mismatch per recipe). */
    private void registerTdCanonical() {
        RegistryAccess ra = server.registryAccess();
        for (RecipeHolder<?> holder : recipes) {
            DatapackEntry entry = byKind.get(EntryKind.RECIPE).stream()
                    .filter(e -> (e.namespace() + ":" + e.path()).equals(holder.id().toString()))
                    .findFirst().orElse(null);
            String fromObject = holderCanonical(holder, ra);
            String fromTd = entry != null ? tdCanonical(entry) : null;
            boolean ok = fromTd != null && fromObject.equals(fromTd);
            DatapackRuntime.LOGGER.info("{} recipe canonical {} {}",
                    MARKER, holder.id(), ok ? "ok" : "mismatch");
        }
    }

    /** Canonical string of a built recipe holder (public API only). */
    private static String holderCanonical(RecipeHolder<?> holder, RegistryAccess ra) {
        Recipe<?> value = holder.value();
        StringBuilder sb = new StringBuilder();
        if (value instanceof ShapedRecipe shaped) {
            ShapedRecipePattern p = shaped.pattern; // public final field (ShapedRecipe.pattern)
            sb.append("shaped").append('|').append(p.width()).append('x').append(p.height());
            for (Ingredient i : p.ingredients()) {
                sb.append('|').append(ingredientKey(i));
            }
            ItemStack res = shaped.getResultItem(ra);
            sb.append('|').append(itemKey(res));
        } else if (value instanceof SmokingRecipe smoking) {
            sb.append("smoking").append('|').append(ingredientKey(smoking.getIngredients().get(0)));
            ItemStack res = smoking.getResultItem(ra);
            sb.append('|').append(itemKey(res));
            sb.append('|').append(smoking.getExperience()).append('|').append(smoking.getCookingTime());
        }
        return sb.toString();
    }

    /** Canonical string derived from the td payload (parse-side mirror). */
    private static String tdCanonical(DatapackEntry e) {
        TdTable p = e.payload();
        String type = p.get("type") != null ? p.get("type").asString() : "";
        switch (type) {
            case "minecraft:crafting_shaped" -> {
                List<String> rows = tdStrings(p.get("pattern"));
                if (rows.isEmpty()) {
                    return null;
                }
                int h = rows.size();
                int w = 0;
                for (String row : rows) {
                    w = Math.max(w, row.length());
                }
                w = Math.max(1, w);
                java.util.Map<Character, String> keyItem = tdKeyItems(p.get("key"));
                StringBuilder sb = new StringBuilder("shaped").append('|').append(w).append('x').append(h);
                for (String row : rows) {
                    for (int c = 0; c < w; c++) {
                        char ch = c < row.length() ? row.charAt(c) : ' ';
                        String itemId = keyItem.get(ch);
                        sb.append('|').append(itemId == null ? " " : itemId + "x1");
                    }
                }
                TdValue resultValue = p.get("result");
                if (!(resultValue instanceof TdTable result)) {
                    return null;
                }
                sb.append('|').append(itemKeyOf(result));
                return sb.toString();
            }
            case "minecraft:smoking" -> {
                TdValue ingredientValue = p.get("ingredient");
                TdValue resultValue = p.get("result");
                if (!(ingredientValue instanceof TdTable ingredient)
                        || !(resultValue instanceof TdTable result)) {
                    return null;
                }
                String inputId = ingredient.get("item") != null ? ingredient.get("item").asString() : "";
                StringBuilder sb = new StringBuilder("smoking").append('|').append(inputId + "x1")
                        .append('|').append(itemKeyOf(result));
                sb.append('|').append(result.get("experience") != null
                        ? Float.toString((float) result.get("experience").asFloat()) : "0.0");
                sb.append('|').append(result.get("cooking_time") != null
                        ? Long.toString(Math.max(1L, result.get("cooking_time").asInt())) : "200");
                return sb.toString();
            }
            default -> {
                return null;
            }
        }
    }

    private static List<String> tdStrings(TdValue v) {
        if (!(v instanceof TdTable t)) {
            return List.of();
        }
        return t.elements().stream().map(TdValue::asString).toList();
    }

    private static java.util.Map<Character, String> tdKeyItems(TdValue v) {
        java.util.Map<Character, String> out = new HashMap<>();
        if (!(v instanceof TdTable keys)) {
            return out;
        }
        for (TdValue item : keys.elements()) {
            if (!(item instanceof TdTable kv)) {
                continue;
            }
            String k = kv.get("k") != null ? kv.get("k").asString() : "";
            TdValue vv = kv.get("v");
            if (k.length() == 1 && vv instanceof TdTable vt && vt.get("item") != null) {
                out.put(k.charAt(0), vt.get("item").asString());
            }
        }
        return out;
    }

    private static String itemKeyOf(TdTable t) {
        String id = t.get("item") != null ? t.get("item").asString() : "";
        String count = t.get("count") != null ? t.get("count").toString() : "1";
        return id + "x" + count;
    }

    private static String ingredientKey(Ingredient ingredient) {
        ItemStack[] items = ingredient.getItems();
        return items.length == 0 ? " " : itemKey(items[0]);
    }

    private static String itemKey(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString() + "x" + stack.getCount();
    }

    /** Builds LootTables from staged td entries (chest schema, single pool, constant rolls). */
    private void buildLootTables() {
        for (DatapackEntry e : byKind.get(EntryKind.LOOT_TABLE)) {
            int pools = buildLootTable(e);
            if (pools == 0) {
                DatapackRuntime.LOGGER.warn("{} skip loot_table {} (unsupported or malformed)", MARKER, e.id());
                continue;
            }
            DatapackRuntime.LOGGER.info("{} loot built {} (pools={}, LootDataManager merge deferred)", MARKER, e.id(), pools);
        }
    }

    /** Returns the number of pools built (0 = malformed/unsupported). */
    private static int buildLootTable(DatapackEntry e) {
        TdTable p = e.payload();
        if (!"minecraft:chest".equals(p.get("type") != null ? p.get("type").asString() : "")) {
            return 0;
        }
        TdValue poolsValue = p.get("pools");
        if (!(poolsValue instanceof TdTable pools)) {
            return 0;
        }
        LootTable.Builder tableBuilder = LootTable.lootTable();
        int built = 0;
        for (TdValue poolItem : pools.elements()) {
            if (!(poolItem instanceof TdTable pool)) {
                continue;
            }
            LootPool.Builder poolBuilder = LootPool.lootPool();
            poolBuilder.setRolls(ConstantValue.exactly(pool.get("rolls") != null
                    ? (float) Math.max(1L, pool.get("rolls").asInt()) : 1.0f));
            TdValue entriesValue = pool.get("entries");
            if (entriesValue instanceof TdTable entries) {
                for (TdValue entryItem : entries.elements()) {
                    if (!(entryItem instanceof TdTable entry)) {
                        continue;
                    }
                    Item item = itemById(entry.get("item") != null ? entry.get("item").asString() : "");
                    if (item == null || item == Items.AIR) {
                        continue;
                    }
                    int weight = (int) (entry.get("weight") != null
                            ? Math.max(1L, entry.get("weight").asInt()) : 1L);
                    poolBuilder.add(LootItem.lootTableItem(item).setWeight(weight));
                }
            }
            tableBuilder.withPool(poolBuilder);
            built++;
        }
        return built;
    }
    private void registerStaged() {
        for (Map.Entry<EntryKind, List<DatapackEntry>> e : byKind.entrySet()) {
            if (e.getKey() == EntryKind.LOOT_TABLE || e.getKey() == EntryKind.WORLDGEN
                    || e.getKey() == EntryKind.STRUCTURE) {
                for (DatapackEntry entry : e.getValue()) {
                    DatapackRuntime.LOGGER.info("{} staged {} ({}, vanilla injection deferred)",
                            MARKER, entry.id(), e.getKey().dir());
                }
            }
        }
    }

    /** tie FUNCTION entries: resolve the binding and call it (0-arg seed contract). */
    private void registerTie() {
        for (Map.Entry<Path, Datapack> e : packsByDir.entrySet()) {
            TieLogicBundle bundle;
            try {
                bundle = TieLogicLoader.load(e.getValue(), e.getKey());
            } catch (IllegalArgumentException ex) {
                DatapackRuntime.LOGGER.warn("{} skip tie libs of {}: {}", MARKER, e.getValue().name(), ex.getMessage());
                continue;
            }
            tieBundles.add(bundle);
            for (DatapackEntry fe : e.getValue().entries().values()) {
                if (fe.kind() == EntryKind.FUNCTION) {
                    try {
                        long result = bundle.resolve(fe).invoke0();
                        functionCalls.put(fe.id(), result);
                    } catch (Throwable t) {
                        DatapackRuntime.LOGGER.warn("{} tie call failed {}: {}", MARKER, fe.id(), t.toString());
                    }
                }
            }
        }
    }

    private static List<String> stringList(TdValue v) {
        if (!(v instanceof TdTable t)) {
            return List.of();
        }
        return t.elements().stream().map(TdValue::asString).toList();
    }

    private final Map<String, Long> functionCalls = new LinkedHashMap<>();

    /** function entry id → call result (deterministic detail marker source). */
    public Map<String, Long> functionCalls() {
        return functionCalls;
    }

    @Override
    public void close() {
        for (TieLogicBundle bundle : tieBundles) {
            try {
                bundle.close();
            } catch (Throwable ignored) {
                // best-effort native handle release
            }
        }
        tieBundles.clear();
        packs.clear();
        packsByDir.clear();
        byKind.clear();
        tagValues.clear();
        langMap.clear();
        recipes.clear();
        functionCalls.clear();
    }
}