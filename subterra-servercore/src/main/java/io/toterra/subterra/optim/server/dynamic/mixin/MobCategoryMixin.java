// Ported from ServerCore (github.com/Wesley1808/ServerCore), MIT (c) Wesley1808.
package io.toterra.subterra.optim.server.dynamic.mixin;

import io.toterra.subterra.optim.server.dynamic.IMobCategory;
import net.minecraft.world.entity.MobCategory;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Lets the dynamic feature scale a {@link MobCategory}'s spawn cap on the fly.
 * The 1.21.1 enum constructor is
 * {@code (String, int, String, int, boolean, boolean, int)} — the trailing int
 * is {@code noDespawnDistance}; only {@code max} feeds the cap, which is what
 * {@code getMaxInstancesPerChunk} reports.
 */
@Mixin(value = MobCategory.class, priority = 900)
public class MobCategoryMixin implements IMobCategory {
    @Shadow
    @Final
    private int max;

    @Unique
    private int subterra$modifiedCapacity;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void subterra$onInit(String enumName, int index, String name, int max, boolean isFriendly, boolean isPersistent, int noDespawnDistance, CallbackInfo ci) {
        this.subterra$modifiedCapacity = max;
    }

    // Should be @ModifyReturnValue to remove callback info allocations, however carpet mod.
    @Inject(method = "getMaxInstancesPerChunk", at = @At("HEAD"), cancellable = true)
    private void subterra$getCapacity(CallbackInfoReturnable<Integer> cir) {
        if (this.subterra$modifiedCapacity != this.max) {
            cir.setReturnValue(this.subterra$modifiedCapacity);
        }
    }

    @Override
    public void subterra$modifyCapacity(double modifier) {
        this.subterra$modifiedCapacity = (int) (this.max * modifier);
    }
}