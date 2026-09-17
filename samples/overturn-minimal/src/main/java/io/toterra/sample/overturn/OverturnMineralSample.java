package io.toterra.sample.overturn;

import io.toterra.subterra.engine.config.Td;
import io.toterra.subterra.engine.config.TdTable;
import io.toterra.subterra.engine.config.TdValue;
import io.toterra.subterra.engine.datapack.DatapackRules;
import io.toterra.subterra.engine.worldgen.feature.FeatureAssembly;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * p.2.29.3 OverturnMinimal「一种按母岩矿石」最小可见效果样例（纯 JDK——不触 MC 类路径）。
 *
 * <p>示例意图：用框架纯 JDK 的「规则 → 特征/成矿」装配核心（{@code engine.worldgen.feature
 * .FeatureAssembly}）把「一种矿石只按母岩产出」写成一段 td 规则（零 json，见类路径资源
 * {@link #FEATURES_RESOURCE}），得到一份确定性挂载计划，并对关键性质逐条断言：总开关、矿物×母岩前置、
 * 矿脉走向、结构护栏（不越界）、组内规范序与规范渲染（同输入同字节）。全部确定性：固定注册序、禁时序、
 * 禁随机、同输入同输出。
 *
 * <p>真实生成路径上的效果：runtime 侧 {@code subterra:rule_ore}（规则驱动矿石特征）与
 * {@code subterra:rule_vein}（规则驱动矿脉原点放置器）读取同一份规则计划，把随附 placed feature 经
 * {@code neoforge:add_features} biome modifier 挂到主世界地下矿脉步；缺省（本样例的 td 未启用）即恒等。
 * 本样例在纯 JDK 侧证明「规则 → 挂载计划」这一半，MC 接线与数据包挂载由根工程提供。
 *
 * <p>p.2.29.3 the OverturnMinimal "mineral-by-host-rock" minimal visible-effect sample (pure JDK —
 * never touches the MC classpath). Intent: express "one mineral only forms by host rock" as a td rule
 * document (zero-json, classpath resource {@link #FEATURES_RESOURCE}) and resolve it through the
 * framework's pure-JDK "rules → feature/ore" assembly core into a deterministic mount plan, asserting
 * the key properties. Deterministic: fixed order, no timing, no randomness, same input → same output.
 * On the real generation path the runtime types read the same rule plan; the default is identity.
 */
public final class OverturnMineralSample {

    /** 特征/成矿规则文档类路径资源。 / The feature/ore rule-document classpath resource. */
    public static final String FEATURES_RESOURCE = "/data/overturn_minimal/features.data.tie";

    private OverturnMineralSample() {
    }

    /**
     * 确定性样例入口：载入 td 规则文档 → 解析为挂载计划 → 断言「一种按母岩矿石」的关键性质 → 打印确定性
     * 报告行。任一断言失败抛异常（exit ≠ 0）；全部通过 → 打印 PASS 行、正常返回（exit 0）。
     * <p>
     * Deterministic sample entry: load the td rule document → resolve the mount plan → assert the key
     * "mineral-by-host-rock" properties → print one deterministic report line each. A failed assertion
     * throws (exit != 0); all pass → the PASS line prints and main returns (exit 0).
     *
     * @param args 未使用（确定性：无任何输入依赖）/ unused (deterministic: no input dependency).
     */
    public static void main(String[] args) throws IOException {
        // 1) 从类路径载入 td 规则文档 → 有效规则表（零 json）。
        Map<String, String> rules = loadRules(FEATURES_RESOURCE);

        // 2) 解析为确定性挂载计划。
        FeatureAssembly.Plan plan = FeatureAssembly.of(rules);
        check(plan.enabled(), "master switch must be on");
        check(plan.mounts().size() == 2, "two mounts expected (one mineral + one vegetation)");
        System.out.println("overturn_minimal feature plan ok (enable=" + plan.enabled()
                + ", minerals=" + plan.minerals().size() + ", vegetation=" + plan.vegetation().size()
                + ", mounts=" + plan.mounts().size() + ", guards=" + plan.guards().size()
                + ", clearance=" + plan.guardClearance() + ")");

        // 3) 矿物 × 母岩前置：铁矿石只在花岗岩母岩上、以竖直矿脉产出。
        FeatureAssembly.Mineral iron = plan.mineral("iron").orElseThrow(
                () -> new IllegalStateException("iron mineral missing"));
        check("minecraft:iron_ore".equals(iron.block()), "iron block must be minecraft:iron_ore");
        check(iron.hostRock().blocks().equals(java.util.List.of("minecraft:granite")),
                "iron must be gated to the granite host rock");
        check(iron.trend() == FeatureAssembly.VeinTrend.VERTICAL, "iron vein trend must be vertical");
        check(iron.count() == 2 && iron.minY() == -48 && iron.maxY() == 16, "iron count/y band");
        System.out.println("overturn_minimal mineral iron -> " + iron.block()
                + " (host_rock=" + iron.hostRock().render() + ", trend=" + iron.trend().id()
                + ", count=" + iron.count() + ", y=[" + iron.minY() + ',' + iron.maxY() + "])");

        // 4) 结构护栏：矿脉原点不越过护栏 box（含 clearance 外扩）。
        check(plan.blocked(0, 0) && plan.blocked(64 + 7 + 4, 0), "guard must block inside box + clearance ring");
        check(!plan.blocked(400, 400), "guard must leave distant origins free");
        System.out.println("overturn_minimal guard ok (blocked(0,0)=" + plan.blocked(0, 0)
                + ", blocked(400,400)=" + plan.blocked(400, 400) + ")");

        // 5) 确定性：同输入同字节（规范渲染再入恒等）。
        String render = FeatureAssembly.render(plan);
        check(render.equals(FeatureAssembly.render(FeatureAssembly.of(rules))), "canonical render re-entry");
        System.out.println("overturn_minimal feature plan render=" + render);

        System.out.println("overturn_minimal FEATURE PASS (mineral-by-host-rock rule plan, deterministic)");
    }

    /** 载入 td 规则文档并归一为有效规则表（{@code k} 字符串 → 值字符串）。 /
     *  Loads the td rule document and normalises it into an effective rule table. */
    static Map<String, String> loadRules(String resource) throws IOException {
        String text;
        try (InputStream in = OverturnMineralSample.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IOException("missing classpath resource: " + resource);
            }
            text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        TdTable root = Td.parse(text);
        Map<String, TdValue> manifest = DatapackRules.fromManifest(root);
        Map<String, String> flat = new LinkedHashMap<>();
        for (Map.Entry<String, TdValue> e : manifest.entrySet()) {
            if (!(e.getValue() instanceof TdTable)) {
                flat.put(e.getKey(), e.getValue().toString());
            }
        }
        return flat;
    }

    /** 样例断言：失败抛 IllegalStateException（main 异常退出 → exit ≠ 0）。 /
     *  Sample assertion: throws on failure (main exits abnormally → exit != 0). */
    private static void check(boolean ok, String what) {
        if (!ok) {
            throw new IllegalStateException("overturn_minimal feature check failed: " + what);
        }
    }
}
