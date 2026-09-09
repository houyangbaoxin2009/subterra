package io.toterra.subterra.engine.session;

import io.toterra.subterra.engine.zd.ZdDocWriter;
import io.toterra.subterra.engine.zd.ZdRow;
import io.toterra.subterra.engine.zd.ZdVolume;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * p.2.11.1 会话命令信封编解码器（engine 纯 JDK，不碰 MC）：确定性、自描述的会话命令信封。
 * <p>
 * <b>信封形态（复用 p.2.5 zd 薄信封风格）</b>：整个信封就是一棵 zd 文档，恰好四行、行序固定：
 * <ol>
 *   <li>position-0 命令动词槽行 kind-2 {@code valueI64 = SessionCommandKind.ordinal()}（同
 *       p.2.5 {@code P2pMessageType} ／{@code ZdChannelCodec} 的类型槽语义，类型自描述）；</li>
 *   <li>position-1 序号行 kind-2 {@code valueI64 = seq}；</li>
 *   <li>position-2 元数据容器段行 kind-1 {@code valueStr = Base64(meta)}（同 p.2.5 元数据 Base64
 *       行写法）；</li>
 *   <li>position-3 载荷容器段行 kind-1 {@code valueStr = Base64(payload)}，{@code payload} 为
 *       zd 文档字节，Base64 作为其确定性承载体。</li>
 * </ol>
 * 读侧按 position + kind 校验取回，键名（key）仅作注释性。
 * <p>
 * <b>复用策略</b>：直接复用 {@code engine.zd}（{@link ZdDocWriter} / {@link ZdVolume} /
 * {@link ZdRow}）的文档读写原语与 p.2.5 薄信封行式；未直接调用 {@code p2p.payload.ZdChannelCodec}
 * 是因为其类型槽绑定 {@code P2pMessageType} 枚举，而会话命令动词是独立枚举，强行复值会扭曲 ABI，
 * 故按 engine.zd 文档形态自实现同样的确定性四行信封，注释于此说明。
 * <p>
 * <b>确定性</b>：不引入时间戳/随机/时序。{@code encode} 是 {@code (kind, seq, meta, payload)}
 * 的纯函数且 Base64 为规范编码，故同输入两次 {@code encode} 逐字节一致；{@code decode} 精确还原
 * 四字段后，{@code encode→decode→再 encode} 逐字节恒等。畸形/越界输入统一抛受检的
 * {@link SessionEnvelopeException}，原因固定（{@code MALFORMED} / {@code UNKNOWN_KIND} /
 * {@code LENGTH} / {@code PAYLOAD}），全程无副作用。
 * <p>
 * p.2.11.1 session-command envelope codec (engine, pure JDK, no Minecraft): a deterministic,
 * self-describing session-command envelope.
 * <p>
 * <b>Envelope shape (reusing the p.2.5 thin zd envelope style)</b>: the whole envelope is one zd
 * document of exactly four rows, in a fixed order:
 * <ol>
 *   <li>position-0 verb slot as a kind-2 row {@code valueI64 = SessionCommandKind.ordinal()}
 *       (same type-slot semantics as p.2.5 {@code P2pMessageType} / {@code ZdChannelCodec},
 *       self-describing);</li>
 *   <li>position-1 sequence row, kind-2 {@code valueI64 = seq};</li>
 *   <li>position-2 metadata container segment, kind-1 {@code valueStr = Base64(meta)} (the same
 *       metadata-Base64 row as p.2.5);</li>
 *   <li>position-3 payload container segment, kind-1 {@code valueStr = Base64(payload)}, where
 *       {@code payload} is zd-document bytes carried deterministically via Base64.</li>
 * </ol>
 * The reader validates by position + kind; row keys are commentary only.
 * <p>
 * <b>Reuse</b>: it reuses {@code engine.zd} directly ({@link ZdDocWriter} / {@link ZdVolume} /
 * {@link ZdRow}) for the document read/write primitives and the p.2.5 thin-envelope row style. It
 * does not call {@code p2p.payload.ZdChannelCodec} directly because that codec's type slot is bound
 * to {@code P2pMessageType}; the session verb is a separate enum, so reusing its type slot would
 * distort the ABI. Accordingly the same deterministic four-row envelope is self-implemented on the
 * engine.zd document shape, as noted above.
 * <p>
 * <b>Determinism</b>: no timestamps / random / timing. {@code encode} is a pure function of
 * {@code (kind, seq, meta, payload)} and Base64 is canonical, so identical input gives byte-identical
 * output; {@code decode} reproduces the four fields exactly, hence {@code encode→decode→re-encode}
 * is byte-for-byte identical. Malformed / out-of-range input uniformly raises the checked
 * {@link SessionEnvelopeException} with a fixed reason ({@code MALFORMED} / {@code UNKNOWN_KIND} /
 * {@code LENGTH} / {@code PAYLOAD}).
 */
public final class SessionEnvelopeCodec {

    /** 信封行数：动词槽 + 序号 + 元数据段 + 载荷段。Envelope row count: verb + seq + meta + payload. */
    private static final int ENVELOPE_ROWS = 4;

    /** 注释性键。Commentary-only keys. */
    private static final String KEY_KIND = "session.kind";
    private static final String KEY_SEQ = "session.seq";
    private static final String KEY_META = "session.meta";
    private static final String KEY_PAYLOAD = "session.payload";

    private SessionEnvelopeCodec() {
    }

    /**
     * 编一条会话命令为 zd 信封字节。{@code command} 为 null 抛 {@link IllegalArgumentException}
     * （调用方程序错误；wire 侧畸形一律走解码异常）。Encodes a session command to zd envelope bytes.
     * A null {@code command} raises {@link IllegalArgumentException} (caller error; wire-side
     * malformation is signalled by the decode exception).
     *
     * @param command 会话命令 / the session command.
     * @return zd 信封字节 / the zd envelope bytes.
     */
    public static byte[] encode(SessionCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("session command must be non-null");
        }
        String metaB64 = command.meta() == null
                ? ""
                : Base64.getEncoder().encodeToString(command.meta().getBytes(StandardCharsets.UTF_8));
        String payB64 = Base64.getEncoder().encodeToString(command.payload());
        List<ZdRow> rows = new ArrayList<>(ENVELOPE_ROWS);
        rows.add(new ZdRow(2, KEY_KIND, command.kind().ordinal(), 0.0, "", 0));
        rows.add(new ZdRow(2, KEY_SEQ, command.seq(), 0.0, "", 0));
        rows.add(new ZdRow(1, KEY_META, 0L, 0.0, metaB64, 0));
        rows.add(new ZdRow(1, KEY_PAYLOAD, 0L, 0.0, payB64, 0));
        return ZdDocWriter.write(0, rows);
    }

    /**
     * 解一帧 zd 信封字节为会话命令。畸形 / 非 zd / 越界 / Base64 非法逐一抛受检
     * {@link SessionEnvelopeException}（原因固定），绝不静默产出错误数据。Decodes zd envelope
     * bytes to a session command. Malformed / non-zd / out-of-range / invalid Base64 raise the
     * checked {@link SessionEnvelopeException} (fixed reason) — never silently yields wrong data.
     *
     * @param envelope zd 信封字节 / the zd envelope bytes.
     * @return 会话命令 / the session command.
     * @throws SessionEnvelopeException 信封畸形或不可解码 / envelope malformed or undecodable.
     */
    public static SessionCommand decode(byte[] envelope) throws SessionEnvelopeException {
        if (envelope == null) {
            throw new SessionEnvelopeException(SessionEnvelopeException.LENGTH,
                    "null envelope");
        }
        final List<ZdRow> all;
        try {
            // throws IllegalArgumentException on non-zd / structurally-broken payload
            all = ZdVolume.readRows(envelope);
        } catch (IllegalArgumentException e) {
            throw new SessionEnvelopeException(SessionEnvelopeException.MALFORMED,
                    "not a zd v2 envelope: " + e.getMessage());
        }
        if (all.size() != ENVELOPE_ROWS) {
            throw new SessionEnvelopeException(
                    all.size() < ENVELOPE_ROWS ? SessionEnvelopeException.LENGTH
                            : SessionEnvelopeException.MALFORMED,
                    "expected " + ENVELOPE_ROWS + " envelope rows but got " + all.size());
        }
        ZdRow kindRow = all.get(0);
        ZdRow seqRow = all.get(1);
        ZdRow metaRow = all.get(2);
        ZdRow payRow = all.get(3);
        if (kindRow.kind() != 2 || seqRow.kind() != 2 || metaRow.kind() != 1 || payRow.kind() != 1) {
            throw new SessionEnvelopeException(SessionEnvelopeException.MALFORMED,
                    "envelope row kinds out of position (expected 2,2,1,1)");
        }
        int ord = (int) kindRow.valueI64();
        final SessionCommandKind kind;
        try {
            kind = SessionCommandKind.values()[ord];
        } catch (ArrayIndexOutOfBoundsException e) {
            throw new SessionEnvelopeException(SessionEnvelopeException.UNKNOWN_KIND,
                    "unknown command verb ordinal " + ord);
        }
        long seq = seqRow.valueI64();
        if (seq < 0) {
            throw new SessionEnvelopeException(SessionEnvelopeException.MALFORMED,
                    "negative seq " + seq);
        }
        String meta = decodeB64(metaRow.valueStr(), SessionEnvelopeException.PAYLOAD, "meta");
        String payloadB64 = decodeB64(payRow.valueStr(), SessionEnvelopeException.PAYLOAD, "payload");
        byte[] payload;
        try {
            payload = Base64.getDecoder().decode(payloadB64);
        } catch (IllegalArgumentException e) {
            throw new SessionEnvelopeException(SessionEnvelopeException.PAYLOAD,
                    "payload segment is not canonical base64");
        }
        return new SessionCommand(kind, seq, meta, payload);
    }

    /** Base64 容器段解码；空串→空字节，非法→固定原因的受检异常。Decodes a Base64 container segment. */
    private static String decodeB64(String encoded, String reason, String segment)
            throws SessionEnvelopeException {
        if (encoded == null || encoded.isEmpty()) {
            return "";
        }
        try {
            byte[] raw = Base64.getDecoder().decode(encoded);
            return new String(raw, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw new SessionEnvelopeException(reason, segment + " segment is not canonical base64");
        }
    }
}