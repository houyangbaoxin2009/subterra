package io.toterra.subterra.runtime.worldgen.gen;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.SurfaceRules;

import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

import io.toterra.subterra.Subterra;
import io.toterra.subterra.engine.config.rules.RuleStore;
import io.toterra.subterra.engine.worldgen.assembly.DensityAssembly;
import io.toterra.subterra.engine.worldgen.pipeline.surfacerules.SurfacePaletteResolution;
import io.toterra.subterra.runtime.rules.RulesRuntime;

/**
 * p.2.29.2 规则→表面规则的<b>真接缝</b>（runtime 装配面）：把引擎侧解析出的表面材质面
 * （{@link SurfacePaletteResolution}，规则键 {@code subterra.worldgen.surface_palette[.<state>]}）
 * 真正接入 MC 区块表面生成路径。
 * <p>
 * 实现方式与 p.2.29.1 的 {@code subterra:density} 完全同构——<b>注册一个自定义 surface rule-source
 * 类型</b> {@code subterra:surface_palette} 到 {@code Registries.MATERIAL_RULE}
 * （{@link SurfaceRules.RuleSource#CODEC} 就是经 {@code BuiltInRegistries.MATERIAL_RULE} 按
 * {@code "type"} 派发的），并由随模组发布的 {@code data/subterra/worldgen/noise_settings/
 * subterra_overworld.json} 的 {@code surface_rule} 顶层引用它（{@code base} 字段包裹原版规则树）。
 * 于是「Subterra」世界预设被选中时，规则会经由表面规则求值路径逐块作用到真实区块表面。
 * <p>
 * <b>缺省恒等（默认预设零变化）</b>：缺省 palette 为 {@code vanilla}，此时 rule-source 直接把 MC
 * 的 {@code base} 规则源原样委派（返回 {@code base.apply(ctx)} 的同一对象，零逐块开销）——表面生成
 * 逐位不变；且默认预设（{@code minecraft:normal}）根本不引用 {@code subterra:subterra_overworld}，
 * 故完全不受影响。仅当 palette 显式为 {@code custom} 且带重绑表时，才对 {@code base} 求值得到的
 * {@link BlockState} 做「状态→方块」确定性重绑（材质面替换）。
 * <p>
 * 确定性：{@link #activeResolution()} 是 volatile 不可变快照（{@code ServerStarted} 一次性安装，
 * 同输入同结果）；有效规则表优先于启动属性优先于缺省（固定优先级）；非法值确定性拒绝——引擎侧
 * {@link SurfacePaletteResolution#fromRules} 抛 {@link IllegalArgumentException}，runtime 侧确定性
 * 落回恒等并打确定性 warn marker（不破坏服务端启动、逐位不变）。不私接 mixin：这是注册面 + 数据包
 * 引用，模组只消费契约。
 * <p>
 * 生命周期：{@link #bootstrap(IEventBus)}（mod 构造，经 {@code Subterra} 在
 * {@code RulesRuntime.bootstrap()} 之后调用 → ServerStarted 监听器在同优先级下后触发，故能实读活跃
 * {@link RuleStore}）。marker 前缀 {@code [Subterra surface]}；E2E 门控属性 {@code subterra.probe.surface}
 * （装上确定性样例并打 marker）。
 * <p>
 * p.2.29.2 the <b>real seam</b> for rules→surface rules (the runtime assembly face): wires the
 * engine-resolved surface palette ({@link SurfacePaletteResolution}, rule keys
 * {@code subterra.worldgen.surface_palette[.<state>]}) onto the real MC chunk surface-generation path.
 * <p>
 * The mechanism is isomorphic to p.2.29.1's {@code subterra:density} — it <b>registers a custom surface
 * rule-source type</b> {@code subterra:surface_palette} into {@code Registries.MATERIAL_RULE}
 * ({@link SurfaceRules.RuleSource#CODEC} dispatches by {@code "type"} through
 * {@code BuiltInRegistries.MATERIAL_RULE}) and the shipped
 * {@code data/subterra/worldgen/noise_settings/subterra_overworld.json} references it at the top of
 * {@code surface_rule} (the {@code base} field wraps the vanilla rule tree). So when the "Subterra"
 * world preset is selected, the rules reach real chunks through the surface-rule evaluation path.
 * <p>
 * <b>Identity by default (zero change on the default preset)</b>: the default palette is
 * {@code vanilla}, in which case the rule source delegates straight through to the MC {@code base}
 * rule source (returning the very same {@code base.apply(ctx)} object, zero per-block cost) — surface
 * generation is bit-for-bit unchanged; and the default preset ({@code minecraft:normal}) never
 * references {@code subterra:subterra_overworld}, so it is entirely unaffected. Only when the palette
 * is explicitly {@code custom} with a rebind table does it deterministically rebind the
 * {@link BlockState}s produced by {@code base} (a material-palette swap).
 * <p>
 * Deterministic: {@link #activeResolution()} is a volatile immutable snapshot (installed once at
 * {@code ServerStarted}, same input → same result); the effective rule table outranks the boot
 * property which outranks the default (fixed precedence); invalid values are deterministically
 * rejected — the engine {@link SurfacePaletteResolution#fromRules} throws
 * {@link IllegalArgumentException}, and the runtime deterministically falls back to identity with a
 * deterministic warn marker (never breaking server boot, never changing a block). No private mixin:
 * this is a registration face + a datapack reference, and mods only consume the contract.
 * <p>
 * Lifecycle: {@link #bootstrap(IEventBus)} (mod construction, called from {@code Subterra} AFTER
 * {@code RulesRuntime.bootstrap()} so the ServerStarted listener fires after it at the same priority
 * and can read the live {@link RuleStore}). Marker prefix {@code [Subterra surface]}; the E2E gate
 * property is {@code subterra.probe.surface} (installs a deterministic sample and emits a marker).
 */
public final class SubterraSurfaceRules {

    /** The registered rule-source type id: {@code {"type": "subterra:surface_palette", "base": ...}}. */
    public static final ResourceLocation TYPE_ID = ResourceLocation.parse("subterra:surface_palette");

    /** The palette selector boot property (mirrors the density boot props). / palette 选择子启动属性。 */
    public static final String PALETTE_PROP = DensityAssembly.RULE_SURFACE_PALETTE;

    /** The deterministic E2E gate property (non-null → install the fixed sample + emit markers). /
     *  确定性 E2E 门控属性。 */
    public static final String GATE_PROP = "subterra.probe.surface";

    /** The fixed deterministic E2E sample state (a canonical overworld surface state). /
     *  确定性 E2E 样例状态。 */
    public static final String SAMPLE_STATE = "minecraft:grass_block";
    /** The fixed deterministic E2E sample target block (always available in vanilla). /
     *  确定性 E2E 样例目标方块。 */
    public static final String SAMPLE_TARGET = "minecraft:podzol";

    /** Marker prefix for every surface-assembly log line. / 表面装配日志行的 marker 前缀。 */
    public static final String MARKER = "[Subterra surface]";

    private static volatile SurfacePaletteResolution.Resolved resolution = SurfacePaletteResolution.DEFAULT;
    /** The installed state→block rebind table (empty = identity = zero per-block cost). /
     *  已安装的状态→方块重绑表（空 = 恒等 = 零逐块开销）。 */
    private static volatile Map<Block, Block> rebind = Map.of();

    private static volatile boolean ruleSourceTypeRegistered = false;
    private static volatile boolean unresolvableTargetLogged = false;

    private SubterraSurfaceRules() {
    }

    /**
     * Called from the mod constructor (after {@code RulesRuntime.bootstrap()}): wires the rule-source
     * registration listener and the server lifecycle listeners. / 由 mod 构造调用（在
     * {@code RulesRuntime.bootstrap()} 之后）：接线 rule-source 注册监听器与服务器生命周期监听器。
     *
     * @param modEventBus the mod event bus (for {@link RegisterEvent}); never null.
     */
    public static void bootstrap(IEventBus modEventBus) {
        modEventBus.addListener(SubterraSurfaceRules::registerRuleSourceType);
        NeoForge.EVENT_BUS.register(SubterraSurfaceRules.class);
    }

    /**
     * Registers {@code subterra:surface_palette} into {@code Registries.MATERIAL_RULE} (the same
     * dispatch registry {@link SurfaceRules} populates), so the datapack
     * {@code {"type":"subterra:surface_palette","base":{...}}} decodes through the standard
     * key-dispatch codec. {@code public static} to match the repo's proven
     * {@code modEventBus.addListener(Class::method)} idiom; double-invocation is harmless.
     * / 把 {@code subterra:surface_palette} 注册进 {@code Registries.MATERIAL_RULE}，使数据包
     * {@code {"type":"subterra:surface_palette","base":{...}}} 经标准键派发编解码器解码。重复调用无害。
     */
    public static void registerRuleSourceType(RegisterEvent event) {
        if (!Registries.MATERIAL_RULE.equals(event.getRegistryKey())) {
            return;
        }
        if (ruleSourceTypeRegistered) {
            return; // harmless double-invocation guard
        }
        event.register(Registries.MATERIAL_RULE, TYPE_ID, () -> PaletteRuleSource.CODEC.codec());
        ruleSourceTypeRegistered = true;
        Subterra.LOGGER.info("{} registered rule-source type {} in Registries.MATERIAL_RULE "
                + "(real chunk-gen surface path; default palette vanilla = identity)", MARKER, TYPE_ID);
    }

    /** Resolution step 1 — snapshots the effective surface palette once the server is up (after the
     * rules shell has built the live {@link RuleStore}) and installs it; emits the deterministic
     * marker and, when the E2E gate is set, installs + logs the fixed deterministic sample. /
     * 解析第一步——服务器起来后（规则壳已构建活跃 {@link RuleStore}）一次性快照有效表面材质面并安装；
     * 打确定性 marker；门控打开时安装并记录固定确定性样例。 */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onServerStarted(ServerStartedEvent event) {
        SurfacePaletteResolution.Resolved effective = resolveEffective();
        install(effective);
        Subterra.LOGGER.info("{} palette={} remap={} mode={} ({} rule-source on the real chunk-gen "
                        + "surface path; default preset minecraft:normal unaffected)",
                MARKER, effective.palette(), effective.mapping().size(),
                effective.identity() ? "identity" : "remap", TYPE_ID);
        if (System.getProperty(GATE_PROP) != null) {
            SurfacePaletteResolution.Resolved sample = SurfacePaletteResolution.fromRules(Map.of(
                    PALETTE_PROP, "custom",
                    SurfacePaletteResolution.KEY_PREFIX + SAMPLE_STATE, SAMPLE_TARGET));
            install(sample);
            Subterra.LOGGER.info("{} probe gate=on sample installed: {} (deterministic E2E sample)",
                    MARKER, SurfacePaletteResolution.render(sample));
        }
    }

    /** Clears the snapshot on server stop so a world switch never reuses a stale palette. /
     *  服务器停止时清空快照，避免换世界复用陈旧 palette。 */
    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        install(SurfacePaletteResolution.DEFAULT);
        Subterra.LOGGER.info("{} palette snapshot cleared (server stopping)", MARKER);
    }

    /** The active immutable resolution (volatile snapshot; read on the surface-rule path). /
     *  当前不可变解析快照（表面规则路径读取）。 */
    public static SurfacePaletteResolution.Resolved activeResolution() {
        return resolution;
    }

    /** The active state→block rebind table (empty = identity). / 当前状态→方块重绑表（空 = 恒等）。 */
    public static Map<Block, Block> activeRebind() {
        return rebind;
    }

    /**
     * Resolves the effective palette with a fixed precedence: the live {@link RuleStore} (when it
     * actually carries {@code subterra.worldgen.surface_palette*} keys, hot-reload-capable) &gt; the
     * boot properties &gt; the vanilla default. Invalid values are deterministically rejected by the
     * engine parser; here the runtime deterministically falls back to identity with a warn marker.
     * / 按固定优先级解析有效 palette：活跃 {@link RuleStore}（当它确实带
     * {@code subterra.worldgen.surface_palette*} 键时，可热重载）&gt; 启动属性 &gt; vanilla 缺省。
     * 非法值由引擎解析器确定性拒绝；runtime 侧确定性落回恒等并打 warn marker。
     */
    private static SurfacePaletteResolution.Resolved resolveEffective() {
        RuleStore store = RulesRuntime.activeStore();
        if (store != null) {
            try {
                Map<String, String> rules = store.resolve();
                boolean carries = rules.keySet().stream()
                        .anyMatch(k -> k != null && k.startsWith(DensityAssembly.RULE_SURFACE_PALETTE));
                if (carries) {
                    return SurfacePaletteResolution.fromRules(rules);
                }
            } catch (IllegalArgumentException rejected) {
                Subterra.LOGGER.warn("{} invalid surface palette rule rejected deterministically ({}); "
                        + "using identity", MARKER, rejected.getMessage());
                return SurfacePaletteResolution.DEFAULT;
            }
        }
        Map<String, String> props = readBootProps();
        if (!props.isEmpty()) {
            try {
                return SurfacePaletteResolution.fromRules(props);
            } catch (IllegalArgumentException rejected) {
                Subterra.LOGGER.warn("{} invalid surface palette boot prop rejected deterministically "
                        + "({}); using identity", MARKER, rejected.getMessage());
                return SurfacePaletteResolution.DEFAULT;
            }
        }
        return SurfacePaletteResolution.DEFAULT;
    }

    /** The boot properties carrying the palette selector + rebind entries (fixed order). /
     *  带 palette 选择子与重绑条目的启动属性（固定序）。 */
    private static Map<String, String> readBootProps() {
        TreeMap<String, String> props = new TreeMap<>();
        String selector = System.getProperty(PALETTE_PROP);
        if (selector != null) {
            props.put(PALETTE_PROP, selector);
        }
        for (String name : System.getProperties().stringPropertyNames()) {
            if (name.startsWith(SurfacePaletteResolution.KEY_PREFIX)) {
                props.put(name, System.getProperty(name));
            }
        }
        return props;
    }

    /** Installs the resolution + its resolved {@code Block→Block} rebind table (identity ⇒ empty). /
     *  安装解析结果与其解析后的 {@code Block→Block} 重绑表（恒等 ⇒ 空）。 */
    private static void install(SurfacePaletteResolution.Resolved next) {
        Objects.requireNonNull(next, "next");
        rebind = buildRebind(next.mapping());
        resolution = next;
    }

    /** Resolves the state-name→block-id table into a {@code Block→Block} table against the live block
     * registry; unresolvable entries are skipped with a deterministic once-only warning. /
     *  把状态名→方块 id 表按活跃方块注册表解析为 {@code Block→Block} 表；不可解析项跳过并确定性只告警一次。 */
    private static Map<Block, Block> buildRebind(Map<String, String> mapping) {
        if (mapping.isEmpty()) {
            return Map.of();
        }
        HashMap<Block, Block> out = new HashMap<>();
        for (Map.Entry<String, String> e : new TreeMap<>(mapping).entrySet()) {
            Block from = blockById(e.getKey());
            Block to = blockById(e.getValue());
            if (from != null && to != null) {
                out.put(from, to);
            } else if (!unresolvableTargetLogged) {
                unresolvableTargetLogged = true;
                Subterra.LOGGER.warn("{} surface palette entry unresolvable ({} -> {}); skipping it "
                        + "(deterministic, surface generation keeps the vanilla material)",
                        MARKER, e.getKey(), e.getValue());
            }
        }
        return Map.copyOf(out);
    }

    private static Block blockById(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        try {
            return BuiltInRegistries.BLOCK.getOptional(ResourceLocation.parse(id.trim())).orElse(null);
        } catch (RuntimeException malformed) {
            return null;
        }
    }
}
