package io.toterra.subterra.engine.p2p.relay;

/**
 * p.2.5.8 志愿 relay 兜底转发的一次确定性结果：{@code FORWARDED}（命中登记并投递，送达承诺）/
 * {@code DROPPED(reason)}（丢弃，如 hop 超限防环）/{@code REJECTED(reason)}（明确拒绝，如 token
 * 未登记 / 错配）。失败从不让调用方摸不着头脑——{@code DROPPED}/{@code REJECTED} 始终携带一个
 * 确定性 {@code reason} 字符串；{@code FORWARDED} 不携带 reason。纯 JDK，无 MC 依赖。
 * <p>
 * p.2.5.8 One deterministic outcome of a voluntary-relay fallback forward: {@code FORWARDED} (a
 * registration was hit and the payload delivered — a delivery commitment) / {@code DROPPED(reason)}
 * (discarded, e.g. hop-limit loop protection) / {@code REJECTED(reason)} (explicitly refused, e.g.
 * unregistered / mismatched token). A failure is never silent — both {@code DROPPED} and
 * {@code REJECTED} always carry a deterministic {@code reason} string. Pure JDK, no Minecraft.
 *
 * @param status {@code FORWARDED} (delivered) / {@code DROPPED} (discarded, has reason) / {@code REJECTED} (refused, has reason)
 * @param reason a deterministic reason for {@code DROPPED}/{@code REJECTED}, else {@code null}
 */
public record ForwardResult(Status status, String reason) {

    /** Deterministic outcome status. */
    public enum Status { FORWARDED, DROPPED, REJECTED }

    public static ForwardResult forwarded() {
        return new ForwardResult(Status.FORWARDED, null);
    }

    public static ForwardResult dropped(String reason) {
        return new ForwardResult(Status.DROPPED, reason);
    }

    public static ForwardResult rejected(String reason) {
        return new ForwardResult(Status.REJECTED, reason);
    }

    /** Whether this outcome is a successful delivery. */
    public boolean isForwarded() {
        return status == Status.FORWARDED;
    }
}