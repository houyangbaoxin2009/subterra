// Clean-room re-key of the ServerCore mob-spawning "enforce-mobcap" GPL face
// (no upstream code): zombie reinforcements may be counted against the mob
// population via the io.toterra.subterra.engine.optim.entity.spawning core.
package io.toterra.subterra.runtime.optim.entity.spawning.mixin;

import io.toterra.subterra.engine.optim.entity.spawning.SpawnEnforcement;
import io.toterra.subterra.engine.optim.entity.spawning.SpawnEnforcement.Source;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.NaturalSpawner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Enforcement shell for the zombie-reinforcement special spawn source: the
 * reinforcement {@code addFreshEntityWithPassengers} call inside
 * {@link Zombie#hurt} is gated by
 * {@link SpawnEnforcement#allowed(Source, int, int)} against the current
 * MONSTER mob count (read from the last spawn state) and a category baseline.
 *
 * <p>The per-source setting is currently the core default (enforcement off —
 * vanilla bypass preserved); it is the wiring hook a td switch will drive.
 * The category count/cap here is approximate (non-per-player) — per-player
 * accounting is vanilla since 1.21.1's {@code LocalMobCapCalculator}.</p>
 */
@Mixin(Zombie.class)
public abstract class ZombieReinforcementMixin {

    /** Approximate MONSTER category baseline (vanilla default is per-chunk). */
    @Unique
    private static final int MONSTER_CAP = 70;

    @Redirect(
            method = "hurt",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerLevel;addFreshEntityWithPassengers(Lnet/minecraft/world/entity/Entity;)V"
            )
    )
    private void subterra$enforceZombieReinforcement(ServerLevel level, Entity zombie) {
        Source enforcement = SpawnEnforcement.Source.disabled(); // default: vanilla bypass
        if (enforcement.enforce()) {
            NaturalSpawner.SpawnState state = ((ServerChunkCacheAccessor) level.getChunkSource()).subterra$getLastSpawnState();
            int count = state == null
                    ? 0
                    : state.getMobCategoryCounts().getOrDefault(MobCategory.MONSTER, 0);
            if (!SpawnEnforcement.allowed(enforcement, count, MONSTER_CAP)) {
                return; // reinforcement refused by the enforced category pool
            }
        }
        level.addFreshEntityWithPassengers(zombie);
    }
}