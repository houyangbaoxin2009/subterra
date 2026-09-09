package io.toterra.subterra.engine.p2p.transport;

/**
 * p.2.5.4 logical P2P transport packet. One packet is the unit that maps onto a caller
 * message; it carries the per-message session sequence ({@code seq}), a caller type
 * ({@code type}), a chunk-reference id ({@code ref}} — normalised to the session sequence
 * so chunk frames group under a single message), the full or per-assembly payload, and
 * opaque {@code meta}. {@code seq} is authoritative for ordering, de-dup and replay
 * rejection; the frame-level stream session (tink v2 {@code EXT_STREAM_SESSION}) is bound
 * to the same value.
 * <p>
 * 中文：p.2.5.4 逻辑 P2P 传输包。一个包对应一条调用者消息，携带会话序号 {@code seq}、调用者
 * 类型 {@code type}、分块引用 id {@code ref}（归一为会话序号，使分块帧归并到同一消息）、整段
 * 或装配中的载荷 {@code payload}，以及不透明 {@code meta}。{\code seq} 是排序、去重与重放拒绝
 * 的权威；帧级流会话（tink v2 {@code EXT_STREAM_SESSION}）绑定到同一值。
 *
 * @param seq     per-message session sequence (monotonic, from {@link SeqCounter})
 * @param type    caller packet type
 * @param ref     chunk-reference id (= session sequence; groups chunk frames of one message)
 * @param payload full payload (send) or an assembled segment
 * @param meta    opaque metadata (may be empty)
 */
public record P2pPacket(long seq, int type, long ref, byte[] payload, byte[] meta) {
}