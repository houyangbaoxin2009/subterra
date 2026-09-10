package io.toterra.subterra.runtime.cfglog;

import com.mojang.logging.LogUtils;
import io.toterra.subterra.engine.config.rules.RuleKey;
import io.toterra.subterra.engine.config.rules.RuleReloader;
import io.toterra.subterra.engine.config.rules.RuleReloader.ReloadResult;
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
 * p.2.19.3 — td 配置热重载壳（cfglog 迁入的一翼）：把 p.2.17 双层配置 + 热重载核心
 * （{@code engine.config.rules}：{@link RuleStore} / {@link RuleReloader}）收编为 MC 壳内的
 * 确定性装载验证。{@link #bootstrap()} 注册 {@code ServerStartedEvent} 门控；门控
 * {@code subterra.probe.cfglog}（经 gradle -P → runServer system property 转发，规避 forked
 * server JVM 的 stdin 双层中转）非 null 才跑——缺省纯 no-op 壳，对启动生命周期零影响。
 * <p>
 * 确定性样例装载：与 p.2.17.4 {@code RulesRuntime} 的既有装载路径同模式（硬编码全局档 + 覆盖档，
 * 经 {@link RuleStore} 装载语义首现胜出），独立使用 {@code subterra.cfglog.*} 命名空间、互不干扰；
 * 不重复 RulesRuntime 的规格集，也不做导出（p.2.18.3 {@code ConfigExporter}/ConfigPack 是导出面，
 * 本壳只验证装载）。固定样例流程（固定序、禁时序、禁 sleep）：
 * <ol>
 *   <li>全局档重载 {@code reloader.reloadGlobalText("", GLOBAL_TD)}（apply，rev=1）；</li>
 *   <li>覆盖档重载 {@code reloader.reloadOverridesText("", OVERRIDES_TD_1)}（apply，rev=2）——
 *       覆盖档对冲突 key 全部胜出（{@code seed_offset} 语义同 {@link RuleStore#resolve}）；</li>
 *   <li>幂等重放：同一覆盖档逐字节重复送达 → {@code unchanged}（确定性幂等，rev 不变）；</li>
 *   <li>变更重载：新覆盖档逐字节不同 → {@code applied}（rev=3；store 因首现胜出保持已装载值，
 *       证明重载不覆盖、不删除先前装载的规则）。</li>
 * </ol>
 * 全部通过打 {@code [Subterra cfglog] config ok (rules=N, render=&lt;render&gt;, rev=M)}；
 * 装载违约/程序错误（样例合法，正常不可达）打 {@code config mismatch (error=...)} marker。
 * <p>
 * p.2.19.3 — the td config hot-reload shell (one wing of the cfglog fold-in): folds the p.2.17
 * two-tier config + hot-reload core ({@code engine.config.rules}: {@link RuleStore} /
 * {@link RuleReloader}) into a deterministic load-verification shell inside the MC shell.
 * {@link #bootstrap()} registers the {@code ServerStartedEvent} gate; gated by
 * {@code subterra.probe.cfglog} (forwarded gradle -P → runServer system property, avoiding the
 * double stdin hop through the forked server JVM), runs only when non-null — a pure no-op shell by
 * default, zero impact on the boot lifecycle.
 * <p>
 * Deterministic sample load: same pattern as the existing p.2.17.4 {@code RulesRuntime} load path
 * (hard-coded global + overrides layers, first-occurrence-wins via the {@link RuleStore} load
 * semantics), using its own {@code subterra.cfglog.*} namespace so the two never interfere; no spec
 * duplication of RulesRuntime, no export (the p.2.18.3 {@code ConfigExporter}/ConfigPack is the
 * export surface — this shell only verifies loading). The fixed sample flow (fixed order, no
 * timing, no sleeps):
 * <ol>
 *   <li>global reload {@code reloader.reloadGlobalText("", GLOBAL_TD)} (applied, rev=1);</li>
 *   <li>overrides reload {@code reloader.reloadOverridesText("", OVERRIDES_TD_1)} (applied,
 *       rev=2) — the overrides layer wins every conflicting key ({@link RuleStore#resolve}
 *       semantics);</li>
 *   <li>idempotent replay: the same overrides document delivered byte-identically again →
 *       {@code unchanged} (deterministic idempotency, rev untouched);</li>
 *   <li>changed reload: a byte-different new overrides document → {@code applied} (rev=3; the
 *       store keeps the already-loaded value via first-occurrence-wins, proving a reload never
 *       overwrites nor removes previously loaded rules).</li>
 * </ol>
 * On success it prints {@code [Subterra cfglog] config ok (rules=N, render=&lt;render&gt;, rev=M)};
 * a load violation / program error (the samples are legal, so normally unreachable) prints a
 * {@code config mismatch (error=...)} marker instead.
 */
public final class ConfigRuntime {

    /** 探针 marker 前缀 / probe marker prefix. */
    public static final String MARKER = "[Subterra cfglog]";

    public static final Logger LOGGER = LogUtils.getLogger();

    /** 固定示例规格集（固定注册序，全带 default，可装载）。The fixed sample spec set (fixed
     * registration order, all with defaults, loadable). */
    private static final List<RuleSpec> FIXED_SPECS = List.of(
            new RuleSpec(new RuleKey("subterra.cfglog.hotload.enabled"), RuleType.BOOLEAN, "true",
                    "master switch for the td hot-reload gate"),
            new RuleSpec(new RuleKey("subterra.cfglog.hotload.interval"), RuleType.INT, "60",
                    "hot-reload poll interval in seconds"),
            new RuleSpec(new RuleKey("subterra.cfglog.hotload.mode"), RuleType.STRING, "replace",
                    "hot-reload application mode"),
            new RuleSpec(new RuleKey("subterra.cfglog.hotload.verbose"), RuleType.BOOLEAN, "false",
                    "log every reload attempt"));

    /** 确定性 E2E 全局档样例：覆盖前值 {@code interval=120}。Deterministic E2E global-layer sample:
     * the pre-override {@code interval=120}. */
    private static final String GLOBAL_TD =
            "type tie<data>\n[ rules = [ [ k = \"subterra.cfglog.hotload.enabled\", v = \"true\" ], "
                    + "[ k = \"subterra.cfglog.hotload.interval\", v = \"120\" ], "
                    + "[ k = \"subterra.cfglog.hotload.mode\", v = \"replace\" ], "
                    + "[ k = \"subterra.cfglog.hotload.verbose\", v = \"false\" ] ] ]\n";

    /** 确定性 E2E 覆盖档样例：胜出值 {@code interval=180}。Deterministic E2E overrides-layer sample:
     * the winning {@code interval=180}. */
    private static final String OVERRIDES_TD_1 =
            "type tie<data>\n[ rules = [ [ k = \"subterra.cfglog.hotload.interval\", v = \"180\" ] ] ]\n";

    /** 确定性 E2E 变更覆盖档样例：{@code interval=240}——与上一档逐字节不同（changed/applied），但首现
     * 胜出令 store 保持 180。Deterministic E2E changed-overrides sample: {@code interval=240} —
     * byte-different from the previous one (changed/applied), yet first-occurrence-wins keeps 180
     * in the store. */
    private static final String OVERRIDES_TD_2 =
            "type tie<data>\n[ rules = [ [ k = \"subterra.cfglog.hotload.interval\", v = \"240\" ] ] ]\n";

    private ConfigRuntime() {
    }

    /** 注册 NeoForge 生命周期监听（mod 构造调用）。Registers the NeoForge lifecycle listeners (call
     * from the mod constructor). */
    public static void bootstrap() {
        NeoForge.EVENT_BUS.register(ConfigRuntime.class);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onServerStarted(ServerStartedEvent event) {
        // p.2.19.3 deterministic E2E hook: run the two-tier sample load + RuleReloader hot-reload
        // core at startup when a probe flag is forwarded (subterra.probe.cfglog) — mirrors the
        // other probe-shell gates, so no console command round-trips through the gradle-forked
        // server JVM stdin. Default no-op.
        String probe = System.getProperty("subterra.probe.cfglog");
        if (probe == null || probe.isBlank()) {
            return;
        }
        try {
            RuleStore store = new RuleStore(FIXED_SPECS);
            RuleReloader reloader = new RuleReloader(store);
            ReloadResult global = reloader.reloadGlobalText("", GLOBAL_TD);
            ReloadResult overrides = reloader.reloadOverridesText("", OVERRIDES_TD_1);
            ReloadResult idempotent = reloader.reloadOverridesText(OVERRIDES_TD_1, OVERRIDES_TD_1);
            ReloadResult changed = reloader.reloadOverridesText(OVERRIDES_TD_1, OVERRIDES_TD_2);
            int rules = store.resolve().size();
            String render = store.render();
            LOGGER.info("{} config ok (rules={}, render={}, rev={})", MARKER, rules, render,
                    reloader.revision());
        } catch (RuntimeException e) {
            // the samples are legal, so a mismatch is a program error — never emit a false ok.
            LOGGER.warn("{} config mismatch (error={})", MARKER, e.getMessage());
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        // no held state -> pure no-op; symmetric empty hook kept (as RulesRuntime/DatapackRuntime).
    }
}
