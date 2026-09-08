// Ported from Inventory Advancement Accelerator (github.com/vicuna-main/InventoryAdvancementAccelerator), MIT (c) vicuna.
package io.toterra.subterra.runtime.optim.server.advancement.mixin;

import io.toterra.subterra.runtime.optim.server.advancement.InventoryAdvancementAccelerator;
import net.minecraft.advancements.CriterionTrigger;
import net.minecraft.advancements.critereon.InventoryChangeTrigger;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.server.PlayerAdvancements;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = SimpleCriterionTrigger.class, priority = 900)
abstract class SimpleCriterionTriggerMixin {
    @Inject(method = "addPlayerListener(Lnet/minecraft/server/PlayerAdvancements;Lnet/minecraft/advancements/CriterionTrigger$Listener;)V", at = @At("TAIL"), require = 0)
    private void invadvopt$onListenerAdded(PlayerAdvancements advancements, CriterionTrigger.Listener<?> listener, CallbackInfo callback) {
        if ((Object)this instanceof InventoryChangeTrigger) {
            InventoryAdvancementAccelerator.runtime().addListener((InventoryChangeTrigger)(Object)this, advancements, listener);
        }
    }

    @Inject(method = "removePlayerListener(Lnet/minecraft/server/PlayerAdvancements;Lnet/minecraft/advancements/CriterionTrigger$Listener;)V", at = @At("TAIL"), require = 0)
    private void invadvopt$onListenerRemoved(PlayerAdvancements advancements, CriterionTrigger.Listener<?> listener, CallbackInfo callback) {
        if ((Object)this instanceof InventoryChangeTrigger) {
            InventoryAdvancementAccelerator.runtime().removeListener((InventoryChangeTrigger)(Object)this, advancements, listener);
        }
    }

    @Inject(method = "removePlayerListeners(Lnet/minecraft/server/PlayerAdvancements;)V", at = @At("TAIL"), require = 0)
    private void invadvopt$onAllListenersRemoved(PlayerAdvancements advancements, CallbackInfo callback) {
        if ((Object)this instanceof InventoryChangeTrigger) {
            InventoryAdvancementAccelerator.runtime().removeListeners(advancements);
        }
    }
}
