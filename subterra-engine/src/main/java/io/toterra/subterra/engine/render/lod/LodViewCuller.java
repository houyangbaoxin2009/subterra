package io.toterra.subterra.engine.render.lod;

import java.util.Objects;

/**
 * Deterministic view culler (p.2.28.3, clean-room self-developed): frustum culling in
 * block/chunk space via an all-integer fixed-point {@link ViewFrustum} (Q8.24 plane
 * coefficients, i.e. values scaled by {@code 1 << 24}), plus facing-bit based backface /
 * horizontal-side culling for merged {@link LodQuad}s. Everything is integer or fixed-point
 * arithmetic over fixed view parameters in a fixed traversal order — same input, same output,
 * no timing, no randomness.
 *
 * <p><b>Convention.</b> AABB vs frustum is decided by the standard conservative test: for each
 * of the six outward planes, take the AABB vertex that is furthest <em>toward</em> that plane's
 * outward normal (the "p-vertex"); if that vertex is strictly beyond the plane
 * ({@code n·p + d > 0}) the AABB is fully outside that plane and is culled; otherwise the AABB
 * intersects or is inside and is kept. Each plane's signed value is a pure Q8.24 {@code long} —
 * {@code n_i·coord_i} where coefficient scales by {@code 1<<24} and block coordinates are
 * {@code int} — so comparison is exact integer, no float. See {@link ViewFrustum} for how the
 * six planes are derived from the fixed view parameters.
 *
 * <p>The facing bits are interpreted under the fixed convention declared by the
 * {@code FACE_*} constants ({@code UP} bit0, {@code NORTH} bit1, {@code SOUTH} bit2,
 * {@code WEST} bit3). {@link #cullByFacingBits(int, long, long)} decides deterministically
 * whether a surface with those facing bits is back-facing (and therefore droppable) for a given
 * viewer direction. A surface whose outward normal points away from the viewer is culled.
 *
 * <p>确定性视景剔除器（p.2.28.3，clean-room 自研）：用全整数定点 {@link ViewFrustum}（Q8.24 平面系数，
 * 即以 {@code 1 << 24} 缩放）在方块/区块空间做视锥剔除，外加按朝向位对合并 {@link LodQuad} 做背面/水平侧面
 * 剔除。整体为固定视参数之下的整数定点运算 + 固定遍历序——同输入同输出，无时序、无随机。
 *
 * <p><b>约定。</b>AABB 对视锥的判定用标准保守测试：对六个朝外平面之一，取 AABB 中最「朝」该平面外法向的顶点
 * （p-vertex）；若该顶点严格越过平面（{@code n·p + d > 0}）则 AABB 完全在该平面另一侧而被剔除；否则判定为
 * 相交或在内部而保留。每个平面的带符号值是纯 Q8.24 {@code long}——{@code n_i·coord_i}{@code 1<<24} 缩放、
 * 区块坐标为 {@code int}——因此比较为精确整数、无浮点。六个平面如何由固定视参数派生出，见 {@link ViewFrustum}。
 *
 * <p>朝向位按 {@code FACE_*} 常量声明的固定约定解释（{@code UP} bit0、{@code NORTH} bit1、
 * {@code SOUTH} bit2、{@code WEST} bit3）。{@link #cullByFacingBits(int, long, long)} 对给定视方向
 * 确定性判定带有该朝向位的表面是否背向（从而可丢弃）。外法向背离观察者的表面被剔除。
 */
public final class LodViewCuller {

    /** Facing bit 0: top (+Y) horizontal surface. / 朝向位 0：顶部（+Y）水平面。 */
    public static final int FACE_UP = 0b0001;
    /** Facing bit 1: north (−Z) side. / 朝向位 1：北向（−Z）侧面。 */
    public static final int FACE_NORTH = 0b0010;
    /** Facing bit 2: south (+Z) side. / 朝向位 2：南向（+Z）侧面。 */
    public static final int FACE_SOUTH = 0b0100;
    /** Facing bit 3: west (−X) side. / 朝向位 3：西向（−X）侧面。 */
    public static final int FACE_WEST = 0b1000;

    private static final int BLOCKS_PER_CHUNK = 16;
    /** World height, for default section bounds. / 世界高度，用于缺省区块边界。 */
    private static final int WORLD_HEIGHT = 256;
    /** Fixed-point fraction bits (Q8.24). / 定点小数位数（Q8.24）。 */
    private static final int FRAC = 24;
    /** Q8.24 {@code 1.0}. / Q8.24 的 {@code 1.0}。 */
    static final long ONE = 1L << FRAC;

    private LodViewCuller() {
    }

    /**
     * Axis-aligned box in block space ({@code minX ≤ maxX}, etc.). / 方块空间轴对齐盒
     * （{@code minX ≤ maxX} 等）。
     */
    public record Bounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {

        /**
         * @throws IllegalArgumentException if any min exceeds its max.
         */
        public Bounds {
            if (minX > maxX || minY > maxY || minZ > maxZ) {
                throw new IllegalArgumentException(
                        "bounds min must not exceed max: (" + minX + "," + minY + "," + minZ
                                + ")..(" + maxX + "," + maxY + "," + maxZ + ")");
            }
        }
    }

    /**
     * An all-integer fixed-point (Q8.24) view frustum: the six outward plane coefficients
     * ({@code nx, ny, nz, d}) scaled by {@code 1 << 24}, derived once from invariant integer
     * view parameters (eye, forward, half-tangents) via cheap cross products. Immutable.
     *
     * <p>Derivation: with {@code F = forward}, {@code U = (0,1,0)} and
     * {@code R = cross(U, F)} (camera right), the side boundaries are built with the fixed
     * half-tangents {@code tanH}/{@code tanV} (Q8.24): {@code BR = F*ONE + R*tanH},
     * {@code BL = F*ONE − R*tanH}, {@code BT = F*ONE + U*tanV}, {@code BB = F*ONE − U*tanV}.
     * Outward plane normals (all Q8.24) are {@code NR=cross(U,BR)}, {@code NL=cross(U,BL)},
     * {@code NT=cross(BT,R)}, {@code NB=cross(R,BB)}, {@code NNear=−F}, {@code NFar=F}, with
     * {@code d = −N·eye} so every plane passes through the eye. The Q8.24 scaling is uniform
     * per plane, so only the sign of {@code n·x + d} matters and non-unit integer cross
     * products are still exact.
     *
     * <p>全整数定点（Q8.24）视图锥：六个朝外平面系数（{@code nx, ny, nz, d}）以 {@code 1 << 24} 缩放，
     * 由不变整数视参数（眼位、forward、半切值）经廉价叉积一次性派生。不可变。
     *
     * <p>派生：设 {@code F = forward}、{@code U = (0,1,0)}、{@code R = cross(U, F)}（相机右向），以固定
     * 半切值 {@code tanH}/{@code tanV}（Q8.24）构造侧边界：{@code BR = F*ONE + R*tanH}、
     * {@code BL = F*ONE − R*tanH}、{@code BT = F*ONE + U*tanV}、{@code BB = F*ONE − U*tanV}。
     * 朝外平面法向（均 Q8.24）为 {@code NR=cross(U,BR)}、{@code NL=cross(U,BL)}、{@code NT=cross(BT,R)}、
     * {@code NB=cross(R,BB)}、{@code NNear=−F}、{@code NFar=F}，且 {@code d = −N·eye} 使每平面过眼位。
     * Q8.24 缩放对每平面一致，故只需 {@code n·x + d} 的符号、非单位整数叉积仍精确。
     */
    public static final class ViewFrustum {

        private static final int PLANES = 6;
        private final long[] nx = new long[PLANES];
        private final long[] ny = new long[PLANES];
        private final long[] nz = new long[PLANES];
        private final long[] d = new long[PLANES];

        private ViewFrustum(long[] nx, long[] ny, long[] nz, long[] dvals) {
            System.arraycopy(nx, 0, this.nx, 0, PLANES);
            System.arraycopy(ny, 0, this.ny, 0, PLANES);
            System.arraycopy(nz, 0, this.nz, 0, PLANES);
            System.arraycopy(dvals, 0, this.d, 0, PLANES);
        }

        /**
         * Builds a frustum from fixed integer view parameters.
         *
         * @param eyeX, eyeY, eyeZ eye in block space.
         * @param fx, fy, fz       forward direction (small integers, need not be normalized).
         * @param halfTanH, halfTanV horizontal / vertical half-angle tangents in Q8.24 (e.g.
         *     {@code ONE} for 90°). Must be {@code > 0}.
         * @param nearBlocks         near-plane distance in blocks ({@code >= 0}).
         * @param farBlocks          far-plane distance in blocks ({@code > nearBlocks}).
         * @throws IllegalArgumentException if a half-tangent is {@code <= 0}, distances are
         *     invalid, or the forward vector is all zero.
         */
        public static ViewFrustum of(long eyeX, long eyeY, long eyeZ,
                                     long fx, long fy, long fz,
                                     long halfTanH, long halfTanV,
                                     long nearBlocks, long farBlocks) {
            if (halfTanH <= 0 || halfTanV <= 0) {
                throw new IllegalArgumentException(
                        "halfTangents must be > 0: H=" + halfTanH + " V=" + halfTanV);
            }
            if (nearBlocks < 0 || farBlocks <= nearBlocks) {
                throw new IllegalArgumentException(
                        "distances must satisfy 0 <= near < far: near=" + nearBlocks
                                + " far=" + farBlocks);
            }
            if (fx == 0 && fy == 0 && fz == 0) {
                throw new IllegalArgumentException("forward vector must not be zero");
            }
            // camera right R = cross(U, F) with U=(0,1,0):
            // U × F = (Uy*Fz - Uz*Fy, Uz*Fx - Ux*Fz, Ux*Fy - Uy*Fx), U=(0,1,0)
            //        = ( 1*fz - 0*fy, 0*fx - 0*fz, 0*fy - 1*fx ) = ( fz, 0, -fx )
            long rx = fz;
            long rz = -fx;
            long[] bx = new long[PLANES], by = new long[PLANES], bz = new long[PLANES];
            // boundaries (Q8.24): right/left use R, top/bottom use U=(0,1,0)
            long[] BX = new long[4], BY = new long[4], BZ = new long[4];
            BX[0] = fx * ONE + rx * halfTanH;      // right
            BY[0] = fy * ONE;
            BZ[0] = fz * ONE + rz * halfTanH;
            BX[1] = fx * ONE - rx * halfTanH;      // left
            BY[1] = fy * ONE;
            BZ[1] = fz * ONE - rz * halfTanH;
            BX[2] = fx * ONE;                      // top
            BY[2] = fy * ONE + halfTanV;
            BZ[2] = fz * ONE;
            BX[3] = fx * ONE;                      // bottom
            BY[3] = fy * ONE - halfTanV;
            BZ[3] = fz * ONE;
            // right: outward +x -> cross(U, BR) = (Bz, 0, -Bx)
            bx[0] = BZ[0];
            by[0] = 0;
            bz[0] = -BX[0];
            // left: outward -x -> -cross(U, BL) = (-Bz, 0, +Bx)
            bx[1] = -BZ[1];
            by[1] = 0;
            bz[1] = BX[1];
            // top: outward +y -> cross(BT, R), R=(rx,0,rz)
            bx[2] = BY[2] * rz;
            by[2] = BZ[2] * rx - BX[2] * rz;
            bz[2] = -BY[2] * rx;
            // bottom: outward -y -> cross(R, BB)
            bx[3] = -rz * BY[3];
            by[3] = rz * BX[3] - rx * BZ[3];
            bz[3] = rx * BY[3];
            // near = -F (outward toward viewer), far = +F (outward forward), offset by distances
            bx[4] = -fx * ONE;
            by[4] = -fy * ONE;
            bz[4] = -fz * ONE;
            bx[5] = fx * ONE;
            by[5] = fy * ONE;
            bz[5] = fz * ONE;
            long[] dx_ = new long[PLANES];
            for (int i = 0; i < PLANES; i++) {
                // plane passes through p0 = eye for side planes; near/far pass through
                // p0 = eye + dist * F (block space).
                long dist = i == 4 ? nearBlocks : i == 5 ? farBlocks : 0;
                long px = eyeX + dist * fx;
                long py = eyeY + dist * fy;
                long pz = eyeZ + dist * fz;
                dx_[i] = -(bx[i] * px + by[i] * py + bz[i] * pz);
            }
            return new ViewFrustum(bx, by, bz, dx_);
        }

        /** Number of planes (6). / 平面数（6）。 */
        public int planeCount() {
            return PLANES;
        }
    }

    /**
     * Determines whether the given AABB is (conservatively) inside or intersecting the
     * frustum. Returns {@code true} to keep, {@code false} to cull (fully outside at least one
     * plane). The 6 planes are tested in fixed order; the p-vertex test is exact integer.
     * / 判定给定 AABB 是否（保守地）在视锥内或与视锥相交。返回 {@code true} 表示保留，{@code false}
     * 表示剔除（在至少一个平面之外）。6 个平面按固定序测试；p-vertex 测试为精确整数。
     */
    public static boolean passesFrustum(ViewFrustum f, Bounds b) {
        Objects.requireNonNull(f, "frustum must not be null");
        Objects.requireNonNull(b, "bounds must not be null");
        for (int i = 0; i < f.planeCount(); i++) {
            // select the AABB vertex furthest toward the outward normal (p-vertex)
            long px = f.nx[i] > 0 ? b.maxX() : b.minX();
            long py = f.ny[i] > 0 ? b.maxY() : b.minY();
            long pz = f.nz[i] > 0 ? b.maxZ() : b.minZ();
            if (f.nx[i] * px + f.ny[i] * py + f.nz[i] * pz + f.d[i] > 0) {
                return false; // fully outside this plane
            }
        }
        return true;
    }

    /**
     * Whether a surface with the given facing bits is back-facing (hence cullable) given the
     * viewer's horizontal offset {@code (viewerDX, viewerDZ)} in blocks from the surface. A
     * horizontal side is culled when the viewer lies on the opposite side of it; the UP face is
     * never culled by this horizontal-only test. Deterministic; {@code (0,0)} treats the viewer
     * as on-surface and nothing is culled.
     * / 给定朝向位之表面，在观察者相对表面的水平偏移 {@code (viewerDX, viewerDZ)}（方块）下，是否背面
     * （即可剔除）。水平侧面在观察者居于其另一侧时被剔除；{@link #FACE_UP} 面不受此纯水平测试影响。确定性；
     * {@code (0,0)} 视观察者在面上、不剔除任何面。
     *
     * @param facingBits the {@link LodQuad} {@code facingBits} mask (see {@code FACE_*}).
     * @param viewerDX   viewer block X − surface block X.
     * @param viewerDZ   viewer block Z − surface block Z.
     * @return {@code true} to cull (back-facing), {@code false} to keep.
     */
    public static boolean cullByFacingBits(int facingBits, long viewerDX, long viewerDZ) {
        int bits = facingBits & 0x0F;
        if (bits == 0) {
            return false;
        }
        boolean horizontalVisibleFound = false;
        boolean horizontalCulled = false;
        if ((bits & FACE_NORTH) != 0) { // outward -Z, visible when viewer is north (dZ < 0)
            horizontalVisibleFound = true;
            horizontalCulled |= viewerDZ >= 0;
        }
        if ((bits & FACE_SOUTH) != 0) { // outward +Z, visible when viewer is south (dZ > 0)
            horizontalVisibleFound = true;
            horizontalCulled |= viewerDZ <= 0;
        }
        if ((bits & FACE_WEST) != 0) { // outward -X, visible when viewer is west (dX < 0)
            horizontalVisibleFound = true;
            horizontalCulled |= viewerDX >= 0;
        }
        boolean upPresent = (bits & FACE_UP) != 0;
        if (upPresent) {
            // top face is kept (rendered from above); it never contributes a cull
            return false;
        }
        // no UP: cull iff there is at least one horizontal face and every present face is
        // back-facing from this viewer.
        return horizontalVisibleFound && horizontalCulled;
    }

    /**
     * Default block-space {@link Bounds} for a LOD section at the given chunk cell: the
     * section spans {@code blockSpan = 16 * cellChunkSide} blocks starting at its section
     * origin {@code secOriginX/secOriginZ} blocks, over the full world height. See
     * {@link LodSection#originBlockX()} / {@link LodLevel#blockSpan()}.
     * / 指定区块单元上 LOD 区块的缺省方块空间 {@link Bounds}：从区块原点
     * {@code secOriginX/secOriginZ}（方块）起、每边 {@code blockSpan = 16 * cellChunkSide} 方块、
     * 贯穿全世界高度。参见 {@link LodSection#originBlockX()} / {@link LodLevel#blockSpan()}。
     */
    public static Bounds sectionBounds(int secOriginBlockX, int secOriginBlockZ, int blockSpan) {
        return new Bounds(secOriginBlockX, 0, secOriginBlockZ,
                secOriginBlockX + blockSpan, WORLD_HEIGHT, secOriginBlockZ + blockSpan);
    }

    /** Same as {@link #sectionBounds(int, int, int)} but for a chunk-level section origin. /
     *  同 {@link #sectionBounds(int, int, int)}，但用于区块级的区块原点。 */
    public static Bounds sectionBoundsFromChunk(int chunkX, int chunkZ, int chunkSide) {
        int span = BLOCKS_PER_CHUNK * chunkSide;
        return sectionBounds(chunkX * BLOCKS_PER_CHUNK, chunkZ * BLOCKS_PER_CHUNK, span);
    }
}