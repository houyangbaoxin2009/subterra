package io.toterra.subterra.engine.network.crypto;

/**
 * Unchecked exception for SecureChannel setup and encrypt/decrypt failures.
 * <p>
 * 中文：SecureChannel 初始化或加密/解密失败时抛出的非受检异常。
 * Checked is avoided because JCA errors are fatal (provider must exist) and
 * should not be handled in the transport layer.
 * <p>
 * 中文：Checked 异常被避免，因为 JCA 错误是致命的（提供者必须存在），传输层不应处理。
 */
public final class SecureChannelException extends RuntimeException {
    public SecureChannelException(String message) {
        super(message);
    }

    public SecureChannelException(String message, Throwable cause) {
        super(message, cause);
    }
}
