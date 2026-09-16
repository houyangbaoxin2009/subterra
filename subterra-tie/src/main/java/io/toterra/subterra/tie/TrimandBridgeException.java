package io.toterra.subterra.tie;

/**
 * Trimand 桥异常（装载 / 符号解析 / 调用失败）。
 *
 * <p>只在调用方<b>显式</b>要求装载时抛出：{@link TrimandBridge#load(java.nio.file.Path)} 与
 * {@link TrimandBridge#coarseClimate(long, long, long)} 等已装载桥的调用。自动路径
 * {@link TrimandBridge#locate()} / {@link TrimandBridge#loadBundled()} 与
 * {@link TrimandBridge#report()} 一律把失败折叠为 {@code Optional.empty()} / 确定性
 * {@code skip} 行，不抛异常——缺 DLL 是确定性 skip，不是失败。
 *
 * <p>Trimand bridge exception (load / symbol resolution / call failure). Thrown only when the
 * caller <b>explicitly</b> asks for a load ({@link TrimandBridge#load(java.nio.file.Path)} and the
 * coarse-field calls on a loaded bridge). The automatic paths
 * ({@link TrimandBridge#locate()} / {@link TrimandBridge#loadBundled()} /
 * {@link TrimandBridge#report()}) fold every failure into {@code Optional.empty()} or a
 * deterministic {@code skip} line — a missing DLL is a deterministic skip, not a failure.
 */
public class TrimandBridgeException extends RuntimeException {

    public TrimandBridgeException(String message) {
        super(message);
    }

    public TrimandBridgeException(String message, Throwable cause) {
        super(message, cause);
    }
}
