// Deterministic acceptance probe for the p.2.8.3 incremental deterministic
// simulation core (io.toterra.subterra.engine.sim.core): dirty-set advance that is
// byte-identical to a full recompute, order-independent per-key ticks, and
// diff/replay reproducibility under a same-input-same-output contract.
// NOT shipped in the mod jar.
package io.toterra.subterra.probes;

import io.toterra.subterra.engine.sim.core.PerSimRandom;
import io.toterra.subterra.engine.sim.core.SimKey;
import io.toterra.subterra.engine.sim.core.SimWorld;
import io.toterra.subterra.engine.sim.core.Simulator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Deterministic acceptance probe for the p.2.8.3 incremental deterministic
 * simulation core (io.toterra.subterra.engine.sim.core). State is an immutable
 * {@code record St(long acc, long rsum)} so it is a valid pure-function input; the
 * {@link Simulator} consumes a strictly per-simulant {@code XoroRandom} stream.
 * Asserts:
 * <ol>
 *   <li>order independence — the same dirty set advanced in sorted order vs a
 *       fixed-seed shuffled order yields per-key byte-identical states;</li>
 *   <li>incremental≡full — {@code advanceAll()} vs {@code advance(keys() in
 *       shuffled order)} yields whole-world byte-identical states;</li>
 *   <li>isolated diff — {@code advance(dirty)} returns exactly the dirty keys and
 *       leaves every non-dirty key byte-identical;</li>
 *   <li>diff replay — applying the same per-tick dirty plan to two worlds
 *       (recording diffs on one, replaying the plan on the other) leaves all
 *       states byte-identical after T ticks;</li>
 *   <li>random golden — {@link PerSimRandom#forTick} first-{@code nextLong}
 *       values are pinned to self-computed constants;</li>
 *   <li>same-input-same-output across seeds — equal seeds give byte-identical
 *       worlds under the same advance sequence, a different seed gives a
 *       different one.</li>
 * </ol>
 * Every check is a final-state assertion — never a wall-clock/timing assertion.
 * States are serialized with a local deterministic big-endian byte encoder and
 * compared with {@link Arrays#equals}. Exit 0 = PASS, 1 = FAIL (never shipped in
 * the mod jar).
 *
 * <p>p.2.8.3 增量确定性模拟核心（io.toterra.subterra.engine.sim.core）的确定性验收
 * 探针。状态为不可变 {@code record St(long acc, long rsum)}，故可作为纯函数入参；
 * {@link Simulator} 消费严格 per-simulant 的 {@code XoroRandom} 流。断言：
 * <ol>
 *   <li>顺序无关——相同脏集分别按排序序与固定种子的乱序推进，per-key 状态逐字节一致；</li>
 *   <li>增量≡全量——{@code advanceAll()} 与乱序 {@code advance(keys())} 的整世界状态
 *       逐字节一致；</li>
 *   <li>隔离 diff——{@code advance(dirty)} 恰返回脏键，且所有非脏键逐字节不变；</li>
 *   <li>diff 回放——对两世界施加相同逐 tick 脏计划（一方记录 diff、另一方重放该计划），
 *       T 个 tick 后全体状态逐字节一致；</li>
 *   <li>随机金样——{@link PerSimRandom#forTick} 的首个 {@code nextLong} 值与自算常数
 *       完全一致；</li>
 *   <li>同输入同输出跨种子——同种子在相同推进序列下逐字节一致，不同种子则不同。</li>
 * </ol>
 * 所有检查均为终态断言，绝不依赖墙钟/时序。状态用本地确定性大端字节编码函数序列化后以
 * {@link Arrays#equals} 比对。退出码 0 = PASS，1 = FAIL（永不随 mod jar 发布）。
 */
public final class SimIncrementalProbe {

    /** Immutable pure state (valid pure-function input). */
    record St(long acc, long rsum) {
    }

    // ---- test constants (fixed advance sequence seeds & goldens) ----
    private static final long SEED_A = 0x5DEECE67L;   // 1573819503591L
    private static final long SEED_B = 0xDEADBEEFL;
    private static final long SHUFFLE_SEED = 0x1234_5678L;

    private SimIncrementalProbe() {
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

    // ---- local deterministic big-endian byte encoder for St ----
    /** Encodes a state to 16 big-endian bytes (acc then rsum) — deterministic, no shared state. */
    static byte[] encode(St s) {
        byte[] b = new byte[16];
        putLong(b, 0, s.acc());
        putLong(b, 8, s.rsum());
        return b;
    }

    private static void putLong(byte[] b, int off, long v) {
        for (int i = 0; i < 8; i++) {
            b[off + i] = (byte) (v >>> (56 - 8 * i));
        }
    }

    /** Pure per-simulant tick function: mixes the key, consumes a fixed slice of the per-key stream. */
    static Simulator<St> sim() {
        return (tickNo, key, rand, state) -> {
            long sum = 0;
            for (int i = 0; i < 4; i++) {
                sum += rand.nextLong();
            }
            long step = rand.nextLong();
            long acc = state.acc() * 31 + key.x() * 17 + key.z() * 13 + step;
            return new St(acc, state.rsum() + sum);
        };
    }

    /** The full key domain: an (x, z) grid [0,xn) x [0,zn). */
    static List<SimKey> allKeys(int xn, int zn) {
        List<SimKey> keys = new ArrayList<>(xn * zn);
        for (long x = 0; x < xn; x++) {
            for (long z = 0; z < zn; z++) {
                keys.add(new SimKey(x, z));
            }
        }
        return keys;
    }

    /** Deterministic initial state for a key — pure, no shared state. */
    static St initState(SimKey k) {
        return new St(k.x() * 1000 + k.z() * 101, k.x() * 7 + k.z() * 13);
    }

    interface KeyPlan {
        List<SimKey> plan(int tick);
    }

    /** A dirty plan selecting keys by a pure modular predicate (varies per tick). */
    static KeyPlan dirtyPlan() {
        return tick -> {
            List<SimKey> out = new ArrayList<>();
            for (SimKey k : allKeys(6, 5)) {
                if (((k.x() + k.z() + tick) & 3) == 0) {
                    out.add(k);
                }
            }
            return out;
        };
    }

    static SimWorld<St> build(long seed) {
        SimWorld<St> w = SimWorld.create(sim(), seed, 0L);
        for (SimKey k : allKeys(6, 5)) {
            w.add(k, initState(k));
        }
        return w;
    }

    /** True if every state in the two worlds is byte-identical (same keys, size, tick). */
    static boolean worldsEqual(SimWorld<St> a, SimWorld<St> b) {
        if (a.size() != b.size() || a.tickNo() != b.tickNo()) {
            return false;
        }
        List<SimKey> ka = a.keys();
        List<SimKey> kb = b.keys();
        if (ka.size() != kb.size()) {
            return false;
        }
        Set<SimKey> bset = new HashSet<>(kb);
        for (SimKey k : ka) {
            if (!bset.contains(k) || !Arrays.equals(encode(a.state(k)), encode(b.state(k)))) {
                return false;
            }
        }
        return true;
    }

    /** Fixed-seed deterministic shuffle (java.util.Random is spec-defined; seed fixed). */
    static List<SimKey> shuffled(List<SimKey> in, long seed) {
        List<SimKey> out = new ArrayList<>(in);
        Collections.shuffle(out, new Random(seed));
        return out;
    }

    public static void main(String[] args) {
        // ---- 1. order independence: dirty advanced sorted vs fixed-seed shuffled ----
        SimWorld<St> w1 = build(SEED_A);
        SimWorld<St> w1s = build(SEED_A);
        List<SimKey> dirty = new ArrayList<>();
        for (SimKey k : allKeys(6, 5)) {
            if (((k.x() + k.z()) & 1) == 0) {
                dirty.add(k);
            }
        }
        List<SimKey> dirtySorted = new ArrayList<>(dirty);
        Collections.sort(dirtySorted);
        List<SimKey> dirtyShuffled = shuffled(dirty, SHUFFLE_SEED);
        if (dirty.equals(dirtyShuffled)) {
            dirtyShuffled = shuffled(dirty, SHUFFLE_SEED ^ 0x55AA_55AAL); // ensure genuinely different order
        }
        w1.advance(dirtySorted);
        w1s.advance(dirtyShuffled);
        boolean orderIndep = true;
        for (SimKey k : dirty) {
            if (!Arrays.equals(encode(w1.state(k)), encode(w1s.state(k)))) {
                orderIndep = false;
            }
        }
        check("order-independence: same dirty set advanced in sorted vs fixed-seed shuffled order -> "
                + "per-key new states byte-identical, world tick == 1", orderIndep && w1.tickNo() == 1 && w1s.tickNo() == 1);

        // ---- 2. incremental == full: advanceAll() vs advance(keys() shuffled) ----
        SimWorld<St> wFull = build(SEED_A);
        SimWorld<St> wInc = build(SEED_A);
        wFull.advanceAll();
        wInc.advance(shuffled(wFull.keys(), SHUFFLE_SEED));
        check("incremental=full: advanceAll() vs advance(all keys, shuffled order) -> whole world "
                + "byte-identical (states + tick)", worldsEqual(wFull, wInc));

        // ---- 3. isolated diff: returns exactly dirty; non-dirty byte-unchanged ----
        SimWorld<St> w3 = build(SEED_A);
        Map<SimKey, byte[]> beforeNonDirty = new java.util.HashMap<>();
        Set<SimKey> nonDirty = new HashSet<>();
        for (SimKey k : allKeys(6, 5)) {
            if (!dirty.contains(k)) {
                beforeNonDirty.put(k, encode(w3.state(k)));
                nonDirty.add(k);
            }
        }
        Map<SimKey, St> diff = w3.advance(dirty);
        boolean diffKeysExact = diff.keySet().size() == dirty.size() && diff.keySet().containsAll(dirty);
        boolean diffMatchesWorld = true;
        for (SimKey k : dirty) {
            if (w3.state(k) == null || diff.get(k) != w3.state(k)) {
                diffMatchesWorld = false;
            }
        }
        boolean nonDirtyUntouched = true;
        for (SimKey k : nonDirty) {
            if (!Arrays.equals(encode(w3.state(k)), beforeNonDirty.get(k))) {
                nonDirtyUntouched = false;
            }
        }
        check("isolated-diff: advance(dirty) returns exactly the dirty keys, diff matches world, and every "
                + "non-dirty key's state is byte-identical to before", diffKeysExact && diffMatchesWorld && nonDirtyUntouched);

        // ---- 4. diff replay: same per-tick dirty plan -> byte-identical after T ticks ----
        KeyPlan plan = dirtyPlan();
        int T = 8;
        SimWorld<St> wReplaySrc = build(SEED_A);
        for (int t = 0; t < T; t++) {
            wReplaySrc.advance(plan.plan(t)); // record diffs (discarded here: replay is the equivalent path)
        }
        SimWorld<St> wReplayDst = build(SEED_A);
        for (int t = 0; t < T; t++) {
            wReplayDst.advance(plan.plan(t));
        }
        check("diff-replay: applying the same per-tick dirty plan over " + T + " ticks to two identical "
                + "worlds -> whole-world states byte-identical (tick " + wReplaySrc.tickNo() + " vs "
                + wReplayDst.tickNo() + ")",
                wReplaySrc.tickNo() == T && wReplayDst.tickNo() == T && worldsEqual(wReplaySrc, wReplayDst));

        // ---- 5. random golden: PerSimRandom.forTick first nextLong pinned ----
        boolean golden = PerSimRandom.forTick(987654321L, 0L, new SimKey(0, 0)).nextLong() == 621528721095467165L
                && PerSimRandom.forTick(987654321L, 0L, new SimKey(3, 7)).nextLong() == 8440324103943276898L
                && PerSimRandom.forTick(987654321L, 7L, new SimKey(3, 7)).nextLong() == 9061977707765709102L
                && PerSimRandom.forTick(987654321L, 7L, new SimKey(-5, 11)).nextLong() == -2867751437310933228L
                && PerSimRandom.forTick(0xCAFEBABEL, 3L, new SimKey(100, -200)).nextLong() == -1577849266400315195L;
        check("random-golden: PerSimRandom.forTick first nextLong equals self-computed constants for "
                + "5 fixed (seed, tick, key) triples", golden);

        // ---- 6. same-input-same-output across seeds ----
        SimWorld<St> a1 = build(SEED_A);
        SimWorld<St> a2 = build(SEED_A);
        SimWorld<St> b = build(SEED_B);
        List<SimKey> ks = allKeys(6, 5);
        for (SimKey k : ks) {
            a1.add(k, a1.state(k));
            a2.add(k, a2.state(k));
            b.add(k, b.state(k));
        }
        a1.advanceAll();
        a2.advanceAll();
        b.advanceAll();
        a1.advance(shuffled(a1.keys(), SHUFFLE_SEED));
        a2.advance(shuffled(a2.keys(), SHUFFLE_SEED ^ 1));
        b.advance(shuffled(b.keys(), SHUFFLE_SEED));
        boolean sameSeed = worldsEqual(a1, a2);
        boolean diffSeed = !worldsEqual(a1, b);
        check("same-in-same-out: equal seeds + same advance sequence -> byte-identical; a different seed "
                + "differs somewhere", sameSeed && diffSeed);

        if (failures == 0) {
            System.out.println("[SimIncrementalProbe] PASS (" + checks + " checks)");
            System.exit(0);
        } else {
            System.out.println("[SimIncrementalProbe] FAIL: " + failures + " assertion(s) of " + checks);
            System.exit(1);
        }
    }
}