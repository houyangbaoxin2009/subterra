package io.toterra.subterra.engine.ai;

import io.toterra.subterra.engine.pcg.PcgSource;

/**
 * Deterministic randomness for AI arbitration (p.2.24.2): a thin facade that
 * <em>delegates</em> to {@code engine.pcg.PcgSource} (p.2.13), which in turn
 * owns a {@link io.toterra.subterra.engine.worldgen.pipeline.noise.XoroRandom} —
 * a Xoroshiro128++ PRNG bit-identical to Minecraft 1.21.1. Semantics are
 * therefore aligned with p.2.13 by delegation rather than re-implementation:
 * the same seed and the same call sequence produce a bit-identical stream, so
 * {@link #nextInt(int)} and {@link #nextDouble()} are fully deterministic.
 * {@link #fork(long)} is a branching isolation point: the derived source is
 * independent of the parent (the parent's later draws equal what it would have
 * produced had no fork happened), and equal seeds fork into equal derived
 * sources. Pure JDK, no global state, no timestamp / timing.
 *
 * <p>AI 仲裁的确定性随机源（p.2.24.2）：<em>委托</em>给 {@code engine.pcg.PcgSource}
 * （p.2.13）的薄门面——后者持有
 * {@link io.toterra.subterra.engine.worldgen.pipeline.noise.XoroRandom}，即与
 * Minecraft 1.21.1 逐位一致的 Xoroshiro128++ PRNG。因此语义以委托而非重实现的方式与 p.2.13
 * 对齐：同种子、同调用序列必产生逐位一致的流，{@link #nextInt(int)} 与
 * {@link #nextDouble()} 完全确定。{@link #fork(long)} 是分支隔离点：派生源与父源互不影响
 * （父源后续取值等于从未 fork 时应有的取值），相同种子 fork 出相同的派生源。纯 JDK、无全局
 * 状态、无时间戳/时序。
 */
public final class AiRandom {

    /** The delegated deterministic source of randomness. */
    private final PcgSource source;

    /**
     * @param seed the master {@code long} seed from which every draw of this
     *             source — and transitively every fork derived from it — is
     *             deterministically produced.
     */
    public AiRandom(long seed) {
        this.source = new PcgSource(seed);
    }

    private AiRandom(PcgSource source) {
        this.source = source;
    }

    /**
     * Next integer in {@code [0, bound)} — unbiased, forwarded from the
     * underlying {@link PcgSource#nextInt(int)} / Xoroshiro128++ stream.
     * Deterministic: same seed, same call sequence, same value.
     *
     * 下一个 {@code [0, bound)} 内整数——无偏，转发自底层
     * {@link PcgSource#nextInt(int)} / Xoroshiro128++ 流。确定性：同种子、同调用序列、同值。
     *
     * @param bound exclusive upper bound, must be positive.
     * @return the next deterministic integer in {@code [0, bound)}.
     * @throws IllegalArgumentException if {@code bound <= 0}.
     */
    public int nextInt(int bound) {
        return source.nextInt(bound);
    }

    /**
     * Next double in {@code [0, 1)} — forwarded from {@link PcgSource#nextDouble()}.
     * Deterministic: same seed, same call sequence, same value.
     *
     * 下一个 {@code [0, 1)} 内 double——转发自 {@link PcgSource#nextDouble()}。确定性：同种子、
     * 同调用序列、同值。
     */
    public double nextDouble() {
        return source.nextDouble();
    }

    /**
     * Derives a new, mutually independent source from this source's current
     * state, delegating to {@link PcgSource#fork(String)}: the seed is encoded
     * as its unsigned decimal string (a deterministic salt), mixed with the
     * current state, and re-derived into a fresh source. The parent's state is
     * untouched — its later draws equal what they would have been had no fork
     * happened — and equal seeds fork into equal derived sources (branch
     * isolation).
     *
     * 从本源当前状态派生一个互不影响的新源，委托 {@link PcgSource#fork(String)}：种子以其无符号
     * 十进制字符串（确定性 salt）编码，与当前状态混合后重派生为新源。父源状态不受影响——其后续
     * 取值等于从未 fork 时应有的取值——相同种子 fork 出相同的派生源（分支隔离）。
     *
     * @param seed the deterministic branching seed (salt).
     * @return a fresh {@link AiRandom} whose stream is independent of this source.
     */
    public AiRandom fork(long seed) {
        return new AiRandom(this.source.fork(Long.toUnsignedString(seed)));
    }
}
