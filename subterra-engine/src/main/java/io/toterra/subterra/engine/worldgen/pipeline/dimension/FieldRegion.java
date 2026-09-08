package io.toterra.subterra.engine.worldgen.pipeline.dimension;

/**
 * An immutable value object describing a rectangular field buffer in block space
 * (Plan A, Step 5): an axis-aligned box
 * {@code [originX, originX+sizeX) × [originY, originY+sizeY) × [originZ,
 * originZ+sizeZ)} holding exactly one scalar sample per cell in
 * {@link #data()}. The samples are laid out x-fastest (x-major, then y, then z):
 * linear {@code index = lx + ly*sizeX + lz*sizeX*sizeY}, where {@code lx/y/z}
 * are the coordinates offset from the origin.
 * <p>
 * The region is the buffer a {@link ModelFieldGenerator#fill(FieldRegion)}
 * writes its output into, so the {@link #data()} array is the generator's
 * backing buffer; treat it as fully populated and then immutable once {@code
 * fill} returns. {@link #index(int, int, int)} / {@link #contains(int, int, int)}
 * / {@link #sample(double, double, double)} bound-check and reject out-of-box
 * queries via {@link IllegalArgumentException}. Construction and sampling are
 * deterministic and allocation-free on the sampling path.
 * <p>
 * 描述方块空间中一块矩形场缓冲的不可变值对象（Plan A，第 5 步）：一个轴对齐盒子
 * {@code [originX, originX+sizeX) × [originY, originY+sizeY) × [originZ,
 * originZ+sizeZ)}，每格在 {@link #data()} 中恰好保存一个标量样本。样本以 x 最快
 * 的顺序布局（x-major，再 y、z）：线性 {@code index = lx + ly*sizeX +
 * lz*sizeX*sizeY}，其中 {@code lx/y/z} 是从原点偏移后的坐标。
 * <p>
 * 该区域是 {@link ModelFieldGenerator#fill(FieldRegion)} 写入输出的缓冲，因此
 * {@link #data()} 数组是生成器的后备缓冲；将其视为在 {@code fill} 返回后已完整填充
 * 且不可变。{@link #index(int, int, int)} / {@link #contains(int, int, int)} /
 * {@link #sample(double, double, double)} 均做越界检查，并经
 * {@link IllegalArgumentException} 拒绝盒外查询。构造与采样确定，采样路径无分配。
 *
 * @param originX the lowest block x (inclusive)
 * @param originY the lowest block y (inclusive)
 * @param originZ the lowest block z (inclusive)
 * @param sizeX   the x extent in blocks (must be &gt; 0)
 * @param sizeY   the y extent in blocks (must be &gt; 0)
 * @param sizeZ   the z extent in blocks (must be &gt; 0)
 * @param data    the cell samples, {@code length == sizeX*sizeY*sizeZ}
 * @throws IllegalArgumentException if any size is &lt;= 0, {@code data} is null,
 *         or {@code data.length != sizeX*sizeY*sizeZ}
 */
public final class FieldRegion {

    private final int originX;
    private final int originY;
    private final int originZ;
    private final int sizeX;
    private final int sizeY;
    private final int sizeZ;
    private final double[] data;

    /**
     * Full-arg constructor validating the region invariant via
     * IllegalArgumentException: positive {@code sizeX/sizeY/sizeZ}, non-null
     * {@code data}, and {@code data.length == sizeX*sizeY*sizeZ}.
     */
    public FieldRegion(int originX, int originY, int originZ,
                       int sizeX, int sizeY, int sizeZ, double[] data) {
        if (sizeX <= 0) {
            throw new IllegalArgumentException("sizeX must be > 0: " + sizeX);
        }
        if (sizeY <= 0) {
            throw new IllegalArgumentException("sizeY must be > 0: " + sizeY);
        }
        if (sizeZ <= 0) {
            throw new IllegalArgumentException("sizeZ must be > 0: " + sizeZ);
        }
        if (data == null) {
            throw new IllegalArgumentException("data must not be null");
        }
        long expect = (long) sizeX * sizeY * sizeZ;
        if (data.length != expect) {
            throw new IllegalArgumentException(
                    "data.length must equal sizeX*sizeY*sizeZ (" + expect + "): " + data.length);
        }
        this.originX = originX;
        this.originY = originY;
        this.originZ = originZ;
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;
        this.data = data;
    }

    /** The lowest block x (inclusive). */
    public int originX() {
        return originX;
    }

    /** The lowest block y (inclusive). */
    public int originY() {
        return originY;
    }

    /** The lowest block z (inclusive). */
    public int originZ() {
        return originZ;
    }

    /** The x extent in blocks. */
    public int sizeX() {
        return sizeX;
    }

    /** The y extent in blocks. */
    public int sizeY() {
        return sizeY;
    }

    /** The z extent in blocks. */
    public int sizeZ() {
        return sizeZ;
    }

    /**
     * The cell samples, laid out x-fastest: {@code index(lx, ly, lz) =
     * lx + ly*sizeX + lz*sizeX*sizeY}. This is the generator's backing buffer;
     * it is written by {@link ModelFieldGenerator#fill(FieldRegion)} and then
     * treated as immutable.
     *
     * @return the backing {@code double[]} buffer (never null)
     */
    public double[] data() {
        return data;
    }

    /**
     * Linear index for the given block coordinate, offset from the origin and
     * bounds-checked: {@code lx = x - originX} (and likewise for y, z) must lie
     * within {@code [0, sizeX/Y/Z)}.
     *
     * @param x the block x (absolute, must lie within this region)
     * @param y the block y (absolute, must lie within this region)
     * @param z the block z (absolute, must lie within this region)
     * @return the linear offset into {@link #data()}
     * @throws IllegalArgumentException if the coordinate is outside this region
     */
    public int index(int x, int y, int z) {
        int lx = x - originX;
        int ly = y - originY;
        int lz = z - originZ;
        if (lx < 0 || lx >= sizeX || ly < 0 || ly >= sizeY || lz < 0 || lz >= sizeZ) {
            throw new IllegalArgumentException("index out of region: (" + x + ", " + y + ", " + z
                    + ") not in [" + originX + ", " + (originX + sizeX)
                    + ")[x[" + originY + ", " + (originY + sizeY)
                    + ")[x[" + originZ + ", " + (originZ + sizeZ) + ")");
        }
        return lx + ly * sizeX + lz * sizeX * sizeY;
    }

    /**
     * Whether the block coordinate lies within this region
     * ({@code x ∈ [originX, originX+sizeX)} and likewise for y, z).
     *
     * @param x the block x (absolute)
     * @param y the block y (absolute)
     * @param z the block z (absolute)
     * @return {@code true} if within this region
     */
    public boolean contains(int x, int y, int z) {
        return x >= originX && x < originX + sizeX
                && y >= originY && y < originY + sizeY
                && z >= originZ && z < originZ + sizeZ;
    }

    /**
     * Deterministic scalar sample for a continuous (double) block coordinate:
     * the containing cell is {@code (int) Math.floor(value - origin)} per axis;
     * the returned value is the cell's stored sample. Rejecting a coordinate
     * outside the box (including exactly on the upper, exclusive boundary) via
     * IllegalArgumentException. Deterministic: identical {@code (x, y, z)}
     * always yield the identical value, and no allocation occurs.
     *
     * @param x the continuous block x
     * @param y the continuous block y
     * @param z the continuous block z
     * @return the cell sample at the containing cell
     * @throws IllegalArgumentException if any axis lies outside this buffered
     *         region
     */
    public double sample(double x, double y, double z) {
        int ix = (int) Math.floor(x);
        int iy = (int) Math.floor(y);
        int iz = (int) Math.floor(z);
        if (!contains(ix, iy, iz)) {
            throw new IllegalArgumentException("sample outside buffered region: ("
                    + x + ", " + y + ", " + z
                    + ") not in [" + originX + ", " + (originX + sizeX)
                    + ")[x[" + originY + ", " + (originY + sizeY)
                    + ")[x[" + originZ + ", " + (originZ + sizeZ) + ")");
        }
        return data[index(ix, iy, iz)];
    }
}