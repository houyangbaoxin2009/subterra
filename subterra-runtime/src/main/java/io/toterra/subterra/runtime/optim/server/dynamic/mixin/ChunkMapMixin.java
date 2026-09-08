// Ported from ServerCore (github.com/Wesley1808/ServerCore), MIT (c) Wesley1808.
package io.toterra.subterra.runtime.optim.server.dynamic.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import io.toterra.subterra.runtime.optim.server.dynamic.DynamicSetting;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Ties the distance at which a chunk is "close enough" to a player for
 * spawning to the dynamically-tuned {@code chunk_tick_distance} knob instead
 * of a hard-coded value.
 */
@Mixin(ChunkMap.class)
public class ChunkMapMixin {

    @ModifyReturnValue(method = "playerIsCloseEnoughForSpawning", at = @At("RETURN"))
    private boolean subterra$withinChunkTickDistance(boolean original, ServerPlayer player, ChunkPos pos) {
        return original && player.chunkPosition().getChessboardDistance(pos) <= DynamicSetting.CHUNK_TICK_DISTANCE.get();
    }
}