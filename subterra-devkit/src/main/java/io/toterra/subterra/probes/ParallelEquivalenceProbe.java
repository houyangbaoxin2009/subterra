// Deterministic acceptance probe for the p.2.7.4 parallel runner facade
// (io.toterra.subterra.engine.parallel.ParallelRunner) with serial golden path.
// NOT shipped in the mod jar.
package io.toterra.subterra.probes;

import io.toterra.subterra.engine.parallel.ParallelRunner;
import io.toterra.subterra.engine.parallel.TaskResult;
import io.toterra.subterra.engine.worldgen.async.ChunkKey;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.function.Function;

/**
 * Deterministic acceptance probe for the p.2.7.4 parallel runner facade
 * (io.toterra.subterra.engine.parallel.ParallelRunner): the parallel {@code run}
 * path vs the serial golden {@code runSerial} path must be element-for-element,
 * byte-for-byte identical over a mixed key domain (String keys "k0".."k95", a
 * record-key 8x8 grid, and a ChunkKey 8x8 grid). Asserts parallel-vs-serial byte
 * equivalence, P=1 vs P=4 equivalence, input-order independence (the merge order
 * is always key order), repeatability (fresh runners + 20 same-runner batches),
 * batch lifecycle reuse (50 varying-scale batches with no leak/crosstalk), the
 * duplicate-key contract symmetry on both paths, the p.2.6 ChunkKey merge-order
 * interop (Z then X, no p.2.6 changes), and the empty-input boundary. Every
 * check is a final-state assertion after the join — never a wall-clock/timing
 * assertion. Exit 0 = PASS, 1 = FAIL (never shipped in the mod jar).
 *
 * <p>p.2.7.4 并行运行器门面（io.toterra.subterra.engine.parallel.ParallelRunner）的
 * 确定性验收探针：并行 {@code run} 路径与串行金样 {@code runSerial} 路径必须在混合键域
 * （String 键 "k0".."k95"、record 键 8x8 网格、ChunkKey 8x8 网格）上逐元素、逐字节一致。
 * 断言并行 vs 串行字节等价、P=1 vs P=4 等价、输入序无关（归并序恒为 key 序）、可重复性
 * （全新 runner + 同 runner 20 批）、批次生命周期复用（50 批不同规模，无泄漏/串扰）、
 * 两路径重复键契约对称、与 p.2.6 的 ChunkKey 归并序互操作（先 Z 后 X，无需改造 p.2.6），
 * 以及空输入边界。所有检查都是 join 之后的终态断言，绝不依赖墙钟/时序。
 * 退出码 0 = PASS，1 = FAIL（永不随 mod jar 发布）。
 */
public final class ParallelEquivalenceProbe {

    /** Record key for the 8x8 grid. Sorted (a, b) — deterministic natural order. */
    record CK(long a, long b) implements Comparable<CK> {
        @Override
        public int compareTo(CK other) {
            int c = Long.compare(a, other.a);
            return c != 0 ? c : Long.compare(b, other.b);
        }
    }

    private ParallelEquivalenceProbe() {
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

    /** 8 bytes, big-endian, derived purely from the chunk (x, z) — deterministic, no shared state. */
    static byte[] payloadFor(ChunkKey key) {
        return payloadFor(key.x(), key.z());
    }

    /** 8 bytes, big-endian, derived purely from (x, z) — deterministic, no shared state. */
    static byte[] payloadFor(long x, long z) {
        return new byte[]{
                (byte) (x >> 24), (byte) (x >> 16), (byte) (x >> 8), (byte) x,
                (byte) (z >> 24), (byte) (z >> 16), (byte) (z >> 8), (byte) z};
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

    /** Chunk-key grid [0, n) x [0, n). */
    static List<ChunkKey> chunkGrid(int n) {
        List<ChunkKey> keys = new ArrayList<>(n * n);
        for (long x = 0; x < n; x++) {
            for (long z = 0; z < n; z++) {
                keys.add(new ChunkKey(x, z));
            }
        }
        return keys;
    }

    public static void main(String[] args) {
        List<String> stringKeys = stringKeys(96);
        List<CK> ckKeys = ckGrid(8);
        List<ChunkKey> chunkKeys = chunkGrid(8);

        // ---- 1. parallel vs serial golden byte equivalence (mixed key domain) ----
        ParallelRunner r1 = new ParallelRunner(4);
        List<TaskResult<String, byte[]>> parS = r1.run(stringKeys, ParallelEquivalenceProbe::payloadFor);
        List<TaskResult<String, byte[]>> serS = r1.runSerial(stringKeys, ParallelEquivalenceProbe::payloadFor);
        List<TaskResult<CK, byte[]>> parC = r1.run(ckKeys, ParallelEquivalenceProbe::payloadFor);
        List<TaskResult<CK, byte[]>> serC = r1.runSerial(ckKeys, ParallelEquivalenceProbe::payloadFor);
        List<TaskResult<ChunkKey, byte[]>> parK = r1.run(chunkKeys, ParallelEquivalenceProbe::payloadFor);
        List<TaskResult<ChunkKey, byte[]>> serK = r1.runSerial(chunkKeys, ParallelEquivalenceProbe::payloadFor);
        check("equivalence: run(P=4) vs runSerial byte-identical element-wise, same length/order (string + record + ChunkKey keys)",
                equivalent(parS, serS) && equivalent(parC, serC) && equivalent(parK, serK));

        // ---- 2. P=1 vs P=4 byte equivalence -------------------------------------
        ParallelRunner rP1 = new ParallelRunner(1);
        ParallelRunner rP4 = new ParallelRunner(4);
        check("equivalence: P=1 vs P=4 runners byte-identical (string + record + ChunkKey keys)",
                equivalent(rP1.run(stringKeys, ParallelEquivalenceProbe::payloadFor),
                        rP4.run(stringKeys, ParallelEquivalenceProbe::payloadFor))
                        && equivalent(rP1.run(ckKeys, ParallelEquivalenceProbe::payloadFor),
                        rP4.run(ckKeys, ParallelEquivalenceProbe::payloadFor))
                        && equivalent(rP1.run(chunkKeys, ParallelEquivalenceProbe::payloadFor),
                        rP4.run(chunkKeys, ParallelEquivalenceProbe::payloadFor)));

        // ---- 3. input order independence -----------------------------------------
        List<String> shuffledS = new ArrayList<>(stringKeys);
        Collections.shuffle(shuffledS, new Random(0x2A7));
        List<CK> shuffledC = new ArrayList<>(ckKeys);
        Collections.shuffle(shuffledC, new Random(0x2A7));
        List<ChunkKey> shuffledK = new ArrayList<>(chunkKeys);
        Collections.shuffle(shuffledK, new Random(0x2A7));
        ParallelRunner r3 = new ParallelRunner(4);
        check("input order: shuffled keys vs natural-order keys produce byte-identical merged output (merge order is always key order)",
                !shuffledS.equals(stringKeys) && !shuffledC.equals(ckKeys) && !shuffledK.equals(chunkKeys)
                        && equivalent(r3.run(stringKeys, ParallelEquivalenceProbe::payloadFor),
                        r3.run(shuffledS, ParallelEquivalenceProbe::payloadFor))
                        && equivalent(r3.run(ckKeys, ParallelEquivalenceProbe::payloadFor),
                        r3.run(shuffledC, ParallelEquivalenceProbe::payloadFor))
                        && equivalent(r3.run(chunkKeys, ParallelEquivalenceProbe::payloadFor),
                        r3.run(shuffledK, ParallelEquivalenceProbe::payloadFor)));

        // ---- 4. repeatability ------------------------------------------------------
        ParallelRunner rA = new ParallelRunner(4);
        ParallelRunner rB = new ParallelRunner(4);
        List<TaskResult<String, byte[]>> first = rA.run(stringKeys, ParallelEquivalenceProbe::payloadFor);
        boolean freshEq = equivalent(first, rB.run(stringKeys, ParallelEquivalenceProbe::payloadFor));
        boolean batchesEq = true;
        for (int i = 0; i < 20; i++) {
            List<TaskResult<String, byte[]>> batch = rA.run(stringKeys, ParallelEquivalenceProbe::payloadFor);
            if (batch.size() != stringKeys.size() || !equivalent(batch, first)) {
                batchesEq = false;
                break;
            }
        }
        check("repeatability: two fresh runners byte-identical; same runner 20 consecutive batches each == first batch (size == key count)",
                freshEq && batchesEq);

        // ---- 5. batch lifecycle reuse (50 varying-scale batches, no leak/crosstalk) ----
        ParallelRunner r5 = new ParallelRunner(4);
        boolean lifecycleOk = true;
        for (int i = 0; i < 50; i++) {
            int kind = i % 3;
            if (kind == 0) {
                List<String> ks = stringKeys(5 + i);
                List<TaskResult<String, byte[]>> m = r5.run(ks, ParallelEquivalenceProbe::payloadFor);
                if (m.size() != ks.size() || !payloadIsolated(m, ParallelEquivalenceProbe::payloadFor)) {
                    lifecycleOk = false;
                    break;
                }
            } else if (kind == 1) {
                List<CK> ks = ckGrid(1 + (i % 7));
                List<TaskResult<CK, byte[]>> m = r5.run(ks, ParallelEquivalenceProbe::payloadFor);
                if (m.size() != ks.size() || !payloadIsolated(m, ParallelEquivalenceProbe::payloadFor)) {
                    lifecycleOk = false;
                    break;
                }
            } else {
                List<ChunkKey> ks = chunkGrid(1 + (i % 7));
                List<TaskResult<ChunkKey, byte[]>> m = r5.run(ks, ParallelEquivalenceProbe::payloadFor);
                if (m.size() != ks.size() || !payloadIsolated(m, ParallelEquivalenceProbe::payloadFor)) {
                    lifecycleOk = false;
                    break;
                }
            }
        }
        check("lifecycle: same runner 50 batches of varying-scale mixed key sets — each size == key count, payloads correct (no leak/crosstalk)",
                lifecycleOk);

        // ---- 6. duplicate-key contract symmetry ------------------------------------
        ParallelRunner r6 = new ParallelRunner(4);
        boolean dupRun = false;
        try {
            r6.run(Arrays.asList("dup", "dup"), ParallelEquivalenceProbe::payloadFor);
        } catch (IllegalArgumentException e) {
            dupRun = true;
        }
        boolean dupSerial = false;
        boolean[] serialFnCalled = {false};
        try {
            r6.runSerial(Arrays.asList("dup", "dup"), s -> {
                serialFnCalled[0] = true;
                return payloadFor(s);
            });
        } catch (IllegalArgumentException e) {
            dupSerial = true;
        }
        check("duplicate: run and runSerial both throw IllegalArgumentException on duplicate keys (runSerial fn never invoked — upfront rejection)",
                dupRun && dupSerial && !serialFnCalled[0]);

        // ---- 7. ChunkKey interop: merge order matches p.2.6 dispatcher (Z then X) ----
        List<ChunkKey> keys7 = chunkGrid(8);
        List<TaskResult<ChunkKey, byte[]>> merged7 = new ParallelRunner(4).run(keys7, ParallelEquivalenceProbe::payloadFor);
        io.toterra.subterra.engine.worldgen.async.DeterministicDispatcher d26 =
                io.toterra.subterra.engine.worldgen.async.DeterministicDispatcher.create(4);
        for (ChunkKey k : keys7) {
            d26.submit(k.x(), k.z(), () -> payloadFor(k.x(), k.z()));
        }
        List<io.toterra.subterra.engine.worldgen.async.DeterministicDispatcher.ChunkResult> cr26 = d26.awaitAll();
        d26.shutdown();
        boolean seqOk = merged7.size() == cr26.size();
        boolean sorted7 = true;
        if (seqOk) {
            for (int i = 0; i < merged7.size(); i++) {
                ChunkKey pk = merged7.get(i).key();
                io.toterra.subterra.engine.worldgen.async.DeterministicDispatcher.ChunkResult c = cr26.get(i);
                if (pk.x() != c.x() || pk.z() != c.z()) {
                    seqOk = false;
                    break;
                }
            }
            for (int i = 1; i < merged7.size(); i++) {
                if (merged7.get(i).key().compareTo(merged7.get(i - 1).key()) <= 0) {
                    sorted7 = false;
                    break;
                }
            }
        }
        check("chunkkey interop: ParallelRunner.run merged key sequence == p.2.6 dispatcher ChunkResult sequence pairwise (Z then X), no p.2.6 changes",
                seqOk && sorted7);

        // ---- 8. empty input --------------------------------------------------------
        ParallelRunner r8 = new ParallelRunner(4);
        boolean emptyRun = r8.run(List.<String>of(), ParallelEquivalenceProbe::payloadFor).isEmpty();
        boolean emptySerial = r8.runSerial(List.<String>of(), ParallelEquivalenceProbe::payloadFor).isEmpty();
        check("empty: run(empty) and runSerial(empty) both return empty lists", emptyRun && emptySerial);

        if (failures == 0) {
            System.out.println("[ParallelEquivalenceProbe] PASS (" + checks + " checks)");
            System.exit(0);
        } else {
            System.out.println("[ParallelEquivalenceProbe] FAIL: " + failures + " assertion(s) of " + checks);
            System.exit(1);
        }
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
