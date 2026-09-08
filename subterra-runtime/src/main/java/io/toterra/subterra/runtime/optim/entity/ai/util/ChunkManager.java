// Ported from ServerCore (github.com/Wesley1808/ServerCore), MIT (c) Wesley1808.
package io.toterra.subterra.runtime.optim.entity.ai.util;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.LevelReader;

/**
 * Minimal port of ServerCore's {@code ChunkManager}, restricted to the chunk
 * access used by the villager lobotomization mixin. Read-only, never mutates.
 */
public final class ChunkManager {

    private ChunkManager() {
    }

    /** Returns the full {@link ChunkAccess} at {@code pos}, or {@code null} if not fully loaded. */
    public static ChunkAccess getChunkNow(LevelReader levelReader, BlockPos pos) {
        return getChunkNow(levelReader, pos.getX() >> 4, pos.getZ() >> 4);
    }

    /** Returns the full {@link ChunkAccess} at chunk coordinates, or {@code null} if not loaded. */
    public static ChunkAccess getChunkNow(LevelReader levelReader, int chunkX, int chunkZ) {
        if (levelReader instanceof ServerLevel level) {
            return level.getChunkSource().getChunkNow(chunkX, chunkZ);
        } else {
            return levelReader.getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
        }
    }
}