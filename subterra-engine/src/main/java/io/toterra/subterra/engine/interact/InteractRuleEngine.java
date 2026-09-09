package io.toterra.subterra.engine.interact;

import java.util.Map;
import java.util.Objects;

/**
 * 确定性交互规则引擎 —— p.2.14.2 的示例实现：对一次"世界内交互"（对某 {@link InteractSurface} 施加某
 * {@link InteractAction}）做<b>确定性</b>求值，产出 {@link InteractResult}。纯 JDK、final、无外部依赖；
 * 每类规则以固定序求值、第一命中即出结果，同 {@code (spec, target, action, state)} 两次求值逐字段一致
 * （可回放），且不引入任何时间戳/随机/时序。
 * <p>
 * <b>求值顺序即规则优先级（写死）</b>，按下列固定优先级逐级判定：
 * <pre>
 *   P1 表面归属   按规范<b>固定文档序</b>首遇 target 匹配的表面；未找到 → 拒绝（固定短语）；
 *   P2 动作许可   action 不在表面 {@code actions} 内 → 拒绝（固定短语）；
 *   P3 态度/动作/种类门禁（本类私有固定表，第一命中即返回，优先级从高到低）：
 *        P3.1 EXAMINE 且 entity 为 NPC       → 拒绝（固定短语：活物无可端详之书）；
 *        P3.2 实体非 NPC 的 EXAMINE           → 放行（固定端详短语）——INSCRIPTION/SCROLL/LETTER/RELIC；
 *        P3.3 attitude == HOSTILE            → 拒绝（固定短语：敌意拒之门外）；
 *        P3.4 attitude == FRIENDLY           → 放行（CONVERSE/READ 用各自固定可/读短语；OFFER 走 P4）；
 *        P3.5 attitude == NEUTRAL            → OFFER 放行（固定呈奉短语，走 P4）；CONVERSE/READ 拒绝；
 *   P4 状态前提/效果  纯状态、固定键序：OFFER 且 state 含 {@code "held.<item>"}（固定序遍历首遇）→
 *        放行、结果短语引用该物品，并把 {@code "offered.<item>"} = 对应 held 值确定性写入 {@code nextState}。
 * </pre>
 * P1→P2→P3→P4 为最高层优先序；P3 内部又按 P3.1..P3.5 固定子序第一命中判出。因此规则优先级由序写死，
 * 不依赖任何运行时次序输入。
 * <p>
 * <b>零 HUD 消息约束（写死）</b>：{@code message} 只可能来自本类的私有固定短语表（见 {@code MSG_} 常量）。
 * 该短语表经人工审查<b>不含</b>任何面板 / 进度条 / 数值计数 / 任务日志 / 等级图标 / 系统弹窗 / 系统提示
 * 语义或词汇，且不含裸数字计数式 UI 文本 —— 语义上与 p.2.14.1 {@code package-info} 的无系统气味约束一致。
 * <p>
 * {@code null} 输入（spec / target / action / state）属程序错误，一律抛 {@link IllegalArgumentException}。
 * <p>
 * Deterministic interaction-rule engine — the p.2.14.2 example implementation: it evaluates one "in-world
 * interaction" (applying one {@link InteractAction} to one {@link InteractSurface}) <b>deterministically</b>
 * into an {@link InteractResult}. Pure JDK, {@code final}, no external dependency; every rule class runs in
 * fixed order and the first hit decides, so two evaluations of the same {@code (spec, target, action, state)}
 * are field-for-field identical (replayable), with no timestamp / random / timing introduced.
 * <p>
 * <b>Evaluation order is rule precedence (pinned)</b>, decided level by level in the following fixed precedence:
 * <pre>
 *   P1 surface ownership  first matching surface by target in the spec's <b>fixed document order</b>;
 *                         not found → reject (fixed phrase);
 *   P2 action permission  action not in the surface's {@code actions} → reject (fixed phrase);
 *   P3 attitude/action/kind gate (a private fixed table, first hit decides, precedence high to low):
 *        P3.1 EXAMINE on an entity that is an NPC  → reject (fixed phrase: nothing written to study in a living being);
 *        P3.2 EXAMINE on a non-NPC entity          → allowed (fixed examine phrase) — INSCRIPTION/SCROLL/LETTER/RELIC;
 *        P3.3 attitude == HOSTILE                 → reject (fixed phrase: hostility turns one away);
 *        P3.4 attitude == FRIENDLY                → allowed (fixed converse/read phrases for CONVERSE/READ; OFFER goes to P4);
 *        P3.5 attitude == NEUTRAL                 → OFFER allowed (fixed offer phrase, to P4); CONVERSE/READ rejected;
 *   P4 state preconditions/effects  pure state, fixed key order: OFFER with {@code "held.<item>"} present in state
 *        (first hit in fixed order) → allowed, the result phrase references the item, and {@code "offered.<item>"}
 *        = the corresponding held value is deterministically written into {@code nextState}.
 * </pre>
 * P1→P2→P3→P4 is the top-level precedence; within P3 the fixed sub-order P3.1..P3.5 decides on first hit. Hence
 * rule precedence is pinned by order, never by runtime-ordered input.
 * <p>
 * <b>Zero-HUD message constraint (pinned)</b>: {@code message} can come only from this class's private fixed
 * phrase table (see the {@code MSG_} constants). That table is hand-audited and contains <b>no</b> panel /
 * progress-bar / numeric-counter / task-log / level-icon / system-popup / system-prompt term or semantics, and
 * no bare numeric-counter UI text — consistent with the no-system-scent constraint of the p.2.14.1
 * {@code package-info}.
 * <p>
 * A {@code null} input (spec / target / action / state) is a program error and always raises
 * {@link IllegalArgumentException}.
 */
public final class InteractRuleEngine {

    private InteractRuleEngine() {
    }

    // ---- 固定短语表（零 HUD：无面板/进度条/数值计数/任务日志/等级图标/系统弹窗/系统提示，无裸数字计数文本）----
    // ---- Fixed phrase table (zero HUD: no panel/progress/numeric-counter/task-log/level-icon/ system-popup/system-prompt semantics and no bare numeric-counting text) ----
    /** 表面未找到。Surface not found within the spec. */
    public static final String MSG_SURFACE_NOT_FOUND = "You find nothing here that answers to that.";
    /** 动作不被表面许可。Action not permitted by the surface. */
    public static final String MSG_ACTION_NOT_PERMITTED = "That is not something you can do.";
    /** 对活物端详。Examining a living being. */
    public static final String MSG_EXAMINE_NPC = "There is nothing written in a living being to study.";
    /** 端详非活物实体。Examining a non-living entity. */
    public static final String MSG_EXAMINE = "You study its form closely, taking in each detail.";
    /** 敌意拒之门外。Hostile rejection of any approach. */
    public static final String MSG_HOSTILE_REJECT = "They turn from you, unreceptive to word or hand.";
    /** 中立之拒。Neutral rejection of converse/read. */
    public static final String MSG_NEUTRAL_REJECT = "They keep a certain distance, uncommitted in word or gesture.";
    /** 友善之交谈。Friendly converse. */
    public static final String MSG_FRIENDLY_CONVERSE = "They answer you, and the exchange settles into understanding.";
    /** 友善之阅读。Friendly read. */
    public static final String MSG_FRIENDLY_READ = "You read, and the words settle into understanding.";
    /** 友善纳呈。Friendly acceptance of an offering. */
    public static final String MSG_FRIENDLY_ACCEPT = "They receive your offering with good will.";
    /** 中立纳呈。Neutral acceptance of an offering. */
    public static final String MSG_NEUTRAL_OFFER = "They regard your offering without haste.";
    /** 持有某物时呈奉（{@code %s} 为世界内物品名，仅来自世界状态，确定性插值）。Offering while carrying an item ({@code %s} is the in-world item name, from the in-world state only, deterministic interpolation). */
    public static final String MSG_OFFER_WITH_ITEM = "You offer them the %s.";
    /** 兜底放行（防御，正常不可达）。Fallback allowance (defensive, normally unreachable). */
    public static final String MSG_DEFAULT_ALLOW = "It is met openly.";

    /** 世界状态里持有物品的键前缀。Key prefix marking an item carried in the in-world state. */
    private static final String HELD_PREFIX = "held.";

    /**
     * 目标引用 —— 指向规范内某一 {@link InteractSurface} 的世界内目标文本（原样照抄，与表面 {@code target}
     * 相等即命中）。A target reference — the in-world target text pointing to one {@link InteractSurface}
     * in the spec (carried verbatim; it hits when equal to a surface's {@code target}).
     *
     * @param target 世界内目标文本 / the in-world target text.
     */
    public record InteractTargetRef(String target) {

        /** 空值防御。Null-guard. */
        public InteractTargetRef {
            Objects.requireNonNull(target, "target reference must not be null");
        }
    }

    /**
     * 确定性求值一次世界内交互（P1→P2→P3→P4 固定优先序，第一命中即出结果）。Deterministically evaluates one
     * in-world interaction (fixed precedence P1→P2→P3→P4, first hit decides).
     *
     * @param spec   交互规范 / the interaction spec.
     * @param target 目标引用 / the target reference.
     * @param action 世界内动作 / the in-world action.
     * @param state  当前世界状态快照 / the current in-world state snapshot.
     * @return 不可变求值结果 / the immutable evaluation result.
     * @throws IllegalArgumentException 任一参数为 null / any argument is null.
     */
    public static InteractResult resolve(InteractSpec spec, InteractTargetRef target,
                                         InteractAction action, InteractState state) {
        Objects.requireNonNull(spec, "spec must not be null");
        Objects.requireNonNull(target, "target must not be null");
        Objects.requireNonNull(action, "action must not be null");
        Objects.requireNonNull(state, "state must not be null");

        // P1 表面归属：固定文档序首遇命中。
        InteractSurface surface = null;
        for (InteractSurface s : spec.surfaces()) {
            if (s.target().equals(target.target())) {
                surface = s;
                break;
            }
        }
        if (surface == null) {
            return new InteractResult(false, MSG_SURFACE_NOT_FOUND, state);
        }

        // P2 动作许可。
        if (!surface.actions().contains(action)) {
            return new InteractResult(false, MSG_ACTION_NOT_PERMITTED, state);
        }

        return gateAndState(surface, action, state);
    }

    /**
     * P3 门禁 + P4 状态规则（固定子序，第一命中即出结果）。P3 gate + P4 state rules (fixed sub-order, first
     * hit decides).
     */
    private static InteractResult gateAndState(InteractSurface surface, InteractAction action,
                                               InteractState state) {
        InteractKind kind = surface.kind();
        Attitude att = surface.attitude();

        // P3.1 — EXAMINE 仅限非活物实体。
        if (action == InteractAction.EXAMINE && kind == InteractKind.NPC) {
            return new InteractResult(false, MSG_EXAMINE_NPC, state);
        }
        // P3.2 — 非活物实体 (INSCRIPTION/SCROLL/LETTER/RELIC) 的端详。
        if (action == InteractAction.EXAMINE) {
            return new InteractResult(true, MSG_EXAMINE, state);
        }
        // P3.3 — HOSTILE：交谈/呈奉皆拒（读亦无可近）。
        if (att == Attitude.HOSTILE) {
            return new InteractResult(false, MSG_HOSTILE_REJECT, state);
        }
        // P3.4 — FRIENDLY：交谈/阅读放行，呈奉走 P4。
        if (att == Attitude.FRIENDLY) {
            if (action == InteractAction.OFFER) {
                return offerAllowed(surface, state, MSG_FRIENDLY_ACCEPT);
            }
            String msg = action == InteractAction.READ ? MSG_FRIENDLY_READ : MSG_FRIENDLY_CONVERSE;
            return new InteractResult(true, msg, state);
        }
        // P3.5 — NEUTRAL：仅呈奉放行（走 P4），交谈/阅读拒绝。
        if (att == Attitude.NEUTRAL) {
            if (action == InteractAction.OFFER) {
                return offerAllowed(surface, state, MSG_NEUTRAL_OFFER);
            }
            return new InteractResult(false, MSG_NEUTRAL_REJECT, state);
        }

        // 防御兜底（枚举已封闭，正常不可达）。
        return new InteractResult(true, MSG_DEFAULT_ALLOW, state);
    }

    /**
     * P4 状态前提/效果：呈奉时若世界状态下持有一物（固定键序首遇 {@code "held."} 前缀），则放行、结果短语引用
     * 该物品，并将 {@code "offered.<item>"} = 对应 held 值确定性写入 {@code nextState}；未持有则仅按 {@code baseMsg}
     * 放行、状态不变。P4 state rules: when offering, if the in-world state carries an item (first hit for the
     * {@code "held."} prefix in fixed key order), the offer is allowed, the result phrase references that item,
     * and {@code "offered.<item>"} = the corresponding held value is deterministically written into
     * {@code nextState}; otherwise it is allowed with {@code baseMsg} and the state is unchanged.
     */
    private static InteractResult offerAllowed(InteractSurface surface, InteractState state, String baseMsg) {
        for (Map.Entry<String, String> e : state.values().entrySet()) {
            if (e.getKey().startsWith(HELD_PREFIX)) {
                String item = e.getKey().substring(HELD_PREFIX.length());
                InteractState next = state.with("offered." + item, e.getValue());
                return new InteractResult(true, String.format(MSG_OFFER_WITH_ITEM, item), next);
            }
        }
        return new InteractResult(true, baseMsg, state);
    }
}