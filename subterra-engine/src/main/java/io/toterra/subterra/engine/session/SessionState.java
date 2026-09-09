package io.toterra.subterra.engine.session;

/**
 * p.2.11.2 会话状态机状态槽：固定的 {@code values()} 权威序
 * {@code CLOSED, HELLO, AUTH, ACTIVE}。权威序即 ordinal，解析/比对侧一律用固定的
 * {@code values()} 序解码，不得重排（新增状态只能追加在末尾）。连线/鉴权/派发/回读/断开的
 * 生命周期与合法迁移在本包 {@code SessionMachine} 中落为确定性转移表；本枚举仅声明状态槽。
 * <p>
 * 本连接流程的确定性转移为 {@code CLOSED→HELLO→ACTIVE→CLOSED}（open 一次进入 HELLO、
 * authenticate 由 HELLO 一次性跳转 ACTIVE）；{@code AUTH} 状态为声明槽，预留给将来需要显式
 * 双阶段鉴权的握手变体接入，当前流程不进入它，但固定其序以保证枚举 ABI 稳定。
 * <p>
 * p.2.11.2 session-state slot: the fixed {@code values()} authoritative order
 * {@code CLOSED, HELLO, AUTH, ACTIVE}. The order <em>is</em> the ordinal; readers/compare code
 * must decode against the fixed {@code values()} sequence and never reorder (new states append
 * only). The connect/auth/dispatch/read-back/disconnect lifecycle and its legal transitions are
 * pinned as a deterministic transition table in {@code SessionMachine} in this package; this enum
 * only declares the state slots.
 * <p>
 * The deterministic transition path of this connect flow is {@code CLOSED→HELLO→ACTIVE→CLOSED}
 * (open enters HELLO once, authenticate jumps HELLO→ACTIVE in one step). {@code AUTH} is a declared
 * slot reserved for an explicitly two-phase auth handshake variant to plug in later; the current
 * flow does not enter it, but its position is fixed so the enum ABI stays stable.
 */
public enum SessionState {
    /** 会话未建立 / 已断开。Session not established / disconnected. */
    CLOSED,
    /** 已建立连接，握手完成，待鉴权。Connection established, handshake done, awaiting auth. */
    HELLO,
    /** 鉴权中 / 双阶段鉴权保留状态槽（本流程不进入）。Authenticating / two-phase-auth reserved slot (not entered by this flow). */
    AUTH,
    /** 已鉴权，命令可派发。Authenticated, commands dispatchable. */
    ACTIVE
}