package io.toterra.subterra.engine.ai;

/**
 * Task (p.2.24.1): the atomic behavior primitive — executes deterministically
 * from the {@link BrainMemory} in fixed registration order during
 * {@link Brain#tick()}, after sensors have sensed and behaviors have decided.
 *
 * <p>任务（p.2.24.1）：行为原子原语——在 {@link Brain#tick()} 中按固定注册序、于感知与
 * 行为决策之后，依 {@link BrainMemory} 确定性执行。
 *
 * <p>Model reference only (clean-room, zero code included): the "task" layering
 * idea of the vanilla {@code net.minecraft.world.entity.ai.Brain} and
 * SmartBrainLib (MPL-2.0); this interface is original deterministic Subterra
 * code. Pure JDK, no Minecraft code touched.
 * <p>模型参考（clean-room，零代码包含）：原版 {@code net.minecraft.world.entity.ai.Brain}
 * 与 SmartBrainLib（MPL-2.0）的「任务」分层思想；本接口为 Subterra 原创确定性代码。
 * 纯 JDK，不触碰任何 MC 代码。
 */
public interface BrainTask {

    /** The unique registration name of this task. 本任务的唯一注册名。 */
    String name();

    /**
     * Executes this task's primitive from {@code memory}, deterministically.
     * 依 {@code memory} 确定性执行本任务原语。
     */
    void run(BrainMemory memory);
}
