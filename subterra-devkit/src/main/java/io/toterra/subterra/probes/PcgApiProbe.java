// p.2.33.8: pcg-domain contract mirror acceptance probe — asserts the API contract
// (api.pcg.PcgApi) and the engine mirror (engine.pcg.PcgApiMirror) are bit-for-bit
// identical on identical inputs, plus determinism re-entry and deterministic rejection
// of invalid inputs. Pure JVM: no wall-clock, no randomness, no MC classes.
package io.toterra.subterra.probes;

import io.toterra.subterra.api.pcg.PcgApi;
import io.toterra.subterra.engine.pcg.PcgApiMirror;

import java.util.List;

/**
 * p.2.33.8 PCG 域契约镜像验收探针 —— 断言 api 层 {@link PcgApi} 与 engine 镜像 {@link PcgApiMirror}
 * 对同输入逐位一致（契约陈述 / 异常原因固定序 / 槽 fork 盐方案 / 模板槽序遍历 / uniform 与 weighted 下标
 * 解析），并断言确定性再入与不合格输入的确定性拒绝（空池、非正权重、draw 越界 → {@link IllegalArgumentException}）。
 * 每项失败计数 +1 并给出诊断；全过才输出 {@code [PcgApiProbe] PASS (n checks)} 并 exit 0，否则 FAIL 计数
 * exit 1。全部线性遍历，无 O(n²)、无时序、无随机。
 * <p>
 * p.2.33.8 pcg-domain contract mirror probe: asserts the api-layer {@link PcgApi} and the engine mirror
 * {@link PcgApiMirror} are bit-for-bit identical for identical inputs (contract statement / fixed-order exception
 * reasons / slot fork-salt scheme / template slot-order traversal / uniform &amp; weighted index resolution), plus
 * determinism re-entry and deterministic rejection of invalid inputs (empty pool, non-positive weight, out-of-range
 * draw → {@link IllegalArgumentException}). Every failure is counted and diagnosed; PASS only when all pass, then
 * exit 0, else FAIL with counts and exit 1. All traversals linear, no O(n²), no timing, no randomness.
 */
public final class PcgApiProbe {

    private static int checks = 0;
    private static int failures = 0;

    private PcgApiProbe() {
    }

    public static void main(String[] args) {
        contractAndReasons();
        forkAndSlots();
        indices();
        if (failures == 0) {
            System.out.println("[PcgApiProbe] PASS (" + checks + " checks)");
            System.exit(0);
        } else {
            System.out.println("[PcgApiProbe] FAIL (" + failures + " of " + checks + " checks)");
            System.exit(1);
        }
    }

    private static void contractAndReasons() {
        check("contractStatement api == mirror",
                PcgApi.contractStatement().equals(PcgApiMirror.contractStatement()));
        check("contractStatement value",
                "same (seed, call sequence) -> same (choice sequence)".equals(PcgApi.contractStatement()));
        check("reasons fixed order",
                List.of("EMPTY_POOL", "NEGATIVE_WEIGHT", "BOUND", "UNKNOWN_SLOT", "SCHEMA_ROOT").equals(PcgApi.reasons()));
        check("reasons api == mirror", PcgApi.reasons().equals(PcgApiMirror.reasons()));
        check("reasons deterministic re-entry", PcgApi.reasons().equals(PcgApi.reasons()));
    }

    private static void forkAndSlots() {
        check("slotForkSalt api == mirror", PcgApi.slotForkSalt("adj").equals(PcgApiMirror.slotForkSalt("adj")));
        check("slotForkSalt scheme value", "slot:adj".equals(PcgApi.slotForkSalt("adj")));
        check("slotForkSalt distinct salts distinct", !PcgApi.slotForkSalt("a").equals(PcgApi.slotForkSalt("b")));

        String t1 = "The {adj} {noun} of {place}";
        String t2 = "{a}{b}{a}";
        String t3 = "no placeholders here";
        String t4 = "{unclosed";
        check("slotsInOrder api == mirror t1",
                PcgApi.slotsInOrder(t1).equals(PcgApiMirror.slotsInOrder(t1)));
        check("slotsInOrder left-to-right first-occurrence",
                List.of("adj", "noun", "place").equals(PcgApi.slotsInOrder(t1)));
        check("slotsInOrder repeated-slot occurrence order",
                List.of("a", "b", "a").equals(PcgApi.slotsInOrder(t2)));
        check("slotsInOrder no-placeholder empty",
                PcgApi.slotsInOrder(t3).isEmpty());
        check("slotsInOrder unclosed brace stops", PcgApi.slotsInOrder(t4).isEmpty());
        check("slotsInOrder api == mirror on all + null",
                PcgApi.slotsInOrder(t2).equals(PcgApiMirror.slotsInOrder(t2))
                        && PcgApi.slotsInOrder(null).equals(PcgApiMirror.slotsInOrder(null)));
        check("slotsInOrder deterministic re-entry",
                PcgApi.slotsInOrder(t1).equals(PcgApi.slotsInOrder(t1)));
    }

    private static void indices() {
        // uniform: api == mirror + boundaries.
        check("uniformIndex api == mirror", PcgApi.uniformIndex(5, 3) == PcgApiMirror.uniformIndex(5, 3));
        check("uniformIndex boundary 0", PcgApi.uniformIndex(5, 0) == 0);
        check("uniformIndex boundary size-1", PcgApi.uniformIndex(5, 4) == 4);
        check("uniformIndex deterministic re-entry", PcgApi.uniformIndex(7, 2) == PcgApi.uniformIndex(7, 2));
        check("uniformIndex rejects empty pool both sides",
                throwsIAE(() -> PcgApi.uniformIndex(0, 0)) && throwsIAE(() -> PcgApiMirror.uniformIndex(0, 0)));
        check("uniformIndex rejects out-of-range draw both sides",
                throwsIAE(() -> PcgApi.uniformIndex(5, 5)) && throwsIAE(() -> PcgApiMirror.uniformIndex(5, -1)));

        // weighted: api == mirror across several weight sets and draws.
        List<Long> w1 = List.of(1L, 1L, 1L);
        List<Long> w2 = List.of(5L, 2L, 3L);
        List<Long> w3 = List.of(10L);
        long[][] draws = {{0, 1, 2}, {0, 4, 5, 6, 9}, {0}};
        boolean wOk = true;
        boolean wRe = true;
        for (int i = 0; i < draws.length; i++) {
            List<Long> w = i == 0 ? w1 : (i == 1 ? w2 : w3);
            for (long d : draws[i]) {
                if (PcgApi.weightedIndex(w, d) != PcgApiMirror.weightedIndex(w, d)) {
                    wOk = false;
                }
                if (PcgApi.weightedIndex(w, d) != PcgApi.weightedIndex(w, d)) {
                    wRe = false;
                }
            }
        }
        check("weightedIndex api == mirror across weight sets", wOk);
        check("weightedIndex deterministic re-entry", wRe);
        // prefix-interval semantics: weights [5,2,3]: draw 0-4 -> 0; 5-6 -> 1; 7-9 -> 2.
        check("weightedIndex prefix interval [5,2,3] draw0->0", PcgApi.weightedIndex(w2, 0) == 0);
        check("weightedIndex prefix interval [5,2,3] draw5->1", PcgApi.weightedIndex(w2, 5) == 1);
        check("weightedIndex prefix interval [5,2,3] draw9->2", PcgApi.weightedIndex(w2, 9) == 2);
        check("weightedIndex rejects empty both sides",
                throwsIAE(() -> PcgApi.weightedIndex(List.of(), 0))
                        && throwsIAE(() -> PcgApiMirror.weightedIndex(List.of(), 0)));
        check("weightedIndex rejects non-positive weight both sides",
                throwsIAE(() -> PcgApi.weightedIndex(List.of(1L, 0L), 0))
                        && throwsIAE(() -> PcgApiMirror.weightedIndex(List.of(0L, 1L), 0)));
        check("weightedIndex rejects out-of-range draw both sides",
                throwsIAE(() -> PcgApi.weightedIndex(w1, 3))
                        && throwsIAE(() -> PcgApiMirror.weightedIndex(w1, -1)));
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