package io.toterra.subterra.probes;

import io.toterra.subterra.engine.config.TdTable;
import io.toterra.subterra.engine.config.TdValue;
import io.toterra.subterra.engine.zd.ZdDocWriter;
import io.toterra.subterra.engine.zd.ZdFooter;
import io.toterra.subterra.engine.zd.ZdHeader;
import io.toterra.subterra.engine.zd.ZdRow;
import io.toterra.subterra.engine.zd.ZdVolume;

import java.util.Arrays;
import java.util.List;

/**
 * zd v3 载荷探针（p.2.3.7，纯 JVM、确定性）：对齐 {@code 2026-09-28-zd-v3-design.md}
 * 的增量面——v3 头（"03" + flags bit5/bit6）、索引 footer（魔数/crc32/段表）、v2+v3 读义务、
 * 与既有 wire2 往返恒等。exit 0 = PASS，exit 1 = FAIL；无时序、无随机；同输入同字节。
 * <ol>
 *   <li><b>头</b>：v3 默认写路径逐字节（"03" + 掩码低 7 位）；v2 兼容路径不变（"02"）；
 *       读义务 = v2+v3，v1/v4 拒。</li>
 *   <li><b>footer</b>：25 字节尾写读逐字节往返；crc32 覆盖索引段（翻一位即拒）；段表
 *       编解码往返（含类型 6 具名段与名称省略）；类型 6 空名确定性拒绝。</li>
 *   <li><b>索引读</b>：writeIndexed → 段类型 0 定位数据体 → 行级/树级往返恒等；
 *       footer 存在 + 头 bit5 一致；bit5 置位无 footer / footer 在 v2 载荷上确定性拒绝。</li>
 *   <li><b>兼容</b>：v2 文件（"02"）经 v3 读端照常读取（读取义务）；无 footer 文件
 *       尾探测返回 null。</li>
 * </ol>
 * <p>
 * zd v3 carrier probe (p.2.3.7, pure JVM, deterministic): covers the increment from
 * {@code 2026-09-28-zd-v3-design.md} — the v3 header ("03" + flags bit5/bit6), the
 * index footer (magic / crc32 / segment table), the v2+v3 reading obligation, and the
 * unchanged wire2 round-trip identity. exit 0 = PASS, exit 1 = FAIL; no timing, no
 * randomness; same input → same bytes.
 */
public final class ZdV3Probe {

    private static int checks = 0;
    private static int failures = 0;

    private ZdV3Probe() {
    }

    private static void check(String name, boolean ok) {
        checks++;
        if (ok) {
            System.out.println("[PASS] " + name);
        } else {
            failures++;
            System.out.println("[FAIL] " + name);
        }
    }

    private static boolean throwsIAE(Runnable r) {
        try {
            r.run();
            return false;
        } catch (IllegalArgumentException e) {
            return true;
        }
    }

    public static void main(String[] args) {
        headerChecks();
        footerChecks();
        indexedReadChecks();
        compatibilityChecks();
        if (failures == 0) {
            System.out.println("[ZdV3Probe] PASS (" + checks + " checks)");
            System.exit(0);
        } else {
            System.out.println("[ZdV3Probe] FAIL (" + failures + " of " + checks + " checks)");
            System.exit(1);
        }
    }

    // ---- 1 头 / header ----

    private static void headerChecks() {
        byte[] v3 = ZdHeader.writeZ(0);
        byte[] expect = {'T', 'I', 'E', 'D', 'B', 'Z', 'D', 0x00, 0x03, 0x00};
        check("v3: writeZ(int) 默认写 \"03\"（逐字节）", Arrays.equals(v3, expect));

        byte[] withBits = ZdHeader.writeZ(ZdHeader.FLAG_INDEX | ZdHeader.FLAG_GRAPH);
        check("v3: bit5/bit6 可置位且掩码 0x7F",
                (withBits[9] & 0xFF) == (ZdHeader.FLAG_INDEX | ZdHeader.FLAG_GRAPH)
                        && ZdHeader.writeZ(0xFF)[9] == (byte) ZdHeader.FLAGS_MASK_V3);

        check("v3: 读义务 v2+v3（isZd 双收、parseVersion 2/3、v1/v4 拒）",
                ZdHeader.isZd(v3) && ZdHeader.isZd(ZdHeader.writeV2(0))
                        && ZdHeader.parseVersion(v3, 0) == 3
                        && ZdHeader.parseVersion(ZdHeader.writeV2(0), 0) == 2
                        && !ZdHeader.isZd(new byte[]{'T', 'I', 'E', 'D', 'B', 'Z', 'D', 0x00, 0x01, 0x00})
                        && !ZdHeader.isZd(new byte[]{'T', 'I', 'E', 'D', 'B', 'Z', 'D', 0x00, 0x04, 0x00}));
    }

    // ---- 2 footer / 段表 ----

    private static void footerChecks() {
        // Segment table: mixed types, one named custom segment, one unnamed.
        List<ZdFooter.Segment> table = List.of(
                new ZdFooter.Segment(ZdFooter.SEG_DATA, 10, 32, ""),
                new ZdFooter.Segment(ZdFooter.SEG_GRAPH, 42, 8, ""),
                new ZdFooter.Segment(ZdFooter.SEG_CUSTOM, 50, 4, "extra"));
        byte[] index = ZdFooter.encodeIndex(table);
        byte[] decoded = ZdFooter.encodeIndex(ZdFooter.decodeIndex(index, 0, index.length));
        check("footer: 段表编解码往返逐字节", Arrays.equals(index, decoded));

        check("footer: 类型 6 空名确定性拒绝",
                throwsIAE(() -> ZdFooter.encodeIndex(List.of(new ZdFooter.Segment(
                        ZdFooter.SEG_CUSTOM, 0, 4, "")))));

        byte[] footer = ZdFooter.write(10, index);
        check("footer: 25 字节 + 魔数 ZD3FT",
                footer.length == ZdFooter.FOOTER_LEN && footer[0] == 'Z' && footer[4] == 'T');

        // Full-document probe round-trip: index bytes embedded, crc covers them.
        byte[] doc = new byte[10 + index.length + footer.length];
        byte[] head = ZdHeader.writeZ(ZdHeader.FLAG_INDEX);
        System.arraycopy(head, 0, doc, 0, 10);
        System.arraycopy(index, 0, doc, 10, index.length);
        System.arraycopy(footer, 0, doc, 10 + index.length, footer.length);
        ZdFooter probed = ZdVolume.footer(doc);
        check("footer: 尾探测解析（off/len/段表/crc 一致）",
                probed != null && probed.indexOff() == 10 && probed.indexLen() == index.length
                        && probed.segments().size() == 3
                        && probed.segment(ZdFooter.SEG_CUSTOM).name().equals("extra"));

        // Single flipped bit inside the index segment -> crc mismatch, deterministic IAE.
        byte[] tampered = doc.clone();
        tampered[10] ^= 0x01;
        check("footer: 索引段翻一位 crc 确定性拒绝", throwsIAE(() -> ZdVolume.footer(tampered)));

        // Footer without the header bit5 -> deterministic disagreement rejection.
        byte[] noBit = doc.clone();
        noBit[9] = 0x00;
        check("footer: footer 存在但头 bit5 清零确定性拒绝", throwsIAE(() -> ZdVolume.footer(noBit)));

        // No footer at all -> probe returns null.
        byte[] plain = ZdDocWriter.write(0, List.of(new ZdRow(2, "a", 1L, 0.0, "", 0)));
        check("footer: 无 footer 文件尾探测返回 null", ZdVolume.footer(plain) == null);
    }

    // ---- 3 索引读 / indexed read ----

    private static void indexedReadChecks() {
        TdTable doc = TdTable.builder()
                .put("name", TdValue.str("zd-v3"))
                .put("n", TdValue.of(42L))
                .put("f", TdValue.of(1.5D))
                .element(TdValue.str("bare"))
                .put("child", TdTable.builder().put("k", TdValue.of(7L)).build())
                .build();

        byte[] indexed = ZdDocWriter.writeTreeIndexed(0, doc);
        List<ZdRow> viaIndex = ZdVolume.readRows(indexed);
        List<ZdRow> direct = ZdVolume.readRows(ZdDocWriter.writeTree(0, doc));
        check("索引读: writeIndexed → 段类型 0 定位数据体 → 行级与无 footer 一致",
                viaIndex.equals(direct) && !viaIndex.isEmpty());

        // Tree-level identity at the byte level (TdTable is reference-equality, so the
        // rebuilt tree is verified by re-serialising it: write ∘ read ∘ write is
        // byte-identical to the original document).
        byte[] plainDoc = ZdDocWriter.writeTree(0, doc);
        check("索引读: 树级往返（重建树再写出与原正文逐字节一致）",
                Arrays.equals(ZdDocWriter.write(0, ZdVolume.readRows(indexed)), plainDoc));
        check("索引读: 头 bit5 置位 + 尾 footer 存在",
                (indexed[9] & 0xFF & ZdHeader.FLAG_INDEX) != 0 && ZdVolume.footer(indexed) != null);
        check("索引读: 同输入同字节（确定性）",
                Arrays.equals(indexed, ZdDocWriter.writeTreeIndexed(0, doc)));
        check("索引读: 带索引文档 = 无 footer 正文 + 索引段 + 25B 尾",
                indexed.length == plainDoc.length + indexLen(indexed) + ZdFooter.FOOTER_LEN);
    }

    /** 从带索引文档读出其索引段长度。 / Reads the index-segment length out of a footered document. */
    private static int indexLen(byte[] indexed) {
        return (int) ZdVolume.footer(indexed).indexLen();
    }

    // ---- 4 兼容 / compatibility ----

    private static void compatibilityChecks() {
        TdTable doc = TdTable.builder()
                .put("k", TdValue.of(-3L))
                .element(TdValue.str("v2"))
                .build();

        // v2 files stay readable through the v3 reader (reading obligation = v2 + v3).
        byte[] v2 = ZdDocWriter.writeTreeV2(0, doc);
        byte[] v3 = ZdDocWriter.writeTree(0, doc);
        check("兼容: v2 文件（\"02\"）经 v3 读端照常读取（行级一致）",
                ZdHeader.parseVersion(v2, 0) == 2
                        && ZdVolume.readRows(v2).equals(ZdVolume.readRows(v3)));

        // v3 footerless file: the body bytes are identical to v2 (the row format is
        // unchanged between v2 and v3 — only the header version differs).
        check("兼容: v3 无 footer 文件与 v2 正文逐字节一致（行格式不变）",
                Arrays.equals(sliceBody(v3), sliceBody(v2)));

        // A footer on a v2 payload is a deterministic rejection.
        byte[] v2WithFooter = Arrays.copyOf(v2, v2.length + ZdFooter.FOOTER_LEN);
        byte[] index = ZdFooter.encodeIndex(List.of(new ZdFooter.Segment(
                ZdFooter.SEG_DATA, 10, v2.length - 10, "")));
        byte[] footer = ZdFooter.write(10, index);
        System.arraycopy(footer, 0, v2WithFooter, v2.length, footer.length);
        check("兼容: v2 载荷上出现 footer 确定性拒绝", throwsIAE(() -> ZdVolume.readRows(v2WithFooter)));

        // bit5 declared but no footer -> deterministic rejection.
        byte[] bit5NoFooter = ZdDocWriter.write(ZdHeader.FLAG_INDEX, List.of(new ZdRow(2, "x", 1L, 0.0, "", 0)));
        check("兼容: 头 bit5 置位但无 footer 确定性拒绝", throwsIAE(() -> ZdVolume.readRows(bit5NoFooter)));
    }

    /** 去掉 10 字节头的正文切片。 / The body slice after the 10-byte header. */
    private static byte[] sliceBody(byte[] doc) {
        return Arrays.copyOfRange(doc, 10, doc.length);
    }
}
