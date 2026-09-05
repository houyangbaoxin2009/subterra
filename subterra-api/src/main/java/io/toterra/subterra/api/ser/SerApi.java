package io.toterra.subterra.api.ser;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * L3 API library (p.1.3): serialization primitives for domain mods.
 * Pure Java (hex and base64) — wire formats like tink frames live elsewhere.
 */
public final class SerApi {

    private SerApi() {
    }

    /** Lower-case hex → bytes (even-length input only; excess ignored on odd input). */
    public static byte[] hexToBytes(String hex) {
        if (hex == null || (hex.length() & 1) != 0) {
            throw new IllegalArgumentException("hex must be non-null and even-length");
        }
        byte[] out = new byte[hex.length() / 2];
        for (int i = 0; i < out.length; i++) {
            int hi = Character.digit(hex.charAt(i * 2), 16);
            int lo = Character.digit(hex.charAt(i * 2 + 1), 16);
            if (hi < 0 || lo < 0) {
                throw new IllegalArgumentException("non-hex character at index " + (i * 2));
            }
            out[i] = (byte) ((hi << 4) | lo);
        }
        return out;
    }

    /** Bytes → lower-case hex string. */
    public static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }

    /** UTF-8 string → base64. */
    public static String encodeBase64(String utf8) {
        return Base64.getEncoder().encodeToString(utf8.getBytes(StandardCharsets.UTF_8));
    }

    /** Base64 → UTF-8 string. */
    public static String decodeBase64(String base64) {
        return new String(Base64.getDecoder().decode(base64), StandardCharsets.UTF_8);
    }
}