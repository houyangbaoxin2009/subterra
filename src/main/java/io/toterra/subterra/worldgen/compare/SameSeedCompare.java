package io.toterra.subterra.worldgen.compare;

import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import io.toterra.subterra.engine.worldgen.pipeline.router.NoiseRouter;

/**
 * Same-seed authoritative compare bridge (p.1.8.17): a self-contained static runner
 * that samples the fifteen vanilla {@code net.minecraft.world.level.levelgen.NoiseRouter}
 * density fields at fixed coordinates for a {@code worldSeed} (system property
 * {@code subterra.compareSeed}, default {@code 44905237L}) and compares them, field by
 * field, against Subterra's pure-JDK mirror
 * {@link NoiseRouter#overworld(long, int, int)}. Output: a deterministic stdout report
 * plus a CSV at {@code run/compare/<seed>.csv}.
 * <p>
 * Vanilla accessor (verified via javap on the joined 1.21.1 jar): from a booted
 * {@link ServerLevel}, {@code level.getChunkSource()} (a {@link ServerChunkCache}) exposes
 * {@code getGenerator()} and — crucially — {@code randomState()}; the seed-authoritative
 * runtime router is {@link RandomState#router()} (the very instance {@code fillFromNoise}
 * worldgen samples). {@code NoiseBasedChunkGenerator} itself has no public router getter in
 * 1.21.1, so we reflect the fifteen record accessors (names = the mirror's canonical
 * {@link NoiseRouter#FIELD_NAMES}, which are byte-for-byte the vanilla record order).
 * Each {@code DensityFunction} is evaluated at raw block coordinates via a minimal
 * {@link DensityFunction.FunctionContext} (vanilla multiplies by its per-field
 * xz/y/scale internally), i.e. only {@code blockX()/blockY()/blockZ()} are surfaced.
 * <p>
 * Entry paths (p.1.8.18 wires these into a booted server):
 * <ul>
 *   <li>self-check ({@code subterra.compareSelfCheck=true}): skips MC entirely and runs
 *       the mirror twice, printing PASS if deterministic — proves the tool without a world;</li>
 *   <li>full compare: resolves the booted server via {@link ServerLifecycleHooks#getCurrentServer()}
 *       (standalone launcher path), or is invoked in-process by {@link SameSeedCompareHook}
 *       on server start (system property {@code subterra.compareSeed}) or via the
 *       {@code /subterra compare <seed>} command. Samples vanilla + mirror, writes the report.</li>
 * </ul>
 * All MC-touching code is guarded and never throws past {@link #main} / the caller.
 * <p>
 * 同种子权威对拍桥（p.1.8.17）：自包含静态启动器，在给定的 {@code worldSeed}（系统属性
 * {@code subterra.compareSeed}，默认 {@code 44905237L}）下的固定坐标处采样原生
 * {@code net.minecraft.world.level.levelgen.NoiseRouter} 的十五个密度场，并逐场与 Subterra
 * 纯 JDK 镜像 {@link NoiseRouter#overworld(long,int,int)} 对比。输出确定的 stdout 报告与
 * {@code run/compare/<seed>.csv}。
 * <p>
 * 原生访问器（在 1.21.1 merging jar 上经 javap 验证）：从已启动的 {@link ServerLevel}，
 * {@code level.getChunkSource()}（{@link ServerChunkCache}）暴露 {@code getGenerator()} 与关键字
 * {@code randomState()}；按种子联动的运行期路由器为 {@link RandomState#router()}（即
 * {@code fillFromNoise} 世界生成实际采样的同一实例）。{@code NoiseBasedChunkGenerator} 在
 * 1.21.1 中没有公共路由器 getter，故用反射调用十五个 record 访问器（名称 = 镜像规范名
 * {@link NoiseRouter#FIELD_NAMES}，与原生 record 顺序逐字一致）。每个 {@code DensityFunction}
 * 经最小 {@link DensityFunction.FunctionContext} 在原始方块坐标处求值（原生在内部按各场
 * xz/y 比例缩放），即仅暴露 {@code blockX()/blockY()/blockZ()}。
 * <p>
 * 入口路径（p.1.8.18 将下文接入已启动的服务器）：
 * <ul>
 *   <li>自检（{@code subterra.compareSelfCheck=true}）：完全不触碰 MC，镜像连跑两次，确定性则打印
 *       PASS——无需世界即可验证工具；</li>
 *   <li>完整对拍：独立启动路径经 {@link ServerLifecycleHooks#getCurrentServer()} 取已启动服务
 *       器；进程内则由 {@link SameSeedCompareHook} 在服务启动时（系统属性 {@code subterra.compareSeed}）
 *       或经 {@code /subterra compare <seed>} 命令调用。采样原生+镜像并写报告。</li>
 * </ul>
 * 所有触碰 MC 的代码均受保护，绝不从 {@link #main} 或调用方抛出异常。
 */
public final class SameSeedCompare {

    /** Default world seed when {@code subterra.compareSeed} is absent. */
    private static final long DEFAULT_SEED = 44905237L;

    /** Default overworld Y range used to (re)build the mirror (vanilla block range). */
    private static final int MIRROR_MIN_Y = -64;
    private static final int MIRROR_MAX_Y = 320;

    /**
     * A mid-altitude altitude per column is chosen near sea level (y=64) because the
     * composite density fields (depth / initial / final) transition sharply there; a second
     * low sample (y=16) sits in the common cave / ore-vein band above bedrock. Two deeper
     * samples cover the bedrock band (y=0 transition, and the top of the deepslate/base
     * rock at y=-40); all Y values sit within the mirror build range [-64, 320] and are
     * fully deterministic. The three columns span different octave regimes: origin, a
     * modest offset, and a large offset.
     * 每个列的中部采样高度选在海面附近（y=64），因为组合密度场（depth / initial / final）在
     * 此处变化剧烈；第二个低采样（y=16）位于基岩之上的常见洞穴 / 矿脉带。另有两个更深采样覆盖
     * 基岩带（y=0 的过渡地带，以及深板岩/底部基岩顶部的 y=-40）；所有 Y 值均落在镜像构建范围
     * [-64, 320] 内且完全确定。三个列跨越不同 octave 量纲：原点、中等偏移、较大偏移。
     */
    private static List<SamplePoint> samplePoints() {
        final long[][] columns = { {0L, 0L}, {123L, -456L}, {9999L, 3L} };
        final int[] ys = {-40, 0, 16, 64};
        List<SamplePoint> out = new ArrayList<>();
        for (long[] c : columns) {
            for (int y : ys) {
                out.add(new SamplePoint(c[0], y, c[1]));
            }
        }
        return out;
    }

    private SameSeedCompare() {
    }

    /** Entry point. Never throws. */
    public static void main(String[] args) {
        try {
            boolean selfCheck = getBool("subterra.compareSelfCheck", false);
            if (selfCheck) {
                System.exit(selfCheck());
                return;
            }
            long seed = getLong("subterra.compareSeed", DEFAULT_SEED);
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server == null) {
                blocked("no booted MinecraftServer in this process; run inside a NeoForge dev server (plain JavaExec has no game, so only self-check runs here).");
            }
            ServerLevel level = server.overworld();
            if (level == null) {
                blocked("server has no overworld ServerLevel yet.");
            }
            runAgainst(level, seed);
        } catch (CompareBlocked e) {
            blocked(e.getMessage());
        } catch (Throwable t) {
            blocked("unexpected: " + t);
        }
    }

    /**
     * Performs the full vanilla-vs-mirror compare against a live {@link ServerLevel},
     * prints the deterministic report to stdout and writes {@code run/compare/<seed>.csv}.
     * 对已启动的 {@link ServerLevel} 执行完整原生 vs 镜像对拍，向 stdout 打印确定性报告并写
     * {@code run/compare/<seed>.csv}。
     *
     * @param level the booted overworld level.
     * @return a process exit code (0 = ok, 2 = blocked).
     */
    public static int runAgainst(ServerLevel level, long seed) {
        runAgainstReport(level, seed);
        return 0;
    }

    /**
     * In-server compare entry (p.1.8.18): computes the vanilla-vs-mirror matrices, renders
     * the stdout report + CSV (same output as {@link #runAgainst}), and returns the aggregate
     * summary so callers can log it (e.g. at INFO on server start). Throws the guarded
     * {@code CompareBlocked} signal on router/server failure — callers must catch it.
     * 服务端对拍入口（p.1.8.18）：计算原生 vs 镜像矩阵，渲染 stdout 报告与 CSV（与
     * {@link #runAgainst} 相同输出），并返回聚合摘要供调用方记录（如服务启动时以 INFO 级别）。
     * 路由器/服务器不可用时抛出受保护的 {@code CompareBlocked} 信号——调用方必须捕获。
     *
     * @param level the booted overworld level.
     * @param seed  the world seed to compare against.
     * @return the aggregate bit-equality summary.
     */
    public static CompareReport runAgainstReport(ServerLevel level, long seed) {
        CompareResult r = compute(level, seed);
        renderStdout(r, seed);
        FieldStats s = stats(r);
        return new CompareReport(seed, s.bitEqualTotal, r.fieldCount * r.pointCount);
    }

    /**
     * Command-facing entry (p.1.8.18): runs the same compare on the command source's level
     * with the given seed and sends a compact per-field summary to the sender. Never throws
     * past the caller; failures are sent to the sender and logged as {@code BLOCKED}.
     * 命令入口（p.1.8.18）：在命令源所在维度以给定种子执行同一对拍，并向发送者发送紧凑的逐场
     * 摘要。绝不向调用方抛异常；失败会以 {@code BLOCKED} 发送给发送者并记录。
     *
     * @param stack the command source (its level is used; may differ from the overworld).
     * @param seed  the world seed to compare against.
     */
    public static void reportToSender(CommandSourceStack stack, long seed) {
        try {
            ServerLevel level = stack.getLevel();
            if (level == null) {
                stack.sendFailure(Component.literal("[SameSeedCompare] BLOCKED: no ServerLevel for the command source."));
                return;
            }
            CompareResult r = compute(level, seed);
            renderStdout(r, seed);
            FieldStats s = stats(r);
            stack.sendSuccess(() -> Component.literal("[SameSeedCompare] compare seed=" + seed), false);
            for (int i = 0; i < r.fieldCount(); i++) {
                final int fi = i;
                stack.sendSuccess(() -> Component.literal(String.format(
                        "  %-30s bitEqual %d/%d  maxAbsDiff %g",
                        NoiseRouter.FIELD_NAMES[fi], s.bitEqualCount[fi], r.pointCount(), s.maxAbs[fi])), false);
            }
            stack.sendSuccess(() -> Component.literal(String.format(
                    "[SameSeedCompare] %d/%d field-samples bit-equal; csv=run/compare/%d.csv",
                    s.bitEqualTotal, r.fieldCount() * r.pointCount(), seed)), false);
        } catch (CompareBlocked e) {
            stack.sendFailure(Component.literal("[SameSeedCompare] BLOCKED: " + e.getMessage()));
        } catch (Throwable t) {
            String reason = t.getMessage() != null && !t.getMessage().isBlank() ? t.getMessage() : t.toString();
            stack.sendFailure(Component.literal("[SameSeedCompare] BLOCKED: " + reason));
        }
    }

    /**
     * Samples the fifteen vanilla and mirror router fields at every fixed point for a seed and
     * packages them as a positioned matrix. Mirrors are unbounded here except the guarded
     * {@code CompareBlocked} on router/server failure.
     * 对给定种子在每个固定点采样十五个原生与镜像路由器场，并封装为带坐标的矩阵。除受保护的
     * {@code CompareBlocked}（路由器/服务器失败）外不向调用方抛其他异常。
     */
    private static CompareResult compute(ServerLevel level, long seed) {
        final List<SamplePoint> points = samplePoints();
        final int fieldCount = NoiseRouter.FIELD_NAMES.length; // 15
        final int pointCount = points.size();

        // Mirror side: pure-JDK reconstructed overworld router for the seed.
        NoiseRouter mirror;
        try {
            mirror = NoiseRouter.overworld(seed, MIRROR_MIN_Y, MIRROR_MAX_Y);
        } catch (RuntimeException e) {
            throw new CompareBlocked("mirror NoiseRouter.overworld failed: " + e);
        }
        double[][] mi = new double[fieldCount][pointCount];
        for (int pi = 0; pi < pointCount; pi++) {
            SamplePoint p = points.get(pi);
            for (int i = 0; i < fieldCount; i++) {
                mi[i][pi] = mirror.fieldAt(i).eval((double) p.x(), (double) p.y(), (double) p.z());
            }
        }

        // Vanilla side: seed-authoritative router from the live chunk cache.
        double[][] va = new double[fieldCount][pointCount];
        for (int pi = 0; pi < pointCount; pi++) {
            double[] v = sampleVanillaFields(level, points.get(pi));
            for (int i = 0; i < fieldCount; i++) {
                va[i][pi] = v[i];
            }
        }
        return new CompareResult(points, fieldCount, pointCount, mi, va);
    }

    /**
     * Computes the per-field bit-equal counts and per-field max absolute diff from the
     * vanilla/mirror matrices. 由原生/镜像矩阵计算逐场逐位相等计数与逐场最大绝对差。
     */
    private static FieldStats stats(CompareResult r) {
        int[] bitEqualCount = new int[r.fieldCount()];
        double[] maxAbs = new double[r.fieldCount()];
        int bitEqualTotal = 0;
        for (int i = 0; i < r.fieldCount(); i++) {
            for (int pi = 0; pi < r.pointCount(); pi++) {
                double van = r.va()[i][pi];
                double m = r.mi()[i][pi];
                if (Double.doubleToLongBits(van) == Double.doubleToLongBits(m)) {
                    bitEqualCount[i]++;
                }
                maxAbs[i] = Math.max(maxAbs[i], Math.abs(van - m));
            }
            bitEqualTotal += bitEqualCount[i];
        }
        return new FieldStats(bitEqualCount, maxAbs, bitEqualTotal);
    }

    /**
     * Writes the CSV ({@code run/compare/<seed>.csv}) and prints the deterministic per-field
     * summary lines to stdout (shared by every in-server/standalone entry).
     * 写 CSV（{@code run/compare/<seed>.csv}）并向 stdout 打印确定的逐场摘要行（所有服务端/独立
     * 入口共用）。
     */
    private static void renderStdout(CompareResult r, long seed) {
        try {
            Files.createDirectories(Path.of("compare"));
        } catch (IOException e) {
            System.out.println("[SameSeedCompare] WARN: could not create run/compare dir: " + e);
        }
        Path csv = Path.of("compare", seed + ".csv");
        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(csv, StandardCharsets.UTF_8))) {
            w.println("field,x,y,z,vanilla,mirror,absDiff,bitEqual");
            for (int i = 0; i < r.fieldCount(); i++) {
                for (int pi = 0; pi < r.pointCount(); pi++) {
                    SamplePoint p = r.points().get(pi);
                    double van = r.va()[i][pi];
                    double m = r.mi()[i][pi];
                    w.printf("%s,%d,%d,%d,%.17g,%.17g,%.3g,%s%n",
                            NoiseRouter.FIELD_NAMES[i], p.x(), p.y(), p.z(), van, m, Math.abs(van - m),
                            Double.doubleToLongBits(van) == Double.doubleToLongBits(m));
                }
            }
        } catch (IOException e) {
            System.out.println("[SameSeedCompare] WARN: could not write csv " + csv + ": " + e);
        }
        FieldStats s = stats(r);
        for (int i = 0; i < r.fieldCount(); i++) {
            System.out.printf("[SameSeedCompare] field %-30s bitEqual %d/%d  maxAbsDiff %g%n",
                    NoiseRouter.FIELD_NAMES[i], s.bitEqualCount[i], r.pointCount(), s.maxAbs[i]);
        }
        int samples = r.fieldCount() * r.pointCount();
        System.out.printf("[SameSeedCompare] %d/%d field-samples bit-equal; max abs diff per field above; csv=%s%n",
                s.bitEqualTotal, samples, csv.toAbsolutePath());
    }

    /**
     * Samples the fifteen vanilla router fields at {@code p}, returning the field values in
     * {@link NoiseRouter#FIELD_NAMES} order. Guarded and never thrown past the caller.
     * 在 {@code p} 处采样十五个原生路由器场，按 {@link NoiseRouter#FIELD_NAMES} 顺序返回场值。
     */
    private static double[] sampleVanillaFields(ServerLevel level, SamplePoint p) {
        ServerChunkCache chp = level.getChunkSource();
        ChunkGenerator gen = chp.getGenerator();
        if (!(gen instanceof NoiseBasedChunkGenerator)) {
            throw new CompareBlocked("generator is not a NoiseBasedChunkGenerator: "
                    + (gen == null ? "null" : gen.getClass().getName())
                    + " -> cannot reach a NoiseRouter.");
        }
        DensityFunction.FunctionContext ctx = new BlockContext((int) p.x(), p.y(), (int) p.z());
        // The seed-authoritative router is the live chunk cache's RandomState.router()
        // (worldgen samples this same instance); the record accessors match FIELD_NAMES.
        net.minecraft.world.level.levelgen.NoiseRouter router = chp.randomState().router();
        double[] out = new double[NoiseRouter.FIELD_NAMES.length];
        for (int i = 0; i < out.length; i++) {
            DensityFunction f;
            try {
                f = (DensityFunction) net.minecraft.world.level.levelgen.NoiseRouter.class
                        .getMethod(NoiseRouter.FIELD_NAMES[i]).invoke(router);
            } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
                throw new CompareBlocked("cannot read vanilla router field '" + NoiseRouter.FIELD_NAMES[i]
                        + "': " + e.getClass().getSimpleName() + ": " + e.getMessage());
            }
            out[i] = f.compute(ctx);
        }
        return out;
    }

    /** Deterministic self-check: run the pure-JDK mirror twice and confirm equality. */
    private static int selfCheck() {
        final long seed = getLong("subterra.compareSeed", DEFAULT_SEED);
        List<SamplePoint> points = samplePoints();
        try {
            NoiseRouter a = NoiseRouter.overworld(seed, MIRROR_MIN_Y, MIRROR_MAX_Y);
            NoiseRouter b = NoiseRouter.overworld(seed, MIRROR_MIN_Y, MIRROR_MAX_Y);
            // Independent runs must agree bit-for-bit on every field at every point.
            for (SamplePoint p : points) {
                for (int i = 0; i < NoiseRouter.FIELD_NAMES.length; i++) {
                    double v1 = a.fieldAt(i).eval((double) p.x(), (double) p.y(), (double) p.z());
                    double v2 = b.fieldAt(i).eval((double) p.x(), (double) p.y(), (double) p.z());
                    if (Double.doubleToLongBits(v1) != Double.doubleToLongBits(v2)) {
                        System.out.println("[SameSeedCompare] self-check FAIL (non-deterministic) at " + p + " field "
                                + NoiseRouter.FIELD_NAMES[i] + ": " + v1 + " vs " + v2);
                        return 1;
                    }
                }
            }
            System.out.println("[SameSeedCompare] self-check PASS (mirror deterministic)");
            return 0;
        } catch (RuntimeException e) {
            System.out.println("[SameSeedCompare] self-check FAIL (mirror threw): " + e);
            return 1;
        }
    }

    // ---- config helpers ----

    private static long getLong(String key, long dflt) {
        String v = System.getProperty(key);
        if (v == null || v.isBlank()) {
            return dflt;
        }
        try {
            return Long.parseLong(v.trim());
        } catch (NumberFormatException e) {
            System.out.println("[SameSeedCompare] WARN: bad " + key + "=" + v + ", using default " + dflt);
            return dflt;
        }
    }

    private static boolean getBool(String key, boolean dflt) {
        String v = System.getProperty(key);
        if (v == null || v.isBlank()) {
            return dflt;
        }
        return Boolean.parseBoolean(v.trim());
    }

    private static void blocked(String reason) {
        System.out.println("[SameSeedCompare] BLOCKED: " + reason);
        System.exit(2);
    }

    // ---- records ----

    /**
     * Minimal {@link DensityFunction.FunctionContext} surfacing the raw block coordinates.
     */
    private record BlockContext(int x, int y, int z) implements DensityFunction.FunctionContext {
        @Override
        public int blockX() { return x; }

        @Override
        public int blockY() { return y; }

        @Override
        public int blockZ() { return z; }

        @Override
        public Blender getBlender() { return Blender.empty(); }
    }

    /** Computed compare matrices for one seed+level run (parallel to {@link SamplePoint} order). */
    private record CompareResult(List<SamplePoint> points, int fieldCount, int pointCount,
                                 double[][] mi, double[][] va) {
    }

    /** Per-field summary stats (bit-equal counts + per-field max abs diff). */
    private record FieldStats(int[] bitEqualCount, double[] maxAbs, int bitEqualTotal) {
    }

    /** Aggregate bit-equality summary returned to in-server callers for logging. */
    public record CompareReport(long seed, int bitEqualTotal, int totalSamples) {
    }

    /** Internal signal meaning "vanilla router / server unavailable" (never escapes a caller). */
    private static final class CompareBlocked extends RuntimeException {
        CompareBlocked(String message) {
            super(message);
        }
    }
}