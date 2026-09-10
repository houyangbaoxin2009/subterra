/**
 * Animation core (p.2.21.1, GeckoLib 4 port sentinel): the pure-JDK deterministic 3D
 * keyframe-animation engine, modeled on the GeckoLib 4 core concepts fetched from the
 * upstream {@code 1.21.1} branch of {@code github.com/bernie-g/geckolib}
 * (Maven {@code software.bernie.geckolib}, MIT — license materials under
 * {@code META-INF/third-party/geckolib-4.8}). The model mirrors GeckoLib's data
 * shape — an {@link io.toterra.subterra.engine.anim.AnimationData} is a named
 * duration-tick animation with per-channel keyframe tracks
 * ({@link io.toterra.subterra.engine.anim.AnimChannel#POSITION} /
 * {@link io.toterra.subterra.engine.anim.AnimChannel#ROTATION} /
 * {@link io.toterra.subterra.engine.anim.AnimChannel#SCALE}, rotation as Euler angles
 * in degrees), {@link io.toterra.subterra.engine.anim.AnimSampler} samples by
 * absolute time with fixed linear / shortest-arc interpolation and fixed clamp
 * semantics, and {@link io.toterra.subterra.engine.anim.AnimStateMachine} provides
 * fixed-order state registration (duplicate rejection), deterministic transitions
 * and deterministic tick/sample. All classes are pure JDK, immutable or
 * fixed-order, with no randomness and no timing — identical inputs yield identical
 * double bit patterns; no Minecraft code is touched. See
 * {@code META-INF/third-party/geckolib-4.8/NOTICE.md} for the adaptation scope
 * (clean-room simplifications: absolute-time sampling vs. upstream tick-by-tick
 * keyframe consumption, GeckoLib's easing sets / bone hierarchies / event keyframes
 * not ported).
 *
 * <p>动画核心（p.2.21.1，GeckoLib 4 移植前哨）：纯 JDK 确定性 3D 关键帧动画引擎，以从上游
 * {@code github.com/bernie-g/geckolib} 的 {@code 1.21.1} 分支抓取的 GeckoLib 4 核心概念为
 * 形态参考（Maven {@code software.bernie.geckolib}，MIT——许可材料见
 * {@code META-INF/third-party/geckolib-4.8}）。模型呼应 GeckoLib 的数据形态——
 * {@link io.toterra.subterra.engine.anim.AnimationData} 为带时长（tick）的命名动画，含逐
 * 通道关键帧轨道（{@link io.toterra.subterra.engine.anim.AnimChannel#POSITION} /
 * {@link io.toterra.subterra.engine.anim.AnimChannel#ROTATION} /
 * {@link io.toterra.subterra.engine.anim.AnimChannel#SCALE}，rotation 为欧拉角（度）），
 * {@link io.toterra.subterra.engine.anim.AnimSampler} 按绝对时间采样，固定线性 / 最短弧插值
 * 与固定 clamp 语义，{@link io.toterra.subterra.engine.anim.AnimStateMachine} 提供固定序状态
 * 注册（重复拒绝）、确定性过渡与确定性 tick/sample。全部类纯 JDK、不可变或固定序、无随机无
 * 时序——同输入恒得同 double 位模式；不触碰任何 MC 代码。适配范围（clean-room 简化：绝对时间
 * 采样 vs 上游逐 tick 关键帧消费，GeckoLib 的 easing 集合 / 骨骼层级 / 事件关键帧未移植）见
 * {@code META-INF/third-party/geckolib-4.8/NOTICE.md}。
 */
package io.toterra.subterra.engine.anim;
