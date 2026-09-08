package io.toterra.subterra.engine.worldgen.profiler.facade;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import io.toterra.subterra.api.worldgen.profiler.BlockEntry;
import io.toterra.subterra.api.worldgen.profiler.BiomeEntry;
import io.toterra.subterra.api.worldgen.profiler.CaveBand;
import io.toterra.subterra.api.worldgen.profiler.HeightStats;
import io.toterra.subterra.api.worldgen.profiler.ProfileCategory;
import io.toterra.subterra.api.worldgen.profiler.ProfileFormat;
import io.toterra.subterra.api.worldgen.profiler.ProfilePlan;
import io.toterra.subterra.api.worldgen.profiler.ProfileReport;
import io.toterra.subterra.api.worldgen.profiler.ProfileSink;
import io.toterra.subterra.api.worldgen.profiler.WorldSampler;
import io.toterra.subterra.engine.worldgen.profiler.core.StatsEngine;
import io.toterra.subterra.engine.worldgen.profiler.report.TdWriter;
import io.toterra.subterra.engine.worldgen.profiler.report.ZdWriter;

/**
 * The pure-JDK entry point that runs a world profile and persists it
 * (p.1.8.30 "World Profiler"). Scanning always happens via {@link StatsEngine};
 * file output only occurs for sinks other than {@link ProfileSink#NONE}. The
 * sink directory passed in is treated as the mature target directory (the
 * caller builds {@code .../profile}&lt;seed&gt;_&lt;dim&gt; or the world's
 * {@code subterra-profile}): files land directly as {@code profile.zd} /
 * {@code profile.td}, always overwritten, with deterministic names for easy
 * script consumption.
 * <p>
 * 纯 JDK 的世界档案运行与持久化入口（p.1.8.30 "World Profiler"）。扫描始终经
 * {@link StatsEngine}；仅当落点不是 {@link ProfileSink#NONE} 时才写出文件。传入的输出目录
 * 被视为成熟的目标目录（由调用方构建 {@code .../profile}&lt;seed&gt;_&lt;dim&gt; 或世界的
 * {@code subterra-profile}）：文件直接落在其中，命名为 {@code profile.zd} /
 * {@code profile.td}，总是覆盖，名称确定便于脚本消费。
 */
public final class WorldProfileRunner {

    private WorldProfileRunner() {
    }

    /**
     * Scans the plan over the sampler and writes any requested file output. With
     * a {@link ProfileSink#NONE} sink nothing is ever written. When a non-NONE
     * sink is set the directory (and any missing parents) is created and the
     * report written via {@link #writeFiles(ProfileReport, ProfilePlan, Path)};
     * a failure surfaces as {@link IOException}.
     * <p>
     * 用采样器扫描计划并写出请求的文件输出。落点为 {@link ProfileSink#NONE} 时绝不写盘。当
     * 落点非 NONE 时创建目录（含缺失的父目录）并经
     * {@link #writeFiles(ProfileReport, ProfilePlan, Path)} 写出报告；失败以
     * {@link IOException} 抛出。
     *
     * @param sampler     the world sampler binding.
     * @param plan        the profiling configuration.
     * @param seed        the world seed for metadata and file naming.
     * @param dimension   the dimension id for metadata.
     * @param appVersion  the application version for metadata.
     * @param sinkDir     the destination directory (ignored for NONE sinks).
     * @return the completed report.
     * @throws IOException when the output directory or files cannot be written.
     */
    public static ProfileReport run(WorldSampler sampler, ProfilePlan plan,
                                    long seed, String dimension, String appVersion,
                                    Path sinkDir) throws IOException {
        ProfileReport report = StatsEngine.scan(sampler, plan, seed, dimension, appVersion);
        if (plan.sink() == ProfileSink.NONE || sinkDir == null) {
            return report;
        }
        try {
            Files.createDirectories(sinkDir);
            writeFiles(report, plan, sinkDir);
        } catch (IOException e) {
            throw new IOException("failed to write world profile to " + sinkDir, e);
        }
        return report;
    }

    /**
     * Writes {@code profile.zd} and/or {@code profile.td} into the sink directory
     * according to the plan's formats. The directory is created if needed.
     * <p>
     * 按计划的格式把 {@code profile.zd} 和/或 {@code profile.td} 写入输出目录。目录缺失时
     * 先创建。
     *
     * @param r       the completed report.
     * @param plan    the plan carrying the format list.
     * @param sinkDir the destination directory.
     * @return the list of files actually written.
     * @throws IOException when a file cannot be written.
     */
    public static List<Path> writeFiles(ProfileReport r, ProfilePlan plan, Path sinkDir)
            throws IOException {
        Files.createDirectories(sinkDir);
        List<Path> written = new ArrayList<>();
        for (ProfileFormat f : plan.formats()) {
            switch (f) {
                case ZD -> {
                    Path p = sinkDir.resolve("profile.zd");
                    Files.write(p, ZdWriter.write(r));
                    written.add(p);
                }
                case TD -> {
                    Path p = sinkDir.resolve("profile.td");
                    Files.write(p, TdWriter.write(r).getBytes(StandardCharsets.UTF_8));
                    written.add(p);
                }
                default -> { /* unknown format: skip */ }
            }
        }
        return written;
    }

    /**
     * Builds a compact multi-line console summary of a report. Lines for a
     * disabled category are omitted, so the summary reflects exactly what the
     * report carries.
     * <p>
     * 为一个报告生成紧凑的多行控制台摘要。关闭类别对应的行会被省略，因此摘要精确反映报告
     * 携带的内容。
     *
     * @param r the completed report.
     * @return the summary text.
     */
    public static String summary(ProfileReport r) {
        ProfilePlan plan = r.plan();
        boolean terrainOn = plan.categories().contains(ProfileCategory.TERRAIN);
        boolean blocksOn = plan.categories().contains(ProfileCategory.BLOCKS);
        boolean cavesOn = plan.categories().contains(ProfileCategory.CAVES);
        boolean biomeOn = plan.categories().contains(ProfileCategory.BIOME);
        StringBuilder sb = new StringBuilder();
        sb.append("world profile seed=").append(r.seed())
          .append(" dim=").append(r.dimension())
          .append(" columns=").append(r.columnSamples())
          .append(" sink=").append(plan.sink().name().toLowerCase(java.util.Locale.ROOT));
        if (r.slice() != null) {
            sb.append(" slice=").append(r.slice().census().total());
        }
        sb.append('\n');

        if (terrainOn) {
            HeightStats t = r.terrain();
            long total = t.landColumns() + t.oceanColumns();
            double landPct = total > 0 ? 100.0 * t.landColumns() / total : 0.0;
            sb.append("terrain: land=").append(format1(landPct)).append("%")
              .append(" height mean=").append(format1(t.mean()))
              .append(" range=").append((long) t.min()).append("..").append((long) t.max())
              .append('\n');
        }
        if (blocksOn) {
            sb.append("blocks: total=").append(r.blocks().total());
            List<BlockEntry> top = topBlocks(r, 5);
            if (!top.isEmpty()) {
                sb.append(" top=");
                for (int i = 0; i < top.size(); i++) {
                    if (i > 0) {
                        sb.append(",");
                    }
                    sb.append(top.get(i).id()).append(':').append(top.get(i).count());
                }
            }
            sb.append('\n');
        }
        if (cavesOn) {
            CaveSummary cs = caveSummary(r);
            sb.append("caves: air=").append(format1(cs.airPct)).append("%")
              .append(" surface_openings=").append(r.caves().surfaceOpenings())
              .append('\n');
        }
        if (biomeOn) {
            sb.append("biomes:");
            List<BiomeEntry> top = topBiomes(r, 5);
            for (int i = 0; i < top.size(); i++) {
                if (i > 0) {
                    sb.append(",");
                } else {
                    sb.append(' ');
                }
                sb.append(top.get(i).id()).append(':').append(top.get(i).ratioN())
                  .append('/').append(top.get(i).ratioD());
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    private static List<BlockEntry> topBlocks(ProfileReport r, int n) {
        List<BlockEntry> l = new ArrayList<>(r.blocks().byId());
        l.sort(java.util.Comparator.<BlockEntry>comparingLong(BlockEntry::count).reversed());
        return l.subList(0, Math.min(n, l.size()));
    }

    private static List<BiomeEntry> topBiomes(ProfileReport r, int n) {
        List<BiomeEntry> l = new ArrayList<>(r.biomes().census());
        l.sort(java.util.Comparator.<BiomeEntry>comparingLong(BiomeEntry::ratioN).reversed());
        return l.subList(0, Math.min(n, l.size()));
    }

    private static CaveSummary caveSummary(ProfileReport r) {
        long air = 0;
        long fluid = 0;
        long solid = 0;
        for (CaveBand b : r.caves().bands()) {
            air += b.air();
            fluid += b.fluid();
            solid += b.solid();
        }
        long total = air + fluid + solid;
        double airPct = total > 0 ? 100.0 * air / total : 0.0;
        return new CaveSummary(airPct);
    }

    private record CaveSummary(double airPct) {
    }

    private static String format1(double v) {
        return String.format(java.util.Locale.ROOT, "%.1f", v);
    }
}