package io.toterra.subterra.engine.session;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

/**
 * p.2.11.2 会话状态机结果：每一次状态转移（或确定性拒绝/回读）的不可变值对象。
 * <ul>
 *   <li>{@code state}：转移后的期望状态（确定性拒绝时等于转移前状态，即状态不变）；</li>
 *   <li>{@code outcome}：{@link Outcome} 分类（ACCEPT/REJECT/OK/READBACK/CLOSED）；</li>
 *   <li>{@code reason}：确定性原因（接受时为约定的空态约定串，拒绝时为 {@code SessionMachineException}
 *       的原因常量，如 REORDER/AUTH_REJECT/UNKNOWN_COMMAND/CLOSED/EXEC_PARSE/ILLEGAL_STATE）；</li>
 *   <li>{@code seq}：触发本结果的命令序号（派发返回携带 {@code cmd.seq()}；非派发方法如 open/
 *       authenticate/status/close 为 {@code -1} 表示「无命令序号」）；{@code >= -1}；</li>
 *   <li>{@code message}：仅承载确定性信息（指令回显/最短确认文本等，禁时间戳/随机/时序）；</li>
 *   <li>{@code snapshot}：可空确定性快照，用 {@link TreeMap}（固定键序）承载，回读只读、防御性
 *       拷贝，仅当 {@code outcome == READBACK} 时非空。</li>
 * </ul>
 * 全体为不可变值对象，{@code snapshot} 在构造时防御性拷贝、访问时只读；字段均不注入任何时序。
 * <p>
 * p.2.11.2 state-machine result: an immutable value object for every transition (or deterministic
 * rejection / read-back).
 * <ul>
 *   <li>{@code state}: the expected post-transition state (identical to the pre-transition state on
 *       a deterministic rejection, i.e. no state change);</li>
 *   <li>{@code outcome}: the {@link Outcome} classification (ACCEPT/REJECT/OK/READBACK/CLOSED);</li>
 *   <li>{@code reason}: the deterministic reason (a fixed acceptance token on acceptance; on
 *       rejection one of the {@code SessionMachineException} reason constants such as REORDER /
 *       AUTH_REJECT / UNKNOWN_COMMAND / CLOSED / EXEC_PARSE / ILLEGAL_STATE);</li>
 *   <li>{@code seq}: the command seq that triggered this result (dispatch returns carry
 *       {@code cmd.seq()}; non-dispatch methods such as open/authenticate/status/close carry
 *       {@code -1} meaning &quot;no command seq&quot;); {@code >= -1};</li>
 *   <li>{@code message}: carries deterministic information only (command echo / minimal ack text,
 *       never timestamp/random/timing);</li>
 *   <li>{@code snapshot}: a nullable deterministic snapshot carried by a {@link TreeMap} (fixed key
 *       order), read-only and defensively copied; non-null only when {@code outcome == READBACK}.</li>
 * </ul>
 * The whole object is immutable; the {@code snapshot} is defensively copied at construction and
 * exposed read-only; no timing is injected into any field.
 */
public record SessionMachineResult(
        SessionState state,
        Outcome outcome,
        String reason,
        long seq,
        String message,
        Map<String, String> snapshot) {

    /**
     * 结果分类（本包内枚举）。均为确定性结果，无负向时序依赖。
     * The outcome classification (package enum). All deterministic, with no timing dependency.
     */
    public enum Outcome {
        /** 转移被接受（open 成功、authenticate 成功等）。Transition accepted (open ok, auth ok, ...). */
        ACCEPT,
        /** 转移被确定性拒绝（状态不变，带固定原因）。Transition deterministically rejected (state unchanged, fixed reason). */
        REJECT,
        /** 命令已执行成功（PING 回显、EXEC 落地）。Command executed ok (PING echo, EXEC applied). */
        OK,
        /** 确定性状态回读，携带只读快照。Deterministic read-back carrying a read-only snapshot. */
        READBACK,
        /** 会话已断开（BYE / close）。Session disconnected (BYE / close). */
        CLOSED
    }

    /**
     * 显式紧凑构造：校验并归一化不变量。非空 {@code state}/{@code outcome}；{@code seq >= -1}；
     * null {@code reason}/{@code message} 归一为空串；null {@code snapshot} 保持可空，非 null 时
     * 防御性拷贝成固定键序的 {@link TreeMap} 并封为只读（回读单向）。{@code snapshot} 仅当
     * {@code outcome == READBACK} 时允许非空。
     * <p>
     * Explicit compact constructor: validates and normalises the invariants. Non-null
     * {@code state}/{@code outcome}; {@code seq >= -1}; null {@code reason}/{@code message}
     * normalise to empty; a null {@code snapshot} stays null, otherwise it is defensively copied
     * into a fixed-key-order {@link TreeMap} and exposed read-only (one-way read-back). A snapshot
     * is only allowed to be non-null when {@code outcome == READBACK}.
     */
    public SessionMachineResult {
        if (state == null) {
            throw new IllegalArgumentException("session-machine result state must be non-null");
        }
        if (outcome == null) {
            throw new IllegalArgumentException("session-machine result outcome must be non-null");
        }
        if (seq < -1) {
            throw new IllegalArgumentException("session-machine result seq must be >= -1 but was " + seq);
        }
        reason = reason == null ? "" : reason;
        message = message == null ? "" : message;
        if (snapshot != null && outcome != Outcome.READBACK) {
            throw new IllegalArgumentException("snapshot is only allowed on a READBACK outcome");
        }
        snapshot = snapshot == null
                ? null
                : Collections.unmodifiableMap(new TreeMap<>(snapshot));
    }

    /** 返回回读快照的只读视图（仅 READBACK 时非空）。Read-only view of the read-back snapshot. */
    @Override
    public Map<String, String> snapshot() {
        return snapshot;
    }
}