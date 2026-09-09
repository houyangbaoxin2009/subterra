package io.toterra.subterra.engine.network.strategy;

import java.util.List;
import java.util.Objects;

/**
 * 有序变更流（p.2.4.3）：一段单调递增 seq（1..n，即列表下标+1）的 {@link DiffOp} 序列，外加一个末端
 * 快照。{@link #replay(FieldSnapshot)} 依序应用全部 ops，从 base 重构出恰好等于末端快照的状态。
 * 构建时对 ops 列表做防御性拷贝，同输入恒得同流（确定性等值）。
 * 诚实而简单：只保存有序列表 + 重放函数，不做时序/时钟假设。
 * <p>
 * Ordered change stream (p.2.4.3): a run of {@link DiffOp}s with monotonically increasing seq
 * (1..n, i.e. list index + 1) plus a terminal snapshot. {@link #replay(FieldSnapshot)} applies all ops
 * in order, rebuilding a state exactly equal to the terminal snapshot. The op list is defensively
 * copied on construction, so equal inputs always yield equal streams (deterministic equality).
 * Honest and simple: it only holds an ordered list plus a replay function, no timing/clock assumptions.
 */
public final class ChangeStream {

    private final List<DiffOp> ops;
    private final FieldSnapshot terminal;

    private ChangeStream(List<DiffOp> ops, FieldSnapshot terminal) {
        this.ops = List.copyOf(ops);
        this.terminal = Objects.requireNonNull(terminal, "terminal snapshot must not be null");
    }

    /** 构建变更流（拷贝 ops）。Build a change stream (copies the op list). */
    public static ChangeStream of(List<DiffOp> ops, FieldSnapshot terminal) {
        return new ChangeStream(ops, terminal);
    }

    /** 末端快照。Terminal snapshot. */
    public FieldSnapshot terminal() {
        return terminal;
    }

    /** 依序操作列表（seq = 下标+1，即 1..n）。Ordered op list (seq = index + 1, i.e. 1..n). */
    public List<DiffOp> ops() {
        return ops;
    }

    /** 给定 op 的 seq（1..n，下标+1）；不在流中则 0。Seq (1..n) of an op; 0 if not present. */
    public int seqOf(DiffOp op) {
        return ops.indexOf(op) >= 0 ? ops.indexOf(op) + 1 : 0;
    }

    /** 流长度。Stream length. */
    public int size() {
        return ops.size();
    }

    /**
     * 重放：从 base 依序应用全部 ops，重构出等于 {@link #terminal()} 的末态。Replay: apply all ops in
     * order to base, reconstructing the state equal to {@link #terminal()}.
     */
    public FieldSnapshot replay(FieldSnapshot base) {
        return IncrementalDiff.apply(base, ops);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ChangeStream other)) {
            return false;
        }
        return ops.equals(other.ops) && terminal.equals(other.terminal);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ops, terminal);
    }

    @Override
    public String toString() {
        return "ChangeStream{" + ops.size() + " ops -> " + terminal + '}';
    }
}