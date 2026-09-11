package io.toterra.subterra.engine.ai;

/**
 * Behavior (p.2.24.1): decides from the {@link BrainMemory} whether to act this
 * tick ({@link #start} returns {@code true}) and, when decided, performs its
 * action via {@link #tick}. Behaviors run in fixed registration order; a
 * behavior must be deterministic — same memory, same decision, same action.
 *
 * <p>行为（p.2.24.1）：依 {@link BrainMemory} 决策本 tick 是否行动（{@link #start}
 * 返回 {@code true}），决策后经 {@link #tick} 执行动作。行为按固定注册序运行；行为必须
 * 确定性——同记忆、同决策、同动作。
 *
 * <p>Model reference only (clean-room, zero code included): the "behavior"
 * layering idea of the vanilla {@code net.minecraft.world.entity.ai.Brain} and
 * SmartBrainLib (MPL-2.0); this interface is original deterministic Subterra
 * code. Pure JDK, no Minecraft code touched.
 * <p>模型参考（clean-room，零代码包含）：原版 {@code net.minecraft.world.entity.ai.Brain}
 * 与 SmartBrainLib（MPL-2.0）的「行为」分层思想；本接口为 Subterra 原创确定性代码。
 * 纯 JDK，不触碰任何 MC 代码。
 */
public interface BrainBehavior {

    /** The unique registration name of this behavior. 本行为的唯一注册名。 */
    String name();

    /**
     * Decides whether this behavior acts this tick, based on {@code memory}.
     * Must be deterministic and side-effect free w.r.t. the memory.
     *
     * 依 {@code memory} 决策本 tick 是否行动。必须确定性且对记忆无副作用。
     */
    boolean start(BrainMemory memory);

    /**
     * Executes the behavior's action; only called when {@link #start} returned
     * {@code true} in the same tick. 执行行为动作；仅当同一 tick 内 {@link #start}
     * 返回 {@code true} 时调用。
     */
    void tick(BrainMemory memory);
}
