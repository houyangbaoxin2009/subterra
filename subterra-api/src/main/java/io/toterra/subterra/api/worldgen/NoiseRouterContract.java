package io.toterra.subterra.api.worldgen;

import java.util.List;

/**
 * The worldgen golden-contract surface (p.2.16.1): a pure-JDK, engine-free
 * description of the frozen vanilla noise-router reference data. It carries the
 * fifteen canonical field names (byte-for-byte the order of the engine mirror's
 * {@code NoiseRouter.FIELD_NAMES}, which itself matches the vanilla 1.21.1
 * record order), the golden anchor (seed {@value #GOLDEN_SEED}, Y range
 * [{@value #GOLDEN_MIN_Y}, {@value #GOLDEN_MAX_Y}), the twelve fixed sample
 * points, and the bit-pattern layout.
 * <p>
 * Golden-contract semantics: same seed + same sample points + same field order
 * ⇒ {@code FIELD_COUNT * POINT_COUNT = 180} {@code double} bit patterns
 * ({@link #bitPattern}) are a frozen golden baseline (golden_version =
 * {@value #GOLDEN_VERSION}). If the engine mirror is changed without bumping
 * {@link #GOLDEN_VERSION}, that is a contract breach, gated by the
 * VanillaGoldenProbe check. This type only states the contract shape; the
 * numeric baseline is injected as data (the golden asset), never derived here.
 * <p>
 * worldgen 黄金契约面（p.2.16.1）：对冻结的原生噪声路由器参照数据的纯 JDK、无 engine
 * 依赖的确定性描述。它携带十五个规范字段名（与 engine 镜像 {@code NoiseRouter.FIELD_NAMES}
 * 逐字同序——后者本身与原生 1.21.1 record 顺序一致）、金标锚点（种子
 * {@value #GOLDEN_SEED}、Y 区间 [{@value #GOLDEN_MIN_Y}, {@value #GOLDEN_MAX_Y}]）、
 * 十二个固定采样点，以及位模式布局。
 * <p>
 * 黄金契约语义：同 seed + 同采样点 + 同字段序 ⇒
 * {@code FIELD_COUNT * POINT_COUNT = 180} 个 {@code double} 位模式
 * （{@link #bitPattern}）为冻结金标（golden_version = {@value #GOLDEN_VERSION}）。
 * 若 engine 镜像被改动而未提升 {@link #GOLDEN_VERSION}，即为违约，由
 * VanillaGoldenProbe 门禁拦截。本类型只陈述契约形状；数值底座以数据注入
 * （golden 资产）呈现，绝不在本处推导。
 */
public final class NoiseRouterContract {

    private NoiseRouterContract() {
    }

    /**
     * The golden seed whose overworld router outputs form the frozen baseline.
     * 金标种子：其主世界路由器输出构成冻结基线。
     */
    public static final long GOLDEN_SEED = 44905237L;

    /** Golden overworld Y range: minimum block Y (inclusive). 金标主世界最小方块 Y（含）。 */
    public static final int GOLDEN_MIN_Y = -64;

    /** Golden overworld Y range: maximum block Y (exclusive). 金标主世界最大方块 Y（不含）。 */
    public static final int GOLDEN_MAX_Y = 320;

    /** Golden asset version; must be bumped whenever the engine mirror output changes.
     *  golden 资产版本：engine 镜像输出一旦变化必须升级。 */
    public static final int GOLDEN_VERSION = 1;

    /** The number of fixed sample points (12). 固定采样点数（12）。 */
    public static final int POINT_COUNT = 12;

    /** The number of canonical router fields (15). 规范路由器字段数（15）。 */
    public static final int FIELD_COUNT = 15;

    /**
     * The fifteen canonical MC field names, in router order: byte-for-byte the
     * order of the engine mirror's {@code NoiseRouter.FIELD_NAMES} (verified
     * against the vanilla 1.21.1 record). This array is the single authoritative
     * source for the contract field order; {@link #indexOfField} resolves names
     * against it.
     * 十五个规范 MC 字段名，按路由器顺序：与 engine 镜像 {@code NoiseRouter.FIELD_NAMES}
     * 逐字同序（经原生 1.21.1 record 核验）。本数组是契约字段序的唯一权威来源；
     * {@link #indexOfField} 按它解析名称。
     */
    public static final String[] FIELD_NAMES = {
            "barrierNoise",
            "fluidLevelFloodednessNoise",
            "fluidLevelSpreadNoise",
            "lavaNoise",
            "temperature",
            "vegetation",
            "continents",
            "erosion",
            "depth",
            "ridges",
            "initialDensityWithoutJaggedness",
            "finalDensity",
            "veinToggle",
            "veinRidged",
            "veinGap",
    };

    /**
     * A fixed sample point at raw block coordinates {@code (x, y, z)}.
     * 原始方块坐标 {@code (x, y, z)} 处的固定采样点。
     */
    public record SamplePoint(long x, int y, long z) {
    }

    /**
     * The twelve fixed sample points, in golden order: three columns
     * {@code (0,0)}, {@code (123,-456)}, {@code (9999,3)} × the four Y levels
     * {@code -40, 0, 16, 64}, outer loop over columns, inner loop over Y — the
     * exact order of the reference compare bridge's sample list, so golden
     * bit-pattern matrices line up index-for-index.
     * 十二个固定采样点，按金标顺序：三列 {@code (0,0)}、{@code (123,-456)}、
     * {@code (9999,3)} × 四个 Y 层 {@code -40, 0, 16, 64}，外层循环列、内层循环
     * Y——与参照对拍桥的采样表完全一致，保证金标位模式矩阵逐索引对齐。
     */
    public static final List<SamplePoint> SAMPLE_POINTS = List.of(
            new SamplePoint(0L, -40, 0L), new SamplePoint(0L, 0, 0L),
            new SamplePoint(0L, 16, 0L), new SamplePoint(0L, 64, 0L),
            new SamplePoint(123L, -40, -456L), new SamplePoint(123L, 0, -456L),
            new SamplePoint(123L, 16, -456L), new SamplePoint(123L, 64, -456L),
            new SamplePoint(9999L, -40, 3L), new SamplePoint(9999L, 0, 3L),
            new SamplePoint(9999L, 16, 3L), new SamplePoint(9999L, 64, 3L));

    /**
     * The golden bit pattern of {@code value}: {@link Double#doubleToLongBits}.
     * The 180 golden patterns are these longs, in
     * {@code FIELD_NAMES[i]} × {@code SAMPLE_POINTS[j]} order.
     * {@code value} 的金标位模式：{@link Double#doubleToLongBits}。180 个金标模式即
     * 这些 long，按 {@code FIELD_NAMES[i]} × {@code SAMPLE_POINTS[j]} 顺序排列。
     */
    public static long bitPattern(double value) {
        return Double.doubleToLongBits(value);
    }

    /**
     * The canonical index of {@code name} in {@link #FIELD_NAMES}, or {@code -1}
     * when the name is not one of the fifteen canonical fields.
     * {@code name} 在 {@link #FIELD_NAMES} 中的规范索引；若不属于十五个规范字段则返回
     * {@code -1}。
     */
    public static int indexOfField(String name) {
        for (int i = 0; i < FIELD_NAMES.length; i++) {
            if (FIELD_NAMES[i].equals(name)) {
                return i;
            }
        }
        return -1;
    }
}
