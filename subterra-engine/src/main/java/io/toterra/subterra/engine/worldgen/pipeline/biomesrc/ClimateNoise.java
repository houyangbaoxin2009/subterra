package io.toterra.subterra.engine.worldgen.pipeline.biomesrc;

import java.util.Objects;

import io.toterra.subterra.engine.worldgen.pipeline.density.Density;
import io.toterra.subterra.engine.worldgen.pipeline.router.NoiseRouter;

/**
 * The six-dimension climate <em>sampler</em> (p.1.8.16) that reads
 * temperature / vegetation(humidity) / continentalness / erosion / depth / ridges
 * off a p.1.8.12 {@link NoiseRouter} and folds them into a {@link ClimateParam}.
 * It mirrors MC 1.21.1's {@code Climate.Sampler}, whose {@code sample(x, y, z)}
 * treats its arguments as quart coordinates, converts them to block coordinates via
 * {@code QuartPos.toBlock(q) = q << 2} (i.e. {@value #CELL_WIDTH} blocks per cell)
 * and evaluates the six {@code DensityFunction}s at those block coordinates — the
 * same {@code NoiseRouter} climate fields.
 * <p>
 * So a {@link ClimateParam} is deterministic and seed-relative: identical router +
 * identical coordinates give an identical six-tuple.
 *
 * <p>六维气候<em>采样器</em>（p.1.8.16）：从 p.1.8.12 {@link NoiseRouter} 读取
 * 温度 / 植被（湿度）/ 大陆性 / 侵蚀 / 深度 / 山脊并折成一个 {@link ClimateParam}。
 * 它镜像 MC 1.21.1 的 {@code Climate.Sampler}：其 {@code sample(x,y,z)} 把参数视为
 * quart 坐标，经 {@code QuartPos.toBlock(q) = q << 2}（即每单元 {@value #CELL_WIDTH}
 * 个方块）换算为方块坐标再对六个 {@code DensityFunction} 求值——即同一个
 * {@code NoiseRouter} 气候字段。
 * 因此 {@link ClimateParam} 确定性且随种子变化：同一路由器 + 同一坐标得到相同六元组。
 */
public final class ClimateNoise {

    /** Blocks per climate cell; mirrors {@code QuartPos.toBlock} step ({@code << 2}). */
    public static final int CELL_WIDTH = 4;

    private final Density temperature;
    private final Density humidity;
    private final Density continentalness;
    private final Density erosion;
    private final Density depth;
    private final Density ridges;

    /** Builds a sampler from six independent climate fields. */
    public ClimateNoise(Density temperature, Density humidity, Density continentalness,
                        Density erosion, Density depth, Density ridges) {
        this.temperature = Objects.requireNonNull(temperature, "temperature");
        this.humidity = Objects.requireNonNull(humidity, "humidity");
        this.continentalness = Objects.requireNonNull(continentalness, "continentalness");
        this.erosion = Objects.requireNonNull(erosion, "erosion");
        this.depth = Objects.requireNonNull(depth, "depth");
        this.ridges = Objects.requireNonNull(ridges, "ridges");
    }

    /** Builds a sampler from the p.1.8.12 router's six climate fields. */
    public ClimateNoise(NoiseRouter router) {
        this(Objects.requireNonNull(router, "router").temperature(),
                router.vegetation(),
                router.continents(),
                router.erosion(),
                router.depth(),
                router.ridges());
    }

    /**
     * Samples the six climate fields at <em>quart</em> coordinates (as
     * {@code Climate.Sampler.sample} does): the fields are evaluated at block
     * coordinates {@code (x*CELL_WIDTH, y*CELL_WIDTH, z*CELL_WIDTH)}.
     */
    public ClimateParam sample(int x, int y, int z) {
        double bx = x * (double) CELL_WIDTH;
        double by = y * (double) CELL_WIDTH;
        double bz = z * (double) CELL_WIDTH;
        return sampleAtBlock(bx, by, bz);
    }

    /** Samples once; alias of {@link #sample}. */
    public ClimateParam eval(int x, int y, int z) {
        return sample(x, y, z);
    }

    /**
     * Evaluates the six climate fields directly at <em>block</em> coordinates — the
     * quantity that {@code sample} feeds after the quart-to-block conversion.
     */
    public ClimateParam sampleAtBlock(double bx, double by, double bz) {
        return new ClimateParam(
                temperature.eval(bx, by, bz),
                humidity.eval(bx, by, bz),
                continentalness.eval(bx, by, bz),
                erosion.eval(bx, by, bz),
                depth.eval(bx, by, bz),
                ridges.eval(bx, by, bz));
    }

    /** Renders the single point (for a fully-sampled diagnostic). */
    @Override
    public String toString() {
        return "ClimateNoise[cell=" + CELL_WIDTH + "]";
    }
}