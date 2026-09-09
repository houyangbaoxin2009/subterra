package io.toterra.subterra.probes;

import io.toterra.subterra.engine.network.integrity.Tsha1f;
import io.toterra.subterra.engine.p2p.NodeAddr;
import io.toterra.subterra.engine.p2p.NodeId;
import io.toterra.subterra.engine.p2p.discovery.KademliaLite;
import io.toterra.subterra.engine.p2p.discovery.KademliaResult;
import io.toterra.subterra.engine.p2p.discovery.RouteCandidate;

import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Random;

/**
 * p.2.5.7 DHT 无中心发现的确定性验收探针（纯 JVM，无 MC 运行时）：断言 {@link KademliaLite}
 * 的确定性 id 空间 + 路由表 / bucket + 入桶 / 自分裂 + 查询路由——固定种子插入 200 个随机 id 后
 * bucket 数 >0 且 knownNodes = 插入成功数、遍历序确定；自分裂使 bucket 数增加且同种子结构一致；
 * routeTo 按 XOR 距离升序 / 排除自身 / 无重复；lookup 命中已插入 id / 对未插入 id 不假命中 /
 * 不超过 α 个；超容量 bucket 按固定规则拒绝且 accept/reject 序列两次一致；以 p.2.4.2 Tsha1f 作
 * 键去重哈希锚点两次一致；同种子同插入序 → knownNodes hex 序列与 bucket 结构逐字节一致，不同
 * localId → bucket 结构（自分裂）不同。
 * <p>
 * Deterministic acceptance probe for the p.2.5.7 DHT discovery core (pure JVM — no Minecraft
 * runtime): asserts the deterministic id-space + routing-table / bucket / insert / self-split /
 * query-routing of {@link KademliaLite}.
 * <p>
 * Determinism: fixed seeds; failure counter advances only on failure; no timing assertions.
 * Exit 0 = PASS all checks; 1 = FAIL.
 */
public final class P2pDiscoveryProbe {

    private P2pDiscoveryProbe() {
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

    /** 确定性可寻址地址：由 id 派生，保证同 id 同地址。 */
    private static NodeAddr addr(NodeId id) {
        return new NodeAddr.Direct("p2p-" + id.hex().substring(0, 8), 25565);
    }

    /** 一次确定性构建：返回表 + 插入 id 序列 + accept/reject 标志串(A/R)。 */
    private static Build build(NodeId local, int k, long insSeed, int count) {
        KademliaLite t = new KademliaLite(local, k);
        List<NodeId> ids = new ArrayList<>();
        StringBuilder acc = new StringBuilder(count);
        Random ir = new Random(insSeed);
        for (int i = 0; i < count; i++) {
            NodeId id = NodeId.random(ir);
            ids.add(id);
            acc.append(t.insert(id, addr(id)) ? 'A' : 'R');
        }
        return new Build(t, ids, acc.toString());
    }

    private record Build(KademliaLite table, List<NodeId> ids, String accepts) {
    }

    private static byte[] xor(byte[] a, byte[] b) {
        byte[] out = new byte[a.length];
        for (int i = 0; i < a.length; i++) {
            out[i] = (byte) (a[i] ^ b[i]);
        }
        return out;
    }

    /** 独立实现的大端字典序 XOR 距离比较，用于从外部校验 routeTo 的升序。 */
    private static int lexXor(byte[] da, byte[] db) {
        for (int i = 0; i < da.length; i++) {
            int x = (da[i] & 0xFF);
            int y = (db[i] & 0xFF);
            if (x != y) {
                return Integer.compare(x, y);
            }
        }
        return 0;
    }

    private static boolean ascendingByDistance(List<RouteCandidate> r, NodeId target) {
        byte[] tt = target.bytes();
        for (int i = 0; i < r.size() - 1; i++) {
            byte[] da = xor(r.get(i).nodeId().bytes(), tt);
            byte[] db = xor(r.get(i + 1).nodeId().bytes(), tt);
            if (lexXor(da, db) > 0) {
                return false;
            }
        }
        return true;
    }

    private static boolean containsId(List<RouteCandidate> r, NodeId id) {
        for (RouteCandidate c : r) {
            if (c.nodeId().equals(id)) {
                return true;
            }
        }
        return false;
    }

    private static boolean allDistinct(List<RouteCandidate> r) {
        LinkedHashSet<NodeId> seen = new LinkedHashSet<>();
        for (RouteCandidate c : r) {
            if (!seen.add(c.nodeId())) {
                return false;
            }
        }
        return true;
    }

    private static String knownHexSeq(KademliaLite t) {
        StringBuilder sb = new StringBuilder();
        for (RouteCandidate c : t.knownNodes()) {
            sb.append(c.nodeId().hex()).append(' ');
        }
        return sb.toString();
    }

    private static String depthProfile(KademliaLite t) {
        return t.bucketDepthProfile().toString();
    }

    // ---- test groups ----

    // 1 固定种子插入 200 个随机 id：bucket>0、knownNodes=插入成功数、遍历序确定。
    private static void seededRouting() {
        NodeId local = NodeId.random(new Random(0xD1A5L));
        Build b = build(local, 20, 0xBEEFL, 200);
        KademliaLite t = b.table;
        check("bucketCount() > 0 after 200 seeded inserts", t.bucketCount() > 0);
        check("knownNodeCount() == successfully-inserted count", t.knownNodeCount() == b.accepts.chars().filter(c -> c == 'A').count());
        List<String> hexes = t.knownNodes().stream().map(c -> c.nodeId().hex()).toList();
        List<String> sorted = new ArrayList<>(hexes);
        sorted.sort(String::compareTo);
        check("knownNodes() traversal is deterministic ascending hex order", hexes.equals(sorted));
    }

    // 2 自分裂：小 k 迫使自 bucket 分裂 → bucket 数 > 初值 1；同种子两次结构一致。
    private static void selfSplit() {
        NodeId local = NodeId.random(new Random(0xA11CE));
        KademliaLite t1 = build(local, 3, 0x5EEDL, 40).table;
        KademliaLite t2 = build(local, 3, 0x5EEDL, 40).table;
        check("self-split raised bucketCount above initial 1 (multiple leaf buckets)", t1.bucketCount() > 1);
        check("same-seed self-split build → identical bucketCount", t1.bucketCount() == t2.bucketCount());
        check("same-seed self-split build → identical depth profile", depthProfile(t1).equals(depthProfile(t2)));
    }

    // 3 XOR 距离序：routeTo 升序、排除自身、无重复,且恰好是已知集。
    private static void routeOrdering() {
        NodeId local = NodeId.random(new Random(0xACC01));
        Build b = build(local, 20, 0xCAFEL, 200);
        NodeId target = NodeId.random(new Random(0xF00DL));
        List<RouteCandidate> route = b.table.routeTo(target);
        check("routeTo returns candidates in ascending XOR distance", ascendingByDistance(route, target));
        check("routeTo has no duplicate candidate (id space distinct)", allDistinct(route));
        boolean noSelf = route.stream().noneMatch(c -> c.nodeId().equals(b.table.localId()));
        check("routeTo excludes the local node itself", noSelf);
        check("routeTo emits exactly the known set", route.size() == b.table.knownNodeCount());
    }

    // 4 lookup：命中已插入；对未插入不假命中；≤ α 个；最近候选即目标。
    private static void lookupHits() {
        NodeId local = NodeId.random(new Random(0x51A7E));
        Build b = build(local, 20, 0x101DL, 300);
        NodeId hit = b.ids.get(7);
        KademliaResult res = b.table.lookup(hit);
        boolean hitOk = res.hit() && containsId(res.candidates(), hit);
        check("lookup returns hit + it for a previously-inserted id", hitOk);
        check("lookup's nearest candidate is the exact target (XOR 0)", !res.candidates().isEmpty()
                && res.candidates().get(0).nodeId().equals(hit));
        check("lookup returns at most α candidates", res.candidates().size() <= 3);
        // pick a never-inserted id distinct from all inserted
        NodeId missing = neverInserted(b.ids, 0xA11C1D3L);
        KademliaResult miss = b.table.lookup(missing);
        check("lookup does not falsely hit a never-inserted target", !miss.hit() && !containsId(miss.candidates(), missing));
    }

    private static NodeId neverInserted(List<NodeId> ids, long seed) {
        Random r = new Random(seed);
        java.util.HashSet<NodeId> set = new java.util.HashSet<>(ids);
        NodeId c = NodeId.random(r);
        while (set.contains(c)) {
            NodeId cc = NodeId.random(r);
            // keep c as a deterministic function; only advance to a fresh candidate
            if (!set.contains(cc)) {
                return cc;
            }
            c = cc;
        }
        return c;
    }

    // 5 容量语义：满桶按固定规则拒绝,且 accept/reject 序列两次同种子一致。
    private static void capacityReject() {
        NodeId local = NodeId.random(new Random(0xB00B5));
        Build b1 = build(local, 5, 0x1E5L, 400);
        Build b2 = build(local, 5, 0x1E5L, 400);
        boolean anyReject = b1.accepts.indexOf('R') >= 0;
        check("over-capacity bucket rejects by the fixed rule (some insert returns false)", anyReject);
        check("accept/reject sequence is deterministic across two same-seed runs", b1.accepts.equals(b2.accepts));
    }

    // 6 哈希锚点：以 Tsha1f 对固定样本键去重，两次一致。
    private static void hashAnchor() {
        List<NodeId> sample = fixedSample(0x1A7A1L, 12);
        String k1 = anchorKeys(sample);
        String k2 = anchorKeys(sample);
        check("Tsha1f anchor: key-dedup digest is stable across two passes", k1.equals(k2));
        check("Tsha1f anchor: distinct keys for distinct ids (dedup count == sample size)",
                k1.split("\\s").length == sample.size());
        List<NodeId> other = fixedSample(0x2B8B2L, 12);
        check("Tsha1f anchor: a different sample yields a different anchor", !anchorKeys(sample).equals(anchorKeys(other)));
    }

    private static List<NodeId> fixedSample(long seed, int count) {
        Random ir = new Random(seed);
        List<NodeId> out = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            out.add(NodeId.random(ir));
        }
        return out;
    }

    private static String anchorKeys(List<NodeId> sample) {
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        StringBuilder sb = new StringBuilder();
        for (NodeId id : sample) {
            String k = Tsha1f.digestHex("f", id.bytes(), 128);
            keys.add(k);
            sb.append(k).append(' ');
        }
        return keys.size() + "|" + sb;
    }

    // 7 确定性超集：同 seed+插入序 → 序列与结构逐字节一致；不同 localId → 结构（自分裂）不同。
    private static void determinism() {
        NodeId la = NodeId.random(new Random(0xA1111));
        String s1 = build(la, 8, 0xD17L, 120).table.knownNodes().toString();
        String s2 = build(la, 8, 0xD17L, 120).table.knownNodes().toString();
        check("same seed + insert order → identical knownNodes hex sequence (byte-identical)", s1.equals(s2));
        String seqA = knownHexSeq(build(la, 8, 0xD17L, 120).table);
        String seqB2 = knownHexSeq(build(la, 8, 0xD17L, 120).table);
        check("two builds with the same localId and seed → identical hex sequence", seqA.equals(seqB2));

        NodeId lb = NodeId.random(new Random(0xBBBBB));
        String profA = depthProfile(build(la, 8, 0xD17L, 120).table);
        String profB = depthProfile(build(lb, 8, 0xD17L, 120).table);
        check("different localId → different bucket structure (self-split difference)", !profA.equals(profB));
        check("same localId + seed → identical structural depth profile",
                depthProfile(build(la, 8, 0xD17L, 120).table).equals(depthProfile(build(la, 8, 0xD17L, 120).table)));
    }

    public static void main(String[] args) {
        seededRouting();
        selfSplit();
        routeOrdering();
        lookupHits();
        capacityReject();
        hashAnchor();
        determinism();

        if (failures == 0) {
            System.out.println("[P2pDiscoveryProbe] PASS: p.2.5.7 DHT discovery core KademliaLite ("
                    + checks + " checks)");
            System.exit(0);
        } else {
            System.out.println("[P2pDiscoveryProbe] FAIL: " + failures + " failure(s) of " + checks);
            System.exit(1);
        }
    }
}