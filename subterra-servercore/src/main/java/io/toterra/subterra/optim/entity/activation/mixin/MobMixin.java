// Clean-room re-key of the ServerCore "activation-range" behaviour (GPL
// surface re-written from contract, no upstream code): the per-entity AI
// step (the customServerAiStep call inside the final Mob.serverAiStep) is
// gated by the io.toterra.subterra.optim.entity.activation.Activator core.
package io.toterra.subterra.optim.entity.activation.mixin;

import io.toterra.subterra.optim.entity.activation.ActivationPolicy;
import io.toterra.subterra.optim.entity.activation.Activator;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Skips {@link Mob#customServerAiStep()} (the brain / behaviour body) while
 * the entity is out of the activation range and not on a wake-up interval,
 * so far-away entities stop both brain and path-finding work. New entities,
 * falling or recently-hurt entities are always active (see
 * {@link Activator#isActive}). The panic immunity is not surfaced here (Mob
 * has no brain field); behaviour stays individual per entity otherwise.
 */
@Mixin(Mob.class)
public abstract class MobMixin {

    @Unique
    private static final ActivationPolicy SUBTERRA_ACTIVATION = ActivationPolicy.defaults();

    @WrapWithCondition(
            method = "serverAiStep",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/Mob;customServerAiStep()V"
            )
    )
    private boolean subterra$aiActivationActive(Mob self) {
        if (self.level().isClientSide) {
            return true;
        }
        double distSq = Double.POSITIVE_INFINITY;
        for (Player player : self.level().players()) {
            double d = self.distanceToSqr(player);
            if (d < distSq) {
                distSq = d;
            }
        }
        return Activator.isActive(
                SUBTERRA_ACTIVATION,
                distSq,
                self.tickCount,
                (int) (self.level().getGameTime() & 0x7fffffffL),
                !self.onGround(),
                self.hurtTime > 0,
                false);
    }
}