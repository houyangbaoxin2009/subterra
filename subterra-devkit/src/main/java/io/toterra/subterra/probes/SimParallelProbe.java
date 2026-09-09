// Deterministic acceptance probe for the p.2.8.4 per-simulant isolation + deterministic
// merge facade (io.toterra.subterra.engine.sim.run.SimRunner) over the p.2.7 parallel runner.
// NOT shipped in the mod jar.
package io.toterra.subterra.probes;

import io.toterra.subterra.engine.parallel.TaskResult;
import io.toterra.subterra.engine.sim.run.SimRunner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Deterministic acceptance probe for the p.2.8.4 per-simulant isolation + deterministic
 * merge facade. Asserts the end-to-end SimRunner contract over a simulant key set:
 * parallel P=1 vs P=8 byte-identical; equality with a hand-written probe-local serial path;
 * key-order merge from shuffled input; explicit duplicate-key rejection (second submit throws,
 * rejected work never invoked); repeatability (two runs over the same input byte-identical);
 * input-order independence (two different input orders over the same key set give identical
 * output); and bank-count normalization (create(0)->1, create(9)->8). The per-key fn payload
 * carries a deterministically derived pseudo-random byte segment so parallel-vs-serial
 * comparison is meaningful. Every check is a final-state assertion — never a wall-clock/timing
 * assertion. Exit 0 = PASS, 1 = FAIL (never shipped in the mod jar).
 *
 * <p>p.2.8.4 per-simulant 隔离 + 确定性归并门面的确定性验收探针。在 simulant 键集上断言
 * SimRunner 端到端契约：并行 P=1 与 P=8 逐字节一致；与探针本地手写串行路径一致；乱序输入归并
 * 后仍为 key 序；重复键显式拒绝（第二次提交抛参，被拒 work 绝不调用）；可重复性（同输入两轮
 * run 逐字节一致）；输入序无关（不同输入序同键集输出一致）；以及 bank 数归一（create(0)->1,
 * create(9)->8）。per-key fn 负载含一段由键确定性派生的伪随机字节段，使并行/串行比对有意义。
 * 所有检查均为 join 之后/调用返回后的终态断言，绝不依赖墙钟/时序。退出码 0 = PASS，1 = FAIL
 * （永不随 mod jar 发布）。
 */
public final class SimParallelProbe {

    /** Simulant key, local to this probe (self-contained, no dependency on sibling sub-items). */
    record SimId(long id) implements Comparable<SimId> {
        @Override
        public int compareTo(SimId other) {
            return Long.compare(id, other.id);
        }
    }

    private SimParallelProbe() {
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

    /** 16 bytes, byte-for-byte derived from the key alone: an 8-byte seed then a deterministic
     *  pseudo-random 8-byte tail (PerSimRandom-style, no shared mutable state). */
    static byte[] payloadFor(SimId key) {
        long id = key.id();
        byte[] out = new byte[16];
        for (int i = 0; i < 8; i++) {
            out[i] = (byte) (id >> (8 * (7 - i)));
        }
        long s = id * 0x9E3779B97F4A7C15L + 0x1234567890ABCDEFL;
        for (int i = 8; i < 16; i++) {
            s ^= s >>> 12;
            s ^= s << 25;
            s ^= s >>> 27;
            out[i] = (byte) (s >>> (8 * (7 - (i - 8)) + (s & 7)));
        }
        return out;
    }

    /** Deterministic simulant key set: ids in ascending order {0..n-1}. */
    static List<SimId> simKeys(int n) {
        List<SimId> keys = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            keys.add(new SimId(i));
        }
        return keys;
    }

    /** A differently-ordered view of the same key set (a fixed deterministic shuffle). */
    static List<SimId> shuffled(List<SimId> keys) {
        List<SimId> out = new ArrayList<>(keys);
        // Fisher-Yates with a fixed affine stride — deterministic, not wall-clock.
        long seed = 0xDEADBEEFL;
        for (int i = out.size() - 1; i > 0; i--) {
            seed = seed * 0x5DEECE66DL + 0xB16; // LCG, purely arithmetic
            long next = seed >>> 32;
            int j = (int) (next % (i + 1));
            SimId tmp = out.get(i);
            out.set(i, out.get(j));
            out.set(j, tmp);
        }
        return out;
    }

    /** Hand-written probe-local serial golden path (independent of SimRunner). */
    static List<TaskResult<SimId, byte[]>> localSerial(List<SimId> keys) {
        List<SimId> ordered = new ArrayList<>(keys);
        ordered.sort(null);
        List<TaskResult<SimId, byte[]>> out = new ArrayList<>(ordered.size());
        for (SimId k : ordered) {
            out.add(new TaskResult<>(k, payloadFor(k)));
        }
        return out;
    }

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

    private static <K extends Comparable<K>, R> boolean sorted(List<TaskResult<K, R>> merged) {
        for (int i = 1; i < merged.size(); i++) {
            if (merged.get(i).compareTo(merged.get(i - 1)) <= 0) {
                return false;
            }
        }
        return true;
    }

    public static void main(String[] args) {
        List<SimId> keysA = simKeys(96);           // ascending input
        List<SimId> keysShuffled = shuffled(keysA); // same set, different input order
        List<TaskResult<SimId, byte[]>> serialGolden = localSerial(keysA);

        // ---- 1. P=1 vs P=8 byte-identical on the same key set (each vs the golden path) ----
        SimRunner r1 = SimRunner.create(1);
        SimRunner r8 = SimRunner.create(8);
        List<TaskResult<SimId, byte[]>> p1 = r1.run(keysA, SimParallelProbe::payloadFor);
        List<TaskResult<SimId, byte[]>> p8 = r8.run(keysA, SimParallelProbe::payloadFor);
        check("parallel: P=1 vs P=8 byte-identical (same key set, same key order)",
                equivalent(p1, p8));
        check("parallel: P=1 and P=8 each byte-identical to probe-local serial golden path",
                equivalent(p1, serialGolden) && equivalent(p8, serialGolden));

        // ---- 2. serial facade vs golden, sorted (key order), byte-identical ----
        List<TaskResult<SimId, byte[]>> s8 = r8.runSerial(keysShuffled, SimParallelProbe::payloadFor);
        check("runSerial: byte-identical to probe-local serial golden + sorted key order",
                equivalent(s8, serialGolden) && sorted(s8));

        // ---- 3. key-order merge from shuffled input (input order independence of order) ----
        List<TaskResult<SimId, byte[]>> p8Shuffled = r8.run(keysShuffled, SimParallelProbe::payloadFor);
        check("merge: shuffled input still yields sorted key-order output",
                sorted(p8Shuffled) && equivalent(p8Shuffled, p8));

        // ---- 4. input-order independence (identical result regardless of input order) ----
        check("input-order independence: ascending vs shuffled input -> identical result",
                equivalent(p8, p8Shuffled));

        // ---- 5. repeatability: two runs over the same input are byte-identical ----
        List<TaskResult<SimId, byte[]>> p8b = r8.run(keysA, SimParallelProbe::payloadFor);
        check("repeatability: two runs over the same input byte-identical", equivalent(p8, p8b));

        // ---- 6. explicit duplicate-key rejection (second submit throws, work never runs) ----
        List<SimId> dup = new ArrayList<>(Arrays.asList(new SimId(7), new SimId(7)));
        java.util.concurrent.atomic.AtomicInteger invoked = new java.util.concurrent.atomic.AtomicInteger();
        boolean threw = false;
        try {
            r8.run(dup, k -> {
                invoked.incrementAndGet();
                return payloadFor(k);
            });
        } catch (IllegalArgumentException e) {
            threw = true;
        }
        check("duplicate: duplicate key rejected (IllegalArgumentException), rejected work never invoked"
                        + " (only the accepted key ran, exactly once)",
                threw && invoked.get() == 1);

        // ---- 7. duplicate rejection also on the serial path ----
        boolean threwSerial = false;
        try {
            r8.runSerial(dup, SimParallelProbe::payloadFor);
        } catch (IllegalArgumentException e) {
            threwSerial = true;
        }
        check("duplicate (serial): runSerial rejects duplicate key with IllegalArgumentException",
                threwSerial);

        // ---- 8. bank-count normalization ----
        boolean banks = SimRunner.create(0).bankCount() == 1
                && SimRunner.create(1).bankCount() == 1
                && SimRunner.create(8).bankCount() == 8
                && SimRunner.create(9).bankCount() == 8
                && SimRunner.create(4).bankCount() == 4;
        check("bankCount: create(0)->1, create(1)->1, create(4)->4, create(8)->8, create(9)->8 (normalized)",
                banks);

        if (failures == 0) {
            System.out.println("[SimParallelProbe] PASS (" + checks + " checks)");
            System.exit(0);
        } else {
            System.out.println("[SimParallelProbe] FAIL: " + failures + " assertion(s) of " + checks);
            System.exit(1);
        }
    }
}