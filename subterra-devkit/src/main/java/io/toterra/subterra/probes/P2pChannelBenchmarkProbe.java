package io.toterra.subterra.probes;

import io.toterra.subterra.engine.network.frame.FrameConst;
import io.toterra.subterra.engine.network.integrity.FrameV2;
import io.toterra.subterra.engine.network.integrity.FrameV2Result;
import io.toterra.subterra.engine.zd.ZdDocWriter;
import io.toterra.subterra.engine.zd.ZdRow;
import io.toterra.subterra.engine.zd.ZdVolume;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * p.2.5.1 P2P 去中心化网络 · 可行性基准探针（纯 JVM，无 MC 运行时）：量化
 * <ol>
 *   <li><b>zd 载荷编码体积</b> — 三档典型载荷（小型状态元数据 / 中档表数据 / 大 chunk 级数据，
 *       固定样本 + 固定种子）的 raw 内容字节 vs {@code ZdDocWriter} zd 文档字节的体积比；</li>
 *   <li><b>zd 编解码吞吐</b> — 固定迭代次数内的编 / 解码 ops/ms（<b>只记录数字不断言</b>）；</li>
 *   <li><b>tink v2 帧开销</b> — payload 若干档 × 快校验 / 强校验两档 → 线缆字节、额外开销字节
 *       与百分比（线缆长 = payload + 10 头 + ext + 校验段，空 ext 下断言）；</li>
 *   <li><b>组合端到端</b> — zd payload → {@code FrameV2}（强校验）→ parse → zd decode，
 *       往返字节一致（断言）。</li>
 * </ol>
 * 确定性纪律：数据全部由固定种子伪随机生成；失败计数只在失败路径自增；<b>无时序断言</b>；
 * 吞吐列只输出数字，不进入校验。退出码 0 = PASS，1 = FAIL。
 * <p>
 * p.2.5.1 P2P decentralised-network feasibility benchmark probe (pure JVM — no Minecraft runtime):
 * quantifies (1) zd payload encoding volume — the raw-content-vs-zd-doc byte ratio for three
 * representative payloads (small state metadata / medium table data / large chunk data, fixed
 * samples + fixed seed); (2) zd encode/decode throughput in ops/ms over a fixed iteration count
 * (numbers recorded only, never asserted); (3) tink v2 frame overhead — several payload sizes ×
 * fast/strong integrity → wire bytes, overhead bytes and percent (asserted when ext is empty);
 * (4) a combined end-to-end chain zd → FrameV2(strong) → parse → zd decode with byte-identical
 * round-trip (asserted). Deterministic: data generated from a fixed seed; failure counters advance
 * only on failure; no timing assertions; throughput lines are printed but never checked.
 * Exit 0 = PASS all checks; 1 = FAIL.
 */
public final class P2pChannelBenchmarkProbe {

    private P2pChannelBenchmarkProbe() {
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

    /** 只记录数字不断言 / figures only, never asserted. */
    private static void bench(String name, Object value) {
        System.out.println("[BENCH] " + name + " = " + value);
    }

    public static void main(String[] args) {
        List<ZdRow> small = sample(SampleKind.SMALL_META);
        List<ZdRow> medium = sample(SampleKind.MEDIUM_TABLES);
        List<ZdRow> large = sample(SampleKind.LARGE_CHUNKS);

        zdVolume(small, "small-state-metadata");
        zdVolume(medium, "medium-table-data");
        zdVolume(large, "large-chunk-data");

        zdThroughput(medium, "medium-table-data");

        frameOverhead();

        combinedChain(medium);

        if (failures == 0) {
            System.out.println("[P2pChannelBenchmarkProbe] PASS: p.2.5.1 feasibility benchmark ("
                    + checks + " checks)");
            System.exit(0);
        } else {
            System.out.println("[P2pChannelBenchmarkProbe] FAIL: " + failures + " failure(s) of " + checks);
            System.exit(1);
        }
    }

    // ================= 典型载荷样本（固定种子，确定性） =================

    private enum SampleKind { SMALL_META, MEDIUM_TABLES, LARGE_CHUNKS }

    /** 固定种子生成三类数据：small=k 张元数据表+少量标量；medium=若干表 + 中量行；large=大量 chunk 行。 */
    private static List<ZdRow> sample(SampleKind kind) {
        Random r = new Random(0x5EEDL | kind.ordinal());
        List<ZdRow> out = new ArrayList<>();
        switch (kind) {
            case SMALL_META -> {
                out.add(new ZdRow(0, "world", 0L, 0.0, "", 6));
                out.add(new ZdRow(1, "name", 0L, 0.0, "toterra-w", 0));
                out.add(new ZdRow(2, "seed", 42L, 0.0, "", 0));
                out.add(new ZdRow(3, "time", 0L, 123450.5, "", 0));
                out.add(new ZdRow(2, "gamemode", 1L, 0.0, "", 0));
                out.add(new ZdRow(2, "difficulty", 2L, 0.0, "", 0));
                out.add(new ZdRow(2, "thunder", 0L, 0.0, "", 0));
            }
            case MEDIUM_TABLES -> {
                out.add(new ZdRow(0, "server", 0L, 0.0, "", 3));
                out.add(new ZdRow(0, "players", 0L, 0.0, "", 64));
                for (int i = 0; i < 64; i++) {
                    out.add(new ZdRow(2, "hp", i % 20L, 0.0, "", 0));
                    out.add(new ZdRow(2, "x", (long) r.nextInt(2000), 0.0, "", 0));
                    out.add(new ZdRow(2, "z", (long) r.nextInt(2000), 0.0, "", 0));
                    out.add(new ZdRow(3, "yaw", 0L, r.nextDouble() * 360, "", 0));
                    out.add(new ZdRow(3, "pitch", 0L, r.nextDouble() * 90 - 45, "", 0));
                }
                out.add(new ZdRow(0, "tables", 0L, 0.0, "", 12));
                for (int t = 0; t < 12; t++) {
                    out.add(new ZdRow(1, "name", 0L, 0.0, "region-" + t, 0));
                    out.add(new ZdRow(2, "count", (long) (100 + r.nextInt(4000)), 0.0, "", 0));
                    out.add(new ZdRow(3, "density", 0L, r.nextDouble(), "", 0));
                }
            }
            case LARGE_CHUNKS -> {
                out.add(new ZdRow(0, "chunks", 0L, 0.0, "", 4096));
                for (int i = 0; i < 4096; i++) {
                    out.add(new ZdRow(2, "cx", (long) i, 0.0, "", 0));
                    out.add(new ZdRow(2, "cy", (long) r.nextInt(16), 0.0, "", 0));
                    out.add(new ZdRow(2, "tick", (long) r.nextInt(60000), 0.0, "", 0));
                    out.add(new ZdRow(3, "activity", 0L, r.nextDouble(), "", 0));
                    out.add(new ZdRow(1, "owner", 0L, 0.0, "p" + (i % 50), 0));
                }
            }
        }
        return List.copyOf(out);
    }

    /** 一行中实际承载内容的字节（key + 数值 + 字符串，不含 10B 头 / tag / 长度前缀）。 */
    private static int rawRowContentBytes(ZdRow row) {
        return row.key().getBytes(java.nio.charset.StandardCharsets.UTF_8).length
                + io.toterra.subterra.engine.zd.ZdPrimitives.encI64(row.valueI64()).length
                + io.toterra.subterra.engine.zd.ZdPrimitives.be64(Double.doubleToLongBits(row.valueF64())).length
                + row.valueStr().getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
    }

    // ================= 1 zd 载荷编码体积 =================

    /** 每行六字段固定 wire2（含空字段也写 tag+len）。 */
    private static int zdWireOverheadPerRow() {
        // 六字段的 tag(1) + len(1) 最小开销 × 6，外加 10 字节头摊销在 write 里单独算。
        return 2 * 6;
    }

    private static void zdVolume(List<ZdRow> rows, String label) {
        byte[] zd = ZdDocWriter.write(0, rows);
        int raw = 0;
        for (ZdRow r : rows) {
            raw += rawRowContentBytes(r);
        }
        int framing = raw + rows.size() * zdWireOverheadPerRow();
        double ratio = (double) raw / zd.length; // < 1：zd 的体积比（头+字段 wire 开销）
        double allWire = (double) framing / zd.length;
        bench(label + " rows", rows.size());
        bench(label + " raw-content-bytes", raw);
        bench(label + " zd-doc-bytes", zd.length);
        bench(label + " raw-vs-zd-ratio", String.format("%.4f", ratio));
        bench(label + " content+frame-vs-zd-ratio", String.format("%.4f", allWire));
        check(label + ": zd doc bytes >= raw content bytes (framing is non-negative)",
                zd.length >= raw && raw >= 0);
        check(label + ": raw-vs-zd ratio in (0, 1] (zd carries content + header/wire overhead)",
                ratio > 0.0 && ratio <= 1.0);
    }

    // ================= 2 zd 编解码吞吐（只记录，不断言） =================

    private static void zdThroughput(List<ZdRow> rows, String label) {
        byte[] zd = ZdDocWriter.write(0, rows);
        int iter = 2000;
        // encode
        int encIters = iter;
        long t0 = System.nanoTime();
        for (int i = 0; i < encIters; i++) {
            ZdDocWriter.write(0, rows);
        }
        long tEncode = System.nanoTime() - t0;
        double encOpsMs = encIters * 1_000_000.0 / tEncode;
        // decode
        int decIters = iter;
        long t1 = System.nanoTime();
        for (int i = 0; i < decIters; i++) {
            ZdVolume.readRows(zd);
        }
        long tDecode = System.nanoTime() - t1;
        double decOpsMs = decIters * 1_000_000.0 / tDecode;
        bench(label + " zd-encode-ops-per-ms", String.format("%.1f", encOpsMs));
        bench(label + " zd-decode-ops-per-ms", String.format("%.1f", decOpsMs));
        // 数字不进入断言：无时序断言。
    }

    // ================= 3 tink v2 帧开销 =================

    private static void frameOverhead() {
        int[] sizes = {256, 1024, 65536, 1048576};
        for (int size : sizes) {
            byte[] payload = filler(size, 0x5EED + (long) size);
            byte[] fastWire = FrameV2.encode(payload, 0, null);
            byte[] strongWire = FrameV2.encode(payload, FrameConst.FLAG_STRONG, null);

            int fastOverhead = fastWire.length - payload.length;
            int strongOverhead = strongWire.length - payload.length;
            double fastPct = 100.0 * fastOverhead / payload.length;
            double strongPct = 100.0 * strongOverhead / payload.length;

            bench("frame payload-" + size + " fast-wire-bytes", fastWire.length);
            bench("frame payload-" + size + " fast-overhead-bytes", fastOverhead);
            bench("frame payload-" + size + " fast-overhead-pct", String.format("%.4f", fastPct));
            bench("frame payload-" + size + " strong-wire-bytes", strongWire.length);
            bench("frame payload-" + size + " strong-overhead-bytes", strongOverhead);
            bench("frame payload-" + size + " strong-overhead-pct", String.format("%.4f", strongPct));

            // 空 ext 下线缆长 == payload + 10 头 + 校验段（快 8 / 强 32）。
            check("frame payload-" + size + ": fast wire == payload + 10 + 8",
                    fastWire.length == payload.length + 10 + FrameConst.INTEGRITY_FAST_BYTES);
            check("frame payload-" + size + ": strong wire == payload + 10 + 32",
                    strongWire.length == payload.length + 10 + FrameConst.INTEGRITY_STRONG_BYTES);
        }
        // 大 payload 放大倍数：1 MiB 强校验线缆 = 1 MiB + 42。
        byte[] big = filler(1048576, 0xB16);
        byte[] wire = FrameV2.encode(big, FrameConst.FLAG_STRONG, null);
        check("frame 1MiB strong: absolute overhead == 42 bytes", wire.length - big.length == 42);
    }

    /** 确定性填充字节（固定种子，二进制安全含 0x00/0xFF）。 */
    private static byte[] filler(int size, long seed) {
        byte[] out = new byte[size];
        Random r = new Random(seed);
        r.nextBytes(out);
        return out;
    }

    // ================= 4 组合端到端：zd -> FrameV2(强) -> parse -> zd decode =================

    private static void combinedChain(List<ZdRow> rows) {
        byte[] zd = ZdDocWriter.write(0, rows);
        byte[] frame = FrameV2.encode(zd, FrameConst.FLAG_STRONG, null);
        FrameV2Result r = FrameV2.parse(frame, 0);
        check("combined: FrameV2(strong) parse ok && integrityOk && nextPos==frame.length",
                r != null && r.ok() && r.integrityOk() && r.nextPos() == frame.length);

        boolean samePayload = r != null
                && Arrays.equals(r.payload(), zd);
        check("combined: frame payload byte-identical to the original zd doc", samePayload);

        if (r == null || !samePayload) {
            return;
        }
        List<ZdRow> back = ZdVolume.readRows(r.payload());
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
        check("combined: zd decode reproduces every ZdRow field (kind/key/i64/f64/str/child)", rowEq);
        bench("combined zd-bytes", zd.length);
        bench("combined frame(strong)-bytes", frame.length);
        bench("combined frame-overhead-bytes", frame.length - zd.length);
    }
}