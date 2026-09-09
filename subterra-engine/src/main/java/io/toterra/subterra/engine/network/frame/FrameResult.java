package io.toterra.subterra.engine.network.frame;

/**
 * v1 帧解析结果载体（p.2.4.1）：{@code FrameV1.parse} 返回的载荷与下一帧位置。
 * <p>
 * The v1 frame parse result (p.2.4.1): the payload and next-frame position returned
 * by {@code FrameV1.parse}.
 *
 * @param payload 帧载荷字节 / the decoded frame payload bytes
 * @param nextPos 下一帧起始位置；{@code -1} 表示失败 / the next-frame position, or {@code -1} on failure
 */
public record FrameResult(byte[] payload, long nextPos) {
}