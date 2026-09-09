package io.toterra.subterra.engine.network.strategy;

import io.toterra.subterra.engine.network.strategy.FieldSnapshot.Value;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * 增量差分（p.2.4.3）：对两个 {@link FieldSnapshot}（base → next）求
 * {@code DiffResult = { List<DiffOp> ops（每个变更/新增/删除键都有一条，按 key 字典序确定排列）,
 * counts { added, changed, removed, same } }}。等价输入 → 恒定输出（确定性）。重放：
 * {@code apply(base, ops)} 得到与 next 完全一致的快照（往返契约，探针中验证）。
 * 全流程线性：键并集一次遍历 + TreeMap/TreeSet 建序，无 O(n²)。
 * <p>
 * Incremental differencing (p.2.4.3): between two {@link FieldSnapshot}s (base → next) computes
 * {@code DiffResult = { List<DiffOp> ops (one per changed/added/removed key, deterministically ordered
 * by key), counts { added, changed, removed, same } }}. Equal inputs → equal output (deterministic).
 * Replay: {@code apply(base, ops)} reproduces a snapshot exactly equal to next (the round-trip
 * contract verified by the probe). Fully linear: one pass over the key union plus Tree-based ordering,
 * no O(n²).
 */
public final class IncrementalDiff {

    private IncrementalDiff() {
    }

    /** 变更统计。Change counts. */
    public record Counts(int added, int changed, int removed, int same) {
        /** 参与统计的全部操作数（added+changed+removed）。Total op count (added+changed+removed). */
        public int opTotal() {
            return added + changed + removed;
        }
    }

    /** 差分结果：有序操作列表 + 统计。Diff result: ordered op list + counts. */
    public record Result(List<DiffOp> ops, Counts counts) {
    }

    /**
     * 计算 base → next 的确定性差分。Compute the deterministic diff base → next.
     *
     * @return 按 key 字典序排列的操作列表与统计；无变化时 ops 为空、counts 全 same。
     *         Ops ordered by key; empty ops and all-same counts when unchanged.
     */
    public static Result diff(FieldSnapshot base, FieldSnapshot next) {
        TreeSet<String> keys = new TreeSet<>();
        keys.addAll(base.sortedKeys());
        keys.addAll(next.sortedKeys());

        List<DiffOp> ops = new ArrayList<>();
        int added = 0;
        int changed = 0;
        int removed = 0;
        int same = 0;

        for (String k : keys) {
            Value a = base.value(k);
            Value b = next.value(k);
            if (a == null) {
                added++;
                ops.add(DiffOp.set(k, b)); // b 必非 null（键在并集内，base 无则 next 有）
            } else if (b == null) {
                removed++;
                ops.add(DiffOp.unset(k));
            } else if (a.equals(b)) {
                same++;
            } else {
                changed++;
                ops.add(DiffOp.set(k, b));
            }
        }
        return new Result(List.copyOf(ops), new Counts(added, changed, removed, same));
    }

    /**
     * 重放：将 ops 依序应用到 base，得到末态快照（可看作与 next 的往返对照）。Replay: apply ops in
     * order onto base, yielding the destination snapshot (the round-trip counterpart of next).
     */
    public static FieldSnapshot apply(FieldSnapshot base, List<DiffOp> ops) {
        Map<String, Value> m = new TreeMap<>();
        for (Map.Entry<String, Value> e : base.entries()) {
            m.put(e.getKey(), e.getValue());
        }
        for (DiffOp op : ops) {
            if (op.kind() == DiffOp.Kind.SET) {
                m.put(op.key(), op.value());
            } else {
                m.remove(op.key());
            }
        }
        return FieldSnapshot.normalize(m);
    }
}