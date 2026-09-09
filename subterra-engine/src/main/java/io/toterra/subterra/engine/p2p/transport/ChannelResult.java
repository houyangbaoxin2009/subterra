package io.toterra.subterra.engine.p2p.transport;

import java.util.Arrays;

/**
 * p.2.5.4 frame-level delivery result for a single {@code receive(...)} call. Failure is
 * never silent: an incomplete / blocked message surfaces a {@link Status#REJECT} with a
 * concrete {@code reason} instead of dropping bytes quietly.
 * <p>
 * 中文：p.2.5.4 单次 {@code receive(...)} 的帧级投递结果。失败从不静默吞掉：不完整 / 阻塞的
 * 消息以 {@link Status#REJECT} 与具体 {@code reason} 显式返回，而不是悄悄丢弃字节。
 *
 * @param status  DELIVERED (a complete message) / PENDING (buffered, waiting) / REJECT (deterministic failure)
 * @param message the assembled message when {@code status==DELIVERED}, else {@code null}
 * @param reason  a deterministic reason string when {@code status==REJECT}, else {@code null}
 */
public record ChannelResult(Status status, byte[] message, String reason) {

    /** Delivery / buffering status. */
    public enum Status { DELIVERED, PENDING, REJECT }

    public static ChannelResult delivered(byte[] message) {
        return new ChannelResult(Status.DELIVERED, message, null);
    }

    public static ChannelResult pending() {
        return new ChannelResult(Status.PENDING, null, null);
    }

    public static ChannelResult reject(String reason) {
        return new ChannelResult(Status.REJECT, null, reason);
    }

    @Override
    public String toString() {
        return "ChannelResult{" + status
                + (message != null ? ", bytes=" + message.length : "")
                + (reason != null ? ", reason='" + reason + '\'' : "")
                + '}';
    }

    /** Convenience for probes: byte compare against an expected message. */
    public boolean delivers(byte[] expected) {
        return status == Status.DELIVERED && Arrays.equals(message, expected);
    }
}