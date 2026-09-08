// Ported from itemban (github.com/lnsanes/itemban), Apache-2.0 (c) lnsanes;
// adapted to Subterra's NeoForge 1.21.1 internal capability.
package io.toterra.subterra.runtime.optim.server.item_control.shell;

import io.toterra.subterra.engine.optim.server.item_control.ItemControlRule;
import io.toterra.subterra.engine.optim.server.item_control.ItemControlRuleSet;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.toterra.subterra.engine.config.Td;
import io.toterra.subterra.engine.config.TdTable;
import io.toterra.subterra.engine.config.TdValue;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Item/block blacklist configuration, stored as td
 * ({@code config/subterra/item_control.td}) via engine.config. Unknown or
 * invalid fields fall back to defaults so a broken config file can never take
 * the capability down.
 *
 * <p>Schema:
 * <pre>{@code
 * [
 *   item_control = [
 *     public_announce = true,       // announce violations in chat.
 *     auto_ban = false,             // auto-ban/kick on confirmed violation.
 *     detect_dropped_items = true,  // periodically scan nearby dropped items.
 *     detect_world_blocks = true,   // periodically scan banned blocks nearby.
 *     exclude_log = [ "minecraft:diamond" ],   // ids skipped in audit log.
 *     blacklist = [                 // item blacklist; optional nbt = SNBT.
 *       [ id = "minecraft:diamond" ],
 *       [ id = "minecraft:enchanted_book", nbt = "{...SNBT...}" ],
 *     ],
 *     block_blacklist = [ [ id = "minecraft:bedrock" ] ],
 *   ],
 * ]
 * }</pre>
 *
 * <p>Runtime toggles and rules (changed by {@code /itemban} commands) are kept
 * in memory and re-serialized back to the same td file on change. NBT rules
 * are parsed once at (re)index time (never per event) into the immutable
 * {@link ItemControlRule} core; matching runs a single linear pass.
 */
public final class ItemControlConfig {

    private static final boolean DEFAULT_PUBLIC_ANNOUNCE = true;
    private static final boolean DEFAULT_AUTO_BAN = false;
    private static final boolean DEFAULT_DETECT_DROPPED_ITEMS = true;
    private static final boolean DEFAULT_DETECT_WORLD_BLOCKS = true;

    private static Path configFile;
    private static volatile boolean useTdFile = true;

    private static volatile boolean publicAnnounce = DEFAULT_PUBLIC_ANNOUNCE;
    private static volatile boolean autoBanOnViolation = DEFAULT_AUTO_BAN;
    private static volatile boolean detectDroppedItems = DEFAULT_DETECT_DROPPED_ITEMS;
    private static volatile boolean detectWorldBlocks = DEFAULT_DETECT_WORLD_BLOCKS;
    private static Set<String> excludeFromLog = new LinkedHashSet<>();

    private static List<ItemControlRule> blacklistRules = new ArrayList<>();
    private static List<ItemControlRule> blockBlacklistRules = new ArrayList<>();

    // Rebuilt on load / command change. Volatile: scanned on a background thread.
    private static volatile ItemControlRuleSet itemRules = ItemControlRuleSet.empty();
    private static volatile ItemControlRuleSet blockRules = ItemControlRuleSet.empty();
    private static volatile Set<String> blockIdsNeedingNbt = Set.of();
    private static volatile Set<Block> trackedBlocks = Set.of();

    private ItemControlConfig() {
    }

    /** Loads the td config under {@code gameDir}; falls back to defaults. */
    public static void load(Path gameDir) {
        configFile = gameDir.resolve("config/subterra/item_control.td");
        useTdFile = Files.isRegularFile(configFile);
        try {
            if (useTdFile) {
                String text = Files.readString(configFile, StandardCharsets.UTF_8);
                if (!text.isBlank()) {
                    TdTable root = Td.parse(text);
                    TdValue ic = root.get("item_control");
                    if (ic instanceof TdTable table) {
                        apply(table);
                    }
                }
            }
        } catch (IllegalArgumentException | IOException e) {
            ItemControl.LOGGER.warn("[item_control] config {} ignored ({}); using defaults", configFile, e);
            defaults();
        }
        rebuildIndexes();
    }

    /** Reloads the config from the td file (keeping loaded state if absent). */
    public static void reload() {
        if (!useTdFile || configFile == null) {
            return; // nothing to reload
        }
        try {
            String text = Files.readString(configFile, StandardCharsets.UTF_8);
            if (!text.isBlank()) {
                TdTable root = Td.parse(text);
                TdValue ic = root.get("item_control");
                if (ic instanceof TdTable table) {
                    apply(table);
                }
            }
        } catch (IllegalArgumentException | IOException e) {
            ItemControl.LOGGER.warn("[item_control] config {} ignored ({}); keeping current state", configFile, e);
        }
        rebuildIndexes();
    }

    private static void defaults() {
        publicAnnounce = DEFAULT_PUBLIC_ANNOUNCE;
        autoBanOnViolation = DEFAULT_AUTO_BAN;
        detectDroppedItems = DEFAULT_DETECT_DROPPED_ITEMS;
        detectWorldBlocks = DEFAULT_DETECT_WORLD_BLOCKS;
        excludeFromLog = new LinkedHashSet<>();
        blacklistRules = new ArrayList<>();
        blockBlacklistRules = new ArrayList<>();
    }

    private static void apply(TdTable table) {
        publicAnnounce = bool(table.get("public_announce"), publicAnnounce);
        autoBanOnViolation = bool(table.get("auto_ban"), autoBanOnViolation);
        detectDroppedItems = bool(table.get("detect_dropped_items"), detectDroppedItems);
        detectWorldBlocks = bool(table.get("detect_world_blocks"), detectWorldBlocks);
        excludeFromLog = new LinkedHashSet<>(strings(table.get("exclude_log")));
        blacklistRules = parseRules(table.get("blacklist"), false);
        blockBlacklistRules = parseRules(table.get("block_blacklist"), true);
    }

    private static boolean bool(TdValue v, boolean fallback) {
        return v != null ? v.asBool() : fallback;
    }

    /** Parses a {@code [ [ id = ..., nbt = ... ], ... ]} table into rules. */
    private static List<ItemControlRule> parseRules(TdValue v, boolean nbtAware) {
        List<ItemControlRule> rules = new ArrayList<>();
        if (!(v instanceof TdTable table)) {
            return rules;
        }
        for (TdValue element : table.elements()) {
            if (!(element instanceof TdTable entry)) {
                continue;
            }
            String id = entry.get("id") != null ? entry.get("id").asString() : "";
            if (id.isBlank()) {
                continue;
            }
            String nbt = entry.get("nbt") != null ? entry.get("nbt").asString() : null;
            if (nbtAware) {
                // blocks expose a real CompoundTag (block-entity NBT), so deep NBT
                // matching is meaningful here.
                rules.add(ItemControlRule.of(id, nbt, new NbtContainsMatcher(nbt)));
            } else {
                // items in 1.21.1 carry the DataComponent system, not a CompoundTag;
                // match item blacklist rules by id only (the configured nbt string is
                // kept for identity/list display but not enforced).
                rules.add(ItemControlRule.of(id, nbt, null));
            }
        }
        return rules;
    }

    private static List<String> strings(TdValue v) {
        List<String> out = new ArrayList<>();
        if (v instanceof TdTable table) {
            for (TdValue element : table.elements()) {
                out.add(element.asString());
            }
        }
        return out;
    }

    /** Re-derives immutable snapshots after any config change. */
    private static void rebuildIndexes() {
        itemRules = ItemControlRuleSet.of(blacklistRules);
        blockRules = ItemControlRuleSet.of(blockBlacklistRules);

        Set<String> nbtIds = new HashSet<>();
        Set<Block> blocks = new HashSet<>();
        for (ItemControlRule rule : blockBlacklistRules) {
            if (rule.hasNbtConstraint()) {
                nbtIds.add(rule.id());
            }
            try {
                ResourceLocation key = ResourceLocation.tryParse(rule.id());
                if (key == null) {
                    continue;
                }
                Block block = BuiltInRegistries.BLOCK.get(key);
                if (block != null && block != Blocks.AIR) {
                    blocks.add(block);
                }
            } catch (Exception ignored) {
                // unknown block id: not tracked
            }
        }
        blockIdsNeedingNbt = Set.copyOf(nbtIds);
        trackedBlocks = Set.copyOf(blocks);
    }

    // ---------- runtime accessors ----------

    public static boolean publicAnnounce() {
        return publicAnnounce;
    }

    public static boolean autoBanOnViolation() {
        return autoBanOnViolation;
    }

    public static boolean detectDroppedItems() {
        return detectDroppedItems;
    }

    public static boolean detectWorldBlocks() {
        return detectWorldBlocks;
    }

    public static boolean isExcludedFromLog(String id) {
        return id != null && excludeFromLog.contains(id);
    }

    public static Set<String> getExcludeFromLog() {
        return new LinkedHashSet<>(excludeFromLog);
    }

    /** Pre-compiled, deduplicated item blacklist (pure core), immutable. */
    public static ItemControlRuleSet getBlacklistRules() {
        return itemRules;
    }

    public static ItemControlRuleSet getBlockBlacklistRules() {
        return blockRules;
    }

    public static boolean hasItemBlacklist() {
        return !itemRules.isEmpty();
    }

    public static boolean hasBlockBlacklist() {
        return !blockRules.isEmpty();
    }

    public static boolean isTrackedBlock(Block block) {
        return !trackedBlocks.isEmpty() && trackedBlocks.contains(block);
    }

    public static boolean blockIdNeedsNbt(String blockId) {
        return blockIdsNeedingNbt.contains(blockId);
    }

    public static boolean isBlacklisted(String itemId, CompoundTag nbt) {
        return itemRules.contains(itemId, nbt);
    }

    public static boolean isBlockBlacklisted(String blockId, CompoundTag nbt) {
        return blockRules.contains(blockId, nbt);
    }

    public static String summary() {
        return "items=" + itemRules.size() + ", blocks=" + blockRules.size()
                + ", announce=" + publicAnnounce + ", autoban=" + autoBanOnViolation
                + ", dropdetect=" + detectDroppedItems + ", worldblocks=" + detectWorldBlocks;
    }

    // ---------- command-driven mutation ----------

    public static void setPublicAnnounce(boolean value) {
        publicAnnounce = value;
        persist();
    }

    public static void setAutoBanOnViolation(boolean value) {
        autoBanOnViolation = value;
        persist();
    }

    public static void setDetectDroppedItems(boolean value) {
        detectDroppedItems = value;
        persist();
    }

    public static void setDetectWorldBlocks(boolean value) {
        detectWorldBlocks = value;
        persist();
    }

    public static void addExcludeFromLog(String itemId) {
        excludeFromLog.add(itemId);
        persist();
    }

    public static void removeExcludeFromLog(String itemId) {
        excludeFromLog.remove(itemId);
        persist();
    }

    public static void clearRules() {
        blacklistRules = new ArrayList<>();
        blockBlacklistRules = new ArrayList<>();
        rebuildIndexes();
    }

    public static void addToBlacklist(String itemId) {
        addToBlacklist(itemId, null);
    }

    public static void addToBlacklist(String itemId, String nbtString) {
        // items match by id in 1.21.1 (see parseRules); configured nbt string is
        // retained for identity/list display.
        blacklistRules.add(ItemControlRule.of(itemId, nbtString, null));
        rebuildIndexes();
        persist();
    }

    public static void removeFromBlacklist(String itemId) {
        removeFromBlacklist(itemId, null);
    }

    public static void removeFromBlacklist(String itemId, String nbtString) {
        if (itemId == null) {
            return;
        }
        String nbt = normalizeNbt(nbtString);
        blacklistRules.removeIf(r -> nbt == null
                ? r.id().equals(itemId)
                : r.id().equals(itemId) && r.nbtSnbt() != null && r.nbtSnbt().equals(nbt));
        rebuildIndexes();
        persist();
    }

    public static void addToBlockBlacklist(String blockId) {
        addToBlockBlacklist(blockId, null);
    }

    public static void addToBlockBlacklist(String blockId, String nbtString) {
        blockBlacklistRules.add(ItemControlRule.of(blockId, nbtString, new NbtContainsMatcher(nbtString)));
        rebuildIndexes();
        persist();
    }

    public static void removeFromBlockBlacklist(String blockId) {
        removeFromBlockBlacklist(blockId, null);
    }

    public static void removeFromBlockBlacklist(String blockId, String nbtString) {
        if (blockId == null) {
            return;
        }
        String nbt = normalizeNbt(nbtString);
        blockBlacklistRules.removeIf(r -> nbt == null
                ? r.id().equals(blockId)
                : r.id().equals(blockId) && r.nbtSnbt() != null && r.nbtSnbt().equals(nbt));
        rebuildIndexes();
        persist();
    }

    private static String normalizeNbt(String nbt) {
        return nbt == null || nbt.trim().isEmpty() ? null : nbt.trim();
    }

    /** Re-serializes the current state back to the td file. */
    static void persist() {
        if (configFile == null) {
            return;
        }
        TdTable table = TdTable.builder()
                .put("public_announce", TdValue.of(publicAnnounce))
                .put("auto_ban", TdValue.of(autoBanOnViolation))
                .put("detect_dropped_items", TdValue.of(detectDroppedItems))
                .put("detect_world_blocks", TdValue.of(detectWorldBlocks))
                .put("exclude_log", listTable(excludeFromLog))
                .put("blacklist", ruleTable(blacklistRules))
                .put("block_blacklist", ruleTable(blockBlacklistRules))
                .build();
        TdTable root = TdTable.builder().put("item_control", table).build();
        try {
            if (configFile.getParent() != null) {
                Files.createDirectories(configFile.getParent());
            }
            Files.writeString(configFile, Td.write(root), StandardCharsets.UTF_8);
        } catch (IOException e) {
            ItemControl.LOGGER.warn("[item_control] failed to persist config {}: {}", configFile, e);
        }
    }

    private static TdTable listTable(Set<String> values) {
        TdTable.Builder b = TdTable.builder();
        for (String v : values) {
            b.element(TdValue.str(v));
        }
        return b.build();
    }

    private static TdTable ruleTable(List<ItemControlRule> rules) {
        TdTable.Builder b = TdTable.builder();
        for (ItemControlRule rule : rules) {
            TdTable.Builder entry = TdTable.builder().put("id", rule.id());
            if (rule.nbtSnbt() != null && !rule.nbtSnbt().isEmpty()) {
                entry.put("nbt", rule.nbtSnbt());
            }
            b.element(entry.build());
        }
        return b.build();
    }

    /**
     * MC-layer NBT matcher: parses the SNBT once and performs the subset
     * containment check. Thread-safe (read-only after construction).
     */
    private static final class NbtContainsMatcher implements ItemControlRule.NbtMatcher {
        private final CompoundTag parsed;

        NbtContainsMatcher(String nbtSnbt) {
            CompoundTag tag = null;
            if (nbtSnbt != null && !nbtSnbt.isBlank()) {
                try {
                    tag = TagParser.parseTag(nbtSnbt);
                } catch (CommandSyntaxException e) {
                    ItemControl.LOGGER.warn("[item_control] invalid NBT rule '{}': {}", nbtSnbt, e.getMessage());
                }
            }
            parsed = tag;
        }

        @Override
        public boolean matches(Object itemNbt) {
            if (!(itemNbt instanceof CompoundTag item)) {
                return false;
            }
            if (parsed == null || parsed.isEmpty()) {
                return true;
            }
            if (item.isEmpty()) {
                return false;
            }
            return nbtContains(item, parsed);
        }
    }

    /** True when {@code item} contains every key of {@code required} with a matching value. */
    private static boolean nbtContains(CompoundTag item, CompoundTag required) {
        for (String key : required.getAllKeys()) {
            if (!item.contains(key)) {
                return false;
            }
            var itemValue = item.get(key);
            var requiredValue = required.get(key);
            if (itemValue == null || requiredValue == null) {
                return false;
            }
            if (requiredValue instanceof CompoundTag requiredCompound) {
                if (!(itemValue instanceof CompoundTag itemCompound) || !nbtContains(itemCompound, requiredCompound)) {
                    return false;
                }
            } else if (requiredValue instanceof ListTag requiredList) {
                if (!(itemValue instanceof ListTag itemList)) {
                    return false;
                }
                for (var reqElement : requiredList) {
                    boolean found = false;
                    for (var itemElement : itemList) {
                        if (reqElement.equals(itemElement)
                                || (reqElement instanceof CompoundTag rc && itemElement instanceof CompoundTag ic
                                && nbtContains(ic, rc))) {
                            found = true;
                            break;
                        }
                    }
                    if (!found) {
                        return false;
                    }
                }
            } else {
                if (!itemEquals(itemValue, requiredValue)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean itemEquals(Object a, Object b) {
        return a.equals(b);
    }
}