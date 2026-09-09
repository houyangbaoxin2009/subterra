package io.toterra.subterra.engine.session;

/**
 * p.2.11.3 外部进程帧协议入口解码产出的会话帧：不可变 record，承载会话归属标签与还原出的
 * 会话命令。会话归属 {@code sessionTag} 来自帧 ext key5（EXT_STREAM_SESSION），即为
 * {@code sessionId} 的确定性固定宽映射（见 {@link SessionFrameCodec#sessionTag(String)}），
 * 外层据此还原该命令属于哪个外部进程会话；命令则为帧载荷信封解码出的
 * {@link SessionCommand}。命令内的载荷已由 {@code SessionCommand} 防御性拷贝，故本 record
 * 无需再拷贝。
 * <p>
 * p.2.11.3 value object produced by the external-process frame-protocol entry on decode: an
 * immutable record carrying the session-affiliation tag and the restored session command. The
 * session affiliation {@code sessionTag} comes from ext key5 (EXT_STREAM_SESSION), i.e. the
 * deterministic fixed-width mapping of {@code sessionId} (see
 * {@link SessionFrameCodec#sessionTag(String)}), letting the outer layer attribute the command
 * to the owning external-process session; the command itself is the {@link SessionCommand}
 * decoded from the frame-payload envelope. The payload is already defensively copied inside
 * {@link SessionCommand}, so this record needs no further copy.
 */
public record SessionFrame(int sessionTag, SessionCommand command) {

    /**
     * 显式紧凑构造：校验不变量。命令非空；{@code sessionTag} 为任意 int（标签只是确定性散列值，
     * 无符号语义）。New command must be non-null.
     * <p>
     * Explicit compact constructor: validates the invariants. The command must be non-null;
     * {@code sessionTag} is any int (the tag is a deterministic hash value with no sign semantics).
     */
    public SessionFrame {
        if (command == null) {
            throw new IllegalArgumentException("session frame command must be non-null");
        }
    }

    /** 返回命令的防御性拷贝（{@code payload} 由{@link SessionCommand}内部保证）。Returns the command. */
    @Override
    public SessionCommand command() {
        return command;
    }
}