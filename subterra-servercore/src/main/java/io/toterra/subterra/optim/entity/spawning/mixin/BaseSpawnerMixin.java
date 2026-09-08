// Clean-room re-key of the ServerCore "enforce-mobcap" GPL face: spawner
// spawns may be counted against the enforced category pool (off by default).
package io.toterra.subterra.optim.entity.spawning.mixin;

import io.toterra.subterra.engine.optim.entity.spawning.SpawnEnforcement.Source;
import io.toterra.subterra.optim.entity.spawning.shell.SpawnEnforcementShell;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BaseSpawner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Enforcement shell for the spawner special spawn source: the spawn-produce
 * {@code tryAddFreshEntityWithPassengers} call inside
 * {@link BaseSpawner#serverTick} is gated by the shared enforcement predicate
 * (off by default — vanilla behaviour preserved).
 */
@Mixin(BaseSpawner.class)
public abstract class BaseSpawnerMixin {

    @Redirect(
            method = "serverTick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerLevel;tryAddFreshEntityWithPassengers(Lnet/minecraft/world/entity/Entity;)Z"
            )
    )
    private boolean subterra$enforcedSpawnerSpawn(ServerLevel level, Entity entity) {
        Source enforcement = Source.disabled(); // default: vanilla bypass
        if (!SpawnEnforcementShell.allowed(level, enforcement, SpawnEnforcementShell.categoryOf(entity.getType()))) {
            return false; // spawn refused by the enforced category pool
        }
        return level.tryAddFreshEntityWithPassengers(entity);
    }
}