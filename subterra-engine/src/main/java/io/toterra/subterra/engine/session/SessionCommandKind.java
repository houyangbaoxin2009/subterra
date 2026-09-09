package io.toterra.subterra.engine.session;

/**
 * p.2.11.1 会话命令动词槽：确定性、自描述的会话命令类型枚举。命令随 zd 文档信封头行的
 * kind-2 行 {@code valueI64 = ordinal()} 表达，解析侧用固定的 {@code values()} 序解码；
 * {@code PING, QUERY, EXEC, BYE} 的 ordinal 0–3 即权威序（新增动词只能追加在末尾，不得
 * 重排）。该风格与 p.2.5 {@code P2pMessageType} 的类型槽语义一致：单一 codec 内确定性，
 * 类型自描述、不依赖任何握手或注册表上下文。
 * <p>
 * p.2.11.1 session-command verb slot: a deterministic, self-describing command-verb enum.
 * The verb travels in the zd envelope-header row (the kind-2 row {@code valueI64 = ordinal()});
 * the reader decodes it against the fixed {@code values()} order, and {@code PING, QUERY, EXEC,
 * BYE} occupy ordinals 0–3 as the authoritative order (new verbs append only, never reorder).
 * This mirrors the p.2.5 {@code P2pMessageType} type-slot semantics: deterministic within a
 * single codec, self-describing, with no dependency on any handshake or registry context.
 */
public enum SessionCommandKind {
    /** 活性探测 / 心跳。Liveness probe / heartbeat. */
    PING,
    /** 查询运行时状态。Query runtime state. */
    QUERY,
    /** 执行会话动作。Execute a session action. */
    EXEC,
    /** 优雅关闭会话。Graceful session close. */
    BYE
}