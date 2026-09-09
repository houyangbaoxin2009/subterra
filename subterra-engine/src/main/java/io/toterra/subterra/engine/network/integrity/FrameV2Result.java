package io.toterra.subterra.engine.network.integrity;

import io.toterra.subterra.engine.network.frame.FrameConst;

/**
 * tink v2 帧编解码器携带的解析结果（p.2.4.2）。
 * <p>
 * The tink v2 frame parse result carried by the codec (p.2.4.2).
 *
 * @param ok          解析是否成功（含 v1 兼容帧）；false = 失败 / whether parsing succeeded (v1-compat frames count as ok); false = failure
 * @param flags       帧 flags 字节（v1 兼容帧为 0）/ frame flags byte (0 for v1-compat frames)
 * @param extLen      扩展头长度 / extended-header length
 * @param payload     载荷字节 / decoded payload bytes
 * @param ext         扩展头原始字节（TLV 序列）/ raw extended-header bytes (TLV sequence), possibly empty
 * @param integrityOk 校验是否一致（v2 快/强校验；v1 兼容帧为 crc 是否匹配）/ whether integrity verified (v2 fast/strong; crc match for v1-compat frames)
 * @param nextPos     下一帧起始位置；失败为 {@code -1} / next-frame start position, or {@code -1} on failure
 */
public record FrameV2Result(boolean ok, int flags, int extLen, byte[] payload, byte[] ext,
                            boolean integrityOk, long nextPos) {

    static FrameV2Result fail() {
        return new FrameV2Result(false, 0, 0, new byte[0], new byte[0], false, -1);
    }
}