// Clean-room re-key of the ServerCore "enforce-mobcap" GPL face:
// nether-portal piglin spawns may be counted against the enforced pool.
package io.toterra.subterra.optim.entity.spawning.mixin;

import io.toterra.subterra.optim.entity.spawning.SpawnEnforcement.Source;
import io.toterra.subterra.optim.entity.spawning.shell.SpawnEnforcementShell;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.block.NetherPortalBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Enforcement shell for the portal-piglin special spawn source: the
 * {@link EntityType#spawn} call inside
 * {@link NetherPortalBlock#randomTick} is gated by the shared enforcement
 * predicate (off by default — vanilla behaviour preserved).
 */
@Mixin(NetherPortalBlock.class)
public abstract class NetherPortalBlockMixin {

    @Redirect(
            method = "randomTick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/EntityType;spawn(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/MobSpawnType;)Lnet/minecraft/world/entity/Entity;"
            )
    )
    private Entity subterra$enforcedPortalPiglin(EntityType<?> type, ServerLevel level, BlockPos pos, MobSpawnType spawnType) {
        Source enforcement = Source.disabled(); // default: vanilla bypass
        if (!SpawnEnforcementShell.allowed(level, enforcement, SpawnEnforcementShell.categoryOf(type))) {
            return null; // spawn refused by the enforced category pool
        }
        return type.spawn(level, pos, spawnType);
    }
}