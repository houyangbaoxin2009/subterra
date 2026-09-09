package io.toterra.subterra.engine.p2p.handshake;

import io.toterra.subterra.engine.network.crypto.SecureChannel;
import io.toterra.subterra.engine.network.frame.FrameConst;
import io.toterra.subterra.engine.p2p.NodeAddr;
import io.toterra.subterra.engine.p2p.NodeId;

import java.security.KeyPair;

/**
 * One endpoint of a deterministic two-message P2P handshake (p.2.5.3) over tink v2 frames:
 * <pre>
 *   initiator.begin()        -> HELLO frame (carries initiator NodeId + X25519 pub + challenge)
 *   responder.accept(HELLO)  -> binds peer NodeId, derives the identity-bound secret,
 *                               returns ACCEPT frame and yields the responder HandshakeResult
 *   initiator.complete(ACCEPT) -> verifies the responder proof, yields the initiator HandshakeResult
 * </pre>
 * Both results carry an identical {@link HandshakeResult#handshakeId()} and an identical
 * {@link SecureChannel} (same AES key + sequence start), since the shared secret is bound to
 * both NodeIds and both public keys ({@link HandshakeKeys#bindSecret}).
 * <p>
 * 中文：确定性两消息 P2P 握手的一端（p.2.5.3），经 tink v2 帧进行：
 * 发起方 begin 产 HELLO（携带本方 NodeId+公钥+挑战）；响应方 accept 绑定对端 NodeId、派生
 * 身份绑定秘密、返回 ACCEPT 帧并获得响应方结果；发起方 complete 校验响应方证明并获得发起方
 * 结果。两端 handshakeId 与 SecureChannel（相同 AES 密钥与起始序列）完全一致。
 * <p>
 * Tamper / ordering / replay rejection: any frame whose payload or strong-integrity slot was
 * altered, any wrong-kind or out-of-order frame, any reuse of an already-consumed handshake,
 * or any proof that does not bind to the both-Node identity fails with {@link HandshakeException}.
 * 中文：篡改 / 乱序 / 重放拒绝：payload 或强校验段被改动、错 kind 或乱序帧、已消费握手的重放、
 * 或证明未能绑定双方身份，一律抛 HandshakeException。
 * <p>
 * Pure JDK — no Minecraft coupling. Frames are produced with the strong-integrity slot
 * ({@link FrameConst#FLAG_STRONG}); the session channel is built on the p.2.4.5
 * {@link SecureChannel} with a deterministic initial sequence counter.
 */
public final class HandshakePeer {

    private final NodeId localId;
    private final NodeAddr localAddr;
    private final KeyPair keyPair;
    private final boolean initiator;
    private final byte[] localPubRaw;

    // dynamic handshake state
    private byte[] challenge;
    private HandshakeResult result;
    private int phase; // 0 idle, 1 in-flight, 2 done

    /**
     * Create one handshake endpoint.
     *
     * 中文：创建一个握手端点。
     *
     * @param localId    our node identity (p.2.5.2)
     * @param localAddr  our node address (reporting only)
     * @param keyPair    our deterministic X25519 key pair
     * @param initiator  true if this node sends the HELLO
     */
    public HandshakePeer(NodeId localId, NodeAddr localAddr, KeyPair keyPair, boolean initiator) {
        if (localId == null || keyPair == null) {
            throw new NullPointerException("localId and keyPair must not be null");
        }
        this.localId = localId;
        this.localAddr = localAddr;
        this.keyPair = keyPair;
        this.initiator = initiator;
        this.localPubRaw = HandshakeKeys.rawPub(keyPair.getPublic());
    }

    public NodeId localId() {
        return localId;
    }

    public NodeAddr localAddr() {
        return localAddr;
    }

    public boolean isInitiator() {
        return initiator;
    }

    /** The established result, or {@code null} before completion. 中文：已建立的结果（未完成前为 null）。 */
    public HandshakeResult result() {
        return result;
    }

    /**
     * Initiator-only: produce the HELLO frame that opens the handshake.
     *  中文：发起方专用：产出开启握手的 HELLO 帧。
     */
    public byte[] begin() {
        if (!initiator) {
            throw new HandshakeException("responder cannot begin()");
        }
        if (phase != 0) {
            throw new HandshakeException("handshake already in progress or done");
        }
        challenge = HandshakeKeys.challenge(localId, localPubRaw);
        phase = 1;
        return HandshakeFrame.encode(new HandshakeFrame.Msg(
                HandshakeFrame.KIND_HELLO, challenge, localId, localPubRaw, new byte[32]));
    }

    /**
     * Responder-only: process the peer HELLO, bind the peer NodeId, derive the identity-bound
     * secret, and return the ACCEPT frame. The responder's {@link HandshakeResult} is available
     * via {@link #result()}.
     * 中文：响应方专用：处理对端 HELLO、绑定对端 NodeId、派生身份绑定秘密，返回 ACCEPT 帧；
     * 响应方结果经 result() 获取。
     */
    public byte[] accept(byte[] helloFrame) {
        if (initiator) {
            throw new HandshakeException("initiator cannot accept()");
        }
        if (phase != 0) {
            throw new HandshakeException("handshake already in progress or done (replay/wrong-order)");
        }
        HandshakeFrame.Msg hello = HandshakeFrame.decode(helloFrame);
        if (hello.kind() != HandshakeFrame.KIND_HELLO) {
            throw new HandshakeException("expected HELLO, got different handshake kind");
        }
        NodeId peerId = hello.nodeId();
        byte[] peerPub = hello.pubKey();
        // The challenge is derived from the initiator identity; reject a mismatched claim.
        if (!java.util.Arrays.equals(HandshakeKeys.challenge(peerId, peerPub), hello.challenge())) {
            throw new HandshakeException("challenge does not match declared initiator identity");
        }
        byte[] sharedX = SecureChannel.sharedSecret(keyPair.getPrivate(), HandshakeKeys.publicFromRaw(peerPub));
        byte[] boundSecret = HandshakeKeys.bindSecret(localId, peerId, localPubRaw, peerPub, sharedX);
        byte[] respProof = HandshakeKeys.respProof(hello.challenge(), boundSecret);
        long seq = HandshakeKeys.seq0(boundSecret);
        result = new HandshakeResult(
                HandshakeKeys.handshakeId(localId, peerId, boundSecret),
                peerId,
                false,
                boundSecret,
                SecureChannel.enabled(boundSecret, seq),
                seq,
                hello.challenge());
        phase = 2;
        return HandshakeFrame.encode(new HandshakeFrame.Msg(
                HandshakeFrame.KIND_ACCEPT, hello.challenge(), localId, localPubRaw, respProof));
    }

    /**
     * Initiator-only: verify the ACCEPT frame (its responder proof must bind to the both-Node
     * identity and the X25519 shared secret), then finalise and return the initiator's
     * {@link HandshakeResult}.
     * 中文：发起方专用：校验 ACCEPT 帧（响应方证明必须绑定双方身份与共享秘密），然后定稿并返
     * 回发起方的 HandshakeResult。
     */
    public HandshakeResult complete(byte[] acceptFrame) {
        if (!initiator) {
            throw new HandshakeException("responder cannot complete()");
        }
        if (phase != 1) {
            throw new HandshakeException("complete() out of order (begin() first, complete() once)");
        }
        HandshakeFrame.Msg accept = HandshakeFrame.decode(acceptFrame);
        if (accept.kind() != HandshakeFrame.KIND_ACCEPT) {
            throw new HandshakeException("expected ACCEPT, got different handshake kind");
        }
        if (!java.util.Arrays.equals(HandshakeKeys.challenge(localId, localPubRaw), accept.challenge())) {
            throw new HandshakeException("challenge does not match this handshake");
        }
        NodeId peerId = accept.nodeId();
        byte[] peerPub = accept.pubKey();
        byte[] sharedX = SecureChannel.sharedSecret(keyPair.getPrivate(), HandshakeKeys.publicFromRaw(peerPub));
        byte[] boundSecret = HandshakeKeys.bindSecret(localId, peerId, localPubRaw, peerPub, sharedX);
        byte[] expected = HandshakeKeys.respProof(accept.challenge(), boundSecret);
        if (!java.util.Arrays.equals(expected, accept.proof())) {
            throw new HandshakeException("responder proof does not bind to the both-Node identity/keys");
        }
        long seq = HandshakeKeys.seq0(boundSecret);
        result = new HandshakeResult(
                HandshakeKeys.handshakeId(localId, peerId, boundSecret),
                peerId,
                true,
                boundSecret,
                SecureChannel.enabled(boundSecret, seq),
                seq,
                accept.challenge());
        phase = 2;
        return result;
    }
}