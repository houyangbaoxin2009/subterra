package io.toterra.subterra.optim.worldgen.ticking.mixin;

import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Exposes the package-private {@link ChunkMap#getChunks()} holder iterator to
 * the clean-room ticking-cache mixin (vanilla's protected access).
 */
@Mixin(ChunkMap.class)
public interface ChunkMapAccessor {

    @Invoker("getChunks")
    Iterable<ChunkHolder> subterra$invokeGetChunks();
}