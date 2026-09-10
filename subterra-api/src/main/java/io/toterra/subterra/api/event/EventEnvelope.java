package io.toterra.subterra.api.event;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * p.2.16.4 确定性事件信封（事件契约面）：{@code (seq, kind, payloadType, payload)} —— 单调序号 +
 * 类型化载荷。{@code seq} 必须严格递增（{@code >} 上一事件；首事件 {@code seq>=0} 即可通过），
 * 乱序/重放由消费方（如 {@link EventStream} 的 append 门）拒绝，语义对齐 p.2.11 的
 * monotonic-seq 门（p.2.11.2 以单一 REORDER 同时覆盖乱序与重放）。{@code payloadType} 声明载荷
 * 的确定性格式（如 UTF-8 文本、tink v2 帧字节），{@link #textPayload()} 为默认 UTF-8 文本读取
 * 辅助。防御性拷贝风格同 p.2.11.1：载荷数组进出均复制，外部不可变引用。
 * <p>
 * p.2.16.4 deterministic event envelope (the event contract surface): {@code (seq, kind,
 * payloadType, payload)} — a monotonic sequence number plus a typed payload. {@code seq} must be
 * strictly increasing ({@code >} the previous event; the first event may have {@code seq>=0});
 * reorder/replay are rejected by the consumer (e.g. the {@link EventStream} append gate), aligning
 * with the p.2.11 monotonic-seq gate (p.2.11.2 pins both reorder and replay under one REORDER).
 * {@code payloadType} declares the deterministic payload format (e.g. UTF-8 text, tink v2 frame
 * bytes); {@link #textPayload()} is the default UTF-8 text reader helper. Defensive-copy style as
 * p.2.11.1: the payload array is copied on the way in and out, never reachable as mutable.
 *
 * @param seq         单调事件序号（严格递增，见类注释）/ the monotonic event seq (strictly increasing, see the class comment).
 * @param kind        事件种类 / the event kind.
 * @param payloadType 载荷格式声明 / the payload format declaration.
 * @param payload     载荷字节 / the payload bytes.
 */
public record EventEnvelope(long seq, EventKind kind, String payloadType, byte[] payload) {

    /**
     * 紧凑构造：防御性拷贝载荷，拒绝 null 组件（调用方程序错误）。
     * Compact constructor: defensively copies the payload; rejects null components (caller-program
     * error).
     */
    public EventEnvelope {
        Objects.requireNonNull(kind, "kind must be non-null");
        Objects.requireNonNull(payloadType, "payloadType must be non-null");
        payload = payload.clone();
    }

    /**
     * 防御性拷贝：内部载荷从不以可变形式暴露。Defensive copy: the internal payload is never
     * exposed as mutable.
     *
     * @return 载荷副本 / a copy of the payload.
     */
    @Override
    public byte[] payload() {
        return payload.clone();
    }

    /**
     * 默认 UTF-8 文本读取辅助（{@code payloadType} 声明为文本格式时使用）。
     * The default UTF-8 text reader (use when {@code payloadType} declares a text format).
     *
     * @return 按 UTF-8 解码的载荷文本 / the payload text decoded as UTF-8.
     */
    public String textPayload() {
        return new String(payload(), StandardCharsets.UTF_8);
    }

    /**
     * 静态工厂：构造确定性事件信封（载荷防御拷贝）。Static factory: builds an envelope (payload
     * defensively copied).
     *
     * @param seq         单调事件序号 / the monotonic event seq.
     * @param kind        事件种类 / the event kind.
     * @param payloadType 载荷格式声明 / the payload format declaration.
     * @param payload     载荷字节 / the payload bytes.
     * @return 事件信封 / the event envelope.
     */
    public static EventEnvelope from(long seq, EventKind kind, String payloadType, byte[] payload) {
        return new EventEnvelope(seq, kind, payloadType, payload);
    }

    /**
     * 便捷静态工厂：UTF-8 文本载荷直接落地。Convenience static factory: a UTF-8 text payload
     * straight to bytes.
     *
     * @param seq         单调事件序号 / the monotonic event seq.
     * @param kind        事件种类 / the event kind.
     * @param payloadType 载荷格式声明 / the payload format declaration.
     * @param text        文本载荷 / the text payload.
     * @return 事件信封 / the event envelope.
     */
    public static EventEnvelope fromText(long seq, EventKind kind, String payloadType, String text) {
        return new EventEnvelope(seq, kind, payloadType, text.getBytes(StandardCharsets.UTF_8));
    }
}
