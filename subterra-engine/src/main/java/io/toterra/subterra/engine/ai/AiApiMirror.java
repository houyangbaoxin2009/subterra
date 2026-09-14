package io.toterra.subterra.engine.ai;

import java.util.List;

/**
 * p.2.33.4 引擎侧镜像实现（api 定义形状 / engine 镜像实现范式，对应 {@code api.ai.AiApi}）：以
 * {@link Brain} / {@link BrainScheduler} / {@link BrainScheduler.TickBudget} 的<b>真实语义</b>为唯一来源，
 * 暴露与 {@code api.ai.AiApi} 同语义的只读契约面——固定编排序、三上限名与预算 {@code >= 1} 校验均同输入
 * 同输出（供 p.2.33.4 探针对照断言）。本镜像<em>不 import</em> api 包，仅消费 engine 自身类型并把 api 契约
 * 值逐字导出，从而在两侧分别实例化后完全一致。
 * <p>确定性：全部方法为纯函数；{@link #validateBudget} 直接委托 {@link BrainScheduler.TickBudget#of}
 * （{@code >= 1} 语义），非法上限确定性拒绝。编排阶段名/{@code Brain} 的 {@code sense→decide→execute}
 * 阶段序见 {@code Brain#tick} 的 javadoc。AI 仲裁随机源 {@link AiRandom} 委托
 * {@code engine.pcg.PcgSource}（Xoroshiro128++），其同种子同序列逐位一致性在引擎内由委托保证——本镜像与
 * api 均不复制该引擎内部 PRNG 流。
 * <p>
 * p.2.33.4 engine-side mirror implementation (api-shapes / engine-mirrors pattern, counterpart of
 * {@code api.ai.AiApi}): using the <b>actual semantics</b> of {@link Brain} / {@link BrainScheduler} /
 * {@link BrainScheduler.TickBudget} as the single source of truth, it exposes a read-only surface with the
 * same semantics as {@code api.ai.AiApi} — the fixed orchestration order, the three-cap names and the budget
 * {@code >= 1} validation are all same-input-same-output (the p.2.33.4 probe asserts both sides). This mirror
 * does <em>not</em> import the api package; it consumes only engine types and exports the api contract values
 * verbatim, so instantiating both sides yields identical results.
 * <p>Deterministic: every method is a pure function; {@link #validateBudget} delegates directly to
 * {@link BrainScheduler.TickBudget#of} (the {@code >= 1} semantics), rejecting illegal caps deterministically.
 * The orchestration phase names / {@code Brain}'s {@code sense→decide→execute} phase order follow the javadoc
 * of {@code Brain#tick}. The AI arbitration randomness source {@link AiRandom} delegates to
 * {@code engine.pcg.PcgSource} (Xoroshiro128++); its same-seed-same-sequence bit-identity is guaranteed inside
 * the engine by delegation — neither this mirror nor the api copy that engine-internal PRNG stream.
 */
public final class AiApiMirror {

    private AiApiMirror() {
    }

    /** The fixed orchestration phase order, sourced from {@code Brain#tick}'s public semantics
     *  ({@code sense, decide, execute}). / 固定编排阶段序，源自 {@code Brain#tick} 的公开语义
     *  （{@code sense, decide, execute}）。 */
    public static List<String> phaseOrder() {
        return List.of("sense", "decide", "execute");
    }

    /** The number of orchestration phases ({@code 3}). / 编排阶段数（{@code 3}）。 */
    public static int phaseCount() {
        return phaseOrder().size();
    }

    /** The fixed three-slot budget cap names, sourced verbatim from the
     *  {@link BrainScheduler.TickBudget} record component order. / 固定三槽预算上限名，逐字源自
     *  {@link BrainScheduler.TickBudget} record 组件序。 */
    public static List<String> budgetCaps() {
        return List.of("senseUnits", "behaviorUnits", "taskUnits");
    }

    /**
     * Validates a per-tick three-slot budget by delegating to {@link BrainScheduler.TickBudget#of} — the exact
     * {@code >= 1} semantics mirrored from {@code api.ai.AiApi#validateBudget}. Rejects any cap {@code < 1} with
     * an {@link IllegalArgumentException}; valid budgets pass silently whether it throws or the api does — both
     * accept on-cap values on the same inputs. / 通过委托 {@link BrainScheduler.TickBudget#of} 校验每 tick
     * 三槽预算——即镜像自 {@code api.ai.AiApi#validateBudget} 的精确 {@code >= 1} 语义。任何上限 {@code < 1}
     * 以 {@link IllegalArgumentException} 拒绝；合法预算静默通过——其与 api 是否抛错或是否对同输入线上限都
     * 接受保持一致。
     *
     * @param senseUnits    the per-tick sensor-execution cap ({@code >= 1}).
     * @param behaviorUnits the per-tick behavior-evaluation cap ({@code >= 1}).
     * @param taskUnits     the per-tick task-execution cap ({@code >= 1}).
     * @throws IllegalArgumentException if any cap is {@code < 1}.
     */
    public static void validateBudget(int senseUnits, int behaviorUnits, int taskUnits) {
        BrainScheduler.TickBudget.of(senseUnits, behaviorUnits, taskUnits);
    }
}