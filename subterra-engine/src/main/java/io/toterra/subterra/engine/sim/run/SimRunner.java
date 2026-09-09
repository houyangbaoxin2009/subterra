package io.toterra.subterra.engine.sim.run;

import io.toterra.subterra.engine.parallel.ParallelRunner;
import io.toterra.subterra.engine.parallel.TaskResult;

import java.util.Collection;
import java.util.List;

/**
 * The p.2.8 {@code engine.sim} run facade: a deliberately thin {@code SimRunner}
 * that consumes the p.2.7 {@code engine.parallel} key-agnostic dispatch/merge
 * pattern for arbitrary simulant key sets. It does not re-implement or copy any
 * dispatch/merge logic — it only delegates to a held {@link ParallelRunner}, whose
 * normalized bank count is the source of this facade's parallelism. Per-simulant
 * isolation + deterministic merge is therefore the default semantics for free:
 * for the same key set, {@link #run} (parallel) and {@link #runSerial} (serial
 * golden) are element-for-element, byte-for-byte identical; the merged output is
 * always in key natural order, independent of input order or bank timing.
 *
 * <p><b>Contract (passthrough, not re-implemented):</b> duplicate keys are
 * explicitly rejected with {@link IllegalArgumentException} on both paths (and the
 * rejected work is never invoked); the merge is in key order; parallelism is
 * deterministic. {@code fn} must be a pure function of the key (per-simulant
 * isolation; no shared mutable state). The facade mirrors the p.2.7
 * {@link ParallelRunner} method set 1:1, so nothing here constrains the key domain
 * beyond {@code K extends Comparable<K>}.
 *
 * <p>p.2.8 的 {@code engine.sim} 运行门面：一个刻意保持纤薄的 {@code SimRunner}，
 * 消费 p.2.7 {@code engine.parallel} 的键无关分发/归并范式来分派任意 simulant 键集。
 * 它绝不复制任何分发/归并逻辑——仅委托给内部持有的 {@link ParallelRunner}，其归一化
 * bank 数即本门面的并行度来源。per-simulant 隔离 + 确定性归并因此天然成为默认语义：
 * 对同一键集，{@link #run}（并行）与 {@link #runSerial}（串行金样）逐元素、逐字节一致；
 * 归并输出恒为 key 自然序，与输入序或 bank 时序无关。
 *
 * <p><b>契约（透传而非重实现）：</b> 两条路径对重复键均以 {@link IllegalArgumentException}
 * 显式拒绝（被拒 work 绝不调用）；归并为 key 序；并行度确定。{@code fn} 必须是对 key 的
 * 纯函数（per-simulant 隔离，禁共享易变状态）。门面与 p.2.7 {@link ParallelRunner}
 * 方法集 1:1 镜像，因此除 {@code K extends Comparable<K>} 外不额外约束键域。
 */
public final class SimRunner {

    private final ParallelRunner delegate;

    private SimRunner(ParallelRunner delegate) {
        this.delegate = delegate;
    }

    /**
     * Creates a sim-run facade over a {@link ParallelRunner} whose bank count is
     * normalized from {@code requestedParallelism} (see that type's contract:
     * {@code <= 0} yields one bank; values are power-of-two-clamped to the p.2.7
     * upper bound).
     *
     * @param requestedParallelism the desired parallelism ({@code <= 0} yields 1 bank).
     * @return a new {@link SimRunner}.
     */
    public static SimRunner create(int requestedParallelism) {
        return new SimRunner(new ParallelRunner(requestedParallelism));
    }

    /**
     * Runs the simulant keys in parallel through the p.2.7 deterministic
     * dispatcher and returns the results merged in key natural order. Duplicate
     * keys are explicitly rejected with {@link IllegalArgumentException}
     * (contract passthrough; the rejected function call never happens). Parallel
     * deterministic — byte-for-byte identical to {@link #runSerial} for the same
     * key set.
     *
     * @param keys the simulant keys to process (duplicates rejected).
     * @param fn   the per-simulant pure function.
     * @return the merged results in key natural order.
     */
    public <K extends Comparable<K>, R> List<TaskResult<K, R>> run(Collection<K> keys, ParallelRunner.KeyFunction<K, R> fn) {
        return delegate.run(keys, fn);
    }

    /**
     * The serial golden path: runs {@code fn} over the keys in natural order,
     * byte-for-byte identical to {@link #run}. Rejects duplicates upfront.
     *
     * @param keys the simulant keys to process (duplicates rejected).
     * @param fn   the per-simulant pure function.
     * @return the results in key natural order.
     */
    public <K extends Comparable<K>, R> List<TaskResult<K, R>> runSerial(Collection<K> keys, ParallelRunner.KeyFunction<K, R> fn) {
        return delegate.runSerial(keys, fn);
    }

    /**
     * @return the normalized bank count in use (transparent passthrough).
     */
    public int bankCount() {
        return delegate.bankCount();
    }
}