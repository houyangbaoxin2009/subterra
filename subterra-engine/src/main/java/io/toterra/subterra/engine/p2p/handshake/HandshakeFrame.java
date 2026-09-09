package io.toterra.subterra.engine.p2p.handshake;

import io.toterra.subterra.engine.network.frame.FrameConst;
import io.toterra.subterra.engine.network.integrity.FrameV2;
import io.toterra.subterra.engine.network.integrity.FrameV2Result;
import io.toterra.subterra.engine.p2p.NodeId;

import java.util.Arrays;

/**
 * Wire codec for P2P handshake messages, carried over a tink v2 {@link FrameV2} frame with
 * the strong-integrity slot ({@link FrameConst#FLAG_STRONG}). Identity and public-key
 * binding material ride in the ext TLV (key = {@link FrameConst#EXT_EXT_META}); the challenge
 * and per-role proof ride in the payload.
 * <p>
 * 中文：P2P 握手消息的帧编解码器。握手消息经 tink v2 帧（强完整性槽 FLAG_STRONG）承载；身份与
 * 公钥绑定材料放在 ext TLV（key9 EXT_EXT_META），挑战与逐角色证明放在 payload。
 * <p>
 * Wire layout (payload): {@code magic("p2phs" 5B) | kind(1B) | challenge(16B) | proof(32B)}.
 * ext = one TLV(key9) over {@code nodeId(16B) | rawPubKey(32B)}. A frame whose payload or
 * strong-integrity slot is altered fails to decode (integrity mismatch → {@link HandshakeException}).
 * 中文：线格式（payload）：magic(5)+kind(1)+challenge(16)+proof(32)；ext=key9 TLV 承载 nodeId+公钥。
 * 任一处（payload / 强校验段）被改动即解码失败。
 */
public final class HandshakeFrame {

    /** Handshake protocol magic. */
    private static final byte[] MAGIC = {'n', 'p', '2', 's', 'S'};

    private static final int CHALLENGE_BYTES = 16;
    private static final int PROOF_BYTES = 32;
    private static final int PAYLOAD_BYTES = MAGIC.length + 1 + CHALLENGE_BYTES + PROOF_BYTES;

    /** Message kinds. */
    public static final byte KIND_HELLO = 1;
    public static final byte KIND_ACCEPT = 2;

    /**
     * A decoded handshake message.
     *
     * @param kind      {@link #KIND_HELLO} or {@link #KIND_ACCEPT}
     * @param challenge 16-byte challenge (initiator-derived)
     * @param nodeId    the declaring endpoint's NodeId
     * @param pubKey    the declaring endpoint's raw 32-byte X25519 public key
     * @param proof     32-byte per-role proof (all zero on HELLO)
     */
    public record Msg(byte kind, byte[] challenge, NodeId nodeId, byte[] pubKey, byte[] proof) {
    }

    private HandshakeFrame() {
    }

    /**
     * Encode a handshake message into a tink v2 frame with strong integrity. Forging a
     * message with altered payload / identity / proof here yields a valid-tag frame; the
     * binding verification at the receiving side is what rejects mismatched material.
     * 中文：把握手消息编码为强校验 tink v2 帧。此处可构造合法的篡改帧；接收侧的身份绑定校验
     * 才会拒绝不匹配的材料。
     */
    public static byte[] encode(Msg m) {
        if (m == null || m.challenge() == null || m.challenge().length != CHALLENGE_BYTES
                || m.nodeId() == null || m.pubKey() == null || m.pubKey().length != 32
                || m.proof() == null || m.proof().length != PROOF_BYTES) {
            throw new HandshakeException("invalid handshake message shape");
        }
        byte[] payload;
        {
            byte[] p = new byte[PAYLOAD_BYTES];
            System.arraycopy(MAGIC, 0, p, 0, MAGIC.length);
            p[MAGIC.length] = m.kind();
            System.arraycopy(m.challenge(), 0, p, MAGIC.length + 1, CHALLENGE_BYTES);
            System.arraycopy(m.proof(), 0, p, MAGIC.length + 1 + CHALLENGE_BYTES, PROOF_BYTES);
            payload = p;
        }
        byte[] ext = FrameV2.extTlv(FrameConst.EXT_EXT_META, HandshakeKeys.concat(m.nodeId().bytes(), m.pubKey()));
        return FrameV2.encode(payload, FrameConst.FLAG_STRONG, ext);
    }

    /**
     * Decode and integrity-verify a handshake frame. Unknown ext TLV keys are skipped
     * (frame-integrity already bound the full ext), but any payload/integrity tamper, wrong
     * magic, or unsupported kind is rejected with {@link HandshakeException}.
     * 中文：解码并校验握手帧。未知 ext TLV 键跳过；但任何 payload/校验段篡改、错误 magic、或不
     * 支持 kind 都会被拒绝。
     */
    public static Msg decode(byte[] frame) {
        if (frame == null) {
            throw new HandshakeException("frame is null");
        }
        FrameV2Result r = FrameV2.parse(frame, 0);
        if (!r.ok() || !r.integrityOk()) {
            throw new HandshakeException("handshake frame rejected (parse or integrity failed)");
        }
        byte[] payload = r.payload();
        if (payload.length != PAYLOAD_BYTES || !Arrays.equals(MAGIC, Arrays.copyOf(payload, MAGIC.length))) {
            throw new HandshakeException("handshake frame payload malformed");
        }
        byte kind = payload[MAGIC.length];
        if (kind != KIND_HELLO && kind != KIND_ACCEPT) {
            throw new HandshakeException("unknown handshake kind: " + kind);
        }
        byte[] challenge = Arrays.copyOfRange(payload, MAGIC.length + 1, MAGIC.length + 1 + CHALLENGE_BYTES);
        byte[] proof = Arrays.copyOfRange(payload,
                MAGIC.length + 1 + CHALLENGE_BYTES,
                MAGIC.length + 1 + CHALLENGE_BYTES + PROOF_BYTES);

        // read ext TLV key=EXT_EXT_META, skipping unknown keys
        byte[] idPub = null;
        byte[] ext = r.ext();
        int p = 0;
        while (p + 4 <= ext.length) {
            int key = ((ext[p] & 0xFF) << 8) | (ext[p + 1] & 0xFF);
            int len = ((ext[p + 2] & 0xFF) << 8) | (ext[p + 3] & 0xFF);
            if (p + 4 + len > ext.length) {
                throw new HandshakeException("handshake ext TLV malformed");
            }
            if (key == FrameConst.EXT_EXT_META && idPub == null) {
                idPub = Arrays.copyOfRange(ext, p + 4, p + 4 + len);
            }
            p += 4 + len;
        }
        if (idPub == null || idPub.length != NodeId.BYTES + 32) {
            throw new HandshakeException("handshake identity/public-key material missing");
        }
        byte[] nodeBytes = Arrays.copyOf(idPub, NodeId.BYTES);
        byte[] pubKey = Arrays.copyOfRange(idPub, NodeId.BYTES, NodeId.BYTES + 32);
        return new Msg(kind, challenge, NodeId.of(nodeBytes), pubKey, proof);
    }
}