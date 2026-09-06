// Ported from mc-smoothboot (github.com/UltimateBoomer/mc-smoothboot), MIT (c) UltimateBoomer.
package io.toterra.subterra.optim.server.threading.mixin.server;

import io.toterra.subterra.optim.server.threading.WorkerPoolTuning;
import net.minecraft.server.Main;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Dedicated-server game thread priority: applied at the very start of
 * {@code net.minecraft.server.Main.main}, before the boot try/catch, matching
 * upstream (HEAD inject, {@code remap = false}).
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
        WorkerPoolTuning.LOGGER.debug("Initialized server game thread");
    }
}
