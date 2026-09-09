// Async I/O queue design derives from C2ME (MIT, Copyright (c) ishland, https://github.com/ishland/C2ME);
// implementation is original Subterra code.
package io.toterra.subterra.engine.worldgen.async.io;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * 确定性有序刷新队列核心（p.2.6.4，纯 JDK，无 MC 导入）：把来自多个并发生产 bank 的写入收进
 * {@link AsyncWrite}，并在 {@link #flushAll()} 时以确定性顺序交给 sink（真正的写者）恰好一次。
 * sink 与存储栈解耦——本路径提供一个内存 sink，运行时 shell 稍后在 {@code -D} 门控下提供真实
 * 的 zd/save 写者。默认关闭（见 {@link #isEnabled()}），队列本身始终行为一致，仅当运行时 shell
 * 显式 {@link #enable()} 并选择把 vanilla I/O 走本队列时才有增强；默认下 vanilla 生命周期不受影响。
 *
 * <p><b>确定性顺序契约（关键）</b> {@link #enqueue} 在锁外通过单调递增 {@link AtomicLong} 原子
 * 地领取入队序号 seq（跨调用者全序、与并发无关）；{@link #flushAll} 把待刷新写按 seq 升序排列后
 * 依序调用 sink。由此同一组写"无论谁在哪台线程先入队"，刷新次序都恒定。这正是子项里程碑要的
 * 确定性合并：在"每个调用方持有自身目标文件集"的前提下，输出顺序确定。
 *
 * <p><b>Counting</b> {@link #pendingCount()} = 已接受未刷新数；{@link #flushedCount()} =
 * 累计已交给 sink 的数。一次成功 {@link #flushAll()} 后二者补集业绩 = 恰好一次。
 *
 * <p><b>Lifecycle</b> {@link #close()} 先刷新剩余写再把队列关闭；关闭后 {@link #enqueue} 抛
 * {@link IllegalStateException}；{@code close()} 幂等。
 *
 * <p>Deterministic ordered flush-queue core (p.2.6.4, pure JDK, no MC imports): funnels
 * writes from several concurrent producer banks into {@link AsyncWrite} and hands each to
 * the consumer sink exactly once, in a deterministic order, on {@link #flushAll()}. The
 * sink is decoupled from the storage stack — this path injects a memory sink, and the
 * runtime shell later provides the real zd/save writer under its {@code -D} gate. Off by
 * default (see {@link #isEnabled()}): the queue itself always behaves identically; it is a
 * progressive enhancement only when a shell explicitly {@link #enable()}s it and chooses to
 * route vanilla I/O through it. Vanilla lifecycle is untouched by default.
 *
 * <p><b>Determinism order contract (key)</b> {@link #enqueue} atomically claims an enqueue
 * sequence {@code seq} outside the lock via a monotonically increasing {@link AtomicLong}
 * (a total order across callers, independent of concurrency); {@link #flushAll} orders the
 * pending writes ascending by {@code seq} and invokes the sink in that order. So the same
 * write set flushes in a constant order no matter which thread enqueued what when. That is
 * the deterministic merge this milestone wants: paired with "each caller's own destination
 * file set", output ordering is deterministic.
 *
 * <p><b>Counting</b> {@link #pendingCount()} = accepted-not-yet-flushed; {@link #flushedCount()}
 * = cumulative handed to the sink. After a successful {@link #flushAll()} the two reconcile
 * to exactly-once.
 *
 * <p><b>Lifecycle</b> {@link #close()} first flushes the remainder, then closes; after close
 * {@link #enqueue} throws {@link IllegalStateException}; {@code close()} is idempotent.
 */
public final class AsyncIoQueue implements AutoCloseable {

    /** 默认关闭门（渐进增强）：运行时 shell 之后在 {@code -D} 门控下切换。Default-off gate
     * (progressive enhancement); the runtime shell flips it later under its {@code -D} gate. */
    private static final AtomicBoolean ENABLED = new AtomicBoolean(false);

    private final Consumer<AsyncWrite> sink;
    private final int requestedParallelism;

    private final AtomicLong seq = new AtomicLong();
    private final Object lock = new Object();
    private final List<Entry> pending = new ArrayList<>();
    private boolean closed = false;
    private long flushedTotal = 0L;

    private record Entry(long seq, AsyncWrite write) {
    }

    private AsyncIoQueue(Consumer<AsyncWrite> sink, int requestedParallelism) {
        this.sink = Objects.requireNonNull(sink, "sink");
        this.requestedParallelism = Math.max(1, requestedParallelism);
    }

    /**
     * 建队并将真正的写者作为 sink 注入。sink 不可为 null。requestedParallelism 被保留为契约元数据
     * （未来并行 drain 的提示），但确定性刷新始终按 seq 串行调用 sink —— 收到序列不随该值改变。
     * Creates a queue with the real writer injected as the sink (non-null). The requested
     * parallelism is retained as contract metadata (a hint for a future parallel drain), but
     * a deterministic flush always invokes the sink serially in seq order — the received
     * sequence does not depend on this value.
     *
     * @param sink                the actual writer (SA/SYNC path when flushes happen).
     * @param requestedParallelism the desired drain parallelism ({@code <= 0} → 1).
     * @return the new queue.
     */
    public static AsyncIoQueue of(Consumer<AsyncWrite> sink, int requestedParallelism) {
        return new AsyncIoQueue(sink, requestedParallelism);
    }

    // ---- default-off gate / 渐进增强门 ----

    /** 门当前是否打开（默认 false）。Whether the gate is on (default false). */
    public static boolean isEnabled() {
        return ENABLED.get();
    }

    /** 显式打开门（运行时 shell 的决定；队列自身行为不变）。Explicitly enables the gate (a shell
     * decision; the queue's own behaviour is unchanged). */
    public static void enable() {
        ENABLED.set(true);
    }

    /** 显式关上门（零副作用）。Explicitly disables the gate (no side effects). */
    public static void disable() {
        ENABLED.set(false);
    }

    // ---- queue API ----

    /**
     * 接受并排序一次写：原子领取递增 seq 后加入待刷新列表。多个生成 bank 可并发调用。关闭后抛
     * {@link IllegalStateException}。Accepts and orders a write: atomically claims an
     * increasing seq, then queues it. Safe for concurrent callers (e.g. multiple generation
     * banks). After the queue is closed it throws {@link IllegalStateException}.
     *
     * @param w the write to defer (its key is the ordered identity).
     */
    public void enqueue(AsyncWrite w) {
        Objects.requireNonNull(w, "w");
        long s = seq.getAndIncrement();
        synchronized (lock) {
            if (closed) {
                throw new IllegalStateException("AsyncIoQueue is closed");
            }
            pending.add(new Entry(s, w));
        }
    }

    /**
     * 把已接受写恰好一次、按入队 seq 升序（确定性全序）经 sink 执行，然后清空。阻塞直至全部完成。
     * Executes every accepted write exactly once through the sink, in ascending enqueue-seq
     * order (the deterministic total order), then clears. Final-state; blocks until all done.
     */
    public void flushAll() {
        List<Entry> snapshot;
        synchronized (lock) {
            snapshot = new ArrayList<>(pending.size());
            snapshot.addAll(pending);
            pending.clear();
        }
        snapshot.sort(Comparator.comparingLong(Entry::seq));
        long executed = 0L;
        for (Entry e : snapshot) {
            sink.accept(e.write());
            executed++;
        }
        if (executed > 0) {
            synchronized (lock) {
                flushedTotal += executed;
            }
        }
    }

    /** 已接受但尚未刷新的写数。Count of accepted-but-not-yet-flushed writes. */
    public int pendingCount() {
        synchronized (lock) {
            return pending.size();
        }
    }

    /** 累计已交给 sink 的写数。Cumulative writes handed to the sink. */
    public long flushedCount() {
        synchronized (lock) {
            return flushedTotal;
        }
    }

    /** queue 是否仍接受新写。Whether the queue still accepts new writes. */
    public boolean open() {
        synchronized (lock) {
            return !closed;
        }
    }

    /**
     * 关闭：先把剩余写刷新（enqueue 已先被拒，故不会丢写），再置关闭；幂等，二次关闭不做事。
     * Closes: first flushes the remainder (enqueue is already rejected, so nothing is lost),
     * then marks closed; idempotent — a second close is a no-op.
     */
    public void close() {
        synchronized (lock) {
            if (closed) {
                return;
            }
            closed = true;
        }
        flushAll();
    }

    /** 记录下来的请求并发度（契约元数据）。The recorded requested parallelism (contract metadata). */
    public int requestedParallelism() {
        return requestedParallelism;
    }

    @Override
    public String toString() {
        synchronized (lock) {
            return "AsyncIoQueue{pending=" + pending.size() + ", flushed=" + flushedTotal
                    + ", closed=" + closed + "}";
        }
    }
}