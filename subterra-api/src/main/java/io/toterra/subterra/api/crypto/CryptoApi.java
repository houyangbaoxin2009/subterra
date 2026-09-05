package io.toterra.subterra.api.crypto;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * L3 API library (p.1.3): crypto helpers for domain mods (standard-library
 * only). Digests and MACs are convenience wrappers; AES-GCM is the default
 * authenticated encryption with a random 12-byte IV per call.
 */
public final class CryptoApi {

    public static final int GCM_IV_BYTES = 12;
    public static final int GCM_TAG_BITS = 128;

    private static final String GCM_TRANSFORM = "AES/GCM/NoPadding";

    /** AES-GCM ciphertext with the IV that was used (generated per encryption). */
    public record Encrypted(byte[] iv, byte[] data) {
    }

    private CryptoApi() {
    }

    /** SHA-256 hex digest of a UTF-8 string. */
    public static String sha256Hex(String data) {
        return digestHex("SHA-256", data);
    }

    /** SHA-1 hex digest of a UTF-8 string. */
    public static String sha1Hex(String data) {
        return digestHex("SHA-1", data);
    }

    /** HMAC-SHA-256 hex over the given key and message bytes. */
    public static String hmacSha256Hex(byte[] key, byte[] message) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(message));
        } catch (Exception e) {
            throw new IllegalStateException("HMAC-SHA-256 unavailable", e);
        }
    }

    /**
     * AES-256-GCM encrypts {@code plaintext} with a fresh random IV.
     * The returned record carries the IV so decryption needs no extra state.
     */
    public static Encrypted aesGcmEncrypt(byte[] key, byte[] plaintext) {
        byte[] iv = new byte[GCM_IV_BYTES];
        new SecureRandom().nextBytes(iv);
        return new Encrypted(iv, aesGcmCipher(Cipher.ENCRYPT_MODE, key, iv, plaintext));
    }

    /** AES-256-GCM decrypts with the exact IV that was used to encrypt. */
    public static byte[] aesGcmDecrypt(byte[] key, byte[] iv, byte[] ciphertext) {
        return aesGcmCipher(Cipher.DECRYPT_MODE, key, iv, ciphertext);
    }

    private static byte[] aesGcmCipher(int mode, byte[] key, byte[] iv, byte[] input) {
        try {
            Cipher cipher = Cipher.getInstance(GCM_TRANSFORM);
            cipher.init(mode, new SecretKeySpec(key, "AES"), new GCMParameterSpec(GCM_TAG_BITS, iv));
            return cipher.doFinal(input);
        } catch (Exception e) {
            throw new IllegalStateException("AES-GCM " + (mode == Cipher.ENCRYPT_MODE ? "encrypt" : "decrypt") + " failed", e);
        }
    }

    private static String digestHex(String algorithm, String data) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance(algorithm).digest(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(algorithm + " unavailable", e);
        }
    }
}