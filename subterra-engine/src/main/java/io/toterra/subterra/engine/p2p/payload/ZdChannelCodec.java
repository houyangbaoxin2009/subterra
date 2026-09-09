package io.toterra.subterra.engine.p2p.payload;

import io.toterra.subterra.engine.network.frame.FrameConst;
import io.toterra.subterra.engine.network.frame.ZdFrameBridge;
import io.toterra.subterra.engine.network.integrity.FrameV2;
import io.toterra.subterra.engine.zd.ZdDocWriter;
import io.toterra.subterra.engine.zd.ZdPrimitives;
import io.toterra.subterra.engine.zd.ZdRow;
import io.toterra.subterra.engine.zd.ZdVolume;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * p.2.5.5 zd 自定义载荷通道 ABI 门面（engine 层纯 JDK，不碰 MC）。把一条「通道消息」确定性地
 * 编解码为一帧的载荷字节，并把通道元数据顺着帧 ext 的 key9 槽复用 tink v2 ABI。最小设计：
 * <ul>
 *   <li><b>载荷即 zd 文档</b> —— {@code encode} 产出的字节就是我们喂给 {@link FrameV2} /
 *       {@code P2pChannel} 的帧载荷，它就是一棵 zd 文档；</li>
 *   <li><b>薄信封</b> —— 文档头部固定前缀两行（position-0 类型槽 kind-2 行、position-1 元数据
 *       Base64 行），其后的行即为调用方载荷（{@link ZdDocWriter} 的一次普通写）；</li>
 *   <li><b>channel meta 复用 ext key9</b> —— 另提供 {@link #channelMetaExt} 把通道元数据放入
 *       帧 ext 槽（{@link FrameConst#EXT_EXT_META}=9），与 tink v2 既有 ext ABI 对齐；{@link #decode}
 *       可读取它（未知 ext 键一律跳过）。</li>
 * </ul>
 * 往返不变式：{@code decode(encode(t,m,rows))} 逐字段还原 {@code rows}；同名输入两次 {@code encode}
 * 字节级一致（确定性）。Type slot：类型自描述、无中心会话。
 * <p>
 * p.2.5.5 zd custom-payload-channel ABI facade (engine, pure JDK, no Minecraft). Deterministically
 * encodes/decodes one "channel message" to a frame payload's bytes and routes the channel metadata
 * through the tink v2 frame ext key9 slot, reusing that ABI. Minimal design: the payload IS the zd
 * document; the document header has a fixed two-row thin envelope (position-0 type slot as a kind-2
 * row, position-1 metadata as a Base64 row) followed by the caller rows written once by
 * {@link ZdDocWriter}. Round-trip invariant: {@code decode(encode(t,m,rows))} reproduces every
 * {@code rows} field; {@code encode} twice with identical input is byte-identical (deterministic).
 */
public final class ZdChannelCodec {

    /** 通道元数据在帧 ext 中复用的 TLV 键 = tink v2 {@code EXT_EXT_META}(9)。 */
    public static final int EXT_CHANNEL_META = FrameConst.EXT_EXT_META;

    /** 信封前缀行长：1 类型行 + 1 元数据行。Envelope prefix size: type row + metadata row. */
    private static final int ENVELOPE_ROWS = 2;

    /** 信封内类型行的自描述 key（写侧仅作注释性，读侧按 position 取）。 */
    private static final String ENV_TYPE_KEY = "p2p.type";
    /** 信封内元数据行的自描述 key（写侧仅作注释性，读侧按 position 取）。 */
    private static final String ENV_META_KEY = "p2p.channel-meta";

    private ZdChannelCodec() {
    }

    // ================= encode =================

    /**
     * 编一条通道消息为帧载荷（payload 即 zd 文档）。类型取 {@link P2pMessageType#STATUS}。
     * Encodes a channel message to a frame payload (the payload IS a zd doc), type defaulting to
     * {@link P2pMessageType#STATUS}.
     *
     * @param channelMeta 不透明通道元数据 / opaque channel metadata
     * @param rows        载荷行 / the payload rows
     * @return 帧载荷字节（zd 文档）/ frame payload bytes (a zd document)
     */
    public static byte[] encode(byte[] channelMeta, List<ZdRow> rows) {
        return encode(P2pMessageType.STATUS, channelMeta, rows);
    }

    /**
     * 带类型槽的编一条通道消息为帧载荷。Encodes a channel message with an explicit type slot.
     */
    public static byte[] encode(P2pMessageType type, byte[] channelMeta, List<ZdRow> rows) {
        P2pMessageType t = type == null ? P2pMessageType.STATUS : type;
        byte[] meta = channelMeta == null ? new byte[0] : channelMeta;
        List<ZdRow> r = rows == null ? List.of() : rows;
        List<ZdRow> doc = new ArrayList<>(r.size() + ENVELOPE_ROWS);
        doc.add(new ZdRow(2, ENV_TYPE_KEY, t.ordinal(), 0.0, "", 0));
        // type slot rides a kind-2 row via valueI64; deterministic against fixed values() order.
        doc.add(new ZdRow(1, ENV_META_KEY, 0L, 0.0, Base64.getEncoder().encodeToString(meta), 0));
        doc.addAll(r);
        return ZdFrameBridge.buildPayload(0, doc);
    }

    /**
     * 编一条以 {@code RawRows} 承载的通道消息。Encodes a message carried by a {@link RawRows}.
     */
    public static byte[] encode(RawRows carrier) {
        if (carrier == null) {
            return encode(P2pMessageType.STATUS, new byte[0], List.of());
        }
        return encode(P2pMessageType.STATUS, carrier.channelMeta(), carrier.rows());
    }

    // ================= decode =================

    /**
     * 解一帧载荷（单参：无 ext，通道元数据取自信封）。成功返回 {@code ok=true}；fetch zd 结构坏 /
     * 非 zd / 类型越界抛 {@link IllegalArgumentException}，绝不静默产出错误数据。Decodes a frame
     * payload (single-arg: no ext, metadata read from the envelope). Returns {@code ok=true} on
     * success; a non-zd / structurally-broken payload or an out-of-range type raises
     * {@link IllegalArgumentException} — it never silently yields wrong data.
     */
    public static Decode decode(byte[] framePayload) {
        return decode(framePayload, new byte[0]);
    }

    /**
     * 解一帧载荷 + 帧 ext。未知 ext 键一律跳过；key9 携带的通道元数据优先于信封内的（复用 ABI）。
     * Decodes a frame payload plus frame ext. Unknown ext keys are always skipped; channel metadata
     * carried by ext key9 takes precedence over the envelope copy (reusing the ABI).
     */
    public static Decode decode(byte[] framePayload, byte[] frameExt) {
        List<ZdRow> all = ZdVolume.readRows(framePayload); // throws on bad zd structure / non-zd
        if (all.size() < ENVELOPE_ROWS) {
            throw new IllegalArgumentException("zd payload shorter than the 2-row channel envelope ("
                    + all.size() + " rows)");
        }
        ZdRow typeRow = all.get(0);
        ZdRow metaRow = all.get(1);
        int ord = (int) typeRow.valueI64();
        P2pMessageType type;
        try {
            type = P2pMessageType.values()[ord];
        } catch (ArrayIndexOutOfBoundsException e) {
            throw new IllegalArgumentException("unknown payload message type ordinal " + ord);
        }
        byte[] envMeta = metaRow.valueStr() == null || metaRow.valueStr().isEmpty()
                ? new byte[0] : Base64.getDecoder().decode(metaRow.valueStr());
        byte[] extMeta = readExtMeta(frameExt);
        byte[] meta = extMeta != null ? extMeta : envMeta;
        return new Decode(true, type, meta, List.copyOf(all.subList(ENVELOPE_ROWS, all.size())));
    }

    // ================= ext ABI 对齐（复用 key9 槽） =================

    /**
     * 把通道元数据放进帧 ext 的 key9 TLV（复用 {@code EXT_EXT_META} ABI）。可并入发送帧的 ext。
     * Puts channel metadata into a frame-ext key9 TLV (reusing the {@code EXT_EXT_META} ABI for a
     * caller that wants it at frame-metadata level). Binary-safe (raw bytes).
     */
    public static byte[] channelMetaExt(byte[] channelMeta) {
        byte[] m = channelMeta == null ? new byte[0] : channelMeta;
        return FrameV2.extTlv(EXT_CHANNEL_META, m);
    }

    /** 从帧 ext 读 key9 通道元数据；未知键跳过，缺 key9 返回 {@code null}。Reads ext-key9 channel metadata; skips unknown keys; {@code null} if absent. */
    private static byte[] readExtMeta(byte[] frameExt) {
        byte[] e = frameExt == null ? new byte[0] : frameExt;
        int p = 0;
        int n = e.length;
        while (p + 4 <= n) {
            int k = ((e[p] & 0xFF) << 8) | (e[p + 1] & 0xFF);
            int vl = ((e[p + 2] & 0xFF) << 8) | (e[p + 3] & 0xFF);
            if (p + 4 + vl > n) {
                break; // truncated trailing TLV → skip gracefully
            }
            if (k == EXT_CHANNEL_META) {
                byte[] v = new byte[vl];
                System.arraycopy(e, p + 4, v, 0, vl);
                return v;
            }
            p = p + 4 + vl;
        }
        return null;
    }

    // ================= 体积比报告（复用 p.2.5.1 口径） =================

    /**
     * zd 文档体积与 raw 内容体积的比值报告。口径与 p.2.5.1 一致：{@code rawContentBytes}
     * = 每行 key + i64 + f64 + str 四类内容字节和；{@code docBytes} = {@code ZdDocWriter.write(0,rows)}
     * 的产出字节（含 10B 头 + 逐行六字段 wire 开销）。{@code ratio = rawContent / docBytes}；
     * 因帧封装只增不减且头/字段开销非负，确定满足 {@code 0 < ratio <= 1}（信息量不丢失）。
     * Volume ratio report, reusing the p.2.5.1 formula: rawContentBytes = per-row key+i64+f64+str
     * content bytes; docBytes = {@code ZdDocWriter.write(0,rows)} (10B header + per-row wire overhead);
     * {@code ratio = rawContent / docBytes}, deterministically in {@code (0, 1]} since framing is
     * non-negative (no information is lost).
     */
    public static VolumeReport volumeRatio(List<ZdRow> rows) {
        List<ZdRow> r = rows == null ? List.of() : rows;
        byte[] zd = ZdDocWriter.write(0, r);
        int raw = 0;
        for (ZdRow row : r) {
            raw += rawContentBytes(row);
        }
        return new VolumeReport(raw, zd.length,
                zd.length == 0 ? 0.0 : (double) raw / zd.length);
    }

    /** 一行内容字节（不含头/tag/长度前缀），与 p.2.5.1 {@code rawRowContentBytes} 口径一致。 */
    private static int rawContentBytes(ZdRow row) {
        return (row.key() == null ? 0 : row.key().getBytes(StandardCharsets.UTF_8).length)
                + ZdPrimitives.encI64(row.valueI64()).length
                + ZdPrimitives.be64(Double.doubleToLongBits(row.valueF64())).length
                + (row.valueStr() == null ? 0 : row.valueStr().getBytes(StandardCharsets.UTF_8).length);
    }

    // ================= 结果载体 =================

    /**
     * 单条通道消息解码结果（p.2.5.5）：{@code ok} 成功标志、{@code type} 类型槽、{@code channelMeta}
     * 通道元数据、{@code rows} 载荷行。成功时 {@code ok=true}；结构坏/非 zd/类型越界一律抛而非返回
     * {@code ok=false}（不静默）。Decode result for one channel message: {@code ok} success flag,
     * {@code type} slot, {@code channelMeta}, {@code rows}. On success {@code ok=true}; structural /
     * non-zd / out-of-range-type failures always throw rather than returning {@code ok=false}.
     */
    public record Decode(boolean ok, P2pMessageType type, byte[] channelMeta, List<ZdRow> rows) {
        public Decode {
            channelMeta = channelMeta == null ? new byte[0] : channelMeta.clone();
            rows = rows == null ? List.of() : List.copyOf(rows);
        }

        @Override
        public byte[] channelMeta() {
            return channelMeta.clone();
        }
    }

    /** 体积比报告（p.2.5.5 / p.2.5.1 口径）。Volume-ratio report. */
    public record VolumeReport(int rawContentBytes, int zdDocBytes, double ratio) {
    }
}