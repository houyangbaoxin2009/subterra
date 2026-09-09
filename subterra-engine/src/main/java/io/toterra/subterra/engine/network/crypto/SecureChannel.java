package io.toterra.subterra.engine.network.crypto;

import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Payload-layer cryptographic core for the enhanced network channel.
 * Pure JDK — only {@code java.security} / {@code javax.crypto}; no external
 * crypto libraries, no Minecraft references.
 * <p>
 * 中文：增强网络通道的载荷层加密核心。纯 JDK —— 仅使用 java.security /
 * javax.crypto，不依赖第三方加密库，不含 Minecraft 引用。
 * <p>
 * <b>Key establishment</b> (密钥协商): X25519 XDH key agreement.
 * <p>
 * <b>KDF</b> (密钥派生): the raw X25519 shared secret is <em>never</em> reused
 * directly; it is fed through SHA-256 to deterministically produce the 32-byte
 * AES-256 key ({@code aesKey = SHA-256(sharedSecret)}). Documented so protocol
 * readers know the raw shared secret is not the payload key.
 * <p>
 * 中文：X25519 共享秘密不直接复用，而是经 SHA-256 派生出 32 字节 AES-256 密钥。
 * <p>
 * <b>AEAD</b>: AES/GCM/NoPadding (128-bit tag). A deterministic 12-byte nonce is
 * derived from a per-channel monotonic sequence counter: the 8-byte big-endian
 * counter followed by a 4-byte zero pad ({@code iv[8]..iv[11] == 0}). The nonce is
 * prepended to the ciphertext (format: {@code [12-byte nonce][GCM output]}), so
 * {@link #decrypt(byte[])} reconstructs the exact nonce used at encrypt time.
 * A key must never reuse a nonce for a different plaintext — the monotonic,
 * per-channel counter guarantees this. On tag mismatch ({@link AEADBadTagException})
 * decrypt returns {@link DecryptResult#ok()}{@code == false}; it never returns garbage.
 * <p>
 * 中文：每通道单调序列计数器派生确定性 nonce（8 字节大端计数 + 4 字节零填充），
 * 拼接在密文前；相同密钥不得为不同明文复用同一 nonce。标签校验失败时解密返回
 * {@code ok == false}，绝不返回脏数据。
 * <p>
 * <b>Toggle</b> (可关切换): when the underlying {@link EncryptionConfig} is disabled
 * (trusted LAN, 局域网可信), encrypt/decrypt become the identity pass-through and the
 * whole pipeline works without keys; {@link #isEncrypted()} reports the flag, which
 * the L3 network strategy (p.2.4.4) consults.
 * <p>
 * Deterministic construction is supported for probes: a fixed shared secret
 * ({@link #enabled(byte[])} / {@link #enabled(byte[], long)}) and an explicit
 * initial sequence counter make outputs reproducible.
 */
public final class SecureChannel {

    /** AES-256 key length in bytes. */
    public static final int KEY_BYTES = 32;

    /** GCM nonce length in bytes (12). */
    public static final int NONCE_BYTES = 12;

    /** GCM authentication tag length in bits (128). */
    public static final int GCM_TAG_BITS = 128;

    /** XDH algorithm name. */
    private static final String XDH = "X25519";

    /** KDF: aesKey = SHA-256(sharedSecret). */
    private static final String KDF = "SHA-256";

    private final EncryptionConfig config;
    private final byte[] aesKey; // null when disabled
    private long seq;

    private SecureChannel(EncryptionConfig config, byte[] aesKey, long initialSeq) {
        this.config = config;
        this.aesKey = aesKey;
        this.seq = initialSeq;
    }

    /**
     * Build an encrypted channel that agrees its AES key by X25519 key agreement
     * between {@code localKeyPair.private} and {@code peerPublicKey}.
     * <p>
     * 中文：以本地私钥与对端公钥做 X25519 密钥协商，构建加密信道。
     *
     * @param localKeyPair  the local X25519 key pair
     * @param peerPublicKey the peer X25519 public key
     * @return an enabled channel
     */
    public static SecureChannel enabled(KeyPair localKeyPair, PublicKey peerPublicKey) {
        return enabled(sharedSecret(localKeyPair.getPrivate(), peerPublicKey));
    }

    /**
     * Build an encrypted channel from a raw shared secret (KDF applied internally).
     * <p>
     * 中文：从原始共享秘密（内部应用 KDF）构建加密信道。
     *
     * @param sharedSecret the raw X25519 shared secret
     * @return an enabled channel with sequence counter starting at 0
     */
    public static SecureChannel enabled(byte[] sharedSecret) {
        return enabled(sharedSecret, 0L);
    }

    /**
     * Build an encrypted channel from a raw shared secret with an explicit initial
     * sequence counter (deterministic construction for probes; repro under reset).
     * <p>
     * 中文：从原始共享秘密构建加密信道并指定初始序列计数（供确定性探针复现）。
     *
     * @param sharedSecret the raw X25519 shared secret
     * @param initialSeq   the initial monotonic sequence counter (non-negative)
     * @return an enabled channel
     */
    public static SecureChannel enabled(byte[] sharedSecret, long initialSeq) {
        if (sharedSecret == null) {
            throw new SecureChannelException("shared secret must not be null");
        }
        return new SecureChannel(EncryptionConfig.on(), deriveKey(sharedSecret), initialSeq);
    }

    /**
     * Build a disabled, pass-through channel (trusted LAN, no keys).
     * <p>
     * 中文：构建关闭加密的透传信道（可信局域网，无需密钥）。
     *
     * @return a disabled channel
     */
    public static SecureChannel disabled() {
        return new SecureChannel(EncryptionConfig.disabled(), null, 0L);
    }

    /**
     * Whether this channel performs encryption (== the underlying toggle flag).
     * <p>
     * 中文：本信道是否执行加密（等价于底层开关配置）。
     *
     * @return true if encryption is enabled
     */
    public boolean isEncrypted() {
        return config.enabled();
    }

    /**
     * The underlying configuration (consulted e.g. by the L3 strategy).
     * <p>
     * 中文：底层加密配置（供 L3 策略等查询）。
     *
     * @return the encryption configuration
     */
    public EncryptionConfig config() {
        return config;
    }

    /**
     * Generate a fresh X25519 key pair.
     * <p>
     * 中文：生成新的 X25519 密钥对。
     *
     * @return a new X25519 key pair
     */
    public static KeyPair generateKeyPair() {
        try {
            return KeyPairGenerator.getInstance(XDH).generateKeyPair();
        } catch (GeneralSecurityException e) {
            throw new SecureChannelException("X25519 key generation failed", e);
        }
    }

    /**
     * Reconstruct an X25519 {@link KeyPair} from hex-encoded private/public keys
     * (deterministic construction for probes / test key injection).
     * <p>
     * 中文：从十六进制私钥/公钥重建 X25519 密钥对（供确定性探针注入测试密钥）。
     *
     * @param privateHex hex-encoded 32-byte private key
     * @param publicHex  hex-encoded 32-byte public key
     * @return the reconstructed key pair
     */
    public static KeyPair keyPairFromHex(String privateHex, String publicHex) {
        byte[] priv = hexToBytes(privateHex);
        byte[] pub = hexToBytes(publicHex);
        if (priv.length != KEY_BYTES || pub.length != KEY_BYTES) {
            throw new SecureChannelException("X25519 keys must be 32 bytes");
        }
        try {
            KeyFactory kf = KeyFactory.getInstance(XDH);
            PrivateKey privateKey = kf.generatePrivate(new PKCS8EncodedKeySpec(pkcs8X25519(priv)));
            PublicKey publicKey = kf.generatePublic(new X509EncodedKeySpec(x509X25519(pub)));
            return new KeyPair(publicKey, privateKey);
        } catch (GeneralSecurityException e) {
            throw new SecureChannelException("invalid X25519 key material", e);
        }
    }

    /**
     * Wrap a raw 32-byte X25519 private scalar in a PKCS#8 structure
     * ({@code SEQUENCE{ INTEGER 0, SEQUENCE{ OID 1.3.101.110 }, OCTET STRING{ OCTET STRING{ key } } }}).
     */
    private static byte[] pkcs8X25519(byte[] privateKey) {
        byte[] oid = {(byte) 0x06, 0x03, 0x2b, 0x65, 0x6e};                 // 1.3.101.110
        byte[] algid = seq(0x30, oid);                                      // SEQUENCE { OID }
        byte[] inner = new byte[2 + privateKey.length];
        inner[0] = 0x04; inner[1] = (byte) privateKey.length;               // inner OCTET STRING
        System.arraycopy(privateKey, 0, inner, 2, privateKey.length);
        byte[] body = new byte[3 + algid.length + 2 + inner.length];
        int p = 0;
        body[p++] = 0x02; body[p++] = 0x01; body[p++] = 0x00;               // INTEGER 0
        System.arraycopy(algid, 0, body, p, algid.length); p += algid.length;
        body[p++] = 0x04; body[p++] = (byte) inner.length;                  // OCTET STRING
        System.arraycopy(inner, 0, body, p, inner.length); p += inner.length;
        return seq(0x30, body);
    }

    /**
     * Wrap a raw 32-byte X25519 public key in an X.509 SubjectPublicKeyInfo
     * ({@code SEQUENCE{ SEQUENCE{ OID 1.3.101.110 }, BIT STRING(0 unused){ key } }}).
     */
    private static byte[] x509X25519(byte[] publicKey) {
        byte[] oid = {(byte) 0x06, 0x03, 0x2b, 0x65, 0x6e};                 // 1.3.101.110
        byte[] algid = seq(0x30, oid);                                      // SEQUENCE { OID } (no params)
        byte[] bitString = new byte[3 + publicKey.length];
        bitString[0] = 0x03;
        bitString[1] = (byte) (publicKey.length + 1);                       // BIT STRING
        bitString[2] = 0x00;                                                //   0 unused bits
        System.arraycopy(publicKey, 0, bitString, 3, publicKey.length);
        byte[] body = new byte[algid.length + bitString.length];
        System.arraycopy(algid, 0, body, 0, algid.length);
        System.arraycopy(bitString, 0, body, algid.length, bitString.length);
        return seq(0x30, body);
    }

    /** DER SEQUENCE (or BIT STRING-style) wrapper with a single-length short form. */
    private static byte[] seq(int tag, byte[] content) {
        byte[] out = new byte[content.length + 4];
        out[0] = (byte) tag;
        int len = content.length;
        if (len < 0x80) {
            out[1] = (byte) len;
            System.arraycopy(content, 0, out, 2, len);
            byte[] shortOut = new byte[len + 2];
            System.arraycopy(out, 0, shortOut, 0, shortOut.length);
            return shortOut;
        }
        out[1] = (byte) 0x81;
        out[2] = (byte) len;
        System.arraycopy(content, 0, out, 3, len);
        byte[] longOut = new byte[len + 3];
        System.arraycopy(out, 0, longOut, 0, longOut.length);
        return longOut;
    }

    /**
     * Compute the raw X25519 shared secret between a local private key and a peer public key.
     * <p>
     * 中文：计算本地私钥与对端公钥的原始 X25519 共享秘密。
     *
     * @param privateKey the local private key
     * @param publicKey  the peer public key
     * @return the raw shared secret
     */
    public static byte[] sharedSecret(PrivateKey privateKey, PublicKey publicKey) {
        try {
            KeyAgreement keyAgreement = KeyAgreement.getInstance(XDH);
            keyAgreement.init(privateKey);
            keyAgreement.doPhase(publicKey, true);
            return keyAgreement.generateSecret();
        } catch (GeneralSecurityException e) {
            throw new SecureChannelException("X25519 key agreement failed", e);
        }
    }

    /**
     * Encrypt a payload. When disabled, returns the input unchanged (identity).
     * <p>
     * 中文：加密载荷。关闭加密时原样返回（透传）。
     *
     * @param plaintext the payload bytes (may be empty)
     * @return {@code [12-byte nonce][GCM output]} when enabled, else {@code plaintext}
     */
    public byte[] encrypt(byte[] plaintext) {
        if (!config.enabled()) {
            return plaintext;
        }
        byte[] nonce = nonceFor(seq++);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(aesKey, "AES"),
                    new GCMParameterSpec(GCM_TAG_BITS, nonce));
            byte[] body = cipher.doFinal(plaintext);
            byte[] out = new byte[nonce.length + body.length];
            System.arraycopy(nonce, 0, out, 0, nonce.length);
            System.arraycopy(body, 0, out, nonce.length, body.length);
            return out;
        } catch (GeneralSecurityException e) {
            throw new SecureChannelException("AES-GCM encrypt failed", e);
        }
    }

    /**
     * Decrypt a ciphertext produced by {@link #encrypt(byte[])}. When disabled,
     * returns the input unchanged (identity). On AEAD tag mismatch returns a
     * REJECT result ({@code ok == false}); it never returns garbage.
     * <p>
     * 中文：解密 {code encrypt} 产生的密文。关闭加密时原样返回。标签校验失败时返回
     * 拒绝结果（ok == false），绝不返回脏数据。
     *
     * @param ciphertext the ciphertext bytes (format {@code [12-byte nonce][GCM output]})
     * @return {@link DecryptResult} carrying the identity / decrypted plaintext
     */
    public DecryptResult decrypt(byte[] ciphertext) {
        if (!config.enabled()) {
            return new DecryptResult(true, ciphertext);
        }
        if (ciphertext == null || ciphertext.length < NONCE_BYTES) {
            return new DecryptResult(false, null);
        }
        byte[] nonce = Arrays.copyOfRange(ciphertext, 0, NONCE_BYTES);
        byte[] body = Arrays.copyOfRange(ciphertext, NONCE_BYTES, ciphertext.length);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(aesKey, "AES"),
                    new GCMParameterSpec(GCM_TAG_BITS, nonce));
            return new DecryptResult(true, cipher.doFinal(body));
        } catch (AEADBadTagException e) {
            return new DecryptResult(false, null);
        } catch (GeneralSecurityException e) {
            // A valid nonce/key with a well-formed ciphertext cannot fail here;
            // treat as reject so callers never see garbage.
            return new DecryptResult(false, null);
        }
    }

    /**
     * KDF: {@code SHA-256(sharedSecret)} — deterministically derives the 32-byte
     * AES-256 key without reusing the raw shared secret directly.
     * <p>
     * 中文：KDF：SHA-256(sharedSecret)，确定性派生 32 字节 AES-256 密钥。
     */
    private static byte[] deriveKey(byte[] sharedSecret) {
        try {
            return MessageDigest.getInstance(KDF).digest(sharedSecret);
        } catch (NoSuchAlgorithmException e) {
            throw new SecureChannelException("SHA-256 unavailable", e);
        }
    }

    /**
     * Deterministic 12-byte nonce from a per-channel monotonic sequence counter:
     * the 8-byte big-endian counter, followed by a 4-byte zero pad.
     * <p>
     * 中文：由每通道单调序列计数器派生确定性 12 字节 nonce（8 字节大端 + 4 字节零填充）。
     */
    private static byte[] nonceFor(long s) {
        byte[] nonce = new byte[NONCE_BYTES];
        for (int i = 7; i >= 0; i--) {
            nonce[i] = (byte) (s & 0xffL);
            s >>>= 8;
        }
        return nonce;
    }

    private static byte[] hexToBytes(String hex) {
        if (hex == null || (hex.length() & 1) != 0) {
            throw new SecureChannelException("invalid hex length");
        }
        int n = hex.length() >> 1;
        byte[] out = new byte[n];
        for (int i = 0; i < n; i++) {
            out[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        return out;
    }

    /**
     * Result of a decrypt attempt. {@code ok == true} means authenticated plaintext;
     * {@code ok == false} means the ciphertext was REJECTED (tampered / wrong key / malformed).
     * <p>
     * 中文：解密结果。ok == true 表示通过认证的明文；ok == false 表示密文被拒绝。
     *
     * @param ok        whether decryption authenticated successfully
     * @param plaintext the plaintext when {@code ok} is true, else {@code null}
     */
    public record DecryptResult(boolean ok, byte[] plaintext) {
    }
}