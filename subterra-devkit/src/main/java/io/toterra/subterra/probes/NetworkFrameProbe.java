package io.toterra.subterra.probes;

import io.toterra.subterra.engine.network.frame.Crc32Ieee;
import io.toterra.subterra.engine.network.frame.FrameResult;
import io.toterra.subterra.engine.network.frame.FrameV1;
import io.toterra.subterra.engine.network.frame.FrameV1Iterator;
import io.toterra.subterra.engine.network.frame.ZdFrameBridge;
import io.toterra.subterra.engine.zd.ZdRow;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * p.2.4.1 engine.network 帧载体确定性验收探针（纯 JVM，无 MC 运行时）：断言 CRC-32/IEEE
 * 已知向量、v1 帧往返逐字节、空载荷往返、多帧零拷贝遍历、截断帧拒绝、脏字节的
 * crc/tsha1f 层次契约，以及 zd 帧载荷桥往返。退出码 0 = PASS，1 = FAIL。
 * <p>
 * Deterministic acceptance probe for the p.2.4.1 engine.network frame carrier
 * (pure JVM — no Minecraft runtime): asserts the known CRC-32/IEEE vector, the v1
 * frame byte-identical round-trip, the empty-payload round-trip, multi-frame
 * zero-copy iteration, truncated-frame rejection, the crc/tsha1f layering contract
 * on dirty bytes, and the zd frame-payload bridge round-trip. Exit 0 = PASS.
 */
public final class NetworkFrameProbe {

    private NetworkFrameProbe() {
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
        crcVector();
        v1RoundTrip();
        emptyRoundTrip();
        multiFrameIteration();
        truncatedFrame();
        dirtyByteContract();
        zdBridgeRoundTrip();

        if (failures == 0) {
            System.out.println("[NetworkFrameProbe] PASS (frame carrier + zd payload bridge, " + checks + " checks)");
            System.exit(0);
        } else {
            System.out.println("[NetworkFrameProbe] FAIL: " + failures + " assertion(s) of " + checks);
            System.exit(1);
        }
    }

    // ---- 1 CRC-32/IEEE known vector ------------------------------------------

    private static void crcVector() {
        byte[] data = "123456789".getBytes(StandardCharsets.US_ASCII);
        check("crc32(\"123456789\") == 0xCBF43926 (zlib.crc32 vector)",
                Crc32Ieee.of(data) == 0xCBF43926);

        int inc = Crc32Ieee.update(Crc32Ieee.update(Crc32Ieee.init(), data, 0, 5), data, 5, 4);
        check("crc32 incremental init/update/value path matches the same vector",
                Crc32Ieee.value(inc) == 0xCBF43926);

        check("crc32(\"\" ) == 0x00000000", Crc32Ieee.of(new byte[0]) == 0x00000000);
        check("crc32 differs for a different input (0x61 != the '123456789' vector)",
                Crc32Ieee.of(new byte[]{(byte) 0x61}) != 0xCBF43926);
    }

    // ---- 2 FrameV1 encode/decode round-trip ----------------------------------

    private static void v1RoundTrip() {
        byte[] payload = new byte[64];
        for (int i = 0; i < payload.length; i++) {
            payload[i] = (byte) i; // 0..63 incl 0x00 and 0xFF range
        }
        payload[payload.length - 1] = (byte) 0xFF;
        String zh = "雨林山脉 UTF-8 中文载荷 — 边界";
        byte[] p2 = zh.getBytes(StandardCharsets.UTF_8);
        byte[] combined = new byte[payload.length + p2.length];
        System.arraycopy(payload, 0, combined, 0, payload.length);
        System.arraycopy(p2, 0, combined, payload.length, p2.length);

        byte[] frame = FrameV1.encode(combined);
        FrameResult r = FrameV1.parse(frame, 0);
        check("v1: encode->decode returns payload nextPos==full length",
                r != null && r.nextPos() == frame.length && Arrays.equals(r.payload(), combined));

        byte[] embedded = FrameV1.encode(combined);
        FrameResult rEmbed = FrameV1.parse(embedded, 0);
        check("v1: binary-safe (0x00/0xFF + UTF-8 Chinese) byte-identical on round-trip",
                rEmbed != null && Arrays.equals(rEmbed.payload(), combined)
                        && new String(rEmbed.payload(), StandardCharsets.UTF_8).equals(
                        new String(r.payload(), StandardCharsets.UTF_8)));

        // sanity: frame length == 4 + len + 4
        check("v1: frame length is 8 + payload length", frame.length == 8 + combined.length);
    }

    // ---- 3 empty payload ------------------------------------------------------

    private static void emptyRoundTrip() {
        byte[] frame = FrameV1.encode(new byte[0]);
        check("v1: empty payload round-trips (nextPos==8, payload.length==0)",
                frame.length == 8 && FrameV1.skip(frame, 0) == 8);
        FrameResult r = FrameV1.parse(frame, 0);
        check("v1: empty payload parse yields empty byte[] and nextPos==8",
                r != null && r.payload().length == 0 && r.nextPos() == 8);
    }

    // ---- 4 multi-frame buffer iteration --------------------------------------

    private static void multiFrameIteration() {
        byte[] f1 = FrameV1.encode("alpha".getBytes(StandardCharsets.UTF_8));
        byte[] f2 = FrameV1.encode("beta-中文".getBytes(StandardCharsets.UTF_8));
        byte[] f3 = FrameV1.encode("gamma".getBytes(StandardCharsets.UTF_8));
        byte[] buf = new byte[f1.length + f2.length + f3.length];
        System.arraycopy(f1, 0, buf, 0, f1.length);
        System.arraycopy(f2, 0, buf, f1.length, f2.length);
        System.arraycopy(f3, 0, buf, f1.length + f2.length, f3.length);

        List<byte[]> got = new ArrayList<>();
        for (FrameV1Iterator it = FrameV1Iterator.of(buf); it.hasNext(); ) {
            got.add(it.next());
        }
        check("v1Iterator: yields exactly 3 frames", got.size() == 3);
        check("v1Iterator: payloads in order (alpha, beta-中文, gamma)",
                new String(got.get(0), StandardCharsets.UTF_8).equals("alpha")
                        && new String(got.get(1), StandardCharsets.UTF_8).equals("beta-中文")
                        && new String(got.get(2), StandardCharsets.UTF_8).equals("gamma"));
        long nextPos = FrameV1.skip(buf, 0);
        check("v1Iterator: full walk consumed the whole buffer (no leftovers)",
                nextPos == f1.length && FrameV1.skip(buf, f1.length) == f1.length + f2.length
                        && FrameV1.skip(buf, f1.length + f2.length) == buf.length);
    }

    // ---- 5 truncated frame ----------------------------------------------------

    private static void truncatedFrame() {
        byte[] frame = FrameV1.encode("truncate-me".getBytes(StandardCharsets.UTF_8));
        for (int k = 1; k <= 3; k++) {
            byte[] cut = Arrays.copyOf(frame, frame.length - k);
            check("v1: truncated by " + k + " byte(s) -> parse null && skip -1",
                    FrameV1.parse(cut, 0) == null && FrameV1.skip(cut, 0) == -1);
        }
        byte[] emptyCut = Arrays.copyOf(frame, 7); // only 7 bytes left
        check("v1: short buffer (7 bytes) rejects both parse and skip",
                FrameV1.parse(emptyCut, 0) == null && FrameV1.skip(emptyCut, 0) == -1);
    }

    // ---- 6 dirty byte: crc mismatch vs length-only skip -----------------------

    private static void dirtyByteContract() {
        byte[] clean = "clean-payload".getBytes(StandardCharsets.UTF_8);
        byte[] frame = FrameV1.encode(clean);
        byte[] dirty = frame.clone();
        dirty[4] ^= 0x01; // flip one payload byte
        check("v1: dirty payload -> parse fails (crc mismatch)",
                FrameV1.parse(dirty, 0) == null);
        check("v1: dirty payload -> length-only skip still succeeds (crc/tsha1f layering contract)",
                FrameV1.skip(dirty, 0) == dirty.length);
    }

    // ---- 7 zd payload bridge round-trip --------------------------------------

    private static void zdBridgeRoundTrip() {
        List<ZdRow> rows = List.of(
                new ZdRow(0, "root", 0L, 0.0, "", 3),
                new ZdRow(1, "name", 0L, 0.0, "雨林地图", 0),
                new ZdRow(2, "seed", 42L, 0.0, "", 0),
                new ZdRow(3, "ratio", 0L, 2.5, "", 0));

        byte[] payload = ZdFrameBridge.buildPayload(0, rows);
        byte[] frame = FrameV1.encode(payload);
        FrameResult r = FrameV1.parse(frame, 0);
        boolean sameBytes = r != null && Arrays.equals(r.payload(), payload);
        check("zdBridge: zd doc bytes survive as a frame payload (byte-identical)",
                payload.length != 0 && FrameV1.skip(frame, 0) == frame.length && sameBytes);
        if (r == null) {
            return;
        }
        List<ZdRow> back = ZdFrameBridge.readRows(r.payload());
        boolean rowEq = back.size() == rows.size();
        if (rowEq) {
            for (int i = 0; i < rows.size(); i++) {
                ZdRow a = rows.get(i);
                ZdRow b = back.get(i);
                if (a.kind() != b.kind() || !a.key().equals(b.key())
                        || a.valueI64() != b.valueI64()
                        || Double.doubleToLongBits(a.valueF64()) != Double.doubleToLongBits(b.valueF64())
                        || !a.valueStr().equals(b.valueStr()) || a.childCount() != b.childCount()) {
                    rowEq = false;
                    break;
                }
            }
        }
        check("zdBridge: unwrap->readRows reproduces every ZdRow field (kind/key/i64/f64/str/child)",
                rowEq);
    }
}