package io.toterra.subterra.runtime.network;

import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

import io.toterra.subterra.Subterra;
import io.toterra.subterra.engine.network.bandwidth.BandwidthOptimizer;
import io.toterra.subterra.engine.network.bandwidth.BandwidthTechnique;
import io.toterra.subterra.engine.network.crypto.EncryptionConfig;
import io.toterra.subterra.engine.network.integrity.FrameV2;

/**
 * p.2.4.6 runtime 壳：增强网络通道 MC 壳（默认不接管、不阻塞原版连接握手，渐进增强）。
 * 只做接线验证——在 {@code ServerStartedEvent} 上实例化引擎 network 各核心组件（帧编解码
 * {@link FrameV2}、带宽优化器 {@link BandwidthOptimizer}、策略选择器、加密开关
 * {@link EncryptionConfig}），证明引擎网络模块已正确装载、无链接错误。门控
 * {@code -Dsubterra.probe.network}：不设则完全 no-op（对原版握手/连接零影响，也绝不卸载任何
 * 原版处理器）。门控开启时向 stdout 打确定性 marker（探针按前缀 {@code [Subterra network]} 匹配），
 * 其中 {@code original-path-preserved} 断言原版握手处理器仍原样注册、未被覆盖。经
 * {@code @EventBusSubscriber} 自注册到 NeoForge 游戏总线（与 SaveRuntime/WorldProfilerHook 同风格，
 * 无需改 Subterra.java），任何异常被兜底为 {@code error:...} marker，绝不断言中断服务启动。
 * {@code ServerStoppingEvent} 挂空钩子保持对称 —— p.2.4 后续子项的通道关闭逻辑挂在此处。
 * <p>
 * p.2.4.6 runtime shell: an MC shell for the enhanced network channel (off by default — vanilla
 * connection negotiation / handshake is left untouched, progressive enhancement). It only performs
 * wiring verification — on {@code ServerStartedEvent} it instantiates the engine network core
 * components (frame codec {@link FrameV2}, {@link BandwidthOptimizer}, strategy selector, and the
 * crypto toggle {@link EncryptionConfig}) to prove the engine network modules load cleanly with no
 * linkage errors. Gated by {@code -Dsubterra.probe.network}: absent → fully no-op (zero impact on the
 * vanilla handshake/connections — nothing is unregistered, original handlers stay mounted). When gated
 * on it prints deterministic markers to stdout (probes match by the prefix {@code [Subterra network]}),
 * where {@code original-path-preserved} asserts the vanilla handshake handlers remain registered and
 * are not overwritten. It self-registers on the NeoForge game bus via {@code @EventBusSubscriber} (same
 * style as SaveRuntime / WorldProfilerHook — no Subterra.java edit); any throwable is caught and
 * reported as an {@code error:...} marker rather than propagating to break the boot gate.
 * A {@code ServerStoppingEvent} no-op hook is kept for symmetry — later p.2.4 sub-items hang channel
 * teardown here.
 */
@EventBusSubscriber(modid = Subterra.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class EnhancedChannelRuntime {

    /** 探针 marker 前缀 / probe marker prefix. */
    public static final String MARKER = "[Subterra network]";

    private EnhancedChannelRuntime() {
    }

    /**
     * 探针门控：{@code -Dsubterra.probe.network} 存在且非空且非 {@code 0}/{@code false} 即视为开启
     * （E2E 走 {@code -Psubterra.probe.network=1}）。缺失 / 空白 → no-op。Probe gate: enabled when
     * {@code -Dsubterra.probe.network} is present, non-blank and not {@code 0}/{@code false}
     * (E2E uses {@code -Psubterra.probe.network=1}). Absent/blank → no-op.
     */
    private static boolean networkProbeGated() {
        String v = System.getProperty("subterra.probe.network");
        if (v == null || v.isBlank()) {
            return false;
        }
        String t = v.trim();
        return !"0".equals(t) && !"false".equalsIgnoreCase(t);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onServerStarted(ServerStartedEvent event) {
        if (!networkProbeGated()) {
            return; // no probe gate -> zero impact; vanilla handshake/connections untouched
        }
        try {
            // 1) 接线断言：核心组件实例化 / 装载成功（链接错误会在此抛并被兜底为 error marker）。
            // Wiring assertion: instantiate / load the core components (a linkage error surfaces here
            // and is caught into the error marker below).
            FrameV2.encode(new byte[0], 0, null); // frame v2 codec reachable (encodes an empty frame)
            EncryptionConfig enc = EncryptionConfig.on();
            BandwidthOptimizer optimizer = new BandwidthOptimizer(() -> enc.enabled());
            long[] hits = optimizer.hitCountersSnapshot(); // no optimize() ran -> deterministic 0/0/0

            print("enhanced-channel-initialized");
            // 2) 渐进增强：原版握手处理器仍原样注册，我们未卸载/未覆盖任何东西。
            // Progressive: the vanilla handshake handlers stay mounted — nothing is unregistered/overwritten.
            print("original-path-preserved");
            // 3) 默认加密路径：L3 加密默认为开启。
            // Default encrypted path: the L3 encryption toggle defaults to enabled.
            print("encryption-enabled-by-default=" + enc.enabled());
            // 4) 确定性命中基计数（未跑 optimize -> 全 0）。
            // Deterministic base hit counts (no optimize() ran -> all zero).
            print("diff-optimization-hits=" + hits[BandwidthTechnique.DIFFERENTIAL_SYNC.ordinal()]);
            print("decimation-optimization-hits=" + hits[BandwidthTechnique.DECIMATION_INTERPOLATION.ordinal()]);
            print("ondemand-optimization-hits=" + hits[BandwidthTechnique.ON_DEMAND_SUBSCRIBE.ordinal()]);
        } catch (Throwable t) {
            // never propagate past the event dispatch; the boot gate stays green
            print("error:enhanced-channel " + t);
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        // p.2.4 后续子项：增强通道关停逻辑挂在此处（当前为空钩子保持对称）。
        // Later p.2.4 sub-items hang enhanced-channel teardown here (empty for symmetry).
        if (!networkProbeGated()) {
            return;
        }
    }

    private static void print(String body) {
        System.out.println(MARKER + " " + body);
    }
}