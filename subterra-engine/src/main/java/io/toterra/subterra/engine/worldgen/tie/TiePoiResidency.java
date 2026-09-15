package io.toterra.subterra.engine.worldgen.tie;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Subterra Wave B 的 y 轴分块 + 关注点(POI)分级内存调度器（Wave B 的 Java 侧）。
 *
 * <p>把"无限世界 × 1024 高"（方块 y ∈ [yMin, yMax)，y 轴被切成 {@code yBlockCount} 个块）
 * 的密度计算限制在为关注点（玩家/实体）驻留的块集内，避免 1024 列整列常驻，老机器也跑得动。
 * 生成/缓存/采样只物化"当前需要的 y 块"。
 *
 * <p>分级驻留规则（L0/L1/L2 半径与采样格来自桥查询，缺省值 {@code {128,512,2048}} /
 * {@code {1,4,32}}）：
 * <ul>
 *   <li>水平距离 {@code d < L0.r} → 该 {@code (x,z)} 列所有 {@code blockY} 都驻留（列含洞穴/表层，
 *       全精度）；</li>
 *   <li>{@code L0.r ≤ d < L1.r} → 只驻留 {@code block_active==1} 的块（粗占位跳过纯空气空块）；</li>
 *   <li>{@code L1.r ≤ d < L2.r} → 只驻留 {@code block_active==1} 且在近地表洞带内的块
 *       （{@code block_active} 本身即以 {@code CAVE_ACTIVE_TOP} 编码"表层/洞带上缘"，
 *       故 L1/L2 同用该判定，天然满足"近地表"约束）；</li>
 *   <li>{@code d ≥ L2.r} → 不驻留，走粗占位。</li>
 * </ul>
 * 结果"总驻留块 ≈ 常数 × 容量"：外环按采样格降采样，总量被分级半径与格距钳住。
 *
 * <p>块身份 = {@code (blockCX, blockCZ, blockY)}，blockY 由 {@code yToBlock} 得出。
 * 关注点移动 → {@link #updatePoi(long, long)} 增量换区：先在 volatile 上换中心（立即生效，
 * 判定层 O(1)），再（位移超过阈值时）重扫驻留列 LRU 并施加 {@code subterra.poi.maxResident} 上限
 * （LinkedHashMap 访问序 LRU，超出剔除最久未用）。全精度判定 {@link #activeLocally} 为纯 O(1)，
 * 热路径零锁：并发只读；换区写锁在内。
 *
 * <p>粗占位：非驻留块不调用 {@code stdens$density}，由低廉常量近似替代
 * （见 {@link #coarsePlaceholder()}，默认 0.0=空气；性能优先，非驻留 0 成本）。
 *
 * <p>单例 {@link #instance()}（volatile 双检，持桥引用）。开关 {@code subterra.poi.enabled}
 * 默认 true；关闭则 {@code activeLocally} 恒真（= 全驻留，与接入调度前逐位一致）。
 * 纯 JDK（在 java.lang.foreign 之外仅标准库），无 MC 依赖，线程安全。
 */
public final class TiePoiResidency {

    private static final Logger LOG = System.getLogger(
            "io.toterra.subterra.engine.worldgen.tie.TiePoiResidency");

    /** 开关：false → 永远全驻留（相当于未接入调度，与现状一致）。默认 true。 */
    private static final boolean ENABLED = Boolean.parseBoolean(
            System.getProperty("subterra.poi.enabled", "true"));
    /** 驻留列 LRU 容量上限。默认 262144（覆盖默认 POI 下全分级驻留列量级，远小于 1024×列面积）。 */
    private static final long MAX_RESIDENT = Long.parseLong(
            System.getProperty("subterra.poi.maxResident", "262144"));
    /** POI 位移达到该阈值才重扫驻留 LRU（块）。默认 8。 */
    private static final long SWEEP_STEP = Long.parseLong(
            System.getProperty("subterra.poi.sweepStep", "8"));
    /** 粗占位常量：非驻留列的密度近似，默认 0.0（空气）。 */
    private static final double COARSE_PLACEHOLDER = Double.parseDouble(
            System.getProperty("subterra.poi.coarsePlaceholder", "0.0"));

    /** 无桥/调度源缺失时的静态 POI 常量（与 tie 核心一致）。 */
    private static final long DEF_Y_MIN = -128L;
    private static final long DEF_Y_MAX = 1024L;
    private static final long DEF_Y_BLOCK_SIZE = 128L;
    private static final double[] DEF_RADIUS = {128.0, 512.0, 2048.0};
    private static final double[] DEF_CELL_SCALE = {1.0, 4.0, 32.0};

    private static volatile TiePoiResidency instance;

    /** 分级半径 / 采样格（索引 0..levels-1）。 */
    private final double[] poiRadius;
    private final double[] poiCellScale;

    /** 真实驻留列数超出 LRU 上限时仅记一次 warn。 */
    private static volatile boolean CAP_WARNED = false;

    // ---- 桥引用（bind 注入，仅留 volatile 读；写加锁） ----
    private volatile TieTerrainDensityBridge bridge;

    // ---- 关注点（写加锁增量换区；读 volatile 无锁 O(1)） ----
    private volatile long poiX = 0;
    private volatile long poiZ = 0;
    // 哨兵远离原点，保证首次 updatePoi 必触发一次重扫（初始化驻留计数）。
    private long lastSweptX = Long.MIN_VALUE;
    private long lastSweptZ = Long.MIN_VALUE;

    /** 当前驻留列 LRU（访问序）；仅换区锁内读写。 */
    private final LinkedHashMap<Long, Boolean> residentLRU;
    private final Object lock = new Object();

    /** 当前驻留列计数（最近一次重扫/换区后的近似值）。 */
    private volatile long residentCount = 0;

    private TiePoiResidency() {
        poiRadius = DEF_RADIUS.clone();
        poiCellScale = DEF_CELL_SCALE.clone();
        residentLRU = new LinkedHashMap<>(512, 0.75f, true);
    }

    /** 单例（volatile 双检）。 */
    public static TiePoiResidency instance() {
        TiePoiResidency cur = instance;
        if (cur != null) {
            return cur;
        }
        synchronized (TiePoiResidency.class) {
            cur = instance;
            if (cur == null) {
                cur = new TiePoiResidency();
                instance = cur;
            }
            return cur;
        }
    }

    /**
     * 绑定就绪的桥（由 {@code SubterraDensity.tieBridgeForCompute} 装载成功后注入；
     * 幂等，参数为 null/不可用则忽略）。本实现的分级常量与桥导出一致，故绑定只记引用。
     */
    public void bind(TieTerrainDensityBridge b) {
        if (b == null || !b.available()) {
            return;
        }
        bridge = b; // volatile；activeLocally 读它做 block_active/y_to_block downcall
    }

    /** 是否启用 POI 调度（关闭 → 永远全驻留）。 */
    public boolean enabled() {
        return ENABLED;
    }

    /** 当前关注点水平坐标 X。 */
    public long poiX() {
        return poiX;
    }

    /** 当前关注点水平坐标 Z。 */
    public long poiZ() {
        return poiZ;
    }

    /**
     * 更新关注点水平坐标并增量换区：先换 volatile 中心（立即生效），
     * 位移达 {@code subterra.poi.sweepStep} 时重扫驻留 LRU（写锁内）。
     * 由后续接入方（玩家/实体位置）调用；本任务不接 MC 事件。
     */
    public void updatePoi(long x, long z) {
        poiX = x;
        poiZ = z;
        if (!ENABLED) {
            return;
        }
        synchronized (lock) {
            long dx = x - lastSweptX;
            long dz = z - lastSweptZ;
            if (absAtLeast(dx, SWEEP_STEP) || absAtLeast(dz, SWEEP_STEP)) {
                resweep();
                lastSweptX = x;
                lastSweptZ = z;
            }
        }
    }

    /** 块级驻留判定（不查 block_active；无 seed 的粗门）。越界块恒假。 */
    public boolean isResident(long x, long z, long y) {
        if (!ENABLED) {
            return true;
        }
        TieTerrainDensityBridge b = bridge;
        long blockY = (b != null) ? b.yToBlock(y) : defaultYToBlock(y);
        if (blockY < 0) {
            return false;
        }
        return tierLevel(x, z) >= 0;
    }

    /**
     * {@code (x,z,y)} 是否需要全精度密度：驻留 且（L0 免查 / L1·L2 {@code block_active==1}）。
     * 纯 O(1)，热路径零锁；DLL 缺失/失败 → 降级为全精度（不破坏回退语义）。越界 y → 粗占位。
     */
    public boolean activeLocally(long x, long y, long z, long seed) {
        if (!ENABLED) {
            return true;
        }
        TieTerrainDensityBridge b = bridge;
        long blockY = (b != null) ? b.yToBlock(y) : defaultYToBlock(y);
        if (blockY < 0) {
            return false;               // 越界 → 粗占位
        }
        int tier = tierLevel(x, z);
        if (tier < 0) {
            return false;               // L2 外 → 粗占位
        }
        if (tier == 0) {
            return true;                // L0：整列全精度
        }
        // L1 / L2：近地表/洞带的块才全精度，其余粗占位。
        if (b != null) {
            return b.blockActive(x, y, z, seed) != 0L;
        }
        return true;                    // 无桥：保守全精度
    }

    /** 当前驻留列的数量级计数（最近一次重扫/换区后的近似上界）。 */
    public long residentCount() {
        return residentCount;
    }

    /** 粗占位密度常量（非驻留列的近似）。 */
    public static double coarsePlaceholder() {
        return COARSE_PLACEHOLDER;
    }

    /** 三级水平等级：0/1/2 = 在 L0/L1/L2 半径内，-1 = 超出 L2。 */
    private int tierLevel(long x, long z) {
        double dx = x - poiX;
        double dz = z - poiZ;
        double d = Math.sqrt(dx * dx + dz * dz);
        if (d < poiRadius[0]) {
            return 0;
        }
        if (d < poiRadius[1]) {
            return 1;
        }
        if (d < poiRadius[2]) {
            return 2;
        }
        return -1;
    }

    /** 公式回退版 y→块（不依赖 DLL）。 */
    private long defaultYToBlock(long y) {
        if (y < DEF_Y_MIN || y >= DEF_Y_MAX) {
            return -1;
        }
        return (y - DEF_Y_MIN) / DEF_Y_BLOCK_SIZE;
    }

    /** 重扫当前 POI 水平的驻留列，重建 LRU 并施加 maxResident 上限。写锁内调用。 */
    private void resweep() {
        residentLRU.clear();
        double r0 = poiRadius[0], r1 = poiRadius[1], r2 = poiRadius[2];
        double s0 = poiCellScale[0], s1 = poiCellScale[1], s2 = poiCellScale[2];
        scanRing(r0, s0);          // L0 盘：列内全驻留
        scanRing(r0, r1, s1);      // L1 环：近地表洞带，1/4 采样
        scanRing(r1, r2, s2);      // L2 环：近地表洞带，1/32 采样
        residentCount = residentLRU.size();
        if (residentCount > MAX_RESIDENT && !CAP_WARNED) {
            CAP_WARNED = true;
            LOG.log(Level.WARNING, String.format(
                    "subterra poi scheduler: resident columns (~%d) exceed cap %d; LRU trimmed. "
                            + "raise -Dsubterra.poi.maxResident if far terrain appears hollow.",
                    residentCount, MAX_RESIDENT));
        }
    }

    /** 以当前 POI 为心、盘半径 {@code r} 且采样格 {@code scale} 扫描驻留列。 */
    private void scanRing(double r, double scale) {
        scanRing(0.0, r, scale);
    }

    /** 扫描 {@code [rLo, rHi)} 环形驻留列（相对当前 POI），步长 {@code scale}（列键进 LRU）。 */
    private void scanRing(double rLo, double rHi, double scale) {
        long half = (long) Math.ceil(rHi / scale);
        long base = -half * (long) scale;
        long top = half * (long) scale;
        for (long ix = base; ix <= top; ix += (long) scale) {
            for (long iz = base; iz <= top; iz += (long) scale) {
                double d = Math.sqrt((double) ix * ix + (double) iz * iz);
                if (d < rLo || d >= rHi) {
                    continue;
                }
                addResident(poiX + ix, poiZ + iz);
            }
        }
    }

    private void addResident(long wx, long wz) {
        long key = columnKey(wx, wz);
        residentLRU.put(key, Boolean.TRUE);
        if (residentLRU.size() > MAX_RESIDENT) {
            var it = residentLRU.entrySet().iterator();
            if (it.hasNext()) {
                it.next();
                it.remove();
            }
        }
    }

    /** 列键：{@code (x,z)} 压缩进一个 long（全列级，块身份除外）。 */
    private static long columnKey(long x, long z) {
        return (x << 32) ^ z;
    }

    /** 无条件地较 |v| ≥ bound（避免平方溢出）。 */
    private static boolean absAtLeast(long v, long bound) {
        return !(v > -bound && v < bound);
    }
}