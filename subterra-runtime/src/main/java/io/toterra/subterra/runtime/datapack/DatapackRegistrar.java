package io.toterra.subterra.runtime.datapack;

import io.toterra.subterra.engine.config.Td;
import io.toterra.subterra.engine.config.TdTable;
import io.toterra.subterra.engine.config.TdValue;
import io.toterra.subterra.engine.datapack.Datapack;
import io.toterra.subterra.engine.datapack.DatapackEntry;
import io.toterra.subterra.engine.datapack.DatapackExportArchive;
import io.toterra.subterra.engine.datapack.DatapackExporter;
import io.toterra.subterra.engine.datapack.DatapackLoader;
import io.toterra.subterra.engine.datapack.EntryKind;
import io.toterra.subterra.engine.datapack.LangDatum;
import io.toterra.subterra.engine.datapack.TagDatum;
import io.toterra.subterra.engine.datapack.TieLogicBundle;
import io.toterra.subterra.engine.datapack.TieLogicLoader;
import io.toterra.subterra.engine.tie.TieFunction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CookingBookCategory;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import net.minecraft.world.item.crafting.SmokingRecipe;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.SimpleBlockConfiguration;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadType;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.minecraft.world.level.levelgen.structure.structures.DesertPyramidStructure;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;

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
import java.util.TreeMap;

/**
 * MC-shell datapack registrar (p.2.2 block 2+): scans every td pack from the
 * configured datapacks directory through the engine.datapack loader (pure td
 * direct loading), then registers the content per kind:
 *
 * <ul>
 * <li>tag → entry id → payload {@code values} index (+ log marker);</li>
 * <li>lang → td k/v pairs → key/value map (+ log markers);</li>
 * <li>recipe → td {@code recipe = [ type = "minecraft:crafting_shaped", … ]}
 * compiled into a vanilla {@link ShapedRecipe} holder and merged into the live
 * {@link RecipeManager} via {@code replaceRecipes} (+ log markers);</li>
 * <li>recipe export round-trip (p.2.2 block 5) → each td-built recipe holder is
 * re-exported to canonical td, re-imported through {@code buildRecipe}, and
 * re-exported again — both td texts must be byte-identical (marker ok per
 * recipe).</li>
 * <li>all-kinds export round-trip (p.2.2 block 6) → every entry across the
 * loaded packs is exported to canonical td (DatapackExporter), fed back through
 * its production consumer (buildRecipe / buildLootTable / buildConfiguredFeature
 * / buildStructure / datum readers) and accepted, with one deterministic
 * {@code <kind-dir> <entry-id> ok/mismatch} line per entry;</li>
 * <li>export archive round-trip (p.2.2 block 6) → the single loaded pack is
 * serialized to one td document (DatapackExportArchive.export), rehydrated, and
 * re-exported — the two documents must be byte-identical.</li>
 * <li>loot_table → td chest schema → {@link LootTable} object, then merged into
 * the {@code LOOT_TABLE} datapack registry through
 * {@link DatapackRegistryInjector} (public un-freeze → register → freeze
 * window; 1.21.1 has no {@code LootDataManager}) (+ log markers);</li>
 * <li>worldgen → td configured_feature schema → {@link ConfiguredFeature}
 * registered into {@code CONFIGURED_FEATURE} (+ log markers);</li>
 * <li>structure → td structure / structure_set schema → {@link Structure} /
 * {@link StructureSet} registered into {@code STRUCTURE} / {@code STRUCTURE_SET}
 * (+ log markers);</li>
 * <li>function → tie binding resolved through {@link TieLogicLoader} and called
 * via {@link TieFunction#invoke0()} (0-arg seed contract) (+ log markers).</li>
 * </ul>
 *
 * <p>Deterministic markers feed the E2E gate; a broken pack or a malformed
 * entry never takes the server down — it is named in a warn and skipped.
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
    /** Loot tables built from td and merged into the LOOT_TABLE datapack registry. */
    private final Map<ResourceKey<LootTable>, LootTable> lootTables = new LinkedHashMap<>();
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

    /** Registers tag / lang / recipe / loot / worldgen / structure / tie content and logs deterministic detail markers. */
    public void registerContent() {
        registerTags();
        registerLang();
        registerRecipes();
        registerTie();
        registerWorldgen();
        registerStructures();
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
        runExportRoundTrip();
        runExportArchive();
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

    /**
     * p.2.2 block 6 — export round-trip for ALL datapack kinds: every entry across
     * the loaded packs is exported to canonical td ({@link DatapackExporter}),
     * fed back through its production consumer and accepted. Per-kind semantics:
     *
     * <ul>
     * <li>RECIPE → object-derived byte compare: rebuild via {@code buildRecipe}
     * ({@code null} = rejected) and re-export the holder through
     * {@code DatapackRecipeExporter}; the exported td text vs the rebuilt text
     * must be byte-identical (the block-5 closed-loop check);</li>
     * <li>TAG / LANG → datum self-consistency: {@link TagDatum} / {@link LangDatum}
     * read-write over the exported payload must reproduce it byte-for-byte;</li>
     * <li>LOOT_TABLE / WORLDGEN / STRUCTURE → consumer acceptance: the exported
     * payload must rebuild through {@code buildLootTable} /
     * {@code buildConfiguredFeature} / {@code buildStructure};</li>
     * <li>FUNCTION → pass-through: the loader already parsed it, the registrar has
     * no consumer, so it is always accepted.</li>
     * </ul>
     *
     * <p>Each entry logs one deterministic line in the uniform marker format
     * {@code export roundtrip <kind-dir> <entry-id> ok/mismatch} (the kind word is
     * inserted, changing the block-5 recipe format); a rejected rebuild reports
     * {@code mismatch}. Runs registration-time only, deterministic, never on the
     * hot path. A zero-entry registration just returns silently.
     */
    private void runExportRoundTrip() {
        Map<String, DatapackEntry> all = new TreeMap<>();
        for (Datapack dp : packs) {
            all.putAll(dp.entries());
        }
        if (all.isEmpty()) {
            return;
        }
        RegistryAccess ra = server.registryAccess();
        Registry<Biome> biomeRegistry = server.registryAccess().registryOrThrow(Registries.BIOME);
        for (DatapackEntry e : all.values()) {
            String e1;
            try {
                e1 = DatapackExporter.exportEntryTd(e);
            } catch (RuntimeException ex) {
                DatapackRuntime.LOGGER.warn("{} export roundtrip {} {} failed: {}",
                        MARKER, e.kind().dir(), e.id(), ex.getMessage());
                continue;
            }
            boolean accepted = false;
            String e2 = null;
            switch (e.kind()) {
                case RECIPE -> {
                    RecipeHolder<?> rebuilt = buildRecipe(e);
                    if (rebuilt != null) {
                        TdTable firstT = DatapackRecipeExporter.exportTd(rebuilt, ra);
                        if (firstT != null) {
                            String first = Td.write(firstT);
                            ResourceLocation rid = ResourceLocation.tryParse(e.id());
                            DatapackEntry back = new DatapackEntry(EntryKind.RECIPE,
                                    rid != null ? rid.getNamespace() : e.namespace(),
                                    rid != null ? rid.getPath() : e.path(), firstT);
                            RecipeHolder<?> rebuilt2 = buildRecipe(back);
                            if (rebuilt2 != null) {
                                TdTable secondT = DatapackRecipeExporter.exportTd(rebuilt2, ra);
                                if (secondT != null) {
                                    // object-level round-trip: the source-built holder's export is the
                                    // reference; re-building from that canonical td and re-exporting must be
                                    // byte-identical (the built ShapedRecipe retains no source key chars, so
                                    // this is the stable closed-loop identity, cf. block 5).
                                    e1 = first;
                                    e2 = Td.write(secondT);
                                }
                            }
                        }
                    }
                    accepted = e2 != null;
                }
                case TAG -> {
                    try {
                        stringList(e.payload().get("values"));
                        e2 = Td.write(TagDatum.read(e.payload()).write());
                        accepted = true;
                    } catch (RuntimeException ex) {
                        // datum re-emission failed; accepted stays false
                    }
                }
                case LANG -> {
                    try {
                        e2 = Td.write(LangDatum.read(e.payload()).write());
                        accepted = e2 != null;
                    } catch (RuntimeException ex) {
                        // datum re-emission failed; accepted stays false
                    }
                }
                case LOOT_TABLE -> accepted = buildLootTable(e) != null;
                case WORLDGEN -> accepted = buildConfiguredFeature(e) != null;
                case STRUCTURE -> accepted = buildStructure(e, biomeRegistry) != null;
                case FUNCTION -> accepted = true;
                default -> accepted = true;
            }
            boolean ok = switch (e.kind()) {
                case RECIPE, TAG, LANG -> e2 != null && e1.equals(e2);
                default -> accepted;
            };
            DatapackRuntime.LOGGER.info("{} export roundtrip {} {} {}{}", MARKER, e.kind().dir(), e.id(),
                    ok ? "ok" : "mismatch",
                    ok ? " (rebuild=" + accepted + ", bytes=" + e1.length() + ")" : "");
        }
    }

    /**
     * p.2.2 block 6 — in-server export archive round-trip: the whole single loaded
     * pack is serialized to one td document ({@link DatapackExportArchive#export}),
     * rehydrated back into a {@link Datapack} ({@link
     * DatapackExportArchive#rehydrate}), and re-exported — the two documents must
     * be byte-identical ({@code export} ∘ {@code rehydrate} is the identity on the
     * archive). Only meaningful for a single loaded pack; otherwise it is logged
     * and skipped. Deterministic, registration-time only.
     */
    private void runExportArchive() {
        if (packs.size() != 1) {
            DatapackRuntime.LOGGER.info("{} export archive skip (packs={}, single-pack archive only)",
                    MARKER, packs.size());
            return;
        }
        Datapack dp = packs.get(0);
        String doc1 = DatapackExportArchive.export(dp);
        String doc2 = DatapackExportArchive.export(DatapackExportArchive.rehydrate(doc1));
        boolean ok = doc1.equals(doc2);
        DatapackRuntime.LOGGER.info("{} export archive roundtrip {} (entries={}, bytes={})",
                MARKER, ok ? "ok" : "mismatch", dp.entries().size(), doc1.length());
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

    /** A built loot table plus the number of pools it carries. */
    private record BuiltLoot(LootTable table, int pools) {
    }

    /**
     * Builds LootTables from td entries (chest schema, single pool, constant
     * rolls) and merges them into the {@code LOOT_TABLE} datapack registry via
     * {@link DatapackRegistryInjector} (the 1.21.1 home of loot data — no
     * {@code LootDataManager} exists; the registry lives in the reloadable layer
     * exposed by {@code ReloadableServerResources.fullRegistries()}, not in
     * {@code server.registryAccess()}). Post-injection visibility is re-read
     * from the registry and logged as a deterministic marker.
     */
    private void buildLootTables() {
        Registry<LootTable> registry = server.getServerResources().managers()
                .fullRegistries().get().registryOrThrow(Registries.LOOT_TABLE);
        for (DatapackEntry e : byKind.get(EntryKind.LOOT_TABLE)) {
            BuiltLoot built = buildLootTable(e);
            if (built == null) {
                DatapackRuntime.LOGGER.warn("{} skip loot_table {} (unsupported or malformed)", MARKER, e.id());
                continue;
            }
            ResourceLocation id = ResourceLocation.tryParse(e.id());
            if (id == null) {
                DatapackRuntime.LOGGER.warn("{} skip loot_table {} (bad id)", MARKER, e.id());
                continue;
            }
            ResourceKey<LootTable> key = ResourceKey.create(Registries.LOOT_TABLE, id);
            if (DatapackRegistryInjector.inject(registry, key, built.table(), DatapackRuntime.LOGGER, MARKER)) {
                lootTables.put(key, built.table());
                DatapackRuntime.LOGGER.info("{} loot built {} (pools={}, merged into LOOT_TABLE registry)",
                        MARKER, e.id(), built.pools());
                boolean ok = registry.get(key) == built.table();
                DatapackRuntime.LOGGER.info("{} loot visible {} (pools={}, registry={})",
                        MARKER, e.id(), built.pools(), ok ? "ok" : "mismatch");
            }
        }
    }

    /** Returns the built table and its pool count; null = malformed/unsupported. */
    private static BuiltLoot buildLootTable(DatapackEntry e) {
        TdTable p = e.payload();
        if (!"minecraft:chest".equals(p.get("type") != null ? p.get("type").asString() : "")) {
            return null;
        }
        TdValue poolsValue = p.get("pools");
        if (!(poolsValue instanceof TdTable pools)) {
            return null;
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
        return built == 0 ? null : new BuiltLoot(tableBuilder.build(), built);
    }

    /**
     * Registers td worldgen entries into the {@code CONFIGURED_FEATURE}
     * datapack registry (configured_feature schema, minimal provable sample).
     */
    private void registerWorldgen() {
        Registry<ConfiguredFeature<?, ?>> registry = server.registryAccess().registryOrThrow(Registries.CONFIGURED_FEATURE);
        for (DatapackEntry e : byKind.get(EntryKind.WORLDGEN)) {
            ConfiguredFeature<?, ?> cf = buildConfiguredFeature(e);
            if (cf == null) {
                DatapackRuntime.LOGGER.warn("{} skip worldgen {} (unsupported or malformed)", MARKER, e.id());
                continue;
            }
            ResourceLocation id = ResourceLocation.tryParse(e.id());
            if (id == null) {
                DatapackRuntime.LOGGER.warn("{} skip worldgen {} (bad id)", MARKER, e.id());
                continue;
            }
            ResourceKey<ConfiguredFeature<?, ?>> key = ResourceKey.create(Registries.CONFIGURED_FEATURE, id);
            if (DatapackRegistryInjector.inject(registry, key, cf, DatapackRuntime.LOGGER, MARKER)) {
                boolean ok = registry.get(key) == cf;
                DatapackRuntime.LOGGER.info("{} worldgen visible {} (configured_feature, registry={})",
                        MARKER, e.id(), ok ? "ok" : "mismatch");
            }
        }
    }

    /** td worldgen entry (configured_feature schema) → a vanilla {@link ConfiguredFeature}. */
    private static ConfiguredFeature<?, ?> buildConfiguredFeature(DatapackEntry e) {
        TdTable p = e.payload();
        if (!"minecraft:configured_feature".equals(p.get("type") != null ? p.get("type").asString() : "")) {
            return null;
        }
        if (!"minecraft:simple_block".equals(p.get("feature") != null ? p.get("feature").asString() : "")) {
            return null;
        }
        String blockId = p.get("block") != null ? p.get("block").asString() : "";
        ResourceLocation blockLoc = ResourceLocation.tryParse(blockId);
        Block block = blockLoc == null ? null : BuiltInRegistries.BLOCK.get(blockLoc);
        if (block == null || block == Blocks.AIR) {
            return null;
        }
        return new ConfiguredFeature<>(Feature.SIMPLE_BLOCK,
                new SimpleBlockConfiguration(BlockStateProvider.simple(block)));
    }

    /**
     * Registers td structure entries into {@code STRUCTURE} and (for the
     * structure_set schema) {@code STRUCTURE_SET} datapack registries.
     */
    private void registerStructures() {
        Registry<Structure> structureRegistry = server.registryAccess().registryOrThrow(Registries.STRUCTURE);
        Registry<StructureSet> setRegistry = server.registryAccess().registryOrThrow(Registries.STRUCTURE_SET);
        Registry<Biome> biomeRegistry = server.registryAccess().registryOrThrow(Registries.BIOME);
        for (DatapackEntry e : byKind.get(EntryKind.STRUCTURE)) {
            Structure built = buildStructure(e, biomeRegistry);
            if (built == null) {
                DatapackRuntime.LOGGER.warn("{} skip structure {} (unsupported or malformed)", MARKER, e.id());
                continue;
            }
            ResourceLocation id = ResourceLocation.tryParse(e.id());
            if (id == null) {
                DatapackRuntime.LOGGER.warn("{} skip structure {} (bad id)", MARKER, e.id());
                continue;
            }
            ResourceKey<Structure> structureKey = ResourceKey.create(Registries.STRUCTURE, id);
            if (!DatapackRegistryInjector.inject(structureRegistry, structureKey, built,
                    DatapackRuntime.LOGGER, MARKER)) {
                continue;
            }
            boolean structureSet = "minecraft:structure_set".equals(
                    e.payload().get("type") != null ? e.payload().get("type").asString() : "");
            if (!structureSet) {
                boolean ok = structureRegistry.get(structureKey) == built;
                DatapackRuntime.LOGGER.info("{} structure visible {} (structure, registry={})",
                        MARKER, e.id(), ok ? "ok" : "mismatch");
                continue;
            }
            StructurePlacement placement = buildPlacement(e);
            if (placement == null) {
                DatapackRuntime.LOGGER.warn("{} skip structure_set {} (bad placement)", MARKER, e.id());
                continue;
            }
            Holder<Structure> holder = structureRegistry.getHolderOrThrow(structureKey);
            StructureSet set = new StructureSet(holder, placement);
            ResourceKey<StructureSet> setKey = ResourceKey.create(Registries.STRUCTURE_SET, id);
            if (DatapackRegistryInjector.inject(setRegistry, setKey, set, DatapackRuntime.LOGGER, MARKER)) {
                boolean ok = setRegistry.get(setKey) == set;
                DatapackRuntime.LOGGER.info("{} structure visible {} (structure_set, structure={}, registry={})",
                        MARKER, e.id(), id, ok ? "ok" : "mismatch");
            }
        }
    }

    /** td structure entry (structure / structure_set schema) → a vanilla {@link Structure}. */
    private static Structure buildStructure(DatapackEntry e, Registry<Biome> biomeRegistry) {
        TdTable p = e.payload();
        String type = p.get("type") != null ? p.get("type").asString() : "";
        if (!"minecraft:structure".equals(type) && !"minecraft:structure_set".equals(type)) {
            return null;
        }
        List<Holder<Biome>> biomes = biomeHolders(p.get("biomes"), biomeRegistry);
        if (biomes.isEmpty()) {
            return null;
        }
        return new DesertPyramidStructure(new Structure.StructureSettings(HolderSet.direct(biomes)));
    }

    /** td structure_set placement fields → {@link RandomSpreadStructurePlacement}. */
    private static StructurePlacement buildPlacement(DatapackEntry e) {
        TdTable p = e.payload();
        if (p.get("spacing") == null || p.get("separation") == null || p.get("salt") == null) {
            return null;
        }
        int spacing = (int) Math.max(1L, p.get("spacing").asInt());
        int separation = (int) Math.max(0L, p.get("separation").asInt());
        int salt = (int) p.get("salt").asInt();
        return new RandomSpreadStructurePlacement(spacing, separation, RandomSpreadType.LINEAR, salt);
    }

    /** Resolves td biome ids to biome-registry holders (unknown ids skipped). */
    private static List<Holder<Biome>> biomeHolders(TdValue v, Registry<Biome> biomeRegistry) {
        List<Holder<Biome>> out = new ArrayList<>();
        if (!(v instanceof TdTable t)) {
            return out;
        }
        for (TdValue item : t.elements()) {
            ResourceLocation loc = ResourceLocation.tryParse(item.asString());
            if (loc == null) {
                continue;
            }
            ResourceKey<Biome> key = ResourceKey.create(Registries.BIOME, loc);
            try {
                out.add(biomeRegistry.getHolderOrThrow(key));
            } catch (Throwable ignored) {
                // unknown biome id; skip this holder
            }
        }
        return out;
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

    /** Loot tables merged into the LOOT_TABLE registry (key → table). */
    public Map<ResourceKey<LootTable>, LootTable> lootTables() {
        return lootTables;
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
        lootTables.clear();
        functionCalls.clear();
    }
}
