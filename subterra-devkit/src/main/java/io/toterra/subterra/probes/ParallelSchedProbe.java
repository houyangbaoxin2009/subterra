// Deterministic acceptance probe for the p.2.7.5 engine.optim.sched combination:
// pooled-scratch work (SimpleObjectPool) driven through the p.2.7 engine.parallel
// runner must stay byte-identical vs the serial golden path. NOT shipped in the mod jar.
package io.toterra.subterra.probes;

import io.toterra.subterra.engine.optim.sched.structs.SimpleObjectPool;
import io.toterra.subterra.engine.parallel.ParallelRunner;
import io.toterra.subterra.engine.parallel.TaskResult;
import io.toterra.subterra.engine.worldgen.async.ChunkKey;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

/**
 * Deterministic acceptance probe for the p.2.7.5 combination of the p.2.7
 * engine.parallel data-parallel lane with the p.1.4.23 engine.optim.sched
 * primitives: the work function allocs a pooled scratch buffer from
 * {@link SimpleObjectPool}, writes key-derived bytes into it, copies the result
 * out, and releases the buffer back to the pool — then the same mixed key set is
 * run through {@link ParallelRunner} at P=4 and P=1 and through the serial golden
 * {@link ParallelRunner#runSerial} path. Asserts parallel-vs-serial byte
 * equivalence under pooling, P=1 vs P=4 equivalence, that the pool is genuinely
 * reused (a released buffer instance is handed out again), and payload
 * correctness (per-key isolation holds under the combination). Every check is a
 * final-state assertion after the join — never a wall-clock/timing assertion.
 * Exit 0 = PASS, 1 = FAIL (never shipped in the mod jar).
 *
 * <p><b>组合故事 / combination story:</b> engine.parallel（p.2.7）提供确定性数据并行
 * lane —— per-key 隔离 + key 序归并；engine.optim.sched（p.1.4.23）提供工作函数内部的
 * 调度/对象池原语（SimpleObjectPool 等）。两者组合后，并行 vs 串行仍逐位一致：因为
 * per-key 隔离（工作函数只经键派生内容）与 key 序归并（归并序 = 键自然序，与 bank 时序
 * 无关）独立于工作函数内部是否使用共享对象池原语而成立。工作函数可用
 * {@code engine.optim.util.ScratchPool} /
 * {@code engine.optim.sched.structs.OneTaskAtATimeExecutor} 而不破逐位确定性；
 * 本探针以 SimpleObjectPool 池化暂存缓冲为代表性用例（{@code alloc → 写入键派生字节 →
 * 复制结果 → release}）。本探针不做 FlowSched 调度逻辑本身——与 FlowSched 的共存由
 * probeAcceptance 全量接线统一验证。
 *
 * <p><b>Division of labor:</b> engine.parallel = 确定性数据并行底座（并行执行 + key 序
 * 归并）；engine.optim.sched = 调度/对象池原语（工作函数内部的暂存分配与复用）；
 * 分工组合不引入任何非确定性。
 */
public final class ParallelSchedProbe {

    /** Record key for the 8x8 grid. Sorted (a, b) — deterministic natural order. */
    record CK(long a, long b) implements Comparable<CK> {
        @Override
        public int compareTo(CK other) {
            int c = Long.compare(a, other.a);
            return c != 0 ? c : Long.compare(b, other.b);
        }
    }

    private ParallelSchedProbe() {
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

    /** Appends the 8 bytes as lowercase hex (16 chars) — ASCII, deterministic. */
    static void appendHex(StringBuilder sb, byte[] p) {
        for (byte b : p) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
    }

    // ---- pooled-scratch work (engine.optim.sched x engine.parallel combination) ----

    /** Shared pool: work functions alloc a scratch buffer, write, copy out, release. */
    private static final SimpleObjectPool<StringBuilder> POOL = new SimpleObjectPool<>(
            poolRef -> {
                StringBuilder sb = new StringBuilder();
                sb.append('x');
                return sb;
            },
            sb -> sb.setLength(0),
            sb -> sb.setLength(0),
            4);

    /** Identities that have been released back into {@link #POOL} (concurrent-safe). */
    private static final Set<Object> RELEASED = ConcurrentHashMap.newKeySet();

    /** Set true on the first alloc that returns an already-released instance. */
    private static final AtomicBoolean REUSE_OBSERVED = new AtomicBoolean();

    /** Records alloc identity and whether it is an already-released (reused) instance. */
    private static void observeAlloc(Object buf) {
        if (RELEASED.contains(buf)) {
            REUSE_OBSERVED.set(true);
        }
    }

    /** Records the released identity for later reuse observation. */
    private static void observeRelease(Object buf) {
        RELEASED.add(buf);
    }

    /** String-key work: pooled scratch buffer -> key-derived bytes copied out. */
    static byte[] pooledScratch(String key) {
        StringBuilder buf = POOL.alloc();
        observeAlloc(buf);
        buf.append(key).append(':');
        appendHex(buf, payloadFor(key));
        byte[] out = buf.toString().getBytes(StandardCharsets.US_ASCII);
        POOL.release(buf);
        observeRelease(buf);
        return out;
    }

    /** Record-key work: pooled scratch buffer -> key-derived bytes copied out. */
    static byte[] pooledScratch(CK key) {
        StringBuilder buf = POOL.alloc();
        observeAlloc(buf);
        buf.append(key.a()).append(',').append(key.b()).append(':');
        appendHex(buf, payloadFor(key));
        byte[] out = buf.toString().getBytes(StandardCharsets.US_ASCII);
        POOL.release(buf);
        observeRelease(buf);
        return out;
    }

    /** Chunk-key work: pooled scratch buffer -> key-derived bytes copied out. */
    static byte[] pooledScratch(ChunkKey key) {
        StringBuilder buf = POOL.alloc();
        observeAlloc(buf);
        buf.append(key.x()).append(',').append(key.z()).append(':');
        appendHex(buf, payloadFor(key));
        byte[] out = buf.toString().getBytes(StandardCharsets.US_ASCII);
        POOL.release(buf);
        observeRelease(buf);
        return out;
    }

    /** Expected scratch bytes for a string key — computed without the pool. */
    static byte[] expectedScratch(String key) {
        StringBuilder sb = new StringBuilder(24);
        sb.append(key).append(':');
        appendHex(sb, payloadFor(key));
        return sb.toString().getBytes(StandardCharsets.US_ASCII);
    }

    /** Expected scratch bytes for a record key — computed without the pool. */
    static byte[] expectedScratch(CK key) {
        StringBuilder sb = new StringBuilder(24);
        sb.append(key.a()).append(',').append(key.b()).append(':');
        appendHex(sb, payloadFor(key));
        return sb.toString().getBytes(StandardCharsets.US_ASCII);
    }

    /** Expected scratch bytes for a chunk key — computed without the pool. */
    static byte[] expectedScratch(ChunkKey key) {
        StringBuilder sb = new StringBuilder(24);
        sb.append(key.x()).append(',').append(key.z()).append(':');
        appendHex(sb, payloadFor(key));
        return sb.toString().getBytes(StandardCharsets.US_ASCII);
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

        // ---- 0. direct pool identity check (FlowSchedProbe-style, its own pool) ----
        SimpleObjectPool<StringBuilder> direct = new SimpleObjectPool<>(
                poolRef -> {
                    StringBuilder sb = new StringBuilder();
                    sb.append('x');
                    return sb;
                },
                sb -> sb.setLength(0),
                sb -> sb.setLength(0),
                4);
        StringBuilder a = direct.alloc();
        a.append("hello");
        direct.release(a);
        StringBuilder b = direct.alloc();
        check("pool: released instance identity-reused on next alloc (FlowSched-style direct check)", a == b);
        check("pool: initializer applied on alloc (length reset to 0)", b.length() == 0);

        // ---- 1. pooled work: parallel (P=4) vs serial golden byte equivalence ----
        ParallelRunner r1 = new ParallelRunner(4);
        List<TaskResult<String, byte[]>> parS = r1.run(stringKeys, ParallelSchedProbe::pooledScratch);
        List<TaskResult<String, byte[]>> serS = r1.runSerial(stringKeys, ParallelSchedProbe::pooledScratch);
        List<TaskResult<CK, byte[]>> parC = r1.run(ckKeys, ParallelSchedProbe::pooledScratch);
        List<TaskResult<CK, byte[]>> serC = r1.runSerial(ckKeys, ParallelSchedProbe::pooledScratch);
        List<TaskResult<ChunkKey, byte[]>> parK = r1.run(chunkKeys, ParallelSchedProbe::pooledScratch);
        List<TaskResult<ChunkKey, byte[]>> serK = r1.runSerial(chunkKeys, ParallelSchedProbe::pooledScratch);
        check("equivalence: pooled work run(P=4) vs runSerial byte-identical element-wise, same length/order (string + record + ChunkKey keys)",
                equivalent(parS, serS) && equivalent(parC, serC) && equivalent(parK, serK));

        // ---- 2. P=1 vs P=4 byte equivalence under pooling -------------------------
        ParallelRunner rP1 = new ParallelRunner(1);
        ParallelRunner rP4 = new ParallelRunner(4);
        check("equivalence: P=1 vs P=4 pooled work byte-identical (string + record + ChunkKey keys)",
                equivalent(rP1.run(stringKeys, ParallelSchedProbe::pooledScratch),
                        rP4.run(stringKeys, ParallelSchedProbe::pooledScratch))
                        && equivalent(rP1.run(ckKeys, ParallelSchedProbe::pooledScratch),
                        rP4.run(ckKeys, ParallelSchedProbe::pooledScratch))
                        && equivalent(rP1.run(chunkKeys, ParallelSchedProbe::pooledScratch),
                        rP4.run(chunkKeys, ParallelSchedProbe::pooledScratch)));

        // ---- 3. pool genuinely reused across the pooled runs ----------------------
        check("pool: release-then-alloc identity reuse observed across pooled runs (pooling genuinely active)",
                REUSE_OBSERVED.get());

        // ---- 4. payload correctness / per-key isolation under combination ---------
        boolean payloadOk = payloadIsolated(parS, ParallelSchedProbe::expectedScratch)
                && payloadIsolated(parC, ParallelSchedProbe::expectedScratch)
                && payloadIsolated(parK, ParallelSchedProbe::expectedScratch);
        check("payload: every pooled result == its key's expected scratch bytes (per-key isolation holds under combination)",
                payloadOk);

        if (failures == 0) {
            System.out.println("[ParallelSchedProbe] PASS (" + checks + " checks)");
            System.exit(0);
        } else {
            System.out.println("[ParallelSchedProbe] FAIL: " + failures + " assertion(s) of " + checks);
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
