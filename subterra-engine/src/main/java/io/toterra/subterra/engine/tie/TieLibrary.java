package io.toterra.subterra.engine.tie;

import java.lang.foreign.Arena;
import java.lang.foreign.SymbolLookup;
import java.nio.file.Path;
import java.util.Optional;

/**
 * tie bridge 库入口（p.2.1）：加载 tiec 编译的动态库并按符号名解析导出函数。
 *
 * <p>ABI 约定（与 tie-main/examples/lib_math_dyn 一致）：导出面 = 顶层函数或命名空间
 * {@code pub func}；符号名 = 命名空间全名转 {@code $}（{@code tiefib::add} →
 * {@code "tiefib$add"}）；边界仅标量（i64/f64/bool/trit/char）与 string；私有函数不导出。
 *
 * <p>纯 JDK：FFM（java.lang.foreign）无需 MC 类路径；Arena 管理宿主库生命周期，{@link #close()}
 * 释放。运行期需 {@code --enable-native-access}（默认 ALL-UNNAMED 由调用方决定）。
 */
public final class TieLibrary implements AutoCloseable {

    private final Arena arena;
    private final SymbolLookup lookup;
    private final Path source;

    private TieLibrary(Arena arena, SymbolLookup lookup, Path source) {
        this.arena = arena;
        this.lookup = lookup;
        this.source = source;
    }

    /** 加载 tiec 编译的动态库（.dll/.so）。失败抛 {@link TieBridgeException}。 */
    public static TieLibrary load(Path dll) {
        Arena arena = Arena.ofConfined();
        try {
            return new TieLibrary(arena, SymbolLookup.libraryLookup(dll, arena), dll);
        } catch (Throwable t) {
            arena.close();
            throw new TieBridgeException("tie 动态库加载失败: " + dll, t);
        }
    }

    /** 按符号解析导出函数；未导出/加载失败返回 empty。 */
    public Optional<TieFunction> find(String symbol) {
        try {
            return lookup.find(symbol).map(addr -> new TieFunction(symbol, addr, this));
        } catch (Throwable t) {
            return Optional.empty();
        }
    }

    /** 便捷判断：符号是否存在（导出面）。 */
    public boolean contains(String symbol) {
        return find(symbol).isPresent();
    }

    public Path source() {
        return source;
    }

    /** 释放宿主库句柄；其后调用该库导出的函数将失败。 */
    @Override
    public void close() {
        arena.close();
    }
}