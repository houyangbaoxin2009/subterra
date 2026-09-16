package io.toterra.subterra.runtime.worldgen.gen;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import io.toterra.subterra.Subterra;
import io.toterra.subterra.engine.config.Td;
import io.toterra.subterra.engine.config.TdTable;
import io.toterra.subterra.engine.config.TdValue;
import io.toterra.subterra.engine.datapack.DatapackRules;
import io.toterra.subterra.engine.worldgen.feature.FeatureAssembly;

/**
 * p.2.29.3 特征/成矿装配面的运行期<b>规则计划持有者</b>：把 td（零 json）规则文件
 * {@code config/subterra/features.td} 一次解析为 engine 的确定性
 * {@link FeatureAssembly.Plan}，供 MC 侧装配面（{@link SubterraRuleOreFeature} /
 * {@link SubterraRuleVeinPlacement}）在真实生成路径上按规则读取。缺省/缺失/非法 → 恒等缺省计划
 * （{@link FeatureAssembly#DEFAULT_PLAN}，零行为变化）。
 *
 * <p>文件形态 = 与 pack.td 同源的 {@code rules} 规则文档（规则键为字符串，含点号）：
 * <pre>{@code
 * type tie<data>
 * [ rules = [
 *   [ k = "subterra.worldgen.feature.enable", v = "true" ],
 *   [ k = "subterra.worldgen.feature.mineral.iron.block", v = "minecraft:iron_ore" ],
 *   [ k = "subterra.worldgen.feature.mineral.iron.host_rock", v = "minecraft:granite" ],
 *   [ k = "subterra.worldgen.feature.mineral.iron.vein_trend", v = "vertical" ],
 *   [ k = "subterra.worldgen.feature.mineral.iron.count", v = 2 ],
 *   [ k = "subterra.worldgen.feature.mineral.iron.min_y", v = -48 ],
 *   [ k = "subterra.worldgen.feature.mineral.iron.max_y", v = 16 ],
 * ] ]
 * }</pre>
 *
 * <p>确定性：纯读、固定序（{@code DatapackRules} 首现获胜、{@link FeatureAssembly} 组内 id 规范序）、
 * 同输入同计划；不写盘、不碰 MC（{@code Td} 为 engine 纯解析器）。热重载接线点见 runtime-wiring.md。
 *
 * <p>p.2.29.3 the runtime <b>rule-plan holder</b> of the feature/ore assembly surface: parses the td
 * (zero-json) rule file {@code config/subterra/features.td} once into the engine deterministic
 * {@link FeatureAssembly.Plan}, read by the MC assembly surface ({@link SubterraRuleOreFeature} /
 * {@link SubterraRuleVeinPlacement}) on the real generation path. Missing / blank / invalid → the
 * identity default plan ({@link FeatureAssembly#DEFAULT_PLAN}, zero behaviour change). Deterministic:
 * pure read, fixed order, same input → same plan; never writes, never touches MC.
 */
public final class SubterraFeaturePlan {

    /** 规则文件相对游戏目录的路径。 / The rule file path relative to the game dir. */
    public static final String CONFIG_REL_PATH = "config/subterra/features.td";

    /** 基线计划（td 载入结果，未乘 ore_density）。 / The base plan (the td load result, before ore-density scaling). */
    private static volatile FeatureAssembly.Plan basePlan = FeatureAssembly.DEFAULT_PLAN;

    /** 当前有效计划（基线 × ore_density，缺省为恒等缺省计划）。 / The current effective plan (base × ore density; identity default until loaded). */
    private static volatile FeatureAssembly.Plan plan = FeatureAssembly.DEFAULT_PLAN;

    private SubterraFeaturePlan() {
    }

    /** 当前有效计划（缺省为恒等缺省计划）。 / The current effective plan (identity default until loaded). */
    public static FeatureAssembly.Plan plan() {
        return plan;
    }

    /** 从游戏目录下的 {@code config/subterra/features.td} 载入（缺失 → 恒等缺省）。 /
     *  Loads {@code config/subterra/features.td} from the game dir (absent → identity default). */
    public static void load(Path gameDir) {
        basePlan = read(gameDir == null ? null : gameDir.resolve(CONFIG_REL_PATH));
        plan = basePlan;
    }

    /**
     * p.2.29.3.1：以装配面 ore_density 标量（{@code AssemblySeam} → {@code
     * DensityAssembly.Params.oreDensity}，ServerStarting 快照期调用）确定性重导有效计划——
     * 每矿物 count 乘以该标量（floor + clamp，见 {@link FeatureAssembly.Plan#withOreDensity}），
     * 基线计划保持不变，故跨世界/多次开服不累积。d = 1（缺省）→ 与基线恒等。
     *
     * <p>p.2.29.3.1: re-derives the effective plan deterministically with the assembly
     * ore-density scalar (called at the ServerStarting snapshot); every mineral count is
     * scaled (floor + clamp, see {@link FeatureAssembly.Plan#withOreDensity}) while the base
     * plan stays untouched, so worlds and repeated boots never compound. d = 1 (default) is
     * identical to the base plan.
     */
    public static void applyOreDensity(double oreDensity) {
        plan = basePlan.withOreDensity(oreDensity);
    }

    /** 解析后的规范计划渲染（确定性；供 E2E 断言）。 / Canonical plan render (deterministic; for E2E assertions). */
    public static String render() {
        return FeatureAssembly.render(plan);
    }

    /**
     * 读取 td 规则文件为规范计划：缺失 / 空白 → 缺省；解析或校验失败 → warn + 缺省（绝不抛到 boot）。
     * / Reads the td rule file into a canonical plan: absent / blank → default; parse/validation failure
     * → warn + default (never throws into boot).
     */
    static FeatureAssembly.Plan read(Path file) {
        if (file == null || !Files.isRegularFile(file)) {
            return FeatureAssembly.DEFAULT_PLAN;
        }
        try {
            String text = Files.readString(file, StandardCharsets.UTF_8);
            if (text.isBlank()) {
                return FeatureAssembly.DEFAULT_PLAN;
            }
            TdTable root = Td.parse(text);
            Map<String, TdValue> rules = DatapackRules.fromManifest(root);
            Map<String, String> flat = new LinkedHashMap<>();
            for (Map.Entry<String, TdValue> e : rules.entrySet()) {
                TdValue v = e.getValue();
                if (!(v instanceof TdTable)) {
                    flat.put(e.getKey(), v.toString());
                }
            }
            return FeatureAssembly.of(flat);
        } catch (IllegalArgumentException | IOException e) {
            Subterra.LOGGER.warn("subterra feature: {} ignored ({}), using identity defaults", file, e);
            return FeatureAssembly.DEFAULT_PLAN;
        }
    }
}
