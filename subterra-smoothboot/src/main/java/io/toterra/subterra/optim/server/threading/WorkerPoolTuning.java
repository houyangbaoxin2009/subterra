// Ported from mc-smoothboot (github.com/UltimateBoomer/mc-smoothboot), MIT (c) UltimateBoomer.
package io.toterra.subterra.optim.server.threading;

import com.mojang.logging.LogUtils;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;

/**
 * Subterra-internal entry point for the Smooth Boot worker tuning.
 * Not a {@code @Mod}: Subterra's own mod entry registers this via
 * {@link #bootstrap(ModContainer)} in the mod constructor, keeping the
 * tuning an internal capability of the Subterra mod jar.
 *
 * <p>The Util mixins swap Minecraft's background/IO worker executors for pools
 * with td-configured thread counts and priorities. They run before commonSetup
 * (the first {@code Util.backgroundExecutor()/ioPool()} call happens very
 * early), so the config is loaded lazily on first use ({@link #config()}) with
 * defaults on any error or absence; {@code bootstrap} only preloads it.</p>
 */
public final class WorkerPoolTuning {
    public static final String MOD_ID = "subterra_smoothboot";
    public static final String NAME = "Smooth Boot";

    public static final Logger LOGGER = LogUtils.getLogger();

    /** Init guards, mirroring upstream (benign races: the swaps are idempotent). */
    public static boolean initConfig = false;
    public static boolean initMainWorker = false;
    public static boolean initIOWorker = false;

    private static volatile WorkerPoolTuningConfig config;

    private WorkerPoolTuning() {
    }

    /**
     * Preloads the td config so a broken file surfaces at mod construction
     * time; the lazy loader remains the source of truth for the mixins.
     */
    public static void bootstrap(ModContainer container) {
        LOGGER.info("{} config initialized: {}", NAME, config());
    }

    /**
     * Lazily-initialized config (double-checked). Called from the mixins, which
     * may run before the mod constructor / commonSetup.
     */
    public static WorkerPoolTuningConfig config() {
        WorkerPoolTuningConfig result = config;
        if (result == null) {
            synchronized (WorkerPoolTuning.class) {
                result = config;
                if (result == null) {
                    result = WorkerPoolTuningConfig.load(FMLPaths.GAMEDIR.get());
                    config = result;
                }
            }
        }
        return result;
    }
}
