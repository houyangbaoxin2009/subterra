package io.toterra.subterra.engine.pcg;

/**
 * p.2.13.1 Deterministic PCG 确定性生成核心的设计决策总纲（final 工具类）——
 * 本类承载 p.2.13.1 的核心契约与固定常量，领域生成器（后续子项）与探针都以它为锚点。
 * <p>
 * 设计决策（Deterministic design decisions）：
 * <ol>
 *   <li><b>同 (seed, 调用序列) 必得同 (选择序列)</b>：所有随机取值都派生自单一 {@code long}
 *       种子、经由 {@link PcgSource} 唯一持有的一条 {@link io.toterra.subterra.engine.worldgen.pipeline.noise.XoroRandom}
 *       确定性流（与 Minecraft 1.21.1 逐位一致的 Xoroshiro128++）。同一种子、同一段调用序列，
 *       两次独立构建 produce 逐位一致的结果——这是本核心的确定性判据。</li>
 *   <li><b>无全局状态</b>：{@link PcgSource} 是实例级、不可变边界内的可变流；不存在共享静态种子、
 *       时钟、时间戳或任何无序随机源。每个值仅由 {seed, 已消耗调用前缀} 决定。</li>
 *   <li><b>固定序</b>：{@link PcgPick} 的 {@code pick}/{@code pickWeighted} 只依据池的固有顺序与源
 *       {@code nextInt}/{@code nextDouble} 作为输入，无 O(n²)（加权选取为单趟前缀和 + 二分）。</li>
 *   <li><b>细分源隔离（fork 隔离）</b>：领域子生成器必须经由 {@link PcgSource#fork(String)} 从父源派生
 *       自己的独立源——派生源互不影响、父源不受 fork 影响（其后续取值与从未 fork 完全一致），
 *       从而多个子生成器可安全串行/并行组合而不被彼此打乱调用序列。</li>
 *   <li><b>状态可探针</b>：{@link PcgSource#tdState()}/{@link PcgSource#fromTdState(String)} 提供
 *       确定性状态描述（对齐 {@link XoroRandom} 的 td 自描述风格），可作探针断言物。</li>
 * </ol>
 * <p>
 * p.2.13.1 design-decision charter of the deterministic content-generation core (final utility
 * class) — carries the core contract and fixed constants that later domain generators and probes
 * anchor to.
 * <p>
 * Deterministic design decisions:
 * <ol>
 *   <li><b>same (seed, call sequence) → same (choice sequence)</b>: every random draw derives from a
 *       single {@code long} seed through exactly one deterministic stream owned by a
 *       {@link PcgSource} over a {@link io.toterra.subterra.engine.worldgen.pipeline.noise.XoroRandom}
 *       (a Xoroshiro128++ PRNG bit-identical to Minecraft 1.21.1). Two independent constructions from
 *       the same seed and the same call prefix produce bit-identical results — the core determinism
 *       criterion.</li>
 *   <li><b>no global state</b>: {@link PcgSource} is instance-level, a mutable stream inside an
 *       immutable boundary; there is no shared static seed, clock, timestamp, or any unordered random
 *       source. Every value is a pure function of {seed, consumed call prefix}.</li>
 *   <li><b>fixed order</b>: {@link PcgPick}'s {@code pick}/{@code pickWeighted} take only the pool's
 *       inherent order and the source's {@code nextInt}/{@code nextDouble} as input, no O(n²)
 *       (weighted pick is a single-pass prefix sum + binary search).</li>
 *   <li><b>fork isolation</b>: domain sub-generators must derive their own source via
 *       {@link PcgSource#fork(String)} from the parent — derived sources are mutually independent and
 *       the parent is unaffected (its later draws equal what it would have had without the fork), so
 *       multiple sub-generators compose safely without disturbing each other's call sequence.</li>
 *   <li><b>probeable state</b>: {@link PcgSource#tdState()}/{@link PcgSource#fromTdState(String)}
 *       expose a deterministic state description (aligned with {@link XoroRandom}'s td
 *       self-describing style), usable as probe assertions.</li>
 * </ol>
 */
public final class Pcg {

    /** Core determinism contract: same seed → same output. 同种子 → 同输出。 */
    public static final String CONTRACT_SAME_SEED_SAME_OUTPUT =
            "same (seed, call sequence) -> same (choice sequence)";

    private Pcg() {
        // utility charter; no instantiation / 工具类，禁止实例化
    }
}
