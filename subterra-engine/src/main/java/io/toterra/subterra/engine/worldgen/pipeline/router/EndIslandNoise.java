package io.toterra.subterra.engine.worldgen.pipeline.router;

import io.toterra.subterra.engine.worldgen.pipeline.density.Density;
import io.toterra.subterra.engine.worldgen.pipeline.noise.LegacyRandom;
import io.toterra.subterra.engine.worldgen.pipeline.noise.simplex.SimplexNoise;

/**
 * The end-island shape leaf (p.1.8.29B), mirroring MC 1.21.1's
 * {@code net.minecraft.world.level.levelgen.DensityFunctions$EndIslandDensityFunction}
 * — the {@code minecraft:end_islands} density function. Clean-room transcription
 * verified via javap on the de-obfuscated 1.21.1 class.
 * <p>
 * The island noise simplex is seeded from
 * {@code new LegacyRandomSource(seed)} with exactly {@code consumeCount(17292)}
 * (17,292 discarded {@code nextInt()} calls) before the lattice build, and the
 * 2-D height field is the classic per-half-cell "main island + ring archipelago"
 * shape: {@code f = clamp(100 - sqrt(x²+z²)·8, -100, 80)} over a 25×25
 * neighbourhood of half-cells, folding each candidate through a vanilla-derived
 * pseudo-random radius offset
 * ({@code ((|lx|·3439 + |lz|·147 + 13) % 9 + 9)} — the 1.21.1 mod-form, absent
 * from the older 1.16 formula) and taking the running maximum. All interior
 * arithmetic follows the bytecode as {@code float} so the resulting heights are
 * bit-identical to vanilla; the {@code compute} mapping is
 * {@code (getHeightValue(x/8, z/8) - 8) / 128} on the block coordinate (the
 * island signal lives on the 8-block grid).
 * <p>
 * 末地岛屿形状叶（p.1.8.29B），镜像 MC 1.21.1 的
 * {@code DensityFunctions$EndIslandDensityFunction}——即
 * {@code minecraft:end_islands} 密度函数。经 javap 对照反混淆后的 1.21.1 类
 * 净室复现。岛屿 simplex 以 {@code new LegacyRandomSource(seed)} 播种并恰在
 * 建表前 {@code consumeCount(17292)}（丢弃 17,292 个 {@code nextInt()}）；二维
 * 高度场为经典的逐半单元"主岛 + 环岛群岛"形状：在 25×25 半单元邻域上取
 * {@code f = clamp(100 - sqrt(x²+z²)·8, -100, 80)}，每个候选点折叠经
 * vanilla 派生的伪随机半径偏移（{@code ((|lx|·3439 + |lz|·147 + 13) % 9 + 9)}——
 * 1.21.1 取模式，旧 1.16 公式无此项）并取运行最大值。内部算术按字节码全部以
 * {@code float} 进行，所得高度与 vanilla 逐位一致；{@code compute} 映射为
 * 块坐标上的 {@code (getHeightValue(x/8, z/8) - 8) / 128}（岛屿信号落在
 * 8 块网格上）。
 */
public final class EndIslandNoise implements Density {

    /** Vanilla {@code new LegacyRandomSource(seed)} draw count before the lattice. */
    private static final int CONSUME = 17292;

    /** Vanilla {@code minValue()}. */
    public static final double MIN_VALUE = -0.84375;
    /** Vanilla {@code maxValue()}. */
    public static final double MAX_VALUE = 0.5625;

    private final long seed;
    private final SimplexNoise islandNoise;

    /**
     * @param seed the world seed (vanilla constructs this leaf directly from the
     *             world seed and re-creates it per random state).
     */
    public EndIslandNoise(long seed) {
        this.seed = seed;
        LegacyRandom random = new LegacyRandom(seed);
        for (int i = 0; i < CONSUME; i++) {
            random.nextInt();
        }
        this.islandNoise = new SimplexNoise(random);
    }

    /** Vanilla {@code DensityFunctions.endIslands(seed)} factory. */
    public static EndIslandNoise of(long seed) {
        return new EndIslandNoise(seed);
    }

    /**
     * The height field at the 8-block-grid coordinate ({@code x}, {@code z}) —
     * vanilla {@code getHeightValue(SimplexNoise, int, int)}. Float-precise.
     */
    public static double heightValue(SimplexNoise noise, int x, int z) {
        int nx = x / 2;
        int nz = z / 2;
        int rx = x % 2;
        int rz = z % 2;
        float f = 100.0f - (float) Math.sqrt((float) (x * x + z * z)) * 8.0f;
        f = clamp(f, -100.0f, 80.0f);
        for (int i = -12; i <= 12; i++) {
            for (int j = -12; j <= 12; j++) {
                long lx = (long) (nx + i);
                long lz = (long) (nz + j);
                if (lx * lx + lz * lz <= 4096L) {
                    continue;
                }
                if (noise.getValue((double) lx, (double) lz) >= (double) -0.9f) {
                    continue;
                }
                float radius = (Math.abs((float) lx) * 3439.0f + Math.abs((float) lz) * 147.0f + 13.0f)
                        % 9.0f + 9.0f;
                float ox = (float) (rx - i * 2);
                float oz = (float) (rz - j * 2);
                float height = 100.0f - (float) Math.sqrt(ox * ox + oz * oz) * radius;
                height = clamp(height, -100.0f, 80.0f);
                f = Math.max(f, height);
            }
        }
        return f;
    }

    private static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    @Override
    public double eval(double x, double y, double z) {
        return (heightValue(this.islandNoise, (int) x / 8, (int) z / 8) - 8.0) / 128.0;
    }

    /** Vanilla {@code minValue()}. */
    public double minValue() {
        return MIN_VALUE;
    }

    /** Vanilla {@code maxValue()}. */
    public double maxValue() {
        return MAX_VALUE;
    }

    /** The world seed (for td self-description). */
    public long seed() {
        return this.seed;
    }

    /** Minimal self-description {@code "[ seed = <worldSeed> ]"}. */
    public String td() {
        return "[ seed = " + this.seed + " ]";
    }

    /** Parses a {@link #td()} snippet back into an equal instance. */
    public static EndIslandNoise fromTd(String td) {
        if (td == null) {
            throw new IllegalArgumentException("td must not be null");
        }
        int lb = td.indexOf('[');
        int rb = td.indexOf(']');
        if (lb < 0 || rb < 0 || rb <= lb) {
            throw new IllegalArgumentException("cannot parse td: " + td);
        }
        String body = td.substring(lb + 1, rb);
        int eq = body.indexOf('=');
        if (eq < 0) {
            throw new IllegalArgumentException("cannot parse td: " + td);
        }
        try {
            return new EndIslandNoise(Long.parseLong(body.substring(eq + 1).trim()));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("bad seed in td: " + td, e);
        }
    }
}