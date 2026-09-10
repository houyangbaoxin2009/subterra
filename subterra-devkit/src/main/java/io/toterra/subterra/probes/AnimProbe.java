// p.2.21.3: deterministic animation probe — anchors the p.2.21.1 engine.anim core
// (AnimationData / KeyframeTrack / AnimSampler / AnimStateMachine) and the p.2.21.2
// AnimRuntime shell to fixed-order / bit-exact / hardcoded-expectation assertions.
// Pure JVM — no MC runtime, no timestamps / random / timing; exit 0 = PASS, 1 = FAIL.
// NOT shipped in the mod jar.
package io.toterra.subterra.probes;

import io.toterra.subterra.engine.anim.AnimationData;
import io.toterra.subterra.engine.anim.AnimChannel;
import io.toterra.subterra.engine.anim.AnimPose;
import io.toterra.subterra.engine.anim.AnimSampler;
import io.toterra.subterra.engine.anim.AnimStateMachine;
import io.toterra.subterra.engine.anim.AnimVec3;
import io.toterra.subterra.engine.anim.Keyframe;
import io.toterra.subterra.engine.anim.KeyframeTrack;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * p.2.21.3 — 动画确定性/接线探针：把 p.2.21.1 的 engine.anim（{@link AnimationData} /
 * {@link KeyframeTrack} / {@link AnimSampler} / {@link AnimStateMachine}）与 p.2.21.2 的
 * {@code AnimRuntime} 壳的确定性契约锚定为纯 JVM 断言。纯 JVM——不碰 MC、无时序、无随机；
 * exit 0 = PASS，exit 1 = FAIL；不进 mod jar。五节：
 * <ol>
 *   <li><b>动画数据模型确定性</b>：{@link AnimationData} 轨道按通道固定序（乱序构造 → 固定
 *       [POSITION,ROTATION,SCALE]；重复通道以 IAE 拒绝）；{@link KeyframeTrack} 关键帧按时间
 *       升序固定序（稳定排序——等时保持输入序）；name/lengthTicks 与 track(channel) 构造/字段
 *       往返；非法输入（NaN 关键帧时间 / 非有限分量 / null 名 / 非正时长 → IAE；null 值 → NPE）。</li>
 *   <li><b>采样确定性</b>：{@link AnimSampler} 对固定关键帧序列在多个 t（关键帧点、关键帧间、
 *       越界 clamp、NaN、非精确点）采样，同输入同 double 位（{@code Double.doubleToLongBits}）——
 *       连跑两遍一致 + 重建同结果；线性插值与最短弧插值期望值抽查（数值硬编码期望，二进制精确
 *       字面量逐位比对）；空/null 轨道 → ZERO。</li>
 *   <li><b>状态机确定性</b>：{@link AnimStateMachine} 固定注册序（两次构建 stateNames 一致、
 *       首注册为当前）、重复状态名拒绝、未知过渡目标 / 负 dt / NaN dt 拒绝、过渡时长内固定步长
 *       tick 序列 → {@link AnimPose} 序列与硬编码期望逐位一致、tick 按时长取模回绕、从头重建
 *       复验。</li>
 *   <li><b>接线契约（静态盘点）</b>：runtime.anim 的 {@code AnimRuntime} 类存在（只加载不初始化，
 *       纯 JVM 不触发 MC 的 {@code LogUtils} 初始化）+ 类字节含门控属性串 {@code subterra.probe.anim}
 *       + marker 前缀 {@code [Subterra anim]} + MARKER 常量名；AsyncE2EProbe 类字节含
 *       {@code ok (states=} 断言字面量——真实生命周期由 AsyncE2EProbe animOk 槽覆盖。</li>
 *   <li><b>许可盘点</b>：classpath 资源 {@code META-INF/third-party/geckolib-4.8/LICENSE} 含
 *       {@code MIT License} 与 {@code GeckoLib}；{@code NOTICE.md} 存在。</li>
 * </ol>
 * 确定性纪律：固定序、无时序、无随机；全部线性遍历（禁 O(n²)）；失败计数只在失败路径自增；
 * 全过输出 {@code [AnimProbe] PASS (n checks)} exit 0，否则 FAIL exit 1。
 *
 * <p>p.2.21.3 — deterministic animation probe: anchors the p.2.21.1 engine.anim core
 * ({@link AnimationData} / {@link KeyframeTrack} / {@link AnimSampler} / {@link AnimStateMachine})
 * and the p.2.21.2 {@code AnimRuntime} shell to pure-JVM assertions. Pure JVM — no MC runtime, no
 * timing, no randomness; exit 0 = PASS, exit 1 = FAIL; never shipped in the mod jar. Five sections:
 * <ol>
 *   <li><b>Animation-data-model determinism</b>: {@link AnimationData} tracks land in fixed channel
 *       order (out-of-order construction → fixed [POSITION,ROTATION,SCALE]; a duplicate channel is
 *       IAE-rejected); {@link KeyframeTrack} keyframes land in fixed ascending-time order (stable
 *       sort — equal times keep input order); name/lengthTicks and track(channel) constructor/field
 *       round-trip; illegal inputs (NaN keyframe time / non-finite component / null name /
 *       non-positive length → IAE; a null value → NPE).</li>
 *   <li><b>Sampling determinism</b>: {@link AnimSampler} over a fixed keyframe sequence at multiple
 *       t (keyframe points, between keyframes, out-of-range clamp, NaN, non-exact points) yields
 *       identical double bits ({@code Double.doubleToLongBits}) — twice-run identity plus
 *       rebuild-identity; linear and shortest-arc interpolation expectations are spot-checked with
 *       hardcoded numerically-exact literals, compared bit-for-bit; empty/null tracks → ZERO.</li>
 *   <li><b>State-machine determinism</b>: {@link AnimStateMachine} fixed registration order (two
 *       builds agree on stateNames, first-registered is current), duplicate-name rejection, unknown
 *       transition / negative dt / NaN dt rejection, a fixed-step tick sequence inside the
 *       transition duration → an {@link AnimPose} sequence bit-equal to hardcoded expectations,
 *       time wrap by duration (loop semantics), and a from-scratch rebuild re-verification.</li>
 *   <li><b>Wiring contract (static inventory)</b>: the runtime.anim {@code AnimRuntime} class is
 *       present (load-only, never initialized — the pure JVM must not trigger the MC
 *       {@code LogUtils} init), its class bytes carry the gate string {@code subterra.probe.anim},
 *       the marker prefix {@code [Subterra anim]} and the MARKER constant name; the
 *       AsyncE2EProbe class bytes carry the {@code ok (states=} assertion literal — the real boot
 *       lifecycle is covered by the AsyncE2EProbe animOk slot.</li>
 *   <li><b>License inventory</b>: the classpath resource
 *       {@code META-INF/third-party/geckolib-4.8/LICENSE} carries {@code MIT License} and
 *       {@code GeckoLib}; {@code NOTICE.md} is present.</li>
 * </ol>
 * Determinism discipline: fixed order, no timing, no randomness; all traversals linear (no O(n²));
 * failures are counted only on failing paths; PASS only when all checks pass, then exit 0, else
 * FAIL with counts and exit 1.
 */
public final class AnimProbe {

    private AnimProbe() {
    }

    private static int checks = 0;
    private static int failures = 0;

    private static void check(String name, boolean ok) {
        checks++;
        if (ok) {
            System.out.println("[PASS] " + name);
        } else {
            failures++;
            System.out.println("[FAIL] " + name);
        }
    }

    public static void main(String[] args) {
        try {
            dataModelDeterminism();
            samplerDeterminism();
            stateMachineDeterminism();
            wiringContract();
            licenseInventory();
        } catch (Exception e) {
            failures++;
            System.out.println("[FAIL] probe exception: " + e);
            e.printStackTrace(System.out);
        }
        if (failures == 0) {
            System.out.println("[AnimProbe] PASS (" + checks + " checks)");
            System.exit(0);
        } else {
            System.out.println("[AnimProbe] FAIL (" + failures + " of " + checks + " checks)");
            System.exit(1);
        }
    }

    // ---------- fixtures (fixed, the deterministic single source of truth) ----------

    /** 固定关键帧序列示例动画 idle（8 tick，三通道 0/4/8 关键帧，位移 +X 摆动、绕 Z 最短弧摇摆、
     *  缩放脉冲）。Fixed-keyframe sample animation idle (8 ticks, all channels keyed at 0/4/8,
     *  +X sway / shortest-arc Z swing / scale pulse). */
    private static AnimationData idleAnimation() {
        return new AnimationData("sample.idle", 8.0, List.of(
                new KeyframeTrack(AnimChannel.POSITION, List.of(
                        kf(0.0, 0.0, 0.0, 0.0), kf(4.0, 2.0, 4.0, 6.0), kf(8.0, 0.0, 0.0, 0.0))),
                new KeyframeTrack(AnimChannel.ROTATION, List.of(
                        kf(0.0, 0.0, 0.0, 0.0), kf(4.0, 0.0, 0.0, 350.0), kf(8.0, 0.0, 0.0, 0.0))),
                new KeyframeTrack(AnimChannel.SCALE, List.of(
                        kf(0.0, 1.0, 1.0, 1.0), kf(4.0, 1.5, 1.5, 1.5), kf(8.0, 1.0, 1.0, 1.0)))));
    }

    /** 固定关键帧序列示例动画 walk（4 tick，三通道 0/2/4 关键帧，沿 +Z 位移、绕 Y 摆幅、缩放
     *  收缩——关键帧时间/值全为二进制精确字面量，使硬编码期望逐位可比。Fixed-keyframe sample
     *  animation walk (4 ticks, all channels keyed at 0/2/4, +Z advance / Y-swing / scale shrink —
     *  every keyframe time/value is a binary-exact literal, so hardcoded expectations compare
     *  bit-for-bit). */
    private static AnimationData walkAnimation() {
        return new AnimationData("sample.walk", 4.0, List.of(
                new KeyframeTrack(AnimChannel.POSITION, List.of(
                        kf(0.0, 0.0, 0.0, 0.0), kf(2.0, 0.0, 0.0, 4.0), kf(4.0, 0.0, 0.0, 0.0))),
                new KeyframeTrack(AnimChannel.ROTATION, List.of(
                        kf(0.0, 0.0, 0.0, 0.0), kf(2.0, 0.0, 10.0, 0.0), kf(4.0, 0.0, 0.0, 0.0))),
                new KeyframeTrack(AnimChannel.SCALE, List.of(
                        kf(0.0, 1.0, 1.0, 1.0), kf(2.0, 0.5, 0.5, 0.5), kf(4.0, 1.0, 1.0, 1.0)))));
    }

    /** 构造关键帧。Builds a keyframe. */
    private static Keyframe kf(double t, double x, double y, double z) {
        return new Keyframe(t, AnimVec3.of(x, y, z));
    }

    /** 构造轨道。Builds a track. */
    private static KeyframeTrack track(AnimChannel channel, Keyframe... keyframes) {
        return new KeyframeTrack(channel, List.of(keyframes));
    }

    /** 逐位一致：向量与三个硬编码分量。Bit-equality: vector vs three hardcoded components. */
    private static boolean bitsEqual(AnimVec3 v, double x, double y, double z) {
        return Double.doubleToLongBits(v.x()) == Double.doubleToLongBits(x)
                && Double.doubleToLongBits(v.y()) == Double.doubleToLongBits(y)
                && Double.doubleToLongBits(v.z()) == Double.doubleToLongBits(z);
    }

    /** 逐位一致：两个向量。Bit-equality: two vectors. */
    private static boolean bitsEqual(AnimVec3 a, AnimVec3 b) {
        return bitsEqual(a, b.x(), b.y(), b.z());
    }

    /** 逐位一致：pose 与三个硬编码向量。Bit-equality: a pose vs three hardcoded vectors. */
    private static boolean poseBitsEqual(AnimPose p, AnimVec3 pos, AnimVec3 rot, AnimVec3 scale) {
        return bitsEqual(p.position(), pos)
                && bitsEqual(p.rotation(), rot)
                && bitsEqual(p.scale(), scale);
    }

    /** 逐位一致：两个 pose。Bit-equality: two poses. */
    private static boolean poseBitsEqual(AnimPose a, AnimPose b) {
        return poseBitsEqual(a, b.position(), b.rotation(), b.scale());
    }

    /** 动作抛出 IllegalArgumentException 为真。True iff the action throws IAE. */
    private static boolean throwsIAE(Runnable r) {
        try {
            r.run();
            return false;
        } catch (IllegalArgumentException e) {
            return true;
        }
    }

    /** 动作抛出 NullPointerException 为真（Keyframe 的 null value 由 requireNonNull 拒绝）。
     *  True iff the action throws NPE (a null Keyframe value is rejected by requireNonNull). */
    private static boolean throwsNPE(Runnable r) {
        try {
            r.run();
            return false;
        } catch (NullPointerException e) {
            return true;
        }
    }

    // ---------- (1) animation-data-model determinism ----------

    private static void dataModelDeterminism() {
        // 乱序构造（SCALE, POSITION, ROTATION）→ 固定序 [POSITION, ROTATION, SCALE]。
        AnimationData d = new AnimationData("sample.idle", 8.0, List.of(
                track(AnimChannel.SCALE, kf(0.0, 1.0, 1.0, 1.0), kf(4.0, 1.5, 1.5, 1.5), kf(8.0, 1.0, 1.0, 1.0)),
                track(AnimChannel.POSITION, kf(0.0, 0.0, 0.0, 0.0), kf(4.0, 2.0, 4.0, 6.0), kf(8.0, 0.0, 0.0, 0.0)),
                track(AnimChannel.ROTATION, kf(0.0, 0.0, 0.0, 0.0), kf(4.0, 0.0, 0.0, 350.0), kf(8.0, 0.0, 0.0, 0.0))));
        check("AnimationData: 乱序轨道构造 → 固定通道序 [POSITION,ROTATION,SCALE]",
                d.tracks().size() == 3
                        && d.tracks().get(0).channel() == AnimChannel.POSITION
                        && d.tracks().get(1).channel() == AnimChannel.ROTATION
                        && d.tracks().get(2).channel() == AnimChannel.SCALE);

        check("AnimationData: 重复通道轨道 → IllegalArgumentException",
                throwsIAE(() -> new AnimationData("dup", 8.0, List.of(
                        track(AnimChannel.POSITION, kf(0.0, 0.0, 0.0, 0.0), kf(4.0, 2.0, 4.0, 6.0), kf(8.0, 0.0, 0.0, 0.0)),
                        track(AnimChannel.POSITION, kf(0.0, 1.0, 1.0, 1.0), kf(4.0, 1.0, 1.0, 1.0), kf(8.0, 1.0, 1.0, 1.0))))));

        check("AnimationData: name/lengthTicks 构造/字段往返",
                "sample.idle".equals(d.name()) && d.lengthTicks() == 8.0);

        check("AnimationData: track(channel) 命中对应通道，缺失/null 通道 → null",
                d.track(AnimChannel.POSITION) != null
                        && d.track(AnimChannel.POSITION).channel() == AnimChannel.POSITION
                        && d.track(AnimChannel.SCALE) != null
                        && d.track(AnimChannel.SCALE).channel() == AnimChannel.SCALE
                        && d.track(null) == null);

        // 乱序关键帧（4, 0, 8, 2）→ 时间升序固定序。
        KeyframeTrack kt = track(AnimChannel.POSITION,
                kf(4.0, 2.0, 4.0, 6.0), kf(0.0, 0.0, 0.0, 0.0), kf(8.0, 0.0, 0.0, 0.0), kf(2.0, 1.0, 2.0, 3.0));
        check("KeyframeTrack: 乱序关键帧构造 → 时间升序固定序 [0,2,4,8]",
                kt.keyframes().size() == 4
                        && kt.keyframes().get(0).time() == 0.0
                        && kt.keyframes().get(1).time() == 2.0
                        && kt.keyframes().get(2).time() == 4.0
                        && kt.keyframes().get(3).time() == 8.0);

        Keyframe early = new Keyframe(2.0, AnimVec3.of(1.0, 0.0, 0.0));
        Keyframe late = new Keyframe(2.0, AnimVec3.of(9.0, 0.0, 0.0));
        KeyframeTrack stable = new KeyframeTrack(AnimChannel.POSITION, List.of(late, early));
        check("KeyframeTrack: 等时关键帧稳定排序保持输入序",
                stable.keyframes().get(0) == late && stable.keyframes().get(1) == early);

        check("构造违约：NaN 关键帧时间 / 非有限分量 / null 名 / 非正时长 → IAE，null 值 → NPE",
                throwsIAE(() -> new Keyframe(Double.NaN, AnimVec3.ZERO))
                        && throwsNPE(() -> new Keyframe(0.0, null))
                        && throwsIAE(() -> AnimVec3.of(0.0, Double.POSITIVE_INFINITY, 0.0))
                        && throwsIAE(() -> new AnimationData(null, 8.0, List.of()))
                        && throwsIAE(() -> new AnimationData("x", 0.0, List.of())));
    }

    // ---------- (2) sampling determinism ----------

    private static void samplerDeterminism() {
        KeyframeTrack pos = track(AnimChannel.POSITION,
                kf(0.0, 0.0, 0.0, 0.0), kf(4.0, 2.0, 4.0, 6.0), kf(8.0, 0.0, 0.0, 0.0));
        KeyframeTrack rot = track(AnimChannel.ROTATION,
                kf(0.0, 0.0, 0.0, 0.0), kf(4.0, 0.0, 0.0, 350.0), kf(8.0, 0.0, 0.0, 0.0));
        KeyframeTrack scl = track(AnimChannel.SCALE,
                kf(0.0, 1.0, 1.0, 1.0), kf(4.0, 1.5, 1.5, 1.5), kf(8.0, 1.0, 1.0, 1.0));
        double[] ts = {0.0, 2.0, 4.0, 6.0, 8.0, -100.0, 100.0, 3.7, 1e9, Double.NaN};

        boolean twice = true;
        for (double t : ts) {
            if (!bitsEqual(AnimSampler.sample(pos, t), AnimSampler.sample(pos, t))
                    || !bitsEqual(AnimSampler.sample(rot, t), AnimSampler.sample(rot, t))
                    || !bitsEqual(AnimSampler.sample(scl, t), AnimSampler.sample(scl, t))) {
                twice = false;
                break;
            }
        }
        check("AnimSampler: 固定 t 序列（关键帧点/帧间/越界 clamp/NaN）连跑两遍逐位一致", twice);

        KeyframeTrack pos2 = track(AnimChannel.POSITION,
                kf(0.0, 0.0, 0.0, 0.0), kf(4.0, 2.0, 4.0, 6.0), kf(8.0, 0.0, 0.0, 0.0));
        boolean rebuild = true;
        for (double t : ts) {
            if (!bitsEqual(AnimSampler.sample(pos, t), AnimSampler.sample(pos2, t))) {
                rebuild = false;
                break;
            }
        }
        check("AnimSampler: 重建同关键帧轨道 → 同 t 序列逐位一致", rebuild);

        check("AnimSampler: 线性插值期望 pos@2 == (1,2,3) 位精确",
                bitsEqual(AnimSampler.sample(pos, 2.0), 1.0, 2.0, 3.0));

        check("AnimSampler: 关键帧点 pos@0 == (0,0,0)、pos@4 == (2,4,6) 位精确",
                bitsEqual(AnimSampler.sample(pos, 0.0), 0.0, 0.0, 0.0)
                        && bitsEqual(AnimSampler.sample(pos, 4.0), 2.0, 4.0, 6.0));

        check("AnimSampler: 越界 clamp t=-100 → 首帧、t=100 → 末帧 位精确",
                bitsEqual(AnimSampler.sample(pos, -100.0), 0.0, 0.0, 0.0)
                        && bitsEqual(AnimSampler.sample(pos, 100.0), 0.0, 0.0, 0.0));

        check("AnimSampler: t=NaN → 首帧值 位精确",
                bitsEqual(AnimSampler.sample(pos, Double.NaN), 0.0, 0.0, 0.0));

        check("AnimSampler: 最短弧期望 rot@2 == (0,0,-5)、rot@6 == (0,0,355) 位精确",
                bitsEqual(AnimSampler.sample(rot, 2.0), 0.0, 0.0, -5.0)
                        && bitsEqual(AnimSampler.sample(rot, 6.0), 0.0, 0.0, 355.0));

        check("AnimSampler: 空轨道 / null 轨道 → ZERO 位精确",
                bitsEqual(AnimSampler.sample(track(AnimChannel.POSITION), 1.0), 0.0, 0.0, 0.0)
                        && bitsEqual(AnimSampler.sample((KeyframeTrack) null, 1.0), 0.0, 0.0, 0.0)
                        && bitsEqual(AnimSampler.sample((AnimationData) null, AnimChannel.POSITION, 1.0), 0.0, 0.0, 0.0));
    }

    // ---------- (3) state-machine determinism ----------

    private static void stateMachineDeterminism() {
        AnimationData idle = idleAnimation();
        AnimationData walk = walkAnimation();

        AnimStateMachine m1 = new AnimStateMachine(2.0);
        m1.registerState("idle", idle).registerState("walk", walk);
        AnimStateMachine m2 = new AnimStateMachine(2.0);
        m2.registerState("idle", idle).registerState("walk", walk);
        check("AnimStateMachine: 固定注册序两次构建 stateNames 一致 [idle,walk]，首注册为当前",
                m1.stateNames().equals(m2.stateNames())
                        && m1.stateNames().equals(List.of("idle", "walk"))
                        && "idle".equals(m1.currentName()));

        check("AnimStateMachine: 重复状态名 → IllegalArgumentException",
                throwsIAE(() -> new AnimStateMachine(2.0)
                        .registerState("idle", idle).registerState("idle", walk)));

        check("AnimStateMachine: 未知过渡目标 / 负 dt / NaN dt → IllegalArgumentException",
                throwsIAE(() -> new AnimStateMachine(2.0)
                        .registerState("idle", idle).requestTransition("bogus"))
                        && throwsIAE(() -> new AnimStateMachine(2.0)
                        .registerState("idle", idle).tick(-1.0))
                        && throwsIAE(() -> new AnimStateMachine(2.0)
                        .registerState("idle", idle).tick(Double.NaN)));

        // 固定步长序列：idle 3.0 tick → 请求 walk 过渡（固定 2-tick）→ 两拍 1.0 tick 跨过渡 →
        // walk 当前位 t=2.0。每步采样与硬编码期望逐位一致（所有期望值二进制精确）。
        AnimStateMachine m = new AnimStateMachine(2.0);
        m.registerState("idle", idle).registerState("walk", walk);
        boolean drive = true;
        m.tick(3.0);
        drive &= poseBitsEqual(m.sample(),
                AnimVec3.of(1.5, 3.0, 4.5), AnimVec3.of(0.0, 0.0, -7.5), AnimVec3.of(1.375, 1.375, 1.375));
        m.requestTransition("walk");
        m.tick(1.0);
        drive &= poseBitsEqual(m.sample(),
                AnimVec3.of(1.0, 2.0, 4.0), AnimVec3.of(0.0, 2.5, 355.0), AnimVec3.of(1.125, 1.125, 1.125));
        m.tick(1.0);
        drive &= poseBitsEqual(m.sample(),
                AnimVec3.of(0.0, 0.0, 4.0), AnimVec3.of(0.0, 10.0, 0.0), AnimVec3.of(0.5, 0.5, 0.5));
        check("AnimStateMachine: 过渡时长内固定步长 tick → AnimPose 序列与硬编码期望逐位一致", drive);

        m.tick(2.0); // walk@2 + 2 = 4 % 4 = 0（回绕）
        boolean wrapOk = m.currentTime() == 0.0
                && poseBitsEqual(m.sample(),
                AnimVec3.of(0.0, 0.0, 0.0), AnimVec3.of(0.0, 0.0, 0.0), AnimVec3.of(1.0, 1.0, 1.0));
        m.tick(5.0); // 0 + 5 = 5 % 4 = 1（回绕）
        wrapOk &= m.currentTime() == 1.0
                && poseBitsEqual(m.sample(),
                AnimVec3.of(0.0, 0.0, 2.0), AnimVec3.of(0.0, 5.0, 0.0), AnimVec3.of(0.75, 0.75, 0.75));
        check("AnimStateMachine: tick 按时长取模回绕（4%4=0，再 +5 → 1）", wrapOk);

        AnimStateMachine r = new AnimStateMachine(2.0);
        r.registerState("idle", idle).registerState("walk", walk);
        r.tick(3.0);
        AnimPose r0 = r.sample();
        r.requestTransition("walk");
        r.tick(1.0);
        AnimPose r1 = r.sample();
        r.tick(1.0);
        AnimPose r2 = r.sample();
        check("AnimStateMachine: 从头重建同驱动 → 每步 sample 逐位一致",
                poseBitsEqual(r0,
                        AnimVec3.of(1.5, 3.0, 4.5), AnimVec3.of(0.0, 0.0, -7.5), AnimVec3.of(1.375, 1.375, 1.375))
                        && poseBitsEqual(r1,
                        AnimVec3.of(1.0, 2.0, 4.0), AnimVec3.of(0.0, 2.5, 355.0), AnimVec3.of(1.125, 1.125, 1.125))
                        && poseBitsEqual(r2,
                        AnimVec3.of(0.0, 0.0, 4.0), AnimVec3.of(0.0, 10.0, 0.0), AnimVec3.of(0.5, 0.5, 0.5)));
    }

    // ---------- (4) wiring contract (static inventory, load-only) ----------

    private static void wiringContract() {
        String animRuntime = "io.toterra.subterra.runtime.anim.AnimRuntime";
        check("wiring: runtime.anim.AnimRuntime 类存在（只加载不初始化，纯 JVM 不触发 MC LogUtils）",
                classExists(animRuntime));
        check("wiring: AnimRuntime 类字节含门控属性串 'subterra.probe.anim'",
                classBytesContain(animRuntime, "subterra.probe.anim"));
        check("wiring: AnimRuntime 类字节含 marker 前缀 '[Subterra anim]'",
                classBytesContain(animRuntime, "[Subterra anim]"));
        check("wiring: AnimRuntime 类字节含 MARKER 常量名",
                classBytesContain(animRuntime, "MARKER"));
        // 真实生命周期由 boot E2E 覆盖：AsyncE2EProbe animOk 槽断言字面量的静态存在性。
        check("wiring: AsyncE2EProbe 类字节含 animOk 断言 'ok (states='（生命周期由 boot E2E 覆盖）",
                classBytesContain("io.toterra.subterra.probes.AsyncE2EProbe", "[Subterra anim]")
                        && classBytesContain("io.toterra.subterra.probes.AsyncE2EProbe", "ok (states="));
    }

    // ---------- (5) third-party license inventory (classpath resources) ----------

    private static void licenseInventory() {
        String license = readResource("/META-INF/third-party/geckolib-4.8/LICENSE");
        check("license: geckolib-4.8 LICENSE classpath 资源存在", license != null);
        if (license != null) {
            check("license: geckolib-4.8 LICENSE 含 'MIT License'", license.contains("MIT License"));
            check("license: geckolib-4.8 LICENSE 含 'GeckoLib'", license.contains("GeckoLib"));
        }
        String notice = readResource("/META-INF/third-party/geckolib-4.8/NOTICE.md");
        check("license: geckolib-4.8 NOTICE.md classpath 资源存在", notice != null);
    }

    // ---------- helpers ----------

    /** 只加载不初始化地确认类存在。Loads without initializing — presence only. */
    private static boolean classExists(String fqcn) {
        try {
            Class.forName(fqcn, false, AnimProbe.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }

    /** 读类的 .class 资源字节（classpath 编译输出）；缺失返回 null。Reads the class-file resource
     * bytes from the classpath compiled outputs; null when absent. */
    private static byte[] classBytes(String fqcn) {
        String resource = "/" + fqcn.replace('.', '/') + ".class";
        try (InputStream in = AnimProbe.class.getResourceAsStream(resource)) {
            if (in == null) {
                return null;
            }
            return in.readAllBytes();
        } catch (IOException e) {
            return null;
        }
    }

    /** 断言类的 .class 字节包含某字符串字面量（常量池 UTF-8，纯 ASCII，ISO-8859-1 逐字节映射，
     *  线性 contains、无 O(n²)）。Asserts the class bytes contain a string literal (constant-pool
     *  UTF-8; the strings are pure ASCII so ISO-8859-1 is a byte-identity mapping — a linear
     *  contains scan, no O(n²)). */
    private static boolean classBytesContain(String fqcn, String literal) {
        byte[] bytes = classBytes(fqcn);
        if (bytes == null) {
            return false;
        }
        return new String(bytes, StandardCharsets.ISO_8859_1).contains(literal);
    }

    /** 读取 classpath 资源（缺失/异常 → null）。Reads a classpath resource (null on missing/error). */
    private static String readResource(String path) {
        try (InputStream in = AnimProbe.class.getResourceAsStream(path)) {
            if (in == null) {
                return null;
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }
}
