package io.toterra.subterra.engine.saveverify.sig;

import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.EdECPublicKey;
import java.security.spec.EdECPoint;
import java.security.spec.EdECPublicKeySpec;
import java.security.spec.NamedParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Arrays;

/**
 * ed25519 密钥对持有者（p.2.10.2，可验证存档案的强签名底座）。不可变：构造后即冻结四个不可变
 * 组件（公私钥对象 + 各自编码字节），无 setter、无状态变更。
 * <p>
 * Immutable holder of an ed25519 key pair (p.2.10.2, the strong-signing base of the verifiable save
 * archive). Once constructed it freezes four immutable parts (key objects + their encoded bytes);
 * no setters, no state change.
 *
 * <p><b>「密钥确定性注入」契约（deterministic keypair injection）</b>：可验证存档案需要一条
 * 「同一输入恒得同一签名、签名结果可复核」的密钥落地路径。落地方式为<b>一次性 provision</b>：
 * 任意熵次调用 {@link #provision()} 生成一次密钥对，把结果编码为固定字节（PKCS#8 私钥 48B +
 * RFC 8032 公钥点 32B）持久化；此后一切签名/验签均经 {@link #fromEncoded(byte[], byte[])} 从
 * <b>固定编码字节</b>确定性重构密钥对象，绝不再生成。这样测试与真实部署都能把密钥当常数注入。
 *
 * <p>注：JDK 不提供「固定种子 → 由私钥标量乘出公钥点」的公开 API，故 {@link #fromEncoded} 不
 * 从 32B seed 推导公钥；只需 provision 一次并存好 {PKCS8 私钥编码, 32B 公钥点} 即可满足确定性
 * 注入契约。RFC 8032 签名本身是确定性的（同 (消息, 密钥) 恒同 64B），无需 SecureRandom。
 */
public final class Ed25519Keys {

    /** ed25519 公钥点字节长（RFC 8032 32B）。ed25519 public point length (RFC 8032, 32 bytes). */
    public static final int PUBLIC_POINT_LEN = 32;

    /** ed25519 PKCS#8 私钥编码字节长（固定 48B）。ed25519 PKCS#8 private-key length (48 bytes). */
    public static final int PKCS8_PRIVATE_LEN = 48;

    /** ed25519 签名字节长（RFC 8032 64B）。ed25519 signature length (RFC 8032, 64 bytes). */
    public static final int SIGNATURE_LEN = 64;

    private final PublicKey publicKey;
    private final PrivateKey privateKey;
    private final byte[] publicPoint;
    private final byte[] pkcs8Private;

    /**
     * 私有构造：强校验后冻结各组件。Private ctor: validate then freeze all parts.
     *
     * @param publicKey    公钥对象（须为 {@link EdECPublicKey}）。
     * @param privateKey   私钥对象。
     * @param publicPoint  RFC 8032 公钥点（32B）。
     * @param pkcs8Private PKCS#8 私钥编码（Ed25519 固定 48B）。
     */
    private Ed25519Keys(PublicKey publicKey, PrivateKey privateKey,
                        byte[] publicPoint, byte[] pkcs8Private) {
        if (!(publicKey instanceof EdECPublicKey)) {
            throw new IllegalArgumentException("publicKey must be an EdECPublicKey");
        }
        if (privateKey == null || publicPoint == null || pkcs8Private == null) {
            throw new IllegalArgumentException("neither key nor encoding may be null");
        }
        if (publicPoint.length != PUBLIC_POINT_LEN) {
            throw new IllegalArgumentException(
                    "publicPoint must be " + PUBLIC_POINT_LEN + " bytes, got " + publicPoint.length);
        }
        if (pkcs8Private.length != PKCS8_PRIVATE_LEN) {
            throw new IllegalArgumentException(
                    "pkcs8Private must be " + PKCS8_PRIVATE_LEN + " bytes, got " + pkcs8Private.length);
        }
        this.publicKey = publicKey;
        this.privateKey = privateKey;
        this.publicPoint = publicPoint.clone();
        this.pkcs8Private = pkcs8Private.clone();
    }

    /**
     * 一次性生成密钥对（provision）。用 JDK 内建 {@code KeyPairGenerator("Ed25519")} 生成一次；
     * 私钥取 {@code getEncoded()}（PKCS#8，Ed25519 固定 48B）；公钥强转 {@link EdECPublicKey} 后
     * 经 {@code getPoint()} 编码为 RFC 8032 32B 字节点（非线性序，禁用 X.509 44B）。
     * <p>
     * One-time keypair generation. Uses the built-in {@code KeyPairGenerator("Ed25519")} once; the
     * private key is {@code getEncoded()} (PKCS#8, 48B for Ed25519); the public key is cast to
     * {@link EdECPublicKey} and encoded to an RFC 8032 32-byte point (economics; the X.509 44B
     * form is not used).
     *
     * @return 一组生成的密钥对（含可持久化的编码字节）。
     */
    public static Ed25519Keys provision() {
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("Ed25519");
            KeyPair pair = kpg.generateKeyPair();
            if (!(pair.getPublic() instanceof EdECPublicKey pub)) {
                throw new IllegalStateException(
                        "Ed25519 provider returned a non-EdECPublicKey: "
                                + pair.getPublic().getClass().getName());
            }
            byte[] point = encodePoint(pub.getPoint());
            byte[] pkcs8 = pair.getPrivate().getEncoded();
            if (pkcs8 == null || pkcs8.length != PKCS8_PRIVATE_LEN) {
                throw new IllegalStateException("unexpected PKCS#8 private-key encoding length: "
                        + (pkcs8 == null ? "null" : pkcs8.length));
            }
            return new Ed25519Keys(pair.getPublic(), pair.getPrivate(), point, pkcs8);
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException("Ed25519 keypair generation failed", e);
        }
    }

    /**
     * 从固定编码字节确定性重构密钥对。用 {@code KeyFactory("Ed25519")} 把 PKCS#8 私钥解码为私钥
     * 对象；公钥经 {@link EdECPublicKeySpec} 用指定 {@code NamedParameterSpec("Ed25519")} 与 32B
     * point 重构为公钥对象（见 {@link #decodePoint}）。入参校验：pkcs8 非空且恰 48B、publicPoint
     * 非空且恰 32B，非法抛 {@link IllegalArgumentException}。
     * <p>
     * Deterministically rebuild the key pair from fixed encoded bytes. The PKCS#8 private key is
     * decoded via {@code KeyFactory("Ed25519")}; the public key is rebuilt from a 32-byte point via
     * {@link EdECPublicKeySpec} with {@code NamedParameterSpec("Ed25519")} (see
     * {@link #decodePoint}). Inputs are validated (pkcs8 non-null and 48B, publicPoint non-null
     * and 32B); illegal input throws {@link IllegalArgumentException}.
     *
     * @param pkcs8Private PKCS#8 私钥编码（Ed25519 固定旗号 48B）。
     * @param publicPoint  RFC 8032 公钥点（32B）。
     * @return 确定性重构的密钥对。
     */
    public static Ed25519Keys fromEncoded(byte[] pkcs8Private, byte[] publicPoint) {
        if (pkcs8Private == null || pkcs8Private.length != PKCS8_PRIVATE_LEN) {
            throw new IllegalArgumentException("pkcs8Private must be " + PKCS8_PRIVATE_LEN
                    + " bytes, got " + (pkcs8Private == null ? "null" : pkcs8Private.length));
        }
        if (publicPoint == null || publicPoint.length != PUBLIC_POINT_LEN) {
            throw new IllegalArgumentException("publicPoint must be " + PUBLIC_POINT_LEN
                    + " bytes, got " + (publicPoint == null ? "null" : publicPoint.length));
        }
        try {
            KeyFactory kf = KeyFactory.getInstance("Ed25519");
            PrivateKey pk = kf.generatePrivate(new PKCS8EncodedKeySpec(pkcs8Private.clone()));
            EdECPoint edPoint = decodePoint(publicPoint);
            PublicKey pub = kf.generatePublic(
                    new EdECPublicKeySpec(new NamedParameterSpec("Ed25519"), edPoint));
            return new Ed25519Keys(pub, pk, publicPoint.clone(), pkcs8Private.clone());
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalArgumentException("failed to rebuild Ed25519 keys from encoding", e);
        }
    }

    /**
     * 把 {@link EdECPoint}（{@code getPoint()} 的返回类型）编码为 RFC 8032 32B 字符串：Y 小端序，
     * 最高字节 bit7 置 X 奇偶位（xOdd）。RFC 8032 &sect;5.1.2 标准表示。
     * <p>
     * Encodes an {@link EdECPoint} (the type returned by {@code getPoint()}) into an RFC 8032
     * 32-byte string: little-endian Y, with the x-odd bit set in bit 7 of the top octet
     * (RFC 8032 &sect;5.1.2).
     */
    private static byte[] encodePoint(EdECPoint p) {
        byte[] out = littleEndianOf(p.getY());
        if (p.isXOdd()) {
            out[PUBLIC_POINT_LEN - 1] |= (byte) 0x80;
        }
        return out;
    }

    /**
     * 把 RFC 8032 32B 公钥点解码回 {@link EdECPoint}：最高字节 bit7 即 xOdd（X 奇偶），其余取 Y
     * 为大整数。与 {@link #encodePoint} 互逆。
     * <p>
     * Decodes an RFC 8032 32-byte public point back to {@link EdECPoint}: bit 7 of the top octet
     * is xOdd, the rest is Y. Inverse of {@link #encodePoint}.
     */
    private static EdECPoint decodePoint(byte[] point) {
        byte[] y = point.clone();
        y[PUBLIC_POINT_LEN - 1] &= 0x7F; // 清掉 xOdd 位，得到纯 Y 大端字节
        byte[] be = new byte[PUBLIC_POINT_LEN];
        for (int i = 0; i < PUBLIC_POINT_LEN; i++) {
            be[i] = y[PUBLIC_POINT_LEN - 1 - i]; // 小端转大端
        }
        return new EdECPoint((point[PUBLIC_POINT_LEN - 1] & 0x80) != 0,
                new BigInteger(1, be));
    }

    /** 把一个无符号大整数编码为 32B 小端字节（用于 RFC 8032 点表示）。LE 32B of an unsigned bigint. */
    private static byte[] littleEndianOf(BigInteger v) {
        byte[] out = new byte[PUBLIC_POINT_LEN];
        byte[] be = v.toByteArray(); // 最小大端表示，可能带符号字节
        int pos = be.length - 1;
        for (int i = 0; i < PUBLIC_POINT_LEN && pos >= 0; i++, pos--) {
            out[i] = be[pos];
        }
        return out;
    }

    /** 公钥对象。Public-key object. */
    public PublicKey publicKey() {
        return publicKey;
    }

    /** 私钥对象。Private-key object. */
    public PrivateKey privateKey() {
        return privateKey;
    }

    /** RFC 8032 公钥点（32B，每次返回新拷贝）。RFC 8032 public point (32B; fresh copy each call). */
    public byte[] publicPoint() {
        return publicPoint.clone();
    }

    /** PKCS#8 私钥编码（48B，每次返回新拷贝）。PKCS#8 private-key encoding (48B; fresh copy). */
    public byte[] pkcs8Private() {
        return pkcs8Private.clone();
    }

    /**
     * 按编码字节相等：仅当 {@code other} 为同包实例且 pkcs8 私钥与公钥点均相等时返回 true。
     * Equality by encoded bytes: true only if {@code other} is an instance of this class with
     * equal PKCS#8 private key and public point.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Ed25519Keys that)) {
            return false;
        }
        return Arrays.equals(publicPoint, that.publicPoint)
                && Arrays.equals(pkcs8Private, that.pkcs8Private);
    }

    /** 按编码字节计算哈希。Hash derived from the encoded bytes. */
    @Override
    public int hashCode() {
        int h = 1;
        h = 31 * h + Arrays.hashCode(publicPoint);
        h = 31 * h + Arrays.hashCode(pkcs8Private);
        return h;
    }
}