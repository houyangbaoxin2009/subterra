package io.toterra.subterra.runtime.worldgen.async;

import java.util.function.Consumer;

import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

import io.toterra.subterra.Subterra;
import io.toterra.subterra.engine.worldgen.async.io.AsyncIoQueue;
import io.toterra.subterra.engine.worldgen.async.io.AsyncWrite;

/**
 * p.2.6.6 runtime 壳：异步区块世界生成 MC 壳（默认不接管，渐进增强）。只做接线验证——在
 * {@code ServerStartedEvent} 上打开引擎异步 I/O 门 {@link AsyncIoQueue#enable()} 并证明异步
 * 核心已正确装载（p.2.6.3 流水线 / p.2.6.4 队列均来自 engine.worldgen.async，纯 JDK）。门控
 * {@code -Dsubterra.probe.async}：不设则完全 no-op，对原版区块生成/生命周期零影响（也不与任何
 * 既有 marker 交互——network/save marker 及各自 original-path 保持字节原样）。门控开启时向 stdout
 * 打确定性 marker（探针按前缀 {@code [Subterra async]} 匹配）。经 {@code @EventBusSubscriber}
 * 自注册到 NeoForge 游戏总线（与 EnhancedChannelRuntime/SaveRuntime 同风格，无需改 Subterra.java），
 * 任何异常被兜底为 {@code error:...} marker，绝不断言中断服务启动。{@code ServerStoppingEvent} 上
 * 清空并关闭本壳持有的异步 I/O 队列（确定性刷新，配合 close 语义）。
 * <p>
 * 本壳刻意不含游戏内区块生成循环、也不在游戏侧断言引擎确定性（那是纯 JVM 探针的事）；C2meCoexistence
 * 的共存协调完全独立、此处不触碰。引擎导入仅限 engine.worldgen.async.io.{@link AsyncIoQueue} /
 * {@link AsyncWrite}。
 * <p>
 * p.2.6.6 runtime shell: an MC shell for async worldgen (off by default — vanilla chunk generation
 * and lifecycle are untouched, progressive enhancement). It only performs wiring verification: on
 * {@code ServerStartedEvent} it flips the engine async I/O gate {@link AsyncIoQueue#enable()} and
 * proves the async core loads cleanly (p.2.6.3 pipeline / p.2.6.4 queue both live in
 * engine.worldgen.async, pure JDK). Gated by {@code -Dsubterra.probe.async}: absent → fully no-op
 * (zero impact on vanilla chunkgen/lifecycle, and it never touches existing markers — the
 * network/save markers plus their original-path lines stay byte-identical). When gated on it prints
 * deterministic markers to stdout (probes match by the prefix {@code [Subterra async]}). It
 * self-registers on the NeoForge game bus via {@code @EventBusSubscriber} (same style as
 * EnhancedChannelRuntime / SaveRuntime — no Subterra.java edit); any throwable is caught and
 * reported as an {@code error:...} marker rather than breaking the boot gate. On
 * {@code ServerStoppingEvent} it drains and closes the shell-held async I/O queue (deterministic
 * flush, consistent with the queue close semantics).
 * <p>
 * This shell deliberately has no in-game chunk-generation loop and does not assert engine
 * determinism inside the game (proven by pure-JVM probes); C2meCoexistence coexistence coordination
 * is fully independent and untouched here. Engine imports are limited to
 * engine.worldgen.async.io.{@link AsyncIoQueue} / {@link AsyncWrite}.
 */
@EventBusSubscriber(modid = Subterra.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class AsyncChunkRuntime {

    /** 探针 marker 前缀 / probe marker prefix. */
    public static final String MARKER = "[Subterra async]";

    /** The shell-held async I/O queue, created only while the gate is on. */
    private static AsyncIoQueue ioQueue;

    private AsyncChunkRuntime() {
    }

    /**
     * 探针门控：{@code -Dsubterra.probe.async} 存在且非空且非 {@code 0}/{@code false} 即视为开启
     * （E2E 走 {@code -Psubterra.probe.async=1}）。缺失 / 空白 → no-op。Probe gate: enabled when
     * {@code -Dsubterra.probe.async} is present, non-blank and not {@code 0}/{@code false}
     * (E2E uses {@code -Psubterra.probe.async=1}). Absent/blank → no-op.
     */
    private static boolean asyncProbeGated() {
        String v = System.getProperty("subterra.probe.async");
        if (v == null || v.isBlank()) {
            return false;
        }
        String t = v.trim();
        return !"0".equals(t) && !"false".equalsIgnoreCase(t);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onServerStarted(ServerStartedEvent event) {
        if (!asyncProbeGated()) {
            return; // no probe gate -> zero impact; vanilla chunkgen / lifecycle untouched
        }
        try {
            // 1) 基线断言：新鲜游戏 JVM 中异步 I/O 门默认关闭（渐进增强，绝不默认接管）。
            // Baseline: in a fresh game JVM the async I/O gate is off by default.
            print("baseline-io-default-off=" + !AsyncIoQueue.isEnabled());
            // 2) 接线断言：打开引擎异步 I/O 门（shell 的决定；队列自身行为不变）。
            // Wiring: enable the engine async I/O gate (a shell decision; the queue
            // behaviour itself is unchanged).
            AsyncIoQueue.enable();
            print("async-io-gate-enabled=" + AsyncIoQueue.isEnabled());
            // 3) 装配一条确定性写并把持排队（ServerStoppingEvent 上确定性刷新 + close）。
            // Stage one deterministic write and hold it queued (drained + closed on
            // ServerStoppingEvent).
            Consumer<AsyncWrite> memorySink = w -> {
                // memory-endpoint sink; nothing routed to vanilla I/O
            };
            ioQueue = AsyncIoQueue.of(memorySink, 1);
            ioQueue.enqueue(new AsyncWrite("subterra.boot", new byte[]{0x53, 0x54}));
            print("async-chunk-runtime-initialized");
        } catch (Throwable t) {
            // never propagate past the event dispatch; the boot gate stays green
            print("error:async-chunk " + t);
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        if (!asyncProbeGated()) {
            return; // no probe gate -> zero impact
        }
        try {
            // 确定性刷新 + 关闭本壳持有的异步 I/O 队列（恰好一次、按入队次序交付）。
            // Deterministic flush + close of the shell-held async I/O queue (exactly-once,
            // delivered in enqueue order).
            if (ioQueue != null) {
                int pending = ioQueue.pendingCount();
                ioQueue.flushAll();
                long flushed = ioQueue.flushedCount();
                ioQueue.close();
                ioQueue = null;
                print("shutdown-flush pending=" + pending + " flushed=" + flushed);
            } else {
                print("shutdown-flush pending=0 flushed=0");
            }
        } catch (Throwable t) {
            // never propagate past the event dispatch; the boot gate stays green
            print("error:async-shutdown " + t);
        }
    }

    private static void print(String body) {
        System.out.println(MARKER + " " + body);
    }
}