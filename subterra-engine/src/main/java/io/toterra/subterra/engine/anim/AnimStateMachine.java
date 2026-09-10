package io.toterra.subterra.engine.anim;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Deterministic animation state machine (p.2.21.1): states are registered in fixed
 * insertion order (a duplicate name is rejected with
 * {@link IllegalArgumentException}), the first registered state is the initial
 * current state, transitions are requested by name with a fixed transition duration
 * and linear easing, {@link #tick(double)} advances deterministically (wrapping
 * animation time by duration — loop semantics), and {@link #sample()} emits a
 * deterministic {@link AnimPose} (blending current and target poses during a
 * transition). No randomness, no timing, fixed order.
 *
 * <p>确定性动画状态机（p.2.21.1）：状态按固定插入序注册（重复名以
 * {@link IllegalArgumentException} 拒绝），首个注册状态为初始当前状态，按名请求过渡，过渡
 * 时长与线性缓动固定，{@link #tick(double)} 确定性推进（动画时间按时长回绕——循环语义），
 * {@link #sample()} 输出确定性 {@link AnimPose}（过渡期间混合当前与目标 pose）。无随机、
 * 无时序、固定序。
 */
public final class AnimStateMachine {

    private final Map<String, AnimationData> states = new LinkedHashMap<>();
    private final double transitionTicks;

    private String currentName;
    private double currentTime;
    private String targetName;
    private double targetTime;
    private double transitionProgress;

    /**
     * Creates a state machine with the given fixed transition duration (ticks).
     * 以给定的固定过渡时长（tick）创建状态机。
     *
     * @param transitionTicks the transition duration in ticks (finite, &gt; 0).
     *                        过渡时长（tick，有限，&gt; 0）。
     */
    public AnimStateMachine(double transitionTicks) {
        if (!Double.isFinite(transitionTicks) || transitionTicks <= 0.0) {
            throw new IllegalArgumentException(
                    "transitionTicks must be finite and > 0: " + transitionTicks);
        }
        this.transitionTicks = transitionTicks;
    }

    /**
     * Creates a state machine with the default fixed transition duration of 1 tick.
     * 以默认固定过渡时长 1 tick 创建状态机。
     */
    public AnimStateMachine() {
        this(1.0);
    }

    /**
     * Registers a state in insertion order. A null/blank name, a null
     * {@link AnimationData}, or a duplicate name is rejected with
     * {@link IllegalArgumentException}. The first registered state becomes the
     * initial current state. Deterministic, no timing.
     *
     * 按插入序注册状态。名为 null/空白、数据为 null、或同名重复注册均以
     * {@link IllegalArgumentException} 拒绝。首个注册状态成为初始当前状态。确定性、无时序。
     *
     * @param name      the state name (registry key). 状态名（注册表键）。
     * @param animation the animation data bound to this state. 绑定到该状态的动画数据。
     * @return {@code this}, for chaining. {@code this}，便于链式调用。
     */
    public AnimStateMachine registerState(String name, AnimationData animation) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("state name must be non-null and non-blank");
        }
        Objects.requireNonNull(animation, "animation must be non-null");
        if (states.containsKey(name)) {
            throw new IllegalArgumentException("state already registered: " + name);
        }
        states.put(name, animation);
        if (currentName == null) {
            currentName = name;
        }
        return this;
    }

    /**
     * All registered state names as a copy in registration order (deterministic).
     * 全部已注册状态名，按注册序返回副本（确定性）。
     */
    public List<String> stateNames() {
        return List.copyOf(states.keySet());
    }

    /**
     * The current state name, or {@code null} before any state is registered.
     * 当前状态名；未注册任何状态时为 {@code null}。
     */
    public String currentName() {
        return currentName;
    }

    /**
     * The current animation time (ticks, already wrapped by duration), or {@code 0}
     * before any state is registered. 当前动画时间（tick，已按时长回绕）；未注册状态时为
     * {@code 0}。
     */
    public double currentTime() {
        return currentTime;
    }

    /**
     * Requests a transition to the named state. An unknown name is rejected with
     * {@link IllegalArgumentException}; requesting the current state is a no-op
     * (the running transition, if any, continues unchanged). A transition in flight
     * is overridden by a new request (target, target time and progress reset).
     * Deterministic.
     *
     * 请求切换到指定状态。未知名以 {@link IllegalArgumentException} 拒绝；请求当前状态为
     * no-op（进行中的过渡保持不变）。新的请求会覆盖进行中的过渡（目标、目标时间与进度重置）。
     * 确定性。
     *
     * @param targetName the target state name. 目标状态名。
     * @return {@code this}, for chaining. {@code this}，便于链式调用。
     */
    public AnimStateMachine requestTransition(String targetName) {
        if (targetName == null || !states.containsKey(targetName)) {
            throw new IllegalArgumentException("unknown target state: " + targetName);
        }
        if (targetName.equals(currentName)) {
            return this;
        }
        this.targetName = targetName;
        this.targetTime = 0.0;
        this.transitionProgress = 0.0;
        return this;
    }

    /**
     * Advances the state machine by {@code dt} ticks (deterministic): animation time
     * wraps by each state's duration (loop semantics), and during a transition both
     * sides advance and the transition progress increases linearly until it reaches
     * 1, at which point the target becomes current. A negative or {@code NaN} {@code dt}
     * is rejected with {@link IllegalArgumentException}; with no registered state this
     * is a no-op.
     *
     * 按 {@code dt} tick 推进状态机（确定性）：动画时间按各状态时长回绕（循环语义）；过渡期间
     * 两侧同时推进，过渡进度线性增长至 1 时目标成为当前状态。负值或 {@code NaN} 的 {@code dt}
     * 以 {@link IllegalArgumentException} 拒绝；未注册状态时为 no-op。
     *
     * @param dt the time delta in ticks. 时间增量（tick）。
     * @return {@code this}, for chaining. {@code this}，便于链式调用。
     */
    public AnimStateMachine tick(double dt) {
        if (Double.isNaN(dt) || dt < 0.0) {
            throw new IllegalArgumentException("dt must be non-negative and finite: " + dt);
        }
        if (currentName == null) {
            return this;
        }
        AnimationData current = states.get(currentName);
        currentTime = wrap(currentTime + dt, current.lengthTicks());
        if (targetName == null) {
            return this;
        }
        AnimationData target = states.get(targetName);
        targetTime = wrap(targetTime + dt, target.lengthTicks());
        transitionProgress += dt / transitionTicks;
        if (transitionProgress >= 1.0) {
            currentName = targetName;
            currentTime = targetTime;
            targetName = null;
            transitionProgress = 0.0;
        }
        return this;
    }

    /**
     * Whether a transition is currently in flight. 当前是否处于过渡中。
     */
    public boolean isTransitioning() {
        return targetName != null;
    }

    /**
     * The current transition progress in {@code [0, 1)}, or {@code 0} when not
     * transitioning. 当前过渡进度 {@code [0, 1)}；未过渡时为 {@code 0}。
     */
    public double transitionProgress() {
        return transitionProgress;
    }

    /**
     * Samples the current deterministic pose: the current state's animation at
     * {@link #currentTime()}; during a transition, the current and target poses are
     * blended with the transition progress using linear easing (ROTATION along the
     * shortest arc). With no registered state, {@link AnimPose#ZERO} is returned.
     *
     * 采样当前确定性 pose：当前状态动画在 {@link #currentTime()} 处的采样；过渡期间，当前与
     * 目标 pose 以过渡进度按线性缓动混合（ROTATION 沿最短弧）。未注册状态时返回
     * {@link AnimPose#ZERO}。
     */
    public AnimPose sample() {
        if (currentName == null) {
            return AnimPose.ZERO;
        }
        AnimationData current = states.get(currentName);
        AnimPose from = poseOf(current, currentTime);
        if (targetName == null) {
            return from;
        }
        AnimationData target = states.get(targetName);
        AnimPose to = poseOf(target, targetTime);
        double f = transitionProgress;
        return new AnimPose(
                mix(from.position(), to.position(), f, AnimChannel.POSITION),
                mix(from.rotation(), to.rotation(), f, AnimChannel.ROTATION),
                mix(from.scale(), to.scale(), f, AnimChannel.SCALE));
    }

    /** Samples all three channels of an animation into a pose. 采样动画三通道为 pose。 */
    private static AnimPose poseOf(AnimationData animation, double t) {
        return new AnimPose(
                AnimSampler.sample(animation, AnimChannel.POSITION, t),
                AnimSampler.sample(animation, AnimChannel.ROTATION, t),
                AnimSampler.sample(animation, AnimChannel.SCALE, t));
    }

    /** Blends two vectors per channel kind (ROTATION shortest-arc, else linear).
     *  按通道类型混合两个向量（ROTATION 最短弧，其余线性）。 */
    private static AnimVec3 mix(AnimVec3 a, AnimVec3 b, double f, AnimChannel channel) {
        return channel == AnimChannel.ROTATION ? a.lerpRotation(b, f) : a.lerp(b, f);
    }

    /** Wraps a non-negative time into {@code [0, length)} — deterministic modular
     *  arithmetic. 将非负时间回绕到 {@code [0, length)}——确定性模运算。 */
    private static double wrap(double t, double length) {
        return t % length;
    }
}
