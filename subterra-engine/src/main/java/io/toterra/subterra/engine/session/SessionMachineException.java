package io.toterra.subterra.engine.session;

/**
 * p.2.11.2 会话状态机受检异常：在错误状态 / 非法环境下调用状态机转移而抛出的分类异常。
 * 携带固定的原因常量之一作为确定性分类，可与代码精确比对；不引入时间戳/随机/时序。
 * <p>
 * 状态机对所有状态转移场景默认走<b>确定性拒绝</b>（返回 {@link SessionMachineResult}，状态
 * 不变、附固定原因），仅对「调用方程序错误」类场景（如对空命令、构造时 null 令牌、会话越权
 * 使用）抛本受检异常；选用哪条路径见 {@link SessionMachine} 的类级 Javadoc，已写死为契约。
 * <p>
 * p.2.11.2 checked exception signalled when a session-machine transition is invoked in a wrong
 * state / illegal context. It carries one of the fixed reason constants as its deterministic
 * classification, exactly comparable in code; no timestamp / random / timing is introduced.
 * <p>
 * The state machine defaults to <b>deterministic rejection</b> for all state-transition scenarios
 * (returning a {@link SessionMachineResult} with the state unchanged and a fixed reason); it only
 * raises this checked exception for caller-program error scenarios (e.g. a null command, a null
 * token at construction, session-identity misuse). Which path applies is pinned as a contract in
 * the {@link SessionMachine} class-level Javadoc.
 */
public class SessionMachineException extends Exception {

    /** 非法状态：在错误状态下调用转移。Illegal state: transition invoked in a wrong state. */
    public static final String ILLEGAL_STATE = "ILLEGAL_STATE";
    /** 序号乱序或重放：seq ≤ 当前 lastSeq（乱序与重放共用此原因，语义见 SessionMachine）。Sequence reorder OR replay: seq ≤ current lastSeq (one reason covers both; see SessionMachine). */
    public static final String REORDER = "REORDER";
    /** 重放别名（文档常量，分类统一走 {@link #REORDER}）。Replay alias (documented constant; classification uses {@link #REORDER}). */
    public static final String REPLAY = "REPLAY";
    /** 鉴权被拒：令牌不匹配，状态不变。Auth rejected: token mismatch, state unchanged. */
    public static final String AUTH_REJECT = "AUTH_REJECT";
    /** 未知命令动词。Unknown command verb. */
    public static final String UNKNOWN_COMMAND = "UNKNOWN_COMMAND";
    /** 会话已关闭。Session already closed. */
    public static final String CLOSED = "CLOSED";
    /** EXEC 载荷格式非法（key=value 契约不满足）。EXEC payload malformed (key=value contract violated). */
    public static final String EXEC_PARSE = "EXEC_PARSE";

    private final String reason;

    /**
     * 以固定原因常量构造。Constructs with a fixed reason constant.
     *
     * @param reason 原因常量 / one of the fixed reason constants above.
     */
    public SessionMachineException(String reason) {
        super(reason);
        this.reason = reason;
    }

    /**
     * 以固定原因常量 + 确定性细节构造（细节不得含时间戳/随机/时序）。Constructs with a fixed
     * reason constant and a deterministic detail (the detail must contain no timestamp/random/timing).
     *
     * @param reason 原因常量 / a fixed reason constant.
     * @param detail 确定性细节 / deterministic detail.
     */
    public SessionMachineException(String reason, String detail) {
        super(reason + ": " + detail);
        this.reason = reason;
    }

    /** 确定性原因分类。The deterministic reason classification. */
    public String reason() {
        return reason;
    }
}