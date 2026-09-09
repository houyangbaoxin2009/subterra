package io.toterra.subterra.engine.p2p;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 无中心地址表现（p.2.5.2）：NodeId → 地址列表（一节点可多地址，存原始顺序，直连优先排序留给上层）。
 * Centreless address representation: node identifier → ordered list of addresses. Multiple
 * addresses per node are kept in insertion order; direct-first ordering is deferred to callers.
 * <p>
 * 内部以 {@link LinkedHashMap} 保持确定性；{@link #resolve} 对未知节点返回空 List（绝不 null）；
 * {@link #snapshot} 按 NodeId hex 字典序输出只读映射以保证确定性遍历。
 */
public final class AddressBook {

    private final LinkedHashMap<NodeId, List<NodeAddr>> entries = new LinkedHashMap<>();

    private static final Comparator<NodeId> HEX_ORDER = Comparator.comparing(NodeId::hex);

    /** 插入或追加一条 (id, addr) 关联。Adds/re-appends a single address for a node. */
    public void put(NodeId id, NodeAddr addr) {
        if (id == null || addr == null) {
            throw new NullPointerException("id and addr must not be null");
        }
        entries.computeIfAbsent(id, k -> new ArrayList<>()).add(addr);
    }

    /** 未知节点 → 空 List（绝不 null）。Unknown node → empty (never null) List. */
    public List<NodeAddr> resolve(NodeId id) {
        if (id == null) {
            return List.of();
        }
        List<NodeAddr> list = entries.get(id);
        return list == null ? List.of() : List.copyOf(list);
    }

    /** 已登记的节点条目数。Number of distinct known nodes. */
    public int size() {
        return entries.size();
    }

    /**
     * 按 NodeId hex 字典序排序的只读快照（确定性遍历）。
     * A read-only snapshot ordered by NodeId hex lexicographically (deterministic traversal).
     */
    public Map<NodeId, List<NodeAddr>> snapshot() {
        TreeMap<NodeId, List<NodeAddr>> out = new TreeMap<>(HEX_ORDER);
        for (Map.Entry<NodeId, List<NodeAddr>> e : entries.entrySet()) {
            out.put(e.getKey(), List.copyOf(e.getValue()));
        }
        return Collections.unmodifiableMap(out);
    }
}