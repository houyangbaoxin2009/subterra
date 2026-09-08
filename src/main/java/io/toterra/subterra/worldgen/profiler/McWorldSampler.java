package io.toterra.subterra.worldgen.profiler;

import java.lang.reflect.InvocationTargetException;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.dimension.DimensionType;

import io.toterra.subterra.api.worldgen.profiler.WorldSampler;

/**
 * The MC binding of {@link WorldSampler} (p.1.8.30 "World Profiler"): reads a live
 * {@link ServerLevel} into the pure-data view the profiling engine consumes. Every
 * query is guarded at the call site by the engine's own failure discipline (this
 * type never throws past {@code executeProfile} in the hook); the router field
 * accessor is the only query that intentionally surfaces failures, as an
 * {@link IllegalStateException} carrying the offending field name.
 * <p>
 * Vanilla API names verified via javap on the joined 1.21.1 official-mapping jar:
 * {@code Level.getHeight(Heightmap.Types, int, int)} returns {@code int} directly;
 * {@code Level.getBlockState(BlockPos)}, {@code LevelReader.getBiome(BlockPos)}
 * (default method) and {@code DimensionType.minY()/height()} are all public.
 * {@code ChunkGenerator.getSeaLevel()} is the abstract accessor the bound
 * {@code NoiseBasedChunkGenerator} implements. The climate-fields mapped here use
 * the profiler engine's canonical names ({@code StatsEngine.CLIMATE_FIELDS}); the
 * vanilla {@link NoiseRouter} record accessors are {@code continents()} and
 * {@code ridges()} for the engine's {@code continentalness}/{@code ridge} mirrors,
 * so those two names are aliased here (everything else is a 1:1 byte-for-byte match,
 * confirmed on the same jar).
 * <p>
 * Splitting the vertical Y sampling between a chunk that is genuinely generated and
 * one that is not yet loaded is impossible to guarantee from a {@link ServerLevel};
 * the out-of-axis guard ({@code "subterra:out_of_bounds"}) keeps reads deterministic
 * even for not-yet-built columns.
 * <p>
 * {@link WorldSampler} 的 MC 绑定（p.1.8.30 "World Profiler"）：把正在运行的
 * {@link ServerLevel} 读成分析引擎需要的纯数据视图。每个查询都由引擎自身的失败纪律在调用点
 * 保护（本类型绝不在 hook 的 {@code executeProfile} 之外抛出）；唯一刻意暴露失败的是路由器
 * 场访问器，以携带出错字段名的 {@link IllegalStateException} 抛出。
 * <p>
 * 原生 API 名已在 1.21.1 official-mapping jar 上经 javap 验证：{@code Level.getHeight(Heightmap.Types,
 * int, int)} 直接返回 {@code int}；{@code Level.getBlockState(BlockPos)}、{@code LevelReader.getBiome(BlockPos)}
 * （default 方法）与 {@code DimensionType.minY()/height()} 均公开。{@code ChunkGenerator.getSeaLevel()}
 * 为被绑定 {@code NoiseBasedChunkGenerator} 实现的抽象访问器。此处映射的气候场使用分析器引擎的
 * 规范名（{@code StatsEngine.CLIMATE_FIELDS}）；原生 {@link NoiseRouter} record 访问器对引擎
 * 的 {@code continentalness}/{@code ridge} 镜像分别名为 {@code continents()}/{@code ridges()}，
 * 故此处对这两个名字做了别名（其余均逐字一致，已在同一 jar 上确认）。
 * <p>
 * 从一个 {@link ServerLevel} 无法保证按"区块已生成与否"拆分竖直 Y 采样的越界读取；因此轴外
 * 守卫（{@code "subterra:out_of_bounds"}）保证即使对未构建完成的柱，读取也保持确定。
 */
public final class McWorldSampler implements WorldSampler {

    private final ServerLevel level;

    /** The minY of the dimension, cached so every vertical band lookup is cheap. */
    private final int yMin;

    /** The exclusive maxY of the dimension ({@code minY + height}). */
    private final int yMax;

    /** The dimension's byte-for-byte sea level from its chunk generator. */
    private final int sea;

    /**
     * @param level the live server level to read (must not be null).
     */
    public McWorldSampler(ServerLevel level) {
        if (level == null) {
            throw new IllegalArgumentException("level must not be null");
        }
        this.level = level;
        DimensionType dt = level.dimensionType();
        this.yMin = dt.minY();
        this.yMax = Math.addExact(dt.minY(), dt.height());
        this.sea = resolveSeaLevel(level);
    }

    @Override
    public int surfaceY(int x, int z) {
        return level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z);
    }

    @Override
    public String block(int x, int y, int z) {
        if (y < yMin || y >= yMax) {
            return "subterra:out_of_bounds";
        }
        BlockState state = level.getBlockState(new BlockPos(x, y, z));
        ResourceLocation key = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        return key == null ? "subterra:unknown" : key.toString();
    }

    @Override
    public String biome(int x, int y, int z) {
        Holder<Biome> holder = level.getBiome(new BlockPos(x, y, z));
        return holder.unwrapKey().map(k -> k.location().toString()).orElse("subterra:unknown");
    }

    @Override
    public double routerField(String fieldName, int x, int y, int z) {
        NoiseRouter router = level.getChunkSource().randomState().router();
        DensityFunction fn;
        try {
            try {
                fn = (DensityFunction) NoiseRouter.class.getMethod(fieldName).invoke(router);
            } catch (NoSuchMethodException e) {
                String alias = aliasField(fieldName);
                if (alias == null) {
                    throw new NoSuchMethodException(fieldName);
                }
                fn = (DensityFunction) NoiseRouter.class.getMethod(alias).invoke(router);
            }
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            throw new IllegalStateException("cannot read vanilla router field '" + fieldName + "': "
                    + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
        return fn.compute(new BlockContext(x, y, z));
    }

    @Override
    public int seaLevel() {
        return sea;
    }

    @Override
    public int minY() {
        return yMin;
    }

    @Override
    public int maxY() {
        return yMax;
    }

    /**
     * Resolves the dimension sea level. The generator's {@code getSeaLevel()} is the
     * authoritative vanilla reading (verified on the joined 1.21.1 jar); fall back to
     * the level's own {@code getSeaLevel()} and finally to the hardcoded 63 (the
     * overworld default) so the sampler stays usable on exotic generators.
     * <p>
     * 计算维度海平面。生成器的 {@code getSeaLevel()} 是权威的原生读数（已在 1.21.1 jar 上
     * 验证）；依次回退到 {@code level.getSeaLevel()} 与写死的 63（主世界默认），使采样器在
     * 奇异生成器下仍可用。
     */
    private static int resolveSeaLevel(ServerLevel lv) {
        try {
            ChunkGenerator gen = lv.getChunkSource().getGenerator();
            if (gen != null) {
                return gen.getSeaLevel();
            }
        } catch (RuntimeException ignored) {
            // fall through to the level reading below
        }
        try {
            return lv.getSeaLevel();
        } catch (RuntimeException ignored) {
            // last-resort overworld default sea level
        }
        return 63;
    }

    /**
     * Maps the profiler engine's climate-field mirrors to the vanilla {@link NoiseRouter}
     * record accessor names when they differ; returns {@code null} when no alias exists.
     * <p>
     * 把分析器引擎的气候场镜像名映射到原生 {@link NoiseRouter} record 访问器名（两者不同时）；
     * 无别名时返回 {@code null}。
     */
    private static String aliasField(String fieldName) {
        switch (fieldName) {
            case "continentalness":
                return "continents";
            case "ridge":
                return "ridges";
            default:
                return null;
        }
    }

    /**
     * Minimal {@link DensityFunction.FunctionContext} surfacing the raw block
     * coordinates, mirroring {@code SameSeedCompare}'s context.
     * <p>
     * 暴露原始方块坐标的最小 {@link DensityFunction.FunctionContext}，镜像
     * {@code SameSeedCompare} 的上下文。
     */
    private record BlockContext(int x, int y, int z) implements DensityFunction.FunctionContext {
        @Override
        public int blockX() {
            return x;
        }

        @Override
        public int blockY() {
            return y;
        }

        @Override
        public int blockZ() {
            return z;
        }

        @Override
        public Blender getBlender() {
            return Blender.empty();
        }
    }
}