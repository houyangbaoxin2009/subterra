// Async dispatch design derives from C2ME (MIT, Copyright (c) ishland, https://github.com/ishland/C2ME);
// implementation is original Subterra code.
package io.toterra.subterra.engine.worldgen.async;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * One execution lane per partition bank. Within a bank the queued tasks run
 * strictly serially (FIFO) on a single worker; tasks in different banks run
 * concurrently. Every {@link #submit(Runnable)} task is executed exactly once.
 * Work is bounded ({@link ArrayBlockingQueue} of fixed capacity) so a burst
 * cannot grow arrays without bound. {@link #awaitIdle()} / {@link #shutdown()}
 * give clean join and teardown semantics.
 *
 * <p><b>Thread engine decision.</b> The {@code engine.optim.sched.ExecutorManager}
 * is a shared-global-queue, work-stealing executor with no per-bank
 * serialization, no completion/join primitive on a task, and no ordered-fetch
 * API — wrapping it to reproduce serial-per-bank + bounded + awaitIdle would add
 * a second scheduler layer with its own ordering hazards. For the deterministic,
 * time-constrained foundation we instead back each bank with one dedicated
 * daemon {@code virtual} worker (JDK 21+, default daemon, so a pure-JVM probe
 * cannot be kept alive). This gives the serial-per-bank + concurrent-across-bank
 * + join contract directly and is trivial to reason about; a later sub-item can
 * still swap in an ExecutorManager-backed implementation behind the same API.
 *
 * <p><b>线程引擎抉择。</b>{@code engine.optim.sched.ExecutorManager} 是共享全局队列、
 * 工作窃取的执行器，没有 per-bank 串行化、任务完成/join 原语、也无序获取接口——
 * 用它复刻"bank 内串行 + 有界 + awaitIdle"会多出一层带自身排序风险的调度。面对
 * 确定性且时间受限的基础设施，这里让每个 bank 独占一个守护 {@code virtual} 工作线程
 * （JDK 21+，默认守护，纯 JVM 探针不会被拖住）。它直接给出"bank 内串行 + bank 间并发
 * + join"的契约且易于推理；后续子项仍可在同一 API 下换成 ExecutorManager 实现。
 *
 * <p><b>Not thread-confined API:</b> {@code submit} may be called concurrently
 * from multiple threads; the single worker consumes, so ordering across callers
 * is defined per-enqueue (FIFO), which is exactly the within-bank serial contract.
 */
public final class AsyncWorkerBank {

    /** Fixed capacity of the per-bank bounded queue. */
    public static final int CAPACITY = 4096;

    private final int index;
    private final ArrayBlockingQueue<Runnable> queue = new ArrayBlockingQueue<>(CAPACITY);
    private final Thread worker;

    private final AtomicInteger submitted = new AtomicInteger();
    private final AtomicInteger executed = new AtomicInteger();

    /** Guard for {@link #outstanding} + {@link #closed} + idle notification. */
    private final Object monitor = new Object();
    private int outstanding = 0;
    private boolean closed = false;

    /**
     * @param index the bank index for diagnostics.
     */
    public AsyncWorkerBank(int index) {
        this.index = index;
        // Virtual threads are daemon by default; they never keep the JVM alive.
        this.worker = Thread.ofVirtual()
                .name("async-bank-" + index)
                .start(this::runLoop);
    }

    private void runLoop() {
        while (true) {
            Runnable task;
            try {
                task = queue.take();
            } catch (InterruptedException ie) {
                // Interrupted: drain anything still queued to keep exactly-once,
                // then stop accepting further work.
                drainAndFinish();
                return;
            }
            try {
                task.run();
            } finally {
                executed.incrementAndGet();
                synchronized (monitor) {
                    outstanding--;
                    monitor.notifyAll();
                }
            }
        }
    }

    private void drainAndFinish() {
        Runnable task;
        // queue.poll() returns immediately once empty; no blocking after interrupt handling.
        while ((task = queue.poll()) != null) {
            try {
                task.run();
            } catch (Throwable ignored) {
                // Draining on shutdown must not lose the exactly-once count.
            } finally {
                executed.incrementAndGet();
                synchronized (monitor) {
                    outstanding--;
                    monitor.notifyAll();
                }
            }
        }
    }

    /**
     * Enqueues a task for exactly-once execution on this bank's serial worker.
     * Blocks (bounded backpressure) if the queue is full; the console caller
     * normally submits from a single thread, so this cannot deadlock.
     *
     * @param task the task to run.
     * @throws IllegalStateException if the bank is shut down.
     */
    public void submit(Runnable task) {
        synchronized (monitor) {
            if (closed) {
                throw new IllegalStateException("bank " + index + " is closed");
            }
            outstanding++;
        }
        submitted.incrementAndGet();
        try {
            queue.put(task);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Blocks until every task accepted up to this point has completed and the
     * queue is drained (undoes any residual shutdown drain too). Returns only
     * after outstanding work is zero — a final-state join, not a timing check.
     */
    public void awaitIdle() {
        synchronized (monitor) {
            while (outstanding > 0) {
                try {
                    monitor.wait();
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    /**
     * Signals the worker to stop. Work already accepted is drained and executed
     * before the worker ends, so exactly-once is preserved even under shutdown.
     * Safe to call once idle; idempotent.
     */
    public void shutdown() {
        synchronized (monitor) {
            closed = true;
        }
        worker.interrupt();
        try {
            worker.join();
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * @return the number of tasks accepted via {@link #submit}.
     */
    public int submittedCount() {
        return submitted.get();
    }

    /**
     * @return the number of tasks that have run to completion.
     */
    public int executedCount() {
        return executed.get();
    }

    /**
     * @return the number of tasks accepted but not yet executed.
     */
    public int queued() {
        synchronized (monitor) {
            return outstanding;
        }
    }
}