package io.toterra.subterra.engine.p2p.discovery;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import io.toterra.subterra.engine.p2p.NodeAddr;
import io.toterra.subterra.engine.p2p.NodeId;

/**
 * KademliaLite —— p.2.5.7「DHT 无中心发现」的确定性纯 JDK 核心。只做确定性 id 空间 + 路由表
 * /bucket + 入桶 / 自分裂 + 固定样本查询路由的纯数据结构核心；<b>不实现 DHT 网络协议 / 消息
 * 编解码、不建 socket</b>（网络往返 / RPC 留给 runtime 壳）。无中心服务器、无账号依赖：
 * 每个节点以自身 {@code localId} 为锚，靠 XOR 距离自组织出一棵路由前缀树。
 * <p>
 * A deterministic pure-JDK core for decentralised discovery: deterministic id space + routing
 * table / k-buckets + insert / self-split + fixed-sample query routing. <b>No DHT wire protocol,
 * no message codec, no socket.</b> Each node anchors on its {@code localId} and organises a routing
 * prefix tree purely by XOR distance.
 *
 * <p><b>键空间与距离约定（convention）</b>：键空间取 {@value BITS} bit（NodeId 16B=128bit）。
 * `distance(a,b)=a.xor(b)`（逐字节位异或）。字节序定死为<b>大端（big-endian）</b>：字节 0 为
 * 最高有效位；XOR 距离的两两比较按「字节 0 → 字节 15」的字典序（做无符号字节比较）。路由表
 * 分层：bucket 深度 =「localId 与 id 最高不同 bit 的位置」（MSB=0 计），即共享前缀越长的 id 落在
 * 越深的 bucket。所有判定均基于该位序，故同 seed 同插入序 → 同结构。
 *
 * <p><b>自分裂（self-split）</b>：任一层满容且「localId 落于该 bucket 范围」时，插入触发该 bucket
 * 分裂成两个深度 +1 的半区并重分配既有条目；分裂只发生在含 localId 的自分支上（Kademlia 经典
 * 规则的最简形态），深度封顶 {@value BITS}（此时范围缩到单点 localId，无可分裂）。非自 bucket
 * 满容则按固定规则拒绝新入（保证确定性）。
 *
 * <p><b>哈希锚点</b>：键去重 / 分桶不以本类自造哈希 —— 上层如需哈希可直接采用引擎既有的
 * p.2.4.2 便携实现 {@code io.toterra.subterra.engine.network.integrity.Tsha1f}
 * （{@code digestHex("f", bytes, n)}）；本类本身只用 NodeId 自带字节与 XOR，不依赖哈希。
 *
 * <p><b>查询路由语义</b>：{@code routeTo} 返回按 XOR 距离升序的完整有序候选清单；{@code lookup}
 * 取其中就近 α 个并给出命中标志。上层可据此做经典迭代式 find_node（本子项只提供纯函数，不写
 * RPC）。地址仅登记在候选上，实际连通性交由 AddressBook / 上层判断。
 *
 * <p>纯 JDK，无 MC 依赖（engine 层铁律）。全操作确定、单线程无共享可变状态。
 */
public final class KademliaLite {

    /** 默认 bucket 容量（k）。Default bucket capacity (k). */
    public static final int DEFAULT_BUCKET_SIZE = 20;

    /** 默认查询并发度 α。Default lookup parallelism α. */
    public static final int DEFAULT_ALPHA = 3;

    /** 键空间位数 = NodeId 16B × 8。Key-space bits = NodeId 16B × 8. */
    public static final int BITS = NodeId.BYTES * 8;

    private static final Comparator<RouteCandidate> HEX_ORDER =
            Comparator.comparing(c -> c.nodeId().hex());

    private final NodeId localId;
    private final byte[] localBytes;
    private final int bucketSize;
    private final int alpha;
    private final Bucket root = new Bucket(0, new byte[NodeId.BYTES]);

    /**
     * 由本地节点身份 + bucket 容量构造（α 用默认 3）。
     * Builds with a local identity and a bucket capacity (α defaults to 3).
     */
    public KademliaLite(NodeId localId, int bucketSize) {
        this(localId, bucketSize, DEFAULT_ALPHA);
    }

    /**
     * 由本地节点身份 + bucket 容量 + 查询并发度 α 构造。可注入的本地 id / k / α。
     * Builds with an injectable local identity, bucket capacity and lookup parallelism α.
     */
    public KademliaLite(NodeId localId, int bucketSize, int alpha) {
        if (localId == null) {
            throw new NullPointerException("localId");
        }
        if (bucketSize <= 0) {
            throw new IllegalArgumentException("bucketSize must be positive: " + bucketSize);
        }
        if (alpha <= 0) {
            throw new IllegalArgumentException("alpha must be positive: " + alpha);
        }
        this.localId = localId;
        this.localBytes = localId.bytes();
        this.bucketSize = bucketSize;
        this.alpha = alpha;
    }

    /**
     * 确定性插入。若 id 已存在则更新其地址（不新增、返回 true）；若目标 bucket 满容且不可分裂
     * （非自 bucket）则按固定规则拒绝并返回 false；如果满容 bucket 落在含 localId 的自分支上则
     * 分裂后重插。返回「是否新增/更新成功」。
     * <p>
     * Deterministic insert. An existing id updates its address (returns true); an insert into a
     * full non-self bucket is rejected by the fixed rule (returns false); a full bucket that falls
     * on the self branch containing localId is split first and the insert retried.
     */
    public boolean insert(NodeId id, NodeAddr addr) {
        if (id == null || addr == null) {
            throw new NullPointerException("id and addr must not be null");
        }
        return insertRec(root, id, addr);
    }

    /**
     * 就近 α 个候选 + 命中标志。命中仅当目标确实在表中。升序按 XOR 距离。
     * The nearest α candidates plus a hit flag; hit is true only when the target is present.
     */
    public KademliaResult lookup(NodeId target) {
        if (target == null) {
            throw new NullPointerException("target");
        }
        List<RouteCandidate> ordered = routeTo(target);
        int n = Math.min(alpha, ordered.size());
        List<RouteCandidate> top = new ArrayList<>(ordered.subList(0, n));
        boolean hit = false;
        for (RouteCandidate c : top) {
            if (c.nodeId().equals(target)) {
                hit = true;
                break;
            }
        }
        return new KademliaResult(Collections.unmodifiableList(top), hit);
    }

    /**
     * 查询路由纯函数：返回按 XOR 距离升序的有序候选清单（排除本地节点自身 localId，无重复），
     * 供上层做迭代式 find_node。RPC 语义不做，只给有序清单。
     * <p>
     * Query-routing pure function: the candidates in ascending XOR distance (excluding the local
     * node itself, no duplicates), for upper layers to drive iterative find_node. No RPC semantics.
     */
    public List<RouteCandidate> routeTo(NodeId target) {
        if (target == null) {
            throw new NullPointerException("target");
        }
        List<RouteCandidate> all = new ArrayList<>(knownNodes());
        all.removeIf(c -> c.nodeId().equals(localId));
        all.sort((x, y) -> compareXor(x.nodeId(), y.nodeId(), target));
        return Collections.unmodifiableList(all);
    }

    /** 已分裂出的叶子 bucket 数。当前路由树的叶数（初值 1，自分裂后递增）。 */
    public int bucketCount() {
        return countLeaf(root);
    }

    /** 已知节点总数（成功入库的去重 count）。Total number of distinct stored nodes. */
    public int knownNodeCount() {
        return countKnown(root);
    }

    /** 确定性遍历序的已知节点（按 NodeId hex 字典序，只读）。Deterministic hex-ordered nodes. */
    public List<RouteCandidate> knownNodes() {
        List<RouteCandidate> out = new ArrayList<>();
        collect(root, out);
        out.sort(HEX_ORDER);
        return Collections.unmodifiableList(out);
    }

    /**
     * 结构签名：各叶子 bucket 的深度（升序、只读）。用于校验「同 seed 同插入序 → 同结构」与
     * 「不同 localId → 自分裂差异」——节点集合可能一致，但 bucket 分裂形态随之反映出来。
     * A structural signature: every leaf bucket depth, ascending, read-only. Makes self-splitting
     * differences observable even when the known-node set is identical.
     */
    public List<Integer> bucketDepthProfile() {
        List<Integer> out = new ArrayList<>();
        collectDepths(root, out);
        out.sort(Integer::compareTo);
        return Collections.unmodifiableList(out);
    }

    /** 本地节点身份。Local node identity. */
    public NodeId localId() {
        return localId;
    }

    /**
     * XOR 距离比较器（大端字典序）：返回负/零/正，等价于 compare( |xor(a,target)|, |xor(b,target)| )。
     * NodeId 内 16 字节按字节 0(MSB) → 15(LSB) 逐个比较。暴露给上层，保证约定单一来源。
     */
    public static int compareXor(NodeId a, NodeId b, NodeId target) {
        byte[] ta = a.bytes();
        byte[] tb = b.bytes();
        byte[] tt = target.bytes();
        for (int i = 0; i < NodeId.BYTES; i++) {
            int da = (ta[i] ^ tt[i]) & 0xFF;
            int db = (tb[i] ^ tt[i]) & 0xFF;
            if (da != db) {
                return Integer.compare(da, db);
            }
        }
        return 0;
    }

    // ---- internal tree ----

    private boolean insertRec(Bucket b, NodeId id, NodeAddr addr) {
        if (!b.leaf) {
            Bucket child = bitAt(id.bytes(), b.depth) == 0 ? b.left : b.right;
            return insertRec(child, id, addr);
        }
        // leaf: update-or-append
        for (int i = 0; i < b.entries.size(); i++) {
            if (b.entries.get(i).nodeId().equals(id)) {
                b.entries.set(i, new RouteCandidate(id, addr)); // update address
                return true;
            }
        }
        if (b.entries.size() < bucketSize) {
            b.entries.add(new RouteCandidate(id, addr));
            return true;
        }
        // full: split only if this leaf is the self branch (contains localId) and still splittable
        if (b.depth < BITS && containsId(b, localBytes)) {
            split(b);
            return insertRec(b, id, addr); // b is now internal -> reroute
        }
        return false; // fixed deterministic reject for a full non-self bucket
    }

    private void split(Bucket b) {
        Bucket left = new Bucket(b.depth + 1, setBit(b.prefix, b.depth, 0));
        Bucket right = new Bucket(b.depth + 1, setBit(b.prefix, b.depth, 1));
        for (RouteCandidate e : b.entries) {
            (containsId(left, e.nodeId().bytes()) ? left : right).entries.add(e);
        }
        b.leaf = false;
        b.left = left;
        b.right = right;
        b.entries.clear();
    }

    private static int countLeaf(Bucket b) {
        if (b.leaf) {
            return 1;
        }
        return countLeaf(b.left) + countLeaf(b.right);
    }

    private static int countKnown(Bucket b) {
        if (b.leaf) {
            return b.entries.size();
        }
        return countKnown(b.left) + countKnown(b.right);
    }

    private static void collect(Bucket b, List<RouteCandidate> out) {
        if (b.leaf) {
            out.addAll(b.entries);
            return;
        }
        collect(b.left, out);
        collect(b.right, out);
    }

    private static void collectDepths(Bucket b, List<Integer> out) {
        if (b.leaf) {
            out.add(b.depth);
            return;
        }
        collectDepths(b.left, out);
        collectDepths(b.right, out);
    }

    /** 全局位序：p 为 0..BITS-1（MSB 优先，字节 0 = 最高有效位）。 */
    private static int bitAt(byte[] b, int p) {
        return (b[p >>> 3] >>> (7 - (p & 7))) & 1;
    }

    /** 前缀数组的副本并把第 depth 位设为 val。Copies prefix and sets bit depth to val. */
    private static byte[] setBit(byte[] prefix, int depth, int val) {
        byte[] copy = prefix.clone();
        int byteIdx = depth >>> 3;
        int bitIdx = 7 - (depth & 7);
        if (val == 0) {
            copy[byteIdx] &= (byte) ~(1 << bitIdx);
        } else {
            copy[byteIdx] |= (byte) (1 << bitIdx);
        }
        return copy;
    }

    /** id 是否落在 bucket b 的 (depth 位) 前缀范围内。Whether id falls in bucket b's prefix range. */
    private static boolean containsId(Bucket b, byte[] idBytes) {
        for (int p = 0; p < b.depth; p++) {
            if (bitAt(idBytes, p) != bitAt(b.prefix, p)) {
                return false;
            }
        }
        return true;
    }

    /** 叶子/内部节点。Leaf buckets are the k-buckets; internal nodes only re-route. */
    private static final class Bucket {
        final int depth;
        final byte[] prefix;
        final List<RouteCandidate> entries = new ArrayList<>();
        Bucket left;
        Bucket right;
        boolean leaf = true;

        Bucket(int depth, byte[] prefix) {
            this.depth = depth;
            this.prefix = prefix;
        }
    }
}