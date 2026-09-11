package io.toterra.subterra.engine.render.lod;

import io.toterra.subterra.engine.tie.TieBridgeException;
import io.toterra.subterra.engine.tie.TieFunction;
import io.toterra.subterra.engine.tie.TieLibrary;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Mounts the LOD pipeline's pure-integer arithmetic hot spots onto tie (p.2.28.2). When a
 * compiled {@code subterra_lod_pipeline.dll} is present the accelerator loads it via
 * {@link io.toterra.subterra.engine.tie.TieLibrary} and invokes the tie primitives
 * {@code lod$profile_top(i64,i64,i64)->i64} and {@code lod$merge_corner(i64,i64,i64,i64)->i64}
 * over the p.2.1 engine.tie FFM bridge. When the library is absent or fails to load the
 * accelerator is a <b>deterministic skip</b>: it reports itself inactive and every
 * {@code callPrimitive}-style method returns {@link Optional#empty()} without throwing — the
 * pipeline then falls back to the JDK golden kernels with byte-identical output.
 *
 * <p><b>Measured DLL boundary (p.2.28.2 conclusion):</b> tiec {@code --shared} exports scalars
 * (i64/f64/bool/trit/char) and string only; private funcs are not exported; custom pointer /
 * byte-buffer FunctionDescriptor pass-through is <em>not</em> supported today. Hence the LOD
 * hot spots are sunk as scalar i64 primitives (this class), and the zd byte-passthrough ABI is
 * explicitly deferred to a future tie toolchain extension — not gambled on.
 *
 * <p>把 LOD 管线纯整数算术热点挂到 tie（p.2.28.2）。当编译好的 {@code subterra_lod_pipeline.dll} 在场时，
 * 本加速器经 {@link io.toterra.subterra.engine.tie.TieLibrary} 装载它，并经 p.2.1 engine.tie FFM 桥调用
 * tie 原语 {@code lod$profile_top(i64,i64,i64)->i64} 与 {@code lod$merge_corner(i64,i64,i64,i64)->i64}。
 * 当库缺省或装载失败时，加速器为<b>确定性 skip</b>：报告自身非激活，一切 {@code callPrimitive}-型方法返回
 * {@link Optional#empty()} 且<em>不抛错</em>——管线随后退化到 JDK 金样内核，输出字节一致。
 *
 * <p><b>实测 DLL 边界（p.2.28.2 结论）：</b>tiec {@code --shared} 仅导出标量（i64/f64/bool/trit/char）
 * 与 string；私有函数不导出；自定义指针/字节缓冲 FunctionDescriptor 直传今天<em>不受支持</em>。故 LOD 热点以
 * 标量 i64 原语下沉（本类），zd 字节直通 ABI 被明确推迟到未来的 tie 工具链扩展——不作赌注。
 */
public final class LodTieAccelerator implements AutoCloseable {

    /** System property key for resolving the tie DLL path in fixed order. / 固定序解析 tie DLL 路径的系统属性键。 */
    public static final String LIB_PROPERTY = "subterra.tie.lib";

    private static final String SYM_PROFILE_TOP = "lod$profile_top";
    private static final String SYM_MERGE_CORNER = "lod$merge_corner";

    private final TieLibrary library;
    private final boolean active;
    private final String reason;

    private LodTieAccelerator(TieLibrary library, boolean active, String reason) {
        this.library = library;
        this.active = active;
        this.reason = reason;
    }

    /**
     * Resolves the accelerator from the {@code subterra.tie.lib} system property. If the property
     * is absent/blank, or the library fails to load, or a required symbol is missing, the
     * accelerator is a deterministic skip (never throws). / 由 {@code subterra.tie.lib} 系统属性解析
     * 加速器。属性缺失/为空，或库装载失败，或必需符号缺失时，加速器为确定性 skip（绝不抛错）。
     */
    public static LodTieAccelerator fromProperties() {
        String pathRaw = System.getProperty(LIB_PROPERTY);
        if (pathRaw == null || pathRaw.isBlank()) {
            return skipped("system property " + LIB_PROPERTY + " not set");
        }
        Path path = Path.of(pathRaw.trim());
        if (!Files.isRegularFile(path)) {
            return skipped("tie dll not found at " + pathRaw);
        }
        return fromDll(path);
    }

    /**
     * Creates an accelerator from an explicit DLL path; malformed or unloadable libraries degrade
     * to a deterministic skip. / 从显式 DLL 路径构造加速器；畸形或不可装载的库退化为确定性 skip。
     */
    public static LodTieAccelerator fromDll(Path dll) {
        TieLibrary lib;
        try {
            lib = TieLibrary.load(dll);
        } catch (Throwable t) {
            return skipped("tie library load failed: " + t.getMessage());
        }
        boolean hasProfile = lib.contains(SYM_PROFILE_TOP);
        boolean hasCorner = lib.contains(SYM_MERGE_CORNER);
        if (!hasProfile || !hasCorner) {
            String reason = "missing symbols profile_top=" + hasProfile
                    + " merge_corner=" + hasCorner;
            try {
                lib.close();
            } catch (Throwable ignored) {
                // best-effort
            }
            return skipped(reason);
        }
        return new LodTieAccelerator(lib, true, "active via " + dll);
    }

    private static LodTieAccelerator skipped(String reason) {
        return new LodTieAccelerator(null, false, reason);
    }

    /** A deterministic no-op (skipped) accelerator, for JDK-only pipelines. /
     *  确定性 no-op（skip）加速器，供仅 JDK 管线使用。 */
    public static LodTieAccelerator skippedNoOp() {
        return skipped("no tie library");
    }

    /** Whether the tie primitives are usable. / tie 原语是否可用。 */
    public boolean active() {
        return active;
    }

    /** Whether this is a deterministic skip (no tie library in effect). / 是否为确定性 skip（无 tie 库生效）。 */
    public boolean skipped() {
        return !active;
    }

    /** Human-readable reason for skip / active source. / skip/激活来源的可读说明。 */
    public String reason() {
        return reason;
    }

    /**
     * The top-surface sample primitive: {@code lods$profile_top(seed, x, z)}. Empty when skip.
     * / 顶表面采样原语：{@code lods$profile_top(seed, x, z)}。skip 时为 empty。
     */
    public Optional<Long> profileTop(long seed, long x, long z) {
        return callAutoclosed(SYM_PROFILE_TOP, seed, x, z);
    }

    /**
     * The 2×2 corner merge primitive: {@code lods$merge_corner(a, b, c, d)}. Empty when skip.
     * / 2×2 corner 合并原语：{@code lods$merge_corner(a, b, c, d)}。skip 时为 empty。
     */
    public Optional<Long> mergeCorner(long a, long b, long c, long d) {
        return callAutoclosed(SYM_MERGE_CORNER, a, b, c, d);
    }

    /**
     * General scalar primitive call up to N i64 args: {@code args} are downcalled through the
     * matching {@code FunctionDescriptor}; empty when skipped. / 通用 N 参 i64 标量原语调用：{@code args}
     * 经匹配的 {@code FunctionDescriptor} 下行调用；skip 时为 empty。
     */
    public Optional<Long> callPrimitive(String symbol, long... args) {
        if (!active || library == null) {
            return Optional.empty();
        }
        Optional<TieFunction> fn = library.find(symbol);
        if (fn.isEmpty()) {
            return Optional.empty();
        }
        try {
            ValueLayout[] params = new ValueLayout[args.length];
            for (int i = 0; i < args.length; i++) {
                params[i] = ValueLayout.JAVA_LONG;
            }
            MethodHandle mh = fn.get().handle(FunctionDescriptor.of(ValueLayout.JAVA_LONG, params));
            Object[] boxed = new Object[args.length];
            for (int i = 0; i < args.length; i++) {
                boxed[i] = args[i];
            }
            return Optional.of((Long) mh.invokeWithArguments(boxed));
        } catch (Throwable t) {
            throw new TieBridgeException("tie scalar call failed for " + symbol, t);
        }
    }

    private Optional<Long> callAutoclosed(String symbol, long... args) {
        if (!active || library == null) {
            return Optional.empty();
        }
        return callPrimitive(symbol, args);
    }

    /** Releases the host library handle if present (idempotent). / 释放宿主库句柄（若有；幂等）。 */
    @Override
    public void close() {
        if (library != null) {
            try {
                library.close();
            } catch (Throwable ignored) {
                // best-effort
            }
        }
    }
}