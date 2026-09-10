package io.toterra.subterra.runtime.rules;

import com.mojang.logging.LogUtils;
import io.toterra.subterra.engine.config.rules.RuleKey;
import io.toterra.subterra.engine.config.rules.RuleSpec;
import io.toterra.subterra.engine.config.rules.RuleStore;
import io.toterra.subterra.engine.config.rules.RuleType;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import org.slf4j.Logger;

import java.util.List;

/**
 * p.2.17.4 — 配置规则 runtime 壳：静态持有当前 {@link RuleStore}（{@link #activeStore()} 为
 * {@code /subterra rule} 命令入口）。store 在 {@code ServerStartedEvent} 以固定示例规格集构造
 * （固定注册序，全部声明 default —— p.2.17.1 边界：TABLE 型无字符串 default 可携带、文档装载面
 * 不展开表值，故示例规格集刻意全部标量化）。
 * <p>
 * 确定性 E2E 钩子与 DatapackRuntime 的 export 钩子同模式：{@code subterra.probe.rule} 门控
 * （经 gradle -P → runServer system property 转发，规避 forked server JVM 的 stdin 双层中转），
 * 非 null 才跑——从硬编码确定性样例（全局档含覆盖前值 {@code seed_offset=7}、覆盖档含胜出值
 * {@code seed_offset=42}）装载到 store，然后跑与命令同一确定性核心 {@link
 * RuntimeRuleCommand#runVerb} 的 view 打 marker（禁 sleep、禁时序）。
 * <p>
 * p.2.17.4 — the config-rules runtime shell: statically holds the current {@link RuleStore}
 * ({@link #activeStore()} is the entry point for the {@code /subterra rule} command). The store is
 * built on {@code ServerStartedEvent} from a fixed sample spec set (fixed registration order, every
 * spec declaring a default — p.2.17.1 boundary: a TABLE spec cannot carry a string default and the
 * document load surface does not expand table values, so the sample set is deliberately all
 * scalars).
 * <p>
 * The deterministic E2E hook mirrors the DatapackRuntime export hook: gated by
 * {@code subterra.probe.rule} (forwarded gradle -P → runServer system property, avoiding the double
 * stdin hop through the forked server JVM), runs only when non-null — it loads the hard-coded
 * deterministic samples (global layer carries the pre-override value {@code seed_offset=7}, the
 * overrides layer carries the winning value {@code seed_offset=42}) into the store, then runs the
 * same deterministic core {@link RuntimeRuleCommand#runVerb} with the view verb to emit markers
 * (no sleeps, no timing).
 */
public final class RulesRuntime {

    public static final Logger LOGGER = LogUtils.getLogger();

    /** 固定示例规格集（固定注册序，全带 default，可装载）。The fixed sample spec set (fixed
     * registration order, all with defaults, loadable). */
    private static final List<RuleSpec> FIXED_SPECS = List.of(
            new RuleSpec(new RuleKey("subterra.worldgen.seed_offset"), RuleType.INT, "0",
                    "world-seed offset applied by the subterra worldgen preset"),
            new RuleSpec(new RuleKey("subterra.entity.activation.range"), RuleType.FLOAT, "32.0",
                    "entity activation range in blocks"),
            new RuleSpec(new RuleKey("subterra.render.weather"), RuleType.STRING, "clear",
                    "weather override rendered into the world"),
            new RuleSpec(new RuleKey("subterra.optim.enable"), RuleType.BOOLEAN, "true",
                    "master switch for the optim runtime tweaks"));

    /** 确定性 E2E 全局档样例：覆盖前值。Deterministic E2E global-layer sample: pre-override values. */
    private static final String GLOBAL_TD =
            "type tie<data>\n[ rules = [ [ k = \"subterra.worldgen.seed_offset\", v = \"7\" ], "
                    + "[ k = \"subterra.entity.activation.range\", v = \"32.0\" ], "
                    + "[ k = \"subterra.render.weather\", v = \"clear\" ], "
                    + "[ k = \"subterra.optim.enable\", v = \"true\" ] ] ]\n";

    /** 确定性 E2E 覆盖档样例：胜出值。Deterministic E2E overrides-layer sample: the winning value. */
    private static final String OVERRIDES_TD =
            "type tie<data>\n[ rules = [ [ k = \"subterra.worldgen.seed_offset\", v = \"42\" ] ] ]\n";

    private static RuleStore store;

    private RulesRuntime() {
    }

    /** 注册 NeoForge 生命周期监听（mod 构造调用）。Registers the NeoForge lifecycle listeners (call
     * from the mod constructor). */
    public static void bootstrap() {
        NeoForge.EVENT_BUS.register(RulesRuntime.class);
        NeoForge.EVENT_BUS.addListener(RuntimeRuleCommand::onRegisterCommands);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onServerStarted(ServerStartedEvent event) {
        RuleStore fresh = new RuleStore(FIXED_SPECS);
        // p.2.17.4 deterministic E2E hook: run the same view core at startup when a probe flag is
        // forwarded (subterra.probe.rule) — mirrors the DatapackRuntime export hook, so no console
        // command round-trips through the gradle-forked server JVM stdin.
        String probe = System.getProperty("subterra.probe.rule");
        if (probe != null && !probe.isBlank()) {
            fresh.loadGlobalText(GLOBAL_TD);
            fresh.loadOverridesText(OVERRIDES_TD);
            for (String m : RuntimeRuleCommand.runVerb(fresh, "view", null, null)) {
                LOGGER.info("{}", m);
            }
        }
        store = fresh;
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        store = null;
    }

    /** 当前服务器的规则存储（命令入口）。The active rule store for the current server (command entry
     * point). */
    public static RuleStore activeStore() {
        return store;
    }
}
