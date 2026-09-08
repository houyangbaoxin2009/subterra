// Clean-room re-key of the ServerCore "enforce-mobcap" GPL face (no upstream
// code): shared gate for the special-spawn-source shells. Lives in a .shell
// sub-package so the pure core's package export stays unique per jar module.
package io.toterra.subterra.optim.entity.spawning.shell;

import io.toterra.subterra.engine.optim.entity.spawning.SpawnEnforcement;
import io.toterra.subterra.engine.optim.entity.spawning.SpawnEnforcement.Source;
import io.toterra.subterra.optim.entity.spawning.mixin.ServerChunkCacheAccessor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.NaturalSpawner;

/**
 * Shared gate used by the special-spawn-source enforcement shells
 * (spawner / portal piglin / infested / zombie reinforcement): resolves the
 * current switch (per-source core default, currently enforcement off — vanilla
 * bypass preserved; a td switch will drive it later), reads the category count
 * from the last spawn state and consults the pure core predicate.
 */
public final class SpawnEnforcementShell {

    /** Approximate MONSTER category baseline (vanilla default is per-chunk). */
    public static final int CATEGORY_CAP = 70;

    private SpawnEnforcementShell() {
    }

    /** True when the spawn should proceed (enforcement off → always true). */
    public static boolean allowed(ServerLevel level, Source source, MobCategory category) {
        if (!source.enforce()) {
            return true;
        }
        NaturalSpawner.SpawnState state = ((ServerChunkCacheAccessor) level.getChunkSource()).subterra$getLastSpawnState();
        int count = state == null ? 0 : state.getMobCategoryCounts().getOrDefault(category, 0);
        return SpawnEnforcement.allowed(source, count, CATEGORY_CAP);
    }

    /** Category of the spawn entity type (fallback MONSTER). */
    public static MobCategory categoryOf(EntityType<?> type) {
        return type != null ? type.getCategory() : MobCategory.MONSTER;
    }
}