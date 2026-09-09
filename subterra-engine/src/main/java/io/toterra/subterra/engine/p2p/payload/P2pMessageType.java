package io.toterra.subterra.engine.p2p.payload;

/**
 * p.2.5.5 载荷消息类型槽：确定性的、无中心账号/会话的自描述消息类型枚举。类型随 zd 文档的信封头
 * 行（position-0 的 kind-2 行 {@code valueI64 = ordinal()}）表达，解析侧用固定的 {@code values()}
 * 序解码，因此在单一 codec 内是确定性的；外层无中心账号/会话——类型即自描述，不依赖任何握手或
 * 注册表上下文。
 * <p>
 * p.2.5.5 payload message-type slot: a deterministic, self-describing message-type enum with no
 * central account or session. The type travels in the zd document's envelope header row (the
 * position-0 kind-2 row {@code valueI64 = ordinal()}); the reader decodes it against the fixed
 * {@code values()} order, so it is deterministic within a single codec — self-describing, with no
 * dependency on any handshake or registry context.
 */
public enum P2pMessageType {
    /** 状态/心跳元数据。Status / heartbeat metadata. */
    STATUS,
    /** 表级同步。Table-level synchronisation. */
    SYNC,
    /** 大 chunk 数据。Large chunk data. */
    CHUNK,
    /** 中继转发。Relay forward. */
    RELAY,
    /** 增量表（delta-table）变更。Delta-table change set. */
    DT
}