// Deterministic acceptance probe for the p.2.8.1 budget-tier scheduling core
// (io.toterra.subterra.engine.sim.budget) — per-simulant/region/global caps with
// key-order degradation under overrun. Never shipped in the mod jar.
package io.toterra.subterra.probes;

import io.toterra.subterra.engine.sim.budget.BudgetScheduler;
import io.toterra.subterra.engine.sim.budget.BudgetScheduler.ChargeResult;
import io.toterra.subterra.engine.sim.budget.RegionKey;
import io.toterra.subterra.engine.sim.budget.TickBudget;

import java.util.ArrayList;
import java.util.List;

/**
 * Deterministic acceptance probe for the p.2.8.1 budget-tier scheduling core
 * (io.toterra.subterra.engine.sim.budget). Asserts the three-tier atomic
 * accept/defer contract: exact hit on full / overrun / edge cases for each tier
 * individually, per-simulant cascading to cap then DEFERRED on the next charge,
 * per-region cascading, per-tick global cascading, overflow-safe and valid-input
 * rejection (illegal units / regionBits throw), correct regionOf shifting for
 * positive and negative coordinates, and determinism — two fresh schedulers over
 * the same request sequence (key order) yield identical decision streams plus
 * deterministically ordered {@code chargedSimulants()}/{@code deferredRegions()}.
 * Every check is a final-state assertion — never a wall-clock/timing assertion.
 * Exit 0 = PASS, 1 = FAIL (never shipped in the mod jar).
 *
 * <p>p.2.8.1 三级预算调度核心（io.toterra.subterra.engine.sim.budget）的确定性验收探针。
 * 断言三 tier 原子 accept/defer 契约：对每层各自的满/超/边缘用例精确命中、
 * per-simulant 级联（累计到 cap 后下一笔 DEFERRED）、per-region 级联、每 tick global 级联、
 * 非法输入拒绝（非法 units / regionBits 抛参）、regionOf 对正负坐标移位正确，
 * 以及确定性——两个全新调度器对同一请求序列（按 key 序）产出逐笔一致的决策流，
 * 且 {@code chargedSimulants()}/{@code deferredRegions()} 为确定性排序。
 * 所有检查都是终态断言，绝不依赖墙钟/时序。退出码 0 = PASS，1 = FAIL（永不随 mod jar 发布）。
 */
public final class SimBudgetProbe {

    /** Bounded simple String key for readable assertions. */
    record SK(String name) implements Comparable<SK> {
        @Override
        public int compareTo(SK o) {
            return name.compareTo(o.name);
        }
    }

    private SimBudgetProbe() {
    }

    private static int failures = 0;
    private static int checks = 0;

    private static void check(String name, boolean ok) {
        checks++;
        if (ok) {
            System.out.println("[PASS] " + name);
        } else {
            failures++;
            System.out.println("[FAIL] " + name);
        }
    }

    /** Records the per-request decision stream of a scheduler into a list (deterministic). */
    private static List<ChargeResult> drive(
            BudgetScheduler<SK> s, List<SK> keys, List<RegionKey> regions, long[] units) {
        List<ChargeResult> out = new ArrayList<>(keys.size());
        for (int i = 0; i < keys.size(); i++) {
            out.add(s.charge(keys.get(i), regions.get(i), units[i]));
        }
        return out;
    }

    public static void main(String[] args) {
        doneTierBudgetEdgeCases();
        perSimulantCascade();
        perRegionCascade();
        perTickGlobalCascade();
        determinism();
        rejectsInvalidInput();
        regionOfShift();

        if (failures == 0) {
            System.out.println("[SimBudgetProbe] PASS (" + checks + " checks)");
            System.exit(0);
        } else {
            System.out.println("[SimBudgetProbe] FAIL: " + failures + " assertion(s) of " + checks);
            System.exit(1);
        }
    }

    /** -- 1. three-tier budget: construction, full/hit edge, and clear overrun per tier. -- */
    private static void doneTierBudgetEdgeCases() {
        // Construction accessors and rejection of a <1 cap.
        TickBudget b = TickBudget.of(5, 7, 100);
        boolean ctorOk = b.simulantUnits() == 5 && b.regionUnits() == 7 && b.globalUnits() == 100;
        boolean ctorReject = rejectedBy(() -> TickBudget.of(0, 7, 100))
                && rejectedBy(() -> TickBudget.of(5, 0, 100))
                && rejectedBy(() -> TickBudget.of(5, 7, 0))
                && rejectedBy(() -> TickBudget.of(-1, 7, 100));
        check("TickBudget: of(5,7,100) accessors exact; of with any cap<1 throws IllegalArgumentException",
                ctorOk && ctorReject);

        // Distinct simulants and regions so only the per-simulant tier is exercised in each charge.
        List<SK> keys = List.of(new SK("a"), new SK("b"), new SK("c"));
        List<RegionKey> regions = List.of(new RegionKey(0, 0), new RegionKey(1, 0), new RegionKey(2, 0));

        // Simulant hit edge: budget (2, 100, 100). charge a=1 (ACCEPT), a=1 (ACCEPT, at cap),
        // a=1 (DEFERRED). b and c use fresh simulant slots.
        BudgetScheduler<SK> s = BudgetScheduler.create(TickBudget.of(2, 100, 100), 4);
        List<ChargeResult> r = drive(s,
                List.of(new SK("a"), new SK("a"), new SK("a"), new SK("b")),
                List.of(new RegionKey(0, 0), new RegionKey(0, 0), new RegionKey(0, 0), new RegionKey(1, 0)),
                new long[]{1, 1, 1, 1});
        List<RegionKey> defSim = s.deferredRegions();
        check("per-simulant tier: budget(2,100,100) a:1 ACCEPT, a:1 ACCEPT(cap), a:1 DEFERRED,"
                        + " b:1 ACCEPT; defers exactly {(0,0)}; global=3",
                r.equals(List.of(ChargeResult.ACCEPTED, ChargeResult.ACCEPTED,
                        ChargeResult.DEFERRED, ChargeResult.ACCEPTED))
                        && defSim.size() == 1 && defSim.get(0).equals(new RegionKey(0, 0))
                        && s.globalUnitsUsed() == 3);

        // Region tier: budget(100, 2, 100). two distinct simulants in the SAME region
        // (0,0) each charge 1 -> ACCEPT (region at cap), third distinct simulant in (0,0)
        // charge 1 -> DEFERRED.
        BudgetScheduler<SK> r2 = BudgetScheduler.create(TickBudget.of(100, 2, 100), 4);
        List<ChargeResult> rr = drive(r2,
                List.of(new SK("a"), new SK("b"), new SK("c")),
                List.of(new RegionKey(0, 0), new RegionKey(0, 0), new RegionKey(0, 0)),
                new long[]{1, 1, 1});
        check("per-region tier: budget(100,2,100) region(0,0): a:1 ACCEPT, b:1 ACCEPT(cap),"
                        + " c:1 DEFERRED; global=2",
                rr.equals(List.of(ChargeResult.ACCEPTED, ChargeResult.ACCEPTED, ChargeResult.DEFERRED))
                        && r2.globalUnitsUsed() == 2);
    }

    /** -- 2. per-simulant cascade: same key accumulates across calls to its cap. -- */
    private static void perSimulantCascade() {
        BudgetScheduler<SK> s = BudgetScheduler.create(TickBudget.of(3, 50, 50), 4);
        RegionKey r = new RegionKey(0, 0);
        List<ChargeResult> seq = new ArrayList<>();
        // 1,1,1 (ACCEPT, ACCEPT, ACCEPT->at cap=3), then 1 DEFERRED.
        for (long u : new long[]{1, 1, 1, 1}) {
            seq.add(s.charge(new SK("k"), r, u));
        }
        check("per-simulant cascade: budget(3,50,50) k charges 1+1+1=capped ACCEPT, 4th charge"
                        + " DEFERRED; chargedSimulants=[k]; global=3",
                seq.equals(List.of(ChargeResult.ACCEPTED, ChargeResult.ACCEPTED,
                        ChargeResult.ACCEPTED, ChargeResult.DEFERRED))
                        && s.chargedSimulants().equals(List.of(new SK("k")))
                        && s.globalUnitsUsed() == 3);
    }

    /** -- 3. per-region cascade: same region accumulates across many simulants. -- */
    private static void perRegionCascade() {
        BudgetScheduler<SK> s = BudgetScheduler.create(TickBudget.of(50, 5, 50), 4);
        RegionKey r = new RegionKey(7, -3);
        List<SK> keys = new ArrayList<>();
        List<RegionKey> regs = new ArrayList<>();
        long[] units = new long[5];
        for (int i = 0; i < 5; i++) {
            keys.add(new SK("k" + i)); // distinct simulant each time, so only region tier triggers
            regs.add(r);
            units[i] = 1;
        }
        // 1 each: simulant caps untouched, region hits 5 -> 5th still ACCEPT (== cap).
        List<ChargeResult> seq = drive(s, keys, regs, units);
        List<ChargeResult> deferred = new ArrayList<>(seq);
        deferred.add(s.charge(new SK("k5"), new RegionKey(7, -3), 1)); // now region overrun -> DEFERRED
        check("per-region cascade: budget(50,5,50) five distinct simulants region(7,-3) =>5"
                        + " ACCEPT(cap), 6th simulant same region DEFERRED; global=5",
                seq.equals(List.of(ChargeResult.ACCEPTED, ChargeResult.ACCEPTED, ChargeResult.ACCEPTED,
                        ChargeResult.ACCEPTED, ChargeResult.ACCEPTED))
                        && deferred.get(5) == ChargeResult.DEFERRED
                        && s.deferredRegions().equals(List.of(new RegionKey(7, -3)))
                        && s.globalUnitsUsed() == 5);
    }

    /** -- 4. per-tick global cascade: fresh simulants/regions but global cap is hit. -- */
    private static void perTickGlobalCascade() {
        BudgetScheduler<SK> s = BudgetScheduler.create(TickBudget.of(100, 100, 3), 4);
        List<SK> keys = List.of(new SK("a"), new SK("b"), new SK("c"), new SK("d"));
        List<RegionKey> regs = List.of(new RegionKey(0, 0), new RegionKey(1, 0), new RegionKey(2, 0), new RegionKey(3, 0));
        List<ChargeResult> seq = drive(s, keys, regs, new long[]{1, 1, 1, 1});
        check("per-tick global cascade: budget(100,100,3) three distinct simulants ACCEPT(=global"
                        + " cap), 4th DEFERRED; global=3; charged=[a,b,c] natural order; deferred={(3,0)}",
                seq.equals(List.of(ChargeResult.ACCEPTED, ChargeResult.ACCEPTED,
                        ChargeResult.ACCEPTED, ChargeResult.DEFERRED))
                        && s.globalUnitsUsed() == 3
                        && s.chargedSimulants().equals(List.of(new SK("a"), new SK("b"), new SK("c")))
                        && s.deferredRegions().equals(List.of(new RegionKey(3, 0))));
    }

    /** -- 5. determinism: two fresh schedulers, identical request stream -> identical decisions +
     *        deterministic ordering of chargedSimulants/deferredRegions. -- */
    private static void determinism() {
        List<SK> keys = List.of(new SK("delta"), new SK("alpha"), new SK("charlie"), new SK("bravo"));
        List<RegionKey> regs = List.of(new RegionKey(30, 0), new RegionKey(20, 5), new RegionKey(10, 0), new RegionKey(20, 5));
        long[] units = {2, 3, 9, 3};

        BudgetScheduler<SK> runA = BudgetScheduler.create(TickBudget.of(6, 6, 12), 4);
        BudgetScheduler<SK> runB = BudgetScheduler.create(TickBudget.of(6, 6, 12), 4);

        List<ChargeResult> a = drive(runA, keys, regs, units);
        List<ChargeResult> b = drive(runB, keys, regs, units);

        // Key order delta->alpha->charlie->bravo:
        //  delta:2  -> sim 2, reg(30,0) 2, global 2                       ACCEPT
        //  alpha:3 -> sim 3, reg(20,5) 3, global 5                        ACCEPT
        //  charlie:9 -> sim 9 > 6 (simulant cap)                          DEFERRED, region(10,0) flagged
        //  bravo:3  -> sim 3, reg(20,5) 3+3=6 (=region cap), global 8     ACCEPT
        List<ChargeResult> expected = List.of(
                ChargeResult.ACCEPTED, // delta
                ChargeResult.ACCEPTED, // alpha
                ChargeResult.DEFERRED, // charlie (simulant overrun)
                ChargeResult.ACCEPTED);// bravo

        boolean sameDecisions = a.equals(b) && a.equals(expected);
        List<SK> chargedA = runA.chargedSimulants();
        List<SK> chargedB = runB.chargedSimulants();
        // Accepted keys: delta, alpha, bravo -> natural order [alpha, bravo, delta].
        boolean sameCharged = chargedA.equals(chargedB)
                && chargedA.equals(List.of(new SK("alpha"), new SK("bravo"), new SK("delta")));
        List<RegionKey> defA = runA.deferredRegions();
        List<RegionKey> defB = runB.deferredRegions();
        boolean sameDef = defA.equals(defB) && defA.equals(List.of(new RegionKey(10, 0)));
        boolean sameGlobal = runA.globalUnitsUsed() == runB.globalUnitsUsed()
                && runA.globalUnitsUsed() == 8;
        check("determinism: two fresh schedulers, same key-order request stream -> identical decisions,"
                        + " identical natural-order chargedSimulants=[alpha,bravo,delta],"
                        + " identical deferredRegions=[(10,0)], identical global=8",
                sameDecisions && sameCharged && sameDef && sameGlobal);

        // Deterministic decision is independent of wall-clock: forcing an empty extra tick-free
        // request sequence (no-op) still yields the same result set state.
        BudgetScheduler<SK> s2 = BudgetScheduler.create(TickBudget.of(6, 6, 12), 4);
        drive(s2, keys, regs, units);
        check("determinism replay: re-driving an identical stream reproduces chargedSimulants"
                        + " and deferredRegions byte-identically (no timing dependence)",
                s2.chargedSimulants().equals(runA.chargedSimulants())
                        && s2.deferredRegions().equals(runA.deferredRegions())
                        && s2.globalUnitsUsed() == runA.globalUnitsUsed());
    }

    /** -- 6. invalid input rejection: bad units and bad regionBits throw. -- */
    private static void rejectsInvalidInput() {
        // units rejected even when no budget would be reached.
        BudgetScheduler<SK> s = BudgetScheduler.create(TickBudget.of(50, 50, 50), 4);
        boolean unitsRejected = rejectedBy(() -> s.charge(new SK("k"), new RegionKey(0, 0), 0))
                && rejectedBy(() -> s.charge(new SK("k"), new RegionKey(0, 0), -3))
                && rejectedBy(() -> s.charge(new SK("k"), new RegionKey(0, 0), Long.MIN_VALUE));
        check("invalid units: charge units<=0 (0, -3, Long.MIN_VALUE) throw IllegalArgumentException"
                        + " and leave state untouched (global=0, charged empty)",
                unitsRejected && s.globalUnitsUsed() == 0 && s.chargedSimulants().isEmpty());

        // regionBits bounds: 0 and 63 accepted; -1 and 64 rejected.
        boolean haveBounds = BudgetScheduler.create(TickBudget.of(1, 1, 1), 0) != null
                && BudgetScheduler.create(TickBudget.of(1, 1, 1), 63) != null;
        boolean badBounds = rejectedBy(() -> BudgetScheduler.create(TickBudget.of(1, 1, 1), -1))
                && rejectedBy(() -> BudgetScheduler.create(TickBudget.of(1, 1, 1), 64));
        check("invalid regionBits: create(..,0) and create(..,63) OK; create(..,-1) and"
                        + " create(..,64) throw IllegalArgumentException; create(..,null) throws",
                haveBounds && badBounds && throwsNpe(() -> BudgetScheduler.create(null, 4)));
    }

    /** -- 7. regionOf: correct arithmetic right-shift for positive, negative and zero coords. -- */
    private static void regionOfShift() {
        // regionBits=3: each region covers an 8x8 block of world cells — verify both coords.
        BudgetScheduler<SK> s = BudgetScheduler.create(TickBudget.of(1, 1, 1), 3);
        // regionBits=63: only the sign bit survives each shift, so world X >= 0 -> x 0, X < 0 -> -1.
        BudgetScheduler<SK> s63 = BudgetScheduler.create(TickBudget.of(1, 1, 1), 63);
        check("regionOf(3)=+: world(8,17)->(1,2); world(7,-31)->(0,-4); world(-1,-1)->(-1,-1);"
                        + " world(0,0)->(0,0); regionBits=63: world(Long.MAX,Long.MIN)->(0,-1)",
                s.regionOf(8, 17).equals(new RegionKey(1, 2))
                        && s.regionOf(7, -31).equals(new RegionKey(0, -4))
                        && s.regionOf(-1, -1).equals(new RegionKey(-1, -1))
                        && s.regionOf(0, 0).equals(new RegionKey(0, 0))
                        && s63.regionOf(Long.MAX_VALUE, Long.MIN_VALUE).equals(new RegionKey(0, -1)));
    }

    /** True if {@code body} threw a NullPointerException. */
    private static boolean throwsNpe(Runnable body) {
        try {
            body.run();
            return false;
        } catch (NullPointerException e) {
            return true;
        }
    }

    /** True if {@code body} threw an IllegalArgumentException. */
    private static boolean rejectedBy(Runnable body) {
        try {
            body.run();
            return false;
        } catch (IllegalArgumentException e) {
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }
}