package io.toterra.subterra.optim.worldgen.pipeline.dimension;

/**
 * Water body class for the hydro dual-slot (Step 3). {@link #NONE} and
 * {@link #OCEAN} are effective classes: the default fallback sampler
 * ({@link VanillaWaterClass}) produces only these, matching vanilla. The
 * remaining values ({@link #RIVER}, {@link #STREAM}, {@link #LAKE},
 * {@link #WETLAND}, {@link #SHELF}, {@link #TRENCH}, {@link #STRAIT}) are
 * reserved: they are never produced by the default fallback sampler and are
 * enabled only by later hydrology algorithm batches. {@link #isReserved()}
 * distinguishes the two groups.
 * <p>
 * 水文双插槽的水体类型（第 3 步）。{@link #NONE} 与 {@link #OCEAN} 为生效值：默认回推
 * 采样器（{@link VanillaWaterClass}）只产生这两者，与原版一致。其余取值
 * {@link #RIVER}、{@link #STREAM}、{@link #LAKE}、{@link #WETLAND}、
 * {@link #SHELF}、{@link #TRENCH}、{@link #STRAIT} 为预留：默认回推采样器永不产生，
 * 仅由后续水文算法批次启用。{@link #isReserved()} 区分这两组。
 */
public enum WaterClass {

    /** No standing water. */
    NONE(false),
    /** Open ocean. */
    OCEAN(false),

    /** Reserved: river channel. */
    RIVER(true),
    /** Reserved: stream outflow. */
    STREAM(true),
    /** Reserved: lake. */
    LAKE(true),
    /** Reserved: wetland. */
    WETLAND(true),
    /** Reserved: continental shelf. */
    SHELF(true),
    /** Reserved: ocean trench. */
    TRENCH(true),
    /** Reserved: strait. */
    STRAIT(true);

    private final boolean reserved;

    WaterClass(boolean reserved) {
        this.reserved = reserved;
    }

    /**
     * Whether this class is a reserved value reserved for later hydrology
     * algorithm batches — i.e. never produced by the default fallback
     * {@link VanillaWaterClass}.
     *
     * @return {@code true} if this is a reserved (future-hydrology) class
     */
    public boolean isReserved() {
        return reserved;
    }
}