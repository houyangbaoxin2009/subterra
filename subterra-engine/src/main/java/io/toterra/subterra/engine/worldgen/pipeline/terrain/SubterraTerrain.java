package io.toterra.subterra.engine.worldgen.pipeline.terrain;

import io.toterra.subterra.engine.worldgen.pipeline.density.Density;
import io.toterra.subterra.engine.worldgen.pipeline.noise.simplex.NormalNoise;
import io.toterra.subterra.engine.worldgen.pipeline.router.PositionalRand;
import io.toterra.subterra.engine.worldgen.pipeline.noise.XoroRandom;

/**
 * p.1.8.34 Subterra 自有主世界地形模型（设计变更：不再还原原版密度配方）。纯 JDK、确定性。
 *
 * <p>三层自有噪声栈 + 海平面 127：
 * <ul>
 *   <li><b>大陆</b>（波长 ~4k 块）：base 主题，决定海陆分布；</li>
 *   <li><b>丘陵</b>（波长 ~130 块）：地表细碎起伏；</li>
 *   <li><b>山脊</b>（波长 ~640 块，ridged 1−|n| 平方）：仅内陆（大陆值过阈）成山，
 *       山高至 ~250；</li>
 *   <li><b>三维细节</b>（波长 ~64 块）：轻微洞穴/悬垂扰动，不改变宏观形状。</li>
 * </ul>
 * 地表高度 {@code surface(x,z)} 与密度 {@code (surface − y) × 0.30 + detail × 0.25}：
 * 密度在 y=surface 处过零——海 127 以下低地表区自然成海。与原版配方的关键差异：
 * 无气候样条、无滑移带、无 noodle、无气候扭曲（p.1.8.33 实证其对角条带伪影）。
 * 同种子同地形；禁时序断言由 TerrainAxisProbe 守护。
 *
 * <p>The p.1.8.34 Subterra-native overworld terrain model (design change: the vanilla
 * density recipe is no longer replicated). Pure JDK, deterministic. Three own noise
 * stacks + sea level 127: continent (~4k wavelength, land/ocean), hills (~130),
 * ridged mountains (~640, inland-only, up to ~250), and a mild 3-D detail term. The
 * density crosses zero at {@code y = surface(x,z)}. Key differences from the vanilla
 * recipe: no climate splines, no slide bands, no noodle, no climate warp (the
 * p.1.8.33 diagonal-banding artifact). Same seed → same terrain.
 */
public final class SubterraTerrain {

    /** 设计海平面。 / The design sea level. */
    public static final double SEA_LEVEL = 127.0;

    /** 密度过零斜率（每块）。 / The density slope per block. */
    private static final double SLOPE = 0.30;

    /** 噪声派生盐。 / The noise derivation salts. */
    private static final String SALT_CONTINENT = "subterra:subterra_continent";
    private static final String SALT_HILLS = "subterra:subterra_hills";
    private static final String SALT_RIDGE = "subterra:subterra_ridge";
    private static final String SALT_DETAIL = "subterra:subterra_detail";

    private SubterraTerrain() {
    }

    /**
     * 主世界最终密度（海 {@link #SEA_LEVEL}）。纯函数、无状态共享、可多线程并发求值。
     *
     * <p>The overworld final density at the design sea level; pure, stateless, thread-safe.
     */
    public static Density finalDensity(long worldSeed) {
        // 大陆（base 波长 2^11 × scale 0.5 ≈ 4096 块）：海陆主题。
        NormalNoise continent = noise(worldSeed, SALT_CONTINENT, -11,
                new double[]{1, 1, 1, 1, 1, 1, 1, 1});
        // 丘陵（2^6 × 0.5 ≈ 128 块）。
        NormalNoise hills = noise(worldSeed, SALT_HILLS, -6,
                new double[]{1, 1, 1, 1});
        // 山脊（2^8 × 0.4 ≈ 655 块，ridged）。
        NormalNoise ridge = noise(worldSeed, SALT_RIDGE, -8,
                new double[]{1, 1, 1, 1, 1});
        // 三维细节（2^4 × 0.25 ≈ 64 块）。
        NormalNoise detail = noise(worldSeed, SALT_DETAIL, -4,
                new double[]{1, 1, 1});

        return (x, y, z) -> {
            double c = continent.getValue(x * 0.5, 0, z * 0.5);
            double surface = SEA_LEVEL - 5.0 + 95.0 * c + 16.0 * hills.getValue(x * 0.5, 0, z * 0.5);
            // 山地掩码：仅内陆（c > 0.15）隆起，平方缓入。
            double mask = clamp((c - 0.15) / 0.5);
            if (mask > 0.0) {
                double r = 1.0 - Math.abs(ridge.getValue(x * 0.4, 0, z * 0.4));
                surface += mask * mask * r * r * 115.0;
            }
            double detailTerm = detail.getValue(x * 0.25, y * 0.06, z * 0.25) * 0.25;
            return (surface - y) * SLOPE + detailTerm;
        };
    }

    /** clamped linear unit step。 / Clamped linear unit step. */
    private static double clamp(double v) {
        return v < 0.0 ? 0.0 : v > 1.0 ? 1.0 : v;
    }

    /** 自有参数噪声实例（不经 vanilla 标签表）。 / An own-parameter noise instance (no vanilla label table). */
    private static NormalNoise noise(long worldSeed, String salt, int firstOctave, double[] amplitudes) {
        PositionalRand state = PositionalRand.ofMaster(worldSeed).fromHashOf(salt);
        return NormalNoise.create(new XoroRandom(state.seedLo(), state.seedHi()), firstOctave, amplitudes);
    }
}
