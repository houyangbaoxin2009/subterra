package io.toterra.subterra.probes;

import io.toterra.subterra.api.sky.SkyApi;
import io.toterra.subterra.engine.sky.SkyApiMirror;

import java.util.List;

/**
 * p.2.0.13（探针）— api.sky 契约面 ↔ engine 镜像逐字对照 + 覆盖存储行为探针：把
 * {@code api.sky.SkyApi}（对外契约 + 覆盖存储）与 {@code engine.sky.SkyApiMirror}（纯语义镜像）锚定为
 * 纯 JVM 断言。纯 JVM——不碰 MC、无时序、无随机；exit 0 = PASS，exit 1 = FAIL；不进 mod jar。分节：
 * <ol>
 *   <li><b>常量契约逐位对照</b>：{@code NO_CLOUDS}（NaN 位型）、
 *       {@code VANILLA_OVERWORLD_CLOUD_HEIGHT}（{@code 192.0} 逐位）、{@code DEFAULT_NAMESPACE}，
 *       api ↔ mirror 同值。</li>
 *   <li><b>api↔engine 镜像逐字对照</b>：{@code normalizeDimensionId} / {@code isValidDimensionId}
 *       对同一组输入（合法/缺省命名空间/大小写空白容忍/非法/多冒号/null）同输入同输出。</li>
 *   <li><b>覆盖存储行为</b>：set → get（含 NaN 隐藏哨兵）、isOverridden、clear 幂等回落、clearAll、
 *       {@code overrides()} 排序确定性、非法 id/非有限高度确定性拒绝（IAE）、确定性再入。
 *       存储为全局静态——finally 里 {@code clearAll()} 还原，探针不可泄漏状态。</li>
 * </ol>
 * 确定性纪律：固定序、无时序、无随机；失败计数只在失败路径自增；全过输出 {@code [SkyApiProbe] PASS (n
 * checks)} exit 0，否则 FAIL exit 1。
 * <p>
 * p.2.0.13 (probe) — api.sky contract ↔ engine mirror verbatim + override-store behaviour probe: anchors
 * {@code api.sky.SkyApi} (external contract + override store) and {@code engine.sky.SkyApiMirror} (pure
 * semantics mirror) to pure-JVM assertions. Pure JVM — no MC runtime, no timing, no randomness;
 * exit 0 = PASS, exit 1 = FAIL; never shipped in the mod jar. Sections: bitwise constant parity /
 * api↔engine mirror verbatim / override-store behaviour (set/get incl. the NaN hide sentinel, isOverridden,
 * idempotent clear fallback, clearAll, deterministic sorted overrides, deterministic rejection of illegal
 * ids and non-finite heights, re-entry). The store is a global static — the probe restores it with
 * {@code clearAll()} in a finally block; probes must not leak state.
 */
public final class SkyApiProbe {

    private SkyApiProbe() {
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
            constantsParity();
            apiMirror();
            storeBehaviour();
        } catch (Exception e) {
            failures++;
            System.out.println("[FAIL] probe exception: " + e);
            e.printStackTrace(System.out);
        }
        if (failures == 0) {
            System.out.println("[SkyApiProbe] PASS (" + checks + " checks)");
            System.exit(0);
        } else {
            System.out.println("[SkyApiProbe] FAIL (" + failures + " of " + checks + " checks)");
            System.exit(1);
        }
    }

    // ---------- (1) constant contracts, bitwise ----------

    private static void constantsParity() {
        check("SkyApi: NO_CLOUDS 为 NaN 位型（api 与 mirror 逐位）",
                Double.isNaN(SkyApi.NO_CLOUDS)
                        && Double.isNaN(SkyApiMirror.NO_CLOUDS)
                        && Double.doubleToRawLongBits(SkyApi.NO_CLOUDS)
                        == Double.doubleToRawLongBits(SkyApiMirror.NO_CLOUDS));
        check("SkyApi: VANILLA_OVERWORLD_CLOUD_HEIGHT == 192.0（api 与 mirror 逐位）",
                Double.doubleToLongBits(SkyApi.VANILLA_OVERWORLD_CLOUD_HEIGHT)
                        == Double.doubleToLongBits(192.0D)
                        && Double.doubleToLongBits(SkyApi.VANILLA_OVERWORLD_CLOUD_HEIGHT)
                        == Double.doubleToLongBits(SkyApiMirror.VANILLA_OVERWORLD_CLOUD_HEIGHT));
        check("SkyApi: DEFAULT_NAMESPACE == minecraft（api 与 mirror 同值）",
                SkyApi.DEFAULT_NAMESPACE.equals("minecraft")
                        && SkyApi.DEFAULT_NAMESPACE.equals(SkyApiMirror.DEFAULT_NAMESPACE));
    }

    // ---------- (2) api↔engine mirror verbatim ----------

    private static void apiMirror() {
        String[] legal = {"minecraft:overworld", " OVERWORLD ", "overworld", "toterra:sky_high",
                "Sky_High", "a:b"};
        boolean legalOk = true;
        for (String f : legal) {
            legalOk = legalOk && SkyApiMirror.normalizeDimensionId(f).equals(SkyApi.normalizeDimensionId(f))
                    && SkyApiMirror.isValidDimensionId(f) == SkyApi.isValidDimensionId(f)
                    && SkyApi.isValidDimensionId(f);
        }
        check("mirror: normalizeDimensionId/isValidDimensionId 同输入同输出（6 组合法，含缺省命名空间补全）",
                legalOk);
        check("mirror: 缺省命名空间补全逐字（'overworld' → 'minecraft:overworld'）",
                SkyApi.normalizeDimensionId("overworld").equals("minecraft:overworld"));
        String[] illegal = {"", " ", "a:b:c", "a::b", ":path", "ns:", "UP PER", "a/b c", "名"};
        boolean rejectOk = true;
        boolean mirrorRejectOk = true;
        for (String f : illegal) {
            rejectOk = rejectOk && throwsIAE(() -> SkyApi.normalizeDimensionId(f));
            mirrorRejectOk = mirrorRejectOk && throwsIAE(() -> SkyApiMirror.normalizeDimensionId(f));
        }
        check("api: 非法/空/多冒号/空侧/非法字符 维度 id 确定性拒绝（IAE）", rejectOk);
        check("mirror: 非法输入确定性拒绝与 api 同形", mirrorRejectOk);
        check("api: null 维度 id 确定性拒绝（NPE，requireNonNull 契约）",
                throwsNPE(() -> SkyApi.normalizeDimensionId(null))
                        && throwsNPE(() -> SkyApiMirror.normalizeDimensionId(null)));
        check("api: isValidDimensionId(null) == false（不走 NPE 路径也确定）",
                !SkyApi.isValidDimensionId(null) && !SkyApiMirror.isValidDimensionId(null));
    }

    // ---------- (3) override-store behaviour (global static, restore in finally) ----------

    private static void storeBehaviour() {
        try {
            SkyApi.setCloudHeight(" OVERWORLD ", 160.0D);
            check("store: set → get（有限值往返，' OVERWORLD ' 与 'minecraft:overworld' 同键）",
                    SkyApi.cloudHeight("minecraft:overworld").isPresent()
                            && Double.doubleToLongBits(SkyApi.cloudHeight("minecraft:overworld").getAsDouble())
                            == Double.doubleToLongBits(160.0D));
            check("store: 未覆盖维度返回空 OptionalDouble",
                    !SkyApi.cloudHeight("minecraft:the_nether").isPresent()
                            && !SkyApi.isOverridden("minecraft:the_nether"));
            SkyApi.setCloudHeight("minecraft:the_nether", SkyApi.NO_CLOUDS);
            check("store: NaN 隐藏哨兵可写入，读出为 NaN（隐藏语义）",
                    SkyApi.isOverridden("minecraft:the_nether")
                            && SkyApi.cloudHeight("minecraft:the_nether").isPresent()
                            && Double.isNaN(SkyApi.cloudHeight("minecraft:the_nether").getAsDouble()));
            check("store: overrides() 排序确定性（两连跑同字节）",
                    SkyApi.overrides().equals(SkyApi.overrides())
                            && SkyApi.overrides().equals(
                            List.of("minecraft:overworld", "minecraft:the_nether")));
            SkyApi.clearCloudHeight("MINECRAFT:OVERWORLD");
            check("store: clear 大小写容忍回落（'MINECRAFT:OVERWORLD' 清掉 'minecraft:overworld'）",
                    !SkyApi.isOverridden("minecraft:overworld")
                            && SkyApi.overrides().equals(List.of("minecraft:the_nether")));
            SkyApi.clearCloudHeight("minecraft:the_nether");
            SkyApi.clearCloudHeight("minecraft:the_nether");
            check("store: clear 幂等（重复 clear 不抛、完全回落）",
                    SkyApi.overrides().isEmpty());
            check("store: 无限高度确定性拒绝（±Inf 均 IAE，NaN 除外）",
                    throwsIAE(() -> SkyApi.setCloudHeight("minecraft:overworld", Double.POSITIVE_INFINITY))
                            && throwsIAE(() -> SkyApi.setCloudHeight("minecraft:overworld",
                            Double.NEGATIVE_INFINITY)));
            check("store: 非法 id 写入确定性拒绝（IAE）+ null 清除确定性拒绝（NPE）",
                    throwsIAE(() -> SkyApi.setCloudHeight("bad id!", 100.0D))
                            && throwsNPE(() -> SkyApi.clearCloudHeight(null)));
            SkyApi.setCloudHeight("minecraft:overworld", 160.0D);
            SkyApi.setCloudHeight("minecraft:overworld", 160.0D);
            check("store: 确定性再入 + 同键重写同值",
                    Double.doubleToLongBits(SkyApi.cloudHeight("minecraft:overworld").getAsDouble())
                            == Double.doubleToLongBits(160.0D));
        } finally {
            SkyApi.clearAll();
        }
        check("store: clearAll 全回落（探针零状态泄漏）", SkyApi.overrides().isEmpty());
    }
}
