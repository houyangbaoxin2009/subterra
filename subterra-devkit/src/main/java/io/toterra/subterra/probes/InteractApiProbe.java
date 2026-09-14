// p.2.33.7: interaction-domain contract mirror acceptance probe — asserts the API
// contract (api.interact.InteractApi) and the engine mirror (engine.interact.InteractApiMirror)
// are bit-for-bit identical on identical inputs, plus determinism re-entry and deterministic
// rejection of invalid inputs. Pure JVM: no wall-clock, no randomness, no MC classes.
package io.toterra.subterra.probes;

import io.toterra.subterra.api.interact.InteractApi;
import io.toterra.subterra.engine.interact.InteractApiMirror;

/**
 * p.2.33.7 交互域契约镜像验收探针 —— 断言 api 层 {@link InteractApi} 与 engine 镜像
 * {@link InteractApiMirror} 对同输入逐位一致（受控词汇固定序 / 零 HUD 系统气味黑名单与单趟扫描 /
 * P3 门禁 + P4 呈奉规则求值），并断言确定性再入与不合格输入的确定性拒绝（未知 kind/action/attitude →
 * {@link IllegalArgumentException}；零 HUD 扫描对 nll/空串确定性返回空串）。每项失败计数 +1 并给出诊断；
 * 全过才输出 {@code [InteractApiProbe] PASS (n checks)} 并 exit 0，否则 FAIL 计数 exit 1。全部线性遍历，
 * 无 O(n²)、无时序、无随机。
 * <p>
 * p.2.33.7 interaction-domain contract mirror probe: asserts the api-layer {@link InteractApi} and the engine
 * mirror {@link InteractApiMirror} are bit-for-bit identical for identical inputs (fixed controlled vocabulary /
 * zero-HUD system-scent blacklist and single-pass scan / P3-gate + P4-offer evaluation), plus determinism
 * re-entry and deterministic rejection of invalid inputs (unknown kind/action/attitude →
 * {@link IllegalArgumentException}; the zero-HUD scan returns an empty string deterministically for null/empty).
 * Every failure is counted and diagnosed; PASS only when all pass, then exit 0, else FAIL with counts and exit 1.
 * All traversals linear, no O(n²), no timing, no randomness.
 */
public final class InteractApiProbe {

    private static int checks = 0;
    private static int failures = 0;

    private InteractApiProbe() {
    }

    public static void main(String[] args) {
        vocabulary();
        zeroHud();
        evaluate();
        if (failures == 0) {
            System.out.println("[InteractApiProbe] PASS (" + checks + " checks)");
            System.exit(0);
        } else {
            System.out.println("[InteractApiProbe] FAIL (" + failures + " of " + checks + " checks)");
            System.exit(1);
        }
    }

    private static void vocabulary() {
        check("kinds fixed order npc/inscription/scroll/letter/relic",
                java.util.Arrays.asList("npc", "inscription", "scroll", "letter", "relic").equals(InteractApi.kinds()));
        check("kinds api == engine mirror", InteractApi.kinds().equals(InteractApiMirror.kinds()));
        check("actions fixed order converse/read/offer/examine",
                java.util.Arrays.asList("converse", "read", "offer", "examine").equals(InteractApi.actions()));
        check("actions api == engine mirror", InteractApi.actions().equals(InteractApiMirror.actions()));
        check("attitudes fixed order friendly/neutral/hostile",
                java.util.Arrays.asList("friendly", "neutral", "hostile").equals(InteractApi.attitudes()));
        check("attitudes api == engine mirror", InteractApi.attitudes().equals(InteractApiMirror.attitudes()));
        check("vocabulary deterministic re-entry",
                InteractApi.kinds().equals(InteractApi.kinds()) && InteractApi.actions().equals(InteractApi.actions()));
    }

    private static void zeroHud() {
        check("zeroHudBlacklist api == engine mirror", InteractApi.zeroHudBlacklist().equals(InteractApiMirror.zeroHudBlacklist()));
        check("zeroHudBlacklist deterministic re-entry",
                InteractApi.zeroHudBlacklist().equals(InteractApi.zeroHudBlacklist()));
        check("zeroHudCheck clean text -> empty both sides",
                InteractApi.zeroHudCheck("in-world language").isEmpty()
                        && InteractApiMirror.zeroHudCheck("in-world language").isEmpty());
        check("zeroHudCheck null/empty -> empty",
                InteractApi.zeroHudCheck(null).isEmpty() && InteractApi.zeroHudCheck("").isEmpty());
        // dirty Chinese text: "弹窗" present -> fixed reason SYSTEM_SCENT:弹窗.
        String dirty = "一道神秘的弹窗出现";
        check("zeroHudCheck dirty text api == mirror",
                InteractApi.zeroHudCheck(dirty).equals(InteractApiMirror.zeroHudCheck(dirty)));
        check("zeroHudCheck dirty reason prefix", InteractApi.zeroHudCheck(dirty).startsWith("SYSTEM_SCENT:"));
        // earliest-position semantics: "the task log" -> "task".
        String earliest = "the task log";
        check("zeroHudCheck earliest-position reason", InteractApi.zeroHudCheck(earliest).equals("SYSTEM_SCENT:task"));
        check("zeroHudCheck deterministic re-entry", InteractApi.zeroHudCheck(dirty).equals(InteractApi.zeroHudCheck(dirty)));
    }

    private static void evaluate() {
        String[] kinds = {"npc", "inscription", "scroll", "letter", "relic"};
        String[] actions = {"converse", "read", "offer", "examine"};
        String[] attitudes = {"friendly", "neutral", "hostile"};
        String[] held = {null, "moon-knife"};
        boolean grammarOk = true;
        boolean reentryOk = true;
        int cross = 0;
        for (String k : kinds) {
            for (String a : actions) {
                for (String t : attitudes) {
                    for (String item : held) {
                        InteractApi.Decision d1 = InteractApi.evaluate(k, a, t, item);
                        InteractApiMirror.Decision d2 = InteractApiMirror.evaluate(k, a, t, item);
                        if (d1.allowed() != d2.allowed() || !d1.message().equals(d2.message())) {
                            grammarOk = false;
                        }
                        if (!d1.message().equals(InteractApi.evaluate(k, a, t, item).message())) {
                            reentryOk = false;
                        }
                        cross++;
                    }
                }
            }
        }
        check("evaluate api == mirror across kind x action x attitude x held (" + cross + " cases)", grammarOk);
        check("evaluate deterministic re-entry (all " + cross + " cases)", reentryOk);
        // Spot semantic checks mirror engine precedence.
        check("examine npc rejected", !InteractApi.evaluate("npc", "examine", "friendly", null).allowed());
        check("examine relic allowed", InteractApi.evaluate("relic", "examine", "hostile", null).allowed());
        check("hostile converse rejected", !InteractApi.evaluate("scroll", "converse", "hostile", null).allowed());
        check("friendly read allowed", InteractApi.evaluate("letter", "read", "friendly", null).allowed());
        check("offer with held item interpolates item",
                InteractApi.evaluate("npc", "offer", "friendly", "moon-knife").message().contains("moon-knife"));
        // Invalid vocabulary rejected on both sides.
        check("evaluate rejects unknown kind both sides",
                throwsIAE(() -> InteractApi.evaluate("dragon", "read", "friendly", null))
                        && throwsIAE(() -> InteractApiMirror.evaluate("dragon", "read", "friendly", null)));
        check("evaluate rejects unknown action both sides",
                throwsIAE(() -> InteractApi.evaluate("npc", "dance", "friendly", null))
                        && throwsIAE(() -> InteractApiMirror.evaluate("npc", "dance", "friendly", null)));
        check("evaluate rejects unknown attitude both sides",
                throwsIAE(() -> InteractApi.evaluate("npc", "read", "hostile2", null))
                        && throwsIAE(() -> InteractApiMirror.evaluate("npc", "read", "hostile2", null)));
    }

    // ---------- helpers ----------

    private static void check(String name, boolean ok) {
        checks++;
        if (ok) {
            System.out.println("[PASS] " + name);
        } else {
            failures++;
            System.out.println("[FAIL] " + name);
        }
    }

    private static boolean throwsIAE(Runnable r) {
        try {
            r.run();
            return false;
        } catch (IllegalArgumentException e) {
            return true;
        }
    }
}