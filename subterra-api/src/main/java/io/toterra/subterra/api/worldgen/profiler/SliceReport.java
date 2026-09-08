package io.toterra.subterra.api.worldgen.profiler;

/**
 * The complete output of a {@link ProfileSlice} (p.1.8.30): the defining box,
 * its block census, optionally rendered planes and optionally probed positions.
 * Immutable; lists are copied on construction. Pure data.
 * <p>
 * {@link ProfileSlice} 的完整输出（p.1.8.30）：定义盒、方块普查、可选的渲染平面与可选
 * 的被采样位置。不可变；构造时列表被拷贝。纯数据。
 */
public record SliceReport(ProfileSlice slice, SliceCensus census,
                          java.util.List<SlicePlane> planes,
                          java.util.List<PositionRecord> positions) {

    /** Compact constructor copying both lists to immutable snapshots. */
    public SliceReport {
        planes = java.util.List.copyOf(planes);
        positions = java.util.List.copyOf(positions);
    }
}