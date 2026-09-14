package io.toterra.subterra.engine.interact;

import java.util.List;
import java.util.Objects;

/**
 * p.2.33.7 引擎侧镜像实现（api 定义形状 / engine 镜像实现范式，对应 {@code api.interact.InteractApi}）：
 * 以 {@code engine.interact} 的真实实现（{@link InteractKind} / {@link InteractAction} / {@link Attitude} /
 * {@link InteractRuleEngine} / {@link ZeroHudGuard}）为<b>唯一来源</b>，暴露与 {@code api.interact.InteractApi}
 * 同语义的只读契约面——受控词汇固定序、零 HUD 系统气味黑名单与单趟扫描、以及 P3 门禁 + P4 呈奉规则的门面
 * 求值均同输入同输出（供 p.2.33.7 探针对照断言）。本镜像<em>不 import</em> api 包，仅消费 engine 自身类型
 * 并把 api 契约值逐字导出，从而在两侧分别实例化后完全一致。
 * <p>确定性：全部方法为纯函数；{@link #evaluate} 逐字复用 {@link InteractRuleEngine} 的 P3/P4 优先序与
 * {@code MSG_} 固定短语表，不重写可能在两侧漂移的门禁逻辑；{@link #zeroHudCheck} 直接委托
 * {@link ZeroHudGuard#check}（单趟、固定序）。无随机、无时序。
 * <p>
 * p.2.33.7 engine-side mirror implementation (api-shapes / engine-mirrors pattern, counterpart of
 * {@code api.interact.InteractApi}): using the <b>actual</b> {@code engine.interact} implementations
 * ({@link InteractKind} / {@link InteractAction} / {@link Attitude} / {@link InteractRuleEngine} /
 * {@link ZeroHudGuard}) as the single source of truth, it exposes a read-only surface with the same semantics as
 * {@code api.interact.InteractApi} — fixed-order controlled vocabulary, the zero-HUD system-scent blacklist and
 * its single-pass scan, and the P3-gate + P4-offer facade evaluation are all same-input-same-output (the p.2.33.7
 * probe asserts both sides). This mirror does <em>not</em> import the api package; it consumes only engine types
 * and exports the api contract values verbatim, so instantiating both sides yields identical results.
 * <p>Deterministic: every method is a pure function; {@link #evaluate} reuses {@link InteractRuleEngine}'s P3/P4
 * precedence and {@code MSG_} fixed phrase table verbatim, never a re-implementation that could drift on one side;
 * {@link #zeroHudCheck} delegates directly to {@link ZeroHudGuard#check} (single-pass, fixed order). No
 * randomness, no timing.
 */
public final class InteractApiMirror {

    /** The single-interaction decision, mirroring {@code api.interact.InteractApi.Decision}. */
    public record Decision(boolean allowed, String message) {

        public Decision {
            Objects.requireNonNull(message, "decision message must not be null");
        }
    }

    private InteractApiMirror() {
    }

    /** The fixed-order entity-kind td names, sourced verbatim from {@link InteractKind#values()}. */
    public static List<String> kinds() {
        return List.of(InteractKind.NPC.tdName(), InteractKind.INSCRIPTION.tdName(),
                InteractKind.SCROLL.tdName(), InteractKind.LETTER.tdName(), InteractKind.RELIC.tdName());
    }

    /** The fixed-order action td names, sourced verbatim from {@link InteractAction#values()}. */
    public static List<String> actions() {
        return List.of(InteractAction.CONVERSE.tdName(), InteractAction.READ.tdName(),
                InteractAction.OFFER.tdName(), InteractAction.EXAMINE.tdName());
    }

    /** The fixed-order attitude td-names, sourced verbatim from {@link Attitude#values()}. */
    public static List<String> attitudes() {
        return List.of(Attitude.FRIENDLY.tdName(), Attitude.NEUTRAL.tdName(), Attitude.HOSTILE.tdName());
    }

    /** The fixed-order system-scent blacklist, sourced verbatim from {@link ZeroHudGuard#BLACKLIST}. */
    public static List<String> zeroHudBlacklist() {
        return List.copyOf(ZeroHudGuard.BLACKLIST);
    }

    /** Single-pass scan, fixed reason or empty string — direct passthrough of {@link ZeroHudGuard#check}. */
    public static String zeroHudCheck(String text) {
        return ZeroHudGuard.check(text);
    }

    /**
     * Evaluates the P3 gate + P4 offer precedence over resolved enums, mirroring
     * {@code api.interact.InteractApi#evaluate} and {@link InteractRuleEngine}'s first-hit rule with the engine's
     * own {@code MSG_} constants as the single phrase source.
     */
    public static Decision evaluate(String kindTd, String actionTd, String attitudeTd, String holdingItem) {
        InteractKind kind = InteractKind.fromTd(kindTd);
        InteractAction action = InteractAction.fromTd(actionTd);
        Attitude att = Attitude.fromTd(attitudeTd);
        if (kind == null || action == null || att == null) {
            throw new IllegalArgumentException("unknown interact vocabulary: kind=" + kindTd
                    + ", action=" + actionTd + ", attitude=" + attitudeTd);
        }
        if (action == InteractAction.EXAMINE && kind == InteractKind.NPC) {
            return new Decision(false, InteractRuleEngine.MSG_EXAMINE_NPC);
        }
        if (action == InteractAction.EXAMINE) {
            return new Decision(true, InteractRuleEngine.MSG_EXAMINE);
        }
        if (att == Attitude.HOSTILE) {
            return new Decision(false, InteractRuleEngine.MSG_HOSTILE_REJECT);
        }
        if (att == Attitude.FRIENDLY) {
            if (action == InteractAction.OFFER) {
                if (holdingItem != null) {
                    return new Decision(true, String.format(InteractRuleEngine.MSG_OFFER_WITH_ITEM, holdingItem));
                }
                return new Decision(true, InteractRuleEngine.MSG_FRIENDLY_ACCEPT);
            }
            return new Decision(true, action == InteractAction.READ
                    ? InteractRuleEngine.MSG_FRIENDLY_READ : InteractRuleEngine.MSG_FRIENDLY_CONVERSE);
        }
        if (att == Attitude.NEUTRAL) {
            if (action == InteractAction.OFFER) {
                if (holdingItem != null) {
                    return new Decision(true, String.format(InteractRuleEngine.MSG_OFFER_WITH_ITEM, holdingItem));
                }
                return new Decision(true, InteractRuleEngine.MSG_NEUTRAL_OFFER);
            }
            return new Decision(false, InteractRuleEngine.MSG_NEUTRAL_REJECT);
        }
        return new Decision(true, InteractRuleEngine.MSG_DEFAULT_ALLOW);
    }
}