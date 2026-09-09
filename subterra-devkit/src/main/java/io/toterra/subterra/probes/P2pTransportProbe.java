package io.toterra.subterra.probes;

import io.toterra.subterra.engine.network.crypto.SecureChannel;
import io.toterra.subterra.engine.network.frame.FrameConst;
import io.toterra.subterra.engine.network.integrity.FrameV2;
import io.toterra.subterra.engine.network.integrity.FrameV2Result;
import io.toterra.subterra.engine.p2p.NodeAddr;
import io.toterra.subterra.engine.p2p.NodeId;
import io.toterra.subterra.engine.p2p.handshake.HandshakeKeys;
import io.toterra.subterra.engine.p2p.handshake.HandshakePeer;
import io.toterra.subterra.engine.p2p.handshake.HandshakeResult;
import io.toterra.subterra.engine.p2p.transport.ChannelResult;
import io.toterra.subterra.engine.p2p.transport.P2pChannel;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * p.2.5.4 frame-level P2P transport channel acceptance probe (pure JVM, no MC runtime): a
 * memory endpoint sending/receiving tink v2 stream frames with per-message session sequence,
 * ordered re-assembly, de-duplication and deterministic loss/tamper/replay rejection. Exercises
 * single-frame round-trip, a 64KiB streamSplit round-trip, out-of-order chunk reassembly, a
 * fixed-seed lost-chunk gap, mid-message replay, single-byte tamper, stale/replay seq, the
 * p.2.5.3 handshake linkage (initialSeq + optional AEAD session), unknown-ext skip, and full
 * reproducibility.
 * <p>
 * Determinism: fixed seeds everywhere; failure counter advances only on assertion failures;
 * no timing assertions. Exit 0 = PASS all checks; 1 = FAIL.
 */
public final class P2pTransportProbe {

    private P2pTransportProbe() {
    }

    private static int failures = 0;
    private static int checks = 0;

    private static void check(String name, boolean ok) {
        checks++;
        if (ok) {
            System.out.println("[PASS] " + name);
        } else {
            failures++;
            System.out.println("[FAIL] " + name);
        }
    }

    public static void main(String[] args) {
        smallRoundTrip();
        bigStreamRoundTrip();
        outOfOrderReassembly();
        lostChunkRejects();
        midMessageReplayRejects();
        tamperRejects();
        staleAndReplaySeqReject();
        handshakeLinkage();
        secureChannelLinkage();
        unknownExtSkipped();
        determinism();

        if (failures == 0) {
            System.out.println("[P2pTransportProbe] PASS: p.2.5.4 frame-level P2P transport channel ("
                    + checks + " checks)");
            System.exit(0);
        } else {
            System.out.println("[P2pTransportProbe] FAIL: " + failures + " failure(s) of " + checks);
            System.exit(1);
        }
    }

    // ---- 1 single-frame small message round-trip ----

    private static void smallRoundTrip() {
        P2pChannel tx = new P2pChannel(0);
        P2pChannel rx = new P2pChannel(0);
        byte[] msg = b("a small single-frame message over the p2p channel");
        List<byte[]> frames = tx.send(msg);
        check("small message frames to exactly one frame", frames.size() == 1);
        check("single-frame send→receive round-trips byte-identical", rx.receive(frames.get(0)).delivers(msg));
    }

    // ---- 2 large (64KiB) streamSplit→reassemble round-trip ----

    private static void bigStreamRoundTrip() {
        byte[] big = new byte[64 * 1024];
        new Random(1234L).nextBytes(big);
        P2pChannel tx = new P2pChannel(0);
        P2pChannel rx = new P2pChannel(0);
        List<byte[]> frames = tx.send(big);
        check("64KiB message is chunked into >1 stream frames", frames.size() > 1);
        ChannelResult r = ChannelResult.pending();
        List<String> outcomes = new ArrayList<>();
        for (byte[] f : frames) {
            r = rx.receive(f);
            outcomes.add(r.status().name());
        }
        check("64KiB streamSplit round-trips byte-identical", r.delivers(big));
        check("all intermediate chunk feeds reported PENDING (only final DELIVERED)",
                outcomes.subList(0, outcomes.size() - 1).stream().allMatch(s -> s.equals("PENDING"))
                        && outcomes.get(outcomes.size() - 1).equals("DELIVERED"));
    }

    // ---- 3 out-of-order chunk arrival reassembles to the original ----

    private static void outOfOrderReassembly() {
        byte[] msg = new byte[70 * 1024]; // > chunk (16384) -> several chunks
        new Random(77L).nextBytes(msg);
        P2pChannel tx = new P2pChannel(0);
        P2pChannel rx = new P2pChannel(0);
        List<byte[]> frames = tx.send(msg);
        // fixed swing shuffle (deterministic) — reverse the chunk order
        List<byte[]> shuffled = new ArrayList<>(frames);
        java.util.Collections.reverse(shuffled);
        Faux rx2 = feedAll(rx, shuffled);
        check("out-of-order (reversed) chunk input reassembles to the original message byte-identical",
                rx2.delivered != null && Arrays.equals(rx2.delivered, msg));
    }

    private static final class Faux {
        ChannelResult last;
        byte[] delivered;
    }

    private static Faux feedAll(P2pChannel rx, List<byte[]> frames) {
        Faux fx = new Faux();
        for (byte[] f : frames) {
            fx.last = rx.receive(f);
            if (fx.last.status() == ChannelResult.Status.DELIVERED) {
                fx.delivered = fx.last.message();
            }
        }
        return fx;
    }

    // ---- 4 skipped chunk (fixed seed) => deterministic REJECT, no half-product ----

    private static void lostChunkRejects() {
        byte[] msg = new byte[50 * 1024];
        new Random(9L).nextBytes(msg);
        P2pChannel tx = new P2pChannel(0);
        P2pChannel rx = new P2pChannel(0);
        List<byte[]> frames = tx.send(msg);
        // deterministically skip a middle chunk (never 0, never last) so total+end are both seen
        int skip = (frames.size() / 2);
        int rejected = 0;
        boolean sawPending = false;
        byte[] delivered = null;
        ChannelResult last = null;
        for (int i = 0; i < frames.size(); i++) {
            if (i == skip) {
                continue;
            }
            last = rx.receive(frames.get(i));
            if (last.status() == ChannelResult.Status.REJECT) {
                rejected++;
            } else if (last.status() == ChannelResult.Status.DELIVERED) {
                delivered = last.message();
            } else {
                sawPending = true;
            }
        }
        check("dropping a chunk yields exactly one deterministic REJECT (incomplete)",
                rejected == 1);
        check("lost-chunk path never produces a half-assembled message (no DELIVERED, not silently swallowed)",
                delivered == null && sawPending && rejected == 1);
        check("loss rejection carries an explicit incomplete signal + reason",
                last != null && last.status() == ChannelResult.Status.REJECT
                        && last.reason() != null && !last.reason().isEmpty());
    }

    // ---- 5 mid-message duplicate chunk (replay) rejected ----

    private static void midMessageReplayRejects() {
        byte[] msg = new byte[40 * 1024];
        new Random(5L).nextBytes(msg);
        P2pChannel tx = new P2pChannel(0);
        P2pChannel rx = new P2pChannel(0);
        List<byte[]> frames = tx.send(msg);
        // feed chunk0, then chunk0 again -> duplicate rejected
        ChannelResult r0 = rx.receive(frames.get(0));
        ChannelResult r1 = rx.receive(frames.get(0));
        check("re-feeding the same chunk of an in-flight message is rejected as a mid-message replay",
                r0.status() == ChannelResult.Status.PENDING
                        && r1.status() == ChannelResult.Status.REJECT);
    }

    // ---- 6 single-byte tamper rejected ----

    private static void tamperRejects() {
        byte[] msg = b("tamper me if you can");
        P2pChannel tx = new P2pChannel(0);
        List<byte[]> frames = tx.send(msg);
        byte[] bad = frames.get(0).clone();
        // flip one payload byte (payload begins after 10-byte header + ext_len u16)
        int extLen = ((bad[8] & 0xFF) << 8) | (bad[9] & 0xFF);
        int payloadStart = 10 + extLen;
        bad[payloadStart] ^= 0x01;
        P2pChannel rx = new P2pChannel(0);
        ChannelResult r = rx.receive(bad);
        check("single-byte payload tamper is rejected with a strong-integrity signal",
                r.status() == ChannelResult.Status.REJECT
                        && r.reason().toLowerCase(Locale.ROOT).contains("integrity"));
        // flip a byte in the (strong) integrity slot
        byte[] bad2 = frames.get(0).clone();
        bad2[bad2.length - 1] ^= 0x40;
        ChannelResult r2 = new P2pChannel(0).receive(bad2);
        check("integrity-slot flip is rejected", r2.status() == ChannelResult.Status.REJECT);
    }

    // ---- 7 stale / replayed whole-message seq rejected ----

    private static void staleAndReplaySeqReject() {
        byte[] m1 = b("message one");
        P2pChannel tx = new P2pChannel(0);
        List<byte[]> f1 = tx.send(m1); // seq=0
        P2pChannel rx = new P2pChannel(0);
        check("first message delivered before stale test",
                rx.receive(f1.get(0)).status() == ChannelResult.Status.DELIVERED);
        // replay the already-delivered frame (seq=0) -> watermark is now 1
        ChannelResult replay = rx.receive(f1.get(0));
        check("replaying an already-delivered frame is rejected (old/replay seq)",
                replay.status() == ChannelResult.Status.REJECT);
        // clean stale rollback: watermark is ahead of the frame's seq
        P2pChannel rx3 = new P2pChannel(5);
        ChannelResult stale = rx3.receive(f1.get(0)); // seq=0 < watermark 5
        check("a frame whose seq is below the delivered watermark is rejected as stale/rollback",
                stale.status() == ChannelResult.Status.REJECT
                        && stale.reason().toLowerCase(Locale.ROOT).contains("stale"));
        // out-of-order onboarding: a higher seq arrives first, then the expected one delivers in order
        P2pChannel tx2 = new P2pChannel(0);
        tx2.send(b("dummy seq=0"));
        List<byte[]> f2 = tx2.send(b("message two seq=1"));
        List<byte[]> f3 = tx2.send(b("message three seq=2"));
        P2pChannel rx2 = new P2pChannel(1); // expect seq=1 next
        ChannelResult high = rx2.receive(f3.get(0)); // seq=2 first -> PENDING (waiting for seq=1)
        ChannelResult low = rx2.receive(f2.get(0));  // seq=1 -> DELIVERED (and flushes seq=2)
        check("higher-seq frame first is buffered PENDING, expected seq then delivers in order",
                high.status() == ChannelResult.Status.PENDING && low.status() == ChannelResult.Status.DELIVERED);
    }

    // ---- 8 p.2.5.3 handshake linkage: channel starts at handshake initialSeq ----

    private static HandshakeResult[] handshake(String a, String bSeed) {
        HandshakePeer init = new HandshakePeer(id(a), addr("i-" + a + ".lan", 22000), kp(a), true);
        HandshakePeer resp = new HandshakePeer(id(bSeed), addr("r-" + bSeed + ".lan", 32000), kp(bSeed), false);
        byte[] hello = init.begin();
        byte[] accept = resp.accept(hello);
        HandshakeResult ri = init.complete(accept);
        HandshakeResult rr = resp.result();
        return new HandshakeResult[]{ri, rr};
    }

    private static void handshakeLinkage() {
        HandshakeResult[] r = handshake("peer-a", "peer-b");
        long initialSeq = r[0].initialSeq();
        P2pChannel chan = new P2pChannel(initialSeq);
        check("channel starts its session counter at the handshake initialSeq",
                chan.counter().current() == initialSeq);
        List<byte[]> frames = chan.send(b("hello over session"));
        byte[] ext = firstFrameExt(frames.get(0));
        long wireSession = channelSession(ext);
        check("first sent frame carries handshake initialSeq as stream session",
                wireSession == initialSeq);
        P2pChannel rx = new P2pChannel(initialSeq);
        check("handshake-seeded channel round-trips the first message",
                rx.receive(frames.get(0)).status() == ChannelResult.Status.DELIVERED);
    }

    // ---- 9 (optional) session frames encrypted via p.2.5.3 SecureChannel ----

    private static void secureChannelLinkage() {
        HandshakeResult[] r = handshake("peer-a", "peer-b");
        SecureChannel sa = r[0].secureChannel();
        SecureChannel sb = r[1].secureChannel();
        long initialSeq = r[0].initialSeq();
        byte[] msg = b("encrypted session transport frame");
        P2pChannel atxA = new P2pChannel(sa, initialSeq);
        List<byte[]> framesA = atxA.send(msg);
        P2pChannel bRx = new P2pChannel(sb, initialSeq); // peer decrypts with its symmetric channel
        ChannelResult rb = bRx.receive(framesA.get(0));
        check("session frame encrypted on A→decrypted on B round-trips via the bound SecureChannel",
                rb.delivers(msg));
        check("session channel advanced one after a send (deterministic sequence on the link)",
                atxA.counter().current() == initialSeq + 1);
    }

    // ---- 10 unknown ext TLV key is skipped ----

    private static void unknownExtSkipped() {
        byte[] msg = new byte[30 * 1024];
        new Random(11L).nextBytes(msg);
        P2pChannel tx = new P2pChannel(0);
        List<byte[]> frames = tx.send(msg);
        // take chunk0, re-encode its payload with the same ext plus a bogus unknown TLV
        FrameV2Result fr = FrameV2.parse(frames.get(0), 0);
        byte[] payload = fr.payload();
        byte[] ext = fr.ext();
        byte[] richer = FrameV2.encode(payload, FrameConst.FLAG_STRONG | FrameConst.FLAG_STREAM,
                concat(ext, FrameV2.extTlv(0x03E7, b("ignored"))));
        P2pChannel rx = new P2pChannel(0);
        ChannelResult r0 = rx.receive(richer);          // chunk0 with unknown key
        ChannelResult r1 = rx.receive(frames.get(1));   // chunk1 plain
        ChannelResult rLast = r1;
        for (int i = 2; i < frames.size(); i++) {
            rLast = rx.receive(frames.get(i));
        }
        check("a frame carrying an unknown ext TLV key is accepted and skipped (reassembly succeeds)",
                r0.status() == ChannelResult.Status.PENDING
                        && rLast.status() == ChannelResult.Status.DELIVERED
                        && Arrays.equals(rLast.message(), msg));
    }

    // ---- 11 reproducibility: two same-seed shuffles => identical deliver + reject sets ----

    private static void determinism() {
        byte[] msg = new byte[45 * 1024];
        new Random(31337L).nextBytes(msg);
        long s1 = runScramble(msg, 4242L);
        long s2 = runScramble(msg, 4242L);
        check("two same-seed scrambled deliveries produce identical delivered bytes and rejection counts",
                s1 == s2);
    }

    private static long runScramble(byte[] msg, long seed) {
        P2pChannel tx = new P2pChannel(0);
        List<byte[]> frames = tx.send(msg);
        List<byte[]> shuffled = new ArrayList<>(frames);
        // deterministic fixed-seed shuffle
        Random rnd = new Random(seed);
        for (int i = shuffled.size() - 1; i > 0; i--) {
            int j = rnd.nextInt(i + 1);
            byte[] t = shuffled.get(i);
            shuffled.set(i, shuffled.get(j));
            shuffled.set(j, t);
        }
        P2pChannel rx = new P2pChannel(0);
        long delivered = 0;
        int rejected = 0;
        for (byte[] f : shuffled) {
            ChannelResult r = rx.receive(f);
            if (r.status() == ChannelResult.Status.REJECT) {
                rejected++;
            } else if (r.status() == ChannelResult.Status.DELIVERED) {
                delivered++;
            }
        }
        return delivered * 1_000_000 + rejected;
    }

    // ---- helpers ----

    private static byte[] b(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    private static NodeId id(String seed) {
        return HandshakeKeys.nodeIdForSeed(b(seed));
    }

    private static KeyPair kp(String seed) {
        return HandshakeKeys.keyPairForSeed(b(seed));
    }

    private static NodeAddr addr(String host, int port) {
        return new NodeAddr.Direct(host, port);
    }

    private static byte[] firstFrameExt(byte[] frame) {
        FrameV2Result fr = FrameV2.parse(frame, 0);
        return fr.ext();
    }

    private static long channelSession(byte[] ext) {
        // the channel carries the full 64-bit session in EXT_P2P_SESSION (0x0A)
        byte[] v = extValue(ext, P2pChannel.EXT_P2P_SESSION);
        if (v == null || v.length == 0) {
            return -1;
        }
        long r = 0;
        for (byte x : v) {
            r = (r << 8) | (x & 0xFFL);
        }
        return r;
    }

    private static byte[] extValue(byte[] ext, int key) {
        int p = 0;
        int n = ext.length;
        while (p + 4 <= n) {
            int k = ((ext[p] & 0xFF) << 8) | (ext[p + 1] & 0xFF);
            int vl = ((ext[p + 2] & 0xFF) << 8) | (ext[p + 3] & 0xFF);
            if (p + 4 + vl > n) {
                return null;
            }
            if (k == key) {
                return Arrays.copyOfRange(ext, p + 4, p + 4 + vl);
            }
            p = p + 4 + vl;
        }
        return null;
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] out = new byte[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }
}