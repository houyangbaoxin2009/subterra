package io.toterra.subterra.probes;

import io.toterra.subterra.engine.p2p.NodeId;
import io.toterra.subterra.engine.p2p.relay.ForwardRequest;
import io.toterra.subterra.engine.p2p.relay.ForwardResult;
import io.toterra.subterra.engine.p2p.relay.RelayEndpoint;
import io.toterra.subterra.engine.p2p.relay.RelayForwarder;
import io.toterra.subterra.engine.p2p.transport.ChannelResult;
import io.toterra.subterra.engine.p2p.transport.P2pChannel;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * p.2.5.8 志愿 relay 兜底转发的确定性验收探针（纯 JVM，无 MC 运行时）：断言 {@link RelayForwarder}
 * 的记忆段端点登记表 + 按 token 的兜底转发——登记→转发→送达逐字节一致、未登记 token / token 错配
 * 明确 REJECTED（不静默）、hop 超限防环 DROPPED(loop) 且同输入两次一致、unregister 后 REJECTED、
 * registeredTokens 确定性遍历序（字典序）、shutdown 关停清空、与 {@link P2pChannel} 串联往返
 * 逐字节一致（含经 relay 转发的 FrameV2 篡改拒收），以及固定 seed 两次结果序列一致。
 * <p>
 * Deterministic acceptance probe for the p.2.5.8 voluntary-relay fallback (pure JVM — no Minecraft
 * runtime): asserts the {@link RelayForwarder} memory-endpoint registry + token-based fallback
 * forward — register → forward → byte-identical delivery, unregistered / mismatched token explicitly
 * REJECTED (not silent), over-limit hop DROPPED(loop) and repeated-input identical, REJECTED after
 * unregister, deterministic lexicographic registeredTokens traversal, shutdown clearing, the
 * {@link P2pChannel} linkage round-trip byte-identical (incl. FrameV2 tamper-reject carried through
 * the relay), and fixed-seed reproducibility of a result sequence.
 * <p>
 * Determinism: fixed seeds; failure counter advances only on failure; no timing assertions.
 * Exit 0 = PASS all checks; 1 = FAIL.
 */
public final class P2pRelayProbe {

    private P2pRelayProbe() {
    }

    private static int failures = 0;
    private static int checks = 0;

    private static void check(String name, boolean ok) {
        checks++;
        if (ok) {
            System.out.println("[PASS] " + name);
        } else {
            failures++;
            System.out.println("[FAIL] " + name);
        }
    }

    private static NodeId id(long seed) {
        byte[] b = new byte[NodeId.BYTES];
        new Random(seed).nextBytes(b);
        return NodeId.of(b);
    }

    private static byte[] payload(long seed, int n) {
        byte[] p = new byte[n];
        new Random(seed).nextBytes(p);
        return p;
    }

    private static byte[] b(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    private static CaptureEndpoint capturing() {
        return new CaptureEndpoint();
    }

    private static final class CaptureEndpoint implements RelayEndpoint {
        byte[] delivered;
        boolean hit;

        @Override
        public void deliver(byte[] payload) {
            this.delivered = payload.clone();
            this.hit = true;
        }
    }

    // ---- 1 登记→转发→送达：命中 A->relay->B，逐字节一致 ----

    private static void registerForwardDeliver() {
        RelayForwarder relay = new RelayForwarder(id(0xA11CE));
        NodeId srcA = id(0x51A7E);
        CaptureEndpoint ep = capturing();
        relay.register("endpoint-b", ep);
        byte[] msg = payload(0x55AA, 64);
        ForwardResult res = relay.forward(new ForwardRequest(srcA, relay.relayId(), "endpoint-b", msg, 0));
        check("registered token forward returns FORWARDED (delivery commitment)", res.isForwarded());
        check("relay forwards the payload byte-for-byte to the memory endpoint",
                ep.hit && Arrays.equals(ep.delivered, msg));
    }

    // ---- 2 registeredTokens 确定性遍历序（登记乱序、遍历字典序） ----

    private static void registeredTokensOrdering() {
        RelayForwarder a = new RelayForwarder(id(0x0101));
        a.register("delta", bytes -> {});
        a.register("alpha", bytes -> {});
        a.register("charlie", bytes -> {});
        check("registeredTokens returns a deterministic lexicographic order",
                a.registeredTokens().equals(List.of("alpha", "charlie", "delta")));
        RelayForwarder b = new RelayForwarder(id(0x0102));
        b.register("delta", bytes -> {});
        b.register("alpha", bytes -> {});
        b.register("charlie", bytes -> {});
        check("registeredTokens traversal is identical across two relays (same registrations)",
                a.registeredTokens().equals(b.registeredTokens()));
    }

    // ---- 3 未登记 token → REJECTED（不静默，载荷不被投递） ----

    private static void unregisteredRejected() {
        RelayForwarder relay = new RelayForwarder(id(0xB0012));
        CaptureEndpoint ep = capturing();
        relay.register("known", ep);
        ForwardResult res = relay.forward(new ForwardRequest(
                id(0xC0DEC), relay.relayId(), "never-registered", b("x"), 0));
        check("unregistered token is explicitly REJECTED (not silent)", res.status() == ForwardResult.Status.REJECTED);
        check("unregistered-token reject carries the 'unregistered' reason",
                res.reason() != null && res.reason().contains("unregistered"));
        check("a rejected forward does not deliver any payload to the endpoint", !ep.hit);
    }

    // ---- 4 token 错配（目标 relay 不符）→ REJECTED ----

    private static void tokenMismatchRejected() {
        RelayForwarder relay = new RelayForwarder(id(0xA5A5A));
        CaptureEndpoint ep = capturing();
        relay.register("shared-token", ep);
        // 请求把 token 定向到另一个 relay（目标 relay 为别的节点）
        ForwardResult res = relay.forward(new ForwardRequest(
                id(0xF00D), id(0xDEADBEEF), "shared-token", b("y"), 0));
        check("mismatched relay for a registered token is explicitly REJECTED",
                res.status() == ForwardResult.Status.REJECTED);
        check("mismatch reject carries the 'mismatch' reason",
                res.reason() != null && res.reason().contains("mismatch"));
        check("a mismatched forward does not deliver to the endpoint", !ep.hit);
    }

    // ---- 5 hop 超限防环 → DROPPED(loop)，且同输入两次结果一致 ----

    private static void hopLimitLoopDropped() {
        RelayForwarder relay = new RelayForwarder(id(0xBA5E));
        CaptureEndpoint ep = capturing();
        relay.register("target", ep);
        ForwardRequest loop = new ForwardRequest(id(0x10A), relay.relayId(), "target", b("loop"), 4);
        ForwardResult r1 = relay.forward(loop);
        ForwardResult r2 = relay.forward(loop);
        check("over-hop forward is DROPPED (loop protection), not forwarded",
                r1.status() == ForwardResult.Status.DROPPED && !ep.hit);
        check("hop-limit drop carries the 'loop' reason",
                r1.reason() != null && r1.reason().contains("loop"));
        check("same over-hop input twice yields an identical DROPPED(loop) result",
                r1.equals(r2));
        // 恰好在限内（hop=MAX_HOPS）仍转发——防环不误杀边界
        ForwardResult ok = relay.forward(new ForwardRequest(id(0x10A), relay.relayId(), "target", b("ok"), 3));
        check("a hop at exactly the limit still forwards (loop guard only kills over-limit)",
                ok.isForwarded() && ep.hit);
    }

    // ---- 6 unregister 后 → REJECTED ----

    private static void unregisterRejects() {
        RelayForwarder relay = new RelayForwarder(id(0xE11E));
        CaptureEndpoint ep = capturing();
        relay.register("temp", ep);
        ForwardRequest req = new ForwardRequest(id(0x27), relay.relayId(), "temp", b("z"), 0);
        check("forward succeeds while registered (baseline before unregister)",
                relay.forward(req).isForwarded());
        relay.unregister("temp");
        ForwardResult res = relay.forward(req);
        check("after unregister the same forward is REJECTED (unregistered)", res.status() == ForwardResult.Status.REJECTED);
        check("registeredCount drops to 0 after unregistering the only token",
                relay.registeredCount() == 0);
    }

    // ---- 7 shutdown 关停：清空登记表，此后转发 REJECTED ----

    private static void shutdownClears() {
        RelayForwarder relay = new RelayForwarder(id(0x5EED));
        relay.register("t1", bytes -> {});
        relay.register("t2", bytes -> {});
        relay.register("t3", bytes -> {});
        int cleared = relay.shutdown();
        check("shutdown() clears all registrations (returns the cleared count)", cleared == 3);
        check("after shutdown registeredCount is 0", relay.registeredCount() == 0);
        ForwardResult res = relay.forward(new ForwardRequest(
                id(0x77), relay.relayId(), "t1", b("after"), 0));
        check("a forward after shutdown is REJECTED (registry emptied)", res.status() == ForwardResult.Status.REJECTED);
    }

    // ---- 8 与 P2pChannel 串联：A send → relay 解析 → B receive 逐字节一致（含 FrameV2 篡改拒收） ----

    private static void p2pChannelLinkage() {
        NodeId relayId = id(0x8147);
        NodeId srcA = id(0xA47A);
        byte[] msg = b("frame-level p2p transmission forwarded via voluntary relay");
        byte[] frame = new P2pChannel(0).send(msg).get(0);

        RelayForwarder relay = new RelayForwarder(relayId);
        P2pChannel bChannel = new P2pChannel(0);
        final ChannelResult[] bResult = {null};
        relay.register("b-rendezvous", payload -> bResult[0] = bChannel.receive(payload));
        ForwardResult res = relay.forward(new ForwardRequest(srcA, relay.relayId(), "b-rendezvous", frame, 0));
        check("relay forward of a frame-payload returns FORWARDED", res.isForwarded());
        check("B's P2pChannel receive via relay delivers the message byte-identical",
                bResult[0] != null && bResult[0].delivers(msg));

        // 沿用 FrameV2：经 relay 转发的帧若在传输中被篡改（翻转一个载荷字节），B 端强完整性拒收。
        byte[] tampered = frame.clone();
        int extLen = ((tampered[8] & 0xFF) << 8) | (tampered[9] & 0xFF);
        tampered[10 + extLen] ^= 0x01;
        RelayForwarder relay2 = new RelayForwarder(relayId);
        P2pChannel b2 = new P2pChannel(0);
        final ChannelResult[] tamperResult = {null};
        relay2.register("b-rendezvous", payload -> tamperResult[0] = b2.receive(payload));
        relay2.forward(new ForwardRequest(srcA, relayId, "b-rendezvous", tampered, 0));
        check("a tampered frame forwarded via relay is REJECTED by B (FrameV2 strong integrity)",
                tamperResult[0] != null && tamperResult[0].status() == ChannelResult.Status.REJECT);
    }

    // ---- 9 确定性：同 seed 两次 → 结果序列一致 ----

    private static void determinism() {
        String s1 = relayScenario(0xC4FE10A2L, id(0x9E1A));
        String s2 = relayScenario(0xC4FE10A2L, id(0x9E1A));
        check("two same-seed forward scenarios produce an identical result sequence", s1.equals(s2));
        String d1 = relayScenario(0x1111L, id(0x2222));
        String d2 = relayScenario(0x3333L, id(0x4444));
        check("a different seed/relay produces a distinguishable scenario (non-trivial determinism)",
                !d1.equals(s2) && !d1.equals(d2));
    }

    private static String relayScenario(long seed, NodeId relayId) {
        RelayForwarder relay = new RelayForwarder(relayId);
        RelayEndpoint sink = p -> { };
        relay.register("tok-a", sink);
        relay.register("tok-b", sink);
        relay.register("tok-c", sink);
        relay.register("ghost", sink);
        Random pr = new Random(seed);
        NodeId src = NodeId.random(pr);
        // 固定四步：命中 token / 未登记 token / over-hop（DROPPED loop）/ 错配 relay。
        ForwardResult[] steps = {
                relay.forward(step(src, relayId, "tok-b", pr, 1)),
                relay.forward(step(src, relayId, "tok-zz", pr, 0)),       // unregistered
                relay.forward(step(src, relayId, "tok-a", pr, 5)),        // over-hop -> DROPPED
                relay.forward(step(src, id(0x0BAD), "tok-b", pr, 0)),     // mismatch
        };
        StringBuilder sb = new StringBuilder();
        for (ForwardResult r : steps) {
            sb.append(r.status()).append('[').append(r.reason()).append(']').append(';');
        }
        return sb.toString();
    }

    private static ForwardRequest step(NodeId src, NodeId relayId, String token, Random pr, int hop) {
        byte[] p = new byte[8 + pr.nextInt(24)];
        pr.nextBytes(p);
        return new ForwardRequest(src, relayId, token, p, hop);
    }

    public static void main(String[] args) {
        registerForwardDeliver();
        registeredTokensOrdering();
        unregisteredRejected();
        tokenMismatchRejected();
        hopLimitLoopDropped();
        unregisterRejects();
        shutdownClears();
        p2pChannelLinkage();
        determinism();

        if (failures == 0) {
            System.out.println("[P2pRelayProbe] PASS: p.2.5.8 voluntary relay fallback + MC shell hook ("
                    + checks + " checks)");
            System.exit(0);
        } else {
            System.out.println("[P2pRelayProbe] FAIL: " + failures + " failure(s) of " + checks);
            System.exit(1);
        }
    }
}
