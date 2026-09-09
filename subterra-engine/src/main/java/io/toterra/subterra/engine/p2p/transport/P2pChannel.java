package io.toterra.subterra.engine.p2p.transport;

import io.toterra.subterra.engine.network.crypto.SecureChannel;
import io.toterra.subterra.engine.network.frame.FrameConst;
import io.toterra.subterra.engine.network.integrity.FrameV2;
import io.toterra.subterra.engine.network.integrity.FrameV2Result;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

/**
 * p.2.5.4 frame-level P2P transport channel — the "thin" deterministic core framed on
 * p.2.4.2 tink v2 {@link FrameV2} streaming (stream split/join) plus p.2.5.3 handshake
 * material. It is pure JDK and purely in-memory: it builds and consumes {@code byte[]}
 * frames only; it does <em>not</em> own a socket, a thread pool or any real network IO —
 * that is the runtime shell's job. Each endpoint is a memory endpoint keyed by what it
 * sends and receives.
 * <p>
 * Wire contract (per message): the logical packet is serialized
 * ({@code seq/type/ref/meta + payload}), optionally encrypted through the session
 * {@link SecureChannel}, then framed with {@link FrameV2#streamSplit} at the configured
 * chunk size. Every chunk frame carries tink v2 stream ext so the receiver can group,
 * de-duplicate and reassemble:
 * <ul>
 *   <li>{@code EXT_STREAM_SESSION} = the message session sequence (u32) {@code seq};</li>
 *   <li>{@code EXT_STREAM_SEQ} = intra-message chunk index (u64, 0..n-1);</li>
 *   <li>{@code EXT_STREAM_TOTAL} = the payload's total byte length (u64, first chunk only);</li>
 *   <li>{@code STREAM_END} flag on the last chunk — its index yields the chunk count (n).</li>
 * </ul>
 * Receive disciplines (deterministic, never silent):
 * <ul>
 *   <li>strong-integrity tamper &amp; foreign v1 / non-stream frames → {@code REJECT};</li>
 *   <li>session seq &lt; delivered watermark (old / whole-message replay) → {@code REJECT};</li>
 *   <li>duplicate chunk index within one message (mid-message replay) → {@code REJECT};</li>
 *   <li>loss: once the last chunk (STREAM_END) and the first chunk (index 0) are both known
 *       yet some middle index is absent, the deterministic core declares the message
 *       permanently lost (no retransmit) → {@code REJECT} (incomplete), and advances the
 *       watermark so the stream continues — it never hands out a half-assembled message;</li>
 *   <li>a complete-but-not-yet-contiguous message is buffered → {@code PENDING} until its
 *       predecessor arrives, then delivered in session order;</li>
 *   <li>unknown ext TLV keys are skipped (never fail on an unknown key).</li>
 * </ul>
 * The channel reassembles via {@link FrameV2#streamJoin} only after buffering and de-duplication,
 * so out-of-order chunk arrival restores the original message byte-for-byte (it sorts/joins once
 * the full contiguous set of a session is present).
 * <p>
 * 中文：p.2.5.4 帧级 P2P 传输通道——以 p.2.4.2 tink v2 {@link FrameV2} 流（split/join）+ p.2.5.3
 * 握手材料为框架的「薄」确定性核心。纯 JDK、纯内存：只构造与消费 {@code byte[]} 帧，不持有
 * socket / 线程池 / 真实网络 IO（那是 runtime 壳的事）。每个端点即内存端。
 */
public final class P2pChannel {

    /** Default transport chunk size (bytes) used by {@code streamSplit}. */
    public static final int DEFAULT_CHUNK = 16384;

    /** Default caller packet type. */
    public static final int TYPE_DATA = 1;

    /**
     * Channel-specific ext TLV key carrying the full 64-bit message session sequence. tink v2's
     * own {@code EXT_STREAM_SESSION} field is only u32 (the {@code streamSplit(session)} int),
     * so a handshake {@code initialSeq} beyond 2^32 would overflow it; this u64 field keeps the
     * watermark compare exact across arbitrary session starts.
     */
    public static final int EXT_P2P_SESSION = 0x0A;

    /** Fixed logical-packet header: seq u64 + type u32 + ref u64 + metaLen u32 = 24 bytes. */
    private static final int HEADER_BYTES = 24;

    private final SecureChannel secure;
    private final SeqCounter counter;
    private final int chunk;

    /** Delivered message watermark: messages with session seq &lt; this are stale/replay. */
    private long nextExpected;

    /** Buffered, incomplete or completed-but-waiting messages keyed by session seq. */
    private final TreeMap<Long, PendingMessage> pending = new TreeMap<>();

    /**
     * Build a pass-through channel (no encryption) starting at {@code initialSeq}.
     *
     * @param initialSeq the session starting sequence (e.g. handshake initialSeq)
     */
    public P2pChannel(long initialSeq) {
        this(null, initialSeq, DEFAULT_CHUNK);
    }

    /**
     * Build an encrypted channel carrying an established session channel.
     *
     * @param secure     the session {@link SecureChannel} (p.2.5.3), or {@code null} for pass-through
     * @param initialSeq the session starting sequence
     */
    public P2pChannel(SecureChannel secure, long initialSeq) {
        this(secure, initialSeq, DEFAULT_CHUNK);
    }

    private P2pChannel(SecureChannel secure, long initialSeq, int chunk) {
        this.secure = secure;
        this.counter = new SeqCounter(initialSeq);
        this.nextExpected = initialSeq;
        this.chunk = chunk > 0 ? chunk : DEFAULT_CHUNK;
    }

    /** The channel sequence counter (its next/current session sequence). */
    public SeqCounter counter() {
        return counter;
    }

    /**
     * Send a message: assigns the next session sequence, serialises into a logical packet,
     * optionally encrypts, then frames it via {@link FrameV2#streamSplit}. Returns the list
     * of self-delimited tink v2 frames in chunk order (transport just hands them to the
     * runtime shell to carry; a small message becomes a single frame, a large one several).
     *
     * @param message the payload bytes (may be empty)
     * @return the frames to transmit, in chunk order
     */
    public List<byte[]> send(byte[] message) {
        return send(message, TYPE_DATA, new byte[0]);
    }

    /**
     * Send a message with an explicit type and metadata.
     *
     * @param message the payload bytes
     * @param type    caller packet type
     * @param meta    opaque metadata (may be {@code null} → empty)
     * @return the frames to transmit, in chunk order
     */
    public List<byte[]> send(byte[] message, int type, byte[] meta) {
        byte[] m = message == null ? new byte[0] : message;
        byte[] mt = meta == null ? new byte[0] : meta;
        long seq = counter.next();
        byte[] body = encodePacket(new P2pPacket(seq, type, seq, m, mt));
        if (secure != null) {
            body = secure.encrypt(body); // message-level AEAD; deterministic via SecureChannel nonces
        }
        // chunk with tink v2 streaming; the u32 stream-session field is only a grouping hint and
        // may overflow for large seq, so the full session travels in EXT_P2P_SESSION (u64).
        byte[] wire = FrameV2.streamSplit(body, chunk, (int) (seq & 0xFFFFFFFFL));
        return splitFrames(wire, seq);
    }

    /**
     * Receive and process exactly one frame. Returns the delivery outcome (see class docs).
     *
     * @param frameBytes a single self-delimited tink v2 frame
     * @return a {@link ChannelResult}; never {@code null}
     */
    public ChannelResult receive(byte[] frameBytes) {
        if (frameBytes == null || frameBytes.length == 0) {
            return ChannelResult.reject("empty frame");
        }
        FrameV2Result fr = FrameV2.parse(frameBytes, 0);
        if (!fr.ok()) {
            return ChannelResult.reject("unparseable frame");
        }
        if (!fr.integrityOk()) {
            return ChannelResult.reject("integrity failure (tamper)");
        }
        if ((fr.flags() & FrameConst.FLAG_STREAM) == 0) {
            return ChannelResult.reject("not a stream frame");
        }
        byte[] ext = fr.ext();
        long sess = readStreamU64(ext, EXT_P2P_SESSION); // full 64-bit session sequence
        long sq = readStreamU64(ext, FrameConst.EXT_STREAM_SEQ);
        if (sess < 0 || sq < 0) {
            return ChannelResult.reject("missing stream metadata");
        }

        if (sess < nextExpected) {
            return ChannelResult.reject("stale or replayed message seq " + sess + " < watermark " + nextExpected);
        }

        PendingMessage pm = pending.computeIfAbsent(sess, k -> new PendingMessage());
        if (pm.frames.containsKey(sq)) {
            return ChannelResult.reject("duplicate chunk (mid-message replay) at index " + sq);
        }
        pm.frames.put(sq, frameBytes);
        boolean last = (fr.flags() & FrameConst.FLAG_STREAM_END) != 0;
        if (last) {
            pm.sawEnd = true;
            pm.endSeq = sq;
        }

        // complete: first(0)..last(endSeq) all present
        if (pm.sawEnd && pm.frames.containsKey(0L) && pm.frames.size() == pm.endSeq + 1) {
            if (sess > nextExpected) {
                return ChannelResult.pending(); // complete but out of order — wait for the watermark
            }
            byte[] joined = joinComplete(pending.remove(sess));
            if (joined == null) {
                return ChannelResult.reject("reassembly failed for message " + sess);
            }
            nextExpected++;
            ChannelResult first = deliver(joined);
            // flush any contiguous successors already complete-and-waiting
            while (first.status() == ChannelResult.Status.DELIVERED) {
                PendingMessage nxt = pending.get(nextExpected);
                if (nxt == null || !complete(nxt)) {
                    break;
                }
                byte[] nj = joinComplete(pending.remove(nextExpected));
                if (nj == null) {
                    break;
                }
                nextExpected++;
                first = deliver(nj);
            }
            return first;
        }

        // deterministic loss: head (0) and last (endSeq) both seen, yet a middle index is absent
        // → permanently lost (no retransmit), never a half-product.
        if (pm.sawEnd && pm.frames.containsKey(0L) && pm.frames.size() < pm.endSeq + 1) {
            pending.remove(sess);
            if (sess == nextExpected) {
                nextExpected++; // consume the lost message deterministically
            }
            return ChannelResult.reject("incomplete message " + sess
                    + " (missing " + (pm.endSeq + 1 - pm.frames.size()) + " chunk(s))");
        }
        return ChannelResult.pending();
    }

    // ================= re-assembly / delivery =================

    private static boolean complete(PendingMessage pm) {
        return pm.sawEnd && pm.frames.containsKey(0L) && pm.frames.size() == pm.endSeq + 1;
    }

    private byte[] joinComplete(PendingMessage pm) {
        if (pm == null) {
            return null;
        }
        // concatenate buffered chunk frames in chunk-index order, then let FrameV2.streamJoin
        // re-parse, re-verify and sort before producing the joined payload.
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] f : pm.frames.values()) {
            out.write(f, 0, f.length);
        }
        byte[] joined = FrameV2.streamJoin(out.toByteArray());
        if (joined == null) {
            return null;
        }
        if (secure != null) {
            SecureChannel.DecryptResult d = secure.decrypt(joined);
            if (!d.ok()) {
                return null; // AEAD reject (tampered / replayed ciphertext)
            }
            return d.plaintext();
        }
        return joined;
    }

    private ChannelResult deliver(byte[] body) {
        byte[] hdr = new byte[HEADER_BYTES];
        if (body.length < HEADER_BYTES) {
            return ChannelResult.reject("truncated packet header");
        }
        System.arraycopy(body, 0, hdr, 0, HEADER_BYTES);
        long seq = readU64BE(hdr, 0);
        int type = (int) readU32BE(hdr, 8);
        long ref = readU64BE(hdr, 12);
        int metaLen = (int) readU32BE(hdr, 20);
        int need = HEADER_BYTES + metaLen;
        if (body.length < need) {
            return ChannelResult.reject("truncated packet meta");
        }
        byte[] meta = new byte[metaLen];
        System.arraycopy(body, HEADER_BYTES, meta, 0, metaLen);
        int pl = body.length - need;
        byte[] payload = new byte[pl];
        System.arraycopy(body, need, payload, 0, pl);
        P2pPacket pkt = new P2pPacket(seq, type, ref, payload, meta);
        if (pkt.seq() != nextExpected - 1) {
            return ChannelResult.reject("packet seq mismatch");
        }
        return ChannelResult.delivered(payload);
    }

    // ================= logical packet codec =================

    private byte[] encodePacket(P2pPacket p) {
        byte[] meta = p.meta() == null ? new byte[0] : p.meta();
        byte[] pay = p.payload() == null ? new byte[0] : p.payload();
        ByteArrayOutputStream out = new ByteArrayOutputStream(HEADER_BYTES + meta.length + pay.length);
        writeU64(out, p.seq());
        writeU32(out, p.type());
        writeU64(out, p.ref());
        writeU32(out, meta.length);
        out.write(meta, 0, meta.length);
        out.write(pay, 0, pay.length);
        return out.toByteArray();
    }

    private List<byte[]> splitFrames(byte[] wire, long seq) {
        List<byte[]> frames = new ArrayList<>();
        byte[] sessionTlv = FrameV2.extTlv(EXT_P2P_SESSION, be64(seq));
        long pos = 0;
        while (pos < wire.length) {
            long next = FrameV2.skip(wire, pos);
            if (next <= pos) {
                break; // defensive; streamSplit output is well-formed
            }
            FrameV2Result fr = FrameV2.parse(wire, pos);
            byte[] richer = FrameV2.encode(fr.payload(), fr.flags(), concat(fr.ext(), sessionTlv));
            frames.add(richer);
            pos = next;
        }
        return frames;
    }

    // ================= ext TLV helpers (read-only; unknown keys skipped) =================

    /** Return a stream u64 metadata field ({@code EXT_STREAM_SEQ} / {@code EXT_P2P_SESSION}); -1 if absent/undersized. */
    private static long readStreamU64(byte[] ext, int key) {
        byte[] v = extValue(ext, key);
        if (v == null || v.length < 8) {
            return -1;
        }
        return readU64BE(v, 0);
    }

    private static byte[] extValue(byte[] ext, int key) {
        byte[] e = ext == null ? new byte[0] : ext;
        int p = 0;
        int n = e.length;
        while (p + 4 <= n) {
            int k = ((e[p] & 0xFF) << 8) | (e[p + 1] & 0xFF);
            int vl = ((e[p + 2] & 0xFF) << 8) | (e[p + 3] & 0xFF);
            if (p + 4 + vl > n) {
                return null; // truncated trailing TLV; treat as missing
            }
            if (k == key) {
                byte[] v = new byte[vl];
                System.arraycopy(e, p + 4, v, 0, vl);
                return v;
            }
            p = p + 4 + vl;
        }
        return null;
    }

    // ================= byte helpers =================

    private static long readU32BE(byte[] b, int o) {
        return ((b[o] & 0xFFL) << 24) | ((b[o + 1] & 0xFFL) << 16)
                | ((b[o + 2] & 0xFFL) << 8) | (b[o + 3] & 0xFFL);
    }

    private static long readU64BE(byte[] b, int o) {
        long r = 0;
        for (int i = 0; i < 8; i++) {
            r = (r << 8) | (b[o + i] & 0xFFL);
        }
        return r;
    }

    private static void writeU32(ByteArrayOutputStream out, int n) {
        out.write((n >>> 24) & 0xFF);
        out.write((n >>> 16) & 0xFF);
        out.write((n >>> 8) & 0xFF);
        out.write(n & 0xFF);
    }

    private static void writeU64(ByteArrayOutputStream out, long n) {
        for (int i = 7; i >= 0; i--) {
            out.write((int) ((n >>> (i * 8)) & 0xFF));
        }
    }

    /** u64 BE → bytes (for the channel session ext TLV value). */
    private static byte[] be64(long n) {
        byte[] out = new byte[8];
        for (int i = 7; i >= 0; i--) {
            out[i] = (byte) (n & 0xFF);
            n >>>= 8;
        }
        return out;
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] out = new byte[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }

    /** Per-message buffer of received chunk frames (key = chunk index). */
    private static final class PendingMessage {
        final TreeMap<Long, byte[]> frames = new TreeMap<>();
        boolean sawEnd;
        long endSeq = -1;
    }
}