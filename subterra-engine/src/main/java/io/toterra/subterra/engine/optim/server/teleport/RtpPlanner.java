package io.toterra.subterra.engine.optim.server.teleport;

import io.toterra.subterra.engine.pcg.PcgSource;

/**
 * p.2.31.1 StellarRTP clean-room 核心：随机传送目标规划（纯 JDK、确定性）。
 * GPLv3 上游（Stellar 随机传送）仅思想参考、零代码包含：圆形环带随机取点 +
 * 表面安全高度回调裁决 + 尝试次数预算，全部由共享种子确定性派生（{@link PcgSource}
 * fork 链），同输入同结果；非法参数确定性拒绝。
 *
 * <p>The clean-room StellarRTP core (p.2.31.1): random-teleport target planning
 * (pure JDK, deterministic). The GPLv3 upstream is reference-of-ideas only, zero
 * code inclusion: annulus sampling + a caller-supplied surface-safety callback +
 * an attempt budget, all derived deterministically from the shared seed
 * ({@link PcgSource} fork chain); identical inputs give identical results and
 * illegal parameters are rejected deterministically.
 */
public final class RtpPlanner {

    /** Pcg 派生盐前缀（fork 链与中心坐标拼接）。 / The Pcg fork salt prefix (joined with the center). */
    public static final String SALT = "subterra.rtp";

    /** 规划参数（构造期确定性校验）。 / Planning parameters (validated at construction). */
    public record Params(int minRadius, int maxRadius, int attempts, int minY, int maxY, int yOffset) {
        public Params {
            if (minRadius < 0) {
                throw new IllegalArgumentException("min_radius must be >= 0 (got " + minRadius + ")");
            }
            if (maxRadius < minRadius) {
                throw new IllegalArgumentException("max_radius must be >= min_radius (got " + maxRadius + " < " + minRadius + ")");
            }
            if (attempts <= 0) {
                throw new IllegalArgumentException("attempts must be > 0 (got " + attempts + ")");
            }
            if (minY > maxY) {
                throw new IllegalArgumentException("min_y must be <= max_y (got " + minY + " > " + maxY + ")");
            }
            if (yOffset < 0 || yOffset > 32) {
                throw new IllegalArgumentException("y_offset must be within [0, 32] (got " + yOffset + ")");
            }
        }

        /** 规范渲染（字典序字段、无时间戳）。 / The canonical render (ordered fields, no timestamps). */
        public String render() {
            return "min_radius=" + minRadius + " max_radius=" + maxRadius + " attempts=" + attempts
                    + " min_y=" + minY + " max_y=" + maxY + " y_offset=" + yOffset;
        }
    }

    /** 规划结果：found=false 表示预算内全部拒绝（确定性穷尽）。 / The outcome: found=false means the budget was deterministically exhausted. */
    public record Target(boolean found, int x, int y, int z, int attempts, String render) {
    }

    /** 表面高度回调（运行期由宿主提供，如 heightmap 查询）。 / The surface-height callback (host-supplied, e.g. a heightmap query). */
    public interface SurfaceFn {
        int surfaceY(int x, int z);
    }

    private RtpPlanner() {
    }

    /**
     * 确定性规划：以 {@code PcgSource(worldSeed).fork(SALT + ":" + centerX + ":" + centerZ)}
     * 派生序列，逐尝试在 [{@code minRadius}, {@code maxRadius}] 环带上取点，经
     * {@code surface} 裁决落在 {@code [minY, maxY]} 即命中；预算穷尽则确定性返回
     * {@code found=false}。同输入同结果。
     *
     * <p>Plans deterministically: the {@code PcgSource(worldSeed).fork(SALT + ":" +
     * centerX + ":" + centerZ)} chain drives per-attempt annulus sampling in
     * {@code [minRadius, maxRadius]}; the first point whose surface height falls in
     * {@code [minY, maxY]} wins; an exhausted budget deterministically returns
     * {@code found=false}. Identical inputs give identical results.
     */
    public static Target locate(long worldSeed, int centerX, int centerZ, Params params, SurfaceFn surface) {
        PcgSource rng = new PcgSource(worldSeed).fork(SALT + ":" + centerX + ":" + centerZ);
        for (int attempt = 1; attempt <= params.attempts(); attempt++) {
            double angle = rng.nextDouble() * StrictMath.PI * 2.0;
            double radius = params.minRadius()
                    + rng.nextDouble() * (params.maxRadius() - params.minRadius());
            int x = centerX + (int) StrictMath.round(StrictMath.cos(angle) * radius);
            int z = centerZ + (int) StrictMath.round(StrictMath.sin(angle) * radius);
            int y = surface.surfaceY(x, z);
            if (y >= params.minY() && y <= params.maxY()) {
                int ty = y + params.yOffset();
                return new Target(true, x, ty, z, attempt,
                        "rtp target x=" + x + " y=" + ty + " z=" + z + " attempts=" + attempt);
            }
        }
        return new Target(false, 0, Integer.MIN_VALUE, 0, params.attempts(),
                "rtp exhausted attempts=" + params.attempts());
    }
}
