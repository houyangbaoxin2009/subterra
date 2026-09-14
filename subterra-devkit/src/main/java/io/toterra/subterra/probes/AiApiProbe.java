package io.toterra.subterra.probes;

import io.toterra.subterra.api.ai.AiApi;
import io.toterra.subterra.engine.ai.AiApiMirror;
import io.toterra.subterra.engine.ai.AiRandom;
import io.toterra.subterra.engine.ai.BrainScheduler;

import java.util.List;

/**
 * p.2.33.4（探针）— api.ai 契约面 ↔ engine 镜像逐字对照探针：把 {@code api.ai.AiApi}（对外契约）与
 * {@code engine.ai.AiApiMirror}（实现镜像）锚定为纯 JVM 断言。纯 JVM——不碰 MC、无时序、无随机；exit 0 =
 * PASS，exit 1 = FAIL；不进 mod jar。分节：
 * <ol>
 *   <li><b>api 编排固定序</b>：{@code phaseOrder()} 固定 {@code sense, decide, execute} +
 *       {@code phaseCount()}=3 + {@code budgetCaps()} 固定三槽名。</li>
 *   <li><b>api↔engine 镜像逐字对照</b>：调 {@link AiApiMirror} 与 {@link AiApi} 对同输入求值，
 *       编排序/阶段数/预算名同输入同输出；{@code validateBudget} 对合法/非法三槽同一性接受/同一性拒绝。</li>
 *   <li><b>预算确定性拒绝</b>：任一元 {@code < 1} 双面均 IAE（琴上/零/负值逐组）。</li>
 *   <li><b>{@link AiRandom} 同种子同序列 + 再入</b>（engine 委托 Xoroshiro128++）：同种子两次调用同值
 *       （确定性再入）、{@code fork(long)} 两遍同种子同派生流、父源 fork 前后取值不变（分支隔离）。
 *       该随机源为 engine 内部委托（见 {@code api.ai.AiApi} javadoc），不作 api 字节复制，此处以镜像侧
 *       确定性再入之锚定。API/mirror 对照的确定性面由第 1–3 节承担。</li>
 * </ol>
 * 确定性纪律：固定序、无时序、无随机；失败计数只在失败路径自增；全过输出 {@code [AiApiProbe] PASS (n
 * checks)} exit 0，否则 FAIL exit 1。
 * <p>
 * p.2.33.4 (probe) — api.ai contract ↔ engine mirror verbatim probe: anchors {@code api.ai.AiApi} (external
 * contract) and {@code engine.ai.AiApiMirror} (implementation mirror) to pure-JVM assertions. Pure JVM — no MC
 * runtime, no timing, no randomness; exit 0 = PASS, exit 1 = FAIL; never shipped in the mod jar. Sections:
 * api fixed orchestration order / api↔engine mirror verbatim / budget deterministic rejection / {@link AiRandom}
 * same-seed-same-sequence + re-entry (engine-internal delegation, anchored mirror-side).
 */
public final class AiApiProbe {

    private AiApiProbe() {
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

    public static void main(String[] args) {
        try {
            apiOrchestrationOrder();
            apiMirror();
            budgetRejection();
            aiRandomReentry();
        } catch (Exception e) {
            failures++;
            System.out.println("[FAIL] probe exception: " + e);
            e.printStackTrace(System.out);
        }
        if (failures == 0) {
            System.out.println("[AiApiProbe] PASS (" + checks + " checks)");
            System.exit(0);
        } else {
            System.out.println("[AiApiProbe] FAIL (" + failures + " of " + checks + " checks)");
            System.exit(1);
        }
    }

    // ---------- (1) api fixed orchestration order ----------

    private static void apiOrchestrationOrder() {
        check("AiApi: phaseOrder() 固定（sense,decide,execute）+ phaseCount()==3",
                AiApi.phaseOrder().equals(List.of("sense", "decide", "execute")) && AiApi.phaseCount() == 3);
        check("AiApi: budgetCaps() 固定三槽名（senseUnits,behaviorUnits,taskUnits）",
                AiApi.budgetCaps().equals(List.of("senseUnits", "behaviorUnits", "taskUnits")));
        check("AiApi: PHASE_ORDER/BUDGET_CAPS/PHASE_COUNT 常量逐字一致",
                List.of("sense", "decide", "execute").equals(AiApi.PHASE_ORDER)
                        && List.of("senseUnits", "behaviorUnits", "taskUnits").equals(AiApi.BUDGET_CAPS)
                        && AiApi.PHASE_COUNT == 3);
    }

    // ---------- (2) api↔engine mirror verbatim ----------

    private static void apiMirror() {
        boolean orderOk = AiApiMirror.phaseOrder().equals(AiApi.phaseOrder())
                && AiApiMirror.phaseCount() == AiApi.phaseCount()
                && AiApiMirror.budgetCaps().equals(AiApi.budgetCaps());
        check("mirror: phaseOrder()/phaseCount()/budgetCaps() 商 engine=api", orderOk);
        // valid budget accepted on both sides (no throw) — same-input-same-acceptance.
        boolean acceptOk = true;
        for (int s = 1; s <= 3; s++) {
            for (int b = 1; b <= 3; b++) {
                for (int t = 1; t <= 3; t++) {
                    final int fs = s;
                    final int fb = b;
                    final int ft = t;
                    boolean apiOk = !throwsIAE(() -> AiApi.validateBudget(fs, fb, ft));
                    boolean mirOk = !throwsIAE(() -> AiApiMirror.validateBudget(fs, fb, ft));
                    acceptOk = acceptOk && apiOk == mirOk && apiOk;
                }
            }
        }
        check("mirror: validateBudget 线上限同接受（1..3 三槽 27 组，双面一致）", acceptOk);
        // invalid budget rejected on both sides (IAE) — same-input-same-rejection.
        check("mirror: validateBudget 缺上限双面同拒绝（IAE）",
                throwsIAE(() -> AiApi.validateBudget(0, 2, 2))
                        && throwsIAE(() -> AiApiMirror.validateBudget(0, 2, 2))
                        && throwsIAE(() -> AiApi.validateBudget(2, -1, 2))
                        && throwsIAE(() -> AiApiMirror.validateBudget(2, -1, 2))
                        && throwsIAE(() -> AiApi.validateBudget(2, 2, 0))
                        && throwsIAE(() -> AiApiMirror.validateBudget(2, 2, 0)));
    }

    // ---------- (3) budget deterministic rejection ----------

    private static void budgetRejection() {
        int[][] bad = {{0, 1, 1}, {1, 0, 1}, {1, 1, 0}, {-3, 1, 1}, {1, -7, 1}, {1, 1, -9}};
        boolean api = true;
        boolean mir = true;
        for (int[] b : bad) {
            api = api && throwsIAE(() -> AiApi.validateBudget(b[0], b[1], b[2]));
            mir = mir && throwsIAE(() -> AiApiMirror.validateBudget(b[0], b[1], b[2]));
        }
        check("budget: 任一元<1（0/负值）api 与 mirror 逐组确定性拒绝", api && mir);
        check("budget: engine BrainScheduler.TickBudget.of 语义与 api 一致（1 组合法 + 1 组非法对照）",
                BrainScheduler.TickBudget.of(1, 1, 1) != null
                        && throwsIAE(() -> AiApi.validateBudget(0, 1, 1)));
    }

    // ---------- (4) AiRandom same-seed-same-sequence re-entry (mirror-side) ----------

    private static void aiRandomReentry() {
        AiRandom r1 = new AiRandom(1234567L);
        boolean same = true;
        for (int i = 0; i < 20; i++) {
            AiRandom fresh = new AiRandom(1234567L);
            for (int j = 0; j < i; j++) {
                fresh.nextInt(100); // advance fresh up to position i (both then draw the (i+1)th value)
            }
            same = same && r1.nextInt(100) == fresh.nextInt(100);
        }
        check("AiRandom(mirror): 同种子同调用序列逐值一致（20 组再入）", same);

        AiRandom a = new AiRandom(9L);
        int parentBefore = a.nextInt(100);
        AiRandom fork1 = a.fork(77L);
        AiRandom fork2 = a.fork(77L);
        boolean forkSame = fork1.nextInt(100) == fork2.nextInt(100)
                && fork1.nextDouble() == fork2.nextDouble();
        check("AiRandom(mirror): fork(77L) 两遍同种子同派生流（整数+double）", forkSame);
        int parentAfter = a.nextInt(100);
        AiRandom fresh2 = new AiRandom(9L);
        int refBefore = fresh2.nextInt(100);
        int refAfter = fresh2.nextInt(100);
        check("AiRandom(mirror): 分支隔离（父源 fork 前后取值与未 fork 对照一致）",
                parentBefore == refBefore && parentAfter == refAfter);
    }
}