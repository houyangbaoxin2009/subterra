package io.toterra.subterra.engine.worldgen.pipeline.noise;

/**
 * Per-noise seed salt / mixing (p.1.8.6), reproducing how Minecraft 1.21.1
 * derives a noise field's PRNG seed from the world seed and a per-noise salt.
 * <p>
 * MC builds each noise field's random source by combining the world seed (the
 * "master seed") with a per-noise salt as a straight XOR before the value is
 * doubled/mixed by the PRNG's own seeding step
 * ({@code new XoroshiroRandomSource(seed ^ salt)}); there is no additional
 * MathHelper-style hashing in the core noise-seed path of 1.21.1. {@link #mix}
 * exposes exactly that seed-argument combination so that {@code new
 * XoroRandom(NoiseSalt.mix(master, salt))} reproduces the MC-noise stream for
 * that salt. XOR is its own inverse, so the operation is symmetric and
 * deterministic for any pair of words.
 * <p>
 * 每噪声的种子盐 / 混合（p.1.8.6）：复现 Minecraft 1.21.1 如何从世界种子
 * （“主种子”）与每噪声盐派生出该噪声场 PRNG 的种子。MC 在 PRNG 自身上升
 * 混合前，用直异或将世界种子与盐组合（{@code new XoroshiroRandomSource(
 * seed ^ salt)}），核心噪声种子路径并无额外（MathHelper 式）散列；{@link
 * #mix} 恰好给出该种子实参，使 {@code new XoroRandom(
 * NoiseSalt.mix(master, salt))} 按该盐逐位复现 MC 噪声流。异或为自逆运算，
 * 对任意两词均对称且确定。
 */
public final class NoiseSalt {

    private NoiseSalt() {
    }

    /**
     * Combines a master seed and a per-noise salt into the PRNG seed argument
     * MC passes when building that noise field.
     *
     * @param masterSeed the world seed.
     * @param salt       the per-noise salt.
     * @return {@code masterSeed ^ salt}.
     */
    public static long mix(long masterSeed, long salt) {
        return masterSeed ^ salt;
    }
}