package io.toterra.subterra.runtime.network.holepunch;

import io.toterra.subterra.engine.p2p.NodeAddr;
import io.toterra.subterra.engine.p2p.holepunch.HolePunchPlanner;
import io.toterra.subterra.engine.p2p.holepunch.HolePunchSession;
import io.toterra.subterra.engine.p2p.holepunch.PunchAttempt;
import io.toterra.subterra.engine.p2p.holepunch.PunchPlan;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * p.2.30.2 LAN 打洞接线壳（runtime.network.holepunch）：默认 no-op（零开销、
 * 零 socket），仅当门控 {@code subterra.probe.holepunch} 设置时在 ServerStarted
 * 执行一次 **loopback 自打洞样例**——真实 UDP 双端（127.0.0.1 两个临时端口）经
 * {@code engine.p2p.holepunch} 计划器驱动互发探针并回执，输出确定性 marker 供
 * E2E 断言。不改动原版握手路径。
 *
 * <p>The p.2.30.2 holepunch wiring shell (runtime.network.holepunch): no-op by
 * default (zero overhead, zero sockets); only when the {@code subterra.probe.holepunch}
 * gate is set does ServerStarted run a **loopback self-punch sample** — two real UDP
 * endpoints (two ephemeral ports on 127.0.0.1) exchange probes driven by the
 * {@code engine.p2p.holepunch} planner and acknowledge, emitting deterministic
 * markers for E2E assertions. The vanilla handshake path is untouched.
 */
public final class HolepunchRuntime {

    /** 确定性日志 marker。 / The deterministic log marker. */
    public static final String MARKER = "[Subterra holepunch]";

    /** E2E 门控属性。 / The E2E gate property. */
    public static final String PROBE_GATE = "subterra.probe.holepunch";

    private static final byte[] PROBE_PAYLOAD = "subterra-holepunch-probe".getBytes(StandardCharsets.UTF_8);
    private static final byte[] ACK_PAYLOAD = "subterra-holepunch-ack".getBytes(StandardCharsets.UTF_8);

    public static final Logger LOGGER = LogUtils.getLogger();

    private HolepunchRuntime() {
    }

    /** 从 mod 构造调用：仅挂 ServerStarted 门控监听。 / Called from the mod constructor: gated ServerStarted listener only. */
    public static void bootstrap(ModContainer container) {
        NeoForge.EVENT_BUS.addListener(HolepunchRuntime::onServerStarted);
        LOGGER.info("{} shell active (default no-op; loopback self-punch only under {})", MARKER, PROBE_GATE);
    }

    private static void onServerStarted(ServerStartedEvent event) {
        if (System.getProperty(PROBE_GATE) == null) {
            return;
        }
        try {
            runLoopbackSelfPunch();
        } catch (Exception e) {
            LOGGER.warn("{} gate=on loopback failed ({})", MARKER, e.toString());
        }
    }

    private static void runLoopbackSelfPunch() throws Exception {
        try (DatagramSocket a = new DatagramSocket(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0));
             DatagramSocket b = new DatagramSocket(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0))) {
            a.setSoTimeout(2000);
            b.setSoTimeout(2000);
            int bPort = b.getLocalPort();
            int aPort = a.getLocalPort();
            // 计划：本机 a → 对端 b 的预测/自报端口尝试（观测序列 = {bPort}，PRESERVE）。
            // Plan: local a → peer b's predicted/self-reported ports (observed = {bPort}, PRESERVE).
            PunchPlan plan = HolePunchPlanner.plan(
                    List.of(new NodeAddr.Direct("127.0.0.1", bPort)),
                    new int[]{bPort},
                    null,
                    new HolePunchPlanner.Params(2, false, 0, 0));
            HolePunchSession session = new HolePunchSession(plan);
            int usedAttempts = 0;
            DatagramPacket ack = new DatagramPacket(new byte[ACK_PAYLOAD.length], ACK_PAYLOAD.length);
            boolean punched = false;
            for (PunchAttempt attempt : plan.attempts()) {
                usedAttempts = attempt.index() + 1;
                if (attempt.kind() == PunchAttempt.Kind.RELAY) {
                    break;
                }
                byte[] addr = attempt.target().toString().getBytes(StandardCharsets.UTF_8);
                a.send(new DatagramPacket(PROBE_PAYLOAD, PROBE_PAYLOAD.length,
                        InetAddress.getByName("127.0.0.1"), portOf(attempt.target())));
                DatagramPacket in = new DatagramPacket(new byte[PROBE_PAYLOAD.length], PROBE_PAYLOAD.length);
                try {
                    b.receive(in);
                } catch (SocketTimeoutException e) {
                    if (session.onEvent(new HolePunchSession.Event(HolePunchSession.Kind.PUNCH_FAIL, null))) {
                        break;
                    }
                    continue;
                }
                session.onEvent(new HolePunchSession.Event(HolePunchSession.Kind.PUNCH_OK,
                        new NodeAddr.Direct("127.0.0.1", aPort)));
                b.send(new DatagramPacket(ACK_PAYLOAD, ACK_PAYLOAD.length,
                        InetAddress.getByName("127.0.0.1"), aPort));
                a.receive(ack);
                punched = true;
                break;
            }
            boolean ackOk = punched && new String(ack.getData(), 0, ack.getLength(), StandardCharsets.UTF_8)
                    .equals(new String(ACK_PAYLOAD, StandardCharsets.UTF_8));
            LOGGER.info("{} gate=on loopback {} (attempts={}, aPort={}, bPort={}, state={})",
                    MARKER, ackOk ? "ok" : "failed", usedAttempts, aPort, bPort, session.state());
        }
    }

    private static int portOf(NodeAddr target) {
        if (target instanceof NodeAddr.Direct d) {
            return d.port();
        }
        throw new IllegalArgumentException("loopback sample only supports direct targets");
    }
}
