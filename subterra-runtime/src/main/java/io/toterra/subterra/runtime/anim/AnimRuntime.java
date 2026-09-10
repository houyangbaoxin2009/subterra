package io.toterra.subterra.runtime.anim;

import com.mojang.logging.LogUtils;
import io.toterra.subterra.engine.anim.AnimationData;
import io.toterra.subterra.engine.anim.AnimChannel;
import io.toterra.subterra.engine.anim.AnimPose;
import io.toterra.subterra.engine.anim.AnimSampler;
import io.toterra.subterra.engine.anim.AnimStateMachine;
import io.toterra.subterra.engine.anim.AnimVec3;
import io.toterra.subterra.engine.anim.Keyframe;
import io.toterra.subterra.engine.anim.KeyframeTrack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import org.slf4j.Logger;

import java.util.List;
import java.util.Locale;

/**
 * p.2.21.2 — anim runtime 壳：把 p.2.21.1 的 engine.anim（GeckoLib 4 纯 JDK 关键帧动画核心：
 * {@link AnimationData} / {@link KeyframeTrack} / {@link AnimSampler} / {@link AnimStateMachine}）
 * 收编进 boot 生命周期的确定性核对。{@link #bootstrap()}（{@code Subterra.java} 构造调用）注册
 * {@code ServerStartedEvent} 门控；门控 {@code subterra.probe.anim}（经 gradle -P → runServer
 * system property 转发，与其余探针壳同模式）非 null 才跑——缺省纯 no-op 壳，对启动生命周期零影响。
 * <p>
 * 确定性样例驱动（固定序、禁时序、禁 sleep）：构造两段固定关键帧序列示例动画（idle/walk，三通道
 * POSITION/ROTATION/SCALE 的关键帧时间与值全固定）→ {@link AnimStateMachine} 固定序注册 → 固定
 * 步长 tick 推进（3.0 tick 空闲位 → 请求 walk 过渡 → 各 1.0 tick 两拍跨过固定 2-tick 过渡 →
 * walk 当前位 t=2.0）→ {@link #sample()} 采样最终确定性 {@link AnimPose}，并对「同一驱动从头
 * 重建」做摘要复验（确定性证明）。全部通过打
 * {@code [Subterra anim] ok (states=N, sample=<AnimPose 摘要>, verify=ok)}
 * （摘要 = 三通道逐分量 {@code %.4f} 固定 Locale.ROOT 文本：{@code pos:.. rot:.. scale:..}）；
 * 违约/程序错误（样例合法，正常不可达）打 {@code anim mismatch (error=...)} marker，绝不打假 ok。
 * <p>
 * <b>MC 动画生命周期接线面（p.2.21 后接线点，本子项只做门控确定性核对）</b>：GeckoLib 的 MC
 * 接线通常在实体渲染层（{@code AnimatedGeoRenderer} 之类）完成；engine.anim 是纯 JDK 确定性
 * 核心，真实动画渲染接管留后续里程碑（避免范围膨胀）。三面接线面文档化如下（沿用 p.2.27.2
 * RenderHooks/RenderHooksClient 的「server 侧只读核对 + 客户端事件注入」范式，均以本壳确定性
 * 核对为前置）：
 * <ol>
 *   <li><b>数据装载面（data load）</b>：把 MC 侧动画资源（Geo/Anim JSON、实体
 *       {@code Animatable} 定义等）装载/转换为 engine.anim 的 {@link AnimationData}（固定通道序、
 *       重复拒）——后续接线点为资源装载器 → 状态机注册表，本子项以固定关键帧样例代偿；</li>
 *   <li><b>驱动更新面（drive update）</b>：逐实体 tick 循环推进 {@link AnimStateMachine}
 *       （GeckoLib 经逐实体 {@code AnimatableManager} 从 {@code tick()} 驱动）——后续接线点为
 *       实体 tick → {@code tick(dt)}，本子项以固定步长序列代偿；</li>
 *   <li><b>渲染消费面（render consume）</b>：渲染器消费采样所得 {@link AnimPose} 驱动骨骼变换
 *       （GeckoLib 的 {@code GeoModel} / {@code AnimatedGeoRenderer}）——后续接线点为渲染器按
 *       pose 更新骨骼，真实动画渲染接管不在本子项。</li>
 * </ol>
 * 门控同 {@code subterra.probe.anim}（默认 no-op）：服务端门控内打 {@code ok (states=.., sample=..,
 * verify=ok)}（供 E2E 断言）。接续表另见仓库 runtime 接线文档；本子项交付门控 marker 与
 * engine.anim 状态机驱动在真实 boot 生命周期上的确定性证明。
 * <p>
 * p.2.21.2 — the anim runtime shell: folds the p.2.21.1 engine.anim (the GeckoLib 4 pure-JDK
 * keyframe-animation core: {@link AnimationData} / {@link KeyframeTrack} / {@link AnimSampler} /
 * {@link AnimStateMachine}) into the boot lifecycle as a deterministic verification.
 * {@link #bootstrap()} (called from the {@code Subterra.java} constructor) registers the
 * {@code ServerStartedEvent} gate; gated by {@code subterra.probe.anim} (forwarded gradle -P →
 * runServer system property, same pattern as the other probe shells), runs only when non-null — a
 * pure no-op shell by default, zero impact on the boot lifecycle.
 * <p>
 * Deterministic sample drive (fixed order, no timing, no sleeps): builds two fixed-keyframe sample
 * animations (idle/walk, all three channels POSITION/ROTATION/SCALE with fully fixed keyframe times
 * and values) → fixed-order {@link AnimStateMachine} registration → fixed-step tick advance (3.0
 * ticks in idle → request the walk transition → two 1.0-tick beats spanning the fixed 2-tick
 * transition → walk current at t=2.0) → {@link #sample()} yields the final deterministic
 * {@link AnimPose}, and the digest is re-verified against an identically re-driven machine from
 * scratch (the determinism proof). On full success it prints
 * {@code [Subterra anim] ok (states=N, sample=<AnimPose digest>, verify=ok)} (digest = per-channel
 * per-component {@code %.4f} fixed-Locale.ROOT text: {@code pos:.. rot:.. scale:..}); a load
 * violation / program error (the samples are legal, so normally unreachable) prints an
 * {@code anim mismatch (error=...)} marker instead — a false ok is never emitted.
 * <p>
 * <b>MC animation-lifecycle wiring surfaces (post-p.2.21 wiring points; this sub-item only does the
 * gated deterministic verification)</b>: GeckoLib's MC wiring usually lives at the entity renderer
 * layer ({@code AnimatedGeoRenderer} and friends); engine.anim is the pure-JDK deterministic core,
 * and the real animation-render takeover lands with a later milestone (scope containment). The three
 * wiring surfaces are documented here (following the p.2.27.2 RenderHooks/RenderHooksClient
 * "server-side read-only check + client-event injection" pattern, each building on this shell's
 * deterministic check):
 * <ol>
 *   <li><b>Data-load surface</b>: loads/converts MC-side animation resources (Geo/Anim JSON, entity
 *       {@code Animatable} definitions, ...) into engine.anim {@link AnimationData} (fixed channel
 *       order, duplicate rejection) — the future wiring point is an asset loader → state-machine
 *       registry; this sub-item substitutes a fixed-keyframe sample;</li>
 *   <li><b>Drive-update surface</b>: the per-entity tick loop advancing {@link AnimStateMachine}
 *       (GeckoLib drives its per-entity {@code AnimatableManager} from {@code tick()}) — the future
 *       wiring point is entity tick → {@code tick(dt)}; this sub-item substitutes a fixed-step
 *       sequence;</li>
 *   <li><b>Render-consume surface</b>: the renderer consumes the sampled {@link AnimPose} to drive
 *       bone transforms (GeckoLib's {@code GeoModel} / {@code AnimatedGeoRenderer}) — the future
 *       wiring point is per-bone pose application; real animation-render takeover is out of scope
 *       here.</li>
 * </ol>
 * Same gate {@code subterra.probe.anim} (default no-op): the server-side gate prints
 * {@code ok (states=.., sample=.., verify=ok)} (E2E-asserted). The wiring table also lives in the
 * repository runtime wiring docs; this sub-item delivers the gated marker and the determinism proof
 * of the engine.anim state-machine drive on a real boot lifecycle.
 */
public final class AnimRuntime {

    /** 探针 marker 前缀 / probe marker prefix. */
    public static final String MARKER = "[Subterra anim]";

    public static final Logger LOGGER = LogUtils.getLogger();

    private AnimRuntime() {
    }

    /** 注册 NeoForge 生命周期监听（mod 构造调用）。Registers the NeoForge lifecycle listeners (call
     * from the mod constructor). */
    public static void bootstrap() {
        NeoForge.EVENT_BUS.register(AnimRuntime.class);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onServerStarted(ServerStartedEvent event) {
        // p.2.21.2 deterministic E2E hook: run the engine.anim sample animation state-machine
        // drive at startup when a probe flag is forwarded (subterra.probe.anim) — mirrors the
        // other probe-shell gates, so no console command round-trips through the gradle-forked
        // server JVM stdin. Default no-op.
        String probe = System.getProperty("subterra.probe.anim");
        if (probe == null || probe.isBlank()) {
            return;
        }
        try {
            // Deterministic fixed-order drive (no timing, no randomness); the final pose digest
            // is re-verified against an identically re-driven machine from scratch.
            AnimStateMachine machine = sampleMachine();
            int states = machine.stateNames().size();
            String digest = poseDigest(machine.sample());
            if (!digest.equals(poseDigest(sampleMachine().sample()))) {
                throw new IllegalStateException("sample pose not deterministic");
            }
            LOGGER.info("{} ok (states={}, sample={}, verify=ok)", MARKER, states, digest);
        } catch (RuntimeException e) {
            // the samples are legal, so a mismatch is a program error — never emit a false ok.
            LOGGER.warn("{} anim mismatch (error={})", MARKER, e.getMessage());
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        // no held state -> pure no-op; symmetric empty hook kept (as the other shells).
    }

    /** 确定性样例驱动序列：idle 注册 → walk 注册 → 3.0 tick 空闲位 → 请求 walk 过渡（固定 2-tick）→
     *  两拍 1.0 tick 跨过渡 → walk 当前位 t=2.0；最终 {@link #sample()} 为 walk@2 确定性 pose。
     *  Deterministic sample drive sequence: register idle → register walk → 3.0 ticks in idle →
     *  request the walk transition (fixed 2 ticks) → two 1.0-tick beats across the transition →
     *  walk current at t=2.0; the final {@link #sample()} is the walk@2 deterministic pose. */
    private static AnimStateMachine sampleMachine() {
        AnimStateMachine machine = new AnimStateMachine(2.0);
        machine.registerState("idle", idleAnimation());
        machine.registerState("walk", walkAnimation());
        machine.tick(3.0);
        machine.requestTransition("walk");
        machine.tick(1.0);
        machine.tick(1.0);
        return machine;
    }

    /** 固定关键帧序列示例动画：idle（8 tick，三通道全 0/4/8 关键帧，位移 +X 摆动、绕 Z 摇摆、缩放
     *  脉冲）。Fixed-keyframe sample animation: idle (8 ticks, all three channels keyed at 0/4/8,
     *  +X sway / Z-rotation swing / scale pulse). */
    private static AnimationData idleAnimation() {
        return new AnimationData("sample.idle", 8.0, List.of(
                new KeyframeTrack(AnimChannel.POSITION, List.of(
                        kf(0.0, 0.0, 0.0, 0.0), kf(4.0, 1.0, 0.0, 0.0), kf(8.0, 0.0, 0.0, 0.0))),
                new KeyframeTrack(AnimChannel.ROTATION, List.of(
                        kf(0.0, 0.0, 0.0, 0.0), kf(4.0, 0.0, 0.0, 45.0), kf(8.0, 0.0, 0.0, 0.0))),
                new KeyframeTrack(AnimChannel.SCALE, List.of(
                        kf(0.0, 1.0, 1.0, 1.0), kf(4.0, 1.2, 1.2, 1.2), kf(8.0, 1.0, 1.0, 1.0)))));
    }

    /** 固定关键帧序列示例动画：walk（6 tick，三通道全 0/3/6 关键帧，沿 +Z 位移、绕 Y 摆幅、缩放
     *  收缩）。Fixed-keyframe sample animation: walk (6 ticks, all three channels keyed at 0/3/6,
     *  +Z advance / Y-swing / scale shrink). */
    private static AnimationData walkAnimation() {
        return new AnimationData("sample.walk", 6.0, List.of(
                new KeyframeTrack(AnimChannel.POSITION, List.of(
                        kf(0.0, 0.0, 0.0, 0.0), kf(3.0, 0.0, 0.0, 2.0), kf(6.0, 0.0, 0.0, 0.0))),
                new KeyframeTrack(AnimChannel.ROTATION, List.of(
                        kf(0.0, 0.0, 0.0, 0.0), kf(3.0, 0.0, 15.0, 0.0), kf(6.0, 0.0, 0.0, 0.0))),
                new KeyframeTrack(AnimChannel.SCALE, List.of(
                        kf(0.0, 1.0, 1.0, 1.0), kf(3.0, 0.9, 0.9, 0.9), kf(6.0, 1.0, 1.0, 1.0)))));
    }

    /** 构造关键帧。Builds a keyframe. */
    private static Keyframe kf(double t, double x, double y, double z) {
        return new Keyframe(t, AnimVec3.of(x, y, z));
    }

    /** 确定性 pose 摘要：三通道逐分量 {@code %.4f}（固定 Locale.ROOT）文本。
     *  Deterministic pose digest: per-channel per-component {@code %.4f} (fixed Locale.ROOT) text. */
    private static String poseDigest(AnimPose pose) {
        return "pos:" + fmt(pose.position()) + " rot:" + fmt(pose.rotation())
                + " scale:" + fmt(pose.scale());
    }

    /** 逐分量 {@code %.4f} 摘要。Per-component {@code %.4f} digest. */
    private static String fmt(AnimVec3 v) {
        return String.format(Locale.ROOT, "%.4f/%.4f/%.4f", v.x(), v.y(), v.z());
    }
}
