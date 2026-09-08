package io.toterra.subterra.profiler.core;

import java.util.ArrayList;
import java.util.List;

import io.toterra.subterra.api.worldgen.profiler.BandBlocks;
import io.toterra.subterra.api.worldgen.profiler.BlockEntry;
import io.toterra.subterra.api.worldgen.profiler.PositionRecord;
import io.toterra.subterra.api.worldgen.profiler.ProfileAxis;
import io.toterra.subterra.api.worldgen.profiler.ProfileSlice;
import io.toterra.subterra.api.worldgen.profiler.SliceCensus;
import io.toterra.subterra.api.worldgen.profiler.SlicePlane;
import io.toterra.subterra.api.worldgen.profiler.SliceReport;
import io.toterra.subterra.api.worldgen.profiler.WorldSampler;
import io.toterra.subterra.profiler.core.ProfileRegion.Box;

/**
 * Captures an axial {@link ProfileSlice} as a full-density block scan
 * (p.1.8.30): always the slice census, plus optionally rendered planes and
 * probed positions. The scan follows the loop order {@code x} outer, {@code y}
 * middle, {@code z} inner over the slice box, so output order is deterministic.
 * Pure JDK; no Minecraft runtime.
 * <p>
 * 以全密度方块扫描采集轴向 {@link ProfileSlice}（p.1.8.30）：恒有切片普查，并可按开关
 * 附带渲染平面与被探针位置。扫描沿切片盒按 {@code x} 外、{@code y} 中、{@code z} 内
 * 的循环次序执行，故输出顺序确定。纯 JDK；不依赖 Minecraft 运行时。
 */
public final class SliceCollector {

    private SliceCollector() {
    }

    /**
     * Collects one {@link SliceReport} for a slice, scanning the whole slice
     * box at full density. The box is derived via {@link ProfileRegion#sliceBox};
     * per-band tallies are clamped against the box's own vertical extent. When
     * plane capture is on, each plane freezes one coordinate along the slice
     * axis (the {@link SlicePlane#axisIndex}) and lays out the remaining two
     * axes as a row-per-first-axis, comma-joined-column matrix:
     *
     * <pre>
     *   axis X : plane = x, row = y, columns = z
     *   axis Y : plane = y, row = x, columns = z
     *   axis Z : plane = z, row = x, columns = y
     * </pre>
     *
     * Positions, when enabled, are appended in scan order {@code x-y-z}. The
     * census is always produced.
     * <p>
     * 为一个切片采集 {@link SliceReport}，以全密度扫描整个切片盒。盒由 {@link
     * ProfileRegion#sliceBox} 推导；按带计数相对盒自身竖直范围钳制。当平面采集开启时，
     * 每个平面冻结切片轴上的一个坐标（即 {@link SlicePlane#axisIndex}），并把剩余两轴
     * 排布为"每行一第一轴、逗号连接列"的矩阵（见上）。开启时位置按 {@code x-y-z} 扫描
     * 序追加。普查恒有。
     *
     * @param s     the world sampler binding.
     * @param slice the slice definition.
     * @param yFrom the low end of the vertical full range.
     * @param yTo   the exclusive high end of the vertical full range.
     * @param zFrom the low end of the horizontal full range.
     * @param zTo   the exclusive high end of the horizontal full range.
     * @return the completed slice report.
     */
    public static SliceReport collect(WorldSampler s, ProfileSlice slice,
                                      int yFrom, int yTo, int zFrom, int zTo) {
        Box box = ProfileRegion.sliceBox(slice, yFrom, yTo, zFrom, zTo);
        int xFrom = box.xFrom();
        int xTo = box.xTo();
        int ybFrom = box.yFrom();
        int ybTo = box.yTo();
        int zFromB = box.zFrom();
        int zToB = box.zTo();
        int bandCount = StatsEngine.bandCount(ybFrom, ybTo);

        java.util.HashMap<String, Long> byId = new java.util.HashMap<>();
        java.util.HashMap<Integer, java.util.HashMap<String, Long>> byBand =
            new java.util.HashMap<>();
        long total = 0;
        List<PositionRecord> positions = slice.positions() ? new ArrayList<>() : List.of();

        for (int x = xFrom; x < xTo; x++) {
            for (int y = ybFrom; y < ybTo; y++) {
                int bi = StatsEngine.clamp((y - ybFrom) / 32, 0, bandCount - 1);
                for (int z = zFromB; z < zToB; z++) {
                    String b = s.block(x, y, z);
                    total++;
                    byId.merge(b, 1L, Long::sum);
                    byBand.computeIfAbsent(bi, k -> new java.util.HashMap<>())
                        .merge(b, 1L, Long::sum);
                    if (slice.positions()) {
                        positions.add(new PositionRecord(x, y, z, b));
                    }
                }
            }
        }

        List<BlockEntry> byIdSorted = StatsEngine.sortedEntries(byId);
        List<BandBlocks> byBandSorted = new ArrayList<>();
        for (int k : StatsEngine.sortedKeys(byBand)) {
            byBandSorted.add(new BandBlocks(ybFrom + 32 * k,
                Math.min(ybFrom + 32 * (k + 1), ybTo),
                StatsEngine.sortedEntries(byBand.get(k))));
        }
        SliceCensus census = new SliceCensus(byIdSorted, byBandSorted, total);

        List<SlicePlane> planes = slice.planes()
            ? buildPlanes(s, slice.axis(), box) : List.of();
        return new SliceReport(slice, census, planes, positions);
    }

    private static List<SlicePlane> buildPlanes(WorldSampler s, ProfileAxis axis, Box box) {
        int xFrom = box.xFrom();
        int xTo = box.xTo();
        int yFrom = box.yFrom();
        int yTo = box.yTo();
        int zFrom = box.zFrom();
        int zTo = box.zTo();
        List<SlicePlane> out = new ArrayList<>();
        switch (axis) {
            case X -> {
                for (int x = xFrom; x < xTo; x++) {
                    List<String> rows = new ArrayList<>();
                    for (int y = yFrom; y < yTo; y++) {
                        rows.add(rowOverZ(s, x, y, zFrom, zTo));
                    }
                    out.add(new SlicePlane(x, rows));
                }
            }
            case Y -> {
                for (int y = yFrom; y < yTo; y++) {
                    List<String> rows = new ArrayList<>();
                    for (int x = xFrom; x < xTo; x++) {
                        rows.add(rowOverZ(s, x, y, zFrom, zTo));
                    }
                    out.add(new SlicePlane(y, rows));
                }
            }
            case Z -> {
                for (int z = zFrom; z < zTo; z++) {
                    List<String> rows = new ArrayList<>();
                    for (int x = xFrom; x < xTo; x++) {
                        rows.add(rowOverY(s, x, z, yFrom, yTo));
                    }
                    out.add(new SlicePlane(z, rows));
                }
            }
        }
        return out;
    }

    private static String rowOverZ(WorldSampler s, int x, int y, int zFrom, int zTo) {
        StringBuilder sb = new StringBuilder();
        for (int z = zFrom; z < zTo; z++) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(s.block(x, y, z));
        }
        return sb.toString();
    }

    private static String rowOverY(WorldSampler s, int x, int z, int yFrom, int yTo) {
        StringBuilder sb = new StringBuilder();
        for (int y = yFrom; y < yTo; y++) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(s.block(x, y, z));
        }
        return sb.toString();
    }
}