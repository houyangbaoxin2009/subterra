package io.toterra.subterra.engine.worldgen.tie;

import java.io.InputStream;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Subterra 地形密度的 tie 桥（性能改造）：把世界生成热路径
 * {@code density(x, y, z, seed)} 委托给 tiec 编译的 DLL 求密度。
 *
 * <p>ABI 契约（与 tie 核心固定）：{@code stdens$density(x: i64, y: i64, z: i64, seed: i64) -> f64}，
 * 返回值 ∈ [-1,1]，{@code >0} 实心、{@code <0} 空洞（洞穴/气腔），硬钳位在 tie 侧完成。完整的地表
 * 构造 + 洞穴雕刻（奶酪腔 / 面条隧道 / 石柱 / 洞口）由 DLL 一次算完，Java 侧无需二次钳位。
 *
 * <p>纯 JDK（FFM downcall），零 MC 依赖。装载来源固定序：① 系统属性 {@code subterra.tie.lib}
 * （dev 由 gradle {@code -Psubterra.tie.lib=<abs path>} 转发）；② 类路径资源
 * {@code /tie/subterra_density.dll}（捆绑在 mod jar，提取到临时文件后装载）；③ 两者皆缺 → 返回
 * {@code null}（调用方静默降级回纯 Java 树，不抛异常、不退出）。
 *
 * <p>线程模型：chunk 生成多线程（ForkJoin worker）并发调用 {@link #density} —— downcall handle 本身
 * 线程安全；{@link Arena#ofShared()} 使库句柄生命期可被多线程安全使用。装载只作为生命周期持有，热路径
 * 不触碰 arena，只在 JVM 结束/显式 {@link #close} 时释放（世界切换无需重载：seed 是每次调用的入参，
 * DLL 无可变全局状态）。
 *
 * <p>降级纪律：首次装载失败/符号缺失/冒烟异常/非有限值 → {@link #load()} 返回 {@code null}
 * （记一条 warn，可选 debug 定位），调用方保留现 Java 树输出逐位不变；同一 JVM 只作一次装载尝试。
 * 运行期需 {@code --enable-native-access}（ModDevGradle dev run 默认注入）。
 */
public final class TieTerrainDensityBridge implements AutoCloseable {

    private static final Logger LOG = System.getLogger(
            "io.toterra.subterra.engine.worldgen.tie.TieTerrainDensityBridge");

    /** 导出符号（tie 命名空间转 {@code $}）。 */
    private static final String DENSITY_SYMBOL = "stdens$density";
    /** 显式指定 DLL 的系统属性。 */
    private static final String LIB_PROP = "subterra.tie.lib";
    /** 类路径捆绑的 DLL 资源（subterra-engine/resources/tie）。 */
    private static final String BUNDLED_DLL = "/tie/subterra_density.dll";
    /** 类路径资源提取后的临时文件名（固定名，进程内单实例、确定性）。 */
    private static final String STAGED_NAME = "subterra_density.dll";

    /** {@code stdens$density(i64,i64,i64,i64) -> f64}。 */
    private static final FunctionDescriptor DENSITY_DESC =
            FunctionDescriptor.of(ValueLayout.JAVA_DOUBLE,
                    ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG);

    // ---- Wave B：y 轴分块 / POI 分级 查询符号（FFM 标量 ABI；旧 DLL 缺失 → 默认值降级） ----
    private static final String SYM_Y_MIN = "stdens$y_min";
    private static final String SYM_Y_MAX = "stdens$y_max";
    private static final String SYM_Y_BLOCK_SIZE = "stdens$y_block_size";
    private static final String SYM_Y_BLOCK_COUNT = "stdens$y_block_count";
    private static final String SYM_POI_LEVELS = "stdens$poi_levels";
    private static final String SYM_POI_CELL_SCALE = "stdens$poi_cell_scale";
    private static final String SYM_POI_RADIUS = "stdens$poi_radius";
    private static final String SYM_Y_TO_BLOCK = "stdens$y_to_block";
    private static final String SYM_BLOCK_ACTIVE = "stdens$block_active";

    /** {@code () -> i64}。 */
    private static final FunctionDescriptor F_I64 = FunctionDescriptor.of(ValueLayout.JAVA_LONG);
    /** {@code (i64) -> f64}。 */
    private static final FunctionDescriptor F_I64_F64 =
            FunctionDescriptor.of(ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_LONG);
    /** {@code (i64) -> i64}。 */
    private static final FunctionDescriptor F_I64_I64 =
            FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG);
    /** {@code (i64,i64,i64,i64) -> i64}（block_active）。 */
    private static final FunctionDescriptor F_I64_4_I64 =
            FunctionDescriptor.of(ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG);

    // 旧 DLL 缺失 POI 查询符号时的静态默认值（与 tie 核心常量一致）。
    private static final long DEF_Y_MIN = -128L;      // 世界底部
    private static final long DEF_Y_MAX = 1024L;      // 世界高度上限（方块 y ∈ [-128,1024)）
    private static final long DEF_Y_BLOCK_SIZE = 128L;
    private static final long DEF_Y_BLOCK_COUNT = 9L;
    private static final int DEF_POI_LEVELS = 3;
    private static final double[] DEF_POI_CELL_SCALE = {1.0, 4.0, 32.0};
    private static final double[] DEF_POI_RADIUS = {128.0, 512.0, 2048.0};
    /** 查询失败日志节流：每 JVM 每条符号只记一次 warn。 */
    private static final boolean[] QUERY_FAIL_LOG = new boolean[9];

    private final Arena arena;
    private final MethodHandle handle;
    private final Path source;
    private final boolean temp;
    private final Extras extras;
    private volatile boolean available = false;
    private volatile boolean closed = false;

    /** 装载来源：DLL 路径 + 是否临时提取（临时文件由桥在失败/关闭时清理）。 */
    private record Source(Path path, boolean temp) {
    }

    /**
     * Wave B 查询符号的解析结果（含缺失符号的静态降级默认值）。记录不空，
     * 各字段对旧 DLL 一律落到默认值，桥整体仍可用。
     */
    private record Extras(
            long yMin, long yMax, long yBlockSize, long yBlockCount, int poiLevels,
            double[] poiCellScale, double[] poiRadius,
            MethodHandle yToBlockH, MethodHandle blockActiveH) {
    }

    private TieTerrainDensityBridge(Arena arena, MethodHandle handle, Path source, boolean temp,
                                    Extras extras) {
        this.arena = arena;
        this.handle = handle;
        this.source = source;
        this.temp = temp;
        this.extras = extras;
    }

    /**
     * 装载并冒烟校验；任何失败/不可用都返回 {@code null}（不抛）。调用方据此走纯 Java 树回退。
     *
     * @return 就绪且通过冒烟的桥；装载失败返回 {@code null}
     */
    public static TieTerrainDensityBridge load() {
        Source source = locate();
        if (source == null) {
            LOG.log(Level.WARNING, "subterra terrain tie bridge: no DLL located "
                    + "(no -Dsubterra.tie.lib, no bundled /tie/subterra_density.dll); "
                    + "falling back to the pure-Java density tree.");
            return null;
        }
        Path dll = source.path();
        boolean temp = source.temp();
        Arena arena = Arena.ofShared();
        TieTerrainDensityBridge bridge = null;
        try {
            SymbolLookup lookup = SymbolLookup.libraryLookup(dll, arena);
            MemorySegment addr = lookup.find(DENSITY_SYMBOL).orElse(null);
            if (addr == null) {
                LOG.log(Level.WARNING, String.format(
                        "subterra terrain tie bridge: symbol %s not exported in %s; "
                                + "falling back to the pure-Java density tree.", DENSITY_SYMBOL, dll));
            } else {
                MethodHandle handle = Linker.nativeLinker().downcallHandle(addr, DENSITY_DESC);
                Extras extras = resolveExtras(dll, lookup);
                bridge = new TieTerrainDensityBridge(arena, handle, dll, temp, extras);
                double smoke = bridge.invoke(0L, 63L, 0L, 42L);
                if (!Double.isFinite(smoke)) {
                    LOG.log(Level.WARNING, String.format(
                            "subterra terrain tie bridge: smoke density(0,63,0,42) returned non-finite %s; "
                                    + "falling back to the pure-Java density tree.", smoke));
                    bridge.available = false;
                    bridge = null;
                } else {
                    bridge.available = true;
                    LOG.log(Level.INFO, String.format(
                            "subterra terrain tie bridge: loaded %s; smoke density(0,63,0,42)=%s",
                            dll, smoke));
                }
            }
        } catch (Throwable t) {
            LOG.log(Level.WARNING, String.format(
                    "subterra terrain tie bridge: load/smoke failed on %s; "
                            + "falling back to the pure-Java density tree.", dll));
            LOG.log(Level.DEBUG, "subterra terrain tie bridge load failure detail", t);
            bridge = null;
        }
        if (bridge == null) {
            try {
                arena.close();
            } catch (Throwable ignored) {
                // best-effort
            }
            if (temp) {
                try {
                    Files.deleteIfExists(dll);
                } catch (Exception ignored) {
                    // best-effort
                }
            }
        }
        return bridge;
    }

    /** 桥是否就绪（DLL + 符号解析 + 一次冒烟调用全部成功）。多线程安全（volatile）。 */
    public boolean available() {
        return !closed && available;
    }

    /**
     * 地形密度热路径：{@code (x, y, z) -> double}，∈ [-1,1]（tie 侧硬钳位），
     * {@code >0} 实心、{@code <0} 洞穴。仅当 {@link #available()} 为真时调用。
     */
    public double density(long x, long y, long z, long seed) {
        return invoke(x, y, z, seed);
    }

    /** downcall 单点标量求值。 */
    private double invoke(long x, long y, long z, long seed) {
        try {
            return (double) handle.invokeExact(x, y, z, seed);
        } catch (Throwable t) {
            // 热路径不应触发；若触发视同装载失败标记不可用，永不向上抛。
            available = false;
            LOG.log(Level.WARNING, "subterra terrain tie bridge: density downcall failed; "
                    + "disabling bridge (caller falls back to the pure-Java density tree).", t);
            return Double.NaN;
        }
    }

    // ==================== Wave B：y 分块 / POI 分级 查询 ====================
    // 每类是否"查询失败"只 warn 一次（节流，避免热路径刷屏）。
    private static void warnQueryFailOnce(int slot, String symbol) {
        synchronized (QUERY_FAIL_LOG) {
            if (slot < QUERY_FAIL_LOG.length && !QUERY_FAIL_LOG[slot]) {
                QUERY_FAIL_LOG[slot] = true;
                LOG.log(Level.WARNING, String.format(
                        "subterra terrain tie bridge: query %s failed; degrading to default.", symbol));
            }
        }
    }

    /** 世界底部方块 y。 */
    public long yMin() {
        return extras.yMin;
    }

    /** 世界高度上限（方块 y < 该值）。 */
    public long yMax() {
        return extras.yMax;
    }

    /** y 轴分块大小。 */
    public long yBlockSize() {
        return extras.yBlockSize;
    }

    /** y 轴分块数量。 */
    public long yBlockCount() {
        return extras.yBlockCount;
    }

    /** 关注点(POI)分级层数。 */
    public int poiLevels() {
        return extras.poiLevels;
    }

    /** 指定 POI 分级层的水平采样格大小；越界 level 返回最外层值。 */
    public double poiCellScale(long level) {
        double[] cs = extras.poiCellScale;
        if (level >= 0 && level < cs.length) {
            return cs[(int) level];
        }
        return cs[cs.length - 1];
    }

    /** 指定 POI 分级层的半径；越界 level 返回 0.0。 */
    public double poiRadius(long level) {
        double[] r = extras.poiRadius;
        if (level >= 0 && level < r.length) {
            return r[(int) level];
        }
        return 0.0;
    }

    /**
     * 方块 y → y 块索引；越界返回 -1。优先走 DLL 符号；缺失/失败降级为公式 {@code (y-yMin)/size}。
     */
    public long yToBlock(long y) {
        MethodHandle h = extras.yToBlockH;
        if (h != null && !closed) {
            try {
                return (long) h.invokeExact(y);
            } catch (Throwable t) {
                warnQueryFailOnce(0, SYM_Y_TO_BLOCK);
            }
        }
        return defaultYToBlock(y);
    }

    /** 公式回退版 {@link #yToBlock}（不依赖 DLL）。 */
    public long defaultYToBlock(long y) {
        long yMin = extras.yMin;
        long yMax = extras.yMax;
        if (y < yMin || y >= yMax) {
            return -1;
        }
        return (y - yMin) / extras.yBlockSize;
    }

    /**
     * {@code (x,z)} 列、含 y 的块是否需要全精度密度：1=需要，0=可粗占位。
     * 优先走 DLL 符号；缺失/失败降级为 1（保守全精度，不破坏地形语义）。
     */
    public long blockActive(long x, long y, long z, long seed) {
        MethodHandle h = extras.blockActiveH;
        if (h != null && !closed) {
            try {
                return (long) h.invokeExact(x, y, z, seed);
            } catch (Throwable t) {
                warnQueryFailOnce(1, SYM_BLOCK_ACTIVE);
            }
        }
        return 1L;
    }

    /**
     * 解析 Wave B 查询符号（缺失/调用失败 → 对应默认值降级），一次性记录缺失符号的 warn。
     */
    private static Extras resolveExtras(Path dll, SymbolLookup lookup) {
        StringBuilder missing = new StringBuilder();
        MethodHandle yMinH = resolve(lookup, SYM_Y_MIN, F_I64, missing);
        MethodHandle yMaxH = resolve(lookup, SYM_Y_MAX, F_I64, missing);
        MethodHandle yBlockSizeH = resolve(lookup, SYM_Y_BLOCK_SIZE, F_I64, missing);
        MethodHandle yBlockCountH = resolve(lookup, SYM_Y_BLOCK_COUNT, F_I64, missing);
        MethodHandle poiLevelsH = resolve(lookup, SYM_POI_LEVELS, F_I64, missing);
        MethodHandle poiCellScaleH = resolve(lookup, SYM_POI_CELL_SCALE, F_I64_F64, missing);
        MethodHandle poiRadiusH = resolve(lookup, SYM_POI_RADIUS, F_I64_F64, missing);
        MethodHandle yToBlockH = resolve(lookup, SYM_Y_TO_BLOCK, F_I64_I64, missing);
        MethodHandle blockActiveH = resolve(lookup, SYM_BLOCK_ACTIVE, F_I64_4_I64, missing);

        long yMin = invokeOr(yMinH, DEF_Y_MIN);
        long yMax = invokeOr(yMaxH, DEF_Y_MAX);
        long yBlockSize = invokeOr(yBlockSizeH, DEF_Y_BLOCK_SIZE);
        long yBlockCount = invokeOr(yBlockCountH, DEF_Y_BLOCK_COUNT);
        long poiLevelsL = invokeOr(poiLevelsH, DEF_POI_LEVELS);
        int poiLevels = (int) clamp(poiLevelsL, 1, 8);

        double[] poiCellScale = new double[poiLevels];
        double[] poiRadius = new double[poiLevels];
        for (int i = 0; i < poiLevels; i++) {
            poiCellScale[i] = poiCellScaleH != null
                    ? invokeFloor(poiCellScaleH, i, i < DEF_POI_CELL_SCALE.length ? DEF_POI_CELL_SCALE[i] : 1.0)
                    : (i < DEF_POI_CELL_SCALE.length ? DEF_POI_CELL_SCALE[i] : 1.0);
            poiRadius[i] = poiRadiusH != null
                    ? invokeFloor(poiRadiusH, i, i < DEF_POI_RADIUS.length ? DEF_POI_RADIUS[i] : 0.0)
                    : (i < DEF_POI_RADIUS.length ? DEF_POI_RADIUS[i] : 0.0);
        }

        if (missing.length() > 0) {
            LOG.log(Level.WARNING, String.format(
                    "subterra terrain tie bridge (%s): optional Wave-B symbols missing; "
                            + "using static defaults for: %s", dll, missing));
        }
        return new Extras(yMin, yMax, yBlockSize, yBlockCount, poiLevels,
                poiCellScale, poiRadius, yToBlockH, blockActiveH);
    }

    /** 解析符号；缺失追加到 {@code missing} 并返回 null。 */
    private static MethodHandle resolve(SymbolLookup lookup, String symbol,
                                        FunctionDescriptor desc, StringBuilder missing) {
        var addr = lookup.find(symbol).orElse(null);
        if (addr == null) {
            missing.append(symbol).append(' ');
            return null;
        }
        return Linker.nativeLinker().downcallHandle(addr, desc);
    }

    /** 调用 0 参 long 查询；失败返回 fallback。 */
    private static long invokeOr(MethodHandle h, long fallback) {
        if (h == null) {
            return fallback;
        }
        try {
            return (long) h.invokeExact();
        } catch (Throwable t) {
            return fallback;
        }
    }

    /** 调用 1 参 long→double 查询；失败返回 fallback。 */
    private static double invokeFloor(MethodHandle h, long level, double fallback) {
        if (h == null) {
            return fallback;
        }
        try {
            return (double) h.invokeExact(level);
        } catch (Throwable t) {
            return fallback;
        }
    }

    private static long clamp(long v, long lo, long hi) {
        return v < lo ? lo : (Math.min(v, hi));
    }

    /** 定位 DLL（固定序：{@code subterra.tie.lib} 属性 → 类路径捆绑资源）；两者皆缺返回 null。 */
    private static Source locate() {
        String external = System.getProperty(LIB_PROP);
        if (external != null && !external.isBlank()) {
            Path p = Path.of(external);
            if (Files.isRegularFile(p)) {
                return new Source(p, false);
            }
        }
        Path staged = Path.of(System.getProperty("java.io.tmpdir"), STAGED_NAME);
        try (InputStream in = TieTerrainDensityBridge.class.getResourceAsStream(BUNDLED_DLL)) {
            if (in == null) {
                return null;
            }
            Files.copy(in, staged, StandardCopyOption.REPLACE_EXISTING);
            return new Source(staged, true);
        } catch (Exception e) {
            return null;
        }
    }

    /** 释放宿主库句柄；此后 {@link #available()} 为 false。幂等。 */
    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        available = false;
        try {
            arena.close();
        } catch (Throwable ignored) {
            // best-effort
        }
        if (temp) {
            try {
                Files.deleteIfExists(source);
            } catch (Exception ignored) {
                // best-effort temp cleanup
            }
        }
    }
}