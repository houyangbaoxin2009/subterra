package io.toterra.subterra.probes;

import io.toterra.subterra.engine.network.crypto.SecureChannel;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.util.Arrays;

/**
 * Deterministic acceptance probe for the p.2.4.5 engine.network.crypto core
 * (X25519 key agreement + SHA-256 KDF + AES-GCM payload encryption, with a
 * trusted-LAN pass-through toggle).
 * Pure JVM — no Minecraft runtime, no external crypto libraries.
 * <p>
 * Uses the RFC 7748 X25519 test vector as a known-vector anchor and hard-coded
 * test keys, so all outputs (ciphertexts, KDF keys) are reproducible. Explicit
 * sequence counters keep encrypt outputs deterministic.
 * Exit 0 = PASS, exit 1 = FAIL (gates acceptance; never shipped in the mod jar).
 */
public final class NetworkCryptoProbe {

    // RFC 7748 §6.1 X25519 test vector.
    private static final String ALICE_PRIV_HEX = "77076d0a7318a57d3c16c17251b26645df4c2f87ebc0992ab177fba51db92c2a";
    private static final String ALICE_PUB_HEX = "8520f0098930a754748b7ddcb43ef75a0dbf3a0d26381af4eba4a98eaa9b4e6a";
    private static final String BOB_PRIV_HEX = "5dab087e624a8a4b79e17f8b83800ee66f3bb1292618b6fd1c2f8b27ff88e0eb";
    private static final String BOB_PUB_HEX = "de9edb7d7b7dc1b4d35b61c2ece435373f8343c85b78674dadfc7e146f882b4f";
    private static final String SHARED_HEX = "4a5d9d5ba4ce2de1728e3bf480350f25e07e21c947d19e3376f09b3c1e161742";

    private NetworkCryptoProbe() {
    }

    public static void main(String[] args) {
        int failures = 0;
        failures += agreement();
        failures += roundtrip();
        failures += tamper();
        failures += wrongKey();
        failures += toggle();
        failures += nonceUniqueness();

        if (failures == 0) {
            System.out.println("[NetworkCryptoProbe] PASS (agreement/roundtrip/tamper/wrong-key/toggle/nonce)");
            System.exit(0);
        } else {
            System.out.println("[NetworkCryptoProbe] FAIL: " + failures + " assertion(s)");
            System.exit(1);
        }
    }

    /** Assertion 1: key-agreement symmetry (fresh keypairs) + RFC 7748 known vector. */
    private static int agreement() {
        int f = 0;
        // Freshly generated keypairs derive the same secret from either direction.
        KeyPair aliceFresh = SecureChannel.generateKeyPair();
        KeyPair bobFresh = SecureChannel.generateKeyPair();
        byte[] aEb = SecureChannel.sharedSecret(aliceFresh.getPrivate(), bobFresh.getPublic());
        byte[] bEa = SecureChannel.sharedSecret(bobFresh.getPrivate(), aliceFresh.getPublic());
        if (!check("agreement symmetric (fresh keypairs)", Arrays.equals(aEb, bEa))) f++;

        // Known vector: fixed RFC 7748 pairs agree on the documented shared secret.
        KeyPair alice = SecureChannel.keyPairFromHex(ALICE_PRIV_HEX, ALICE_PUB_HEX);
        KeyPair bob = SecureChannel.keyPairFromHex(BOB_PRIV_HEX, BOB_PUB_HEX);
        byte[] shared = SecureChannel.sharedSecret(alice.getPrivate(), bob.getPublic());
        if (!check("agreement RFC7748 vector", arrayEqualsHex(shared, SHARED_HEX))) f++;
        return f;
    }

    /** Assertion 2: encrypt/decrypt round-trip over several seq values and payloads. */
    private static int roundtrip() {
        int f = 0;
        byte[] secret = hexToBytes(SHARED_HEX);
        byte[][] payloads = {
                "Subterra 网络加密 载荷 \u4e16\u754c".getBytes(StandardCharsets.UTF_8),
                new byte[]{(byte) 0x00, (byte) 0xff, (byte) 0x80, (byte) 0x00, (byte) 0xff, 0x39},
                new byte[]{},
                new byte[]{(byte) 0xff},
        };

        // One auto-increment channel, several payloads with distinct seq values.
        SecureChannel ch = SecureChannel.enabled(secret);
        for (byte[] p : payloads) {
            SecureChannel.DecryptResult r = ch.decrypt(ch.encrypt(p));
            if (!check("roundtrip identity", r.ok() && Arrays.equals(p, r.plaintext()))) f++;
        }

        // Explicit initial sequence counters keep round-trips correct and reproducible.
        for (long seq : new long[]{0L, 7L, 42L}) {
            SecureChannel fixed = SecureChannel.enabled(secret, seq);
            byte[] enc = fixed.encrypt(payloads[0]);
            SecureChannel.DecryptResult r = fixed.decrypt(enc);
            if (!check("roundtrip seq=" + seq, r.ok() && Arrays.equals(payloads[0], r.plaintext()))) f++;
            // Reproducibility: same secret + same seq -> identical ciphertext.
            SecureChannel again = SecureChannel.enabled(secret, seq);
            if (!check("roundtrip reproducible seq=" + seq, Arrays.equals(enc, again.encrypt(payloads[0])))) f++;
        }
        return f;
    }

    /** Assertion 3: tampering a ciphertext byte is REJECTED (fail path only). */
    private static int tamper() {
        int f = 0;
        byte[] secret = hexToBytes(SHARED_HEX);
        SecureChannel ch = SecureChannel.enabled(secret);
        byte[] plain = "tamper test".getBytes(StandardCharsets.UTF_8);
        byte[] enc = ch.encrypt(plain);
        byte[] bad = enc.clone();
        bad[bad.length - 1] ^= 0x01;

        // Intact ciphertext still authenticates (control).
        SecureChannel.DecryptResult ok = ch.decrypt(enc);
        if (!check("tamper control ok", ok.ok() && Arrays.equals(plain, ok.plaintext()))) f++;

        // Flipped byte -> REJECT, never garbage.
        SecureChannel.DecryptResult r = ch.decrypt(bad);
        if (!check("tamper rejected", r != null && !r.ok() && r.plaintext() == null)) f++;
        return f;
    }

    /** Assertion 4: decrypting with a channel derived from an unrelated keypair is REJECTED. */
    private static int wrongKey() {
        int f = 0;
        KeyPair alice = SecureChannel.keyPairFromHex(ALICE_PRIV_HEX, ALICE_PUB_HEX);
        KeyPair bob = SecureChannel.keyPairFromHex(BOB_PRIV_HEX, BOB_PUB_HEX);
        KeyPair carol = SecureChannel.generateKeyPair();

        SecureChannel aliceBob = SecureChannel.enabled(SecureChannel.sharedSecret(alice.getPrivate(), bob.getPublic()));
        SecureChannel aliceCarol = SecureChannel.enabled(SecureChannel.sharedSecret(alice.getPrivate(), carol.getPublic()));

        byte[] plain = "wrong key test \u4e16\u754c".getBytes(StandardCharsets.UTF_8);
        byte[] enc = aliceBob.encrypt(plain);
        if (!check("wrong-key encrypt ok", aliceBob.decrypt(enc).ok())) f++;
        SecureChannel.DecryptResult r = aliceCarol.decrypt(enc);
        if (!check("wrong-key decrypt rejected", r != null && !r.ok() && r.plaintext() == null)) f++;
        return f;
    }

    /** Assertion 5: disabled (LAN-trusted) channel is pass-through identity; flag reports correctly. */
    private static int toggle() {
        int f = 0;
        SecureChannel disabled = SecureChannel.disabled();
        byte[] plain = "LAN payload \u4e16\u754c".getBytes(StandardCharsets.UTF_8);

        if (!check("disabled isEncrypted false", !disabled.isEncrypted())) f++;
        if (!check("disabled config disable", !disabled.config().enabled())) f++;
        byte[] e = disabled.encrypt(plain);
        if (!check("disabled encrypt identity", e == plain || Arrays.equals(e, plain))) f++;
        SecureChannel.DecryptResult r = disabled.decrypt(plain);
        if (!check("disabled decrypt identity", r.ok() && (r.plaintext() == plain || Arrays.equals(r.plaintext(), plain)))) f++;

        byte[] secret = hexToBytes(SHARED_HEX);
        SecureChannel enabled = SecureChannel.enabled(secret);
        if (!check("enabled isEncrypted true", enabled.isEncrypted())) f++;
        if (!check("enabled config enable", enabled.config().enabled())) f++;
        if (!check("enabled ciphertext differs", !Arrays.equals(enabled.encrypt(plain), plain))) f++;
        return f;
    }

    /** Assertion 6: distinct sequence counters yield distinct ciphertexts for the same plaintext. */
    private static int nonceUniqueness() {
        int f = 0;
        byte[] secret = hexToBytes(SHARED_HEX);
        byte[] plain = "nonce uniqueness".getBytes(StandardCharsets.UTF_8);

        // Explicit different initial seq -> different ciphertext for identical plaintext.
        byte[] s0 = SecureChannel.enabled(secret, 0L).encrypt(plain);
        byte[] s100 = SecureChannel.enabled(secret, 100L).encrypt(plain);
        if (!check("nonce differ explicit seq", !Arrays.equals(s0, s100))) f++;

        // Auto-increment within one channel -> every encrypt uses a fresh nonce.
        SecureChannel ch = SecureChannel.enabled(secret, 0L);
        byte[] c1 = ch.encrypt(plain);
        byte[] c2 = ch.encrypt(plain);
        byte[] c3 = ch.encrypt(plain);
        if (!check("nonce auto-increment pairwise distinct",
                !Arrays.equals(c1, c2) && !Arrays.equals(c2, c3) && !Arrays.equals(c1, c3))) f++;
        return f;
    }

    private static boolean check(String what, boolean ok) {
        if (!ok) {
            System.out.println("[NetworkCryptoProbe] FAIL " + what);
        }
        return ok;
    }

    private static boolean arrayEqualsHex(byte[] arr, String hex) {
        return Arrays.equals(arr, hexToBytes(hex));
    }

    private static byte[] hexToBytes(String hex) {
        int n = hex.length() >> 1;
        byte[] out = new byte[n];
        for (int i = 0; i < n; i++) {
            out[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        return out;
    }
}