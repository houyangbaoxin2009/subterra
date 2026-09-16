/**
 * p.2.30.1 LAN 打洞核心（engine.p2p.holepunch）。纯 JDK、确定性：端口预测
 * （PRESERVE/INCREMENT/RANDOM）、计划器（PREDICTED/WILDCARD/RELAY 有序尝试、
 * 延迟确定性递增）、会话状态机（同事件序同轨迹、终态后事件确定性拒绝）。
 * 实际 UDP I/O 由 runtime 侧注入（自打洞样例走 loopback），engine 不引用 MC。
 * 复用 p.2.5 寻址槽（{@code NodeAddr.Direct} / {@code NodeAddr.ViaRelay}）与
 * relay 兜底语义（p.2.5.8）。
 *
 * <p>The p.2.30.1 LAN holepunch core (engine.p2p.holepunch). Pure JDK, deterministic:
 * port prediction (PRESERVE/INCREMENT/RANDOM), the planner (ordered
 * PREDICTED/WILDCARD/RELAY attempts with deterministically growing delays), and the
 * session state machine (same event sequence → same trajectory; post-terminal events
 * rejected deterministically). Actual UDP I/O is injected runtime-side (the self-punch
 * sample goes over loopback); the engine never references MC. Reuses the p.2.5
 * addressing slots ({@code NodeAddr.Direct} / {@code NodeAddr.ViaRelay}) and the
 * relay-fallback semantics (p.2.5.8).
 */
package io.toterra.subterra.engine.p2p.holepunch;
