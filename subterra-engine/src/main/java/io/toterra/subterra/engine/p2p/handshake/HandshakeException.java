package io.toterra.subterra.engine.p2p.handshake;

/**
 * Deterministic P2P handshake failure (p.2.5.3). Thrown when a handshake step cannot
 * proceed: a tampered / malformed / incorrectly-ordered / replayed frame, a mismatched
 * identity-binding proof, or an invalid phase transition.
 * <p>
 * 中文：确定性 P2P 握手的失败信号（p.2.5.3）。当握手任一步骤无法继续时抛出：帧被篡改 /
 * 格式损坏 / 乱序 / 重放、身份绑定证明不匹配，或非法阶段转移。
 * <p>
 * Pure JDK — no Minecraft coupling.
 */
public final class HandshakeException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public HandshakeException(String message) {
        super(message);
    }

    public HandshakeException(String message, Throwable cause) {
        super(message, cause);
    }
}