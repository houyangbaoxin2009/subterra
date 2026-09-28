package io.toterra.subterra.probes;

import io.toterra.subterra.api.worldgen.DimCapability;
import io.toterra.subterra.api.worldgen.DimMode;
import io.toterra.subterra.api.worldgen.EcoDim;
import io.toterra.subterra.api.worldgen.WorldgenProfile;
import io.toterra.subterra.engine.worldgen.profile.WorldgenProfileMirror;

import java.util.ArrayList;
import java.util.List;

/**
 * p.2.36.2（探针）— 世界生成配置面地基探针：把 {@code api.worldgen.WorldgenProfile}
 * （窗口 + 海平面 + 九维能力位，td + api 双接口）与 {@code engine.worldgen.profile.WorldgenProfileMirror}
 * （逐字镜像）锚定为纯 JVM 断言。纯 JVM——不碰 MC、无时序、无随机；exit 0 = PASS，exit 1 = FAIL；
 * 不进 mod jar。分节：
 * <ol>
 *   <li><b>缺省与窗口门</b>：窗口 {@code [-128, 592)} / 海平面 {@code 127.0} / 窗口上沿 {@code 592}，
 *       海平面严格落在窗口内（p.1.8.33 淹没回归的直接守门）。</li>
 *   <li><b>九维固定序</b>：api {@code EcoDim.ALL} 九维序 == mirror {@code DIM_ORDER}，缺省全 ENABLE。</li>
 *   <li><b>td 往返</b>：渲染 → 解析逐字节往返；部分文档回落缺省；注释/头/多余逗号容忍。</li>
 *   <li><b>api ↔ mirror 逐位一致</b>：缺省与三种文档的 td 逐字节相等，且同一组非法输入同拒。</li>
 *   <li><b>确定性拒绝</b>：窗口对齐、海平面越窗、非有限海平面、未知/重复键、畸形条目、坏模式值、
 *       重定向目标契约，全部 IAE（null 为 NPE）。</li>
 *   <li><b>不可变与查询</b>：{@code withWindow} / {@code withDim} 不改原值、保持规范序。</li>
 * </ol>
 * <p>
 * p.2.36.2 (probe) — the worldgen configuration foundation probe: anchors {@code api.worldgen.WorldgenProfile}
 * (window + sea level + nine-dimension capability bits, td + api dual interface) and its verbatim engine
 * mirror to pure-JVM assertions. Pure JVM — no MC runtime, no timing, no randomness; exit 0 = PASS, exit 1
 * = FAIL; never shipped in the mod jar. Sections: defaults and the window gate / the fixed nine-dimension
 * order / td round-trip / api↔mirror bitwise parity (equal td bytes and equal rejection sets) / deterministic
 * rejection (window alignment, sea level outside the window, non-finite sea level, unknown or duplicate keys,
 * malformed entries, bad mode values, redirect-target contract — all IAE, NPE for null) / immutability and
 * lookups.
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
        for (DimCapability cap : def.dims()) {
            profileIds.add(cap.dim());
        }
        check("九维固定序: profile 能力位 == EcoDim.ALL 九维序", apiIds.equals(profileIds));
        check("九维固定序: mirror DIM_ORDER == api EcoDim.ALL 序",
                WorldgenProfileMirror.DIM_ORDER.equals(apiIds));
        boolean allEnabled = true;
        for (DimCapability cap : def.dims()) {
            allEnabled &= cap.mode() == DimMode.ENABLE && !cap.redirected()
                    && cap.target().equals(DimCapability.NO_TARGET);
        }
        check("缺省九维全 ENABLE、无重定向目标", allEnabled && def.dims().size() == 9);
    }

    private static void tdRoundTrip() {
        WorldgenProfile def = WorldgenProfile.defaults();
        String td = def.td();
        check("td 往返: defaults 渲染 → 解析逐字节恒等",
                WorldgenProfile.fromTd(td).equals(def)
                        && WorldgenProfile.fromTd(td).td().equals(td));
        check("td 渲染: 规范形状（[ / min_y / height / sea_level / dim.* 九行 / ]）",
                td.startsWith("[\n  min_y = -128,\n  height = 720,\n  sea_level = 127.0,\n")
                        && td.endsWith(",\n]") && td.split("\n").length == 14);
        check("td 渲染确定性: 两次渲染逐字节相同", def.td().equals(WorldgenProfile.fromTd(td).td()));

        WorldgenProfile tuned = def.withWindow(-64, 384, 63.0D)
                .withDim("litho", DimMode.REDIRECT, "overturn:litho");
        String tunedTd = tuned.td();
        WorldgenProfile back = WorldgenProfile.fromTd(tunedTd);
        check("td 往返: 改窗 + 重定向维（min_y=-64/height=384/sea=63.0, litho→overturn:litho）",
                back.equals(tuned)
                        && back.minY() == -64 && back.height() == 384 && back.windowTop() == 320
                        && back.mode("litho") == DimMode.REDIRECT
                        && back.capability("litho").target().equals("overturn:litho"));
        check("td 往返: 非重定向维保持 ENABLE（改窗不影响能力位）",
                back.mode("terrain") == DimMode.ENABLE && back.mode("relic") == DimMode.ENABLE);

        String partial = "type tie<data>\n// a comment\n[\n  sea_level = 100.0, // trailing\n  ,,\n]";
        WorldgenProfile parsed = WorldgenProfile.fromTd(partial);
        check("td 解析: 部分文档回落缺省 + 容忍 type 头/注释/多余逗号",
                parsed.minY() == -128 && parsed.height() == 720
                        && Double.doubleToLongBits(parsed.seaLevel()) == Double.doubleToLongBits(100.0D)
                        && parsed.mode("climate") == DimMode.ENABLE);
        String disabled = "[\n  dim.hydro = disable,\n  dim.fauna = redirect:overturn:fauna,\n]";
        WorldgenProfile dims = WorldgenProfile.fromTd(disabled);
        check("td 解析: 单维改写（disable / redirect 双形态）",
                dims.mode("hydro") == DimMode.DISABLE && dims.mode("fauna") == DimMode.REDIRECT
                        && dims.capability("fauna").target().equals("overturn:fauna")
                        && dims.mode("mineral") == DimMode.ENABLE);
    }

    private static void apiMirrorParity() {
        List<String> docs = List.of(
                WorldgenProfile.defaults().td(),
                "[\n  min_y = -64,\n  height = 384,\n  sea_level = 63.0,\n]",
                "[\n  dim.litho = redirect:overturn:litho,\n]",
                "[\n  height = 512,\n]");
        boolean allEqual = true;
        for (String doc : docs) {
            String apiTd = WorldgenProfile.fromTd(doc).td();
            String mirrorTd = WorldgenProfileMirror.fromTd(doc).td();
            allEqual &= apiTd.equals(mirrorTd);
        }
        check("api ↔ mirror: 四种文档解析 → 渲染逐字节一致", allEqual);
        check("api ↔ mirror: 缺省档案逐字节一致",
                WorldgenProfile.defaults().td().equals(WorldgenProfileMirror.defaults().td()));

        List<String> bad = List.of(
                "",
                "[\n  height = 100,\n]",                     // not 16-aligned
                "[\n  height = 0,\n]",
                "[\n  min_y = -100,\n]",                     // not 16-aligned
                "[\n  min_y = 1984,\n  height = 96,\n]",     // top > 2032
                "[\n  min_y = -128,\n  height = 720,\n  sea_level = -128,\n]",  // sea == bottom
                "[\n  sea_level = 592,\n]",                  // sea == top
                "[\n  sea_level = nan,\n]",                  // non-finite
                "[\n  dim.bogus = enable,\n]",
                "[\n  dim.terrain = enable,\n  dim.terrain = disable,\n]",
                "[\n  height = 384,\n  height = 384,\n]",
                "[\n  nope = 1,\n]",
                "[\n  height,\n]",
                "[\n  dim.terrain = maybe,\n]",
                "[\n  dim.terrain = redirect:,\n]");
        boolean apiRejectsAll = true;
        boolean mirrorSame = true;
        for (String doc : bad) {
            boolean apiReject = throwsIAE(() -> WorldgenProfile.fromTd(doc));
            boolean mirrorReject = WorldgenProfileMirror.rejects(doc);
            apiRejectsAll &= apiReject;
            mirrorSame &= apiReject == mirrorReject;
        }
        check("确定性拒绝: 十五种非法文档 api 全部 IAE 拒绝", apiRejectsAll);
        check("api ↔ mirror: 同一组非法输入拒绝结果完全相同", mirrorSame);
        check("确定性拒绝: null 源 NPE（api 与 mirror 同）",
                throwsNPE(() -> WorldgenProfile.fromTd(null))
                        && throwsNPE(() -> WorldgenProfileMirror.fromTd(null)));
    }

    private static void deterministicRejection() {
        WorldgenProfile def = WorldgenProfile.defaults();
        check("窗口对齐: height / min_y 非 16 倍数 IAE（含构造直接入参）",
                throwsIAE(() -> new WorldgenProfile(-128, 100, 127.0D, def.dims()))
                        && throwsIAE(() -> new WorldgenProfile(-100, 720, 127.0D, def.dims()))
                        && throwsIAE(() -> new WorldgenProfile(-128, 0, 127.0D, def.dims()))
                        && throwsIAE(() -> new WorldgenProfile(-128, -16, 127.0D, def.dims())));
        check("窗口界: min_y + height > 2032 与 min_y < -2032 均 IAE",
                throwsIAE(() -> new WorldgenProfile(1984, 96, 2000.0D, def.dims()))
                        && throwsIAE(() -> new WorldgenProfile(-2048, 720, 0.0D, def.dims())));
        check("海平面: 等于下沿 / 等于上沿 / 越窗 / 非有限（NaN、±Inf）均 IAE",
                throwsIAE(() -> new WorldgenProfile(-128, 720, -128.0D, def.dims()))
                        && throwsIAE(() -> new WorldgenProfile(-128, 720, 592.0D, def.dims()))
                        && throwsIAE(() -> new WorldgenProfile(-128, 720, 1000.0D, def.dims()))
                        && throwsIAE(() -> new WorldgenProfile(-128, 720, Double.NaN, def.dims()))
                        && throwsIAE(() -> new WorldgenProfile(-128, 720, Double.POSITIVE_INFINITY, def.dims())));
        check("能力位: 未知维 / 缺维 / 多维 / 乱序 均 IAE",
                throwsIAE(() -> new DimCapability("bogus", DimMode.ENABLE, ""))
                        && throwsIAE(() -> new WorldgenProfile(-128, 720, 127.0D, List.of()))
                        && throwsIAE(() -> new WorldgenProfile(-128, 720, 127.0D,
                        java.util.stream.Stream.concat(def.dims().stream(),
                                java.util.stream.Stream.of(DimCapability.enabled("terrain"))).toList()))
                        && throwsIAE(() -> new WorldgenProfile(-128, 720, 127.0D, reversed(def.dims()))));
        check("能力位: 重定向目标契约（空目标 / 无冒号 / 多冒号 / 非法字符 → IAE；非重定向带目标 → IAE）",
                throwsIAE(() -> new DimCapability("litho", DimMode.REDIRECT, ""))
                        && throwsIAE(() -> new DimCapability("litho", DimMode.REDIRECT, "overturn.litho"))
                        && throwsIAE(() -> new DimCapability("litho", DimMode.REDIRECT, "a:b:c"))
                        && throwsIAE(() -> new DimCapability("litho", DimMode.REDIRECT, "Bad:Path"))
                        && throwsIAE(() -> new DimCapability("litho", DimMode.ENABLE, "overturn:litho")));
        check("模式解析: 别名 on/off/1/0  tolerant，未知值 IAE（null NPE）",
                DimMode.parse("on") == DimMode.ENABLE && DimMode.parse("OFF") == DimMode.DISABLE
                        && DimMode.parse("1") == DimMode.ENABLE && DimMode.parse("0") == DimMode.DISABLE
                        && throwsIAE(() -> DimMode.parse("maybe"))
                        && throwsNPE(() -> DimMode.parse(null)));
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
        check("不可变: withDim 未知维 IAE、null 模式 NPE",
                throwsIAE(() -> def.withDim("bogus", DimMode.ENABLE, ""))
                        && throwsNPE(() -> def.withDim("terrain", null, "")));
        check("查询: 未知维 capability 为 null、mode 视作 ENABLE（读永不抛）",
                def.capability("nope") == null && def.mode("nope") == DimMode.ENABLE
                        && def.capability("TERRAIN") != null && def.capability(null) == null);
    }

    /** The reverse of the list (helper, kept private to the probe). / 列表反序（探针私有辅助）。 */
    private static List<DimCapability> reversed(List<DimCapability> in) {
        List<DimCapability> out = new ArrayList<>(in);
        java.util.Collections.reverse(out);
        return out;
    }
}
