package io.toterra.subterra.engine.p2p.handshake;

import io.toterra.subterra.engine.network.crypto.SecureChannel;
import io.toterra.subterra.engine.p2p.NodeId;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Arrays;
import java.util.HexFormat;

/**
 * Deterministic handshake key material and identity-binding derivation (p.2.5.3).
 * Pure JDK — everything derives reproducibly from a fixed seed so a given pair of
 * seeds produces an exactly reproducible handshake outcome.
 * <p>
 * 中文：确定性握手密钥材质与身份绑定派生（p.2.5.3）。纯 JDK —— 一切由固定种子可复现派生，
 * 因而同一对种子下的握手结论完全可复现。
 * <p>
 * <ul>
 *   <li>{@link #nodeIdForSeed} — 16-byte {@link NodeId} from a seed.</li>
 *   <li>{@link #keyPairForSeed} — a matching X25519 key pair from the same seed (the
 *       public component is the true base-point multiple of the private scalar, so key
 *       agreement with any peer reproduces the same secret on both sides).</li>
 *   <li>{@link #bindSecret} — the final channel secret, bound to <em>both</em> endpoint
 *       {@link NodeId}s <em>and</em> both public keys plus the raw X25519 shared secret.
 *       Changing either endpoint's NodeId or key pair changes the secret (identity
 *       binding); the input is canonical so initiator and responder agree.</li>
 *   <li>{@code initProof}/{@code respProof} — per-role handshake proofs over the bound
 *       secret, so a party that cannot reconstruct the same secret fails verification.</li>
 * </ul>
 * 中文：绑定缺失判等语义：仅持有与所声明公钥匹配私钥的一方才能重建同一共享秘密。
 */
public final class HandshakeKeys {

    private HandshakeKeys() {
    }

    /** Domain-separated seed → NodeId label. */
    private static final String LABEL_ID = "subterra.p2p.hs.id:";
    /** Domain-separated seed → key-pair label. */
    private static final String LABEL_KEY = "subterra.p2p.hs.key:";
    /** Challenge derivation label. */
    private static final String LABEL_CHAL = "subterra.p2p.hs.chal";
    /** KDF label for the bound final secret. */
    private static final String LABEL_KDF = "subterra.p2p.hs.kdf";
    /** Initiator proof label. */
    private static final String LABEL_INIT = "subterra.p2p.hs.init";
    /** Responder proof label. */
    private static final String LABEL_RESP = "subterra.p2p.hs.resp";
    /** Handshake-id label. */
    private static final String LABEL_IDHS = "subterra.p2p.hs.hsid";
    /** Session-start sequence label. */
    private static final String LABEL_SEQ = "subterra.p2p.hs.seq";

    private static final HexFormat HEX = HexFormat.of();

    /**
     * Derive the 16-byte {@link NodeId} for a node from a fixed seed (deterministic).
     * 中文：由固定种子确定性派生节点的 16 字节 NodeId。
     */
    public static NodeId nodeIdForSeed(byte[] seed) {
        byte[] h = sha256(concat(bytes(LABEL_ID), seed));
        return NodeId.of(Arrays.copyOf(h, NodeId.BYTES));
    }

    /**
     * Derive a matching X25519 key pair from a fixed seed (deterministic). The private
     * scalar is {@code SHA-256(seed)}; the public key is its true X25519 base-point
     * multiple (computed via {@link SecureChannel#sharedSecret} against the base point
     * {@code U = 9}), so both peers agree on the same shared secret.
     * 中文：由固定种子确定性派生匹配的 X25519 密钥对（私钥 = SHA-256(种子)，公钥为其真 正的
     * X25519 基点 U=9 倍点），保证双方协商出相同共享秘密。
     */
    public static KeyPair keyPairForSeed(byte[] seed) {
        byte[] privScalar = sha256(concat(bytes(LABEL_KEY), seed));
        String privHex = HEX.formatHex(privScalar);
        // The holder's public component is ignored; only the private scalar is used.
        KeyPair holder = SecureChannel.keyPairFromHex(privHex, HEX.formatHex(new byte[32]));
        PrivateKey priv = holder.getPrivate();
        // X25519 base point U=9, encoded little-endian (byte[0] = 9, rest 0).
        byte[] base = new byte[32];
        base[0] = (byte) 0x09;
        PublicKey basePub = SecureChannel.keyPairFromHex(HEX.formatHex(new byte[32]), HEX.formatHex(base)).getPublic();
        byte[] pubBytes = SecureChannel.sharedSecret(priv, basePub); // == public key of privScalar
        return SecureChannel.keyPairFromHex(privHex, HEX.formatHex(pubBytes));
    }

    /**
     * Raw 32-byte X25519 public key from an encoded {@link PublicKey} (last 32 bytes of the
     * SPKI, which is exactly the 32-byte scalar coordinate).
     * 中文：从 X25519 PublicKey 取原始 32 字节公钥（SPKI 末尾 32 字节即 32 字节标量坐标）。
     */
    public static byte[] rawPub(PublicKey publicKey) {
        byte[] enc = publicKey.getEncoded();
        if (enc == null || enc.length < 32) {
            throw new HandshakeException("public key has no raw material");
        }
        return Arrays.copyOfRange(enc, enc.length - 32, enc.length);
    }

    /**
     * Reconstruct an X25519 {@link PublicKey} object from its raw 32 bytes.
     * 中文：由原始 32 字节重建 X25519 PublicKey 对象。
     */
    public static PublicKey publicFromRaw(byte[] raw) {
        if (raw == null || raw.length != 32) {
            throw new HandshakeException("X25519 public key must be 32 bytes");
        }
        return SecureChannel.keyPairFromHex(HEX.formatHex(new byte[32]), HEX.formatHex(raw)).getPublic();
    }

    /**
     * 16-byte handshake challenge, deterministically derived from the initiator identity.
     * 中文：由发起方身份确定性派生的 16 字节握手挑战。
     */
    public static byte[] challenge(NodeId initId, byte[] initPub) {
        return Arrays.copyOf(sha256(concat(bytes(LABEL_CHAL), initId.bytes(), initPub)), 16);
    }

    /**
     * The <em>bound</em> session secret: canonicalised inputs so that both endpoints derive
     * an identical value. Inputs are the two NodeIds (sorted) with each one's public key,
     * plus the raw X25519 shared secret. Changing any NodeId or any public key (i.e. any
     * key pair) changes the result — identity- and key-binding.
     * 中文：绑定了双方身份的会话秘密；输入按 NodeId 字典序规范化，使两端得出相同值。任一端身
     * 份或密钥对变化都会改变结果（身份 + 密钥绑定）。
     */
    public static byte[] bindSecret(NodeId idA, NodeId idB, byte[] pubA, byte[] pubB, byte[] sharedX) {
        // canonical order by NodeId hex so initiator and responder inputs collide
        boolean swap = idB.hex().compareTo(idA.hex()) < 0;
        byte[] in;
        if (!swap) {
            in = concat(bytes(LABEL_KDF), idA.bytes(), idB.bytes(), pubA, pubB, sharedX);
        } else {
            in = concat(bytes(LABEL_KDF), idB.bytes(), idA.bytes(), pubB, pubA, sharedX);
        }
        return sha256(in);
    }

    /** Initiator's handshake proof over the bound secret. 中文：发起方对绑定秘密的握手证明。 */
    public static byte[] initProof(byte[] challenge, byte[] boundSecret) {
        return sha256(concat(bytes(LABEL_INIT), challenge, boundSecret));
    }

    /** Responder's handshake proof over the bound secret. 中文：响应方对绑定秘密的握手证明。 */
    public static byte[] respProof(byte[] challenge, byte[] boundSecret) {
        return sha256(concat(bytes(LABEL_RESP), challenge, boundSecret));
    }

    /**
     * Deterministic handshake id: SHA-256 of the canonical (sorted) NodeIds + bound secret,
     * as lowercase hex. Identical on both endpoints.
     * 中文：确定性握手 id：规范化 NodeId + 绑定秘密的 SHA-256，小写 hex，两端一致。
     */
    public static String handshakeId(NodeId idA, NodeId idB, byte[] boundSecret) {
        NodeId a = idA.hex().compareTo(idB.hex()) <= 0 ? idA : idB;
        NodeId b = a == idA ? idB : idA;
        return HEX.formatHex(sha256(concat(bytes(LABEL_IDHS), a.bytes(), b.bytes(), boundSecret)));
    }

    /**
     * Deterministic non-negative initial sequence counter for the session channel, from the
     * bound secret.
     * 中文：由绑定秘密确定性派生、非负的会话信道起始序列计数。
     */
    public static long seq0(byte[] boundSecret) {
        byte[] h = sha256(concat(bytes(LABEL_SEQ), boundSecret));
        long s = 0;
        for (int i = 0; i < 8; i++) {
            s = (s << 8) | (h[i] & 0xFFL);
        }
        return s & Long.MAX_VALUE;
    }

    /** SHA-256 digest. */
    public static byte[] sha256(byte[] in) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(in);
        } catch (NoSuchAlgorithmException e) {
            throw new HandshakeException("SHA-256 unavailable", e);
        }
    }

    /** UTF-8 bytes of a string. */
    public static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    /** Concatenate byte arrays. */
    public static byte[] concat(byte[]... parts) {
        int n = 0;
        for (byte[] p : parts) {
            n += p.length;
        }
        byte[] out = new byte[n];
        int o = 0;
        for (byte[] p : parts) {
            System.arraycopy(p, 0, out, o, p.length);
            o += p.length;
        }
        return out;
    }
}