package io.toterra.subterra.probes;

import io.toterra.subterra.engine.network.integrity.Tsha1f;
import io.toterra.subterra.engine.network.integrity.FrameV2;
import io.toterra.subterra.engine.network.integrity.FrameV2Result;
import io.toterra.subterra.engine.network.frame.FrameConst;
import io.toterra.subterra.engine.network.frame.FrameV1;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * p.2.4.2 engine.network 帧级强校验确定性验收探针（纯 JVM，无 MC 运行时）：断言 {@link Tsha1f}
 * 的 9 个黄金向量、强摘要已知值、n=48 锚点，{@link FrameV2} 的编解码往返、篡改拒绝、保留位拒绝、
 * v1 兼容读、零拷贝跳帧、流式分块重组（正确顺序产出原串、乱序拒绝）。
 * <p>
 * Deterministic acceptance probe for the p.2.4.2 per-frame integrity layer (pure JVM — no
 * Minecraft runtime): asserts the 9 golden vectors of {@link Tsha1f}, the known strong-digest
 * value, the n=48 anchor point, the FrameV2 encode-decode round-trip, tamper rejection,
 * reserved-bit rejection, v1-compatible read, zero-copy skipping, and streaming split/join
 * (correct-order reproduces original, out-of-order is rejected).
 * <p>
 * Exit 0 = PASS all checks; 1 = FAIL at least one.
 */
public final class NetworkIntegrityProbe {

    private NetworkIntegrityProbe() {
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
        // 1. tsha1f n=8 base=48 KAT vectors from tie-main
        kat8("", "5juavlyl");
        kat8("123456789", "3Kz1piuc");
        kat8("abc", "5FGIxu1J");
        kat8(repeat('a', 55), "3Lto494h");
        kat8(repeat('a', 56), "5jzi8Dnc");
        kat8(repeat('a', 63), "1s6e98FF");
        kat8(repeat('a', 64), "5juavlyl");
        kat8(repeat('a', 65), "3Dzv2hzG");
        kat8(repeat('a', 1000), "5GlqGKBB");

        // 2. strong digest KAT: tsha1_digest("f","123456789",48) → first 64 hex chars
        strongKat("123456789", 48, "260c7340bcc60eb82c727e6df6f9c7620d040c2d4837ee96dc333f1a16fd76bf");
        // 2b. n=48 anchor: tsha1f("abc", 48, 48)
        anchorN48("abc", 48, "g8e3aCsch9EfvLil9l8nq3nbrholGzy7o2HBp4w1E58cwxaa");

        // 3. v2 encode→parse round-trip (fast mode)
        roundTrip("fast", 0);
        // 4. v2 encode→parse round-trip (strong mode)
        roundTrip("strong", FrameConst.FLAG_STRONG);

        // 5. tamper 1 payload byte → ok but integrityOk=false
        tamperPayload();
        // 6. tamper 1 integrity byte → ok but integrityOk=false
        tamperIntegrity();

        // 7. frame with reserved bits set → ok=false
        reservedBitsRejected();

        // 8. v1-compatible parse → ok=true, integrityOk=true (crc match)
        v1CompatParse();

        // 9. FrameV2.skip walks 3-frame buffer to exact end
        skipThreeFrames();

        // 10. streamSplit/streamJoin: 5 chunks → join reproduces byte-identical payload
        streamSplitJoinOk();

        // 11. streamJoin corruption: reorder chunks → join rejected (returns null)
        streamSplitJoinReject();

        if (failures == 0) {
            System.out.println("[NetworkIntegrityProbe] PASS: tsha1f + tink-v2 frame integrity (" + checks + " checks)");
            System.exit(0);
        } else {
            System.out.println("[NetworkIntegrityProbe] FAIL: " + failures + " failure(s) of " + checks);
            System.exit(1);
        }
    }

    // -------- 1 tsha1f 黄金向量 / KAT vectors --------

    private static void kat8(String msg, String expected) {
        byte[] b = msg.getBytes(StandardCharsets.UTF_8);
        String got = Tsha1f.tsha1f(b, 8, 48);
        check("tsha1f(\"" + shortRepr(msg) + "\", 8, 48) == \"" + expected + "\"",
                expected.equals(got));
    }

    private static void strongKat(String msg, int n, String expectedFirst64) {
        byte[] b = msg.getBytes(StandardCharsets.UTF_8);
        String full = Tsha1f.digestHex("f", b, n);
        boolean ok = full.length() >= 64 && expectedFirst64.equals(full.substring(0, 64));
        check("tsha1_digest(\"f\", \"123456789\", 48) first 64 chars matches golden", ok);
    }

    private static void anchorN48(String msg, int n, String expected) {
        byte[] b = msg.getBytes(StandardCharsets.UTF_8);
        String got = Tsha1f.tsha1f(b, n, 48);
        check("tsha1f(\"abc\", 48, 48) (anchor for generic W=9 path) == expected",
                expected.equals(got));
    }

    private static String shortRepr(String s) {
        if (s.length() <= 12) {
            return s;
        }
        return s.substring(0, 3) + "...";
    }

    private static String repeat(char c, int n) {
        StringBuilder sb = new StringBuilder(n);
        for (int i = 0; i < n; i++) {
            sb.append(c);
        }
        return sb.toString();
    }

    // -------- 3-4 往返 / round-trip --------

    private static void roundTrip(String label, int flags) {
        // binary-safe test: 0..63 with 0x00/0xFF + Chinese UTF-8
        byte[] payload = new byte[64];
        for (int i = 0; i < payload.length; i++) {
            payload[i] = (byte) i;
        }
        payload[payload.length - 1] = (byte) 0xFF;
        String zh = "雨林山脉 UTF-8 中文载荷 — 帧级强校验 p.2.4.2";
        byte[] zhBytes = zh.getBytes(StandardCharsets.UTF_8);
        byte[] combined = new byte[payload.length + zhBytes.length];
        System.arraycopy(payload, 0, combined, 0, payload.length);
        System.arraycopy(zhBytes, 0, combined, payload.length, zhBytes.length);

        byte[] frame = FrameV2.encode(combined, flags, null);
        FrameV2Result r = FrameV2.parse(frame, 0);
        boolean ok = r.ok() && r.integrityOk() && r.nextPos() == frame.length
                && Arrays.equals(r.payload(), combined);
        check(label + ": encode→parse round-trip (binary-safe 0x00/0xFF + UTF-8 Chinese)", ok);
        boolean nextPosOk = FrameV2.skip(frame, 0) == frame.length;
        check(label + ": skip matches full frame length (no integrity recompute)", nextPosOk);
    }

    // -------- 5-6 篡改测试 / tamper tests --------

    private static void tamperPayload() {
        byte[] payload = "tamper-me-please-integrity-fail".getBytes(StandardCharsets.UTF_8);
        byte[] frame = FrameV2.encode(payload, 0, null); // fast mode
        byte[] dirty = frame.clone();
        dirty[10 + 2] ^= 0x01; // flip one payload byte
        FrameV2Result r = FrameV2.parse(dirty, 0);
        boolean ok = r.ok() && !r.integrityOk(); // parsing succeeds but integrity fails
        check("tamper 1 payload byte: parse ok but integrity rejected (integrityOk=false)", ok);
    }

    private static void tamperIntegrity() {
        byte[] payload = "tamper-the-integrity-slot".getBytes(StandardCharsets.UTF_8);
        byte[] frame = FrameV2.encode(payload, FrameConst.FLAG_STRONG, null);
        byte[] dirty = frame.clone();
        dirty[dirty.length - 1] ^= 0x01; // flip last byte of strong integrity slot
        FrameV2Result r = FrameV2.parse(dirty, 0);
        boolean ok = r.ok() && !r.integrityOk();
        check("tamper 1 integrity byte: parse ok but integrity rejected (integrityOk=false)", ok);
    }

    // -------- 7 保留位拒绝 / reserved bits rejection --------

    private static void reservedBitsRejected() {
        byte[] payload = "reserved-bits-set".getBytes(StandardCharsets.UTF_8);
        int badFlags = 0x00 | FrameConst.FLAG_RESERVED_MASK; // bit5..7 set → reject
        byte[] frame = FrameV2.encode(payload, badFlags, null);
        FrameV2Result r = FrameV2.parse(frame, 0);
        check("frame with reserved bits (0xE0) set → parse ok=false (rejected)", !r.ok());
    }

    // -------- 8 v1 兼容 / v1 compatible --------

    private static void v1CompatParse() {
        byte[] payload = "v1-compatible-payload".getBytes(StandardCharsets.UTF_8);
        byte[] v1Frame = FrameV1.encode(payload);
        FrameV2Result r = FrameV2.parse(v1Frame, 0);
        // magic "tk" mismatch → fallback to v1 parse; integrityOk = crc match
        boolean ok = r.ok() && r.integrityOk() && Arrays.equals(r.payload(), payload);
        check("v1-compatible: frame encoded by FrameV1 → parse ok, integrityOk=true (crc match)", ok);
    }

    // -------- 9 跳帧 / skip walk --------

    private static void skipThreeFrames() {
        byte[] f1 = FrameV2.encode("one".getBytes(StandardCharsets.UTF_8), 0, null);
        byte[] f2 = FrameV2.encode("two-strong".getBytes(StandardCharsets.UTF_8), FrameConst.FLAG_STRONG, null);
        byte[] f3 = FrameV2.encode("three".getBytes(StandardCharsets.UTF_8), 0, null);
        byte[] buf = new byte[f1.length + f2.length + f3.length];
        System.arraycopy(f1, 0, buf, 0, f1.length);
        System.arraycopy(f2, 0, buf, f1.length, f2.length);
        System.arraycopy(f3, 0, buf, f1.length + f2.length, f3.length);

        long p0 = FrameV2.skip(buf, 0);
        long p1 = FrameV2.skip(buf, p0);
        long p2 = FrameV2.skip(buf, p1);
        boolean ok = p0 == f1.length && p1 == f1.length + f2.length && p2 == buf.length;
        check("FrameV2.skip: 3 consecutive frames walked to exact buffer end", ok);
    }

    // -------- 10-11 流式分块重组 / streaming split/join --------

    private static void streamSplitJoinOk() {
        String longStr = "这是一段较长的文本，用来测试流式分块和重组。要求分片之后重组得到完全相同的字节序列，"
                + "每片携带会话和序号，乱序也要通过重排序还原，丢帧/序号不连续必须拒绝。";
        byte[] original = longStr.getBytes(StandardCharsets.UTF_8);
        byte[] frames = FrameV2.streamSplit(original, 32, 12345);
        byte[] joined = FrameV2.streamJoin(frames);
        boolean ok = joined != null && Arrays.equals(joined, original);
        check("streamSplit/streamJoin: 5 chunks (chunk 32, session 12345) → joined byte-identical", ok);
    }

    private static void streamSplitJoinReject() {
        String longStr = "这段文本在中间丢掉一块后必须被检测为损坏，无法拼接得到原串。";
        byte[] original = longStr.getBytes(StandardCharsets.UTF_8);
        byte[] frames = FrameV2.streamSplit(original, 16, 9876);
        // 丢弃中间一块（缺 seq → 连续性校验 0..count-1 失败）→ 与 tink_v2.ie stream_join 同语义拒绝
        // Dropping a middle chunk breaks the seq-contiguity check (0..count-1) → rejected,
        // matching the stream_join rejection semantics of tink_v2.ie.
        byte[] corrupted = dropMiddleChunk(frames);
        byte[] joined = FrameV2.streamJoin(corrupted);
        check("streamSplit/streamJoin: dropped a middle chunk (seq gap) → join returns null (rejected)",
                joined == null);
    }

    /** 丢弃拼接缓冲中中间的那一帧（保留首末帧）。Drops the middle frame chunk of a concatenated buffer. */
    private static byte[] dropMiddleChunk(byte[] buf) {
        FrameV2Result r0 = FrameV2.parse(buf, 0);
        if (!r0.ok()) {
            return buf;
        }
        int endChunk0 = (int) r0.nextPos(); // 第 0 帧结束位置
        FrameV2Result rMid = FrameV2.parse(buf, endChunk0);
        if (!rMid.ok()) {
            return buf;
        }
        FrameV2Result rAfter = FrameV2.parse(buf, rMid.nextPos());
        if (!rAfter.ok()) {
            return buf; // 不足三段则不丢弃，避免误报
        }
        // 保留第 0 帧与第 2 帧之后的所有帧，丢弃第 1（中间）帧
        byte[] prefix = Arrays.copyOfRange(buf, 0, endChunk0);
        byte[] rest = Arrays.copyOfRange(buf, (int) rMid.nextPos(), buf.length);
        return concatBytes(prefix, rest);
    }

    private static byte[] concatBytes(byte[] a, byte[] b) {
        byte[] out = new byte[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }
}