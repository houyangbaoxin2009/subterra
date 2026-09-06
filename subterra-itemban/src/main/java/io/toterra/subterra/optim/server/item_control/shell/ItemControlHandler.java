// Ported from itemban (github.com/lnsanes/itemban), Apache-2.0 (c) lnsanes;
// adapted to Subterra's NeoForge 1.21.1 internal capability.
package io.toterra.subterra.optim.server.item_control.shell;

import io.toterra.subterra.optim.server.item_control.ItemControlRule;
import io.toterra.subterra.optim.server.item_control.ItemControlRuleSet;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/**
 * Event handlers for the itemban port (player inventory, dropped items,
 * item-frames and banned world blocks).
 *
 * <p>Performance notes (kept from upstream for low-end servers):
 * <ul>
 *   <li>all periodic scans run at most every {@code 12} ticks, and one kind of
 *       scan at a time per player;</li>
 *   <li>inventory scan every {@code 10} ticks, frame/block scan every {@code 12},
 *       dropped-item scan every {@code 5};</li>
 *   <li>the main thread only snapshots; blacklist matching happens on a
 *       background thread; deletion/announcement returns to the main thread;</li>
 *   <li>block scanning is skipped entirely when the block blacklist is empty;</li>
 *   <li>container scanning is event-driven (open event); dropped-item
 *       interception is event-driven while {@code dropdetect} is off;</li>
 *   <li>creative/OP players return early to avoid unnecessary checks.</li>
 * </ul>
 *
 * <p>Registers as a static subscriber on {@link net.neoforged.neoforge.common.NeoForge#EVENT_BUS}.
 */
public final class ItemControlHandler {

    private static final int INV_SCAN_INTERVAL = 10;
    private static final int ENV_SCAN_INTERVAL = 12;
    private static final int DROP_SCAN_INTERVAL = 5;

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static Path logDir = Paths.get("logs", "Subterra", "ItemControl");

    static {
        try {
            Files.createDirectories(logDir);
        } catch (IOException ignored) {
        }
    }

    private static boolean modsDetected = false;

    private static volatile ExecutorService scanExecutor = newScanExecutor();
    private static final ConcurrentHashMap<UUID, PlayerScanState> scanStates = new ConcurrentHashMap<>();

    private enum ScanKind { DROP, INV, ENV }

    private static final class PlayerScanState {
        volatile boolean busy;
        int lastDrop = Integer.MIN_VALUE / 4;
        int lastInv = Integer.MIN_VALUE / 4;
        int lastEnv = Integer.MIN_VALUE / 4;
    }

    private record DropSnapshot(int entityId, ItemStack stack, String loc) {}
    private record InvSnapshot(int slot, boolean carried, ItemStack stack) {}
    private record FrameSnapshot(int entityId, ItemStack stack, String loc) {}
    private record BlockSnapshot(BlockPos pos, String blockId, CompoundTag nbt) {}

    private ItemControlHandler() {
    }

    private static ExecutorService newScanExecutor() {
        return Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "Subterra-ItemControl-Scan");
            t.setDaemon(true);
            return t;
        });
    }

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        ItemControl.LOGGER.info("[item_control] server starting; blacklist loaded");
        scanStates.clear();
        if (scanExecutor == null || scanExecutor.isShutdown()) {
            scanExecutor = newScanExecutor();
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        scanStates.clear();
        ExecutorService executor = scanExecutor;
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) {
            return;
        }
        if (!(player instanceof ServerPlayer sp) || isOpOrCreative(sp)) {
            return;
        }
        if (!modsDetected) {
            detectLoadedMods();
        }
        scheduleNextScan(sp);
    }

    @SubscribeEvent
    public static void onItemEntitySpawn(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide || !(event.getEntity() instanceof ItemEntity itemEntity)) {
            return;
        }
        // With the dropped-item toggle on, interception is via range scan instead.
        if (ItemControlConfig.detectDroppedItems()) {
            return;
        }
        if (itemEntity.getOwner() instanceof Player owner && isOpOrCreative(owner)) {
            return;
        }
        ItemStack stack = itemEntity.getItem();
        if (isBlacklisted(stack)) {
            event.setCanceled(true); // immediate interception (only when dropdetect is off)
        }
    }

    @SubscribeEvent
    public static void onContainerOpen(PlayerContainerEvent.Open event) {
        if (event.getEntity().level().isClientSide) {
            return;
        }
        Player player = event.getEntity();
        if (isOpOrCreative(player)) {
            return;
        }

        // Clear banned items from the container first, recording them, then ban.
        // Skip ghost/pattern slots so config placeholder ItemStacks aren't
        // treated as real holdings.
        AbstractContainerMenu menu = event.getContainer();
        ItemStack firstViolation = ItemStack.EMPTY;
        boolean found = false;
        for (Slot slot : menu.slots) {
            if (isConfigGhostSlot(slot, player)) {
                continue;
            }
            ItemStack stack = slot.getItem();
            if (!stack.isEmpty() && isBlacklisted(stack)) {
                ItemStack removed = stack.copy();
                if (!found) {
                    firstViolation = removed.copy();
                    found = true;
                }
                slot.set(ItemStack.EMPTY); // delete first
                logObtained(player, removed, formatEntityPos(player), "container", false);
            }
        }
        if (found && player instanceof ServerPlayer serverPlayer) {
            menu.broadcastChanges();
            banAfterVerifiedClean(serverPlayer, firstViolation);
        }
    }

    /**
     * Creative filter slot, AE2 FakeSlot / pattern marker slot, etc.: the GUI
     * shows config placeholder glyphs the player cannot actually take out.
     * These slots must not be cleared, logged or counted as held.
     */
    private static boolean isConfigGhostSlot(Slot slot, Player player) {
        if (slot == null) {
            return true;
        }
        // Player's real inventory slots are always checked.
        if (player != null && slot.container == player.getInventory()) {
            return false;
        }
        try {
            if (!slot.mayPickup(player)) {
                return true;
            }
        } catch (Throwable ignored) {
            return true;
        }
        String className = slot.getClass().getName();
        String simpleName = slot.getClass().getSimpleName();
        String lower = (className + " " + simpleName).toLowerCase();
        return lower.contains("fakeslot")
                || lower.contains("ghost")
                || lower.contains("phantom")
                || lower.contains("filterslot")
                || lower.contains("configslot")
                || simpleName.equalsIgnoreCase("FakeSlot")
                || simpleName.equalsIgnoreCase("FilterSlot");
    }

    private static boolean isBlacklisted(ItemStack stack) {
        String id = stack.getItem().builtInRegistryHolder().key().location().toString();
        // items carry the DataComponent system in 1.21.1, not a CompoundTag; item
        // rules match by id (see ItemControlConfig).
        return ItemControlConfig.isBlacklisted(id, null);
    }

    private static boolean isOpOrCreative(Player player) {
        if (player.isCreative()) {
            return true;
        }
        if (player instanceof ServerPlayer sp) {
            return sp.hasPermissions(2); // OP level
        }
        return false;
    }

    private static String formatEntityPos(Entity entity) {
        if (entity == null) {
            return "unknown position";
        }
        return formatPos(entity.level(), entity.blockPosition());
    }

    private static String formatPos(Level level, BlockPos pos) {
        if (pos == null) {
            return "unknown position";
        }
        String dim = formatDimension(level);
        return String.format("%s @ %d, %d, %d", dim, pos.getX(), pos.getY(), pos.getZ());
    }

    private static String formatDimension(Level level) {
        if (level == null) {
            return "unknown dimension";
        }
        ResourceKey<Level> key = level.dimension();
        if (key == Level.OVERWORLD) {
            return "overworld";
        }
        if (key == Level.NETHER) {
            return "the_nether";
        }
        if (key == Level.END) {
            return "the_end";
        }
        return key.location().toString();
    }

    /**
     * Clears all banned items from a player's inventory and logs/announces.
     *
     * @return first cleared item copy; {#link ItemStack#EMPTY} if none.
     */
    private static ItemStack purgeInventoryItems(Player player, String location, String source, boolean sync) {
        ItemStack firstViolation = ItemStack.EMPTY;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty() && isBlacklisted(stack)) {
                if (firstViolation.isEmpty()) {
                    firstViolation = stack.copy();
                }
                // delete first so a ban/kick never interrupts the removal
                ItemStack removed = stack.copy();
                player.getInventory().setItem(i, ItemStack.EMPTY);
                logObtained(player, removed, location, source, false);
            }
        }

        // also clear a dragged cursor item
        if (player.containerMenu != null) {
            ItemStack carried = player.containerMenu.getCarried();
            if (!carried.isEmpty() && isBlacklisted(carried)) {
                if (firstViolation.isEmpty()) {
                    firstViolation = carried.copy();
                }
                ItemStack removed = carried.copy();
                player.containerMenu.setCarried(ItemStack.EMPTY);
                logObtained(player, removed, location, source, false);
            }
        }

        if (!firstViolation.isEmpty() && sync && player instanceof ServerPlayer serverPlayer) {
            player.getInventory().setChanged();
            serverPlayer.inventoryMenu.broadcastChanges();
            serverPlayer.containerMenu.broadcastChanges();
        }
        return firstViolation;
    }

    private static boolean inventoryHasBlacklisted(Player player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty() && isBlacklisted(stack)) {
                return true;
            }
        }
        if (player.containerMenu != null) {
            ItemStack carried = player.containerMenu.getCarried();
            if (!carried.isEmpty() && isBlacklisted(carried)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Only ban/kick once the inventory is verified clean; if anything remains,
     * clear once more and, only if that still fails, log an error and don't kick.
     */
    private static void banAfterVerifiedClean(ServerPlayer player, ItemStack sampleViolation) {
        if (!ItemControlConfig.autoBanOnViolation() || sampleViolation == null || sampleViolation.isEmpty()) {
            return;
        }
        String itemId = sampleViolation.getItem().builtInRegistryHolder().key().location().toString();
        if (ItemControlConfig.isExcludedFromLog(itemId)) {
            return;
        }
        if (player.getServer() == null || player.hasDisconnected()) {
            return;
        }

        if (inventoryHasBlacklisted(player)) {
            ItemControl.LOGGER.warn("[item_control] violation still present before ban; second purge | {}",
                    player.getGameProfile().getName());
            purgeInventoryItems(player, formatEntityPos(player), "second purge", true);
        }
        if (inventoryHasBlacklisted(player)) {
            ItemControl.LOGGER.error("[item_control] could not fully clear violations; skipping kick to avoid item loss | {}",
                    player.getGameProfile().getName());
            return;
        }

        ItemControl.LOGGER.info("[item_control] confirmed clean inventory; banning | {} | item {}",
                player.getGameProfile().getName(), itemId);
        String banReason = String.format("You were banned for obtaining a banned item (%s). Contact the server admins.", itemId);
        BanHelper.banAndKick(player, banReason);
    }

    /**
     * Logs/announces a violation. The ban must be invoked separately via
     * {@link #banAfterVerifiedClean} after the item is removed and verified.
     */
    private static void logObtained(Player player, ItemStack stack, String location, String source, boolean allowBan) {
        String itemId = stack.getItem().builtInRegistryHolder().key().location().toString();
        String loc = location == null || location.isBlank() ? formatEntityPos(player) : location;
        String src = source == null || source.isBlank() ? "unknown" : source;

        if (!ItemControlConfig.isExcludedFromLog(itemId)) {
            String date = LocalDate.now().format(DATE_FORMAT);
            Path logFile = logDir.resolve(date + ".log");
            String entry = String.format("[%s] player %s obtained banned item %s (count %d) | source: %s | at %s\n",
                    java.time.LocalDateTime.now(), player.getName().getString(), itemId, stack.getCount(), src, loc);
            try {
                Files.writeString(logFile, entry, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException e) {
                ItemControl.LOGGER.error("[item_control] audit log write failed", e);
            }
            ItemControl.LOGGER.info("[item_control] banned item {} | player {} | source {} | at {}",
                    itemId, player.getName().getString(), src, loc);
        }

        if (ItemControlConfig.publicAnnounce()) {
            String nbtInfo = "";
            if (!stack.getComponents().isEmpty()) {
                // Items in 1.21.1 use the DataComponent system; show a short
                // component-based hint instead of the legacy SNBT tag.
                nbtInfo = " (components)";
            }
            String message = String.format("\u00a7c[ItemControl] \u00a7fplayer \u00a7e%s \u00a7ffor holding banned item \u00a7c%s%s \u00a7fsystem removed it! \u00a77[%s | %s]",
                    player.getName().getString(), itemId, nbtInfo, src, loc);
            if (player.level().getServer() != null) {
                player.level().getServer().getPlayerList().broadcastSystemMessage(Component.literal(message), false);
            }
        }

        if (allowBan && player instanceof ServerPlayer serverPlayer) {
            banAfterVerifiedClean(serverPlayer, stack);
        }
    }

    private static void detectLoadedMods() {
        var modList = net.neoforged.fml.ModList.get();
        modsDetected = true;
        ItemControl.LOGGER.info("[item_control] storage mods detected - sophisticatedbackpacks:{}, ae2:{}, create:{}",
                modList.isLoaded("sophisticatedbackpacks"),
                modList.isLoaded("ae2"),
                modList.isLoaded("create"));
    }

    private static void scheduleNextScan(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        PlayerScanState state = scanStates.computeIfAbsent(player.getUUID(), id -> new PlayerScanState());
        if (state.busy) {
            return;
        }
        ScanKind kind = nextDueScan(state, player.tickCount);
        if (kind == null) {
            return;
        }
        state.busy = true;
        switch (kind) {
            case DROP -> {
                state.lastDrop = player.tickCount;
                startDroppedItemScan(player, state);
            }
            case INV -> {
                state.lastInv = player.tickCount;
                startInventoryScan(player, state);
            }
            case ENV -> {
                state.lastEnv = player.tickCount;
                startEnvironmentScan(player, state);
            }
        }
    }

    private static ScanKind nextDueScan(PlayerScanState state, int tick) {
        boolean hasItemRules = ItemControlConfig.hasItemBlacklist();
        ScanKind best = null;
        int bestOverdue = -1;
        if (ItemControlConfig.detectDroppedItems() && hasItemRules) {
            int overdue = tick - state.lastDrop;
            if (overdue >= DROP_SCAN_INTERVAL && overdue > bestOverdue) {
                best = ScanKind.DROP;
                bestOverdue = overdue;
            }
        }
        if (hasItemRules) {
            int overdue = tick - state.lastInv;
            if (overdue >= INV_SCAN_INTERVAL && overdue > bestOverdue) {
                best = ScanKind.INV;
                bestOverdue = overdue;
            }
        }
        boolean envNeeded = hasItemRules || (ItemControlConfig.detectWorldBlocks() && ItemControlConfig.hasBlockBlacklist());
        if (envNeeded) {
            int overdue = tick - state.lastEnv;
            if (overdue >= ENV_SCAN_INTERVAL && overdue > bestOverdue) {
                best = ScanKind.ENV;
            }
        }
        return best;
    }

    private static void finishScan(PlayerScanState state) {
        if (state != null) {
            state.busy = false;
        }
    }

    private static void submitMatch(MinecraftServer server, PlayerScanState state, Runnable matchWork) {
        ExecutorService executor = scanExecutor;
        if (executor == null || executor.isShutdown()) {
            finishScan(state);
            return;
        }
        try {
            executor.execute(() -> {
                try {
                    matchWork.run();
                } catch (Throwable t) {
                    ItemControl.LOGGER.warn("[item_control] background scan failed: {}", t.toString());
                    server.execute(() -> finishScan(state));
                }
            });
        } catch (RejectedExecutionException ignored) {
            finishScan(state);
        }
    }

    private static void startInventoryScan(ServerPlayer player, PlayerScanState state) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            finishScan(state);
            return;
        }
        List<InvSnapshot> snapshots = new ArrayList<>();
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty()) {
                snapshots.add(new InvSnapshot(i, false, stack.copy()));
            }
        }
        if (player.containerMenu != null) {
            ItemStack carried = player.containerMenu.getCarried();
            if (!carried.isEmpty()) {
                snapshots.add(new InvSnapshot(-1, true, carried.copy()));
            }
        }
        if (snapshots.isEmpty()) {
            finishScan(state);
            return;
        }
        UUID playerId = player.getUUID();
        ItemControlRuleSet rules = ItemControlConfig.getBlacklistRules();
        submitMatch(server, state, () -> {
            List<InvSnapshot> hits = new ArrayList<>();
            for (InvSnapshot shot : snapshots) {
                if (rules.contains(shot.stack().getItem().builtInRegistryHolder().key().location().toString(), null)) {
                    hits.add(shot);
                }
            }
            if (hits.isEmpty()) {
                finishScan(state);
                return;
            }
            server.execute(() -> {
                try {
                    applyInventoryHits(server, playerId, hits);
                } finally {
                    finishScan(state);
                }
            });
        });
    }

    private static void applyInventoryHits(MinecraftServer server, UUID playerId, List<InvSnapshot> hits) {
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player == null || player.hasDisconnected()) {
            return;
        }
        ItemStack firstViolation = ItemStack.EMPTY;
        for (InvSnapshot hit : hits) {
            ItemStack live;
            if (hit.carried()) {
                if (player.containerMenu == null) {
                    continue;
                }
                live = player.containerMenu.getCarried();
                if (live.isEmpty() || !isBlacklisted(live)) {
                    continue;
                }
                if (firstViolation.isEmpty()) {
                    firstViolation = live.copy();
                }
                ItemStack removed = live.copy();
                player.containerMenu.setCarried(ItemStack.EMPTY);
                logObtained(player, removed, formatEntityPos(player), "inventory", false);
            } else {
                live = player.getInventory().getItem(hit.slot());
                if (live.isEmpty() || !isBlacklisted(live)) {
                    continue;
                }
                if (firstViolation.isEmpty()) {
                    firstViolation = live.copy();
                }
                ItemStack removed = live.copy();
                player.getInventory().setItem(hit.slot(), ItemStack.EMPTY);
                logObtained(player, removed, formatEntityPos(player), "inventory", false);
            }
        }
        if (!firstViolation.isEmpty()) {
            player.getInventory().setChanged();
            player.inventoryMenu.broadcastChanges();
            player.containerMenu.broadcastChanges();
            banAfterVerifiedClean(player, firstViolation);
        }
    }

    /**
     * Scans dropped items around the player (only when detectDroppedItems is
     * on). Main thread snapshots entities; matching is background; removal/
     * announcement/ban returns to the main thread.
     */
    private static void startDroppedItemScan(ServerPlayer player, PlayerScanState state) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            finishScan(state);
            return;
        }
        var level = player.level();
        int centerChunkX = player.chunkPosition().x;
        int centerChunkZ = player.chunkPosition().z;
        var aabb = new net.minecraft.world.phys.AABB(
            (centerChunkX - 2) << 4, level.getMinBuildHeight(), (centerChunkZ - 2) << 4,
            ((centerChunkX + 2) << 4) + 16, level.getMaxBuildHeight(), ((centerChunkZ + 2) << 4) + 16
        );

        List<DropSnapshot> snapshots = new ArrayList<>();
        for (ItemEntity itemEntity : level.getEntitiesOfClass(ItemEntity.class, aabb)) {
            ItemStack stack = itemEntity.getItem();
            if (!stack.isEmpty()) {
                snapshots.add(new DropSnapshot(itemEntity.getId(), stack.copy(), formatEntityPos(itemEntity)));
            }
        }
        if (snapshots.isEmpty()) {
            finishScan(state);
            return;
        }

        ItemControlRuleSet rules = ItemControlConfig.getBlacklistRules();
        UUID playerId = player.getUUID();
        submitMatch(server, state, () -> {
            List<DropSnapshot> hits = new ArrayList<>();
            for (DropSnapshot shot : snapshots) {
                if (rules.contains(shot.stack().getItem().builtInRegistryHolder().key().location().toString(), null)) {
                    hits.add(shot);
                }
            }
            if (hits.isEmpty()) {
                finishScan(state);
                return;
            }
            server.execute(() -> {
                try {
                    applyDroppedItemHits(server, playerId, hits);
                } finally {
                    finishScan(state);
                }
            });
        });
    }

    private static void applyDroppedItemHits(MinecraftServer server, UUID playerId, List<DropSnapshot> hits) {
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player == null || player.hasDisconnected()) {
            return;
        }
        var level = player.level();
        ItemStack firstViolation = ItemStack.EMPTY;
        for (DropSnapshot hit : hits) {
            Entity entity = level.getEntity(hit.entityId());
            if (!(entity instanceof ItemEntity itemEntity) || !itemEntity.isAlive()) {
                continue;
            }
            ItemStack live = itemEntity.getItem();
            if (live.isEmpty() || !isBlacklisted(live)) {
                continue;
            }
            if (firstViolation.isEmpty()) {
                firstViolation = live.copy();
            }
            ItemStack removed = live.copy();
            itemEntity.discard();
            logObtained(player, removed, hit.loc(), "dropped item", false);
        }
        if (!firstViolation.isEmpty()) {
            purgeInventoryItems(player, formatEntityPos(player), "dropped-item linked purge", true);
            banAfterVerifiedClean(player, firstViolation);
        }
    }

    /**
     * Scans item-frames and banned blocks within 2 chunks of the player.
     * Main-thread snapshot, background match, main-thread cleanup.
     */
    private static void startEnvironmentScan(ServerPlayer player, PlayerScanState state) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            finishScan(state);
            return;
        }
        var level = player.level();
        int centerChunkX = player.chunkPosition().x;
        int centerChunkZ = player.chunkPosition().z;
        int playerY = player.getBlockY();
        var aabb = new net.minecraft.world.phys.AABB(
            (centerChunkX - 2) << 4, level.getMinBuildHeight(), (centerChunkZ - 2) << 4,
            ((centerChunkX + 2) << 4) + 16, level.getMaxBuildHeight(), ((centerChunkZ + 2) << 4) + 16
        );

        List<FrameSnapshot> frames = new ArrayList<>();
        if (ItemControlConfig.hasItemBlacklist()) {
            for (ItemFrame frame : level.getEntitiesOfClass(ItemFrame.class, aabb)) {
                ItemStack displayed = frame.getItem();
                if (!displayed.isEmpty()) {
                    frames.add(new FrameSnapshot(frame.getId(), displayed.copy(), formatEntityPos(frame)));
                }
            }
        }

        List<BlockSnapshot> blocks = new ArrayList<>();
        if (ItemControlConfig.detectWorldBlocks() && ItemControlConfig.hasBlockBlacklist()) {
            BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
            int minY = Math.max(playerY - 16, level.getMinBuildHeight());
            int maxY = Math.min(playerY + 16, level.getMaxBuildHeight() - 1);
            int minBuild = level.getMinBuildHeight();
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    int chunkX = centerChunkX + dx;
                    int chunkZ = centerChunkZ + dz;
                    var chunk = level.getChunkSource().getChunk(chunkX, chunkZ, false);
                    if (chunk == null) {
                        continue;
                    }
                    int minSection = chunk.getSectionIndex(minY);
                    int maxSection = chunk.getSectionIndex(maxY);
                    for (int sec = minSection; sec <= maxSection; sec++) {
                        LevelChunkSection section = chunk.getSection(sec);
                        if (section.hasOnlyAir()) {
                            continue;
                        }
                        int baseY = minBuild + (sec << 4);
                        int yStart = Math.max(minY, baseY);
                        int yEnd = Math.min(maxY, baseY + 15);
                        int originX = chunkX << 4;
                        int originZ = chunkZ << 4;
                        for (int x = originX; x < originX + 16; x++) {
                            for (int z = originZ; z < originZ + 16; z++) {
                                for (int y = yStart; y <= yEnd; y++) {
                                    BlockState blockState = chunk.getBlockState(cursor.set(x, y, z));
                                    if (blockState.isAir()) {
                                        continue;
                                    }
                                    if (!ItemControlConfig.isTrackedBlock(blockState.getBlock())) {
                                        continue;
                                    }
                                    String blockId = blockState.getBlock().builtInRegistryHolder().key().location().toString();
                                    CompoundTag blockNbt = null;
                                    var blockEntity = level.getBlockEntity(cursor);
                                    if (blockEntity != null && ItemControlConfig.blockIdNeedsNbt(blockId)) {
                                        blockNbt = blockEntity.saveWithFullMetadata(level.registryAccess());
                                    }
                                    blocks.add(new BlockSnapshot(cursor.immutable(), blockId,
                                            blockNbt == null ? null : blockNbt.copy()));
                                }
                            }
                        }
                    }
                }
            }
        }

        if (frames.isEmpty() && blocks.isEmpty()) {
            finishScan(state);
            return;
        }

        ItemControlRuleSet itemRules = ItemControlConfig.getBlacklistRules();
        ItemControlRuleSet blockRules = ItemControlConfig.getBlockBlacklistRules();
        UUID playerId = player.getUUID();
        submitMatch(server, state, () -> {
            List<FrameSnapshot> frameHits = new ArrayList<>();
            for (FrameSnapshot shot : frames) {
                if (itemRules.contains(shot.stack().getItem().builtInRegistryHolder().key().location().toString(), null)) {
                    frameHits.add(shot);
                }
            }
            List<BlockSnapshot> blockHits = new ArrayList<>();
            for (BlockSnapshot shot : blocks) {
                if (blockRules.contains(shot.blockId(), shot.nbt())) {
                    blockHits.add(shot);
                }
            }
            if (frameHits.isEmpty() && blockHits.isEmpty()) {
                finishScan(state);
                return;
            }
            server.execute(() -> {
                try {
                    applyEnvironmentHits(server, playerId, frameHits, blockHits);
                } finally {
                    finishScan(state);
                }
            });
        });
    }

    private static void applyEnvironmentHits(MinecraftServer server, UUID playerId,
                                             List<FrameSnapshot> frames, List<BlockSnapshot> blocks) {
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player == null || player.hasDisconnected()) {
            return;
        }
        var level = player.level();
        ItemStack firstItem = ItemStack.EMPTY;
        for (FrameSnapshot hit : frames) {
            Entity entity = level.getEntity(hit.entityId());
            if (!(entity instanceof ItemFrame frame) || !frame.isAlive()) {
                continue;
            }
            ItemStack live = frame.getItem();
            if (live.isEmpty() || !isBlacklisted(live)) {
                continue;
            }
            if (firstItem.isEmpty()) {
                firstItem = live.copy();
            }
            ItemStack removed = live.copy();
            frame.setItem(ItemStack.EMPTY);
            logObtained(player, removed, hit.loc(), "item frame", false);
        }
        for (BlockSnapshot hit : blocks) {
            BlockState liveState = level.getBlockState(hit.pos());
            if (liveState.isAir()) {
                continue;
            }
            String liveId = liveState.getBlock().builtInRegistryHolder().key().location().toString();
            if (!hit.blockId().equals(liveId)) {
                continue;
            }
            CompoundTag liveNbt = null;
            var blockEntity = level.getBlockEntity(hit.pos());
            if (blockEntity != null && ItemControlConfig.blockIdNeedsNbt(liveId)) {
                liveNbt = blockEntity.saveWithFullMetadata(level.registryAccess());
            }
            if (!ItemControlConfig.isBlockBlacklisted(liveId, liveNbt)) {
                continue;
            }
            level.setBlock(hit.pos(), Blocks.AIR.defaultBlockState(), 3);
            logBlockViolation(player, liveId, liveNbt, formatPos(level, hit.pos()));
            if (player.hasDisconnected()) {
                return;
            }
        }
        if (!firstItem.isEmpty()) {
            purgeInventoryItems(player, formatEntityPos(player), "item-frame linked purge", true);
            banAfterVerifiedClean(player, firstItem);
        }
    }

    /**
     * Logs/announces a block violation (same shape as {@link #logObtained} but
     * with its own message). Auto-ban applies here too.
     */
    private static void logBlockViolation(Player player, String blockId, CompoundTag nbt, String location) {
        String loc = location == null || location.isBlank() ? formatEntityPos(player) : location;

        if (!ItemControlConfig.isExcludedFromLog(blockId)) {
            String date = LocalDate.now().format(DATE_FORMAT);
            Path logFile = logDir.resolve(date + ".log");
            String entry = String.format("[%s] player %s broke banned block %s | source: world | at %s\n",
                    java.time.LocalDateTime.now(), player.getName().getString(), blockId, loc);
            try {
                Files.writeString(logFile, entry, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException e) {
                ItemControl.LOGGER.error("[item_control] audit log write failed", e);
            }
            ItemControl.LOGGER.info("[item_control] banned block {} | player {} | at {}",
                    blockId, player.getName().getString(), loc);
        }

        if (ItemControlConfig.publicAnnounce()) {
            String nbtInfo = "";
            if (nbt != null && !nbt.isEmpty()) {
                String nbtStr = nbt.toString();
                if (nbtStr.length() > 60) {
                    nbtStr = nbtStr.substring(0, 57) + "...";
                }
                nbtInfo = " " + nbtStr;
            }
            String message = String.format("\u00a7c[ItemControl] \u00a7fplayer \u00a7e%s \u00a7ffor breaking banned block \u00a7c%s%s \u00a7fsystem removed it! \u00a77[world | %s]",
                    player.getName().getString(), blockId, nbtInfo, loc);
            if (player.level().getServer() != null) {
                player.level().getServer().getPlayerList().broadcastSystemMessage(Component.literal(message), false);
            }
        }

        if (ItemControlConfig.autoBanOnViolation() && !ItemControlConfig.isExcludedFromLog(blockId)
                && player instanceof ServerPlayer serverPlayer) {
            if (serverPlayer.getServer() != null) {
                String banReason = String.format("You were banned for breaking a banned block (%s). Contact the server admins.", blockId);
                BanHelper.banAndKick(serverPlayer, banReason);
            }
        }
    }
}