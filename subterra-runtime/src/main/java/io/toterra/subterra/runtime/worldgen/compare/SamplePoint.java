package io.toterra.subterra.runtime.worldgen.compare;

/**
 * One fixed sampling point: a block column {@code (x, z)} at altitude {@code y}
 * (p.1.8.17). The compare bridge evaluates every router field at these coordinates
 * on both the vanilla seam and the pure-JDK mirror.
 * <p>
 * 一个固定采样点：列坐标 {@code (x, z)} 与高度 {@code y}（p.1.8.17）。对拍桥在原生接缝与
 * 纯 JDK 镜像两侧，于该坐标处求值每个路由器场。
 *
 * @param x block X (vanilla {@link  net.minecraft.world.level.levelgen.DensityFunction.FunctionContext#blockX()}).
 * @param y block Y (altitude).
 * @param z block Z.
 */
public record SamplePoint(long x, int y, long z) {

    @Override
    public String toString() {
        return "(" + x + "," + y + "," + z + ")";
    }
}