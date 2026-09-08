// Ported from mc-smoothboot (github.com/UltimateBoomer/mc-smoothboot), MIT (c) UltimateBoomer.
package io.toterra.subterra.runtime.optim.server.threading.mixin.client;

import io.toterra.subterra.runtime.optim.server.threading.WorkerPoolTuning;
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
        if (!WorkerPoolTuning.initConfig) {
            WorkerPoolTuning.config();
            WorkerPoolTuning.initConfig = true;
        }

        Thread.currentThread().setPriority(WorkerPoolTuning.config().gamePriority());
        WorkerPoolTuning.LOGGER.debug("Initialized client game thread");
    }
}
