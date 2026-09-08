// Ported from Inventory Advancement Accelerator (github.com/vicuna-main/InventoryAdvancementAccelerator), MIT (c) vicuna.
package io.toterra.subterra.runtime.optim.server.advancement.mixin;

import io.toterra.subterra.runtime.optim.server.advancement.InventoryAdvancementAccelerator;
import net.minecraft.advancements.critereon.ItemPredicate;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ItemPredicate.class, priority = 900)
abstract class ItemPredicateMixin {
    @Inject(method = "test(Lnet/minecraft/world/item/ItemStack;)Z", at = @At("HEAD"), require = 0)
    private void invadvopt$countPredicateTest(ItemStack stack, CallbackInfoReturnable<Boolean> callback) {
        InventoryAdvancementAccelerator.runtime().onItemPredicateTest();
    }
}
