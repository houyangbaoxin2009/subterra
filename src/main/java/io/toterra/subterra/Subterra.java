package io.toterra.subterra;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;

import io.toterra.subterra.runtime.JvmEnv;
import io.toterra.subterra.runtime.JvmLaunchArgs;

import java.lang.management.ManagementFactory;
import java.util.List;

/**
 * Subterra — foundation mod for the Toterra series.
 * L1 launch: verifies the Java runtime (1.21.1 targets Java 21 bytecode; runs on Java 25 LTS).
 */
@Mod(Subterra.MODID)
public class Subterra {
    // Must match mod_id in gradle.properties and neoforge.mods.toml
    public static final String MODID = "subterra";
    // Directly reference a slf4j logger
    public static final Logger LOGGER = LogUtils.getLogger();

    private final ModContainer modContainer;

    public Subterra(IEventBus modEventBus, ModContainer modContainer) {
        this.modContainer = modContainer;
        modEventBus.addListener(this::commonSetup);

        // Register ourselves for server and other game events
        NeoForge.EVENT_BUS.register(this);

        // Register mod config
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);

        // Inventory Advancement Accelerator (ported, MIT): its config must be
        // registered here, in the mod constructor, so ConfigTracker sees it
        // before the config-load stage (a commonSetup registration would be too
        // late: TagsUpdated fires before the config is ever loaded and every
        // read would throw "Cannot get config value before config is loaded").
        io.toterra.subterra.optim.server.invadvopt.InventoryAdvancementAccelerator.bootstrap(modContainer);

        // Smooth Boot worker tuning (ported, MIT): preloads the td config
        // (config/subterra/smoothboot.td); the Util mixins swap the background
        // and IO worker executors lazily on first use with defaults fallback.
        io.toterra.subterra.optim.server.smoothboot.SmoothBoot.bootstrap(modContainer);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        // L1 launch check: log Java version and verification result
        JvmEnv.Report report = JvmEnv.verify();
        LOGGER.info("Subterra L1: Java {} (min supported 21, verified: {})", report.javaVersion(), report.verified());
        if (!report.verified()) {
            LOGGER.warn("Subterra L1: Java {} is below the supported baseline; launch arguments may be incomplete.", report.javaVersion());
        }

        // L1 launch check: verify the JVM argument package was injected before process start
        List<String> applied = ManagementFactory.getRuntimeMXBean().getInputArguments();
        List<String> missing = JvmLaunchArgs.missingStaticFlags(applied);
        if (!missing.isEmpty()) {
            LOGGER.warn("Subterra L1: missing JVM tuning flags: {} — inject the Subterra launch argument package before starting the game (see subterra-launch JvmLaunchArgs).", missing);
        } else {
            LOGGER.info("Subterra L1: JVM argument package present ({})", JvmLaunchArgs.staticTuningFlags().size() + " static flags");
        }

        // Logging module boot-time wiring: td config, sinks, module registry,
        // and the crash-report diagnostics callable.
        SubterraLogging.bootstrap();
    }

    // You can use SubscribeEvent and let the Event Bus discover methods to call
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        // Foundation hooks fire after server start here
    }
}