// Ported from mc-smoothboot (github.com/UltimateBoomer/mc-smoothboot), MIT (c) UltimateBoomer.
package io.toterra.subterra.optim.server.smoothboot.mixin.client;

import io.toterra.subterra.optim.server.smoothboot.SmoothBoot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.server.Services;
import net.minecraft.server.WorldStem;
import net.minecraft.server.level.progress.ChunkProgressListenerFactory;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Integrated (singleplayer) server thread priority: set on the server thread
 * at the end of the constructor. Ported to the 1.21.1 constructor signature
 * (LevelStorage.Session/ResourcePackManager/SaveLoader/ApiServices/
 * WorldGenerationProgressListenerFactory were renamed on 1.20.x).
 */
@Mixin(IntegratedServer.class)
public class IntegratedServerMixin {
    @Inject(method = "<init>", at = @At("RETURN"))
    public void onInit(Thread serverThread, Minecraft minecraft, LevelStorageSource.LevelStorageAccess session,
                       PackRepository dataPackManager, WorldStem saveLoader, Services apiServices,
                       ChunkProgressListenerFactory worldGenerationProgressListenerFactory, CallbackInfo ci) {
        serverThread.setPriority(SmoothBoot.config().integratedServerPriority());
        SmoothBoot.LOGGER.debug("Initialized integrated server thread");
    }
}
