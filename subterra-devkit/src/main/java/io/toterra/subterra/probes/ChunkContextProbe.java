// Deterministic acceptance probe for the p.2.6.2 per-chunk context isolation +
// deterministic random (io.toterra.subterra.engine.worldgen.async.ctx). Derived
// design from C2ME (MIT); implementation is original Subterra code. NOT shipped.
package io.toterra.subterra.probes;

import io.toterra.subterra.engine.worldgen.async.DeterministicDispatcher;
import io.toterra.subterra.engine.worldgen.async.ctx.ChunkTaskContext;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Deterministic acceptance probe for the p.2.6.2 per-chunk isolated context +
 * per-chunk deterministic random (io.toterra.subterra.engine.worldgen.async.ctx).
 * Asserts (a) derivation determinism, (b) cross-chunk isolation (distinct streams,
 * pinned golden coercion-values), (c) per-instance independence (interleaving does
 * not perturb either stream), and (d) the canonical concurrency-safety check:
 * parallel (P=4) per-chunk isolation via {@link DeterministicDispatcher} must equal
 * the serial (P=1) golden byte-for-byte. Data points are pinned as regression goldens
 * for the fixed seed {@code SEED}. Every check is a final-state assertion — no
 * wall-clock/timing assertions. Exit 0 = PASS, 1 = FAIL (never shipped in the mod jar).
 *
 * <p>p.2.6.2 每区块隔离上下文 + 每区块确定随机数
 * （io.toterra.subterra.engine.worldgen.async.ctx）的确定性验收探针。断言 (a) 派生
 * 确定性、(b) 跨区块隔离（流互异、固定金样数值）、(c) 实例独立性（交错推进不扰动
 * 任一流），以及 (d) 规范并发安全检查：经 {@link DeterministicDispatcher} 的并行
 * （P=4）每区块隔离必须与串行（P=1）金样逐字节一致。在固定种子 {@code SEED} 下以
 * 具体数值钉住回归金样。所有检查都是终态断言——绝无墙钟/时序断言。退出码 0 = PASS，
 * 1 = FAIL（永不随 mod jar 发布）。
 */
public final class ChunkContextProbe {

    private ChunkContextProbe() {
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

    /** Fixed world seed for the pinned golden vectors. */
    static final long SEED = 44905237L;

    // ---- Pinned per-coordinate goldens at SEED (derived-seed + first nextLong). ----
    static final long SEED_00 = 4115516595058092587L;
    static final long FIRST_00 = 7931731625990172516L;
    static final long SECOND_00 = 6679088647289096164L;
    static final long FIRST_10 = -9060981442548340422L;
    static final long FIRST_01 = 5135065403286200688L;
    static final long FIRST_LARGE = -4732883753458416632L; // (1234567890L, -987654321L)

    /** First {@code count} {@link PerChunkRandom} nextLong values for a coordinate. */
    static long[] streamFirst(long worldSeed, long x, long z, int count) {
        ChunkTaskContext ctx = ChunkTaskContext.of(worldSeed, x, z);
        long[] out = new long[count];
        for (int i = 0; i < count; i++) {
            out[i] = ctx.random().nextLong();
        }
        return out;
    }

    /** Big-endian 8-byte encode of {@code v} into {@code dst[off..off+8)}. */
    static void putLongBE(byte[] dst, int off, long v) {
        for (int i = 0; i < 8; i++) {
            dst[off + i] = (byte) (v >>> (56 - 8 * i));
        }
    }

    /**
     * Per-chunk deterministic payload: encode {@code count} consecutive nextLong
     * values drawn from a single isolated {@link ChunkTaskContext}. This is the
     * unit of parallel work fed to the p.2.6.1 dispatcher.
     */
    static byte[] perChunkPayload(long worldSeed, long x, long z, int count) {
        ChunkTaskContext ctx = ChunkTaskContext.of(worldSeed, x, z);
        byte[] b = new byte[count * 8];
        for (int i = 0; i < count; i++) {
            putLongBE(b, i * 8, ctx.random().nextLong());
        }
        return b;
    }

    /** The fixed 4x4 coordinate window (16 disjoint chunk keys). */
    static List<long[]> window4x4() {
        List<long[]> coords = new ArrayList<>(16);
        for (long x = 0; x < 4; x++) {
            for (long z = 0; z < 4; z++) {
                coords.add(new long[]{x, z});
            }
        }
        return coords;
    }

    /** Runs the window at a parallelism, returns merged payloads in (z,x) order. */
    static List<byte[]> runWindow(int parallelism, int streamLen) {
        DeterministicDispatcher d = DeterministicDispatcher.create(parallelism);
        for (long[] c : window4x4()) {
            long x = c[0];
            long z = c[1];
            d.submit(x, z, () -> perChunkPayload(SEED, x, z, streamLen));
        }
        List<DeterministicDispatcher.ChunkResult> merged = d.awaitAll();
        List<byte[]> payloads = new ArrayList<>(merged.size());
        for (DeterministicDispatcher.ChunkResult r : merged) {
            payloads.add(r.payload());
        }
        boolean counts = d.submittedCount() == 16 && d.executedCount() == 16;
        d.shutdown();
        check("window P=" + parallelism + ": submittedCount()==" + d.submittedCount()
                + " executedCount()==" + d.executedCount() + " (==16)", counts);
        return payloads;
    }

    public static void main(String[] args) {
        // ---- a. derivation determinism ---------------------------------------------
        long[] s1 = streamFirst(SEED, 0, 0, 32);
        long[] s2 = streamFirst(SEED, 0, 0, 32);
        boolean identical = Arrays.equals(s1, s2);
        check("a1: two contexts of(SEED,0,0) produce byte-identical 32-nextLong streams", identical);

        ChunkTaskContext c00a = ChunkTaskContext.of(SEED, 0, 0);
        ChunkTaskContext c00b = ChunkTaskContext.of(SEED, 0, 0);
        check("a2: derived seed for (0,0) == pinned golden 4115516595058092587",
                c00a.random().seed() == SEED_00 && c00b.random().seed() == SEED_00);
        check("a3: first nextLong for (0,0) == pinned golden 7931731625990172516",
                c00a.random().nextLong() == FIRST_00 && c00b.random().nextLong() == FIRST_00);
        check("a4: accessors chunkX()/chunkZ() reflect constructed coords",
                c00a.chunkX() == 0 && c00a.chunkZ() == 0
                        && ChunkTaskContext.of(SEED, 7, 9).chunkX() == 7
                        && ChunkTaskContext.of(SEED, 7, 9).chunkZ() == 9);

        // ---- b. cross-chunk isolation (distinct streams + pinned goldens) ----------
        long f00 = ChunkTaskContext.of(SEED, 0, 0).random().nextLong();
        long f10 = ChunkTaskContext.of(SEED, 1, 0).random().nextLong();
        long f01 = ChunkTaskContext.of(SEED, 0, 1).random().nextLong();
        long fLarge = ChunkTaskContext.of(SEED, 1234567890L, -987654321L).random().nextLong();

        check("b1: first value of (0,0) == pinned golden " + FIRST_00, f00 == FIRST_00);
        check("b2: first value of (1,0) == pinned golden " + FIRST_10, f10 == FIRST_10);
        check("b3: first value of (0,1) == pinned golden " + FIRST_01, f01 == FIRST_01);
        check("b4: first value of (1234567890,-987654321) == pinned golden " + FIRST_LARGE,
                fLarge == FIRST_LARGE);

        Set<Long> distinct = new HashSet<>(Arrays.asList(f00, f10, f01, fLarge));
        boolean allDistinctFirst = distinct.size() == 4;
        boolean firstsDifferFromSeed = f00 != f10 && f00 != f01 && f00 != fLarge
                && f10 != f01 && f10 != fLarge && f01 != fLarge;
        check("b5: (0,0)/(1,0)/(0,1)/large each have a distinct first value",
                allDistinctFirst && firstsDifferFromSeed);

        long sdA = ChunkTaskContext.of(SEED, 0, 0).random().seed();
        long sdB = ChunkTaskContext.of(SEED, 1, 0).random().seed();
        long sdC = ChunkTaskContext.of(SEED, 0, 1).random().seed();
        check("b6: distinct (x,z) at one seed yield distinct derived seeds",
                sdA != sdB && sdB != sdC && sdA != sdC);
        check("b7: early-stream values differ across chunks (2nd/3rd of (0,0) vs (1,0))",
                !Arrays.equals(streamFirst(SEED, 0, 0, 5), streamFirst(SEED, 1, 0, 5)));

        // ---- c. instance independence ----------------------------------------------
        ChunkTaskContext a = ChunkTaskContext.of(SEED, 0, 0);
        ChunkTaskContext b = ChunkTaskContext.of(SEED, 0, 0);
        long a1 = a.random().nextLong();
        long b1 = b.random().nextLong();
        check("c1: A and B (same coords) both start at pinned first value " + FIRST_00,
                a1 == FIRST_00 && b1 == FIRST_00);
        // Advance A by 5 more; B must be untouched.
        for (int i = 0; i < 5; i++) {
            a.random().nextLong();
        }
        long b2 = b.random().nextLong();
        check("c2: advancing A does not perturb B (B's 2nd value stays pinned "
                + SECOND_00 + ")", b2 == SECOND_00);
        // Interleave: two interleaved fresh contexts must remain identical to a single
        // canonical evolution (both read the golden 8-long sequence in lockstep, so
        // advancing one never perturbs the other).
        long[] canon = streamFirst(SEED, 0, 0, 8);
        ChunkTaskContext x = ChunkTaskContext.of(SEED, 0, 0);
        ChunkTaskContext y = ChunkTaskContext.of(SEED, 0, 0);
        long[] ip = new long[8];
        long[] iq = new long[8];
        for (int i = 0; i < 8; i++) {
            ip[i] = x.random().nextLong();
            iq[i] = y.random().nextLong();
        }
        check("c3: two interleaved (0,0) contexts produce identical 8-long streams",
                Arrays.equals(ip, iq) && Arrays.equals(ip, canon));

        // ---- d. parallel-vs-serial equivalence (integration with p.2.6.1) -----------
        int streamLen = 8;
        List<byte[]> serial = runWindow(1, streamLen);
        List<byte[]> parallel = runWindow(4, streamLen);
        boolean byteIdentical = serial.size() == parallel.size() && serial.size() == 16;
        if (byteIdentical) {
            for (int i = 0; i < serial.size(); i++) {
                if (!Arrays.equals(serial.get(i), parallel.get(i))) {
                    byteIdentical = false;
                    break;
                }
            }
        }
        check("d1: window(4x4) P=4 payload list byte-identical to P=1 golden (z,x order)",
                byteIdentical);

        // Determinism of the window payloads themselves: re-running P=4 reproduces the
        // exact same bytes (regression against the whole derived-random chain).
        List<byte[]> parallel2 = runWindow(4, streamLen);
        boolean rerunStable = parallel.size() == parallel2.size();
        if (rerunStable) {
            for (int i = 0; i < parallel.size(); i++) {
                if (!Arrays.equals(parallel.get(i), parallel2.get(i))) {
                    rerunStable = false;
                    break;
                }
            }
        }
        check("d2: re-running P=4 reproduces identical per-chunk bytes (no run-to-run drift)",
                rerunStable);

        if (failures == 0) {
            System.out.println("[ChunkContextProbe] PASS (" + checks + " checks)");
            System.exit(0);
        } else {
            System.out.println("[ChunkContextProbe] FAIL: " + failures + " assertion(s) of " + checks);
            System.exit(1);
        }
    }
}