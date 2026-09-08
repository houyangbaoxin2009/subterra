package io.toterra.subterra.engine.tie;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;

/**
 * tie 动态库导出的一个函数（p.2.1 tie bridge）。基于 FFM downcall 句柄封装。
 *
 * <p>提供定形便捷调用（i64 0/1/2 参、f64 1 参）覆盖当前边界标量集；任意形态经
 * {@link #handle(FunctionDescriptor)} 自取。句柄按形态惰性构建并缓存。
 */
public final class TieFunction {

    private final String symbol;
    private final MemorySegment address;
    private final TieLibrary library;

    private MethodHandle h0;
    private MethodHandle h1i;
    private MethodHandle h2i;
    private MethodHandle h1d;

    TieFunction(String symbol, MemorySegment address, TieLibrary library) {
        this.symbol = symbol;
        this.address = address;
        this.library = library;
    }

    public String symbol() {
        return symbol;
    }

    public TieLibrary library() {
        return library;
    }

    /** 通用形态：调用方自建 FunctionDescriptor，自行承担签名匹配责任。 */
    public MethodHandle handle(FunctionDescriptor descriptor) {
        return Linker.nativeLinker().downcallHandle(address, descriptor);
    }

    /** () -> i64 */
    public long invoke0() {
        try {
            return (long) handle0().invokeExact();
        } catch (Throwable t) {
            throw new TieBridgeException("tie 调用失败: " + symbol + "()", t);
        }
    }

    /** (i64) -> i64 */
    public long invokeI64(long a) {
        try {
            return (long) handle1i().invokeExact(a);
        } catch (Throwable t) {
            throw new TieBridgeException("tie 调用失败: " + symbol + "(i64)", t);
        }
    }

    /** (i64, i64) -> i64 */
    public long invokeI64I64(long a, long b) {
        try {
            return (long) handle2i().invokeExact(a, b);
        } catch (Throwable t) {
            throw new TieBridgeException("tie 调用失败: " + symbol + "(i64,i64)", t);
        }
    }

    /** (f64) -> f64 */
    public double invokeF64(double d) {
        try {
            return (double) handle1d().invokeExact(d);
        } catch (Throwable t) {
            throw new TieBridgeException("tie 调用失败: " + symbol + "(f64)", t);
        }
    }

    private MethodHandle handle0() {
        if (h0 == null) {
            h0 = Linker.nativeLinker().downcallHandle(address, FunctionDescriptor.of(ValueLayout.JAVA_LONG));
        }
        return h0;
    }

    private MethodHandle handle1i() {
        if (h1i == null) {
            h1i = Linker.nativeLinker().downcallHandle(address,
                    FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG));
        }
        return h1i;
    }

    private MethodHandle handle2i() {
        if (h2i == null) {
            h2i = Linker.nativeLinker().downcallHandle(address,
                    FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG));
        }
        return h2i;
    }

    private MethodHandle handle1d() {
        if (h1d == null) {
            h1d = Linker.nativeLinker().downcallHandle(address,
                    FunctionDescriptor.of(ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE));
        }
        return h1d;
    }
}