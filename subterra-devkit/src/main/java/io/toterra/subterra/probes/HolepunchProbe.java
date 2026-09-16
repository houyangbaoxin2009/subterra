package io.toterra.subterra.probes;

import io.toterra.subterra.engine.p2p.NodeAddr;
import io.toterra.subterra.engine.p2p.holepunch.HolePunchPlanner;
import io.toterra.subterra.engine.p2p.holepunch.HolePunchSession;
import io.toterra.subterra.engine.p2p.holepunch.PortPredictor;
import io.toterra.subterra.engine.p2p.holepunch.PunchAttempt;
import io.toterra.subterra.engine.p2p.holepunch.PunchPlan;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * p.2.30 确定性探针（纯 JVM）：端口预测三策略分类与预测值、计划器确定性
 * （同输入深相等 / 尝试序与延迟递增 / PREDICTED 与 WILDCARD 档语义 / RELAY 兜底
 * 开关）、非法值确定性拒绝、会话状态机全轨迹（直连命中 / 预算穷尽转 RELAY /
 * RELAY 失败 FAILED / 终态事件拒绝）+ loopback 真实 UDP 自打洞 + runtime 壳
 * 接线盘点（marker / 门控 class-bytes）。禁时序断言（socket 超时仅作 I/O 兜底）。
 *
 * <p>The p.2.30 deterministic probe (pure JVM): port-prediction strategies and
 * predictions, planner determinism (deep-equal plans, ordered attempts with growing
 * delays, PREDICTED/WILDCARD semantics, the RELAY toggle), deterministic rejection
 * of illegal values, the full session state-machine trajectories (direct hit /
 * exhausted budget → RELAY / RELAY failure → FAILED / post-terminal rejection),
 * a real loopback UDP self-punch, and the runtime-shell wiring inventory (marker /
 * gate class bytes). No timing assertions (socket timeouts are I/O backstops only).
 */
public final class HolepunchProbe {

    private static int checks;
    private static int failures;

    public static void main(String[] args) throws Exception {
        portPrediction();
        plannerDeterminism();
        plannerRejections();
        sessionTrajectories();
        loopbackSelfPunch();
        wiringInventory();
        System.out.println("[HolepunchProbe] " + (failures == 0 ? "PASS (" + checks + " checks)"
                : "FAIL (" + failures + " of " + checks + " checks failed)"));
        if (failures > 0) {
            System.exit(1);
        }
    }

    private static void portPrediction() {
        check("predict: stable sequence is PRESERVE, predicts last",
                PortPredictor.classify(new int[]{40000, 40000, 40000}) == PortPredictor.Strategy.PRESERVE
                        && PortPredictor.predict(new int[]{40000, 40000}) == 40000);
        check("predict: constant delta is INCREMENT, predicts last+delta",
                PortPredictor.classify(new int[]{40000, 40001, 40002}) == PortPredictor.Strategy.INCREMENT
                        && PortPredictor.predict(new int[]{40000, 40001, 40002}) == 40003);
        check("predict: irregular sequence is RANDOM, predicts -1",
                PortPredictor.classify(new int[]{40000, 40100, 40002}) == PortPredictor.Strategy.RANDOM
                        && PortPredictor.predict(new int[]{40000, 40100, 40002}) == -1);
        check("predict: empty sequence is RANDOM",
                PortPredictor.predict(new int[]{}) == -1 && PortPredictor.predict(null) == -1);
        check("predict: increment clamped to 65535",
                PortPredictor.predict(new int[]{65534, 65535, 65535 + 1}) == 65535);
    }

    private static void plannerDeterminism() {
        List<NodeAddr.Direct> candidates = List.of(
                new NodeAddr.Direct("203.0.113.10", 25565),
                new NodeAddr.Direct("203.0.113.11", 25566));
        NodeAddr relay = new NodeAddr.ViaRelay(io.toterra.subterra.engine.p2p.NodeId.fromHex("00000000000000000000000000000001"), "tok");
        HolePunchPlanner.Params p = new HolePunchPlanner.Params(2, true, 0, 50);
        PunchPlan a = HolePunchPlanner.plan(candidates, new int[]{40000, 40001, 40002}, relay, p);
        PunchPlan b = HolePunchPlanner.plan(candidates, new int[]{40000, 40001, 40002}, relay, p);
        check("plan: identical inputs deep-equal", a.equals(b));
        check("plan: PREDICTED uses the predicted port", a.attempts().get(0).kind() == PunchAttempt.Kind.PREDICTED
                && a.attempts().get(0).target().toString().contains("40003"));
        check("plan: WILDCARD uses the advertised port", a.attempts().get(1).kind() == PunchAttempt.Kind.WILDCARD
                && a.attempts().get(1).target().toString().contains("25565"));
        check("plan: relay appended last when enabled",
                a.attempts().get(a.attempts().size() - 1).kind() == PunchAttempt.Kind.RELAY);
        check("plan: delays grow deterministically", a.attempts().get(1).delayMillis() > a.attempts().get(0).delayMillis());
        PunchPlan noRelay = HolePunchPlanner.plan(candidates, new int[]{40000}, null, new HolePunchPlanner.Params(1, false, 0, 10));
        check("plan: relay skipped when disabled or absent",
                noRelay.attempts().stream().noneMatch(x -> x.kind() == PunchAttempt.Kind.RELAY));
        PunchPlan randomPorts = HolePunchPlanner.plan(candidates, new int[]{}, relay, p);
        check("plan: unpredictable ports skip the PREDICTED tier",
                randomPorts.attempts().stream().noneMatch(x -> x.kind() == PunchAttempt.Kind.PREDICTED));
        check("plan: canonical render prefix", a.render().startsWith("punch plan attempts="));
    }

    private static void plannerRejections() {
        reject("no candidates and no relay", () -> HolePunchPlanner.plan(List.of(), new int[]{1}, null,
                new HolePunchPlanner.Params(2, false, 0, 0)));
        reject("negative direct attempts", () -> HolePunchPlanner.plan(
                List.of(new NodeAddr.Direct("203.0.113.10", 25565)), new int[]{1}, null,
                new HolePunchPlanner.Params(-1, false, 0, 0)));
        reject("negative base delay", () -> HolePunchPlanner.plan(
                List.of(new NodeAddr.Direct("203.0.113.10", 25565)), new int[]{1}, null,
                new HolePunchPlanner.Params(1, false, -1, 0)));
    }

    private static void sessionTrajectories() {
        List<NodeAddr.Direct> candidates = List.of(new NodeAddr.Direct("203.0.113.10", 25565));
        NodeAddr relay = new NodeAddr.ViaRelay(
                io.toterra.subterra.engine.p2p.NodeId.fromHex("00000000000000000000000000000001"), "tok");
        PunchPlan plan = HolePunchPlanner.plan(candidates, new int[]{40000, 40001}, relay, new HolePunchPlanner.Params(2, true, 0, 10));
        // 轨迹一：直连第一次尝试即命中。
        HolePunchSession s1 = new HolePunchSession(plan);
        NodeAddr hit = new NodeAddr.Direct("203.0.113.10", 40000);
        boolean settled = s1.onEvent(new HolePunchSession.Event(HolePunchSession.Kind.PUNCH_OK, hit));
        check("session: first-hit establishes", settled && s1.state() == HolePunchSession.State.ESTABLISHED
                && s1.established().equals(hit));
        // 轨迹二：直连全败 → RELAY（有兜底）。直连预算 = 计划中非 RELAY 尝试数。
        HolePunchSession s2 = new HolePunchSession(plan);
        int directBudget = (int) plan.attempts().stream()
                .filter(x -> x.kind() != PunchAttempt.Kind.RELAY).count();
        for (int i = 0; i < directBudget - 1; i++) {
            s2.onEvent(new HolePunchSession.Event(HolePunchSession.Kind.PUNCH_FAIL, null));
        }
        boolean wentRelay = !s2.onEvent(new HolePunchSession.Event(HolePunchSession.Kind.PUNCH_FAIL, null))
                && s2.state() == HolePunchSession.State.RELAY;
        check("session: exhausted budget falls back to RELAY", wentRelay);
        settled = s2.onEvent(new HolePunchSession.Event(HolePunchSession.Kind.RELAY_OK, relay));
        check("session: relay ok establishes", settled && s2.state() == HolePunchSession.State.ESTABLISHED);
        // 轨迹三：无兜底计划直连全败 → FAILED。
        PunchPlan noFallback = HolePunchPlanner.plan(candidates, new int[]{40000}, null, new HolePunchPlanner.Params(1, false, 0, 0));
        HolePunchSession s3 = new HolePunchSession(noFallback);
        int noFallbackBudget = (int) noFallback.attempts().stream()
                .filter(x -> x.kind() != PunchAttempt.Kind.RELAY).count();
        for (int i = 0; i < noFallbackBudget - 1; i++) {
            s3.onEvent(new HolePunchSession.Event(HolePunchSession.Kind.PUNCH_FAIL, null));
        }
        settled = s3.onEvent(new HolePunchSession.Event(HolePunchSession.Kind.PUNCH_FAIL, null));
        check("session: exhausted without fallback fails", settled && s3.state() == HolePunchSession.State.FAILED);
        // 轨迹四：终态后事件确定性拒绝。
        try {
            s3.onEvent(new HolePunchSession.Event(HolePunchSession.Kind.PUNCH_FAIL, null));
            check("session: post-terminal events rejected", false);
        } catch (IllegalArgumentException e) {
            check("session: post-terminal events rejected", true);
        }
    }

    private static void loopbackSelfPunch() throws Exception {
        try (DatagramSocket a = new DatagramSocket(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0));
             DatagramSocket b = new DatagramSocket(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0))) {
            a.setSoTimeout(2000);
            b.setSoTimeout(2000);
            int bPort = b.getLocalPort();
            PunchPlan plan = HolePunchPlanner.plan(
                    List.of(new NodeAddr.Direct("127.0.0.1", bPort)), new int[]{bPort}, null,
                    new HolePunchPlanner.Params(2, false, 0, 0));
            HolePunchSession session = new HolePunchSession(plan);
            byte[] probe = "subterra-holepunch-probe".getBytes(StandardCharsets.UTF_8);
            byte[] ackB = "subterra-holepunch-ack".getBytes(StandardCharsets.UTF_8);
            DatagramPacket ack = new DatagramPacket(new byte[ackB.length], ackB.length);
            boolean punched = false;
            for (PunchAttempt attempt : plan.attempts()) {
                if (attempt.kind() == PunchAttempt.Kind.RELAY) {
                    break;
                }
                a.send(new DatagramPacket(probe, probe.length,
                        InetAddress.getByName("127.0.0.1"), directPort(attempt.target())));
                DatagramPacket in = new DatagramPacket(new byte[probe.length], probe.length);
                try {
                    b.receive(in);
                } catch (SocketTimeoutException e) {
                    session.onEvent(new HolePunchSession.Event(HolePunchSession.Kind.PUNCH_FAIL, null));
                    continue;
                }
                session.onEvent(new HolePunchSession.Event(HolePunchSession.Kind.PUNCH_OK,
                        new NodeAddr.Direct("127.0.0.1", a.getLocalPort())));
                b.send(new DatagramPacket(ackB, ackB.length,
                        InetAddress.getByName("127.0.0.1"), a.getLocalPort()));
                a.receive(ack);
                punched = true;
                break;
            }
            String ackText = new String(ack.getData(), 0, ack.getLength(), StandardCharsets.UTF_8);
            check("loopback: real UDP self-punch establishes and acks byte-exact",
                    punched && session.state() == HolePunchSession.State.ESTABLISHED
                            && ackText.equals(new String(ackB, StandardCharsets.UTF_8)));
        }
    }

    private static void wiringInventory() {
        check("wiring: HolepunchRuntime present (load-only)",
                present("io.toterra.subterra.runtime.network.holepunch.HolepunchRuntime"));
        check("wiring: marker literal '[Subterra holepunch]' in runtime bytes",
                classBytesContain("io.toterra.subterra.runtime.network.holepunch.HolepunchRuntime", "[Subterra holepunch]"));
        check("wiring: gate literal 'subterra.probe.holepunch' in runtime bytes",
                classBytesContain("io.toterra.subterra.runtime.network.holepunch.HolepunchRuntime", "subterra.probe.holepunch"));
    }

    private static int directPort(NodeAddr target) {
        if (target instanceof NodeAddr.Direct d) {
            return d.port();
        }
        throw new IllegalArgumentException("loopback sample only supports direct targets");
    }

    private static void reject(String name, Runnable r) {
        try {
            r.run();
            check("reject: " + name, false);
        } catch (IllegalArgumentException e) {
            check("reject: " + name, true);
        }
    }

    private static boolean present(String fqcn) {
        try {
            Class.forName(fqcn, false, HolepunchProbe.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }

    private static byte[] classBytes(String fqcn) {
        String resource = "/" + fqcn.replace('.', '/') + ".class";
        try (java.io.InputStream in = HolepunchProbe.class.getResourceAsStream(resource)) {
            return in == null ? null : in.readAllBytes();
        } catch (java.io.IOException e) {
            return null;
        }
    }

    private static boolean classBytesContain(String fqcn, String literal) {
        byte[] bytes = classBytes(fqcn);
        return bytes != null && new String(bytes, java.nio.charset.StandardCharsets.ISO_8859_1).contains(literal);
    }

    private static void check(String name, boolean ok) {
        checks++;
        if (ok) {
            System.out.println("[PASS] " + name);
        } else {
            failures++;
            System.out.println("[FAIL] " + name);
        }
    }
}
