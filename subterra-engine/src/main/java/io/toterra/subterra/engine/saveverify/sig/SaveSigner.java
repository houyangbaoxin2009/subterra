package io.toterra.subterra.engine.saveverify.sig;

import java.security.GeneralSecurityException;
import java.security.Signature;

/**
 * ed25519 强签名操作者（p.2.10.2，可验证存档案的签名底座）。纯静态工具，无内部状态：
 * 对同一 (keys, canonicalHead) 输入恒产出同一 64B 签名（RFC 8032 签名确定性，无需 SecureRandom）。
 * <p>
 * The ed25519 strong-signature operator (p.2.10.2, the signature base of the verifiable save
 * archive). A pure static utility with no internal state: identical (keys, canonicalHead) input
 * always yields the identical 64-byte signature (RFC 8032 deterministic signatures; no
 * SecureRandom needed).
 *
 * <p>签名/验签均从 {@link Ed25519Keys} 的固定编码字节确定性重构，处于热路径、无时钟/时间戳/
 * 默认种子随机。任何 JCE 受检异常统一转运行时 {@link IllegalStateException} 并注明原因。
 */
public final class SaveSigner {

    private SaveSigner() {
    }

    /**
     * 对规范化存档头签名。用 {@code Signature("Ed25519")} initSign(privateKey) → update(canonicalHead)
     * → sign()，返回 64B 签名（RFC 8032）。同 (keys, canonicalHead) 恒同 64B。
     * <p>
     * Signs the canonical save head with {@code Signature("Ed25519")}: initSign(privateKey) →
     * update(canonicalHead) → sign(), returning a 64-byte signature. Identical (keys,
     * canonicalHead) always yields the identical 64B signature.
     *
     * @param keys          密钥对（须与 {@code fromEncoded} provision 结果一致）；null 抛
     *                      {@link IllegalArgumentException}。
     * @param canonicalHead 规范化存档头字节（已确定性编码）；null 抛
     *                      {@link IllegalArgumentException}。
     * @return 64B 确定性签名。
     * @throws IllegalStateException 签名运算底层失败（如密钥算法不符）。
     */
    public static byte[] sign(Ed25519Keys keys, byte[] canonicalHead) {
        if (keys == null) {
            throw new IllegalArgumentException("keys must not be null");
        }
        if (canonicalHead == null) {
            throw new IllegalArgumentException("canonicalHead must not be null");
        }
        try {
            Signature sig = Signature.getInstance("Ed25519");
            sig.initSign(keys.privateKey());
            sig.update(canonicalHead);
            byte[] out = sig.sign();
            if (out.length != Ed25519Keys.SIGNATURE_LEN) {
                throw new IllegalStateException("unexpected Ed25519 signature length: " + out.length);
            }
            return out;
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Ed25519 signing failed", e);
        }
    }

    /**
     * 校验规范化存档头的签名。initVerify(publicKey) → update → verify；signature 长度非 64B 直接
     * 返回 false，不抛异常。
     * <p>
     * Verifies the signature over the canonical save head: initVerify(publicKey) → update →
     * verify(); a signature not exactly 64 bytes returns {@code false} directly.
     *
     * @param keys          密钥对（null 视为校验失败）。
     * @param canonicalHead 规范化存档头字节（null 视为校验失败）。
     * @param signature     待验签的 64B 签名（null 或长度非 64B → false）。
     * @return true = 签名有效且与 (keys, canonicalHead) 一致；否则 false。
     * @throws IllegalStateException 验签运算底层失败（如密钥算法不符）。
     */
    public static boolean verify(Ed25519Keys keys, byte[] canonicalHead, byte[] signature) {
        if (keys == null || canonicalHead == null
                || signature == null || signature.length != Ed25519Keys.SIGNATURE_LEN) {
            return false;
        }
        try {
            Signature sig = Signature.getInstance("Ed25519");
            sig.initVerify(keys.publicKey());
            sig.update(canonicalHead);
            return sig.verify(signature);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Ed25519 verification failed", e);
        }
    }
}