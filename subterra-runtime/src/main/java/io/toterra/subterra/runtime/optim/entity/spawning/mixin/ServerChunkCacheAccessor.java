package io.toterra.subterra.runtime.optim.entity.spawning.mixin;

import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.world.level.NaturalSpawner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes the package-private per-tick spawn state so the enforcement shell
 * can read the current category mob counts (vanilla's protected access).
 */
@Mixin(ServerChunkCache.class)
public interface ServerChunkCacheAccessor {

    @Accessor("lastSpawnState")
    NaturalSpawner.SpawnState subterra$getLastSpawnState();
}