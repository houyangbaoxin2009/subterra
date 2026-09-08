// Ported from mc-smoothboot (github.com/UltimateBoomer/mc-smoothboot), MIT (c) UltimateBoomer.
package io.toterra.subterra.runtime.optim.server.threading.mixin;

import io.toterra.subterra.runtime.optim.server.threading.WorkerPoolTuning;
import io.toterra.subterra.runtime.optim.server.threading.WorkerPoolTuningConfig;
import io.toterra.subterra.runtime.optim.server.threading.util.LoggingForkJoinWorkerThread;
import net.minecraft.Util;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinWorkerThread;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Replaces {@link Util}'s worker executors on first getter call, swapping the
 * {@code @Final @Mutable} static fields for pools with td-configured thread
 * counts and priorities.
 *
 * <p>Ported to 1.21.1: the 1.19.4 {@code MAIN_WORKER_EXECUTOR/IO_WORKER_EXECUTOR}
 * fields and {@code getMainWorkerExecutor()/getIoWorkerExecutor()} getters were
 * renamed to {@code BACKGROUND_EXECUTOR/IO_POOL} and
 * {@code backgroundExecutor()/ioPool()} in 1.20.x; {@code NEXT_WORKER_ID} became
 * a per-pool local (kept here as a mixin-local counter) and
 * {@code uncaughtExceptionHandler} was renamed to {@code onThreadException}.
 * The bootstrap/BOOTSTRAP executor no longer exists on 1.21.1, so that part of
 * upstream is dropped.
 */
@Mixin(Util.class)
public abstract class UtilMixin {
    @Shadow @Final @Mutable
    private static ExecutorService BACKGROUND_EXECUTOR;

    @Shadow @Final @Mutable
    private static ExecutorService IO_POOL;

    @Shadow
    private static void onThreadException(Thread thread, Throwable throwable) {
    }

    private static final AtomicInteger NEXT_WORKER_ID = new AtomicInteger(1);

    @Inject(method = "backgroundExecutor", at = @At("HEAD"))
    private static void onGetBackgroundExecutor(CallbackInfoReturnable<Executor> ci) {
        if (!WorkerPoolTuning.initMainWorker) {
            BACKGROUND_EXECUTOR = replWorker("Main");
            WorkerPoolTuning.LOGGER.debug("Main worker replaced");
            WorkerPoolTuning.initMainWorker = true;
        }
    }

    @Inject(method = "ioPool", at = @At("HEAD"))
    private static void onGetIoPool(CallbackInfoReturnable<Executor> ci) {
        if (!WorkerPoolTuning.initIOWorker) {
            IO_POOL = replIoWorker();
            WorkerPoolTuning.LOGGER.debug("IO worker replaced");
            WorkerPoolTuning.initIOWorker = true;
        }
    }

    /**
     * Replace the background executor: a ForkJoinPool with the configured main
     * thread count (clamped to 1..0x7fff like upstream) and main priority.
     */
    private static ExecutorService replWorker(String name) {
        if (!WorkerPoolTuning.initConfig) {
            WorkerPoolTuning.config();
            WorkerPoolTuning.initConfig = true;
        }
        WorkerPoolTuningConfig config = WorkerPoolTuning.config();

        return new ForkJoinPool(Mth.clamp(config.mainThreads(), 1, 0x7fff), (forkJoinPool) -> {
            String workerName = "Worker-" + name + "-" + NEXT_WORKER_ID.getAndIncrement();
            WorkerPoolTuning.LOGGER.debug("Initialized " + workerName);

            ForkJoinWorkerThread forkJoinWorkerThread = new LoggingForkJoinWorkerThread(forkJoinPool, WorkerPoolTuning.LOGGER);
            forkJoinWorkerThread.setPriority(config.mainPriority());
            forkJoinWorkerThread.setName(workerName);
            return forkJoinWorkerThread;
        }, UtilMixin::onThreadException, true);
    }

    /**
     * Replace the IO executor: a fixed thread pool of the configured IO thread
     * count (upstream 1.19.4 used an unbounded cached pool; the port honours
     * the td {@code io_threads} cap), daemon threads with the IO priority.
     */
    private static ExecutorService replIoWorker() {
        if (!WorkerPoolTuning.initConfig) {
            WorkerPoolTuning.config();
            WorkerPoolTuning.initConfig = true;
        }
        WorkerPoolTuningConfig config = WorkerPoolTuning.config();

        return new ThreadPoolExecutor(config.ioThreads(), config.ioThreads(), 0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(), (runnable) -> {
            String workerName = "IO-Worker-" + NEXT_WORKER_ID.getAndIncrement();
            WorkerPoolTuning.LOGGER.debug("Initialized " + workerName);

            Thread thread = new Thread(runnable);
            thread.setName(workerName);
            thread.setDaemon(true);
            thread.setPriority(config.ioPriority());
            thread.setUncaughtExceptionHandler(UtilMixin::onThreadException);
            return thread;
        });
    }
}
