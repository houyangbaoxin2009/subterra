package io.toterra.subterra.api.worldgen.profiler;

/**
 * The horizontal extent of a world profile run (p.1.8.30): a center position and
 * a radius in whole chunks, so a {@code (2*radiusChunks+1)} x
 * {@code (2*radiusChunks+1)} column area around the center is profiled. Pure and
 * deterministic.
 * <p>
 * 一次世界档案运行的水平范围（p.1.8.30）：一个中心位置和以整个区块计的半径，环绕中心
 * 形成 {@code (2*radiusChunks+1)} x {@code (2*radiusChunks+1)} 的柱状区域被采集。
 * 纯且确定。
 */
public record ProfileWindow(int centerX, int centerZ, int radiusChunks) {

    /** Compact constructor enforcing a non-negative radius. */
    public ProfileWindow {
        if (radiusChunks < 0) {
            throw new IllegalArgumentException("radius must be >= 0: " + radiusChunks);
        }
    }
}