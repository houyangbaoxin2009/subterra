package io.toterra.subterra.engine.session;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

/**
 * p.2.11.2 确定性会话状态机（实例级，无全局可变状态）：把「连接 → 鉴权 → 命令派发 → 状态回读 →
 * 断开」全生命周期落为固定转移序。禁时间戳 / 随机 / 全局扫描，同一输入序列必然逐场一致。
 * <p>
 * <b>状态与合法转移表（确定性）</b>：
 * <ul>
 *   <li>{@code CLOSED}：{@code open()}→HELLO（唯一合法）；其余（authenticate/dispatch/status 均
 *       确定性拒绝）。{@code close()} 再调用→确定性拒绝（原因 {@code CLOSED}）；</li>
 *   <li>{@code HELLO}：{@code authenticate()}→令牌对→ACTIVE；令牌错→确定性拒绝（原因
 *       {@code AUTH_REJECT}）且状态不变（仍 HELLO）；{@code open()} 再调用→确定性拒绝（原因
 *       {@code ILLEGAL_STATE}）；{@code dispatch()}→确定性拒绝（{@code ILLEGAL_STATE}）；
 *       {@code close()}→CLOSED；</li>
 *   <li>{@code AUTH}：当前流程不进入（预留槽），故无合法来源转移；若被非法置入（防御起见）一律
 *       {@code ILLEGAL_STATE} 拒绝，不会产生新状态推进；</li>
 *   <li>{@code ACTIVE}：{@code dispatch()}→按 kind 路由（见下）；{@code close()}→CLOSED；
 *       {@code open()}/{@code authenticate()}→确定性拒绝（{@code ILLEGAL_STATE}）。</li>
 * </ul>
 * <b>seq 门禁</b>：仅为 ACTIVE 的 {@code dispatch()} 消费命令，且要求 {@code cmd.seq()}
 * 严格递增（{@code > lastSeq}，lastSeq 初值 {@code -1}，故首命令 {@code seq>=0} 即可通过）；否则
 * 确定性拒绝并钉死原因 {@code REORDER}——该原因同时覆盖<b>乱序</b>（seq ≤ lastSeq）与<b>重放</b>
 * （同一 seq 再次出现亦 ≤ lastSeq，即 REPLAY），注释约定统一走 REORDER。被拒绝的命令不推进
 * {@code lastSeq}。仅成功派发（PING/QUERY/EXEC/BYE）推进 {@code lastSeq}；已知动词但不合法状态
 * （如非 ACTIVE 派发）或未知动词一律不推进。
 * <p>
 * <b>命令路由（确定性，均仅 ACTIVE 生效）</b>：
 * <ul>
 *   <li>{@code PING}→回显 {@code OK}（message 固定 {@code pong}）；</li>
 *   <li>{@code QUERY}→回读确定性快照（{@code READBACK}，携带固定键序的只读
 *       {@code TreeMap<String,String>}）；</li>
 *   <li>{@code EXEC}→按文档契约（payload 为 UTF-8 文本，「key=value 行」契约，见 {@link #applyExec}）
 *       确定性更新实例内「可编程运行态」（固定键序 {@code TreeMap}），完成后 {@code OK}；</li>
 *   <li>{@code BYE}→{@code ACTIVE→CLOSED}（{@code CLOSED} 结果）；</li>
 *   <li>未知动词（枚举后续追加而本机未处理）→确定性拒绝（{@code UNKNOWN_COMMAND}）且不推进 seq。</li>
 * </ul>
 * <b>非法调用策略（写死）</b>：所有「错误状态下调用转移」一律<b>确定性拒绝</b>（返回
 * {@link SessionMachineResult}，状态不变、附固定原因），<b>不抛异常</b>；仅对「调用方程序错误」
 * 抛 {@link IllegalArgumentException}：{@code dispatch(null)}、构造传入 null 令牌、{@code open(null)}。
 * 本机不抛 {@link SessionMachineException}；该类及其原因常量作为本包所有确定性拒绝所用原因串的
 * 权威词汇表，供上层按需选用（如某些调用方愿以投掷形式表达拒绝）。同 p.2.11.1 的防御性拷贝风格，
 * 载荷从不被外部引用可变。
 * <p>
 * p.2.11.2 deterministic session state machine (instance-level, no global mutable state): it pins
 * the full lifecycle &quot;connect → auth → dispatch → read-back → disconnect&quot; to a fixed
 * transition order. No timestamp / random / global scan — the same input sequence always yields the
 * same per-step outcomes.
 * <p>
 * <b>States &amp; legal transitions (deterministic)</b>:
 * <ul>
 *   <li>{@code CLOSED}: {@code open()}→HELLO (the only legal one); anything else
 *       (authenticate/dispatch/status) is deterministically rejected. A further {@code close()} is
 *       deterministically rejected (reason {@code CLOSED});</li>
 *   <li>{@code HELLO}: {@code authenticate()}→HELLO→ACTIVE on a matching token; on a mismatch a
 *       deterministic rejection (reason {@code AUTH_REJECT}) leaves the state unchanged (still HELLO);
 *       a further {@code open()} is rejected ({@code ILLEGAL_STATE}); {@code dispatch()} is rejected
 *       ({@code ILLEGAL_STATE}); {@code close()}→CLOSED;</li>
 *   <li>{@code AUTH}: not entered by this flow (reserved slot); hence no legal source transition. Any
 *       defensive attempt to advance from it is rejected ({@code ILLEGAL_STATE}) and never produces a
 *       new state;</li>
 *   <li>{@code ACTIVE}: {@code dispatch()} routes by kind (see below); {@code close()}→CLOSED;
 *       {@code open()}/{@code authenticate()} are deterministically rejected ({@code ILLEGAL_STATE}).</li>
 * </ul>
 * <b>seq gate</b>: only {@code dispatch()} in ACTIVE consumes a command, and it requires
 * {@code cmd.seq()} to be strictly increasing ({@code > lastSeq}, with {@code lastSeq} initially
 * {@code -1}, so the first command with {@code seq>=0} passes); otherwise it deterministically rejects
 * and pins the reason {@code REORDER} — this single reason covers both <b>reorder</b> ({@code seq ≤
 * lastSeq}) and <b>replay</b> (a revisited seq is also {@code ≤ lastSeq}, i.e. REPLAY); the comment
 * convention routes both to REORDER. A rejected command never advances {@code lastSeq}. Only a
 * successfully dispatched command (PING/QUERY/EXEC/BYE) advances {@code lastSeq}; a known verb in an
 * illegal state (e.g. dispatch outside ACTIVE) or an unknown verb never advances it.
 * <p>
 * <b>Command routing (deterministic, ACTIVE only)</b>:
 * <ul>
 *   <li>{@code PING}→echo {@code OK} (message fixed to {@code pong});</li>
 *   <li>{@code QUERY}→read back the deterministic snapshot ({@code READBACK}, a fixed-key-order
 *       read-only {@code TreeMap<String,String>});</li>
 *   <li>{@code EXEC}→deterministically update the instance "programmable runtime state" (a
 *       fixed-key-order {@code TreeMap}) per the documented contract (payload is UTF-8 text, the
 *       &quot;key=value line&quot; contract, see {@link #applyExec}), then {@code OK};</li>
 *   <li>{@code BYE}→{@code ACTIVE→CLOSED} (a {@code CLOSED} result);</li>
 *   <li>unknown verb (a future enum value this machine does not yet handle)→deterministic rejection
 *       ({@code UNKNOWN_COMMAND}) without advancing seq.</li>
 * </ul>
 * <b>Illegal-call policy (pinned)</b>: every wrong-state transition is <b>deterministically
 * rejected</b> (a {@link SessionMachineResult} returns, state unchanged, fixed reason), and
 * <b>never throws</b>; {@link IllegalArgumentException} is thrown only for caller-program errors:
 * {@code dispatch(null)}, a null token at construction, {@code open(null)}. This machine never throws
 * {@link SessionMachineException}; that class and its constants serve as the authoritative vocabulary
 * of the reason strings used by every deterministic rejection here, for upper layers that prefer to
 * express a rejection as a thrown signal. It follows the p.2.11.1 defensive-copy style: a payload is
 * never reachable as mutable from outside.
 */
public final class SessionMachine {

    /** 确定性可编程运行态：固定键序，EXEC 落键值、QUERY/status 只读回读。The deterministic programmable runtime state. */
    private final TreeMap<String, String> runtime = new TreeMap<>();

    /** 实例固定鉴权令牌（构造注入，可变报文不得暴露为可写）。The instance-fixed auth token (injected at construction). */
    private final String authToken;

    /** 会话标识（构造/打开时固定，仅承载确定性身份信息）。The session id (identity only, deterministic). */
    private final String sessionId;

    /** 当前状态（每次转移原子推进）。The current state (advanced atomically on each transition). */
    private SessionState state = SessionState.CLOSED;

    /** 已接受的最大命令序号；初值 -1 令首命令 {@code seq>=0} 可过。The highest accepted command seq; -1 so the first {@code seq>=0} passes. */
    private long lastSeq = -1L;

    /** 默认鉴权令牌（常量；可作为未显式注入时的固定缺省）。Default fixed auth token (constant; the fixed default when not injected explicitly). */
    public static final String DEFAULT_AUTH_TOKEN = "subterra-session-v1";

    private static final String KEY_SEP = "=";

    /**
     * 以固定鉴权令牌构造状态机。token 为 null 抛 {@link IllegalArgumentException}（程序错误）。
     * Constructs the machine with a fixed auth token. A null token raises
     * {@link IllegalArgumentException} (caller-program error).
     *
     * @param sessionId 会话标识 / the session id.
     * @param authToken 固定鉴权令牌 / the fixed auth token to accept.
     */
    public SessionMachine(String sessionId, String authToken) {
        if (sessionId == null) {
            throw new IllegalArgumentException("sessionId must be non-null");
        }
        if (authToken == null) {
            throw new IllegalArgumentException("auth token must be non-null");
        }
        this.sessionId = sessionId;
        this.authToken = authToken;
    }

    /**
     * 开关会话：仅 {@code CLOSED} 下合法，{@code CLOSED→HELLO} 并重置 lastSeq 为 -1、清空运行态。
     * Open a session: legal only from {@code CLOSED}; {@code CLOSED→HELLO}, resets {@code lastSeq} to
     * -1 and clears the runtime state.
     *
     * @param sessionId 会话标识，必须非空 / the session id, must be non-null.
     * @return ACCEPT（进入 HELLO）或确定性拒绝 / ACCEPT (→HELLO) or a deterministic rejection.
     */
    public SessionMachineResult open(String sessionId) {
        if (sessionId == null) {
            throw new IllegalArgumentException("sessionId must be non-null");
        }
        if (state != SessionState.CLOSED) {
            return reject(SessionMachineException.ILLEGAL_STATE, -1,
                    "open requires CLOSED but state is " + state);
        }
        runtime.clear();
        lastSeq = -1L;
        state = SessionState.HELLO;
        return accept(SessionMachineResult.Outcome.ACCEPT, -1, "opened");
    }

    /**
     * 鉴权：仅 {@code HELLO} 下合法。令牌与实例固定令牌逐字相等→{@code HELLO→ACTIVE}；不相等→
     * 确定性拒绝（{@code AUTH_REJECT}）且状态不变。
     * Authenticate: legal only in {@code HELLO}. If the token equals the instance fixed token
     * →{@code HELLO→ACTIVE}; otherwise a deterministic rejection ({@code AUTH_REJECT}) with the state
     * unchanged.
     *
     * @param token 待鉴权令牌 / the token to authenticate.
     * @return ACCEPT（进入 ACTIVE）或确定性拒绝 / ACCEPT (→ACTIVE) or a deterministic rejection.
     */
    public SessionMachineResult authenticate(String token) {
        if (state != SessionState.HELLO) {
            return reject(SessionMachineException.ILLEGAL_STATE, -1,
                    "authenticate requires HELLO but state is " + state);
        }
        if (!authToken.equals(token)) {
            return reject(SessionMachineException.AUTH_REJECT, -1, "token mismatch");
        }
        state = SessionState.ACTIVE;
        return accept(SessionMachineResult.Outcome.ACCEPT, -1, "authenticated");
    }

    /**
     * 命令派发：仅 {@code ACTIVE} 消费命令。先跑 seq 门禁（严格递增，违例钉死 {@code REORDER}，
     * 覆盖乱序与重放），再按 kind 路由：PING→OK 回显；QUERY→READBACK 快照；EXEC→按
     * key=value 行契约确定性更新后 OK；BYE→ACTIVE→CLOSED；未知动词→UNKNOWN_COMMAND 拒绝且不推进 seq。
     * {@code cmd} 为 null 抛 {@link IllegalArgumentException}（程序错误）。
     * Dispatch a command: consumed only in {@code ACTIVE}. First the seq gate (strictly increasing,
     * violations pinned to {@code REORDER}, covering reorder and replay), then route by kind: PING→OK
     * echo; QUERY→READBACK snapshot; EXEC→deterministic update per the key=value-line contract then OK;
     * BYE→ACTIVE→CLOSED; unknown verb→UNKNOWN_COMMAND rejection without advancing seq. A null
     * {@code cmd} raises {@link IllegalArgumentException} (caller-program error).
     *
     * @param cmd 要派发的命令 / the command to dispatch.
     * @return 确定性派发结果 / the deterministic dispatch result.
     */
    public SessionMachineResult dispatch(SessionCommand cmd) {
        if (cmd == null) {
            throw new IllegalArgumentException("command must be non-null");
        }
        if (state != SessionState.ACTIVE) {
            return reject(SessionMachineException.ILLEGAL_STATE, cmd.seq(),
                    "dispatch requires ACTIVE but state is " + state);
        }
        final long s = cmd.seq();
        if (s <= lastSeq) {
            // REORDER 同时覆盖乱序（seq ≤ lastSeq）与重放（同 seq 复现亦 ≤ lastSeq）。
            // A single REORDER covers reorder (seq ≤ lastSeq) and replay (a revisited seq is also ≤ lastSeq).
            return reject(SessionMachineException.REORDER, s,
                    "seq must be strictly > lastSeq; got " + s + ", lastSeq " + lastSeq);
        }
        switch (cmd.kind()) {
            case PING:
                lastSeq = s;
                return accept(SessionMachineResult.Outcome.OK, s, "pong");
            case QUERY:
                lastSeq = s;
                return readback(s);
            case EXEC:
                return applyExec(s, cmd);
            case BYE:
                lastSeq = s;
                state = SessionState.CLOSED;
                return closed(s, "bye");
            default:
                // 未知动词（枚举后续追加而本机未处理）：拒绝且不推进 seq、不改状态。
                // Unknown verb (future enum value not handled here): rejected, seq and state untouched.
                return reject(SessionMachineException.UNKNOWN_COMMAND, s,
                        "unknown command verb " + cmd.kind());
        }
    }

    /**
     * 状态回读：返回当前状态 + 确定性快照（固定键序只读 {@link TreeMap}）。取当前状态并复制运行态，
     * 确定性结果无副作用。Read back status: returns the current state plus the deterministic snapshot
     * (a fixed-key-order read-only {@link TreeMap}). Reads the current state and copies the runtime; a
     * deterministic result with no side effect.
     *
     * @return READBACK（含快照）/ READBACK (with snapshot).
     */
    public SessionMachineResult status() {
        return new SessionMachineResult(state, SessionMachineResult.Outcome.READBACK, "", -1,
                "status", new TreeMap<>(runtime));
    }

    /**
     * 断开：非 CLOSED 一律→CLOSED；CLOSED 再调用→确定性拒绝（{@code CLOSED}）。会清理运行态。
     * Disconnect: any non-CLOSED state→CLOSED; a further close from CLOSED is deterministically
     * rejected ({@code CLOSED}). Clears the runtime state.
     *
     * @return CLOSED 或确定性拒绝 / CLOSED or a deterministic rejection.
     */
    public SessionMachineResult close() {
        if (state == SessionState.CLOSED) {
            return reject(SessionMachineException.CLOSED, -1, "already closed");
        }
        state = SessionState.CLOSED;
        runtime.clear();
        return closed(-1, "closed");
    }

    /**
     * EXEC 落地：按文档契约解析 payload（UTF-8 文本，「key=value 行」）并确定性更新运行态。
     * <b>载荷契约</b>：payload 为 UTF-8 文本，按 {@code '\n'} 分段；每段调用
     * {@link String#trim()} 后为空则跳过；非空段必须形如 {@code key=value}（取首个 {@code '='} 分隔，
     * key/value 各自 trim），key 非空；否则整条 EXEC 确定性拒绝（{@code EXEC_PARSE}），且<b>既不改
     * 状态也不推进 seq，也不部分应用</b>（先整体校验、再整体落地）。合法输入逐段写入固定键序运行态
     * （重复 key 覆盖），全部成功后推进 {@code lastSeq} 并返回 {@code OK}。
     * <p>
     * EXEC apply: parses the payload per the documented contract (UTF-8 text, &quot;key=value
     * lines&quot;) and deterministically updates the runtime state.
     * <b>Payload contract</b>: the payload is UTF-8 text split on {@code '\n'}; a segment whose
     * {@link String#trim()} is empty is skipped; a non-empty segment must be of the form
     * {@code key=value} (split on the first {@code '='}, key/value each trimmed), key non-empty;
     * otherwise the whole EXEC is deterministically rejected ({@code EXEC_PARSE}), and <b>neither the
     * state nor {@code lastSeq} changes, nor is anything partially applied</b> (validate everything,
     * then apply). On valid input each segment is written into the fixed-key-order runtime (a repeated
     * key overwrites), {@code lastSeq} advances, and {@code OK} is returned.
     *
     * @param s   已过门的命令序号 / the gated command seq.
     * @param cmd 命令 / the command.
     * @return OK（已落地）或确定性拒绝（EXEC_PARSE）/ OK (applied) or a deterministic rejection (EXEC_PARSE).
     */
    private SessionMachineResult applyExec(long s, SessionCommand cmd) {
        final byte[] payload = cmd.payload();
        if (payload.length == 0) {
            lastSeq = s;
            return accept(SessionMachineResult.Outcome.OK, s, "exec noop");
        }
        final String text = new String(payload, StandardCharsets.UTF_8);
        final String[] lines = text.split("\n", -1);
        final List<String[]> parsed = new ArrayList<>(lines.length);
        for (int i = 0; i < lines.length; i++) {
            final String line = lines[i].trim();
            if (line.isEmpty()) {
                continue;
            }
            final int eq = line.indexOf(KEY_SEP);
            if (eq <= 0) {
                // 整体拒绝：不推进 seq、不改状态、不部分应用。Whole rejection: no seq advance, no state change, no partial apply.
                return reject(SessionMachineException.EXEC_PARSE, s,
                        "line " + i + " is not key=" + KEY_SEP + "value");
            }
            final String key = line.substring(0, eq).trim();
            final String val = line.substring(eq + 1).trim();
            if (key.isEmpty()) {
                return reject(SessionMachineException.EXEC_PARSE, s,
                        "line " + i + " has an empty key");
            }
            parsed.add(new String[]{key, val});
        }
        for (String[] kv : parsed) {
            runtime.put(kv[0], kv[1]);
        }
        lastSeq = s;
        return accept(SessionMachineResult.Outcome.OK, s, "exec " + parsed.size() + " key(s)");
    }

    /** READBACK 结果：携带随机体快照，固定键序确定性回读。READBACK result with a copied fixed-key-order snapshot. */
    private SessionMachineResult readback(long s) {
        return new SessionMachineResult(state, SessionMachineResult.Outcome.READBACK, "", s,
                "snapshot " + runtime.size() + " key(s)", new TreeMap<>(runtime));
    }

    /** 确定性接受结果（reason 用约定空态）。A deterministic acceptance (reason is the fixed acceptance token). */
    private SessionMachineResult accept(SessionMachineResult.Outcome outcome, long seq, String message) {
        return new SessionMachineResult(state, outcome, "", seq, message, null);
    }

    /** CLOSED 结果。A CLOSED result. */
    private SessionMachineResult closed(long seq, String message) {
        return new SessionMachineResult(SessionState.CLOSED, SessionMachineResult.Outcome.CLOSED,
                "", seq, message, null);
    }

    /** 确定性拒绝结果：状态不变、附固定原因，seq 随调用方给定（派发拒携带违例 seq）。A deterministic rejection: state unchanged, fixed reason, seq as caller-provided. */
    private SessionMachineResult reject(String reason, long seq, String detail) {
        return new SessionMachineResult(state, SessionMachineResult.Outcome.REJECT, reason, seq,
                detail, null);
    }
}