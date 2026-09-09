package io.toterra.subterra.engine.worldgen.profiler.report;

import io.toterra.subterra.api.worldgen.profiler.ProfileReport;
import io.toterra.subterra.engine.config.TdTable;
import io.toterra.subterra.engine.zd.ZdDocWriter;

/**
 * Writes a {@link ProfileReport} as z-file (zd) binary (p.1.8.30). The file is
 * produced from the same {@link ProfileTree} as {@link TdWriter}, so a zd
 * payload always corresponds one-to-one with the td text of the same report.
 * <p>
 * Layout: a 10-byte header {@code "TIEDBZD" + 0x00 0x02 + flags} (base-48 version
 * digits stored as raw digit bytes: v2 = digits d1=0, d2=2 -> bytes 0x00 0x02, per the
 * pinned zd v2 header spec), followed by the
 * flattened tree as a sequence of wire2 records. Field
 * numbers are 1 = kind, 2 = key, 3 = value_i64, 4 = value_f64, 5 = value_str,
 * 6 = child_count, giving tags 10 / 18 / 26 / 34 / 42 / 50.
 * <p>
 * <b>p.2.3.1 refactor:</b> the byte-building logic (header / {@code enc_i64} / varint /
 * UTF-8 / big-endian markers and the DFS preorder flatten) has been lifted into the
 * generic {@code engine.zd} carrier ({@code ZdHeader} / {@code ZdPrimitives} /
 * {@code ZdDocWriter}); this class now delegates to {@link ZdDocWriter#writeTree}
 * with flags 0. The produced bytes are unchanged — {@link ZdWriter} output is still
 * the pinned vector anchor checked by {@code WorldProfileProbe} (10-byte
 * {@code TIEDBZD} v2 header and the first record's kind tag 0x0A at byte 10).
 * <p>
 * 把 {@link ProfileReport} 写为 zd 二进制（p.1.8.30）。文件由与 {@link TdWriter} 相同的
 * 一棵 {@link ProfileTree} 生成，因此 zd 载荷总是与同一报告对应的 td 文本一一对应。
 * <p>
 * 布局：10 字节头 {@code "TIEDBZD" + 0x00 0x02 + flags}（base-48 版本数字按原始进制字节
 * 存储：v2 = 数字 d1=0、d2=2 → 字节 0x00 0x02，遵循已定稿的 zd v2 头规范），随后是按深度
 * 优先先序平铺的树，以一串 wire2 记录呈现。字段号为 1 = kind、2 = key、3 = value_i64、4 =
 * value_f64、5 = value_str、6 = child_count，对应 tag 10 / 18 / 26 / 34 / 42 / 50。
 * <p>
 * <b>p.2.3.1 重构：</b>字节构建逻辑（头 / {@code enc_i64} / varint / UTF-8 / 大端标记与
 * DFS 先序平铺）已上移到通用 {@code engine.zd} 载体（{@code ZdHeader} /
 * {@code ZdPrimitives} / {@code ZdDocWriter}）；本类改为以 flags 0 委托
 * {@link ZdDocWriter#writeTree}。产出的字节不变，{@link ZdWriter} 输出仍是
 * {@code WorldProfileProbe} 校验的钉点向量（10 字节 {@code TIEDBZD} v2 头 + 字节 10 处的
 * 首记录 kind tag 0x0A）。
 * <p>
 * <b>Deviation notice:</b> the reference {@code compiler/tdzd.tie} packs whole
 * columns as six aggregate array fields plus per-value zd type markers. This
 * implementation instead follows design document section 8's "fixed six fields"
 * row-per-node record (no per-scalar type markers, f64 field is a bare 8-byte
 * bit pattern), and it is therefore the vector anchor that the downstream probe
 * checks against.
 * <p>
 * <b>偏离说明：</b>参考 {@code compiler/tdzd.tie} 把整列打包成六个聚合数组字段并带逐值类型
 * 标记。本实现改为遵循设计文档第 8 节的"固定六字段"逐节点记录（无逐标量的类型标记，f64
 * 字段为裸 8 字节位模式），因此本实现是下游探针校验其向量的锚点。
 */
public final class ZdWriter {

    private ZdWriter() {
    }

    /**
     * Produces the full zd byte stream (header + flattened records) for a report.
     * <p>
     * 为一个报告生成完整的 zd 字节流（头部 + 平铺记录）。
     *
     * @param r the completed report.
     * @return the zd serialization.
     */
    public static byte[] write(ProfileReport r) {
        TdTable root = ProfileTree.toTd(r);
        return ZdDocWriter.writeTree(0, root);
    }
}