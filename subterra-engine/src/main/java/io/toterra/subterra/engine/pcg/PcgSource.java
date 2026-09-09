package io.toterra.subterra.engine.pcg;

import io.toterra.subterra.engine.worldgen.pipeline.noise.XoroRandom;

/**
 * p.2.13.1 确定性随机源（final，实例级）——DF（Deterministic Fork 确定性 fork）与
 * 确定性抽取的单一入口。实例持有一个 {@link XoroRandom}（与 Minecraft 1.21.1 逐位一致的
 * Xoroshiro128++ 确定性 PRNG），把取值的「确定性」直接绑定到该底层流：同一个实例、同一段
 * 调用序列，必产生逐位一致的结果序列（同 seed → 同输出）。
 * <p>
 * 契约锚点（也是确定性判据）：
 * <ul>
 *   <li>同 {@code (seed, 调用序列)} 两次独立构建 → 两次 produce 逐字节/逐值一致；</li>
 *   <li>{@link #fork(String)} 从当前状态派生一个互不影响的新源——调用方与派生源各自独立，
 *       父源不受 fork 影响、后续取值与从未 fork 完全一致；两个 {@code salt} 不同的 fork
 *       产出的派生源不同；{@code salt} 相同的 fork 从同一父源状态派生出的派生源一致；
 *       fork 隔离专为「子生成器相互隔离」设计；</li>
 *   <li>{@link #tdState()} / {@link #fromTdState(String)} 给出确定性状态描述，对齐
 *       {@link XoroRandom} 的 td 自描述风格，可作为探针断言物。</li>
 * </ul>
 * 纯 JDK、无全局状态、无时间戳/随机/时序。
 * <p>
 * p.2.13.1 deterministic source of randomness (final, instance-level) — the single entry
 * point for deterministic draws and deterministic forks. The instance owns a
 * {@link XoroRandom} (a Xoroshiro128++ deterministic PRNG bit-identical to Minecraft 1.21.1)
 * and binds determinism directly to that underlying stream: the same instance consuming the
 * same call sequence yields a bit-identical result stream (same seed → same output).
 * <p>
 * Contract anchors (also the determinism criteria):
 * <ul>
 *   <li>two independent constructions from the same {@code (seed, call sequence)} → byte/value
 *       identical results;</li>
 *   <li>{@link #fork(String)} derives a new, mutually independent source from the current state —
 *       caller and derived source advance independently, the parent is unaffected and its later
 *       draws equal what it would have produced had it never forked; distinct {@code salt} values
 *       fork into distinct derived sources, equal {@code salt} forks from the same parent state
 *       yield equal derived sources; fork isolation exists to keep child generators apart;</li>
 *   <li>{@link #tdState()} / {@link #fromTdState(String)} give a deterministic state description,
 *       aligned with {@link XoroRandom}'s td self-describing style, usable as a probe assertion.</li>
 * </ul>
 * Pure JDK, no global state, no timestamp / random / timing.
 */
public final class PcgSource {

    /** The owned deterministic PRNG. */
    private final XoroRandom rnd;

    /**
     * @param seed the master {@code long} seed from which every value in this source —
     *             and transitively every fork derived from it — is deterministically produced.
     */
    public PcgSource(long seed) {
        this.rnd = new XoroRandom(seed);
    }

    /**
     * Next 64-bit value ({@code (int) nextLong()} of the underlying PRNG).
     *
     * @return the next deterministic {@code long}.
     */
    public long nextLong() {
        return rnd.nextLong();
    }

    /** Next 32-bit value (low word of {@link #nextLong()}). */
    public int nextInt() {
        return rnd.nextInt();
    }

    /**
     * Next integer in {@code [0, bound)} — unbiased, forwarded from the underlying
     * {@link XoroRandom#nextInt(int)}.
     *
     * @param bound exclusive upper bound, must be positive.
     * @return the next deterministic integer in {@code [0, bound)}.
     * @throws IllegalArgumentException if {@code bound <= 0}.
     */
    public int nextInt(int bound) {
        return rnd.nextInt(bound);
    }

    /**
     * Next double in {@code [0, 1)} — forwarded from {@link XoroRandom#nextDouble()}.
     *
     * @return the next deterministic double.
     */
    public double nextDouble() {
        return rnd.nextDouble();
    }

    /**
     * Derives a new, mutually independent source from this source's current state:
     * the salt is mixed with the current state words, then the pair is re-derived via the
     * same state-doubling the PRNG uses (upgradeSeedTo128bit equivalence + a 
     * Stafford13-mixed salt word). The parent's own state is untouched, so a later
     * {@code nextInt} on this source equals what it would have been had no fork happened.
     *
     * @param salt a deterministic separation string; distinct salts → distinct derived sources.
     * @return a fresh {@link PcgSource} whose stream is independent of this source.
     */
    public PcgSource fork(String salt) {
        long lo = rnd.seedLo();
        long hi = rnd.seedHi();
        long h = mixStafford13(lo ^ mixStafford13(saltHash(salt)));
        long derived = h ^ mixStafford13(hi);
        return new PcgSource(derived);
    }

    /**
     * Deterministic state description (td self-describing style, aligned with
     * {@link XoroRandom#td()}): {@code "[ seedLo = <long>; seedHi = <long> ]"} of the
     * underlying state. Intended as a probe assertion log.
     *
     * @return a deterministic, human-readable state description.
     */
    public String tdState() {
        return "[ seedLo = " + rnd.seedLo() + "; seedHi = " + rnd.seedHi() + " ]";
    }

    /**
     * Restores a fresh source whose underlying state equals the one described by
     * {@link #tdState()}.
     *
     * @param td the td-state snippet produced by {@link #tdState()}.
     * @return a fresh {@link PcgSource} with the described state.
     * @throws IllegalArgumentException if the snippet cannot be parsed.
     */
    public static PcgSource fromTdState(String td) {
        if (td == null) {
            throw new IllegalArgumentException("td must not be null");
        }
        int lb = td.indexOf('[');
        int rb = td.indexOf(']');
        if (lb < 0 || rb < 0 || rb <= lb) {
            throw new IllegalArgumentException("cannot parse td: " + td);
        }
        String body = td.substring(lb + 1, rb);
        long lo = 0L;
        long hi = 0L;
        boolean haveLo = false;
        boolean haveHi = false;
        for (String part : body.split(";")) {
            String p = part.trim();
            if (p.isEmpty()) {
                continue;
            }
            int eq = p.indexOf('=');
            if (eq < 0) {
                throw new IllegalArgumentException("cannot parse td: " + td);
            }
            String key = p.substring(0, eq).trim();
            String val = p.substring(eq + 1).trim();
            try {
                if ("seedLo".equals(key)) {
                    lo = Long.parseLong(val);
                    haveLo = true;
                } else if ("seedHi".equals(key)) {
                    hi = Long.parseLong(val);
                    haveHi = true;
                } else {
                    throw new IllegalArgumentException("unknown key: " + key);
                }
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("bad value in td: " + val, e);
            }
        }
        if (!haveLo || !haveHi) {
            throw new IllegalArgumentException("td missing seedLo/seedHi: " + td);
        }
        return ofState(lo, hi);
    }

    /**
     * Builds a source directly from raw state words (the two-word Xoroshiro128++ state used
     * as-is, no seed upgrade) — mirrors the two-word constructor of the underlying PRNG.
     *
     * @param seedLo the low 64-bit state word.
     * @param seedHi the high 64-bit state word.
     * @return a fresh {@link PcgSource} over that state.
     */
    public static PcgSource ofState(long seedLo, long seedHi) {
        XoroRandom r = new XoroRandom(seedLo, seedHi);
        return new PcgSource(r);
    }

    private PcgSource(XoroRandom r) {
        this.rnd = r;
    }

    /** Stafford13 / splitmix64 finalizer (multiply-xorshift avalanche). */
    private static long mixStafford13(long x) {
        x = (x ^ (x >>> 30)) * 0xBF58476D1CE4E5B9L;
        x = (x ^ (x >>> 27)) * 0x94D049BB133111EBL;
        return x ^ (x >>> 31);
    }

    /** Deterministic 64-bit hash of the salt string (no randomness, plays well with forks). */
    private static long saltHash(String salt) {
        if (salt == null) {
            salt = "";
        }
        long h = 0x9E3779B97F4A7C15L;
        for (int i = 0; i < salt.length(); i++) {
            h = mixStafford13(h ^ ((long) salt.charAt(i)) * (0x6A09E667F3BCC909L + i));
        }
        return h;
    }
}

