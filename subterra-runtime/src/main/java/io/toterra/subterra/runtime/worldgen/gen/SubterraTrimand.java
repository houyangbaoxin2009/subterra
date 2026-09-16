package io.toterra.subterra.runtime.worldgen.gen;

import java.util.Objects;

import io.toterra.subterra.Subterra;
import io.toterra.subterra.engine.worldgen.assembly.TrimandAssembly;
import io.toterra.subterra.tie.TrimandBridge;

/**
 * p.2.35 — Trimand 三场在 MC 层的消费壳：把 tie 侧 Trimand DLL 的三个宏观粗场（气候 / 海拔 /
 * 山带）接到密度装配的接缝上，缺省<b>完全不触碰 DLL</b>。
 *
 * <p>接线模型（确定性、门控）：
 * <ul>
 *   <li>启动快照：{@code SubterraWorldgen.onServerStarting} 经
 *       {@code AssemblySeam.of(bootRules)} 解析后调用 {@link #setSnapshot(TrimandAssembly.Params)}
 *       一次性写入（缺省 {@link TrimandAssembly#DEFAULT_PARAMS} = 关闭）。</li>
 *   <li>惰性装载：仅当快照非恒等（启用且权重 &gt; 0）时才经 {@link TrimandBridge#locate()}
 *       装载一次；失败即以 {@link #active} 落定为 false（单次尝试，不每 chunk 重试）。</li>
 *   <li>热路径 {@link #apply}：非激活 → 逐位直通原始密度（确定性 skip，不改变真值）；激活 →
 *       读三场并按 {@link TrimandAssembly#apply} 融合。任何异常都把壳降级为直通并只记一条 warn。</li>
 * </ul>
 *
 * <p>因此缺 DLL / 缺符号 / 调用失败一律等价于「本壳不存在」：密度真值与默认预设逐位不变。
 * <p>
 * p.2.35 — the MC-layer Trimand three-field consumption shell: wires the three macroscopic coarse
 * fields (climate / elevation / mountain) from the tie-side Trimand DLL onto the density assembly
 * seam, and by default <b>never touches the DLL at all</b>.
 *
 * <p>Wiring model (deterministic, gated): the boot snapshot is written once from
 * {@code SubterraWorldgen.onServerStarting} via {@code AssemblySeam.of(bootRules)}; the bridge is
 * loaded lazily (and at most once) only when the snapshot is non-identity; the hot path
 * {@link #apply} passes the raw density through bit-for-bit while inactive (deterministic skip,
 * truth unchanged) and otherwise reads the three fields and blends them via
 * {@link TrimandAssembly#apply}. Any failure degrades the shell to pass-through with a single
 * warning. A missing DLL / missing symbol / failing call is therefore equivalent to "this shell does
 * not exist": the density truth and the default preset stay bit-identical.
 */
public final class SubterraTrimand {

    /** 探针 marker 前缀（与 {@link TrimandBridge#MARKER} 同字面量）。 /
     *  The probe marker prefix (same literal as {@link TrimandBridge#MARKER}). */
    public static final String MARKER = TrimandBridge.MARKER;

    private static volatile TrimandAssembly.Params snapshot = TrimandAssembly.DEFAULT_PARAMS;
    private static volatile TrimandBridge bridge;
    private static volatile boolean bridgeFailed = false;
    private static volatile boolean active = false;
    private static final Object LOAD_LOCK = new Object();
    /** 热路径降级只记一次 warn。 / The hot-path degradation is warned only once. */
    private static volatile boolean degradedLogged = false;

    private SubterraTrimand() {
    }

    /**
     * 写入启动快照并（必要时）尝试装载 DLL。缺省参数为恒等 → 立即落定为非激活，DLL 一次也不碰。 /
     *  Writes the boot snapshot and (only if needed) attempts the DLL load. The default params are
     *  identity → the shell settles as inactive immediately and the DLL is never touched.
     */
    public static void setSnapshot(TrimandAssembly.Params params) {
        Objects.requireNonNull(params, "params must not be null");
        snapshot = params;
        if (TrimandAssembly.isIdentity(params)) {
            active = false;
            return;
        }
        active = bridge() != null;
    }

    /** 当前快照（恒非 null）。 / The current snapshot (never null). */
    public static TrimandAssembly.Params snapshot() {
        return snapshot;
    }

    /** 本壳当前是否真实接管密度（快照非恒等且桥就绪）。 /
     *  Whether the shell currently takes over the density (non-identity snapshot and a ready
     *  bridge). */
    public static boolean active() {
        return active;
    }

    /**
     * 确定性状态行（boot marker / 探针断言面）：{@code ok (dll=..., ...)}、{@code skip (disabled)}、
     * {@code skip (no dll)}、{@code mismatch (...)}。禁时序、禁随机。 /
     *  The deterministic status line (boot marker / probe assertion surface).
     */
    public static String statusLine() {
        TrimandAssembly.Params p = snapshot;
        if (TrimandAssembly.isIdentity(p)) {
            return MARKER + " skip (disabled; " + TrimandAssembly.render(p) + ")";
        }
        TrimandBridge b = bridge();
        if (b == null) {
            return MARKER + " skip (no dll; " + TrimandAssembly.render(p) + ")";
        }
        return b.status();
    }

    /**
     * 密度接缝：把 Trimand 三场提示施加到已装配密度上。非激活 → 逐位直通（确定性 skip）。 /
     *  The density seam: applies the Trimand hint to an assembled density. Inactive → pass-through
     *  bit-for-bit (deterministic skip).
     *
     * @param assembled 已经过密度偏移/缩放的密度值 / the density already offset/scaled
     * @param x         方块 x / block x
     * @param z         方块 z / block z
     * @param seed      世界种子 / the world seed
     */
    public static double apply(double assembled, long x, long z, long seed) {
        if (!active) {
            return assembled;
        }
        TrimandBridge b = bridge;
        if (b == null) {
            active = false; // 桥在上次降级中被释放：确定性回到直通
            return assembled;
        }
        try {
            TrimandAssembly.Fields fields = new TrimandAssembly.Fields(
                    b.coarseClimate(x, z, seed),
                    b.coarseElev(x, z, seed),
                    b.coarseMountain(x, z, seed));
            return TrimandAssembly.apply(snapshot, assembled, fields);
        } catch (RuntimeException e) {
            active = false;
            if (!degradedLogged) {
                degradedLogged = true;
                Subterra.LOGGER.warn("{} degrade (coarse-field call failed; density passes through "
                        + "unchanged for the rest of this JVM)", MARKER, e);
            }
            return assembled;
        }
    }

    /**
     * 单次尝试惰性装载（失败缓存，不每 chunk 重试）。 / Single-flight lazy load (failure cached, no
     * per-chunk retry).
     */
    private static TrimandBridge bridge() {
        TrimandBridge current = bridge;
        if (current != null) {
            return current;
        }
        if (bridgeFailed) {
            return null;
        }
        synchronized (LOAD_LOCK) {
            current = bridge;
            if (current != null) {
                return current;
            }
            if (bridgeFailed) {
                return null;
            }
            try {
                current = TrimandBridge.locate().orElse(null);
            } catch (Throwable t) {
                current = null;
            }
            if (current == null) {
                bridgeFailed = true;
            } else {
                bridge = current;
            }
            return current;
        }
    }
}
