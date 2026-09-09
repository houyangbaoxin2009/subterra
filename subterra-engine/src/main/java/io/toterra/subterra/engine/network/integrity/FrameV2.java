package io.toterra.subterra.engine.network.integrity;

import io.toterra.subterra.engine.network.frame.Crc32Ieee;
import io.toterra.subterra.engine.network.frame.FrameConst;
import io.toterra.subterra.engine.network.frame.FrameResult;
import io.toterra.subterra.engine.network.frame.FrameV1;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * tink v2 帧编解码器（p.2.4.2），镜像 tie-main {@code std/tink_v2.tie} 命名空间 {@code tink2}
 * 的线格式与语义：
 * <pre>
 *   帧 = [magic u8[2]="tk"][version u8=2][flags u8][len u32 BE][ext_len u16 BE]
 *        [ext: ext_len 字节][payload: len 字节][integrity: 定长]
 * </pre>
 * flags bit0 = INTEGRITY_STRONG：0 → 快校验段 8 字节 = `from_ascii(tsha1f(payload,8,48))`；
 * 1 → 强校验段 32 字节 = digest 全宽 hex 转字节取前 32（模型/位宽按 ext key1/key2 覆盖）。
 * 保留位 bit5..7 必须为 0 否则帧被拒绝。首两字节非 magic 时走 v1 兼容读，委托
 * {@link FrameV1} 的 crc 校验，{@code integrityOk} 即 crc 是否匹配。升层契约：crc 为快速
 * 头部通过，tsha1f 为强校验关卡。未知 ext TLV 键解析时一律跳过（绝不对未知键失败）。
 * <p>
 * The tink v2 frame codec (p.2.4.2), mirroring tie-main {@code std/tink_v2.tie} (namespace
 * {@code tink2}) wire format and semantics. bit0 = INTEGRITY_STRONG: 0 → the fast-integrity
 * slot is 8 bytes = {@code from_ascii(tsha1f(payload,8,48))}; 1 → the strong slot is 32 bytes
 * = the digest full-width hex converted to bytes and truncated to its first 32 (model/bits
 * overridable via ext key1/key2). Reserved bits bit5..7 must be 0 or the frame is rejected.
 * When the first two bytes are not magic, parsing falls back to a v1-compatible read that
 * delegates to {@link FrameV1}'s crc check, and {@code integrityOk} equals whether the crc
 * matched. Layering contract: crc is the fast header pass, tsha1f the strong pass. Unknown
 * ext TLV keys are always skipped (never fail on an unknown key).
 *
 * <p>Also provides the streaming helpers mirroring tie: {@link #extStreamChunk},
 * {@link #streamChunkMake}, {@link #streamSplit} and {@link #streamJoin}.
 */
public final class FrameV2 {

    // 固定头部（magic 2 + version 1 + flags 1 + len 4 + ext_len 2 = 10）
    static final int HEADER_BYTES = 10;

    private FrameV2() {
    }

    // ================= 帧编码 / encoding =================

    /**
     * 把 {@code payload} 封成 tink v2 帧。bit0 置位走强校验，否则走快校验。{@code ext}
     * 可为 {@code null} 视为空扩展头；强校验按 ext key1/key2 覆盖模型/位宽（缺省 f/48）。
     * Frames {@code payload} as a tink v2 frame. bit0 selects strong integrity, else fast
     * integrity. {@code ext} may be {@code null} (treated as empty); strong integrity honors
     * ext key1/key2 for model/bits (default f/48).
     */
    public static byte[] encode(byte[] payload, int flags, byte[] ext) {
        byte[] p = payload == null ? new byte[0] : payload;
        byte[] e = ext == null ? new byte[0] : ext;
        byte[] integ;
        if ((flags & FrameConst.FLAG_STRONG) != 0) {
            integ = strongIntegrity(p, e);
        } else {
            integ = fastIntegrity(p);
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream(HEADER_BYTES + e.length + p.length + integ.length);
        out.write(FrameConst.MAGIC_FIRST);
        out.write(FrameConst.MAGIC_SECOND);
        out.write(FrameConst.FRAME_VERSION);
        out.write(flags & 0xFF);
        writeBe32(out, p.length);
        writeBe16(out, e.length);
        out.write(e, 0, e.length);
        out.write(p, 0, p.length);
        out.write(integ, 0, integ.length);
        return out.toByteArray();
    }

    /** 快校验段：ASCII 字节 of {@code tsha1f(payload, 8, 48)}，恰 8 字节。Fast slot: ASCII of tsha1f(p,8,48), 8 bytes. */
    static byte[] fastIntegrity(byte[] payload) {
        byte[] p = payload == null ? new byte[0] : payload;
        return fromAscii(Tsha1f.tsha1f(p, 8, 48));
    }

    /** 强校验段：digest 全宽 hex 转字节取前 32；ext key1(模型)/key2(位宽) 覆盖，缺省 f/48。Strong slot. */
    static byte[] strongIntegrity(byte[] payload, byte[] ext) {
        byte[] p = payload == null ? new byte[0] : payload;
        byte[] e = ext == null ? new byte[0] : ext;
        String model = "f";
        int m = extU8(e, FrameConst.EXT_TSHA1_MODEL);
        if (m == 1) {
            model = "r";
        } else if (m == 2) {
            model = "b";
        } else if (m == 3) {
            model = "x";
        }
        int nb = extU8(e, FrameConst.EXT_TSHA1_BITS);
        int nbits = 48;
        if (nb >= 2 && nb <= 200) {
            nbits = nb;
        }
        String dh = Tsha1f.digestHex(model, p, nbits);
        byte[] db = hexBytes(dh);
        byte[] out = new byte[FrameConst.INTEGRITY_STRONG_BYTES];
        System.arraycopy(db, 0, out, 0, Math.min(db.length, FrameConst.INTEGRITY_STRONG_BYTES));
        return out; // 尾不足（状态池不足，正常不应发生）为 0
    }

    // ================= 帧解析 / parsing =================

    /**
     * 从 {@code buf} 的 {@code pos} 起解析一枚帧（v2 或 v1 兼容）。绝不抛异常；失败返回
     * {@code ok=false,nextPos=-1}。v2 检查版本=2、保留位=0、缓冲足够，并重算校验段比对。
     * 首两字节非 magic → 委托 {@link FrameV1#parse} 的 crc 校验，{@code integrityOk} = crc 匹配。
     * Parses one frame (v2 or v1-compat) from {@code buf} at {@code pos}; never throws.
     * On failure returns {@code ok=false, nextPos=-1}. v2 verifies version==2, reserved
     * bits clear and sufficient bytes, then recomputes the integrity slot. When the first
     * two bytes are not magic, it delegates to {@link FrameV1#parse}'s crc check, with
     * {@code integrityOk} = whether the crc matched.
     */
    public static FrameV2Result parse(byte[] buf, long pos) {
        if (buf == null || pos < 0 || pos > buf.length) {
            return FrameV2Result.fail();
        }
        if (buf.length - pos < 2) {
            return FrameV2Result.fail();
        }
        boolean isV2 = (buf[(int) pos] & 0xFF) == FrameConst.MAGIC_FIRST
                && (buf[(int) pos + 1] & 0xFF) == FrameConst.MAGIC_SECOND;
        if (isV2) {
            return parseV2(buf, pos);
        }
        return parseV1(buf, pos);
    }

    private static FrameV2Result parseV2(byte[] buf, long pos) {
        int nb = buf.length;
        if (nb - pos < HEADER_BYTES) {
            return FrameV2Result.fail();
        }
        int ver = buf[(int) pos + 2] & 0xFF;
        if (ver != FrameConst.FRAME_VERSION) {
            return FrameV2Result.fail();
        }
        int fl = buf[(int) pos + 3] & 0xFF;
        if ((fl & FrameConst.FLAG_RESERVED_MASK) != 0) {
            return FrameV2Result.fail();
        }
        int ln = readBe32(buf, pos + 4);
        int el = readBe16(buf, pos + 8);
        if (ln < 0) {
            return FrameV2Result.fail();
        }
        int integLen = ((fl & FrameConst.FLAG_STRONG) != 0)
                ? FrameConst.INTEGRITY_STRONG_BYTES : FrameConst.INTEGRITY_FAST_BYTES;
        long total = pos + HEADER_BYTES + el + (long) ln + integLen;
        if (total > nb) {
            return FrameV2Result.fail();
        }
        int pstart = (int) (pos + HEADER_BYTES + el);
        byte[] payload = new byte[ln];
        System.arraycopy(buf, pstart, payload, 0, ln);
        byte[] ext = new byte[el];
        System.arraycopy(buf, (int) (pos + HEADER_BYTES), ext, 0, el);
        byte[] stored = new byte[integLen];
        System.arraycopy(buf, pstart + ln, stored, 0, integLen);

        byte[] computed;
        if ((fl & FrameConst.FLAG_STRONG) != 0) {
            computed = strongIntegrity(payload, ext);
        } else {
            computed = fastIntegrity(payload);
        }
        boolean iok = java.util.Arrays.equals(computed, stored);
        return new FrameV2Result(true, fl, el, payload, ext, iok, pos + HEADER_BYTES + el + (long) ln + integLen);
    }

    private static FrameV2Result parseV1(byte[] buf, long pos) {
        FrameResult r = FrameV1.parse(buf, pos);
        if (r == null) {
            return FrameV2Result.fail();
        }
        return new FrameV2Result(true, 0, 0, r.payload(), new byte[0], true, r.nextPos());
    }

    // ================= 帧跳过（零拷贝，不重算校验）/ zero-copy skip =================

    /**
     * 只读长度跳帧：返回下一帧 {@code nextPos} 或 {@code -1}。不做校验重算。v2 与 v1 路径
     * 均含（v1 委托 {@link FrameV1#skip}）。Returns the next-frame position or {@code -1};
     * no integrity recompute. Handles both v2 and v1 (delegating to {@link FrameV1#skip}).
     */
    public static long skip(byte[] buf, long pos) {
        if (buf == null || pos < 0 || pos > buf.length) {
            return -1;
        }
        if (buf.length - pos < 2) {
            return -1;
        }
        int nb = buf.length;
        boolean isV2 = (buf[(int) pos] & 0xFF) == FrameConst.MAGIC_FIRST
                && (buf[(int) pos + 1] & 0xFF) == FrameConst.MAGIC_SECOND;
        if (isV2) {
            if (nb - pos < HEADER_BYTES) {
                return -1;
            }
            int ver = buf[(int) pos + 2] & 0xFF;
            if (ver != FrameConst.FRAME_VERSION) {
                return -1;
            }
            int fl = buf[(int) pos + 3] & 0xFF;
            if ((fl & FrameConst.FLAG_RESERVED_MASK) != 0) {
                return -1;
            }
            int ln = readBe32(buf, pos + 4);
            int el = readBe16(buf, pos + 8);
            if (ln < 0) {
                return -1;
            }
            int integLen = ((fl & FrameConst.FLAG_STRONG) != 0)
                    ? FrameConst.INTEGRITY_STRONG_BYTES : FrameConst.INTEGRITY_FAST_BYTES;
            long total = pos + HEADER_BYTES + el + (long) ln + integLen;
            if (total > nb) {
                return -1;
            }
            return total;
        }
        return FrameV1.skip(buf, pos);
    }

    // ================= ext TLV 构建 / 读取 =================

    /** 拼一条 TLV：key u16 BE + value_len u16 BE + value。Builds one TLV (key u16 BE, len u16 BE, value). */
    public static byte[] extTlv(int key, byte[] value) {
        byte[] v = value == null ? new byte[0] : value;
        ByteArrayOutputStream out = new ByteArrayOutputStream(4 + v.length);
        writeBe16(out, key);
        writeBe16(out, v.length);
        out.write(v, 0, v.length);
        return out.toByteArray();
    }

    /** ext 中 key1/key2 等 u8 value：未知/缺 key 返回 -1；格式坏的尾 TLV 也返回 -1。f u8 TLV read; -1 if missing. */
    public static int extU8(byte[] ext, int key) {
        byte[] e = ext == null ? new byte[0] : ext;
        int p = 0;
        int n = e.length;
        while (p + 4 <= n) {
            int k = readBe16(e, p);
            int vl = readBe16(e, p + 2);
            if (p + 4 + vl > n) {
                return -1;
            }
            if (k == key) {
                if (vl >= 1) {
                    return e[p + 4] & 0xFF;
                }
                return 0;
            }
            p = p + 4 + vl;
        }
        return -1;
    }

    /** ext 中 u32 BE value（如 key5 session）：缺返回 -1。u32 BE TLV read; -1 if missing. */
    public static int extU32(byte[] ext, int key) {
        byte[] e = ext == null ? new byte[0] : ext;
        int p = 0;
        int n = e.length;
        while (p + 4 <= n) {
            int k = readBe16(e, p);
            int vl = readBe16(e, p + 2);
            if (p + 4 + vl > n) {
                return -1;
            }
            if (k == key) {
                if (vl >= 4) {
                    return readBe32(e, p + 4);
                }
                return 0;
            }
            p = p + 4 + vl;
        }
        return -1;
    }

    /** ext 中 u64 BE value（如 key6/key7）：缺返回 -1。u64 BE TLV read; -1 if missing. */
    static long extU64(byte[] ext, int key) {
        byte[] e = ext == null ? new byte[0] : ext;
        int p = 0;
        int n = e.length;
        while (p + 4 <= n) {
            int k = readBe16(e, p);
            int vl = readBe16(e, p + 2);
            if (p + 4 + vl > n) {
                return -1;
            }
            if (k == key) {
                if (vl >= 8) {
                    return readBe64(e, p + 4);
                }
                return 0;
            }
            p = p + 4 + vl;
        }
        return -1;
    }

    /** 块帧扩展头：key5=session(u32) + key6=seq(u64)。Ext header: session + seq. */
    public static byte[] extStreamChunk(int session, long seq) {
        return concat(extTlv(FrameConst.EXT_STREAM_SESSION, be32(session)),
                extTlv(FrameConst.EXT_STREAM_SEQ, be64(seq)));
    }

    /**
     * 单帧流构造：{@code piece} 打 STREAM 标记与 SESSION/SEQ(+首次 TOTAL) 扩展头；末帧设
     * STREAM_END。Constructs one stream frame from a piece, tagged STREAM with session/seq
     * (+total on the first frame); the last frame sets STREAM_END.
     */
    public static byte[] streamChunkMake(byte[] piece, int session, long seq, long total,
                                         boolean first, boolean last) {
        byte[] ext = extStreamChunk(session, seq);
        if (first) {
            ext = concat(ext, extTlv(FrameConst.EXT_STREAM_TOTAL, be64(total)));
        }
        int fl = FrameConst.FLAG_STREAM;
        if (last) {
            fl |= FrameConst.FLAG_STREAM_END;
        }
        return encode(piece, fl, ext);
    }

    /**
     * 按 ≤{@code chunk} 分块，返回逐帧字节拼接（每帧自定界，帧序即 seq 0..n-1）。
     * Splits {@code payload} into ≤{@code chunk} pieces and returns the concatenated
     * self-delimited frames (frame order == seq order 0..n-1).
     */
    public static byte[] streamSplit(byte[] payload, int chunk, int session) {
        byte[] p = payload == null ? new byte[0] : payload;
        int total = p.length;
        int c = chunk;
        if (c <= 0) {
            c = 65536;
        }
        int nchunks = total / c;
        if (nchunks * c < total) {
            nchunks++;
        }
        if (nchunks == 0) {
            nchunks = 1;
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (int i = 0; i < nchunks; i++) {
            int cs = i * c;
            int ce = Math.min(cs + c, total);
            byte[] piece = java.util.Arrays.copyOfRange(p, cs, ce);
            byte[] fr = streamChunkMake(piece, session, i, total, i == 0, i == nchunks - 1);
            out.write(fr, 0, fr.length);
        }
        return out.toByteArray();
    }

    /**
     * 重组成整载荷：逐帧解析（SESSION 一致 + SEQ 顺序重组，序号须 0..count-1 连续）；会话
     * 不一致/丢帧/总长不符返回 {@code null}。Reassembles the whole payload; parses each frame
     * (session consistency + SEQ-ordered reassembly with contiguous 0..count-1). Returns
     * {@code null} on session mismatch / missing frames / total-length mismatch.
     */
    public static byte[] streamJoin(byte[] frames) {
        if (frames == null) {
            return null;
        }
        int sess = -1;
        long total = -1;
        List<Long> seqs = new ArrayList<>();
        List<Integer> starts = new ArrayList<>();
        List<Integer> sizes = new ArrayList<>();
        byte[] paybuf = new byte[0];
        int count = 0;
        long pos = 0;
        int appendCursor = 0;
        while (pos < frames.length) {
            FrameV2Result fr = parse(frames, pos);
            if (!fr.ok()) {
                return null;
            }
            byte[] piece = fr.payload();
            paybuf = concat(paybuf, piece);
            long s = extU64(fr.ext(), FrameConst.EXT_STREAM_SESSION);
            if (s < 0) {
                return null;
            }
            if (sess == -1) {
                sess = (int) s;
            } else if (s != sess) {
                return null;
            }
            long sq = extU64(fr.ext(), FrameConst.EXT_STREAM_SEQ);
            if (sq < 0) {
                return null;
            }
            seqs.add(sq);
            starts.add(appendCursor);
            sizes.add(piece.length);
            appendCursor += piece.length;
            long tl = extU64(fr.ext(), FrameConst.EXT_STREAM_TOTAL);
            if (tl >= 0) {
                total = tl;
            }
            count++;
            pos = fr.nextPos();
        }
        if (count == 0) {
            return null;
        }
        // 选择排序按 SEQ 升序，并校验连续
        for (int i = 0; i < count; i++) {
            int mi = i;
            for (int j = i + 1; j < count; j++) {
                if (seqs.get(j) < seqs.get(mi)) {
                    mi = j;
                }
            }
            if (mi != i) {
                long ts = seqs.get(i); seqs.set(i, seqs.get(mi)); seqs.set(mi, ts);
                int tst = starts.get(i); starts.set(i, starts.get(mi)); starts.set(mi, tst);
                int tsz = sizes.get(i); sizes.set(i, sizes.get(mi)); sizes.set(mi, tsz);
            }
        }
        for (int i = 0; i < count; i++) {
            if (seqs.get(i) != i) {
                return null;
            }
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (int i = 0; i < count; i++) {
            int s0 = starts.get(i);
            int e0 = s0 + sizes.get(i);
            out.write(paybuf, s0, e0 - s0);
        }
        if (total >= 0 && out.size() != total) {
            return null;
        }
        return out.toByteArray();
    }

    // ================= 工具 / helpers =================

    /** ASCII 字符串 → 字节（字符码原样落位）。ASCII string → bytes. */
    static byte[] fromAscii(String s) {
        int n = s.length();
        byte[] out = new byte[n];
        for (int i = 0; i < n; i++) {
            out[i] = (byte) s.charAt(i);
        }
        return out;
    }

    /** 小写 hex → 字节（两字符一组）。Lowercase hex → bytes. */
    static byte[] hexBytes(String h) {
        int n = h.length();
        byte[] out = new byte[(n + 1) / 2];
        int k = 0;
        for (int i = 0; i < n; i += 2) {
            int hi = hexVal(h.charAt(i));
            int lo = i + 1 < n ? hexVal(h.charAt(i + 1)) : 0;
            out[k++] = (byte) (((hi & 15) << 4) | (lo & 15));
        }
        return out;
    }

    private static int hexVal(char c) {
        if (c >= '0' && c <= '9') {
            return c - '0';
        }
        if (c >= 'a' && c <= 'f') {
            return c - 'a' + 10;
        }
        if (c >= 'A' && c <= 'F') {
            return c - 'A' + 10;
        }
        return 0;
    }

    /** 拼接两个字节数组。Concatenates two byte arrays. */
    static byte[] concat(byte[] a, byte[] b) {
        byte[] out = new byte[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }

    private static int readBe16(byte[] b, long off) {
        return ((b[(int) off] & 0xFF) << 8) | (b[(int) off + 1] & 0xFF);
    }

    private static int readBe32(byte[] b, long off) {
        return ((b[(int) off] & 0xFF) << 24)
                | ((b[(int) off + 1] & 0xFF) << 16)
                | ((b[(int) off + 2] & 0xFF) << 8)
                | (b[(int) off + 3] & 0xFF);
    }

    private static long readBe64(byte[] b, long off) {
        long r = 0;
        for (int i = 0; i < 8; i++) {
            r = (r << 8) | (b[(int) off + i] & 0xFF);
        }
        return r;
    }

    private static void writeBe16(ByteArrayOutputStream out, int n) {
        out.write((n >> 8) & 0xFF);
        out.write(n & 0xFF);
    }

    private static void writeBe32(ByteArrayOutputStream out, long n) {
        out.write((int) ((n >> 24) & 0xFF));
        out.write((int) ((n >> 16) & 0xFF));
        out.write((int) ((n >> 8) & 0xFF));
        out.write((int) (n & 0xFF));
    }

    /** u32 BE 成字节。u32 BE → bytes. */
    private static byte[] be32(int n) {
        return new byte[]{(byte) (n >>> 24), (byte) (n >>> 16), (byte) (n >>> 8), (byte) n};
    }

    /** u64 BE 成字节。u64 BE → bytes. */
    private static byte[] be64(long n) {
        byte[] out = new byte[8];
        for (int i = 7; i >= 0; i--) {
            out[i] = (byte) (n & 0xFF);
            n >>>= 8;
        }
        return out;
    }
}