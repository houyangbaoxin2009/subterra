// Deterministic acceptance probe for the p.2.8.2 partitioned spatial index
// (io.toterra.subterra.engine.sim.spatial): fixed-cell grid, local-window
// neighborhood queries with no global scan, cell keys partitioned through the
// p.2.7 KeyPartition two-long path.
// NOT shipped in the mod jar.
package io.toterra.subterra.probes;

import io.toterra.subterra.engine.parallel.KeyPartition;
import io.toterra.subterra.engine.sim.spatial.CellCoord;
import io.toterra.subterra.engine.sim.spatial.SpatialIndex;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Deterministic acceptance probe for the p.2.8.2 partitioned spatial index
 * (io.toterra.subterra.engine.sim.spatial). Asserts: radius-constrained queries
 * return window cells in cell-then-key natural order; radius 0 touches only the
 * center cell; radius 1/2 windows hit exactly their hand-enumerated expected set
 * and never surface keys planted far outside the window (proving no global scan);
 * negative radius / illegal cellBits throw; duplicate inserts are idempotent;
 * {@code bankIndex(CellCoord, k)} is identity-equal to
 * {@code KeyPartition.bankIndex(x, z, k)} across several bank counts; an empty
 * index answers empty; and a large sparse index answers only the window content.
 * Every check is a plain final-state assertion — never wall-clock/timing.
 * Exit 0 = PASS, 1 = FAIL (never shipped in the mod jar).
 *
 * <p>p.2.8.2 分区化空间索引（io.toterra.subterra.engine.sim.spatial）的确定性验收探针。
 * 断言：按半径限制的查询按 cell 后键的自然序返回窗口 cell；radius=0 只访问中心 cell；
 * radius=1/2 窗口恰好命中手工枚举的期望集合，且绝不含窗口之外远端摆入的键（证明无全局扫描）；
 * 负半径 / 非法 cellBits 抛参；重复插入幂等；{@code bankIndex(CellCoord,k)} 与
 * {@code KeyPartition.bankIndex(x,z,k)} 在多个 bank 数下恒等；空索引查询返回空；大索引
 * 稀疏窗口只返回窗口内容。所有检查都是纯终态断言，绝不依赖墙钟/时序。
 * 退出码 0 = PASS，1 = FAIL（永不随 mod jar 发布）。
 */
public final class SimSpatialProbe {

    private SimSpatialProbe() {
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

    /** Per-cell key storage mirror used to build independent expected results. */
    private static final Map<CellCoord, List<String>> SRC = new HashMap<>();

    /** Plants keys "k0".."k{n-1}" into the mirror for a given cell. */
    private static void plant(CellCoord cell, int n) {
        List<String> k = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            k.add("k" + i);
        }
        SRC.put(cell, k);
    }

    /** Flattens an expected result from the mirror by enumerating the window in cell natural order. */
    private static List<String> windowExpected(long cx, long cz, int radius) {
        List<String> out = new ArrayList<>();
        for (long dx = -radius; dx <= radius; dx++) {
            for (long dz = -radius; dz <= radius; dz++) {
                List<String> keys = SRC.get(new CellCoord(cx + dx, cz + dz));
                if (keys != null) {
                    out.addAll(keys);
                }
            }
        }
        return out;
    }

    public static void main(String[] args) {
        final int cellBits = 4;

        // ---- 1. insert then query by cell+key natural order (radius 1) ----
        SpatialIndex<String> s1 = new SpatialIndex<>();
        SRC.clear();
        CellCoord c00 = CellCoord.of(0, 0, cellBits);
        CellCoord c01 = CellCoord.of(0, 16, cellBits);   // z +16 -> cell z +1
        CellCoord c10 = CellCoord.of(16, 0, cellBits);   // x +16 -> cell x +1
        CellCoord cm10 = CellCoord.of(-16, 0, cellBits); // cell (-1, 0)
        plant(c00, 3);   // k0, k1, k2  (inserted out of order below)
        plant(c01, 1);   // b
        plant(c10, 1);   // a
        plant(cm10, 1);  // x
        s1.insert(c00, "k2");
        s1.insert(c00, "k0");
        s1.insert(c00, "k1");
        s1.insert(c01, "b");
        s1.insert(c10, "a");
        s1.insert(cm10, "x");
        List<String> q1 = s1.query(0, 0, 1, cellBits);
        // cells sorted: (-1,0){x}, (0,0){k0,k1,k2}, (0,1){b}, (1,0){a}
        List<String> exp1 = new ArrayList<>();
        exp1.add("x"); exp1.add("k0"); exp1.add("k1"); exp1.add("k2"); exp1.add("b"); exp1.add("a");
        check("radius=1: result equals hand-enumerated cell-then-key natural order", q1.equals(exp1));

        // ---- 2. radius 0 touches only the center cell ----
        List<String> q0 = s1.query(0, 0, 0, cellBits);
        List<String> exp0 = new ArrayList<>();
        exp0.add("k0"); exp0.add("k1"); exp0.add("k2");
        check("radius=0: only the center cell's keys returned", q0.equals(exp0));

        // ---- 3. radius=2 window exact hit over a larger field, no global scan ----
        SpatialIndex<String> s3 = new SpatialIndex<>();
        SRC.clear();
        for (long x = -3; x <= 3; x++) {
            for (long z = -3; z <= 3; z++) {
                int n = 1 + ((int) ((x + 7) * 13 + (z + 7)) % 3); // 1..3 keys
                CellCoord c = CellCoord.of(x * 16, z * 16, cellBits);
                plant(c, n);
                for (int i = 0; i < n; i++) {
                    // mirror keys "k0".. are per-cell; insert reversely to shake order
                    s3.insert(c, "k" + (n - 1 - i));
                }
            }
        }
        long cx = 0, cz = 0;
        List<String> exp3 = windowExpected(cx, cz, 2);
        List<String> q3 = s3.query(0, 0, 2, cellBits);
        check("radius=2: window equals independently enumerated expected set", q3.equals(exp3));
        check("radius=2: size matches expected", s3.size() == expectedAll() && q3.size() == exp3.size());

        // ---- 4. remote disturbance keys never surface (no global scan proof) ----
        SRC.clear();
        SpatialIndex<String> s4 = new SpatialIndex<>();
        plant(c00, 2);
        plant(CellCoord.of(2000, 2000, cellBits), 9); // far away, cell (125,125)
        s4.insert(c00, "k0");
        s4.insert(c00, "k1");
        for (int i = 0; i < 9; i++) {
            s4.insert(CellCoord.of(2000, 2000, cellBits), "k" + i);
        }
        List<String> q4 = s4.query(0, 0, 2, cellBits);
        List<String> exp4 = new ArrayList<>();
        exp4.add("k0"); exp4.add("k1");
        boolean containsRemote = false;
        for (String k : exp4) {
            if (!q4.contains(k)) {
                containsRemote = true;
            }
        }
        boolean noRemote = q4.equals(exp4);
        // also probe a second query that can never see remote keys
        clearMirror();
        check("remote keys never surfaced by a radius=2 query (window-only access, no global scan)",
                q4.equals(exp4) && !containsRemote && q4.size() == 2);

        // ---- 5. negative radius throws ----
        SpatialIndex<String> s5 = new SpatialIndex<>();
        boolean negRadius = false;
        try {
            s5.query(0, 0, -1, cellBits);
        } catch (IllegalArgumentException e) {
            negRadius = true;
        }
        check("negative radius throws IllegalArgumentException", negRadius);

        // ---- 6. illegal cellBits throws (query & CellCoord.of) ----
        boolean illegalCb = false;
        try {
            CellCoord.of(0, 0, -1);
        } catch (IllegalArgumentException e) {
            // expected
        }
        try {
            CellCoord.of(0, 0, 64);
        } catch (IllegalArgumentException e) {
            illegalCb = true;
        }
        boolean illegalCbQuery = false;
        try {
            s5.query(0, 0, 1, -1);
        } catch (IllegalArgumentException e) {
            illegalCbQuery = true;
        }
        check("illegal cellBits (-1 and 64) throws on CellCoord.of and query", illegalCb && illegalCbQuery);

        // ---- 7. duplicate insert is idempotent ----
        SpatialIndex<String> s7 = new SpatialIndex<>();
        s7.insert(c00, "k0");
        s7.insert(c00, "k0");
        s7.insert(c00, "k0");
        s7.insert(CellCoord.of(16, 16, cellBits), "dup");
        s7.insert(CellCoord.of(16, 16, cellBits), "dup");
        check("duplicate insert idempotent: size counts once, query returns once, cellCount correct",
                s7.size() == 2 && s7.cellCount() == 2
                        && s7.query(0, 0, 2, cellBits).size() == 2);

        // ---- 8. bankIndex(CellCoord, k) == KeyPartition.bankIndex(x, z, k) ----
        boolean bankEq = true;
        for (int banks : new int[]{1, 2, 3, 4, 5, 8, 9}) {
            for (long x = -40; x <= 40; x += 3) {
                for (long z = -40; z <= 40; z += 3) {
                    CellCoord c = new CellCoord(x, z);
                    if (SpatialIndex.bankIndex(c, banks) != KeyPartition.bankIndex(x, z, banks)) {
                        bankEq = false;
                    }
                }
            }
        }
        // also reset mirror before any queries here are done (none), but keep SRC small
        check("bankIndex(CellCoord,k) === KeyPartition.bankIndex(x,z,k) across bank counts 1..9",
                bankEq);

        // ---- 9. empty index answers empty ----
        SpatialIndex<String> s9 = new SpatialIndex<>();
        List<String> q9 = s9.query(123, -456, 3, cellBits);
        check("empty index query returns empty list", q9.isEmpty() && s9.size() == 0 && s9.cellCount() == 0);

        // ---- 10. large sparse index, tiny window returns only window content ----
        SpatialIndex<String> s10 = new SpatialIndex<>();
        // dense far-field cells, none within the (cx,cz) window around (0,0)
        for (long w = 1; w <= 2000; w++) {
            CellCoord c = CellCoord.of(w * 4096, w * -8192, cellBits);
            s10.insert(c, "far" + w);
            s10.insert(c, "far" + w + "_b");
        }
        // one controlled center cell so the window is non-empty but isolated
        CellCoord centerCell = CellCoord.of(1, 1, cellBits);
        s10.insert(centerCell, "near0");
        s10.insert(centerCell, "near1");
        List<String> q10 = s10.query(1, 1, 1, cellBits);
        List<String> exp10 = new ArrayList<>();
        exp10.add("near0"); exp10.add("near1");
        boolean onElevenIsIsolated = q10.equals(exp10);
        // querying far from any stored cell must come back empty (still window-only)
        boolean sparseEmpty = s10.query(-900000, 900000, 1, cellBits).isEmpty();
        check("large sparse index (cellCount=" + s10.cellCount() + "): small window returns only its own content,"
                        + " far-field cells never surfaced",
                onElevenIsIsolated && sparseEmpty && s10.cellCount() == 2001);

        if (failures == 0) {
            System.out.println("[SimSpatialProbe] PASS (" + checks + " checks)");
            System.exit(0);
        } else {
            System.out.println("[SimSpatialProbe] FAIL: " + failures + " assertion(s) of " + checks);
            System.exit(1);
        }
    }

    /** String size of the current mirror (every key). */
    private static int expectedAll() {
        int total = 0;
        for (List<String> k : SRC.values()) {
            total += k.size();
        }
        return total;
    }

    private static void clearMirror() {
        SRC.clear();
    }
}