package io.toterra.subterra.runtime.hub;

import com.mojang.logging.LogUtils;
import io.toterra.subterra.engine.config.rules.RuleKey;
import io.toterra.subterra.engine.config.rules.RuleReloader;
import io.toterra.subterra.engine.config.rules.RuleSpec;
import io.toterra.subterra.engine.config.rules.RuleStore;
import io.toterra.subterra.engine.config.rules.RuleType;
import io.toterra.subterra.engine.hub.FormRenderData;
import io.toterra.subterra.engine.hub.FormStructure;
import io.toterra.subterra.engine.hub.HubConfigEditor;
import io.toterra.subterra.engine.hub.HubConfigReloader;
import io.toterra.subterra.engine.hub.HubFormGen;
import io.toterra.subterra.engine.schema.SchemaViolationException;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * p.2.23.3 — hub runtime 壳：把 p.2.23 的 engine.hub 数据面核心（p.2.23.1 表单自动生成
 * {@link HubFormGen} / {@link FormStructure}，p.2.23.2 配置编辑 {@link HubConfigEditor} 与热重载
 * {@link HubConfigReloader}）收编进 boot 生命周期的确定性核对。{@link #bootstrap()}（
 * {@code Subterra.java} 构造调用）注册 {@code ServerStartedEvent} 门控与 {@code /subterra hub}
 * 命令（{@link HubCommand#onRegisterCommands}，与 export / rule 同根合并并存）；门控
 * {@code subterra.probe.hub}（经 gradle -P → runServer system property 转发，与其余探针壳同模式）
 * 非 null 才跑——缺省纯 no-op 壳，对启动生命周期零影响。
 * <p>
 * 确定性样例消费（固定序、禁时序、禁 sleep），全部经 {@link HubCommand#runVerb} 共享确定性核心
 * （命令与启动钩子同输出）：固定定型 td schema → {@code HubFormGen.fromSchemaTd} 得表单结构 →
 * {@code form} marker；固定样例键值 → {@code HubConfigEditor} 双层写（{@code set} 全局档 +
 * {@code override} 存档覆盖档）→ {@code set/override} marker；随后 {@code view} 打有效规则
 * render。热重载复验（{@code HubConfigReloader} 委托 {@link RuleStore}/{@link RuleReloader} 的
 * 四步确定性契约）：固定旧/新表单值对 → global applied + 幂等重投 unchanged + overrides applied +
 * reloader 版本单调（rev=2）；连同表单 render 与编辑器 render 的二次运行逐字节复验（确定性证明）。
 * 全部通过打 {@code [Subterra hub] ok (fields=N, rules=<render>, rev=<revision>, verify=ok)}
 * （fields = 样例表单字段数；rules = {@code HubConfigEditor.render} 双层解析后的有效规则；
 * rev = 编辑器单调版本（两次实际编辑 = 2）；verify = 热重载确定性复验）；违约/程序错误
 * （样例合法，正常不可达）打 {@code [Subterra hub] mismatch (…)} marker，绝不打假 ok。
 * <p>
 * <b>玩家侧配置入口接线面（p.2.23 后接线点，本子项只做管理侧门控确定性核对）</b>：
 * engine.hub 是纯 JDK 确定性配置/表单数据面，真实玩家侧表单 UI 接线留后续（避免范围膨胀）。
 * 玩家侧交互走 p.2.14（engine.interact，零 HUD 规则）；面板仅管理视图（承接 p.2.22 runtime.ui
 * 门控壳范式）。本壳持有当前服务器的 {@link HubConfigEditor} 与 {@link HubConfigReloader}——
 * 后续接线点为：p.2.22 的 {@code UiRuntime} 管理面板（mod hub 面板视图）打开表单 → 表单值
 * 提交经 {@code HubConfigEditor.editGlobal/editOverrides} 双层写（本命令 set/override 即该写面的
 * 命令形态）；服务端配置热重载经 {@code HubConfigReloader.reloadGlobal/reloadOverrides}（表单值
 * 形态）接入外部配置源变更。本子项只做管理侧门控确定性核对，不引玩家 UI 注入。
 * <p>
 * p.2.23.3 — the hub runtime shell: folds the p.2.23 engine.hub data-plane cores (the p.2.23.1
 * form auto-generation {@link HubFormGen} / {@link FormStructure}, and the p.2.23.2 config-edit
 * {@link HubConfigEditor} and hot-reload {@link HubConfigReloader}) into the boot lifecycle as a
 * deterministic verification. {@link #bootstrap()} (called from the {@code Subterra.java}
 * constructor) registers the {@code ServerStartedEvent} gate and the {@code /subterra hub} command
 * ({@link HubCommand#onRegisterCommands}, merged with the export / rule commands under the same
 * root); gated by {@code subterra.probe.hub} (forwarded gradle -P → runServer system property, same
 * pattern as the other probe shells), runs only when non-null — a pure no-op shell by default, zero
 * impact on the boot lifecycle.
 * <p>
 * Deterministic sample consumption (fixed order, no timing, no sleeps), all through the shared
 * deterministic core {@link HubCommand#runVerb} (identical output from the command and the startup
 * hook): a fixed settled td schema → {@code HubFormGen.fromSchemaTd} yields the form structure →
 * the {@code form} marker; fixed sample key/values → two-tier writes via {@code HubConfigEditor}
 * ({@code set} to the global layer + {@code override} to the save-overrides layer) → the
 * {@code set/override} markers; then {@code view} prints the effective rules render. The hot-reload
 * re-verification ({@code HubConfigReloader} delegating the four-step deterministic contract of
 * {@link RuleStore}/{@link RuleReloader}): fixed old/new form-value pairs → global applied +
 * idempotent re-delivery unchanged + overrides applied + reloader revision monotonic (rev=2); plus
 * a twice-run byte-identity re-check of the form render and the editor render (the determinism
 * proof). On full success it prints
 * {@code [Subterra hub] ok (fields=N, rules=<render>, rev=<revision>, verify=ok)} (fields = the
 * sample form field count; rules = the effective rules after the two-tier resolve via
 * {@code HubConfigEditor.render}; rev = the editor's monotonic revision (two actual edits = 2);
 * verify = the hot-reload determinism re-check); a violation / program error (the samples are
 * legal, so normally unreachable) prints a {@code [Subterra hub] mismatch (…)} marker instead — a
 * false ok is never emitted.
 * <p>
 * <b>Player-side config entry wiring surface (post-p.2.23 wiring point; this sub-item only does
 * the management-side gated deterministic check)</b>: engine.hub is the pure-JDK deterministic
 * config/form data plane, and the real player-side form UI wiring lands later (scope containment).
 * Player-side interaction goes through p.2.14 (engine.interact, zero-HUD rule); the panel is a
 * management view only (carrying over the p.2.22 runtime.ui gated-shell pattern). This shell holds
 * the current server's {@link HubConfigEditor} and {@link HubConfigReloader} — the future wiring
 * points are: the p.2.22 {@code UiRuntime} management panel (the mod-hub panel view) opens a form →
 * the submitted form values write the two tiers via
 * {@code HubConfigEditor.editGlobal/editOverrides} (this command's set/override are the command
 * shape of that write surface); server-side config hot reload consumes external config-source
 * changes via {@code HubConfigReloader.reloadGlobal/reloadOverrides} (form-value shape). This
 * sub-item only does the management-side gated deterministic check, with no player-UI injection.
 */
public final class HubRuntime {

    /** 探针 marker 前缀 / probe marker prefix. */
    public static final String MARKER = "[Subterra hub]";

    public static final Logger LOGGER = LogUtils.getLogger();

    /** 固定样例表单 id。The fixed sample form id. */
    public static final String SAMPLE_FORM_ID = "world";

    /** 固定定型 td schema（TABLE 根，三字段，固定文档序）→ {@code HubFormGen.fromSchemaTd}。
     * The fixed settled td schema (TABLE root, three fields, fixed document order) →
     * {@code HubFormGen.fromSchemaTd}. */
    private static final String SAMPLE_SCHEMA_TD =
            "[\n"
            + "  root = \"table\",\n"
            + "  fields = [\n"
            + "    seed_offset = \"int\",\n"
            + "    activation_range = \"float\",\n"
            + "    weather = \"string\",\n"
            + "  ],\n"
            + "]";

    /** 固定示例规格集（固定注册序，全带 default，可装载）——编辑面与热重载面共用，与 p.2.17.4
     * 样例同源风格。The fixed sample spec set (fixed registration order, all with defaults,
     * loadable) — shared by the edit surface and the hot-reload surface, same source style as the
     * p.2.17.4 samples. */
    private static final List<RuleSpec> FIXED_SPECS = List.of(
            new RuleSpec(new RuleKey("subterra.worldgen.seed_offset"), RuleType.INT, "0",
                    "world-seed offset applied by the subterra worldgen preset"),
            new RuleSpec(new RuleKey("subterra.entity.activation.range"), RuleType.FLOAT, "32.0",
                    "entity activation range in blocks"),
            new RuleSpec(new RuleKey("subterra.render.weather"), RuleType.STRING, "clear",
                    "weather override rendered into the world"));

    /** 确定性样例双层写：set 全局档值（覆盖前）。Deterministic sample two-tier write: the set
     * global-layer value (pre-override). */
    private static final String SAMPLE_SET_KEY = "subterra.worldgen.seed_offset";
    private static final String SAMPLE_SET_VALUE = "7";

    /** 确定性样例双层写：override 存档覆盖档值（胜出）。Deterministic sample two-tier write: the
     * override save-layer value (the winner). */
    private static final String SAMPLE_OVERRIDE_KEY = "subterra.worldgen.seed_offset";
    private static final String SAMPLE_OVERRIDE_VALUE = "42";

    /** 热重载旧/新表单值样例（global 档：seed_offset 7 → 42）。Hot-reload old/new form-value
     * samples (global layer: seed_offset 7 → 42). */
    private static final Map<String, String> OLD_GLOBAL = Map.of(SAMPLE_SET_KEY, "7");
    private static final Map<String, String> NEW_GLOBAL = Map.of(SAMPLE_SET_KEY, "42");

    /** 热重载旧/新表单值样例（overrides 档：activation range 32.0 → 64.0）。Hot-reload old/new
     * form-value samples (overrides layer: activation range 32.0 → 64.0). */
    private static final Map<String, String> OLD_OVERRIDES =
            Map.of("subterra.entity.activation.range", "32.0");
    private static final Map<String, String> NEW_OVERRIDES =
            Map.of("subterra.entity.activation.range", "64.0");

    /** 当前服务器的 hub runtime（{@code /subterra hub} 命令入口），ServerStopping 置空。
     * The active hub runtime for the current server (the {@code /subterra hub} command entry
     * point), nulled on ServerStopping. */
    private static HubRuntime active;

    /** 样例表单集（id → 表单结构）。The sample form set (id → form structure). */
    private final Map<String, FormStructure> forms;
    /** 双层配置编辑面（{@code set/override/view} 命令入口）。The two-tier config-edit surface
     * (the {@code set/override/view} command entry). */
    private final HubConfigEditor editor;
    /** 热重载门面（委托 {@link RuleStore}/{@link RuleReloader}）。The hot-reload facade
     * (delegating to {@link RuleStore}/{@link RuleReloader}). */
    private final HubConfigReloader reloader;

    private HubRuntime() {
        FormStructure world = formStructure();
        this.forms = Map.of(SAMPLE_FORM_ID, world);
        RuleStore ruleStore = new RuleStore(FIXED_SPECS);
        this.editor = new HubConfigEditor(FIXED_SPECS);
        this.reloader = new HubConfigReloader(ruleStore);
    }

    /** 注册 NeoForge 生命周期监听 + {@code /subterra hub} 命令（mod 构造调用）。
     * Registers the NeoForge lifecycle listeners and the {@code /subterra hub} command (call from
     * the mod constructor). */
    public static void bootstrap() {
        NeoForge.EVENT_BUS.register(HubRuntime.class);
        NeoForge.EVENT_BUS.addListener(HubCommand::onRegisterCommands);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onServerStarted(ServerStartedEvent event) {
        HubRuntime fresh = new HubRuntime();
        // p.2.23.3 deterministic E2E hook: consume the engine.hub data plane at startup when a
        // probe flag is forwarded (subterra.probe.hub) — mirrors the other probe-shell gates, so
        // no console command round-trips through the gradle-forked server JVM stdin. Default no-op.
        String probe = System.getProperty("subterra.probe.hub");
        if (probe != null && !probe.isBlank()) {
            try {
                for (String m : runProbe(fresh)) {
                    LOGGER.info("{}", m);
                }
            } catch (Throwable t) {
                // the samples are legal, so a mismatch is a program error — never emit a false ok.
                LOGGER.error("{} hub mismatch (error={})", MARKER, t.toString());
            }
        }
        active = fresh;
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        active = null;
    }

    /** 当前服务器的 hub runtime（命令入口）。The active hub runtime for the current server
     * (command entry point). */
    public static HubRuntime active() {
        return active;
    }

    /** 样例表单集（id → 表单结构，只读）。The sample form set (id → form structure, read-only). */
    public Map<String, FormStructure> forms() {
        return forms;
    }

    /** 双层配置编辑面（命令 set/override/view 入口）。The two-tier config-edit surface (the
     * command set/override/view entry). */
    public HubConfigEditor editor() {
        return editor;
    }

    /** 热重载门面（启动钩子确定性复验入口）。The hot-reload facade (the startup-hook
     * deterministic re-verification entry). */
    public HubConfigReloader reloader() {
        return reloader;
    }

    /**
     * 固定样例确定性场景（固定序、禁时序、禁 sleep），全部经 {@link HubCommand#runVerb} 共享核心：
     * {@code form}（表单结构 marker）→ {@code set}（全局档写）→ {@code override}（覆盖档写）→
     * {@code view}（有效规则 render）；随后热重载确定性复验（global applied + 幂等重投 unchanged +
     * overrides applied + reloader 版本单调）+ 表单/编辑器 render 二次运行逐字节复验。全部通过打
     * 汇总 {@code [Subterra hub] ok (fields=N, rules=<render>, rev=<revision>, verify=ok)}，
     * 否则 {@code [Subterra hub] mismatch (verify failed)}。命令层与启动钩子共享同一
     * {@code runVerb} 核心，输出逐字符一致。
     * <p>
     * The fixed-sample deterministic scenario (fixed order, no timing, no sleeps), all through the
     * shared {@link HubCommand#runVerb} core: {@code form} (form-structure marker) → {@code set}
     * (global-layer write) → {@code override} (overrides-layer write) → {@code view} (effective
     * rules render); then the hot-reload determinism re-check (global applied + idempotent
     * re-delivery unchanged + overrides applied + reloader revision monotonic) plus a twice-run
     * byte-identity re-check of the form/editor renders. On full success it emits the aggregate
     * {@code [Subterra hub] ok (fields=N, rules=<render>, rev=<revision>, verify=ok)}, otherwise
     * {@code [Subterra hub] mismatch (verify failed)}. The command layer and the startup hook share
     * the same {@code runVerb} core, so their output is character-for-character identical.
     */
    static List<String> runProbe(HubRuntime rt) {
        List<String> out = new ArrayList<>();
        out.addAll(HubCommand.runVerb(rt, "form", SAMPLE_FORM_ID, null));
        out.addAll(HubCommand.runVerb(rt, "set", SAMPLE_SET_KEY, SAMPLE_SET_VALUE));
        out.addAll(HubCommand.runVerb(rt, "override", SAMPLE_OVERRIDE_KEY, SAMPLE_OVERRIDE_VALUE));
        out.addAll(HubCommand.runVerb(rt, "view", null, null));
        if (!verifyReload(rt)) {
            out.add(MARKER + " mismatch (verify failed)");
            return out;
        }
        String render = rt.editor.render();
        if (!render.equals(rt.editor.render())) {
            out.add(MARKER + " mismatch (render not deterministic)");
            return out;
        }
        out.add(MARKER + " ok (fields=" + rt.forms.get(SAMPLE_FORM_ID).fields().size()
                + ", rules=" + render
                + ", rev=" + rt.editor.revision()
                + ", verify=ok)");
        return out;
    }

    /**
     * 热重载确定性复验：{@code HubConfigReloader.reloadGlobal} 固定旧/新表单值对 → applied；同对
     * 重投 → 幂等 unchanged（同目标重复送达不重复生效）；{@code reloadOverrides} → applied；
     * reloader 版本单调（两次实际 applied = 2）。另以表单 render 二次运行逐字节复验（确定性证明）。
     * <p>
     * Hot-reload determinism re-check: {@code HubConfigReloader.reloadGlobal} on the fixed
     * old/new form-value pair → applied; the same pair re-delivered → idempotent unchanged (the
     * same target delivered twice never takes effect twice); {@code reloadOverrides} → applied;
     * the reloader revision is monotonic (two actual applied = 2). A twice-run byte-identity
     * re-check of the form render is included (the determinism proof).
     */
    private static boolean verifyReload(HubRuntime rt) {
        RuleReloader.ReloadResult global = rt.reloader.reloadGlobal(OLD_GLOBAL, NEW_GLOBAL);
        boolean idempotent = !rt.reloader.reloadGlobal(OLD_GLOBAL, NEW_GLOBAL).applied();
        RuleReloader.ReloadResult overrides = rt.reloader.reloadOverrides(OLD_OVERRIDES, NEW_OVERRIDES);
        String formRender = FormRenderData.render(rt.forms.get(SAMPLE_FORM_ID));
        return global.applied() && idempotent && overrides.applied()
                && rt.reloader.revision() == 2L
                && formRender.equals(FormRenderData.render(rt.forms.get(SAMPLE_FORM_ID)));
    }

    /**
     * 固定样例表单结构（{@code HubFormGen.fromSchemaTd}）；同文本两次生成结构恒等（确定性证明）。
     * 样例合法——文档违约仅可能来自程序错误，包为 {@link IllegalStateException}。
     * The fixed sample form structure ({@code HubFormGen.fromSchemaTd}); two generations from the
     * same text are structurally identical (the determinism proof). The sample is legal — a
     * document violation can only be a program error, wrapped as {@link IllegalStateException}.
     */
    private static FormStructure formStructure() {
        try {
            FormStructure s = HubFormGen.fromSchemaTd(SAMPLE_SCHEMA_TD);
            if (!s.equals(HubFormGen.fromSchemaTd(SAMPLE_SCHEMA_TD))) {
                throw new IllegalStateException("form generation not deterministic");
            }
            return s;
        } catch (SchemaViolationException e) {
            throw new IllegalStateException("sample schema invalid: " + e.reason());
        }
    }
}
