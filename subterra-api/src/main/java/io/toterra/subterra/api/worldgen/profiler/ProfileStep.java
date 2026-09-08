package io.toterra.subterra.api.worldgen.profiler;

/**
 * The sampling stride of a world profile run (p.1.8.30): how densely the column
 * grid (horizontally, {@code xz}) and each vertical column ({@code y}) are
 * probed. Must be at least 1 in both dimensions. Pure and deterministic.
 * <p>
 * 一次世界档案运行的采样步长（p.1.8.30）：水平方向上（{@code xz}）柱状网格的密度、
 * 以及每个竖直柱（{@code y}）的密度。两个维度都至少为 1。纯且确定。
 */
public record ProfileStep(int xz, int y) {

    /** Compact constructor enforcing strict positives. */
    public ProfileStep {
        if (xz < 1 || y < 1) {
            throw new IllegalArgumentException("step must be >= 1: xz=" + xz + ", y=" + y);
        }
    }
}