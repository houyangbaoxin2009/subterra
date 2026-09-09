package io.toterra.subterra.engine.network.frame;

import java.io.ByteArrayOutputStream;

/**
 * tie v1 帧编解码器（p.2.4.1）：{@link #encode} 封帧、{@link #parse} 解帧（校验 crc）、
 * {@link #skip} 只读长度零拷贝跳帧。解析失败一律返回 null / {@code -1}，绝不抛异常。
 * <p>
 * The tie v1 frame codec (p.2.4.1): {@link #encode} frames a payload,
 * {@link #parse} decodes a frame (verifying crc), and {@link #skip} advances by
 * length only for zero-copy walking. Parse failures always return null / {@code -1},
 * never throw.
 */
public final class FrameV1 {

    /** 固定头部：4 字节长度。Fixed framing headers: 4-byte length prefix. */
    static final int LEN_BYTES = 4;
    /** 固定尾部：4 字节 crc。Fixed framing tail: 4-byte crc. */
    static final int CRC_BYTES = 4;

    private FrameV1() {
    }

    /**
     * 把 {@code payload} 封成 v1 帧（长度前缀 + 载荷 + crc）。{@code null} 视为空载荷。
     * Frames {@code payload} as a v1 frame (length prefix + payload + crc); {@code null}
     * is treated as an empty payload.
     */
    public static byte[] encode(byte[] payload) {
        byte[] p = payload == null ? new byte[0] : payload;
        ByteArrayOutputStream out = new ByteArrayOutputStream(LEN_BYTES + p.length + CRC_BYTES);
        writeBe32(out, p.length);
        out.write(p, 0, p.length);
        writeBe32(out, Crc32Ieee.of(p));
        return out.toByteArray();
    }

    /**
     * 从 {@code buf} 的 {@code pos} 起解析一枚 v1 帧。成功返回载荷与下一帧位置；长度不足 /
     * 负长度 / crc 不匹配返回 {@code null}；绝不抛异常。
     * Parses one v1 frame from {@code buf} at {@code pos}. On success returns the payload
     * and the next-frame position; insufficient bytes / negative length / crc mismatch
     * return {@code null}; never throws.
     */
    public static FrameResult parse(byte[] buf, long pos) {
        if (buf == null || pos < 0 || pos > buf.length) {
            return null;
        }
        if (buf.length - pos < LEN_BYTES + CRC_BYTES) {
            return null; // even an empty frame needs 8 bytes
        }
        int len = readBe32(buf, pos);
        if (len < 0) {
            return null;
        }
        long payloadOff = pos + LEN_BYTES;
        if (buf.length - payloadOff < (long) len + CRC_BYTES) {
            return null; // truncated frame
        }
        int stored = readBe32(buf, payloadOff + len);
        int actual = Crc32Ieee.of(buf, (int) payloadOff, len);
        if (stored != actual) {
            return null; // crc mismatch
        }
        byte[] payload = new byte[len];
        System.arraycopy(buf, (int) payloadOff, payload, 0, len);
        return new FrameResult(payload, payloadOff + len + CRC_BYTES);
    }

    /**
     * 只读长度的零拷贝跳帧：返回下一帧位置 {@code nextPos}，或 {@code -1}。不做 crc 校验，
     * 仅做长度边界约束。是 {@link FrameV1Iterator} 零分配遍历的底层原语。
     * Zero-copy length-only skip: returns the next-frame position {@code nextPos}, or
     * {@code -1}. No crc verification — only length-bound validation. This is the
     * allocation-free primitive behind {@link FrameV1Iterator}.
     */
    public static long skip(byte[] buf, long pos) {
        if (buf == null || pos < 0 || pos > buf.length) {
            return -1;
        }
        if (buf.length - pos < LEN_BYTES) {
            return -1;
        }
        int len = readBe32(buf, pos);
        if (len < 0) {
            return -1;
        }
        long payloadOff = pos + LEN_BYTES;
        if (buf.length - payloadOff < (long) len + CRC_BYTES) {
            return -1; // truncated frame
        }
        return payloadOff + len + CRC_BYTES;
    }

    private static int readBe32(byte[] b, long off) {
        return ((b[(int) off] & 0xFF) << 24)
                | ((b[(int) off + 1] & 0xFF) << 16)
                | ((b[(int) off + 2] & 0xFF) << 8)
                | (b[(int) off + 3] & 0xFF);
    }

    private static void writeBe32(ByteArrayOutputStream out, long n) {
        out.write((int) ((n >> 24) & 0xFF));
        out.write((int) ((n >> 16) & 0xFF));
        out.write((int) ((n >> 8) & 0xFF));
        out.write((int) (n & 0xFF));
    }
}