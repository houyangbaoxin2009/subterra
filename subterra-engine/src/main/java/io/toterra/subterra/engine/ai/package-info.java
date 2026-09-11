/**
 * AI behavior core (p.2.24): the clean-room brain orchestration core — typed
 * memory slots ({@link io.toterra.subterra.engine.ai.BrainMemory}), sensors
 * ({@link io.toterra.subterra.engine.ai.BrainSensor}), behaviors
 * ({@link io.toterra.subterra.engine.ai.BrainBehavior}), task primitives
 * ({@link io.toterra.subterra.engine.ai.BrainTask}) and the deterministic
 * orchestrator ({@link io.toterra.subterra.engine.ai.Brain}). The layered
 * memory/sensor/behavior/task model is referenced from the vanilla
 * {@code net.minecraft.world.entity.ai.Brain} and SmartBrainLib (MPL-2.0) —
 * model reference only, clean-room: zero third-party code is included, copied
 * or bundled; every implementation is original deterministic Subterra code.
 * All classes are pure JDK: fixed registration order, duplicate rejection, no
 * randomness, no timing — identical inputs yield identical outputs.
 *
 * <p>AI 行为核心（p.2.24）：clean-room Brain 编排核心——类型化记忆槽
 * （{@link io.toterra.subterra.engine.ai.BrainMemory}）、感知器
 * （{@link io.toterra.subterra.engine.ai.BrainSensor}）、行为
 * （{@link io.toterra.subterra.engine.ai.BrainBehavior}）、任务原语
 * （{@link io.toterra.subterra.engine.ai.BrainTask}）与确定性编排器
 * （{@link io.toterra.subterra.engine.ai.Brain}）。memory/sensor/behavior/task
 * 分层模型参考自原版 {@code net.minecraft.world.entity.ai.Brain} 与 SmartBrainLib
 * （MPL-2.0）——仅模型参考，clean-room：不包含、不复制、不捆绑任何第三方代码；所有实现
 * 均为 Subterra 原创确定性代码。全部类纯 JDK：固定注册序、重复拒、无随机、无时序——同输入
 * 恒得同输出。
 */
package io.toterra.subterra.engine.ai;
