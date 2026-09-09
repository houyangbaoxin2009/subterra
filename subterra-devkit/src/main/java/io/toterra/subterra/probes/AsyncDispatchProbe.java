// Deterministic acceptance probe for the p.2.6.1 async dispatch foundation
// (io.toterra.subterra.engine.worldgen.async). NOT shipped in the mod jar.
package io.toterra.subterra.probes;

import io.toterra.subterra.engine.worldgen.async.ChunkPartition;
import io.toterra.subterra.engine.worldgen.async.DeterministicDispatcher;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Deterministic acceptance probe for the p.2.6.1 async dispatch foundation
 * (io.toterra.subterra.engine.worldgen.async). Asserts the pure partition
 * contract, the exactly-once + sorted-merge contract of
 * {@link DeterministicDispatcher}, parallel-vs-serial byte equivalence, payload
 * isolation, and a concurrency smoke final-state. Every check is a final-state
 * assertion after the join — never a wall-clock/timing assertion. Exit 0 = PASS,
 * 1 = FAIL (never shipped in the mod jar).
 *
 * <p>p.2.6.1 异步分派基础设施（io.toterra.subterra.engine.worldgen.async）的确定性
 * 验收探针。断言纯分区契约、{@link DeterministicDispatcher} 的"恰好一次 + 有序归并"
 * 契约、并行 vs 串行字节等价、负载隔离，以及并发冒烟的终态。所有检查都是 join 之后的
 * 终态断言，绝不依赖墙钟/时序。退出码 0 = PASS，1 = FAIL（永不随 mod jar 发布）。
 */
public final class AsyncDispatchProbe {

    private AsyncDispatchProbe() {
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

    /** 8 bytes, big-endian, derived purely from (x, z) — deterministic, no shared state. */
    static byte[] payloadFor(long x, long z) {
        return new byte[]{
                (byte) (x >> 24), (byte) (x >> 16), (byte) (x >> 8), (byte) x,
                (byte) (z >> 24), (byte) (z >> 16), (byte) (z >> 8), (byte) z};
    }

    /** Builds the canonical deterministic task set: coords [0, n) x [0, n). */
    static List<long[]> taskSet(int n) {
        List<long[]> coords = new ArrayList<>(n * n);
        for (long x = 0; x < n; x++) {
            for (long z = 0; z < n; z++) {
                coords.add(new long[]{x, z});
            }
        }
        return coords;
    }

    static void submitAll(DeterministicDispatcher d, List<long[]> coords) {
        for (long[] c : coords) {
            long x = c[0];
            long z = c[1];
            d.submit(x, z, () -> payloadFor(x, z));
        }
    }

    public static void main(String[] args) {
        // ---- a. partition coverage -------------------------------------------------
        for (int desired = 1; desired <= 8; desired++) {
            int b = ChunkPartition.bankCount(desired);
            boolean inRange = true;
            int bank = ChunkPartition.bankIndex(0, 0, b);
            if (bank < 0 || bank >= b) {
                inRange = false;
            }
            boolean[] seen = new boolean[b];
            for (long x = 0; x < 64; x++) {
                for (long z = 0; z < 64; z++) {
                    int idx = ChunkPartition.bankIndex(x, z, b);
                    if (idx >= 0 && idx < b) {
                        seen[idx] = true;
                    } else {
                        inRange = false;
                    }
                }
            }
            boolean allCovered = true;
            for (boolean s : seen) {
                if (!s) {
                    allCovered = false;
                }
            }
            check("partition: bankCount(" + desired + ")=" + b + " in [1,8] pow2"
                    + " && all indices in range && every bank covered",
                    b >= 1 && b <= ChunkPartition.MAX_BANKS && (b & (b - 1)) == 0
                            && inRange && allCovered);
        }

        // ---- b. partition determinism ---------------------------------------------
        int bDet = ChunkPartition.bankCount(4);
        boolean identical = true;
        for (long x = 0; x < 128 && identical; x++) {
            for (long z = 0; z < 128; z++) {
                int first = ChunkPartition.bankIndex(x, z, bDet);
                int second = ChunkPartition.bankIndex(x, z, bDet);
                if (first != second) {
                    identical = false;
                    break;
                }
            }
        }
        check("partition: two independent calls produce identical mappings", identical);

        // ---- c. exactly-once (P=4 and P=1) ----------------------------------------
        runExactlyOnce(4);
        runExactlyOnce(1);

        // ---- d. parallel-vs-serial equivalence ------------------------------------
        List<long[]> coords = taskSet(64);
        List<DeterministicDispatcher.ChunkResult> serialResults = runResults(1, coords);
        List<DeterministicDispatcher.ChunkResult> parallelResults = runResults(4, coords);
        boolean elementWise = serialResults.size() == parallelResults.size();
        if (elementWise) {
            for (int i = 0; i < serialResults.size(); i++) {
                if (!Arrays.equals(serialResults.get(i).payload(), parallelResults.get(i).payload())) {
                    elementWise = false;
                    break;
                }
            }
        }
        check("equivalence: banks=1 vs banks=4 merged payload lists byte-identical (element-wise, same order)",
                elementWise && serialResults.size() == coords.size());

        // ---- e. payload correctness / isolation ------------------------------------
        boolean payloadOk = true;
        for (DeterministicDispatcher.ChunkResult r : serialResults) {
            if (!Arrays.equals(r.payload(), payloadFor(r.x(), r.z()))) {
                payloadOk = false;
                break;
            }
        }
        check("payload: each result equals its own coordinates' derived bytes (no cross-task contamination)",
                payloadOk);

        // ---- f. concurrency smoke (final-state only) ------------------------------
        DeterministicDispatcher dSmoke = DeterministicDispatcher.create(4);
        List<long[]> smokeCoords = taskSet(96);
        for (long[] c : smokeCoords) {
            long x = c[0];
            long z = c[1];
            // Deterministic work: derive bytes then fold a fixed-size loop into the tag.
            dSmoke.submit(x, z, () -> {
                byte[] p = payloadFor(x, z);
                long acc = 0;
                for (int i = 0; i < 1024; i++) {
                    acc += (x * 31 + z) ^ i;
                }
                p[7] = (byte) (p[7] ^ (acc & 0xFF));
                return p;
            });
        }
        dSmoke.awaitAll();
        int smokeN = smokeCoords.size();
        check("concurrency: banks=4, all 9216 submitted tasks executed (no lost tasks)",
                dSmoke.submittedCount() == smokeN && dSmoke.executedCount() == smokeN);
        dSmoke.shutdown();

        if (failures == 0) {
            System.out.println("[AsyncDispatchProbe] PASS (" + checks + " checks)");
            System.exit(0);
        } else {
            System.out.println("[AsyncDispatchProbe] FAIL: " + failures + " assertion(s) of " + checks);
            System.exit(1);
        }
    }

    /** exactly-once + sorted-merge contract at a given parallelism. */
    private static void runExactlyOnce(int parallelism) {
        DeterministicDispatcher d = DeterministicDispatcher.create(parallelism);
        List<long[]> coords = taskSet(64);
        submitAll(d, coords);
        List<DeterministicDispatcher.ChunkResult> merged = d.awaitAll();

        int n = coords.size();
        boolean countsOk = d.submittedCount() == n && d.executedCount() == n;
        boolean ordered = true;
        for (int i = 1; i < merged.size(); i++) {
            if (merged.get(i).compareTo(merged.get(i - 1)) <= 0) {
                ordered = false;
                break;
            }
        }
        Set<Long> seenKeys = new HashSet<>();
        boolean everyKey = true;
        for (DeterministicDispatcher.ChunkResult r : merged) {
            long key = r.x() * 1_000_000L + r.z();
            if (!seenKeys.add(key)) {
                everyKey = false;
                break;
            }
        }
        check("exactly-once P=" + parallelism + ": submitted==executed==N, merged list has N unique"
                + " keys sorted ascending", countsOk && merged.size() == n && ordered && everyKey);
    }

    /** Runs a task set and returns the merged results in deterministic (z,x) order. */
    private static List<DeterministicDispatcher.ChunkResult> runResults(int parallelism, List<long[]> coords) {
        DeterministicDispatcher d = DeterministicDispatcher.create(parallelism);
        submitAll(d, coords);
        List<DeterministicDispatcher.ChunkResult> merged = d.awaitAll();
        d.shutdown();
        return merged;
    }
}