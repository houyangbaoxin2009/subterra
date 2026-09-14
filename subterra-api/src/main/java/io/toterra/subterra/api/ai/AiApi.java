package io.toterra.subterra.api.ai;

import java.util.List;

/**
 * p.2.33.4 对外的 AI 编排契约门面（final class、静态纯函数、纯 JDK）：确定性接口面，供上层/域模组按
 * 「固定编排序 + 三上限预算语义」驱动 Brain 编排。语义与 {@code engine.ai} 的
 * {@code Brain#tick}/{@code BrainScheduler}/{@code BrainScheduler.TickBudget}（p.2.24.1/.2）一致——本处
 * 为契约与数据面注入，engine 为实现镜像（{@code engine.ai.AiApiMirror}），api 不依赖 engine。所有契约
 * 常量均系从 engine 实际常量/语义逐字对照落于此，禁止拍脑袋数值。
 * <p>确定性：{@link #phaseOrder()} 返回固定编排序（{@code sense, decide, execute}，镜像
 * {@code Brain#tick} 的「感知→决策→执行」阶段序）；{@link #budgetCaps()} 返回固定三上限名
 * （{@code senseUnits, behaviorUnits, taskUnits}，镜像 {@code BrainScheduler.TickBudget} record 组件）；
 * {@link #validateBudget} 为纯校验——任何上限 {@code < 1} 确定性以 {@link IllegalArgumentException} 拒绝
 * （镜像 {@code BrainScheduler.TickBudget#of}），线上限接受。
 * <p>关于 AI 仲裁的随机源：engine 的 {@code AiRandom}（p.2.24.2）<em>委托</em>给
 * {@code engine.pcg.PcgSource}/{@code XoroRandom}（与 MC 1.21.1 逐位一致的 Xoroshiro128++），同种子同序列
 * 必逐位一致。该 PRNG 流在引擎内部实现，api 不作字节级复制（避免在中立契约面伪造逐位引擎内部），其确定性
 * 由委托而非重实现保证；本契约面只暴露编排序/预算等与随机数无关的确定性语义。
 * <p>
 * p.2.33.4 the external AI orchestration contract facade (final class, static pure functions, pure JDK): a
 * deterministic interface surface for upper layers / domain mods to drive Brain orchestration from a "fixed
 * orchestration order + three-cap budget semantics". Semantics match {@code engine.ai}'s
 * {@code Brain#tick}/{@code BrainScheduler}/{@code BrainScheduler.TickBudget} (p.2.24.1/.2) — this is the
 * contract and data-injection surface for the engine to mirror as its implementation
 * ({@code engine.ai.AiApiMirror}), and the api does not depend on the engine. Every contract constant is
 * pinned verbatim from the engine's actual constants/semantics — nothing is guessed.
 * <p>Deterministic: {@link #phaseOrder()} returns the fixed orchestration order ({@code sense, decide,
 * execute}, mirroring {@code Brain#tick}'s sense→decide→execute phase order);
 * {@link #budgetCaps()} returns the fixed three-cap names ({@code senseUnits, behaviorUnits, taskUnits},
 * mirroring the {@code BrainScheduler.TickBudget} record components); {@link #validateBudget} is a pure
 * validation — any cap {@code < 1} is rejected deterministically with an
 * {@link IllegalArgumentException} (mirroring {@code BrainScheduler.TickBudget#of}), while on-cap values are
 * accepted.
 * <p>On the AI arbitration randomness: the engine's {@code AiRandom} (p.2.24.2) <em>delegates</em> to
 * {@code engine.pcg.PcgSource}/{@code XoroRandom} (a Xoroshiro128++ PRNG bit-identical to Minecraft 1.21.1);
 * same seed, same call sequence ⇒ bit-identical. That PRNG stream lives inside the engine and is not
 * byte-duplicated into the neutral contract surface (to avoid fabricating bit-exact engine internals); its
 * determinism is guaranteed by delegation rather than re-implementation. This contract surface exposes only
 * the randomization-free orchestration/budget semantics above.
 */
public final class AiApi {

    /** Fixed orchestration phase order ({@code sense, decide, execute}), mirroring {@code Brain#tick} /
     *  固定编排阶段序（{@code sense, decide, execute}），镜像 {@code Brain#tick}。 */
    public static final List<String> PHASE_ORDER = List.of("sense", "decide", "execute");

    /** Fixed three-slot per-tick budget caps ({@code senseUnits, behaviorUnits, taskUnits}), mirroring
     *  {@code BrainScheduler.TickBudget} component order. / 固定三槽每 tick 预算上限（{@code senseUnits,
     *  behaviorUnits, taskUnits}），镜像 {@code BrainScheduler.TickBudget} 组件序。 */
    public static final List<String> BUDGET_CAPS = List.of("senseUnits", "behaviorUnits", "taskUnits");

    /** Number of orchestration phases ({@code 3}). / 编排阶段数（{@code 3}）。 */
    public static final int PHASE_COUNT = 3;

    private AiApi() {
    }

    /** The fixed orchestration phase order ({@code sense, decide, execute}). Deterministic. /
     *  固定编排阶段序（{@code sense, decide, execute}）。确定性。 */
    public static List<String> phaseOrder() {
        return PHASE_ORDER;
    }

    /** The number of orchestration phases ({@code 3}). / 编排阶段数（{@code 3}）。 */
    public static int phaseCount() {
        return PHASE_COUNT;
    }

    /** The fixed three-slot budget cap names. Deterministic. / 固定三槽预算上限名。确定性。 */
    public static List<String> budgetCaps() {
        return BUDGET_CAPS;
    }

    /**
     * Validates a per-tick three-slot budget with the exact {@code >= 1} semantics of
     * {@code engine.ai.BrainScheduler.TickBudget#of}: any cap {@code < 1} is rejected deterministically with
     * an {@link IllegalArgumentException}; valid budgets pass silently. Pure and side-effect free.
     * / 以 {@code engine.ai.BrainScheduler.TickBudget#of} 精确的 {@code >= 1} 语义校验每 tick 三槽预算：
     * 任何上限 {@code < 1} 确定性以 {@link IllegalArgumentException} 拒绝；合法预算静默通过。纯且无副作用。
     *
     * @param senseUnits    the per-tick sensor-execution cap ({@code >= 1}).
     * @param behaviorUnits the per-tick behavior-evaluation cap ({@code >= 1}).
     * @param taskUnits     the per-tick task-execution cap ({@code >= 1}).
     * @throws IllegalArgumentException if any cap is {@code < 1}.
     */
    public static void validateBudget(int senseUnits, int behaviorUnits, int taskUnits) {
        if (senseUnits < 1 || behaviorUnits < 1 || taskUnits < 1) {
            throw new IllegalArgumentException(
                    "each budget cap must be >= 1 (senseUnits=" + senseUnits
                            + ", behaviorUnits=" + behaviorUnits + ", taskUnits=" + taskUnits + ")");
        }
    }
}