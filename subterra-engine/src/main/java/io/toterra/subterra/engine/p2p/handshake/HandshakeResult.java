package io.toterra.subterra.engine.p2p.handshake;

import io.toterra.subterra.engine.network.crypto.SecureChannel;
import io.toterra.subterra.engine.p2p.NodeId;

/**
 * Final complete P2P handshake result (p.2.5.3). Carries:
 * <ul>
 *   <li>the handshake identifier, deterministically derived from the bound secret and the
 *       two NodeIds (identical on both ends);</li>
 *   <li>the remote peer's authenticated NodeId and the handshake challenge;</li>
 *   <li>whether we are the handshake initiator (sender of HELLO) or the responder;</li>
 *   <li>the identity- and key-bound 32-byte shared {@code secret} (fed through the p.2.4.5
 *       KDF by {@link SecureChannel} into the AES-256-GCM key);</li>
 *   <li>a ready {@link SecureChannel} for session frames and its deterministic initial
 *       sequence counter.</li>
 * </ul>
 * 中文：完整 P2P 握手结果（p.2.5.3）。携带：确定性握手标识符（两端一致）；远端认证 NodeId 与
 * 握手挑战；本方是否为发起方；绑定双方身份的 32 字节共享 secret（由 SecureChannel 经 KDF 派生
 * AES-256-GCM 密钥）；以及就绪的会话 SecureChannel 与其确定性起始序列计数。
 *
 * @param handshakeId   deterministic handshake identifier (hex, identical on both ends)
 * @param peerId        authenticated remote NodeId
 * @param isInitiator   true if this node initiated the handshake (HELLO sender)
 * @param secret        the 32-byte identity- and key-bound shared secret
 * @param secureChannel the established encryption channel for session frames
 * @param initialSeq    the initial sequence counter for the channel
 * @param challenge     the 16-byte handshake challenge (initiator-derived, identical on both ends)
 */
public record HandshakeResult(String handshakeId, NodeId peerId, boolean isInitiator,
                              byte[] secret, SecureChannel secureChannel,
                              long initialSeq, byte[] challenge) {
}