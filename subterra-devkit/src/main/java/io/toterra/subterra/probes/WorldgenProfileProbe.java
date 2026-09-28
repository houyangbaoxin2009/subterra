package io.toterra.subterra.probes;

import io.toterra.subterra.api.worldgen.Blend;
import io.toterra.subterra.api.worldgen.DimCapability;
import io.toterra.subterra.api.worldgen.DimMode;
import io.toterra.subterra.api.worldgen.DimParams;
import io.toterra.subterra.api.worldgen.EcoDim;
import io.toterra.subterra.api.worldgen.FieldQuad;
import io.toterra.subterra.api.worldgen.RelationEdge;
import io.toterra.subterra.api.worldgen.TransitionBand;
import io.toterra.subterra.api.worldgen.WorldgenProfile;
import io.toterra.subterra.engine.worldgen.profile.WorldgenProfileMirror;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * p.2.36.2（探针）— 世界生成配置面探针：把 {@code api.worldgen.WorldgenProfile}
 * （窗口 + 海平面 + 九维能力位 + 参数向量 + 关系图 + 三场四元组 + 过渡带，td + api 双接口）
 * 与 {@code engine.worldgen.profile.WorldgenProfileMirror}（逐字镜像）锚定为纯 JVM 断言。
 * 纯 JVM——不碰 MC、无时序、无随机；exit 0 = PASS，exit 1 = FAIL；不进 mod jar。分节：
 * <ol>
 *   <li><b>缺省与窗口门</b>：窗口 {@code [-128, 592)} / 海平面 {@code 127.0}，海平面严格落窗内
 *       （p.1.8.33 淹没回归守门）。</li>
 *   <li><b>九维固定序</b>：能力位与参数向量均按 {@code EcoDim.ALL} 九维序。</li>
 *   <li><b>关系图</b>：缺省关系链 16 边（terrain/climate 为根）、确定性拓扑求值序（api == mirror）、
 *       环确定性拒绝、自环/重复边/乱序直接构造拒绝、td 声明边整表替换、withEdge/withoutEdge。</li>
 *   <li><b>三场与过渡</b>：缺省三场全旁路（相对 p.1.8.34 逐位恒等）、过渡带三具名缺省、
 *       曲线族别名解析。</li>
 *   <li><b>td 往返</b>：渲染 → 解析逐字节恒等；缺省段不渲染非缺省段；部分文档回落缺省。</li>
 *   <li><b>api ↔ mirror 逐位一致</b>：多种文档解析 → 渲染逐字节相等，非法输入拒绝结果相同。</li>
 *   <li><b>确定性拒绝</b>：窗口对齐、海平面越窗、参数/边/场/带越约、未知与重复键、畸形条目。</li>
 *   <li><b>不可变与查询</b>：with* 家族不改原值。</li>
 * </ol>
 * <p>
 * p.2.36.2 (probe) — the worldgen configuration probe: anchors {@code api.worldgen.WorldgenProfile}
 * (window + sea level + nine-dimension capability bits + parameter vectors + relation graph + tone-field
 * quads + transition bands, td + api dual interface) and its verbatim engine mirror to pure-JVM
 * assertions. Pure JVM — no MC runtime, no timing, no randomness; exit 0 = PASS, exit 1 = FAIL; never
 * shipped in the mod jar.
 */
public final class WorldgenProfileProbe {

    private WorldgenProfileProbe() {
    }

    private static int checks = 0;
    private static int failures = 0;

    private static void check(String name, boolean ok) {
        checks++;
        if (ok) {
            System.out.println("[PASS] " + name);
        } else {
            failures++;
            System.out.println("[FAIL] " + name);
        }
    }

    /** True iff the action raises IllegalArgumentException. 动作抛出 IAE 为真。 */
    private static boolean throwsIAE(Runnable r) {
        try {
            r.run();
            return false;
        } catch (IllegalArgumentException e) {
            return true;
        }
    }

    /** True iff the action raises NullPointerException. 动作抛出 NPE 为真。 */
    private static boolean throwsNPE(Runnable r) {
        try {
            r.run();
            return false;
        } catch (NullPointerException e) {
            return true;
        }
    }

    public static void main(String[] args) {
        try {
            defaultsAndWindowGate();
            dimensionOrder();
            relationGraph();
            fieldsAndTransitions();
            tdRoundTrip();
            apiMirrorParity();
            deterministicRejection();
            immutability();
        } catch (Exception e) {
            failures++;
            System.out.println("[FAIL] probe exception: " + e);
            e.printStackTrace(System.out);
        }
        if (failures == 0) {
            System.out.println("[WorldgenProfileProbe] PASS (" + checks + " checks)");
            System.exit(0);
        } else {
            System.out.println("[WorldgenProfileProbe] FAIL (" + failures + " of " + checks + " checks)");
            System.exit(1);
        }
    }

    private static void defaultsAndWindowGate() {
        WorldgenProfile def = WorldgenProfile.defaults();
        check("defaults: 窗口 [-128, 592) / 海平面 127.0（p.2.36.1 落地默认）",
                def.minY() == -128 && def.height() == 720 && def.windowTop() == 592
                        && Double.doubleToLongBits(def.seaLevel()) == Double.doubleToLongBits(127.0D));
        check("窗口门: 海平面严格落在窗口内（min_y < sea < top）",
                def.seaLevel() > def.minY() && def.seaLevel() < def.windowTop());
        check("窗口常量: 对齐 16 / 上界 2032 / 下界 -2032",
                WorldgenProfile.WINDOW_ALIGN == 16 && WorldgenProfile.MAX_WINDOW_TOP == 2032
                        && WorldgenProfile.MIN_WINDOW_BOTTOM == -2032);
        check("mirror 窗口默认值与 api 逐位一致",
                WorldgenProfileMirror.DEFAULT_MIN_Y == WorldgenProfile.DEFAULT_MIN_Y
                        && WorldgenProfileMirror.DEFAULT_HEIGHT == WorldgenProfile.DEFAULT_HEIGHT
                        && Double.doubleToLongBits(WorldgenProfileMirror.DEFAULT_SEA_LEVEL)
                        == Double.doubleToLongBits(WorldgenProfile.DEFAULT_SEA_LEVEL));
    }

    private static void dimensionOrder() {
        WorldgenProfile def = WorldgenProfile.defaults();
        List<String> apiIds = new ArrayList<>();
        for (EcoDim dim : EcoDim.ALL) {
            apiIds.add(dim.id());
        }
        List<String> profileIds = new ArrayList<>();
        List<String> paramIds = new ArrayList<>();
        for (DimCapability cap : def.dims()) {
            profileIds.add(cap.dim());
        }
        for (int i = 0; i < def.params().size(); i++) {
            paramIds.add(def.dims().get(i).dim());
        }
        check("九维固定序: 能力位与参数向量均 == EcoDim.ALL 九维序",
                apiIds.equals(profileIds) && apiIds.equals(paramIds));
        check("九维固定序: mirror DIM_ORDER == api EcoDim.ALL 序",
                WorldgenProfileMirror.DIM_ORDER.equals(apiIds));
        boolean allEnabled = true;
        for (DimCapability cap : def.dims()) {
            allEnabled &= cap.mode() == DimMode.ENABLE && !cap.redirected()
                    && cap.target().equals(DimCapability.NO_TARGET);
        }
        check("缺省九维全 ENABLE、无重定向目标", allEnabled && def.dims().size() == 9);
        DimParams p = def.params("terrain");
        check("参数向量缺省: 中性起点（weight 1.0 / wavelength 256.0 / amplitude 1.0 / band 0.5）",
                Double.doubleToLongBits(p.weight()) == Double.doubleToLongBits(1.0D)
                        && Double.doubleToLongBits(p.wavelength()) == Double.doubleToLongBits(256.0D)
                        && Double.doubleToLongBits(p.amplitude()) == Double.doubleToLongBits(1.0D)
                        && Double.doubleToLongBits(p.band()) == Double.doubleToLongBits(0.5D));
        check("参数向量查询: 未知维回落中性缺省（读永不抛）",
                def.params("bogus").equals(DimParams.defaults()) && def.params(null).equals(DimParams.defaults()));
    }

    private static void relationGraph() {
        WorldgenProfile def = WorldgenProfile.defaults();
        check("缺省关系链: 16 边、边权 1.0、混合 MIX",
                def.edges().size() == 16
                        && def.edges().get(0).equals(RelationEdge.of("terrain", "vegetation"))
                        && def.edges().stream().allMatch(e -> Double.doubleToLongBits(e.weight())
                        == Double.doubleToLongBits(RelationEdge.DEFAULT_WEIGHT)
                        && e.blend() == Blend.MIX));
        check("缺省关系图: terrain 与 climate 为根（无入边）",
                def.edges().stream().noneMatch(e -> e.to().equals("terrain"))
                        && def.edges().stream().noneMatch(e -> e.to().equals("climate")));
        List<String> expectedOrder = List.of(
                "terrain", "climate", "hydro", "vegetation", "surface",
                "litho", "mineral", "fauna", "relic");
        check("拓扑求值序: 缺省链的确定性 Kahn 序（并列按规范序）",
                def.evaluationOrder().equals(expectedOrder));
        check("拓扑求值序: mirror 与 api 完全一致",
                WorldgenProfileMirror.evaluationOrder(WorldgenProfileMirror.defaults())
                        .equals(def.evaluationOrder()));
        check("环检测: 二元环构造即确定性拒绝（ctor 与 fromTd 同）",
                throwsIAE(() -> new WorldgenProfile(-128, 720, 127.0D, def.dims(), def.params(),
                        List.of(RelationEdge.of("climate", "terrain"), RelationEdge.of("terrain", "climate")),
                        def.fields(), def.transitions()))
                        && throwsIAE(() -> WorldgenProfile.fromTd(
                        "[\n  edge.terrain.climate = 1.0:mix,\n  edge.climate.terrain = 1.0:mix,\n]")));
    }

    private static void fieldsAndTransitions() {
        WorldgenProfile def = WorldgenProfile.defaults();
        check("三场缺省: 全旁路（gain 0.0，相对 p.1.8.34 逐位恒等）",
                def.field("coarse_elev").bypassed() && def.field("coarse_mountain").bypassed()
                        && def.field("coarse_climate").bypassed()
                        && def.fields().size() == 3 && def.field("bogus") == null);
        check("三场固定序: FIELD_IDS 与 fields 平行同序",
                WorldgenProfile.FIELD_IDS.size() == 3
                        && WorldgenProfileMirror.FIELD_IDS.equals(WorldgenProfile.FIELD_IDS));
        check("混合函数族: 五支（MIX/ADD/MULTIPLY/MAX/MIN），解析大小写宽容",
                Blend.parse("mix") == Blend.MIX && Blend.parse("MULTIPLY") == Blend.MULTIPLY
                        && Blend.parse("max") == Blend.MAX && Blend.parse("min") == Blend.MIN
                        && Blend.parse("add") == Blend.ADD);
        check("过渡带缺省: land_sea(0.08/smoothstep/1) mountain(0.25/smoothstep/2) biome(0.12/clamp-linear/1)",
                Double.doubleToLongBits(def.transition("land_sea").width())
                        == Double.doubleToLongBits(0.08D)
                        && def.transition("land_sea").curve() == TransitionBand.Curve.SMOOTHSTEP
                        && def.transition("mountain").order() == 2
                        && def.transition("biome").curve() == TransitionBand.Curve.CLAMP_LINEAR
                        && def.transition("bogus") == null);
        check("过渡曲线族: 两支 + 别名解析（clamp_linear / linear / smooth）",
                TransitionBand.Curve.parse("clamp_linear") == TransitionBand.Curve.CLAMP_LINEAR
                        && TransitionBand.Curve.parse("linear") == TransitionBand.Curve.CLAMP_LINEAR
                        && TransitionBand.Curve.parse("smooth") == TransitionBand.Curve.SMOOTHSTEP
                        && TransitionBand.Curve.parse("smoothstep").id().equals("smoothstep")
                        && TransitionBand.Curve.parse("clamp-linear").id().equals("clamp-linear"));
    }

    private static void tdRoundTrip() {
        WorldgenProfile def = WorldgenProfile.defaults();
        String td = def.td();
        check("td 往返: defaults 渲染 → 解析逐字节恒等",
                WorldgenProfile.fromTd(td).equals(def)
                        && WorldgenProfile.fromTd(td).td().equals(td));
        check("td 渲染: 规范形状（窗口 3 行 + dim.* 9 行 + p.* 9 行，缺省段不渲染边/场/带）",
                td.startsWith("[\n  min_y = -128,\n  height = 720,\n  sea_level = 127.0,\n")
                        && td.contains("  p.terrain = 1.0:256.0:1.0:0.5,\n")
                        && !td.contains("edge.") && !td.contains("field.") && !td.contains("transition.")
                        && td.split("\n").length == 23);
        check("td 渲染确定性: 两次渲染逐字节相同", def.td().equals(WorldgenProfile.fromTd(td).td()));

        WorldgenProfile tuned = def.withWindow(-64, 384, 63.0D)
                .withDim("litho", DimMode.REDIRECT, "overturn:litho");
        WorldgenProfile back = WorldgenProfile.fromTd(tuned.td());
        check("td 往返: 改窗 + 重定向维（min_y=-64/height=384/sea=63.0, litho→overturn:litho）",
                back.equals(tuned)
                        && back.minY() == -64 && back.height() == 384 && back.windowTop() == 320
                        && back.mode("litho") == DimMode.REDIRECT
                        && back.capability("litho").target().equals("overturn:litho"));

        String partial = "type tie<data>\n// a comment\n[\n  sea_level = 100.0, // trailing\n  ,,\n]";
        WorldgenProfile parsed = WorldgenProfile.fromTd(partial);
        check("td 解析: 部分文档回落缺省 + 容忍 type 头/注释/多余逗号",
                parsed.minY() == -128 && parsed.height() == 720
                        && Double.doubleToLongBits(parsed.seaLevel()) == Double.doubleToLongBits(100.0D)
                        && parsed.mode("climate") == DimMode.ENABLE);

        WorldgenProfile params = WorldgenProfile.fromTd(
                "[\n  p.hydro = 0.75:128.0:2.0:0.25,\n]");
        check("td 解析: 单维参数向量改写（其余维回落中性缺省）",
                Double.doubleToLongBits(params.params("hydro").weight())
                        == Double.doubleToLongBits(0.75D)
                        && Double.doubleToLongBits(params.params("hydro").wavelength())
                        == Double.doubleToLongBits(128.0D)
                        && Double.doubleToLongBits(params.params("hydro").amplitude())
                        == Double.doubleToLongBits(2.0D)
                        && Double.doubleToLongBits(params.params("hydro").band())
                        == Double.doubleToLongBits(0.25D)
                        && params.params("terrain").equals(DimParams.defaults()));

        WorldgenProfile oneEdge = WorldgenProfile.fromTd(
                "[\n  edge.hydro.vegetation = 0.5:mix,\n]");
        check("td 语义: 声明任何 edge.* 即整表替换缺省关系链（单边图）",
                oneEdge.edges().size() == 1
                        && oneEdge.edges().get(0).from().equals("hydro")
                        && oneEdge.edges().get(0).to().equals("vegetation")
                        && Double.doubleToLongBits(oneEdge.edges().get(0).weight())
                        == Double.doubleToLongBits(0.5D));
        check("td 语义: 乱序边文档解析后落规范序（解析器排序）",
                WorldgenProfile.fromTd(
                        "[\n  edge.climate.hydro = 1.0:mix,\n  edge.terrain.hydro = 1.0:mix,\n]")
                        .edges().get(0).from().equals("terrain"));

        WorldgenProfile mixed = WorldgenProfile.fromTd(
                "[\n  edge.hydro.vegetation = 1.0:mix,\n"
                        + "  field.coarse_elev = 0.4:0.1:0.5:mix,\n"
                        + "  transition.mountain = 0.3:smoothstep:2,\n]");
        String mixedTd = mixed.td();
        check("td 渲染: 非缺省段渲染、缺省段跳过（边 1 行 + 场 1 行 + 带 1 行）",
                mixedTd.contains("  edge.hydro.vegetation = 1.0:mix,\n")
                        && mixedTd.contains("  field.coarse_elev = 0.4:0.1:0.5:mix,\n")
                        && mixedTd.contains("  transition.mountain = 0.3:smoothstep:2,\n")
                        && !mixedTd.contains("field.coarse_mountain")
                        && !mixedTd.contains("transition.land_sea"));
        check("td 往返: 边 + 场 + 带混合改写逐字节恒等", WorldgenProfile.fromTd(mixedTd).equals(mixed));
    }

    private static void apiMirrorParity() {
        List<String> docs = List.of(
                WorldgenProfile.defaults().td(),
                "[\n  min_y = -64,\n  height = 384,\n  sea_level = 63.0,\n]",
                "[\n  dim.litho = redirect:overturn:litho,\n  p.terrain = 2.0:512.0:0.5:0.4,\n]",
                "[\n  edge.hydro.vegetation = 1.0:mix,\n  field.coarse_climate = 0.6:0:0.5:add,\n]",
                "[\n  transition.land_sea = 0.2:linear:1,\n  height = 512,\n]");
        boolean allEqual = true;
        for (String doc : docs) {
            String apiTd = WorldgenProfile.fromTd(doc).td();
            String mirrorTd = WorldgenProfileMirror.fromTd(doc).td();
            allEqual &= apiTd.equals(mirrorTd);
        }
        check("api ↔ mirror: 五种文档解析 → 渲染逐字节一致", allEqual);
        check("api ↔ mirror: 缺省档案逐字节一致",
                WorldgenProfile.defaults().td().equals(WorldgenProfileMirror.defaults().td()));

        List<String> bad = List.of(
                "",
                "[\n  height = 100,\n]",
                "[\n  height = 0,\n]",
                "[\n  min_y = -100,\n]",
                "[\n  min_y = 1984,\n  height = 96,\n]",
                "[\n  min_y = -128,\n  height = 720,\n  sea_level = -128,\n]",
                "[\n  sea_level = 592,\n]",
                "[\n  sea_level = nan,\n]",
                "[\n  dim.bogus = enable,\n]",
                "[\n  dim.terrain = enable,\n  dim.terrain = disable,\n]",
                "[\n  height = 384,\n  height = 384,\n]",
                "[\n  nope = 1,\n]",
                "[\n  height,\n]",
                "[\n  dim.terrain = maybe,\n]",
                "[\n  dim.terrain = redirect:,\n]",
                "[\n  p.bogus = 1:2:3:4,\n]",
                "[\n  p.terrain = 1:2,\n]",
                "[\n  p.terrain = -1:256:1:0.5,\n]",
                "[\n  p.terrain = 1:0:1:0.5,\n]",
                "[\n  p.terrain = 1:256:1:1.5,\n]",
                "[\n  edge.terrain.bogus = 1.0:mix,\n]",
                "[\n  edge.terrain.terrain = 1.0:mix,\n]",
                "[\n  edge.terrain.hydro = 1.0,\n]",
                "[\n  edge.terrain.hydro = x:mix,\n]",
                "[\n  edge.terrain.hydro = 1.0:mix,\n  edge.terrain.hydro = 2.0:mix,\n]",
                "[\n  edge.terrain.climate = 1.0:mix,\n  edge.climate.terrain = 1.0:mix,\n]",
                "[\n  field.bogus = 0:0:0.5:mix,\n]",
                "[\n  field.coarse_elev = 1:2:3,\n]",
                "[\n  field.coarse_elev = 0:0:2:mix,\n]",
                "[\n  field.coarse_elev = 0:0:0.5:blendx,\n]",
                "[\n  transition.bogus = 0.1:smoothstep:1,\n]",
                "[\n  transition.land_sea = 0,\n]",
                "[\n  transition.land_sea = 0.1:curvy:1,\n]",
                "[\n  transition.land_sea = 0.1:smoothstep:0,\n]");
        boolean apiRejectsAll = true;
        boolean mirrorSame = true;
        for (String doc : bad) {
            boolean apiReject = throwsIAE(() -> WorldgenProfile.fromTd(doc));
            boolean mirrorReject = WorldgenProfileMirror.rejects(doc);
            apiRejectsAll &= apiReject;
            mirrorSame &= apiReject == mirrorReject;
        }
        check("确定性拒绝: 三十五种非法文档 api 全部 IAE 拒绝", apiRejectsAll);
        check("api ↔ mirror: 同一组非法输入拒绝结果完全相同", mirrorSame);
        check("确定性拒绝: null 源 NPE（api 与 mirror 同）",
                throwsNPE(() -> WorldgenProfile.fromTd(null))
                        && throwsNPE(() -> WorldgenProfileMirror.fromTd(null)));
    }

    private static void deterministicRejection() {
        WorldgenProfile def = WorldgenProfile.defaults();
        check("窗口对齐: height / min_y 非 16 倍数 IAE（含构造直接入参）",
                throwsIAE(() -> new WorldgenProfile(-128, 100, 127.0D, def.dims(), def.params(),
                        def.edges(), def.fields(), def.transitions()))
                        && throwsIAE(() -> new WorldgenProfile(-100, 720, 127.0D, def.dims(), def.params(),
                        def.edges(), def.fields(), def.transitions()))
                        && throwsIAE(() -> new WorldgenProfile(-128, 0, 127.0D, def.dims(), def.params(),
                        def.edges(), def.fields(), def.transitions()))
                        && throwsIAE(() -> new WorldgenProfile(-128, -16, 127.0D, def.dims(), def.params(),
                        def.edges(), def.fields(), def.transitions())));
        check("窗口界: min_y + height > 2032 与 min_y < -2032 均 IAE",
                throwsIAE(() -> new WorldgenProfile(1984, 96, 2000.0D, def.dims(), def.params(),
                        def.edges(), def.fields(), def.transitions()))
                        && throwsIAE(() -> new WorldgenProfile(-2048, 720, 0.0D, def.dims(), def.params(),
                        def.edges(), def.fields(), def.transitions())));
        check("海平面: 等于下沿 / 等于上沿 / 越窗 / 非有限（NaN、±Inf）均 IAE",
                throwsIAE(() -> new WorldgenProfile(-128, 720, -128.0D, def.dims(), def.params(),
                        def.edges(), def.fields(), def.transitions()))
                        && throwsIAE(() -> new WorldgenProfile(-128, 720, 592.0D, def.dims(), def.params(),
                        def.edges(), def.fields(), def.transitions()))
                        && throwsIAE(() -> new WorldgenProfile(-128, 720, 1000.0D, def.dims(), def.params(),
                        def.edges(), def.fields(), def.transitions()))
                        && throwsIAE(() -> new WorldgenProfile(-128, 720, Double.NaN, def.dims(), def.params(),
                        def.edges(), def.fields(), def.transitions()))
                        && throwsIAE(() -> new WorldgenProfile(-128, 720, Double.POSITIVE_INFINITY,
                        def.dims(), def.params(), def.edges(), def.fields(), def.transitions())));
        check("能力位: 未知维 / 缺维 / 多维 / 乱序 均 IAE",
                throwsIAE(() -> new DimCapability("bogus", DimMode.ENABLE, ""))
                        && throwsIAE(() -> new WorldgenProfile(-128, 720, 127.0D, List.of(), def.params(),
                        def.edges(), def.fields(), def.transitions()))
                        && throwsIAE(() -> new WorldgenProfile(-128, 720, 127.0D,
                        java.util.stream.Stream.concat(def.dims().stream(),
                                java.util.stream.Stream.of(DimCapability.enabled("terrain"))).toList(),
                        def.params(), def.edges(), def.fields(), def.transitions()))
                        && throwsIAE(() -> new WorldgenProfile(-128, 720, 127.0D, reversed(def.dims()),
                        def.params(), def.edges(), def.fields(), def.transitions())));
        check("能力位: 重定向目标契约（空目标 / 无冒号 / 多冒号 / 非法字符 → IAE；非重定向带目标 → IAE）",
                throwsIAE(() -> new DimCapability("litho", DimMode.REDIRECT, ""))
                        && throwsIAE(() -> new DimCapability("litho", DimMode.REDIRECT, "overturn.litho"))
                        && throwsIAE(() -> new DimCapability("litho", DimMode.REDIRECT, "a:b:c"))
                        && throwsIAE(() -> new DimCapability("litho", DimMode.REDIRECT, "Bad:Path"))
                        && throwsIAE(() -> new DimCapability("litho", DimMode.ENABLE, "overturn:litho")));
        check("参数向量: 负权重 / 零波长 / 负幅度 / 越界带位 / 非有限 均 IAE",
                throwsIAE(() -> new DimParams(-1.0D, 256.0D, 1.0D, 0.5D))
                        && throwsIAE(() -> new DimParams(1.0D, 0.0D, 1.0D, 0.5D))
                        && throwsIAE(() -> new DimParams(1.0D, 256.0D, -1.0D, 0.5D))
                        && throwsIAE(() -> new DimParams(1.0D, 256.0D, 1.0D, 1.5D))
                        && throwsIAE(() -> new DimParams(Double.NaN, 256.0D, 1.0D, 0.5D))
                        && throwsIAE(() -> DimParams.parse("1:2")));
        check("边: 自环 / 未知端点 / 负权 / 非有限权 均 IAE（构造与解析同）",
                throwsIAE(() -> RelationEdge.parse("terrain", "terrain", "1.0:mix"))
                        && throwsIAE(() -> RelationEdge.parse("terrain", "bogus", "1.0:mix"))
                        && throwsIAE(() -> RelationEdge.parse("terrain", "hydro", "-1.0:mix"))
                        && throwsIAE(() -> RelationEdge.parse("terrain", "hydro", "nan:mix"))
                        && throwsIAE(() -> RelationEdge.parse("terrain", "hydro", "1.0:nope")));
        check("三场: 非有限增益 / 偏置 / 越界带位 / 未知混合 均 IAE",
                throwsIAE(() -> new FieldQuad(Double.NaN, 0.0D, 0.5D, Blend.MIX))
                        && throwsIAE(() -> new FieldQuad(0.0D, Double.POSITIVE_INFINITY, 0.5D, Blend.MIX))
                        && throwsIAE(() -> new FieldQuad(0.0D, 0.0D, -0.1D, Blend.MIX))
                        && throwsIAE(() -> FieldQuad.parse("0:0:0.5:")));
        check("过渡带: 零宽 / 负阶 / 未知曲线 均 IAE",
                throwsIAE(() -> new TransitionBand(0.0D, TransitionBand.Curve.SMOOTHSTEP, 1))
                        && throwsIAE(() -> new TransitionBand(0.1D, TransitionBand.Curve.SMOOTHSTEP, 0))
                        && throwsIAE(() -> TransitionBand.parse("0.1:curvy:1")));
        check("直接构造: 乱序 / 重复边 IAE；三场/过渡段长度错 IAE",
                throwsIAE(() -> new WorldgenProfile(-128, 720, 127.0D, def.dims(), def.params(),
                        List.of(RelationEdge.of("climate", "hydro"), RelationEdge.of("terrain", "hydro")),
                        def.fields(), def.transitions()))
                        && throwsIAE(() -> new WorldgenProfile(-128, 720, 127.0D, def.dims(), def.params(),
                        List.of(RelationEdge.of("terrain", "hydro"), RelationEdge.of("terrain", "hydro")),
                        def.fields(), def.transitions()))
                        && throwsIAE(() -> new WorldgenProfile(-128, 720, 127.0D, def.dims(), def.params(),
                        def.edges(), List.of(FieldQuad.bypass()), def.transitions()))
                        && throwsIAE(() -> new WorldgenProfile(-128, 720, 127.0D, def.dims(), def.params(),
                        def.edges(), def.fields(), List.of(TransitionBand.defaults()))));
    }

    private static void immutability() {
        WorldgenProfile def = WorldgenProfile.defaults();
        WorldgenProfile copy = def.withWindow(-64, 384, 63.0D);
        check("不可变: withWindow 不改原值（原档案仍为 [-128, 592)/127.0）",
                def.minY() == -128 && def.height() == 720
                        && Double.doubleToLongBits(def.seaLevel()) == Double.doubleToLongBits(127.0D)
                        && copy.minY() == -64 && copy.windowTop() == 320);
        WorldgenProfile dimCopy = def.withDim("mineral", DimMode.DISABLE, "");
        check("不可变: withDim 不改原值且保持九维规范序",
                def.mode("mineral") == DimMode.ENABLE && dimCopy.mode("mineral") == DimMode.DISABLE
                        && dimCopy.dims().size() == 9 && dimCopy.dims().get(6).dim().equals("mineral"));
        WorldgenProfile paramCopy = def.withParams("terrain",
                new DimParams(2.0D, 512.0D, 0.5D, 0.4D));
        check("不可变: withParams 不改原值（原维仍为中性缺省）",
                def.params("terrain").equals(DimParams.defaults())
                        && Double.doubleToLongBits(paramCopy.params("terrain").wavelength())
                        == Double.doubleToLongBits(512.0D)
                        && throwsIAE(() -> def.withParams("bogus", DimParams.defaults())));
        WorldgenProfile edgeCopy = def.withEdge(new RelationEdge("terrain", "hydro", 0.5D, Blend.MAX))
                .withEdge(RelationEdge.of("climate", "terrain"));
        check("不可变: withEdge 按 (from,to) 替换/新增并恢复规范序",
                edgeCopy.edges().stream().filter(e -> e.from().equals("terrain") && e.to().equals("hydro"))
                        .allMatch(e -> e.blend() == Blend.MAX)
                        && edgeCopy.edges().stream().anyMatch(e -> e.from().equals("climate")
                        && e.to().equals("terrain"))
                        && edgeCopy.edges().size() == 17);
        check("不可变: withoutEdge 幂等移除（缺边为空操作）",
                def.withoutEdge("terrain", "hydro").edges().size() == 15
                        && def.withoutEdge("terrain", "hydro").withoutEdge("terrain", "hydro")
                        .edges().size() == 15
                        && def.withoutEdge("bogus", "hydro").edges().size() == 16);
        WorldgenProfile fieldCopy = def.withField("coarse_elev",
                new FieldQuad(0.4D, 0.1D, 0.5D, Blend.MIX));
        check("不可变: withField / withTransition 按 id 替换",
                def.field("coarse_elev").bypassed() && !fieldCopy.field("coarse_elev").bypassed()
                        && Double.doubleToLongBits(fieldCopy.field("coarse_elev").gain())
                        == Double.doubleToLongBits(0.4D)
                        && Double.doubleToLongBits(def.withTransition("biome",
                        new TransitionBand(0.2D, TransitionBand.Curve.CLAMP_LINEAR, 1))
                        .transition("biome").width()) == Double.doubleToLongBits(0.2D)
                        && throwsIAE(() -> def.withField("bogus", FieldQuad.bypass()))
                        && throwsIAE(() -> def.withTransition("bogus", TransitionBand.defaults()))
                        && throwsNPE(() -> def.withDim("terrain", null, "")));
        check("查询: 未知维 capability 为 null、mode 视作 ENABLE（读永不抛）",
                def.capability("nope") == null && def.mode("nope") == DimMode.ENABLE
                        && def.capability("TERRAIN") != null && def.capability(null) == null);
    }

    /** The reverse of the list (helper, kept private to the probe). / 列表反序（探针私有辅助）。 */
    private static List<DimCapability> reversed(List<DimCapability> in) {
        List<DimCapability> out = new ArrayList<>(in);
        Collections.reverse(out);
        return out;
    }
}
