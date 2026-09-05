package io.toterra.subterra.probes;

import io.toterra.subterra.api.crypto.CryptoApi;
import io.toterra.subterra.api.net.NetApi;
import io.toterra.subterra.api.ser.SerApi;
import io.toterra.subterra.api.time.TimeApi;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Deterministic acceptance probe for the L3 API library (p.1.3).
 * Pure JVM — no Minecraft runtime, no real network I/O.
 * <p>
 * Asserts boundary cases, known vectors, and round-trips for the
 * time / network / serialization / crypto wrappers.
 * Exit 0 = PASS, exit 1 = FAIL (gates acceptance; never shipped in the mod jar).
 */
public final class ApiProbe {

    private ApiProbe() {
    }

    public static void main(String[] args) {
        int failures = 0;
        failures += time();
        failures += net();
        failures += ser();
        failures += crypto();

        if (failures == 0) {
            System.out.println("[ApiProbe] PASS (time/net/ser/crypto)");
            System.exit(0);
        } else {
            System.out.println("[ApiProbe] FAIL: " + failures + " assertion(s)");
            System.exit(1);
        }
    }

    private static int time() {
        int f = 0;
        if (!check("nowMillis()>0", TimeApi.nowMillis() > 0)) f++;
        if (!check("nowNanos()>0", TimeApi.nowNanos() > 0)) f++;
        long t = 1_700_000_000_000L;
        if (!check("isoUtc/parseIsoUtc roundtrip", TimeApi.parseIsoUtc(TimeApi.isoUtc(t)) == t)) f++;
        // 2d 3h 4m 5s
        long twoDays = (2L * 86400 + 3 * 3600 + 4 * 60 + 5) * 1000L;
        if (!check("humanDuration 2d 3h 4m 5s", "2d 3h 4m 5s".equals(TimeApi.humanDuration(twoDays)))) f++;
        if (!check("humanDuration <1s", "<1s".equals(TimeApi.humanDuration(999)))) f++;
        return f;
    }

    private static int net() {
        int f = 0;
        if (!check("ipv4 valid", NetApi.isValidIpv4("255.255.255.255"))) f++;
        if (!check("ipv4 octet overflow", !NetApi.isValidIpv4("256.1.1.1"))) f++;
        if (!check("ipv4 leading zero", !NetApi.isValidIpv4("01.2.3.4"))) f++;
        if (!check("ipv4 too few parts", !NetApi.isValidIpv4("1.2.3"))) f++;
        if (!check("ipv4 non-numeric", !NetApi.isValidIpv4("a.b.c.d"))) f++;
        if (!check("port 0 invalid", !NetApi.isValidPort(0))) f++;
        if (!check("port 65535 valid", NetApi.isValidPort(65535))) f++;
        if (!check("port 65536 invalid", !NetApi.isValidPort(65536))) f++;
        String roundtrip = "a b&c=d";
        if (!check("urlEncode/urlDecode roundtrip", roundtrip.equals(NetApi.urlDecode(NetApi.urlEncode(roundtrip))))) f++;
        return f;
    }

    private static int ser() {
        int f = 0;
        String hex = "0ffa10";
        if (!check("hexToBytes roundtrip", hex.equals(SerApi.bytesToHex(SerApi.hexToBytes(hex))))) f++;
        if (!check("hex values", "0ffa".equals(SerApi.bytesToHex(new byte[]{(byte) 0x0f, (byte) 0xfa})))) f++;
        String base = "toterra api \u4e16\u754c";
        if (!check("base64 roundtrip", base.equals(SerApi.decodeBase64(SerApi.encodeBase64(base))))) f++;
        return f;
    }

    private static int crypto() {
        int f = 0;
        // NIST FIPS 180-2 vector: sha256("abc")
        if (!check("sha256 vector", "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"
                .equals(CryptoApi.sha256Hex("abc")))) f++;
        // hmac-sha256(key="key", message="The quick brown fox jumps over the lazy dog")
        // (standard test vector, e.g. used across RFC 4231 doc sets)
        if (!check("hmac vector", "f7bc83f430538424b13298e6aa6fb143ef4d59a14946175997479dbc2d1a3cd8"
                .equals(CryptoApi.hmacSha256Hex("key".getBytes(StandardCharsets.UTF_8),
                        "The quick brown fox jumps over the lazy dog".getBytes(StandardCharsets.UTF_8))))) f++;

        byte[] key = new byte[32];
        Arrays.fill(key, (byte) 7);
        byte[] secret = "top secret \u4e16\u754c".getBytes(StandardCharsets.UTF_8);
        try {
            CryptoApi.Encrypted enc = CryptoApi.aesGcmEncrypt(key, secret);
            byte[] dec = CryptoApi.aesGcmDecrypt(key, enc.iv(), enc.data());
            if (!check("aes-gcm roundtrip", Arrays.equals(secret, dec))) f++;
            if (!check("aes-gcm iv length", enc.iv().length == CryptoApi.GCM_IV_BYTES)) f++;
        } catch (Exception e) {
            System.out.println("[ApiProbe] FAIL aes-gcm roundtrip: exception " + e.getMessage());
            f++;
        }
        try {
            CryptoApi.Encrypted enc = CryptoApi.aesGcmEncrypt(key, secret);
            byte[] bad = enc.data().clone();
            bad[0] ^= 0x01;
            boolean threw = false;
            try {
                CryptoApi.aesGcmDecrypt(key, enc.iv(), bad);
            } catch (Exception tamper) {
                threw = true;
            }
            if (!check("aes-gcm tamper detection", threw)) f++;
        } catch (Exception e) {
            System.out.println("[ApiProbe] FAIL aes-gcm tamper detection: exception " + e.getMessage());
            f++;
        }
        return f;
    }

    private static boolean check(String what, boolean ok) {
        if (!ok) {
            System.out.println("[ApiProbe] FAIL " + what);
        }
        return ok;
    }
}