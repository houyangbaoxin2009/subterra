// Deterministic acceptance probe for the p.2.7.3 generic deterministic dispatcher
// (io.toterra.subterra.engine.parallel) with explicit duplicate-key rejection.
// NOT shipped in the mod jar.
package io.toterra.subterra.probes;

import io.toterra.subterra.engine.parallel.DeterministicDispatcher;
import io.toterra.subterra.engine.parallel.KeyPartition;
import io.toterra.subterra.engine.parallel.TaskResult;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * Deterministic acceptance probe for the p.2.7.3 generic deterministic dispatcher
 * (io.toterra.subterra.engine.parallel). Asserts the exactly-once + sorted-merge
 * contract over a mixed key domain (String keys "k0".."k63" plus a record-key
 * 8x8 grid), parallel-vs-serial byte equivalence, payload isolation, the explicit
 * duplicate-key rejection contract (second submission throws
 * IllegalArgumentException; rejected work never invoked), post-shutdown submit
 * rejection, a concurrency smoke final-state, and bank-count normalization.
 * Every check is a final-state assertion after the join — never a wall-clock
 * assertion. Exit 0 = PASS, 1 = FAIL (never shipped in the mod jar).
 *
 * <p>p.2.7.3 通用确定性分发器（io.toterra.subterra.engine.parallel）的确定性验收探针。
 * 断言混合键域（String 键 "k0".."k63" + record 键 8x8 网格）上的"恰好一次 + 有序归并"
 * 契约、并行 vs 串行字节等价、负载隔离、重复键显式拒绝契约（第二次提交抛
 * IllegalArgumentException，被拒 work 绝不调用）、shutdown 后提交被拒、并发冒烟终态，
 * 以及 bank 数归一。所有检查都是 join 之后的终态断言，绝不依赖墙钟/时序。
 * 退出码 0 = PASS，1 = FAIL（永不随 mod jar 发布）。
 */
public final class ParallelDispatchProbe {

    /** Mixed key domain: a record key. Sorted (a, b) — deterministic natural order. */
    record CK(long a, long b) implements Comparable<CK> {
        @Override
        public int compareTo(CK other) {
            int c = Long.compare(a, other.a);
            return c != 0 ? c : Long.compare(b, other.b);
        }
    }

    private ParallelDispatchProbe() {
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

    /** 8 bytes, big-endian, derived purely from the string key — deterministic, no shared state. */
    static byte[] payloadFor(String key) {
        int h = key.hashCode();
        int n = key.length();
        return new byte[]{
                (byte) (n >> 8), (byte) n,
                (byte) (h >> 24), (byte) (h >> 16), (byte) (h >> 8), (byte) h,
                (byte) (key.charAt(0) & 0xFF), (byte) (key.charAt(n - 1) & 0xFF)};
    }

    /** 8 bytes, big-endian, derived purely from the record key — deterministic, no shared state. */
    static byte[] payloadFor(CK key) {
        long a = key.a();
        long b = key.b();
        return new byte[]{
                (byte) (a >> 24), (byte) (a >> 16), (byte) (a >> 8), (byte) a,
                (byte) (b >> 24), (byte) (b >> 16), (byte) (b >> 8), (byte) b};
    }

    /** String keys "k0".."k(n-1)". */
    static List<String> stringKeys(int n) {
        List<String> keys = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            keys.add("k" + i);
        }
        return keys;
    }

    /** Record-key grid [0, n) x [0, n). */
    static List<CK> ckGrid(int n) {
        List<CK> keys = new ArrayList<>(n * n);
        for (long a = 0; a < n; a++) {
            for (long b = 0; b < n; b++) {
                keys.add(new CK(a, b));
            }
        }
        return keys;
    }

    /** Runs a task set and returns the merged results in deterministic key order. */
    private static <K extends Comparable<K>> List<TaskResult<K, byte[]>> runResults(
            int parallelism, List<K> keys, Function<K, byte[]> payload) {
        DeterministicDispatcher<K, byte[]> d = DeterministicDispatcher.create(parallelism);
        for (K k : keys) {
            d.submit(k, () -> payload.apply(k));
        }
        List<TaskResult<K, byte[]>> merged = d.awaitAll();
        d.shutdown();
        return merged;
    }

    public static void main(String[] args) {
        // ---- 1+2. exactly-once + sorted merge (P=4 and P=1, mixed key domain) ----
        runExactlyOnce(4);
        runExactlyOnce(1);

        // ---- 3. parallel-vs-serial byte equivalence (mixed key domain) ----------
        List<String> stringKeys = stringKeys(64);
        List<CK> ckKeys = ckGrid(8);

        List<TaskResult<String, byte[]>> serialS = runResults(1, stringKeys, ParallelDispatchProbe::payloadFor);
        List<TaskResult<String, byte[]>> parallelS = runResults(4, stringKeys, ParallelDispatchProbe::payloadFor);
        List<TaskResult<CK, byte[]>> serialC = runResults(1, ckKeys, ParallelDispatchProbe::payloadFor);
        List<TaskResult<CK, byte[]>> parallelC = runResults(4, ckKeys, ParallelDispatchProbe::payloadFor);
        check("equivalence: banks=1 vs banks=4 merged lists byte-identical element-wise, same order (string + record keys)",
                equivalent(serialS, parallelS) && equivalent(serialC, parallelC));

        // ---- 4. payload correctness / isolation ----------------------------------
        boolean payloadOkS = payloadIsolated(parallelS, ParallelDispatchProbe::payloadFor);
        boolean payloadOkC = payloadIsolated(parallelC, ParallelDispatchProbe::payloadFor);
        check("payload: each result equals its own key's derived bytes (no cross-task contamination)",
                payloadOkS && payloadOkC);

        // ---- 5. duplicate key explicit rejection ----------------------------------
        DeterministicDispatcher<String, byte[]> d5 = DeterministicDispatcher.create(2);
        d5.submit("dup", () -> payloadFor("dup"));
        boolean[] rejectedWorkCalled = {false};
        boolean rejected = false;
        try {
            d5.submit("dup", () -> {
                rejectedWorkCalled[0] = true;
                return payloadFor("dup");
            });
        } catch (IllegalArgumentException e) {
            rejected = true;
        }
        List<TaskResult<String, byte[]>> m5 = d5.awaitAll();
        check("duplicate: second submit of same key throws IllegalArgumentException, rejected work never invoked,"
                        + " awaitAll OK, submitted==executed==accepted(1)",
                rejected && !rejectedWorkCalled[0] && m5.size() == 1
                        && d5.submittedCount() == 1 && d5.executedCount() == 1);
        d5.shutdown();

        // ---- 6. rejection-then-batch consistency -----------------------------------
        DeterministicDispatcher<String, byte[]> d6 = DeterministicDispatcher.create(4);
        d6.submit("k0", () -> payloadFor("k0"));
        boolean rejected6 = false;
        try {
            d6.submit("k0", () -> payloadFor("k0"));
        } catch (IllegalArgumentException e) {
            rejected6 = true;
        }
        for (int i = 1; i <= 9; i++) {
            String k = "k" + i;
            d6.submit(k, () -> payloadFor(k));
        }
        List<TaskResult<String, byte[]>> m6 = d6.awaitAll();
        int k0Count = 0;
        boolean ordered6 = true;
        for (int i = 0; i < m6.size(); i++) {
            if (m6.get(i).key().equals("k0")) {
                k0Count++;
            }
            if (i > 0 && m6.get(i).compareTo(m6.get(i - 1)) <= 0) {
                ordered6 = false;
            }
        }
        check("reject-then-batch: k0 duplicate rejected, k1..k9 accepted -> submitted==executed==10,"
                        + " 10 results, sorted, k0 exactly once",
                rejected6 && d6.submittedCount() == 10 && d6.executedCount() == 10
                        && m6.size() == 10 && ordered6 && k0Count == 1);
        d6.shutdown();

        // ---- 7. submit after shutdown -> IllegalStateException ----------------------
        DeterministicDispatcher<String, byte[]> d7 = DeterministicDispatcher.create(2);
        d7.submit("a", () -> payloadFor("a"));
        d7.awaitAll();
        d7.shutdown();
        boolean threwIse = false;
        try {
            d7.submit("b", () -> payloadFor("b"));
        } catch (IllegalStateException e) {
            threwIse = true;
        }
        check("shutdown: submit after shutdown throws IllegalStateException", threwIse);

        // ---- 8. concurrency smoke (final-state only) --------------------------------
        DeterministicDispatcher<CK, byte[]> d8 = DeterministicDispatcher.create(4);
        int n = 96;
        for (long a = 0; a < n; a++) {
            for (long b = 0; b < n; b++) {
                long x = a;
                long z = b;
                d8.submit(new CK(a, b), () -> {
                    byte[] p = payloadFor(new CK(x, z));
                    long acc = 0;
                    for (int i = 0; i < 1024; i++) {
                        acc += (x * 31 + z) ^ i;
                    }
                    p[7] = (byte) (p[7] ^ (acc & 0xFF));
                    return p;
                });
            }
        }
        d8.awaitAll();
        int smokeN = n * n;
        check("concurrency: banks=4, all 9216 submitted tasks executed (no lost tasks)",
                d8.submittedCount() == smokeN && d8.executedCount() == smokeN);
        d8.shutdown();

        // ---- 9. bank-count normalization --------------------------------------------
        boolean bankCounts = DeterministicDispatcher.create(4).bankCount() == 4
                && DeterministicDispatcher.create(7).bankCount() == 8
                && DeterministicDispatcher.create(1).bankCount() == 1
                && DeterministicDispatcher.create(0).bankCount() == 1
                && DeterministicDispatcher.create(9).bankCount() == KeyPartition.MAX_BANKS;
        check("bankCount: create(4)==4, create(7)==8, create(1)==1, create(0)==1, create(9)==8 (KeyPartition normalization)",
                bankCounts);

        if (failures == 0) {
            System.out.println("[ParallelDispatchProbe] PASS (" + checks + " checks)");
            System.exit(0);
        } else {
            System.out.println("[ParallelDispatchProbe] FAIL: " + failures + " assertion(s) of " + checks);
            System.exit(1);
        }
    }

    /** exactly-once + sorted-merge contract at a given parallelism, both key domains. */
    private static void runExactlyOnce(int parallelism) {
        DeterministicDispatcher<String, byte[]> ds = DeterministicDispatcher.create(parallelism);
        List<String> stringKeys = stringKeys(64);
        for (String k : stringKeys) {
            ds.submit(k, () -> payloadFor(k));
        }
        List<TaskResult<String, byte[]>> mergedS = ds.awaitAll();
        int nS = stringKeys.size();
        boolean countsOkS = ds.submittedCount() == nS && ds.executedCount() == nS;
        boolean orderedS = strictlyAscending(mergedS);
        boolean uniqueS = uniqueKeys(mergedS);
        check("exactly-once P=" + parallelism + " (string keys): submitted==executed==N, merged list has N unique"
                + " keys sorted ascending", countsOkS && mergedS.size() == nS && orderedS && uniqueS);
        ds.shutdown();

        DeterministicDispatcher<CK, byte[]> dc = DeterministicDispatcher.create(parallelism);
        List<CK> ckKeys = ckGrid(8);
        for (CK k : ckKeys) {
            dc.submit(k, () -> payloadFor(k));
        }
        List<TaskResult<CK, byte[]>> mergedC = dc.awaitAll();
        int nC = ckKeys.size();
        boolean countsOkC = dc.submittedCount() == nC && dc.executedCount() == nC;
        boolean orderedC = strictlyAscending(mergedC);
        boolean uniqueC = uniqueKeys(mergedC);
        check("exactly-once P=" + parallelism + " (record keys): submitted==executed==N, merged list has N unique"
                + " keys sorted ascending", countsOkC && mergedC.size() == nC && orderedC && uniqueC);
        dc.shutdown();
    }

    /** True if the merged list is strictly ascending by natural (key) order. */
    private static <K extends Comparable<K>, R> boolean strictlyAscending(List<TaskResult<K, R>> merged) {
        for (int i = 1; i < merged.size(); i++) {
            if (merged.get(i).compareTo(merged.get(i - 1)) <= 0) {
                return false;
            }
        }
        return true;
    }

    /** True if every merged key is distinct. */
    private static <K extends Comparable<K>, R> boolean uniqueKeys(List<TaskResult<K, R>> merged) {
        Set<K> seen = new HashSet<>();
        for (TaskResult<K, R> r : merged) {
            if (!seen.add(r.key())) {
                return false;
            }
        }
        return true;
    }

    /** True if the two merged lists have identical keys (same order) and byte-identical payloads. */
    private static <K extends Comparable<K>> boolean equivalent(
            List<TaskResult<K, byte[]>> a, List<TaskResult<K, byte[]>> b) {
        if (a.size() != b.size()) {
            return false;
        }
        for (int i = 0; i < a.size(); i++) {
            if (!a.get(i).key().equals(b.get(i).key())
                    || !Arrays.equals(a.get(i).result(), b.get(i).result())) {
                return false;
            }
        }
        return true;
    }

    /** True if every result's payload equals the bytes derived from its own key. */
    private static <K extends Comparable<K>> boolean payloadIsolated(
            List<TaskResult<K, byte[]>> merged, Function<K, byte[]> payload) {
        for (TaskResult<K, byte[]> r : merged) {
            if (!Arrays.equals(r.result(), payload.apply(r.key()))) {
                return false;
            }
        }
        return true;
    }
}
