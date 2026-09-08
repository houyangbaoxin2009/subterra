package io.toterra.subterra.engine.tie;

/**
 * tie 动态库加载/调用失败（p.2.1 tie bridge）。运行时异常，向上传递 FFM/C 调用链错误。
 */
public final class TieBridgeException extends RuntimeException {

    public TieBridgeException(String message) {
        super(message);
    }

    public TieBridgeException(String message, Throwable cause) {
        super(message, cause);
    }
}