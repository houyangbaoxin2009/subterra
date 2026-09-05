// Ported from mc-smoothboot (github.com/UltimateBoomer/mc-smoothboot), MIT (c) UltimateBoomer.
package io.toterra.subterra.optim.server.smoothboot.mixin.client;

import io.toterra.subterra.optim.server.smoothboot.SmoothBoot;
import net.minecraft.client.main.Main;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Client game thread priority: applied at the very start of
 * {@code net.minecraft.client.main.Main.main}, matching upstream (HEAD inject,
 * {@code remap = false}).
 */
@Mixin(Main.class)
public class MainMixin {
    @Inject(method = "main", at = @At("HEAD"), remap = false)
    private static void onMain(CallbackInfo ci) {
        if (!SmoothBoot.initConfig) {
            SmoothBoot.config();
            SmoothBoot.initConfig = true;
        }

        Thread.currentThread().setPriority(SmoothBoot.config().gamePriority());
        SmoothBoot.LOGGER.debug("Initialized client game thread");
    }
}
