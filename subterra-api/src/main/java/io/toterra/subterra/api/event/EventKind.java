package io.toterra.subterra.api.event;

/**
 * p.2.16.4 确定性事件种类（事件契约面）：固定的 {@code values()} 权威序即契约序，解析/比对一律
 * 按此序，不得重排（新增种类只能追加在末尾，保证枚举 ABI 稳定，同 p.2.11.2 {@code SessionState}
 * 的追加式纪律）。每种事件携带确定性 {@link #form()} 注册名，类型化载荷在
 * {@link EventEnvelope} 中按 {@code payloadType} 声明。
 * <p>
 * p.2.16.4 deterministic event kinds (the event contract surface): the fixed {@code values()}
 * authoritative order <em>is</em> the contract order; readers/compare code must decode against it
 * and never reorder (new kinds append only, keeping the enum ABI stable, mirroring the p.2.11.2
 * {@code SessionState} append-only discipline). Each kind carries a deterministic {@link #form()}
 * registration name; typed payloads are declared via {@code payloadType} on {@link EventEnvelope}.
 */
public enum EventKind {

    /** tink v2 帧到达（p.2.4.2 帧语义：ext 流头 session/seq，帧序即 seq 序）。tink v2 frame arrival (p.2.4.2 frame semantics: ext stream header session/seq, frame order == seq order). */
    FRAME_RECEIVED("frame.received"),

    /** 会话状态转移（p.2.11.2 确定性转移表，如 ACTIVE→CLOSED）。Session state transition (p.2.11.2 deterministic transition table, e.g. ACTIVE→CLOSED). */
    SESSION_STATE_CHANGED("session.state_changed"),

    /** 生命周期事件（会话建立/断开等端到端生命周期）。Lifecycle event (session open/close and other end-to-end lifecycle). */
    LIFECYCLE("lifecycle"),

    /** 日志事件（确定性诊断输出）。Log event (deterministic diagnostics). */
    LOG("log");

    /** 注册名（小写点分，确定性契约，非 Java 枚举名）。The registration name (lower-case dotted, the deterministic contract; not the Java enum name). */
    private final String form;

    EventKind(String form) {
        this.form = form;
    }

    /**
     * 确定性注册名（如 {@code "frame.received"}），供序列化/路由使用。
     * The deterministic registration name (e.g. {@code "frame.received"}), for serialization/routing.
     *
     * @return 注册名 / the registration name.
     */
    public String form() {
        return form;
    }
}
