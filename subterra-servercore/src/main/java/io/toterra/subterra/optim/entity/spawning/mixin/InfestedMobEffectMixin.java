// Clean-room re-key of the ServerCore "enforce-mobcap" GPL face: infested
// silverfish spawns may be counted against the enforced pool.
package io.toterra.subterra.optim.entity.spawning.mixin;

import io.toterra.subterra.optim.entity.spawning.SpawnEnforcement.Source;
import io.toterra.subterra.optim.entity.spawning.shell.SpawnEnforcementShell;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Enforcement shell for the infested special spawn source: the
 * {@code addFreshEntity} call inside the effect's private
 * {@code spawnSilverfish} is gated by the shared enforcement predicate
 * (off by default — vanilla behaviour preserved). Targeted by string:
 * {@code InfestedMobEffect} is package-private in vanilla.
 */
@Mixin(targets = "net.minecraft.world.effect.InfestedMobEffect")
public abstract class InfestedMobEffectMixin {

    @Redirect(
            method = "spawnSilverfish",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"
            )
    )
    private boolean subterra$enforcedInfestedSpawn(Level level, Entity entity) {
        Source enforcement = Source.disabled(); // default: vanilla bypass
        if (level instanceof ServerLevel serverLevel) {
            if (!SpawnEnforcementShell.allowed(serverLevel, enforcement,
                    SpawnEnforcementShell.categoryOf(EntityType.SILVERFISH))) {
                return false; // spawn refused by the enforced category pool
            }
        }
        return level.addFreshEntity(entity);
    }
}