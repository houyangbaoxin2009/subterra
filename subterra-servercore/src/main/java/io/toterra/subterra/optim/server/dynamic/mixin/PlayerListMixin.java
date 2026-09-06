// Ported from ServerCore (github.com/Wesley1808/ServerCore), MIT (c) Wesley1808.
package io.toterra.subterra.optim.server.dynamic.mixin;

import io.toterra.subterra.optim.server.dynamic.DynamicSetting;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Keeps the live {@link DynamicSetting} view/simulation-distance values in
 * sync with the actual server values whenever the {@link PlayerList} is told
 * to change them (e.g. by the manager or by commands).
 */
@Mixin(PlayerList.class)
public class PlayerListMixin {

    @Inject(method = "setViewDistance", at = @At("HEAD"))
    private void subterra$updateViewDistance(int value, CallbackInfo ci) {
        DynamicSetting.VIEW_DISTANCE.set(value, null);
    }

    @Inject(method = "setSimulationDistance", at = @At("HEAD"))
    private void subterra$updateSimulationDistance(int value, CallbackInfo ci) {
        DynamicSetting.SIMULATION_DISTANCE.set(value, null);
    }
}