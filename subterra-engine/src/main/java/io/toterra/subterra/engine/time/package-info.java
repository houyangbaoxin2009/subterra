/**
 * Time-scaling core (p.2.26.1): the pure-JDK, deterministic data model and scheduling
 * primitives for scaled world time-flow, self-developed as a clean-room replacement for
 * the third-party {@code TimeScaleLib} (evaluated and excluded: its PolyForm Shield
 * Noncommercial license variant is non-portable and unsuitable; as a clean-room effort no
 * code from {@code TimeScaleLib} is referenced or copied — only the broad idea of "scaled
 * time-flow via a velocity ratio" is followed, implemented entirely from scratch).
 * <p>
 * The four fixed {@link io.toterra.subterra.engine.time.TimeDomain}s ({@code global /
 * entity / zone / player}) carry a {@link io.toterra.subterra.engine.time.FlowRate} ratio
 * relative to vanilla speed; {@link io.toterra.subterra.engine.time.TimeScale} aggregates
 * them with a deterministic two-tier resolve chain
 * ({@code GLOBAL -> ENTITY / ZONE / PLAYER}, cf. {@code TimeScale.effective});
 * {@link io.toterra.subterra.engine.time.TimeThrottle} provides deterministic logic-level
 * per-tick gating (skip/down-sample by {@code ceil(1/rate)}); the perception level
 * {@link io.toterra.subterra.engine.time.TimeInterpolation} yields fixed arithmetic
 * interpolation for smooth rendering. {@link io.toterra.subterra.engine.time.TimeScaleDoc}
 * serializes the two-tier document in the framework's {@code td} data language. Budget
 * semantics are aligned with (not dependent on) {@code engine.sim.budget} (p.2.8) and the
 * budget-skip idea of {@code BrainScheduler} (p.2.24). All classes are pure JDK, immutable
 * or fixed-order, with no randomness and no timing — identical inputs yield identical
 * double bit patterns / byte streams; no Minecraft code is touched.
 *
 * <p>时间缩放核心（p.2.26.1）：缩放世界时间流速的纯 JDK 确定性数据模型与调度原语，作为第三方
 * {@code TimeScaleLib} 的 clean-room 自研替代（已评估剔除：其 PolyForm Shield Noncommercial 许可变体
 * 不可移植、不适用；作为 clean-room 产出，不引用、不复制 {@code TimeScaleLib} 的任何代码——仅沿用
 * 「以流速比例缩放时间」这一宽泛思想，实现完全从零自研）。
 * <p>四个固定 {@link io.toterra.subterra.engine.time.TimeDomain}（{@code global / entity / zone /
 * player}）承载相对原速的 {@link io.toterra.subterra.engine.time.FlowRate} 比例；
 * {@link io.toterra.subterra.engine.time.TimeScale} 以确定性双层解析链
 * （{@code GLOBAL -> ENTITY / ZONE / PLAYER}，见 {@code effective}）聚合之；
 * {@link io.toterra.subterra.engine.time.TimeThrottle} 提供确定性逻辑级每 tick 门控（按
 * {@code ceil(1/rate)} 跳过/降频）；感知级 {@link io.toterra.subterra.engine.time.TimeInterpolation}
 * 为平滑渲染提供固定算术插值。{@link io.toterra.subterra.engine.time.TimeScaleDoc} 以框架 {@code td}
 * 数据语言序列化双层文档。预算语义与 {@code engine.sim.budget}（p.2.8）及 {@code BrainScheduler}
 * （p.2.24）的预算跳过思想<em>对齐</em>而非依赖。全部类纯 JDK、不可变或固定序、无随机无时序——同输入
 * 恒得同 double 位 / 字节流；不触碰任何 MC 代码。
 */
package io.toterra.subterra.engine.time;