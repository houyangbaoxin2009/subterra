// Clean-room re-key of the ServerCore "cache-ticking-chunks" concept
// (MIT/GPL-mixed surface, re-written from behaviour contract, no upstream
// code): vanilla ServerChunkCache.tickChunks() iterates every loaded chunk
// holder once per tick to collect the ticking ones; the snapshot from
// io.toterra.subterra.engine.worldgen.ticking.TickingChunkCache makes that
// full scan a once-per-second (every 20 ticks) rebuild, reused in between.
package io.toterra.subterra.optim.worldgen.ticking.mixin;

import io.toterra.subterra.engine.worldgen.ticking.TickingChunkCache;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.ArrayList;
import java.util.List;

/**
 * Redirects the per-tick full chunk scan in
 * {@link ServerChunkCache#tickChunks()} to a cached list of currently-ticking
 * holders that is rebuilt once per second ({@link TickingChunkCache}'s
 * refresh interval). Chunk enter/leave takes effect with at most a 1-second
 * delay; the per-tick scan cost drops to the size of the ticking set (the
 * vanilla collect step then filters them as before, keeping behaviour
 * identical except for the delayed visibility).
 */
@Mixin(ServerChunkCache.class)
public abstract class ServerChunkCacheMixin {

    @Unique
    private final TickingChunkCache subterra$cache = new TickingChunkCache();

    @Unique
    private List<ChunkHolder> subterra$tickingHolders = null;

    @Redirect(
            method = "tickChunks",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ChunkMap;getChunks()Ljava/lang/Iterable;"
            )
    )
    private Iterable<ChunkHolder> subterra$cachedTickingChunks(ChunkMap chunkMap) {
        ServerLevel level = ((ServerChunkCache) (Object) this).level;
        int tick = (int) (level.getGameTime() & 0x7fffffffL);
        if (this.subterra$tickingHolders == null || TickingChunkCache.isRefreshBoundary(tick)) {
            List<Long> ticking = new ArrayList<>();
            List<ChunkHolder> fresh = new ArrayList<>();
            for (ChunkHolder holder : ((ChunkMapAccessor) chunkMap).subterra$invokeGetChunks()) {
                if (holder.getTickingChunk() != null) {
                    fresh.add(holder);
                    ticking.add(holder.getPos().toLong());
                }
            }
            this.subterra$cache.update(tick, ticking);
            this.subterra$tickingHolders = fresh;
        }
        return this.subterra$tickingHolders;
    }
}