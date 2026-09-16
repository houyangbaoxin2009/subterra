package io.toterra.subterra.tie;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;

import io.toterra.subterra.engine.tie.TieFunction;
import io.toterra.subterra.engine.tie.TieLibrary;

/**
 * Trimand —— 微型扩散三定调器 Java 桥（装配于 subterra-tie 资源）。
 *
 * <p>装载 tiec {@code --shared} 产出的 {@code subterra_diffuse.dll}（导出
 * {@code rfwd$coarse_climate / rfwd$coarse_elev / rfwd$coarse_mountain}，ABI =
 * {@code (i64, i64, i64) -> f64}），为地形/气候/水文消费方提供宏观粗场查询。
 *
 * <p><b>装载链路（p.2.35 闭环）</b>：FFM 不再自建，改走引擎共享的 tie 桥
 * ——{@link TieLibrary#load(Path)} 建库（SymbolLookup + Arena 生命周期）→
 * {@link TieLibrary#find(String)} 解析导出函数 → {@link TieFunction#invokeI64I64I64I64}
 * （其内部即 p.2.1 新增的 {@code h3i} downcall 句柄，形态 {@code (i64,i64,i64) -> f64}）。
 * 因此 subterra-tie 与 runtime 共用同一条 FFM 装载路径，无第二套 FFM 实现。
 *
 * <p><b>符号名（确定性固定序）</b>：① {@code rfwd$coarse_*} —— 生成产物
 * {@code tie-src/trimand/gen/gen-subterra-runtime.tie} 把模板命名空间 {@code stdens} 并入
 * {@code namespace rfwd} 后的实际导出面（当前随仓 DLL 即此形态）；② 回退
 * {@code stdens$coarse_*} —— 模板 {@code subterra_diffuse_runtime.tmpl.tie} 的原始命名空间，
 * 为未重写命名空间的旧/旁支 DLL 保留。符号解析按此固定序逐个尝试，同库同符号恒得同结果。
 *
 * <p><b>装载来源（确定性固定序，与 TieRuntime 同模式）</b>：① 系统属性
 * {@code subterra.trimand.lib}（dev 经 gradle {@code -Psubterra.trimand.lib=<abs path>}
 * 转发）；② 类路径资源 {@code /tie/subterra_diffuse.dll}（jar 捆绑时提取到临时文件装载）；
 * ③ 两者皆缺 → {@link #locate()} 返回 empty（调用方按「无 tie 库」确定性 skip）。
 *
 * <p><b>缺 DLL 的降级纪律</b>：{@link #locate()} 返回 empty、或装载/符号解析失败时，
 * {@link #report()} 产出确定性 {@code skip} 行（而非失败），{@link TrimandBridgeException}
 * 只在调用方显式 {@link #load(Path)} 时抛出。skip 与 ok 两路径都可被探针/E2E 断言，且两者
 * 都不改变缺省预设的密度真值（引擎侧 {@code TrimandAssembly.DEFAULT_PARAMS} 为恒等）。
 *
 * <p>纯 JDK：FFM（java.lang.foreign）经引擎桥，无 MC 依赖；运行期需
 * {@code --enable-native-access}（ModDevGradle dev run 已注入）。
 *
 * <p>确定性：同 (x,z,seed) 同值；粗场值域 ∈ [-1,1]。tie 侧同名源见
 * {@code tie-src/trimand/}（gen/subterra_diffuse_runtime.gen.tie → DLL）。
 */
public final class TrimandBridge implements AutoCloseable {

    /** 探针 marker 前缀 / probe marker prefix. */
    public static final String MARKER = "[Subterra trimand]";

    /** 类路径捆绑的 Trimand 动态库资源。Bundled Trimand dll resource. */
    public static final String BUNDLED_DLL = "/tie/subterra_diffuse.dll";

    /** 显式指定 DLL 的系统属性。The system property naming an explicit dll. */
    public static final String LIB_PROP = "subterra.trimand.lib";

    /** 类路径资源提取后的临时文件名（固定名，进程内单实例，确定性）。 */
    private static final String STAGED_NAME = "subterra_trimand.dll";

    /** 主符号前缀（生成产物的 {@code namespace rfwd}）。 */
    private static final String NS_PRIMARY = "rfwd$";
    /** 回退符号前缀（模板的 {@code namespace stdens}）。 */
    private static final String NS_FALLBACK = "stdens$";

    /** 三个粗场的短名（导出符号 = 前缀 + 短名）。 */
    private static final String SHORT_CLIMATE = "coarse_climate";
    private static final String SHORT_ELEV = "coarse_elev";
    private static final String SHORT_MOUNTAIN = "coarse_mountain";

    /** 冒烟/报告用的固定查询参数（禁止时序/随机）。 */
    private static final long REPORT_X = 100L;
    private static final long REPORT_Z = 200L;
    private static final long REPORT_SEED = 20260915L;

    /** 粗场报告的宽松确定性界限（tie 侧契约 [-1,1]；留相位余量，超界即判 mismatch）。 */
    private static final double REPORT_BOUND = 4.0;

    private final TieLibrary library;
    private final TieFunction climate;
    private final TieFunction elevation;
    private final TieFunction mountain;
    private final Path source;
    private final String symbolPrefix;

    private TrimandBridge(TieLibrary library, TieFunction climate, TieFunction elevation,
                          TieFunction mountain, Path source, String symbolPrefix) {
        this.library = library;
        this.climate = climate;
        this.elevation = elevation;
        this.mountain = mountain;
        this.source = source;
        this.symbolPrefix = symbolPrefix;
    }

    /** 从指定路径装载。失败抛 {@link TrimandBridgeException}（含装载与符号两层原因）。 */
    public static TrimandBridge load(Path dll) {
        TieLibrary lib;
        try {
            lib = TieLibrary.load(dll);
        } catch (Throwable t) {
            throw new TrimandBridgeException("Trimand DLL 装载失败: " + dll, t);
        }
        try {
            String prefix = resolvePrefix(lib);
            return new TrimandBridge(lib,
                    require(lib, prefix, SHORT_CLIMATE),
                    require(lib, prefix, SHORT_ELEV),
                    require(lib, prefix, SHORT_MOUNTAIN),
                    dll, prefix);
        } catch (Throwable t) {
            lib.close();
            throw t instanceof TrimandBridgeException e ? e
                    : new TrimandBridgeException("Trimand DLL 装载失败: " + dll, t);
        }
    }

    /** 从类路径资源提取并装载（jar 捆绑；提取到临时文件）。资源缺失 → empty。 */
    public static Optional<TrimandBridge> loadBundled() {
        Path staged = Path.of(System.getProperty("java.io.tmpdir"), STAGED_NAME);
        try (InputStream in = TrimandBridge.class.getResourceAsStream(BUNDLED_DLL)) {
            if (in == null) {
                return Optional.empty();
            }
            Files.copy(in, staged, StandardCopyOption.REPLACE_EXISTING);
            return Optional.of(load(staged));
        } catch (Throwable t) {
            return Optional.empty();
        }
    }

    /** 定位 Trimand 动态库（固定序：{@code subterra.trimand.lib} 属性 → 类路径捆绑资源）。 */
    public static Optional<TrimandBridge> locate() {
        String external = System.getProperty(LIB_PROP);
        if (external != null && !external.isBlank()) {
            Path p = Path.of(external);
            if (Files.isRegularFile(p)) {
                try {
                    return Optional.of(load(p));
                } catch (TrimandBridgeException e) {
                    return Optional.empty();
                }
            }
        }
        return loadBundled();
    }

    /**
     * 确定性装载报告（ok / skip 两路径，禁时序）：无 DLL 或装载失败 → {@code skip (...)}；
     * 装载成功且三个粗场在固定查询点通过「值域 + 二次调用逐位一致」校验 → {@code ok (...)}；
     * 值域/确定性不满足 → {@code mismatch (...)}（防御性第三路径，不伪装成 ok）。
     *
     * <p>报告行恒以 {@link #MARKER} 开头，可被纯 JVM 探针与 E2E 门控直接断言；任何路径都不抛异常。
     */
    public static String report() {
        Optional<TrimandBridge> located;
        try {
            located = locate();
        } catch (Throwable t) {
            return MARKER + " skip (dll load failed)";
        }
        if (located.isEmpty()) {
            return MARKER + " skip (no dll)";
        }
        try (TrimandBridge b = located.get()) {
            return b.status();
        } catch (Throwable t) {
            return MARKER + " skip (dll load failed)";
        }
    }

    /**
     * 已装载桥的确定性状态行（{@code ok}/{@code mismatch} 两路径）。 /
     *  The deterministic status line of a loaded bridge ({@code ok}/{@code mismatch}).
     */
    public String status() {
        double c = coarseClimate(REPORT_X, REPORT_Z, REPORT_SEED);
        double e = coarseElev(REPORT_X, REPORT_Z, REPORT_SEED);
        double m = coarseMountain(REPORT_X, REPORT_Z, REPORT_SEED);
        double c2 = coarseClimate(REPORT_X, REPORT_Z, REPORT_SEED);
        boolean ok = c == c2
                && withinBound(c) && withinBound(e) && withinBound(m);
        return MARKER + (ok ? " ok" : " mismatch")
                + " (dll=" + source + ", ns=" + symbolPrefix + ", x=" + REPORT_X + ", z=" + REPORT_Z
                + ", climate=" + c + ", elev=" + e + ", mtn=" + m + ")";
    }

    /** 气候粗场 ∈ [-1,1]（湿润正 / 干旱负；供水文组合派生）。 */
    public double coarseClimate(long x, long z, long seed) {
        return call(climate, x, z, seed);
    }

    /** 海拔粗场 ∈ [-1,1]（>0 陆地 / <0 海洋）。 */
    public double coarseElev(long x, long z, long seed) {
        return call(elevation, x, z, seed);
    }

    /** 山带粗场 ∈ [-1,1]（[0,1] 山带强度 ×2-1）。 */
    public double coarseMountain(long x, long z, long seed) {
        return call(mountain, x, z, seed);
    }

    /** 装载来源路径。 / The load source path. */
    public Path source() {
        return source;
    }

    /** 命中的符号命名空间前缀（{@code rfwd$} 或 {@code stdens$}）。 /
     *  The matched symbol namespace prefix ({@code rfwd$} or {@code stdens$}). */
    public String symbolPrefix() {
        return symbolPrefix;
    }

    /** 释放宿主库句柄；其后调用失败。幂等。 */
    @Override
    public void close() {
        library.close();
    }

    /** 按固定序解析命名空间前缀：{@code rfwd$} 三符号齐全先胜，否则 {@code stdens$}。 */
    private static String resolvePrefix(TieLibrary lib) {
        if (hasAll(lib, NS_PRIMARY)) {
            return NS_PRIMARY;
        }
        if (hasAll(lib, NS_FALLBACK)) {
            return NS_FALLBACK;
        }
        throw new TrimandBridgeException("Trimand DLL 缺导出符号: "
                + NS_PRIMARY + SHORT_CLIMATE + " / " + NS_FALLBACK + SHORT_CLIMATE);
    }

    private static boolean hasAll(TieLibrary lib, String prefix) {
        return lib.contains(prefix + SHORT_CLIMATE)
                && lib.contains(prefix + SHORT_ELEV)
                && lib.contains(prefix + SHORT_MOUNTAIN);
    }

    private static TieFunction require(TieLibrary lib, String prefix, String shortName) {
        String symbol = prefix + shortName;
        return lib.find(symbol).orElseThrow(
                () -> new TrimandBridgeException("Trimand DLL 缺导出符号: " + symbol));
    }

    private static boolean withinBound(double v) {
        return Double.isFinite(v) && v >= -REPORT_BOUND && v <= REPORT_BOUND;
    }

    private static double call(TieFunction fn, long x, long z, long seed) {
        try {
            return fn.invokeI64I64I64I64(x, z, seed);
        } catch (Throwable t) {
            throw new TrimandBridgeException("Trimand 粗场调用失败 (" + x + "," + z + "," + seed + ")", t);
        }
    }

    /** 冒烟：打印确定性报告行（skip 与 ok 两路径），mismatch 时退出码 1。 */
    public static void main(String[] args) {
        String line = report();
        System.out.println(line);
        if (line.contains(" mismatch")) {
            System.exit(1);
        }
    }
}
