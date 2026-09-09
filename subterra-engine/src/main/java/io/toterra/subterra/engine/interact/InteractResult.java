package io.toterra.subterra.engine.interact;

import java.util.Objects;

/**
 * 不可变确定性求值结果 —— p.2.14.2 {@link InteractRuleEngine} 的单次求值输出：本次交互是否被允许、以<b>世界内
 * 语言</b>表达的确定性短语、以及求值后的世界状态快照（如呈奉带来的状态追加）。本 record 的 {@code message}
 * 只由固定短语表产生，绝不引入随机/时间戳/数值计数式 UI 文本（零 HUD，见 {@link InteractRuleEngine} Javadoc）。
 * 同输入两次求值产出逐字段一致的可回放结果。
 * <p>
 * Immutable deterministic evaluation result — the single output of an {@link InteractRuleEngine} evaluation:
 * whether the interaction is allowed, a deterministic phrase expressed in <b>in-world language</b>, and the
 * in-world state snapshot after evaluation (e.g. the state appended by an offer). This record's {@code message}
 * is produced only from the fixed phrase table and never carries random / timestamp / numeric-counter UI text
 * (zero HUD, see the {@link InteractRuleEngine} Javadoc). Two evaluations of the same input yield a replayable
 * result, field-for-field identical.
 *
 * @param allowed   本次交互是否被允许 / whether the interaction is allowed.
 * @param message   世界内语言确定性短语（仅来自固定短语表）/ the deterministic in-world-language phrase (from the fixed phrase table only).
 * @param nextState 求值后的世界状态快照 / the in-world state snapshot after evaluation.
 */
public record InteractResult(boolean allowed, String message, InteractState nextState) {

    /**
     * 紧凑构造：空值防御（{@code message}、{@code nextState} 必非 null）。Compact constructor: null-guards
     * ({@code message} and {@code nextState} must not be null).
     */
    public InteractResult {
        Objects.requireNonNull(message, "result message must not be null");
        Objects.requireNonNull(nextState, "result nextState must not be null");
    }
}