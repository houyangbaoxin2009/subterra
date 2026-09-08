// Ported from ServerCore (github.com/Wesley1808/ServerCore), MIT (c) Wesley1808.
package io.toterra.subterra.runtime.optim.entity.ai.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import io.toterra.subterra.runtime.optim.entity.ai.LobotomizeConfig;
import io.toterra.subterra.runtime.optim.entity.ai.VillagerLobotomize;
import io.toterra.subterra.runtime.optim.entity.ai.util.ChunkManager;
import io.toterra.subterra.runtime.optim.entity.ai.util.EntityTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Based on: Purpur (Lobotomize-stuck-villagers.patch), reimplemented by
 * ServerCore.
 * <p>
 * Skips the {@link AbstractVillager}'s brain tick while it is unable to move
 * (stuck), checking only every {@code tick_interval} ticks, so path-finding CPU
 * is saved.
 * <p>
 * Ported to {@link AbstractVillager} (upstream targeted {@link Villager}); the
 * injection point is unchanged — the {@code Brain.tick} call inside the
 * {@code customServerAiStep} method.
 */
@Mixin(value = AbstractVillager.class, priority = 1100)
public abstract class AbstractVillagerMixin extends AgeableMob {
    @Unique
    private boolean subterra$lobotomized = false;

    @Unique
    private int subterra$notLobotomizedCount = 0;

    private AbstractVillagerMixin(EntityType<? extends AgeableMob> entityType, Level level) {
        super(entityType, level);
    }

    @WrapWithCondition(
            method = "customServerAiStep",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/ai/Brain;tick(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/LivingEntity;)V"
            )
    )
    private boolean subterra$shouldTickBrain(Brain<Villager> brain, ServerLevel level, LivingEntity livingEntity) {
        LobotomizeConfig config = VillagerLobotomize.config();
        return !config.enabled() || !this.subterra$isLobotomized()
                || this.tickCount % config.tickInterval() == 0 || this.isUnderWater();
    }

    @Unique
    private boolean subterra$isLobotomized() {
        // Check half as often if not lobotomized for the last 3+ consecutive checks
        if (this.tickCount % (this.subterra$notLobotomizedCount > 3 ? 600 : 300) == 0) {
            this.subterra$lobotomized = !this.getTags().contains(EntityTags.EXCLUDE_FROM_LOBOTOMIZATION) && (
                    this.isPassenger() || !this.subterra$canTravel()
            );

            if (this.subterra$lobotomized) {
                this.subterra$notLobotomizedCount = 0;
            } else {
                this.subterra$notLobotomizedCount++;
            }
        }

        return this.subterra$lobotomized;
    }

    @Unique
    private boolean subterra$canTravel() {
        // Offset Y for short blocks like dirt_path/farmland
        BlockPos center = BlockPos.containing(this.getX(), this.getY() + 0.0625D, this.getZ());
        ChunkAccess chunk = ChunkManager.getChunkNow(this.level(), center);
        if (chunk == null) {
            return false;
        }

        BlockPos.MutableBlockPos mutable = center.mutable();
        boolean canJump = !this.subterra$hasCollisionAt(chunk, mutable.move(Direction.UP, 2));

        for (Direction direction : Direction.Plane.HORIZONTAL) {
            if (this.subterra$canTravelTo(mutable.setWithOffset(center, direction), canJump)) {
                return true;
            }
        }
        return false;
    }

    @Unique
    private boolean subterra$canTravelTo(BlockPos.MutableBlockPos mutable, boolean canJump) {
        ChunkAccess chunk = ChunkManager.getChunkNow(this.level(), mutable);
        if (chunk == null) {
            return false;
        }

        BlockState bottomState = chunk.getBlockState(mutable);
        Block bottom = bottomState.getBlock();
        if (bottom instanceof BedBlock) {
            // Allows iron farms to function normally
            return true;
        }

        boolean bottomHasCollision = !bottomState.getCollisionShape(chunk, mutable.immutable()).isEmpty();
        if (this.subterra$hasCollisionAt(chunk, mutable.move(Direction.UP))) {
            // Early return if the top block has collision.
            return false;
        }

        // The villager can only jump if:
        // - There is no collision above the villager
        // - There is no collision above the top block
        // - The bottom block is short enough to jump on
        boolean isTallBlock = bottom instanceof FenceBlock || bottom instanceof FenceGateBlock || bottom instanceof WallBlock;
        return !bottomHasCollision || (canJump && !isTallBlock && !this.subterra$hasCollisionAt(chunk, mutable.move(Direction.UP)));
    }

    @Unique
    private boolean subterra$hasCollisionAt(ChunkAccess chunk, BlockPos pos) {
        return !chunk.getBlockState(pos).getCollisionShape(chunk, pos).isEmpty();
    }
}