package io.toterra.subterra.engine.network.frame;

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * v1 帧迭代器（p.2.4.1）：基于 {@link FrameV1#skip} 的零拷贝跳帧遍历一个字节缓冲里的
 * 全部帧，依序产出（载荷）{@code byte[]}；到缓冲末尾干净停下，遇到无效/截断帧同样干净结束
 * （不再抛、不产出该帧）。遍历只走边界、不做 crc 校验（与 read 回退的语义一致）。
 * <p>
 * A v1 frame iterator (p.2.4.1): walks all frames in a {@code byte[]} buffer via the
 * {@link FrameV1#skip} zero-copy length-based jump, yielding each {@code byte[]} payload
 * in order; it stops cleanly at the buffer end and also stops cleanly (without throwing
 * or emitting) on an invalid/truncated frame. Walking is boundary-only and crc-free,
 * matching the compatible-read fallback semantics.
 */
public final class FrameV1Iterator implements Iterator<byte[]> {

    private final byte[] buf;
    private long pos;
    private byte[] next;

    private FrameV1Iterator(byte[] buf) {
        this.buf = buf == null ? new byte[0] : buf;
        this.pos = 0L;
        advance();
    }

    /**
     * 从 {@code buf} 起始构造迭代器（扫描到第一个帧）。Constructs the iterator at the
     * start of {@code buf} (pre-scanned to the first frame).
     */
    public static FrameV1Iterator of(byte[] buf) {
        return new FrameV1Iterator(buf);
    }

    /** 是否存在下一个帧。Returns whether another frame is pending. */
    @Override
    public boolean hasNext() {
        return next != null;
    }

    /** 取出下一个帧的载荷字节。返回后推进游标；无更多帧抛 {@link NoSuchElementException}。 */
    @Override
    public byte[] next() {
        if (next == null) {
            throw new NoSuchElementException("no more v1 frames");
        }
        byte[] out = next;
        next = null;
        advance();
        return out;
    }

    /** 不支持删除。Removal is not supported. */
    @Override
    public void remove() {
        throw new UnsupportedOperationException("FrameV1Iterator does not permit removal");
    }

    private void advance() {
        if (next != null || pos >= buf.length) {
            return;
        }
        long frameStart = pos;
        long nextPos = FrameV1.skip(buf, pos);
        if (nextPos < 0) {
            return; // invalid / truncated: stop cleanly
        }
        int len = (int) (nextPos - frameStart - FrameV1.LEN_BYTES - FrameV1.CRC_BYTES);
        byte[] p = new byte[len];
        System.arraycopy(buf, (int) (frameStart + FrameV1.LEN_BYTES), p, 0, len);
        next = p;
        pos = nextPos;
    }
}