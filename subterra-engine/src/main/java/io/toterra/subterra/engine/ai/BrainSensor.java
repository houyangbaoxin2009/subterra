package io.toterra.subterra.engine.ai;

/**
 * Sensor (p.2.24.1): perceives and writes observations into the
 * {@link BrainMemory} during {@link Brain#sense()} — sensors run in fixed
 * registration order, keeping the orchestration deterministic. Each sensor has
 * a unique name used as its registration key.
 *
 * <p>感知器（p.2.24.1）：在 {@link Brain#sense()} 期间感知并把观测写入
 * {@link BrainMemory}——感知器按固定注册序运行，使编排保持确定性。每个感知器有唯一名称，
 * 用作注册键。
 *
 * <p>Model reference only (clean-room, zero code included): the "sensor"
 * layering idea of the vanilla {@code net.minecraft.world.entity.ai.Brain} and
 * SmartBrainLib (MPL-2.0); this interface is original deterministic Subterra
 * code. Pure JDK, no Minecraft code touched.
 * <p>模型参考（clean-room，零代码包含）：原版 {@code net.minecraft.world.entity.ai.Brain}
 * 与 SmartBrainLib（MPL-2.0）的「感知器」分层思想；本接口为 Subterra 原创确定性代码。
 * 纯 JDK，不触碰任何 MC 代码。
 */
public interface BrainSensor {

    /** The unique registration name of this sensor. 本感知器的唯一注册名。 */
    String name();

    /**
     * Perceives and writes into {@code memory} (typically via
     * {@link BrainMemory#put}), deterministically.
     * 感知并把观测确定性写入 {@code memory}（通常经 {@link BrainMemory#put}）。
     */
    void sense(BrainMemory memory);
}
