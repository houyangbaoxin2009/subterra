package io.toterra.subterra.engine.session;

import io.toterra.subterra.engine.network.frame.FrameConst;
import io.toterra.subterra.engine.network.integrity.FrameV2;
import io.toterra.subterra.engine.network.integrity.FrameV2Result;
import io.toterra.subterra.engine.network.integrity.Tsha1f;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;

/**
 * p.2.11.3 外部进程帧协议入口编解码器（final 工具类，engine 纯 JDK，不走 Minecraft）。
 * <p>
 * <b>复用 p.2.5 tink v2 帧 ABI，不另起炉灶</b>：把 {@link SessionCommand} 装进/取出 tink v2 帧。
 * 每个会话命令都被编码为<b>单帧、非流式</b>（不设 {@code STREAM} / {@code STREAM_END} 标志）：
 * <ul>
 *   <li><b>帧载荷 payload</b> = {@link SessionEnvelopeCodec#encode} 的 zd 信封字节（命令本体）；</li>
 *   <li><b>ext key5</b> {@code EXT_STREAM_SESSION}：确定性的固定宽会话标签，即
 *       {@link #sessionTag(String)} 把 {@code sessionId} 映射为 32 位 tag（见下），供外层还原
 *       会话归属；</li>
 *   <li><b>ext key6</b> {@code EXT_STREAM_SEQ}：u64 BE 命令序号（节点到 tink 既有流序号语义）；</li>
 *   <li><b>ext key9</b> {@code EXT_EXT_META}：元数据 UTF-8 字节（p.2.5 既定 ABI 语义，meta 走
 *       key9 是铁律，必须遵守）。</li>
 * </ul>
 * 帧级完整性走既有 frame 完整性子槽：本入口以强校验（bit0 置位，n=32；模型/位宽按 FrameV2 既有
 * 约定够 f/48）编码，读取侧对快/强两种子槽（8/32B）都按 {@link FrameV2} 既有约定校验。同输入两次
 * {@code encodeFrame} 逐字节一致（无时间戳/随机/时序，Hash 与 Base64 均为确定性）。
 * <p>
 * <b>sessionId→tag 确定性契约</b>：{@code tag = UInt32BE( tsha1f_digest_hex("f", utf8(sessionId),
 * 48) 的前 8 个 hex 字符 )}。即以 {@link Tsha1f#digestHex} 对 {@code sessionId} 计算 48 位摘要，
 * 固定取摘要 hex 字符串的前 8 个字符（= 4 字节）解析为 32 位无符号整型作为标签；{@code null}
 * sessionId 视同空串。此映射写死为本模块文档契约，不得随意更换（更换会改变外部位路由）。
 * <p>
 * <b>decodeFrame 拒绝语义</b>：先按既有 {@link FrameV2#parse} 路径做结构校验（魔数/版本/保留位/
 * 长度/完整性），逐类失败映射为 {@link SessionFrameException} 的固定原因；随后解 ext
 * （session tag/seq/key9 meta）并调 {@link SessionEnvelopeCodec#decode} 取出命令，并交叉校验
 * ext 的 seq/meta 与信封还原值一致。任何畸形（坏魔数、坏版本、保留位非 0、长度不足、完整性子槽
 * 不匹配、所需 ext 键缺失、key9 非规范 UTF-8、信封畸形、ext 与信封不一致）都抛对应固定原因，
 * 绝不静默产出数据。<b>顺序/重放拒绝属于机器层</b>（p.2.11.2 的 dispatch seq 门禁），此处只保证
 * 「帧层确定性拒绝坏帧」。无 O(n²) 路径，全程无副作用。
 * <p>
 * p.2.11.3 external-process frame-protocol entry codec (final utility class; engine, pure JDK,
 * no Minecraft host).
 * <p>
 * <b>Reuses the p.2.5 tink v2 frame ABI — no new wire format</b>: it puts / pulls a
 * {@link SessionCommand} into/out of a tink v2 frame. Each session command is encoded as a
 * <b>single, non-streaming frame</b> (no {@code STREAM} / {@code STREAM_END} flags):
 * <ul>
 *   <li><b>frame payload</b> = the zd envelope bytes of {@link SessionEnvelopeCodec#encode}
 *       (the command body);</li>
 *   <li><b>ext key5</b> {@code EXT_STREAM_SESSION}: the deterministic fixed-width session tag,
 *       i.e. the 32-bit mapping of {@code sessionId} by {@link #sessionTag(String)} (see below),
 *       letting the outer layer restore the session affiliation;</li>
 *   <li><b>ext key6</b> {@code EXT_STREAM_SEQ}: the u64 BE command sequence (mirroring the tink
 *       streaming-seq semantics);</li>
 *   <li><b>ext key9</b> {@code EXT_EXT_META}: the metadata UTF-8 bytes (the p.2.5 ABI mandates
 *       meta on key9 — a hard rule that must be honoured).</li>
 * </ul>
 * Frame-level integrity uses the existing integrity slots: this entry encodes with strong
 * integrity (bit0 set, n=32; model/bits follow the FrameV2 convention, defaulting to f/48) and
 * decodes both fast and strong slots (8/32B) under the {@link FrameV2} convention. Two
 * {@code encodeFrame} calls with identical input are byte-for-byte identical (no
 * timestamp/random/timing; hashing and Base64 are deterministic).
 * <p>
 * <b>sessionId→tag deterministic contract</b>: {@code tag = UInt32BE( first 8 hex chars of
 * tsha1f_digest_hex("f", utf8(sessionId), 48) )}. That is, a 48-bit digest of {@code sessionId} is
 * computed with {@link Tsha1f#digestHex} and the first 8 hex chars (= 4 bytes) are parsed as a
 * 32-bit unsigned integer tag; a {@code null} sessionId is treated as the empty string. This
 * mapping is a frozen documented contract of this module and must not be swapped casually
 * (changing it would alter external routing).
 * <p>
 * <b>decodeFrame rejection semantics</b>: structural validation goes through the existing
 * {@link FrameV2#parse} path (magic / version / reserved bits / length / integrity), with each
 * failure class mapped to a fixed {@link SessionFrameException} reason; the ext fields
 * (session tag / seq / key9 meta) are then read and {@link SessionEnvelopeCodec#decode} yields the
 * command, cross-checking the ext seq/meta against the envelope-restored values. Any
 * malformation (bad magic, bad version, reserved bits set, short length, integrity mismatch,
 * missing required ext key, non-well-formed key9 UTF-8, malformed envelope, or ext/envelope
 * inconsistency) throws the matching fixed reason; malformed input never silently yields data.
 * <b>Ordering / replay rejection belongs to the state-machine layer</b> (p.2.11.2's dispatch seq
 * gate); this entry only guarantees deterministic rejection of bad frames at the frame layer. No
 * O(n²) path; fully side-effect free.
 */
public final class SessionFrameCodec {

    private SessionFrameCodec() {
    }

    /**
     * 把 {@code sessionId} 确定性地映射为固定宽会话标签（见类 Javadoc 的契约）。Deterministically
     * maps {@code sessionId} to a fixed-width session tag (contract in the class javadoc).
     *
     * @param sessionId 会话 id（{@code null} 视同空串）/ the session id (null treated as empty).
     * @return 32 位无符号标签 / the 32-bit unsigned tag.
     */
    public static int sessionTag(String sessionId) {
        String id = sessionId == null ? "" : sessionId;
        byte[] idBytes = id.getBytes(StandardCharsets.UTF_8);
        String hex = Tsha1f.digestHex("f", idBytes, 48);
        if (hex.length() < 8) {
            throw new IllegalStateException("tsha1f digest too short to derive a 32-bit session tag");
        }
        return (int) Long.parseLong(hex.substring(0, 8), 16);
    }

    /**
     * 把一条会话命令确定为单帧、非流式 tink v2 帧（强完整性）。{@code command} 为 null 抛
     * {@link IllegalArgumentException}（调用方程序错误）。Encodes a session command to a single,
     * non-streaming tink v2 frame (strong integrity). A null {@code command} raises
     * {@link IllegalArgumentException} (caller error).
     *
     * @param command   会话命令 / the session command.
     * @param sessionId 会话归属 id，映射为固定宽 tag / the owning session id, mapped to the tag.
     * @return tink v2 帧字节 / the tink v2 frame bytes.
     */
    public static byte[] encodeFrame(SessionCommand command, String sessionId) {
        if (command == null) {
            throw new IllegalArgumentException("session command must be non-null");
        }
        int tag = sessionTag(sessionId);
        byte[] payload = SessionEnvelopeCodec.encode(command);
        String meta = command.meta();
        byte[] metaExt = meta == null ? new byte[0] : meta.getBytes(StandardCharsets.UTF_8);
        byte[] ext = concat3(FrameV2.extTlv(FrameConst.EXT_STREAM_SESSION, be32(tag)),
                FrameV2.extTlv(FrameConst.EXT_STREAM_SEQ, be64(command.seq())),
                FrameV2.extTlv(FrameConst.EXT_EXT_META, metaExt));
        return FrameV2.encode(payload, FrameConst.FLAG_STRONG, ext);
    }

    /**
     * 解一帧 tink v2 会话命令帧。先按 {@link FrameV2#parse} 既有路径校验结构，再解 ext 与信封，
     * 交叉校验 ext seq/meta 一致；任何失败映射为 {@link SessionFrameException} 固定原因，绝不
     * 静默产出数据。Decodes a tink v2 session-command frame. Validates structure via the existing
     * {@link FrameV2#parse} path, then reads ext and the envelope, cross-checking ext seq/meta; any
     * failure maps to a fixed {@link SessionFrameException} reason and never silently yields data.
     *
     * @param frame tink v2 帧字节 / the tink v2 frame bytes.
     * @return 会话帧（归属标签 + 命令）/ the session frame (affiliation tag + command).
     * @throws SessionFrameException 坏帧或无法解码 / bad frame or undecodable.
     */
    public static SessionFrame decodeFrame(byte[] frame) throws SessionFrameException {
        if (frame == null || frame.length < 2) {
            throw new SessionFrameException(SessionFrameException.LENGTH, "frame too short for a tink magic");
        }
        if ((frame[0] & 0xFF) != FrameConst.MAGIC_FIRST || (frame[1] & 0xFF) != FrameConst.MAGIC_SECOND) {
            throw new SessionFrameException(SessionFrameException.BAD_MAGIC, "frame does not start with the tink magic");
        }
        if (frame.length < 10) {
            throw new SessionFrameException(SessionFrameException.LENGTH, "frame shorter than the tink v2 header");
        }
        if ((frame[2] & 0xFF) != FrameConst.FRAME_VERSION) {
            throw new SessionFrameException(SessionFrameException.BAD_VERSION, "expected tink v2 but got version " + (frame[2] & 0xFF));
        }
        int flags = frame[3] & 0xFF;
        if ((flags & FrameConst.FLAG_RESERVED_MASK) != 0) {
            throw new SessionFrameException(SessionFrameException.RESERVED_BITS,
                    "reserved flags bit5..bit7 must be clear");
        }
        FrameV2Result parsed = FrameV2.parse(frame, 0);
        // after the manual magic/version/reserved checks, a structural failure here is a length fault
        if (parsed == null || !parsed.ok()) {
            throw new SessionFrameException(SessionFrameException.LENGTH, "frame length/extension out of bounds");
        }
        if (!parsed.integrityOk()) {
            throw new SessionFrameException(SessionFrameException.INTEGRITY, "integrity slot did not verify");
        }
        byte[] ext = parsed.ext();
        byte[] sessB = extRead(ext, FrameConst.EXT_STREAM_SESSION);
        if (sessB == null || sessB.length < 4) {
            throw new SessionFrameException(SessionFrameException.EXT, "missing/invalid session tag (ext key5)");
        }
        int tag = u32BE(sessB, 0);
        byte[] seqB = extRead(ext, FrameConst.EXT_STREAM_SEQ);
        if (seqB == null || seqB.length < 8) {
            throw new SessionFrameException(SessionFrameException.EXT, "missing/invalid seq (ext key6)");
        }
        long extSeq = u64BE(seqB, 0);
        byte[] metaB = extRead(ext, FrameConst.EXT_EXT_META);
        if (metaB == null) {
            throw new SessionFrameException(SessionFrameException.EXT, "missing key9 ext meta");
        }
        String extMeta = strictUtf8(metaB);
        final SessionCommand command;
        try {
            command = SessionEnvelopeCodec.decode(parsed.payload());
        } catch (SessionEnvelopeException e) {
            throw new SessionFrameException(SessionFrameException.ENVELOPE,
                    "inner envelope did not decode to a session command", e);
        }
        if (command.seq() != extSeq) {
            throw new SessionFrameException(SessionFrameException.EXT,
                    "ext seq " + extSeq + " differs from envelope seq " + command.seq());
        }
        if (!Objects.equals(command.meta(), extMeta)) {
            throw new SessionFrameException(SessionFrameException.META,
                    "ext key9 meta differs from envelope meta");
        }
        return new SessionFrame(tag, command);
    }

    // ================= 确定性私有原语 / deterministic private primitives =================

    /** 读 ext 中某个 TLV 键的原始值字节；缺键/畸尾 TLV 返回 null。Reads the raw value bytes of one ext TLV key; null if absent/malformed. */
    private static byte[] extRead(byte[] ext, int key) {
        if (ext == null) {
            return null;
        }
        int p = 0;
        int n = ext.length;
        while (p + 4 <= n) {
            int k = u16BE(ext, p);
            int vl = u16BE(ext, p + 2);
            if (p + 4 + vl > n || vl < 0) {
                return null; // trailing TLV malformed → treat as missing key
            }
            if (k == key) {
                return Arrays.copyOfRange(ext, p + 4, p + 4 + vl);
            }
            p += 4 + vl;
        }
        return null;
    }

    /** 规范 UTF-8 解码；非法序列抛固定原因 {@code META}。Well-formed UTF-8 decode; invalid raises fixed reason {@code META}. */
    private static String strictUtf8(byte[] b) throws SessionFrameException {
        try {
            CharsetDecoder d = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT);
            return d.decode(ByteBuffer.wrap(b)).toString();
        } catch (CharacterCodingException e) {
            throw new SessionFrameException(SessionFrameException.META, "key9 meta is not well-formed UTF-8");
        }
    }

    private static int u16BE(byte[] b, int off) {
        return ((b[off] & 0xFF) << 8) | (b[off + 1] & 0xFF);
    }

    private static int u32BE(byte[] b, int off) {
        return ((b[off] & 0xFF) << 24)
                | ((b[off + 1] & 0xFF) << 16)
                | ((b[off + 2] & 0xFF) << 8)
                | (b[off + 3] & 0xFF);
    }

    private static long u64BE(byte[] b, int off) {
        long r = 0;
        for (int i = 0; i < 8; i++) {
            r = (r << 8) | (b[off + i] & 0xFF);
        }
        return r;
    }

    private static byte[] be32(int n) {
        return new byte[]{(byte) (n >>> 24), (byte) (n >>> 16), (byte) (n >>> 8), (byte) n};
    }

    private static byte[] be64(long n) {
        byte[] out = new byte[8];
        for (int i = 7; i >= 0; i--) {
            out[i] = (byte) (n & 0xFF);
            n >>>= 8;
        }
        return out;
    }

    private static byte[] concat3(byte[] a, byte[] b, byte[] c) {
        byte[] out = new byte[a.length + b.length + c.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        System.arraycopy(c, 0, out, a.length + b.length, c.length);
        return out;
    }
}