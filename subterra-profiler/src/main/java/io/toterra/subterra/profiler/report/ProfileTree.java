package io.toterra.subterra.profiler.report;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import io.toterra.subterra.api.worldgen.profiler.BandBlocks;
import io.toterra.subterra.api.worldgen.profiler.BiomeEntry;
import io.toterra.subterra.api.worldgen.profiler.BiomeStats;
import io.toterra.subterra.api.worldgen.profiler.BlockEntry;
import io.toterra.subterra.api.worldgen.profiler.BlockStats;
import io.toterra.subterra.api.worldgen.profiler.CaveBand;
import io.toterra.subterra.api.worldgen.profiler.CaveStats;
import io.toterra.subterra.api.worldgen.profiler.ClimateHistogram;
import io.toterra.subterra.api.worldgen.profiler.HeightStats;
import io.toterra.subterra.api.worldgen.profiler.PositionRecord;
import io.toterra.subterra.api.worldgen.profiler.ProfileAxis;
import io.toterra.subterra.api.worldgen.profiler.ProfileCategory;
import io.toterra.subterra.api.worldgen.profiler.ProfileFormat;
import io.toterra.subterra.api.worldgen.profiler.ProfilePlan;
import io.toterra.subterra.api.worldgen.profiler.ProfileReport;
import io.toterra.subterra.api.worldgen.profiler.ProfileSink;
import io.toterra.subterra.api.worldgen.profiler.ProfileSlice;
import io.toterra.subterra.api.worldgen.profiler.SliceCensus;
import io.toterra.subterra.api.worldgen.profiler.SlicePlane;
import io.toterra.subterra.api.worldgen.profiler.SliceReport;
import io.toterra.subterra.api.worldgen.profiler.SliceUnit;
import io.toterra.subterra.engine.config.TdTable;
import io.toterra.subterra.engine.config.TdValue;

/**
 * Materializes a {@link ProfileReport} into a single nested {@link TdTable} tree
 * (p.1.8.30 "World Profiler"). The tree is the single source of truth shared by
 * the td and zd writers, so both formats always carry identical content. Key
 * order follows the design document section 8; keys that have no data (disabled
 * categories, an absent slice, or closed planes/positions) are simply omitted.
 * Floating point values are written at full precision via {@code
 * Double.toString}; integer values keep their full 64-bit width.
 * <p>
 * 把 {@link ProfileReport} 物化为一棵单一的嵌套 {@link TdTable} 树（p.1.8.30 "World
 * Profiler"）。这棵树是 td 与 zd 写出共用的单一事实源，因此两种格式内容始终一致。键顺序
 * 遵照设计文档第 8 节；没有数据的键（关闭的类别、缺省的切片或关闭的平面/坐标）会被直接
 * 省略。浮点值经 {@code Double.toString} 以全精度写出；整数值保留完整 64 位宽度。
 */
public final class ProfileTree {

    private ProfileTree() {
    }

    /**
     * Converts the complete report into its td table. The returned tree is
     * detached from the report (all scalars are copied), so later mutation of
     * the report has no effect on the tree.
     * <p>
     * 把完整报告转换为 td 表。返回的树与报告解耦（所有标量均已拷贝），因此后续对报告的
     * 修改不会影响这棵树。
     *
     * @param r the completed report.
     * @return the nested td tree for the report.
     */
    public static TdTable toTd(ProfileReport r) {
        TdTable.Builder root = TdTable.builder();
        root.put("meta", meta(r));
        root.put("plan", plan(r.plan()));
        root.put("column_samples", TdValue.of(r.columnSamples()));
        root.put("terrain", terrain(r.terrain(), r.columnSamples()));
        root.put("blocks", blocks(r.blocks()));
        root.put("caves", caves(r.caves()));
        root.put("biomes", biomes(r.biomes()));
        if (r.slice() != null) {
            root.put("slices", slices(r.slice()));
        }
        return root.build();
    }

    private static TdTable meta(ProfileReport r) {
        TdTable.Builder b = TdTable.builder();
        b.put("seed", TdValue.of(r.seed()));
        b.put("dimension", r.dimension());
        b.put("app", r.appVersion());
        b.put("version", "p.1.8.30");
        return b.build();
    }

    private static TdTable plan(ProfilePlan p) {
        TdTable.Builder b = TdTable.builder();
        b.put("residency", TdValue.of(p.residency()));

        TdTable.Builder center = TdTable.builder();
        center.element(TdValue.of((long) p.window().centerX()));
        center.element(TdValue.of((long) p.window().centerZ()));
        TdTable.Builder window = TdTable.builder();
        window.put("center", center.build());
        window.put("radius", TdValue.of((long) p.window().radiusChunks()));
        b.put("window", window.build());

        TdTable.Builder cats = TdTable.builder();
        for (ProfileCategory c : sorted(p.categories())) {
            cats.element(TdValue.str(category(c)));
        }
        b.put("categories", cats.build());

        TdTable.Builder step = TdTable.builder();
        step.put("xz", TdValue.of((long) p.step().xz()));
        step.put("y", TdValue.of((long) p.step().y()));
        b.put("step", step.build());

        if (p.slice() != null) {
            b.put("slice", sliceBox(p.slice()));
        }

        TdTable.Builder fmts = TdTable.builder();
        for (ProfileFormat f : p.formats()) {
            fmts.element(TdValue.str(format(f)));
        }
        b.put("formats", fmts.build());
        b.put("sink", sink(p.sink()));
        return b.build();
    }

    private static TdTable sliceBox(ProfileSlice s) {
        TdTable.Builder b = TdTable.builder();
        b.put("axis", axis(s.axis()));
        b.put("start", TdValue.of((long) s.start()));
        b.put("end", TdValue.of((long) s.end()));
        b.put("unit", unit(s.unit()));
        b.put("planes", TdValue.of(s.planes()));
        b.put("positions", TdValue.of(s.positions()));
        return b.build();
    }

    private static TdTable terrain(HeightStats t, long sampleCount) {
        TdTable.Builder b = TdTable.builder();
        b.put("sample_count", TdValue.of(sampleCount));
        b.put("min", TdValue.of(t.min()));
        b.put("max", TdValue.of(t.max()));
        b.put("mean", TdValue.of(t.mean()));
        b.put("p5", TdValue.of(t.p5()));
        b.put("p25", TdValue.of(t.p25()));
        b.put("p50", TdValue.of(t.p50()));
        b.put("p75", TdValue.of(t.p75()));
        b.put("p95", TdValue.of(t.p95()));
        b.put("band_height", TdValue.of((long) t.bandHeight()));
        b.put("band_counts", intArray(t.bandCounts()));
        b.put("land_columns", TdValue.of(t.landColumns()));
        b.put("ocean_columns", TdValue.of(t.oceanColumns()));
        return b.build();
    }

    private static TdTable blocks(BlockStats s) {
        TdTable.Builder b = TdTable.builder();
        b.put("total", TdValue.of(s.total()));
        TdTable.Builder byId = TdTable.builder();
        for (BlockEntry e : s.byId()) {
            byId.element(blockEntry(e));
        }
        b.put("by_id", byId.build());
        TdTable.Builder byBand = TdTable.builder();
        for (BandBlocks bb : s.byBand()) {
            TdTable.Builder t = TdTable.builder();
            t.put("y_from", TdValue.of((long) bb.yFrom()));
            t.put("y_to", TdValue.of((long) bb.yTo()));
            TdTable.Builder entries = TdTable.builder();
            for (BlockEntry e : bb.entries()) {
                entries.element(blockEntry(e));
            }
            t.put("entries", entries.build());
            byBand.element(t.build());
        }
        b.put("by_band", byBand.build());
        return b.build();
    }

    private static TdTable blockEntry(BlockEntry e) {
        TdTable.Builder t = TdTable.builder();
        t.put("id", e.id());
        t.put("count", TdValue.of(e.count()));
        return t.build();
    }

    private static TdTable caves(CaveStats s) {
        TdTable.Builder b = TdTable.builder();
        TdTable.Builder bands = TdTable.builder();
        for (CaveBand cb : s.bands()) {
            TdTable.Builder t = TdTable.builder();
            t.put("y_from", TdValue.of((long) cb.yFrom()));
            t.put("y_to", TdValue.of((long) cb.yTo()));
            t.put("air", TdValue.of(cb.air()));
            t.put("fluid", TdValue.of(cb.fluid()));
            t.put("solid", TdValue.of(cb.solid()));
            bands.element(t.build());
        }
        b.put("bands", bands.build());
        b.put("crossings_histogram", longArray(s.crossingsHistogram()));
        b.put("surface_openings", TdValue.of(s.surfaceOpenings()));
        return b.build();
    }

    private static TdTable biomes(BiomeStats s) {
        TdTable.Builder b = TdTable.builder();
        TdTable.Builder census = TdTable.builder();
        for (BiomeEntry e : s.census()) {
            TdTable.Builder t = TdTable.builder();
            t.put("id", e.id());
            t.put("ratio_n", TdValue.of(e.ratioN()));
            t.put("ratio_d", TdValue.of(e.ratioD()));
            census.element(t.build());
        }
        b.put("census", census.build());
        TdTable.Builder climate = TdTable.builder();
        for (ClimateHistogram h : s.climate()) {
            TdTable.Builder t = TdTable.builder();
            t.put("field", h.field());
            t.put("min", TdValue.of(h.min()));
            t.put("max", TdValue.of(h.max()));
            t.put("bucket_count", TdValue.of((long) h.bucketCount()));
            t.put("buckets", intArray(h.buckets()));
            climate.element(t.build());
        }
        b.put("climate", climate.build());
        return b.build();
    }

    private static TdTable slices(SliceReport s) {
        SliceCensus c = s.census();
        TdTable.Builder b = TdTable.builder();
        b.put("axis", axis(s.slice().axis()));
        b.put("start", TdValue.of((long) s.slice().start()));
        b.put("end", TdValue.of((long) s.slice().end()));
        b.put("unit", unit(s.slice().unit()));
        b.put("total", TdValue.of(c.total()));

        TdTable.Builder census = TdTable.builder();
        for (BlockEntry e : c.byId()) {
            census.element(blockEntry(e));
        }
        b.put("census", census.build());

        TdTable.Builder byBand = TdTable.builder();
        for (BandBlocks bb : c.byBand()) {
            TdTable.Builder t = TdTable.builder();
            t.put("y_from", TdValue.of((long) bb.yFrom()));
            t.put("y_to", TdValue.of((long) bb.yTo()));
            TdTable.Builder entries = TdTable.builder();
            for (BlockEntry e : bb.entries()) {
                entries.element(blockEntry(e));
            }
            t.put("entries", entries.build());
            byBand.element(t.build());
        }
        b.put("by_band", byBand.build());

        if (s.slice().planes()) {
            TdTable.Builder planes = TdTable.builder();
            for (SlicePlane p : s.planes()) {
                TdTable.Builder t = TdTable.builder();
                t.put("axis_index", TdValue.of((long) p.axisIndex()));
                t.put("rows", stringArray(p.rows()));
                planes.element(t.build());
            }
            b.put("planes", planes.build());
        }
        if (s.slice().positions()) {
            TdTable.Builder positions = TdTable.builder();
            for (PositionRecord p : s.positions()) {
                TdTable.Builder t = TdTable.builder();
                t.put("x", TdValue.of((long) p.x()));
                t.put("y", TdValue.of((long) p.y()));
                t.put("z", TdValue.of((long) p.z()));
                t.put("block", p.block());
                positions.element(t.build());
            }
            b.put("positions", positions.build());
        }
        return b.build();
    }

    private static TdTable intArray(int[] values) {
        TdTable.Builder b = TdTable.builder();
        for (int v : values) {
            b.element(TdValue.of((long) v));
        }
        return b.build();
    }

    private static TdTable longArray(long[] values) {
        TdTable.Builder b = TdTable.builder();
        for (long v : values) {
            b.element(TdValue.of(v));
        }
        return b.build();
    }

    private static TdTable stringArray(List<String> values) {
        TdTable.Builder b = TdTable.builder();
        for (String v : values) {
            b.element(TdValue.str(v));
        }
        return b.build();
    }

    private static List<ProfileCategory> sorted(Set<ProfileCategory> set) {
        List<ProfileCategory> l = new ArrayList<>(set);
        l.sort(Comparator.comparing(Enum::name));
        return l;
    }

    private static String category(ProfileCategory c) {
        return c.name().toLowerCase(Locale.ROOT);
    }

    private static String format(ProfileFormat f) {
        return f.name().toLowerCase(Locale.ROOT);
    }

    private static String sink(ProfileSink s) {
        return s.name().toLowerCase(Locale.ROOT);
    }

    private static String axis(ProfileAxis a) {
        return a.name().toLowerCase(Locale.ROOT);
    }

    private static String unit(SliceUnit u) {
        return u.name().toLowerCase(Locale.ROOT);
    }
}