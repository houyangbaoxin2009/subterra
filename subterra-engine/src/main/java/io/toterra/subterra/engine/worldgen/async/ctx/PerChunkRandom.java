// Async per-chunk context isolation design derives from C2ME (MIT, Copyright (c) ishland, https://github.com/ishland/C2ME);
// implementation is original Subterra code.
package io.toterra.subterra.engine.worldgen.async.ctx;

import io.toterra.subterra.engine.worldgen.pipeline.noise.XoroRandom;

/**
 * Per-chunk deterministic random (p.2.6.2), derived design (does not copy C2ME).
 * Wraps a single {@link XoroRandom} that is seeded by a <em>pure function</em> of
 * {@code (worldSeed, chunkX, chunkZ)} only. Because {@link XoroRandom}'s single
 * {@code long} seed is expanded to a 128-bit state by a fixed deterministic
 * upgrade ({@code RandomSupport.upgradeSeedTo128bit}, splitmix64/Stafford13) and
 * its stream is a pure function of that state, the derived seed fully determines
 * the byte-exact {@link #nextLong()} / {@link #nextInt} sequence.
 * <p>
 * <b>Seed derivation formula</b> (documented contract — pinned by the probe as
 * regression goldens). Let {@code Kx = 0xBF58476D1CE4E5B9}, {@code Kz =
 * 0x94D049BB133111EB}, {@code R = 0x9E3779B97F4A7C15} (golden ratio), and let
 * {@code mix(x)} be the Stafford13 / splitmix64 avalanche finalizer
 * {@code x^=x>>>30; x*=0xBF58476D1CE4E5B9; x^=x>>>27; x*=0x94D049BB133111EB;
 * x^=x>>>31}. Then
 * {@code seed = mix( mix(worldSeed XOR (chunkX * Kx)) XOR ((chunkZ * Kz) XOR R) )}.
 * Distinct {@code (chunkX, chunkZ)} at one world seed avalanche to distinct seeds
 * (see {@link #seed()}), giving distinct per-chunk streams; identical inputs give
 * identical seeds and therefore byte-identical streams.
 *
 * <p><b>Stateless w.r.t. other instances:</b> each {@link PerChunkRandom} owns the
 * only reference to its internal {@link XoroRandom}; advancing one instance never
 * mutates any other. Constructing a fresh {@code of(...)} for the same coordinates
 * reproduces the stream from the first value (no shared state, no persisted cursor).
 *
 * <p><b>Method semantics</b> (what the probe asserts):
 * <ul>
 *   <li>{@link #nextLong()} — single Xoroshiro128++ step of the wrapped source.</li>
 *   <li>{@link #nextInt(int bound)} — unbiased integer in {@code [0, bound)} via the
 *       low-32-bit Lemire reduction (identical to {@link XoroRandom#nextInt(int)}).</li>
 * </ul>
 *
 * <p>p.2.6.2 每区块确定随机数（派生设计，未复制 C2ME）。包装单个 {@link XoroRandom}，
 * 其种子仅为 {@code (worldSeed, chunkX, chunkZ)} 的纯函数。因
 * {@code XoroRandom} 的单 long 种子经固定确定性升级（splitmix64/Stafford13）
 * 展开为 128 位状态，且流是该状态的纯函数，故派生种子完全决定
 * {@link #nextLong()} / {@code nextInt} 的逐字节序列。
 *
 * <p><b>种子派生公式</b>（文档化契约，探针以其为回归金样）：见上述公式。
 * 同一世界种子下不同的 {@code (chunkX, chunkZ)} 雪崩为不同种子，得到不同的每区块流；
 * 相同输入得到相同种子与逐字节相同的流。
 *
 * <p><b>相对其他实例无共享状态：</b> 每个 {@link PerChunkRandom} 独占其内部
 * {@link XoroRandom} 的唯一引用；推进其一不会改变任何其他实例。对相同坐标再次
 * {@code of(...)} 会从首值复现整个流（无共享状态、无持久游标）。
 *
 * <p><b>方法语义</b>（探针所断言者）：{@link #nextLong()} 为被包装源的单步
 * Xoroshiro128++；{@link #nextInt(int bound)} 为 {@code [0,bound)} 上无偏整数，
 * 采用低 32 位 Lemire 归约（与 {@link XoroRandom#nextInt(int)} 相同）。
 */
public final class PerChunkRandom {

    private static final long KX = 0xBF58476D1CE4E5B9L;
    private static final long KZ = 0x94D049BB133111EBL;
    private static final long ROT = 0x9E3779B97F4A7C15L;

    private final long worldSeed;
    private final long chunkX;
    private final long chunkZ;
    private final long seed;
    private final XoroRandom rng;

    private PerChunkRandom(long worldSeed, long chunkX, long chunkZ) {
        this.worldSeed = worldSeed;
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.seed = deriveSeed(worldSeed, chunkX, chunkZ);
        this.rng = new XoroRandom(this.seed);
    }

    /**
     * Builds the per-chunk random for the given coordinates at a fixed world seed.
     *
     * @param worldSeed the master world seed.
     * @param chunkX    the chunk X coordinate.
     * @param chunkZ    the chunk Z coordinate.
     * @return a freshly seeded, isolated instance.
     */
    public static PerChunkRandom of(long worldSeed, long chunkX, long chunkZ) {
        return new PerChunkRandom(worldSeed, chunkX, chunkZ);
    }

    /** Master world seed this instance was derived from. */
    public long worldSeed() {
        return worldSeed;
    }

    /** Chunk X coordinate. */
    public long chunkX() {
        return chunkX;
    }

    /** Chunk Z coordinate. */
    public long chunkZ() {
        return chunkZ;
    }

    /** The derived 64-bit seed {@code deriveSeed(worldSeed, chunkX, chunkZ)}. */
    public long seed() {
        return seed;
    }

    /** Next 64-bit value from the Xoroshiro128++ core. */
    public long nextLong() {
        return rng.nextLong();
    }

    /** Next 32-bit value ({@code (int) nextLong()}). */
    public int nextInt() {
        return rng.nextInt();
    }

    /**
     * Next integer in {@code [0, bound)} via the low-32 Lemire reduction, identical
     * to {@link XoroRandom#nextInt(int)} — fully deterministic for a given seed.
     *
     * @param bound exclusive upper bound, must be positive.
     * @throws IllegalArgumentException if {@code bound <= 0}.
     */
    public int nextInt(int bound) {
        return rng.nextInt(bound);
    }

    /**
     * Pure seed derivation described in the class javadoc. Deterministic; identical
     * {@code (worldSeed, chunkX, chunkZ)} always yield the identical {@code long}.
     */
    static long deriveSeed(long worldSeed, long chunkX, long chunkZ) {
        long h = mixStafford13(worldSeed ^ (chunkX * KX));
        return mixStafford13(h ^ ((chunkZ * KZ) ^ ROT));
    }

    /** Stafford13 / splitmix64 avalanche finalizer. */
    private static long mixStafford13(long x) {
        x = (x ^ (x >>> 30)) * 0xBF58476D1CE4E5B9L;
        x = (x ^ (x >>> 27)) * 0x94D049BB133111EBL;
        return x ^ (x >>> 31);
    }
}