package io.toterra.subterra.engine.sim.core;

import io.toterra.subterra.engine.worldgen.pipeline.noise.XoroRandom;

/**
 * Stateless, self-developed per-simulant-per-tick random factory (p.2.8.3).
 * For a fixed {@code (worldSeed, tickNo, key)} triple it derives a 64-bit seed
 * by a <em>pure function</em> and hands back a freshly seeded {@link XoroRandom}
 * whose byte-exact stream is fully determined by that seed (Xoroshiro128++
 * stream is a pure function of the 128-bit state). No instances are held and no
 * state is shared — {@code forTick} is a factory, so constructing it again for
 * the same triple reproduces the stream from its first value.
 *
 * <p><b>Documented seed-derivation formula</b> (self-developed clean-room, own
 * constants; pinned by the {@code SimIncrementalProbe} as regression goldens).
 * Let {@code KX=0xD1342543DE82EF95L}, {@code KZ=0x2545F4914F6CDD1DL},
 * {@code KT=0xA5A5CAFF53A04C0BL}, and {@code mix(x)} be the self-developed
 * multiply-xorshift avalanche below. Then
 * {@code seed = mix( mix(a) ^ Long.rotateRight(a, 29) )} where
 * {@code a = worldSeed ^ Long.rotateLeft(tickNo*KT, 11) ^ (x*KX) ^ Long.rotateLeft(z*KZ, 17)}.
 * Distinct triples avalanche to distinct seeds; identical triples give identical
 * seeds and therefore byte-identical {@link XoroRandom} streams.
 *
 * <p><b>Why per-key independence holds:</b> each simulant's stream depends only on
 * its own {@code (tick, key)} and the master world seed — never on which other
 * keys advance in the same batch, nor on iteration order. This is exactly what
 * {@link SimWorld#advance}/{@link SimWorld#advanceAll} rely on for their
 * order-independent, diff/byte-identical contract.
 *
 * <p><b>无共享状态的每 simulant 每 tick 随机工厂</b>（p.2.8.3）。对固定三元组
 * {@code (worldSeed, tickNo, key)}，它以<em>纯函数</em>派生出 64 位种子，并交回一个
 * 以该种子全新初始化的 {@link XoroRandom}，其后流完全由该种子决定（Xoroshiro128++
 * 流是 128 位状态的纯函数）。本类不持有任何实例、不共享任何状态——{@code forTick}
 * 是工厂方法，对同一三元组再次调用会从首值复现整个流。
 *
 * <p><b>种子派生公式</b>（自研 clean-room、自拟常量；由 {@code SimIncrementalProbe}
 * 固化为回归金样）：见英文说明。不同三元组雪崩为不同种子；相同三元组得到相同种子与
 * 逐字节相同的 {@link XoroRandom} 流。
 *
 * <p><b>每键独立的成因：</b> 每个 simulant 的流仅取决于其自身的 {@code (tick,key)}
 * 与主世界种子——绝不依赖同一批中还有哪些键被推进，也与迭代序无关。这正是
 * {@link SimWorld#advance}/{@link SimWorld#advanceAll} 实现顺序无关、diff/逐字节一致
 * 契约所依赖的性质。
 */
public final class PerSimRandom {

    private static final long KX = 0xD1342543DE82EF95L;
    private static final long KZ = 0x2545F4914F6CDD1DL;
    private static final long KT = 0xA5A5CAFF53A04C0BL;

    private PerSimRandom() {
    }

    /**
     * Returns a freshly seeded, isolated {@link XoroRandom} for the given simulant
     * at the given tick. The seed is {@code deriveSeed(worldSeed, tickNo,
     * key.x(), key.z())}; identical inputs always yield a byte-identical stream.
     *
     * @param worldSeed the master world seed.
     * @param tickNo    the tick the simulant advances to.
     * @param key       the simulant identity ({@code x}, {@code z}).
     * @return a new, unshared {@link XoroRandom}: 为给定 simulant 在给定 tick 交回一个
     *         全新、相互隔离的 {@link XoroRandom}。种子即
     *         {@code deriveSeed(worldSeed, tickNo, key.x(), key.z())}；相同输入永远得到
     *         逐字节相同的流。
     */
    public static XoroRandom forTick(long worldSeed, long tickNo, SimKey key) {
        return new XoroRandom(deriveSeed(worldSeed, tickNo, key.x(), key.z()));
    }

    /**
     * Pure seed derivation described in the class javadoc. Deterministic; identical
     * {@code (worldSeed, tickNo, x, z)} always yield the identical {@code long}.
     *
     * 类 javadoc 详述的纯种子派生。确定性：相同 {@code (worldSeed, tickNo, x, z)}
     * 恒得相同 long。
     */
    static long deriveSeed(long worldSeed, long tickNo, long x, long z) {
        long a = worldSeed
                ^ Long.rotateLeft(tickNo * KT, 11)
                ^ (x * KX)
                ^ Long.rotateLeft(z * KZ, 17);
        long b = mix(a);
        return mix(b ^ Long.rotateRight(a, 29));
    }

    /**
     * Self-developed multiply-xorshift avalanche finalizer (own constants, distinct
     * from the build-in noise mixers): smears each bit across the whole word so
     * nearby keys/tick counts do not produce trivially related streams.
     *
     * 自研的乘-异或-移位雪崩最终化器（自拟常量，与内置噪声混合器不同），令每位扩散至
     * 整个字长，使相邻键/tick 不产生可轻易相关的流。
     */
    private static long mix(long x) {
        x ^= x >>> 32;
        x *= 0xD6E8FEB86659FD93L;
        x ^= x >>> 28;
        x *= 0xE7037ED1A0B428DBL;
        x ^= x >>> 32;
        x *= 0x8EABC8B3F17D0F39L;
        return x ^ (x >>> 26);
    }
}