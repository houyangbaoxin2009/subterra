// Ported from FlowSched (MIT, Copyright (c) 2023 ishland, https://github.com/ishland/FlowSched)
// into io.toterra.subterra.engine.optim.sched (p.1.4.23). FlowSched is distributed under the MIT license.
package io.toterra.subterra.engine.optim.sched.executor;

// Orginal used SLF4J for logging; this module is pure JDK so java.util.logging
// is used instead (behavior-equivalent error reporting).
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;
import java.util.logging.Level;
import java.util.logging.Logger;

public class WorkerThread extends Thread {

    private static final Logger LOGGER = Logger.getLogger("io.toterra.subterra.engine.optim.sched.executor.WorkerThread");

    private final ExecutorManager executorManager;
    private volatile boolean shutdown = false;

    public WorkerThread(ExecutorManager executorManager) {
        this.executorManager = executorManager;
    }

    @Override
    public void run() {
        main_loop:
        while (true) {
            this.executorManager.waitObj.acquireUninterruptibly();

            if (this.shutdown) {
                return;
            }
            while (!this.shutdown && !pollTasks()) {
                Thread.onSpinWait();
            }
        }
    }

    private boolean pollTasks() {
        Task task = this.executorManager.getGlobalWorkQueue().dequeue();
        if (task == null) {
            return false;
        }
        if (!this.executorManager.tryLock(task)) {
            return true; // polled
        }
        try {
            AtomicBoolean released = new AtomicBoolean(false);
            try {
                task.run(() -> {
                    if (released.compareAndSet(false, true)) {
                        executorManager.releaseLocks(task);
                    }
                });
            } catch (Throwable t) {
                try {
                    if (released.compareAndSet(false, true)) {
                        executorManager.releaseLocks(task);
                    }
                } catch (Throwable t1) {
                    t.addSuppressed(t1);
                    LOGGER.log(Level.SEVERE, "Exception thrown while releasing locks", t);
                }
                try {
                    task.propagateException(t);
                } catch (Throwable t1) {
                    t.addSuppressed(t1);
                    LOGGER.log(Level.SEVERE, "Exception thrown while propagating exception", t);
                }
            }
            return true;
        } catch (Throwable t) {
            LOGGER.log(Level.SEVERE, "Exception thrown while executing task", t);
            return true;
        }
    }

    public void shutdown() {
        shutdown = true;
    }


}