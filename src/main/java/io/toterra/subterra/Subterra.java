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
        io.toterra.subterra.runtime.optim.server.advancement.InventoryAdvancementAccelerator.bootstrap(modContainer);

        // Smooth Boot worker tuning (ported, MIT): preloads the td config
        // (config/subterra/smoothboot.td); the Util mixins swap the background
        // and IO worker executors lazily on first use with defaults fallback.
        io.toterra.subterra.runtime.optim.server.threading.WorkerPoolTuning.bootstrap(modContainer);

        // ServerCore villager lobotomization (ported, MIT): preloads the td
        // config (config/subterra/servercore.td); the AbstractVillagerMixin
        // skips a stuck villager's brain tick so path-finding CPU is saved.
        io.toterra.subterra.runtime.optim.entity.ai.VillagerLobotomize.bootstrap(modContainer);

        // ServerCore dynamic view/simulation distance (ported, MIT): preloads
        // the td config (config/subterra/servercore.td) and wires the server
        // lifecycle listeners; the DynamicManager auto-tunes view/sim distance
        // (and mobcap/chunk-tick knobs) from the measured server tick time so a
        // loaded world keeps the server thread responsive.
        io.toterra.subterra.runtime.optim.server.dynamic.DynamicDistance.bootstrap(modContainer);

        // ServerCore sync-load guard (clean-room): loads the master switch from
        // servercore.td; incremental call-site rewrites land behind it (off by
        // default preserves vanilla synchronous loading).
        io.toterra.subterra.runtime.optim.server.loading.shell.SyncLoadRuntime.bootstrap(modContainer);

        // Item/block blacklist control (ported, Apache-2.0): preloads the td
        // config (config/subterra/item_control.td) and wires the handlers,
        // /itemban commands and recipe stripper.
        io.toterra.subterra.runtime.optim.server.item_control.shell.ItemControl.bootstrap(modContainer);

        // Worldgen (p.1.8.21, td-gated): registers the subterra:density
        // density-function type and captures the world seed so "Subterra" can be
        // selected as a world generator. Zero effect while the preset is unused.
        io.toterra.subterra.runtime.worldgen.gen.SubterraWorldgen.bootstrap(modEventBus);

        // Worldgen option gate (p.1.8.22): loads config/subterra/worldgen.td and
        // logs whether the Subterra generator is the default — OFF unless enabled
        // via config ([ use_subterra_generator = true ]) or API. No boot impact.
        io.toterra.subterra.runtime.worldgen.gen.WorldgenConfig.bootstrap();

        // Datapack runtime (p.2.2): wires the td datapack loader into the server
        // lifecycle (ServerStartedEvent); registration lands in the registrar.
        io.toterra.subterra.runtime.datapack.DatapackRuntime.bootstrap();

        // Rules runtime (p.2.17.4): /subterra rule command tree + the rule store
        // shell (ServerStartedEvent); the deterministic E2E view hook is gated by
        // subterra.probe.rule, mirroring the datapack export probe channel.
        io.toterra.subterra.runtime.rules.RulesRuntime.bootstrap();

        // Export hub runtime (p.2.18.5): /subterra export <form> [<path>] branch over
        // engine.export ExportHub + the deterministic all-forms E2E hook gated by
        // subterra.probe.exportHub (coexists with the p.2.2.7 export [path] command).
        io.toterra.subterra.runtime.export.ExportHubRuntime.bootstrap();
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        // Launch runtime (p.2.19.2): folds the L1 JVM-env + JVM-args read-only checks into a
        // deterministic health report (same L1 log text, so the ServerBootProbe assertions stand)
        // and registers the subterra.probe.launch ServerStarted gate (default no-op).
        io.toterra.subterra.runtime.launch.LaunchRuntime.bootstrap();

        // Fix runtime (p.2.19.2): folds the Java25Gaps registry into the boot lifecycle via the
        // subterra.probe.fix ServerStarted gate (read-only consumer; default no-op).
        io.toterra.subterra.runtime.fix.FixRuntime.bootstrap();

        // Logging module boot-time wiring: td config, sinks, module registry,
        // and the crash-report diagnostics callable.
        SubterraLogging.bootstrap();

        // Config runtime (p.2.19.3): td two-tier sample load + RuleStore/RuleReloader hot-reload
        // gate (subterra.probe.cfglog; default no-op).
        io.toterra.subterra.runtime.cfglog.ConfigRuntime.bootstrap();

        // Log runtime (p.2.19.3): engine.log MC-shell load verification + deterministic gate
        // marker (same subterra.probe.cfglog property; default no-op).
        io.toterra.subterra.runtime.cfglog.LogRuntime.bootstrap();

        // C2ME coexistence (MIT, supported compatible peer): detects the official
        // C2ME jar (ModList is queryable only after mod loading) and logs coexistence
        // guidance on the shared control surfaces; Subterra bundles its own derived
        // async chunk engine (engine.worldgen.async, MIT-attributed in NOTICE). Optional.
        io.toterra.subterra.runtime.worldgen.async.C2meCoexistence.bootstrap(modContainer);
    }

    // You can use SubscribeEvent and let the Event Bus discover methods to call
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        // Foundation hooks fire after server start here
    }
}