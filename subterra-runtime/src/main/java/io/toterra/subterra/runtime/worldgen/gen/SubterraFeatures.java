package io.toterra.subterra.runtime.worldgen.gen;

import io.toterra.subterra.Subterra;
import io.toterra.subterra.engine.worldgen.feature.FeatureAssembly;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * p.2.29.3 <b>规则 → 特征/成矿</b>的 MC 装配接线：把 td 规则计划（{@link SubterraFeaturePlan}）驱动的
 * <b>真实</b>世界生成类型注册进静态注册表，并由随附数据包（placed_feature / biome_modifier JSON）把
 * 对应 placed feature <b>挂载</b>到真实生成路径——规则只做参数与前置判定，缺省（计划关闭）即恒等
 * （{@link SubterraRuleOreFeature} 不放置任何方块、{@link SubterraRuleVeinPlacement} 产出零原点）。
 *
 * <p>两处注册（均为静态注册表、NeoForge {@link RegisterEvent}）：
 * <ul>
 *   <li>{@code subterra:rule_ore} → {@link Registries#FEATURE}（规则驱动矿石特征）；</li>
 *   <li>{@code subterra:rule_vein} → {@link Registries#PLACEMENT_MODIFIER_TYPE}（规则驱动矿脉原点放置器）。</li>
 * </ul>
 * 挂载面（随附数据包，命名空间 {@code subterra}）：
 * <ul>
 *   <li>{@code data/subterra/worldgen/configured_feature/rule_ore_iron.json}（本 mod 的规则矿石特征）；</li>
 *   <li>{@code data/subterra/worldgen/placed_feature/rule_ore_iron.json}（引用 {@code subterra:rule_vein}
 *       + 标准 {@code in_square} / {@code height_range} / {@code biome} 放置器）；</li>
 *   <li>{@code data/subterra/neoforge/biome_modifier/rule_ore_iron.json}（{@code neoforge:add_features}
 *       挂载该 placed feature——<b>挂载目标为缺省空白 tag {@code #subterra:feature_mount_biomes}</b>
 *       （p.2.29.3.2 挂载面缺省零增量：tag 空 → 不改任何生物群系；服务端数据包填 tag 即启用挂载）。</li>
 * </ul>
 * 缺省：规则计划缺省关闭 ⇒ 已挂载的 placed feature 产出零原点、零方块 ⇒ 默认预设零行为变化。
 *
 * <p>定位说明：p.2.29.3 采用 MC 原生「自定义 {@code Feature} + 自定义 {@code PlacementModifier} + 数据包
 * 挂载」真接缝（静态注册可即时生效，无需 mixin、无需运行时写盘）；矿物 / 植被的内容与参数一律 td 声明。
 *
 * <p>p.2.29.3 the MC assembly wiring for "rules → feature/ore": registers the td-rule-plan-driven
 * ({@link SubterraFeaturePlan}) <b>real</b> worldgen types into the static registries and <b>mounts</b> the
 * matching placed feature onto the real generation path via the shipped datapack (placed_feature /
 * biome_modifier JSON) — the rules only supply parameters and prerequisites, and the default (plan off)
 * is identity. Two registrations (static registries, NeoForge {@link RegisterEvent}):
 * {@code subterra:rule_ore} into {@link Registries#FEATURE} and {@code subterra:rule_vein} into
 * {@link Registries#PLACEMENT_MODIFIER_TYPE}. The mount surface is the shipped {@code subterra}-namespace
 * datapack (configured/placed feature + biome modifier). Default: the rule plan is off, so the mounted
 * placed feature yields zero origins and zero blocks → zero behaviour change on the default preset.
 */
public final class SubterraFeatures {

    /** 确定性日志 marker。 / The deterministic log marker. */
    public static final String MARKER = "[Subterra feature]";

    /** 规则矿石特征 id。 / The rule-ore feature id. */
    public static final ResourceLocation RULE_ORE_ID = ResourceLocation.parse("subterra:rule_ore");
    /** 规则矿脉原点放置器 id。 / The rule-vein origin placer id. */
    public static final ResourceLocation RULE_VEIN_ID = ResourceLocation.parse("subterra:rule_vein");

    /** 规则矿石特征单例（无状态）。 / The rule-ore feature singleton (stateless). */
    public static final SubterraRuleOreFeature RULE_ORE = new SubterraRuleOreFeature();

    /** 规则矿脉原点放置器类型（{@code codec()} 即 {@link SubterraRuleVeinPlacement#CODEC}）。 /
     *  The rule-vein origin placer type ({@code codec()} = {@link SubterraRuleVeinPlacement#CODEC}). */
    public static final PlacementModifierType<SubterraRuleVeinPlacement> RULE_VEIN =
            () -> SubterraRuleVeinPlacement.CODEC;

    private static volatile boolean oreRegistered = false;

    private static volatile boolean veinRegistered = false;

    private static volatile boolean typesRegistered = false;

    private SubterraFeatures() {
    }

    /**
     * 从 mod 构造调用：载入 td 规则计划、挂注册监听与 ServerStarted 标记监听。缺省（无配置文件）即恒等。
     * / Called from the mod constructor: loads the td rule plan and wires the registration + ServerStarted
     * marker listeners. Default (no config file) is identity.
     *
     * @param modEventBus 模组事件总线 / the mod event bus
     */
    public static void bootstrap(IEventBus modEventBus) {
        SubterraFeaturePlan.load(FMLPaths.GAMEDIR.get());
        modEventBus.addListener(SubterraFeatures::registerTypes);
        NeoForge.EVENT_BUS.addListener(SubterraFeatures::onServerStarted);
    }

    /**
     * 注册静态世界生成类型（{@link RegisterEvent}）。幂等（重复调用无害）。
     * / Registers the static worldgen types ({@link RegisterEvent}). Idempotent.
     *
     * <p>根因注记 / Root-cause note: {@link RegisterEvent} fires <b>once per registry</b>, so the shared
     * single "typesRegistered" guard skipped the second registration entirely (FEATURE fired first and
     * marked both done; the shipped placed_feature JSON then failed with "Unknown registry key ...
     * placement_modifier_type: subterra:rule_vein"). Guards are per-type now, and the marker prints the
     * accurate pair state. / 事件逐注册表各触发一次，旧实现用单个布尔守卫导致第二次注册被整体跳过
     * （marker 日志却照打）——守卫已改为逐类型，marker 反映真实成对状态。
     */
    public static void registerTypes(RegisterEvent event) {
        if (event.getRegistryKey() == Registries.FEATURE && !oreRegistered) {
            event.register(Registries.FEATURE, RULE_ORE_ID, () -> RULE_ORE);
            oreRegistered = true;
        } else if (event.getRegistryKey() == Registries.PLACEMENT_MODIFIER_TYPE && !veinRegistered) {
            event.register(Registries.PLACEMENT_MODIFIER_TYPE, RULE_VEIN_ID, () -> RULE_VEIN);
            veinRegistered = true;
        } else {
            return;
        }
        if (oreRegistered && veinRegistered && !typesRegistered) {
            typesRegistered = true;
            Subterra.LOGGER.info("{} registered worldgen types {} -> FEATURE, {} -> PLACEMENT_MODIFIER_TYPE",
                    MARKER, RULE_ORE_ID, RULE_VEIN_ID);
        }
    }

    /** 门控启动标记：{@code subterra.probe.feature} 设置时打印确定性计划渲染 + 挂载计数（供 E2E 断言）。 /
     *  Gated boot marker: when {@code subterra.probe.feature} is set, prints the deterministic plan render +
     *  the mount count (for E2E assertions). */
    public static void onServerStarted(ServerStartedEvent event) {
        if (System.getProperty("subterra.probe.feature") == null) {
            return;
        }
        FeatureAssembly.Plan plan = SubterraFeaturePlan.plan();
        Subterra.LOGGER.info("{} plan ok (enable={}, mounts={}, guards={}, render={})",
                MARKER, plan.enabled(), plan.mounts().size(), plan.guards().size(), SubterraFeaturePlan.render());
    }
}
