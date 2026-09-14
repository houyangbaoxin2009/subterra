// p.2.33.6: sim-domain contract mirror acceptance probe — asserts the API contract
// (api.sim.SimApi) and the engine mirror (engine.sim.core.SimApiMirror) are bit-for-bit
// identical on identical inputs, plus determinism re-entry and deterministic rejection of
// invalid inputs. Pure JVM: no wall-clock, no randomness, no MC classes.
package io.toterra.subterra.probes;

import io.toterra.subterra.api.sim.SimApi;
import io.toterra.subterra.engine.sim.core.SimApiMirror;

import java.util.List;

/**
 * p.2.33.6 sim 域契约镜像验收探针 —— 断言 api 层 {@link SimApi} 与 engine 镜像
 * {@link SimApiMirror} 对同输入逐位一致（预算三级固定序 / region/cell 右移映射 / 无溢出预算决策 /
 * Chebyshev 窗口 cell 枚举 / per-simulant-per-tick 种子派生），并断言确定性再入（同输入两次调用逐位一致）
 * 与不合格输入的确定性拒绝（cap &lt; 1、units &le; 0、used &lt; 0、radius &lt; 0、bits 越界 →
 * {@link IllegalArgumentException}）。每项失败计数 +1 并给出诊断；全过才输出
 * {@code [SimApiProbe] PASS (n checks)} 并 exit 0，否则 FAIL 计数 exit 1。全部线性遍历，无 O(n²)、
 * 无时序、无随机。
 * <p>
 * p.2.33.6 sim-domain contract mirror probe: asserts the api-layer {@link SimApi} and the engine mirror
 * {@link SimApiMirror} are bit-for-bit identical for identical inputs (fixed budget-tier order / region &amp;
 * cell right-shift maps / overflow-free budget decision / Chebyshev window-cell enumeration /
 * per-simulant-per-tick seed), plus determinism re-entry (two calls on the same input are bit-identical) and
 * deterministic rejection of invalid inputs (cap &lt; 1, units &le; 0, used &lt; 0, radius &lt; 0, out-of-range
 * bits → {@link IllegalArgumentException}). Every failure is counted and diagnosed; PASS only when all pass,
 * then exit 0, else FAIL with counts and exit 1. All traversals linear, no O(n²), no timing, no randomness.
 */
public final class SimApiProbe {

    private static int checks = 0;
    private static int failures = 0;

    private SimApiProbe() {
    }

    public static void main(String[] args) {
        tiers();
        regionOf();
        budgetAccepted();
        cellNeighbors();
        simSeed();
        if (failures == 0) {
            System.out.println("[SimApiProbe] PASS (" + checks + " checks)");
            System.exit(0);
        } else {
            System.out.println("[SimApiProbe] FAIL (" + failures + " of " + checks + " checks)");
            System.exit(1);
        }
    }

    private static void tiers() {
        List<String> a1 = SimApi.budgetTiers();
        List<String> a2 = SimApi.budgetTiers();
        List<String> m = SimApiMirror.budgetTiers();
        check("budgetTiers fixed order SIMULANT/REGION/GLOBAL",
                List.of("SIMULANT", "REGION", "GLOBAL").equals(a1));
        check("budgetTiers deterministic re-entry", a1.equals(a2));
        check("budgetTiers api == engine mirror", a1.equals(m));
    }

    private static void regionOf() {
        check("regionOf api == mirror (+scaling) positive",
                SimApi.regionOf(17, -9, 4).x() == SimApiMirror.regionOf(17, -9, 4).x()
                        && SimApi.regionOf(17, -9, 4).z() == SimApiMirror.regionOf(17, -9, 4).z());
        check("regionOf api == mirror negatives (arithmetic shift)",
                SimApi.regionOf(-31, -33, 3).x() == SimApiMirror.regionOf(-31, -33, 3).x()
                        && SimApi.regionOf(-31, -33, 3).z() == SimApiMirror.regionOf(-31, -33, 3).z());
        check("regionOf api re-entry", SimApi.regionOf(1025, 7, 6).equals(SimApi.regionOf(1025, 7, 6)));
        check("regionOf deterministic rejection (bits>63)",
                throwsIAE(() -> SimApi.regionOf(1, 1, 64))
                        && throwsIAE(() -> SimApiMirror.regionOf(1, 1, -1)));
    }

    private static void budgetAccepted() {
        // within all caps -> accept on both sides.
        boolean a = SimApi.budgetAccepted(10, 10, 30, 0, 0, 0, 5);
        boolean m = SimApiMirror.budgetAccepted(10, 10, 30, 0, 0, 0, 5);
        check("budgetAccepted accept api==mirror", a && m && a == m);
        // per-region overflow -> defer on both sides.
        boolean d1 = SimApi.budgetAccepted(100, 4, 100, 50, 3, 60, 2);
        boolean d2 = SimApiMirror.budgetAccepted(100, 4, 100, 50, 3, 60, 2);
        check("budgetAccepted defer on region overflow same both sides", !d1 && !d2);
        // exactly-at-cap still accepted (units == cap - used on every tier).
        boolean e1 = SimApi.budgetAccepted(5, 5, 5, 4, 4, 4, 1);
        boolean e2 = SimApiMirror.budgetAccepted(5, 5, 5, 4, 4, 4, 1);
        check("budgetAccepted accept-at-cap same both sides", e1 && e1 == e2);
        // determinism re-entry.
        check("budgetAccepted deterministic re-entry",
                SimApi.budgetAccepted(9, 9, 9, 2, 3, 4, 3) == SimApi.budgetAccepted(9, 9, 9, 2, 3, 4, 3));
        // invalid inputs rejected on both sides.
        check("budgetAccepted rejects cap<1 both sides",
                throwsIAE(() -> SimApi.budgetAccepted(0, 5, 5, 0, 0, 0, 1))
                        && throwsIAE(() -> SimApiMirror.budgetAccepted(5, 0, 5, 0, 0, 0, 1)));
        check("budgetAccepted rejects units<=0 both sides",
                throwsIAE(() -> SimApi.budgetAccepted(5, 5, 5, 0, 0, 0, 0))
                        && throwsIAE(() -> SimApiMirror.budgetAccepted(5, 5, 5, 0, 0, 0, -2)));
        check("budgetAccepted rejects negative used both sides",
                throwsIAE(() -> SimApi.budgetAccepted(5, 5, 5, -1, 0, 0, 1))
                        && throwsIAE(() -> SimApiMirror.budgetAccepted(5, 5, 5, 0, -1, 0, 1)));
    }

    private static void cellNeighbors() {
        // cellOf identity across scales (component-wise; the two sides' record types differ).
        check("cellOf api == mirror", SimApi.cellOf(63, -64, 4).x() == SimApiMirror.cellOf(63, -64, 4).x()
                && SimApi.cellOf(63, -64, 4).z() == SimApiMirror.cellOf(63, -64, 4).z());
        check("cellOf deterministic re-entry", SimApi.cellOf(7, 9, 2).equals(SimApi.cellOf(7, 9, 2)));
        check("cellOf deterministic rejection (bits out of range)",
                throwsIAE(() -> SimApi.cellOf(1, 1, 64)) && throwsIAE(() -> SimApiMirror.cellOf(1, 1, -1)));
        // neighborCells window enumeration identical to mirror (component-wise across the differing types).
        check("neighborCells api == mirror (radius 0)", sameCells(SimApi.neighborCells(3, 4, 0), SimApiMirror.neighborCells(3, 4, 0)));
        check("neighborCells api == mirror (radius 2)", sameCells(SimApi.neighborCells(-5, 6, 2), SimApiMirror.neighborCells(-5, 6, 2)));
        check("neighborCells window size (2r+1)^2",
                SimApi.neighborCells(0, 0, 3).size() == 49);
        check("neighborCells deterministic re-entry",
                SimApi.neighborCells(1, 1, 1).equals(SimApi.neighborCells(1, 1, 1)));
        check("neighborCells rejects negative radius both sides",
                throwsIAE(() -> SimApi.neighborCells(0, 0, -1)) && throwsIAE(() -> SimApiMirror.neighborCells(0, 0, -1)));
    }

    /** Component-wise comparison of an api cell list against a mirror cell list (linear). */
    private static boolean sameCells(List<SimApi.Cell> a, List<SimApiMirror.Cell> b) {
        if (a.size() != b.size()) {
            return false;
        }
        for (int i = 0; i < a.size(); i++) {
            if (a.get(i).x() != b.get(i).x() || a.get(i).z() != b.get(i).z()) {
                return false;
            }
        }
        return true;
    }

    private static void simSeed() {
        long[][] triples = {
                {1, 0, 0, 0}, {1, 1, 0, 0}, {42, 7, -3, 5},
                {99_999, 128, 100, 200}, {-77_777, 0, -50, -50}
        };
        boolean seedOk = true;
        boolean reentryOk = true;
        for (long[] t : triples) {
            if (SimApi.simSeed(t[0], t[1], t[2], t[3]) != SimApiMirror.simSeed(t[0], t[1], t[2], t[3])) {
                seedOk = false;
            }
            if (SimApi.simSeed(t[0], t[1], t[2], t[3]) != SimApi.simSeed(t[0], t[1], t[2], t[3])) {
                reentryOk = false;
            }
        }
        check("simSeed api == mirror across triples", seedOk);
        check("simSeed deterministic re-entry", reentryOk);
        // distinct (tick/x/z) triples must not trivially collide for a fixed seed.
        long s0 = SimApi.simSeed(1, 0, 0, 0);
        long s1 = SimApi.simSeed(1, 1, 0, 0);
        long s2 = SimApi.simSeed(1, 0, 1, 0);
        check("simSeed distinct inputs avalanche to distinct seeds",
                s0 != s1 && s0 != s2 && s1 != s2);
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