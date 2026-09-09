package io.toterra.subterra.engine.network.frame;

import io.toterra.subterra.engine.zd.ZdDocWriter;
import io.toterra.subterra.engine.zd.ZdRow;
import io.toterra.subterra.engine.zd.ZdVolume;

import java.util.List;

/**
 * zd 帧载荷桥（p.2.4.1）：把 p.2.3 的 zd 文档字节作为帧载荷，在 zd 文档字节与帧载荷
 * {@code byte[]} 之间建立薄薄的、无解释的转换面。本子项只需要「zd 字节当帧载荷」加一个供探针
 * 用的往返；后续 v2 子项在桥之上叠加真正的帧结构。
 * <p>
 * A thin zd↔frame payload bridge (p.2.4.1): treats the p.2.3 zd document bytes as a
 * frame payload, providing a minimal, non-interpreting conversion surface between a
 * zd doc's bytes and the frame payload {@code byte[]}. This sub-item only needs
 * "zd bytes as a frame payload" plus a round-trip for the probe; later v2 sub-items
 * layer the real frame structure on top of this bridge.
 */
public final class ZdFrameBridge {

    private ZdFrameBridge() {
    }

    /**
     * 把一组 zd 行写成文档字节，作为帧载荷返回（等价于 {@code ZdDocWriter.write(flags, rows)}）。
     * Builds a zd document from {@code rows} with the given header {@code flags} and returns
     * its byte[] as a frame payload (equivalent to {@code ZdDocWriter.write(flags, rows)}).
     */
    public static byte[] buildPayload(int flags, List<ZdRow> rows) {
        return ZdDocWriter.write(flags, rows);
    }

    /**
     * 把一个帧载荷字节解析回 {@link ZdRow} 列表（委托 {@code ZdVolume.readRows}）。
     * Parses a frame payload back into a list of {@link ZdRow} (delegates to
     * {@code ZdVolume.readRows}).
     */
    public static List<ZdRow> readRows(byte[] payload) {
        return ZdVolume.readRows(payload);
    }
}