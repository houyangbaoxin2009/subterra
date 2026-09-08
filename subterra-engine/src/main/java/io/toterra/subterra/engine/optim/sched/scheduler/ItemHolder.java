// Ported from FlowSched (MIT, Copyright (c) 2023 ishland, https://github.com/ishland/FlowSched)
// into io.toterra.subterra.engine.optim.sched (p.1.4.23). FlowSched is distributed under the MIT license.
//
// RxJava/fastutil removal notes (see report p.1.4.23):
// - subscribeOp: rxjava Completable -> CompletionStage<Void> (busy-ref bookkeeping
//   unchanged: increment now, decrement on completion).
// - getCriticalSectionScheduler: rxjava Scheduler returned by Schedulers.from(...);
//   nothing internal consumed it, so it and the field are removed; getCriticalSectionExecutor()
//   already exposes the wrapped java Executor used for scheduling.
// - runningUpgradeAction uses fastutil Pair -> io.toterra.subterra.engine.optim.sched.structs.Pair (record).
// - dependencyInfos: fastutil Object2ReferenceLinkedOpenHashMap (identity keys/values,
//   insertion-ordered, rehash-grow-only hack) -> LinkedHashMap<K, DependencyInfo>.
//   Iteration order is preserved; the grow-only rehash was only a memory optimization.
// - DependencyInfo.callbacks: fastutil ObjectArrayList -> java.util.ArrayList.
package io.toterra.subterra.engine.optim.sched.scheduler;

import io.toterra.subterra.engine.optim.sched.structs.OneTaskAtATimeExecutor;
import io.toterra.subterra.engine.optim.sched.structs.Pair;
import io.toterra.subterra.engine.optim.sched.util.Assertions;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

public class ItemHolder<K, V, Ctx, UserData> {

    private static final VarHandle FUTURES_HANDLE = MethodHandles.arrayElementVarHandle(CompletableFuture[].class);
    private static final AtomicIntegerFieldUpdater<ItemHolder> SCHEDULED_DIRTY_UPDATER =
            AtomicIntegerFieldUpdater.newUpdater(ItemHolder.class, "scheduledDirty");

    public static final IllegalStateException UNLOADED_EXCEPTION = new IllegalStateException("Not loaded");
    private static final CompletableFuture<Void> UNLOADED_FUTURE = CompletableFuture.failedFuture(UNLOADED_EXCEPTION);
    private static final CompletableFuture<Void> COMPLETED_VOID_FUTURE = CompletableFuture.completedFuture(null);

    @SuppressWarnings("PointlessBitwiseExpression")
    public static final int FLAG_REMOVED = 1 << 0;
    /**
     * Indicates the holder have been marked broken
     * If set, the holder:
     * - will not be allowed to be upgraded any further
     * - will still be allowed to be downgraded, but operations to it should be careful
     */
    public static final int FLAG_BROKEN = 1 << 1;
    /**
     * Indicates the holder have at least one failed transactions and proceeded to retry
     */
    public static final int FLAG_HAVE_RETRIED = 1 << 2;

    private final K key;
    private final ItemStatus<K, V, Ctx> unloadedStatus;
    private final AtomicReference<V> item = new AtomicReference<>();
    private final AtomicReference<UserData> userData = new AtomicReference<>();
    private final BusyRefCounter busyRefCounter = new BusyRefCounter();
    private final AtomicReference<Pair<CancellationSignaller, ItemStatus<K, V, Ctx>>> runningUpgradeAction = new AtomicReference<>();
    private final TicketSet<K, V, Ctx> tickets;
    private volatile ItemStatus<K, V, Ctx> status = null;
//    private final List<Pair<ItemStatus<K, V, Ctx>, Long>> statusHistory = ReferenceLists.synchronize(new ReferenceArrayList<>());
    private final KeyStatusPair<K, V, Ctx>[][] requestedDependencies;
    private final CompletableFuture<Void>[] futures;
    private final AtomicInteger flags = new AtomicInteger(0);
    private volatile int scheduledDirty = 0; // meant to be used as a boolean
    private final OneTaskAtATimeExecutor criticalSectionExecutor;
    private final LinkedHashMap<K, DependencyInfo> dependencyInfos = new LinkedHashMap<>();
    private boolean dependencyDirty = false;

    ItemHolder(ItemStatus<K, V, Ctx> initialStatus, K key, ObjectFactory objectFactory, Executor backgroundExecutor) {
        this.unloadedStatus = Objects.requireNonNull(initialStatus);
        this.status = this.unloadedStatus;
        this.key = Objects.requireNonNull(key);
        this.tickets = new TicketSet<>(this.unloadedStatus, objectFactory);

        ItemStatus<K, V, Ctx>[] allStatuses = initialStatus.getAllStatuses();
        this.futures = new CompletableFuture[allStatuses.length];
        this.requestedDependencies = new KeyStatusPair[allStatuses.length][];
        for (int i = 0, allStatusesLength = allStatuses.length; i < allStatusesLength; i++) {
            this.futures[i] = UNLOADED_FUTURE;
            this.requestedDependencies[i] = null;
        }
        this.criticalSectionExecutor = new OneTaskAtATimeExecutor(new ConcurrentLinkedQueue<>(), backgroundExecutor);
        VarHandle.fullFence();
    }

    /**
     * Not thread-safe, protect with statusMutex
     */
    private void createFutures() {
        final ItemStatus<K, V, Ctx> targetStatus = this.getTargetStatus();
        for (int i = this.unloadedStatus.ordinal() + 1; i <= targetStatus.ordinal(); i++) {
            this.futures[i] = this.futures[i] == UNLOADED_FUTURE ? new CompletableFuture<>() : this.futures[i];
        }
    }

    /**
     * Get the target status of this item.
     *
     * @return the target status of this item, or null if no ticket is present
     */
    public ItemStatus<K, V, Ctx> getTargetStatus() {
        synchronized (this) {
            return this.tickets.getTargetStatus();
        }
    }

    public ItemStatus<K, V, Ctx> getStatus() {
        return this.status;
    }

    public synchronized boolean isBusy() {
        assertOpen();
        return busyRefCounter.isBusy();
    }

    public ItemStatus<K, V, Ctx> upgradingStatusTo() {
        assertOpen();
        final Pair<CancellationSignaller, ItemStatus<K, V, Ctx>> pair = this.runningUpgradeAction.get();
        return pair != null ? pair.right() : null;
    }

    public void addTicket(ItemTicket<K, V, Ctx> ticket) {
        assertOpen();
        final boolean add = this.tickets.checkAdd(ticket);
        if (!add) {
            throw new IllegalStateException("Ticket already exists");
        }

        boolean needConsumption;
        synchronized (this) {
            this.tickets.addUnchecked(ticket);
            createFutures();
            needConsumption = ticket.getTargetStatus().ordinal() <= this.getStatus().ordinal();
        }

        if (needConsumption) {
            ticket.consumeCallback();
        }
        this.validateRequestedFutures(ticket.getTargetStatus());
    }

    public void removeTicket(ItemTicket<K, V, Ctx> ticket) {
        assertOpen();
        final boolean remove = this.tickets.checkRemove(ticket);
        if (!remove) {
            throw new IllegalStateException("Ticket does not exist");
        }
        synchronized (this) {
            this.tickets.removeUnchecked(ticket);
        }
    }

    public void submitOp(CompletionStage<Void> op) {
        assertOpen();
//        this.opFuture.set(opFuture.get().thenCombine(op, (a, b) -> null).handle((o, throwable) -> null));
//        this.opFuture.getAndUpdate(future -> future.thenCombine(op, (a, b) -> null).handle((o, throwable) -> null));
        this.busyRefCounter.incrementRefCount();
        op.whenComplete((unused, throwable) -> this.busyRefCounter.decrementRefCount());
    }

    // rxjava Completable.onErrorComplete().subscribe(runnable) -> CompletionStage.whenComplete
    // (runs the completion callback regardless of success/error, exactly like onErrorComplete).
    public void subscribeOp(CompletionStage<Void> op) {
        assertOpen();
        this.busyRefCounter.incrementRefCount();
        op.whenComplete((unused, throwable) -> this.busyRefCounter.decrementRefCount());
    }

    BusyRefCounter busyRefCounter() {
        return this.busyRefCounter;
    }

    public void submitUpgradeAction(CancellationSignaller signaller, ItemStatus<K, V, Ctx> status) {
        assertOpen();
        final boolean success = this.runningUpgradeAction.compareAndSet(null, Pair.of(signaller, status));
        Assertions.assertTrue(success, "Only one action can happen at a time");
        signaller.addListener(unused -> this.runningUpgradeAction.set(null));
    }

    public void tryCancelUpgradeAction() {
        assertOpen();
        final Pair<CancellationSignaller, ItemStatus<K, V, Ctx>> signaller = this.runningUpgradeAction.get();
        if (signaller != null) {
            signaller.left().cancel();
        }
    }

    public CompletableFuture<Void> getOpFuture() { // best-effort
        assertOpen();
        if (!this.busyRefCounter.isBusy()) {
            return COMPLETED_VOID_FUTURE;
        }
        CompletableFuture<Void> future = new CompletableFuture<>();
        this.busyRefCounter.addListener(() -> future.complete(null));
        return future;
    }

    public void submitOpListener(Runnable runnable) {
        assertOpen();
        this.busyRefCounter.addListener(runnable);
    }

    public void consolidateMarkDirty(StatusAdvancingScheduler<K, V, Ctx, UserData> scheduler) {
        assertOpen();
        this.busyRefCounter.addListenerOnce(() -> this.markDirty(scheduler));
    }

    public Executor getCriticalSectionExecutor() {
        assertOpen();
        return this.criticalSectionExecutor;
    }

    public void executeCriticalSectionAndBusy(Runnable command) {
        assertOpen();
        this.busyRefCounter().incrementRefCount();
        this.getCriticalSectionExecutor().execute(() -> {
            try {
                command.run();
            } finally {
                this.busyRefCounter().decrementRefCount();
            }
        });
    }

    public void markDirty(StatusAdvancingScheduler<K, V, Ctx, UserData> scheduler) {
        assertOpen();
        markDirty0(scheduler);
    }

    public boolean tryMarkDirty(StatusAdvancingScheduler<K, V, Ctx, UserData> scheduler) {
        if (!isOpen()) return false;
        markDirty0(scheduler);
        return true;
    }

    private void markDirty0(StatusAdvancingScheduler<K, V, Ctx, UserData> scheduler) {
        if (SCHEDULED_DIRTY_UPDATER.compareAndSet(this, 0, 1)) {
            this.criticalSectionExecutor.execute(() -> {
                SCHEDULED_DIRTY_UPDATER.set(this, 0);
                scheduler.tickHolder0(this);
            });
        }
    }

    public boolean setStatus(ItemStatus<K, V, Ctx> status, boolean isCancellation) {
        assertOpen();
        ItemTicket<K, V, Ctx>[] ticketsToFire = null;
        CompletableFuture<Void> futureToFire = null;
        synchronized (this) {
            final ItemStatus<K, V, Ctx> prevStatus = this.getStatus();
            Assertions.assertTrue(status != prevStatus, "duplicate setStatus call");
//            this.statusHistory.add(Pair.of(status, System.currentTimeMillis()));
            final int compare = Integer.compare(status.ordinal(), prevStatus.ordinal());
            if (compare < 0) { // status downgrade
                Assertions.assertTrue(prevStatus.getPrev() == status, "Invalid status downgrade");

//                if (this.getTargetStatus().ordinal() > status.ordinal()) {
//                    return false;
//                }

                this.status = status;

                // reinit higher futures because downgraded, and deinit futures higher than target status
                // already protected by statusMutex
                final ItemStatus<K, V, Ctx> targetStatus = this.getTargetStatus();
                for (int i = prevStatus.ordinal(); i < this.futures.length; i ++) {
                    if (i > targetStatus.ordinal()) {
                        this.futures[i].completeExceptionally(UNLOADED_EXCEPTION);
                        this.futures[i] = UNLOADED_FUTURE;
                    } else {
                        this.futures[i] = this.futures[i].isDone() ? new CompletableFuture<>() : this.futures[i];
                    }
                }
            } else if (compare > 0) { // status upgrade
                Assertions.assertTrue(prevStatus.getNext() == status, "Invalid status upgrade");

                this.status = status;

                // already protected by statusMutex
                final CompletableFuture<Void> future = this.futures[status.ordinal()];

                if (!isCancellation) {
                    Assertions.assertTrue(future != UNLOADED_FUTURE);
                    Assertions.assertTrue(!future.isDone());
                }
                futureToFire = future;
                ticketsToFire = this.tickets.getTicketsForStatus(status).toArray(ItemTicket[]::new);
            }
        }
        if (ticketsToFire != null) {
            for (ItemTicket<K, V, Ctx> ticket : ticketsToFire) {
                ticket.consumeCallback();
            }
        }
        if (futureToFire != null) {
            futureToFire.complete(null);
        }
        return true;
    }

    void flushUnloadedStatus(ItemStatus<K, V, Ctx> currentStatus) {
        ArrayList<CompletableFuture<Void>> futuresToFire = null;
        if (currentStatus.getNext() == null) {
            return;
        }
        synchronized (this) {
            ItemStatus<K, V, Ctx> targetStatus = this.getTargetStatus();
            if (targetStatus.getNext() == null) {
                return;
            }
            for (int i = Math.max(currentStatus.ordinal(), targetStatus.ordinal()) + 1; i < this.futures.length; i ++) {
                if (futuresToFire == null) futuresToFire = new ArrayList<>();
                CompletableFuture<Void> oldFuture = this.futures[i];
                futuresToFire.add(oldFuture);
                this.futures[i] = UNLOADED_FUTURE;
            }
        }
        if (futuresToFire != null) {
            for (int i = 0, finalFuturesToFireSize = futuresToFire.size(); i < finalFuturesToFireSize; i++) {
                CompletableFuture<Void> future = futuresToFire.get(i);
                future.completeExceptionally(UNLOADED_EXCEPTION);
            }
        }
    }

    void validateCompletedFutures(ItemStatus<K, V, Ctx> current) {
        synchronized (this) {
            for (int i = this.unloadedStatus.ordinal() + 1; i <= current.ordinal(); i++) {
                CompletableFuture<Void> future = this.futures[i];
                Assertions.assertTrue(future != UNLOADED_FUTURE, "Future for loaded status cannot be UNLOADED_FUTURE");
                Assertions.assertTrue(future.isDone(), "Future for loaded status must be completed");
            }
        }
    }

    void validateAllFutures() {
        synchronized (this) {
            for (int i = this.unloadedStatus.ordinal() + 1; i < this.futures.length; i++) {
                CompletableFuture<Void> future = this.futures[i];
                if (i <= this.getStatus().ordinal()) {
                    Assertions.assertTrue(future.isDone(), "Future for loaded status must be completed");
                }
                if (i <= this.getTargetStatus().ordinal()) {
                    Assertions.assertTrue(future != UNLOADED_FUTURE, "Future for requested status cannot be UNLOADED_FUTURE");
                } else {
                    Assertions.assertTrue(future == UNLOADED_FUTURE, "Future for non-requested status must be UNLOADED_FUTURE");
                }
            }
        }
    }

    void validateRequestedFutures(ItemStatus<K, V, Ctx> current) {
        synchronized (this) {
            for (int i = this.unloadedStatus.ordinal() + 1; i <= current.ordinal(); i++) {
                CompletableFuture<Void> future = this.futures[i];
                Assertions.assertTrue(future != UNLOADED_FUTURE, "Future for requested status cannot be UNLOADED_FUTURE");
            }
        }
    }

    public synchronized void setDependencies(ItemStatus<K, V, Ctx> status, KeyStatusPair<K, V, Ctx>[] dependencies) {
        assertOpen();
        final int ordinal = status.ordinal();
        if (dependencies != null) {
            Assertions.assertTrue(this.requestedDependencies[ordinal] == null, "Duplicate setDependencies call");
            this.requestedDependencies[ordinal] = dependencies;
        } else {
            Assertions.assertTrue(this.requestedDependencies[ordinal] != null, "Duplicate setDependencies call");
            this.requestedDependencies[ordinal] = null;
        }
    }

    public synchronized KeyStatusPair<K, V, Ctx>[] getDependencies(ItemStatus<K, V, Ctx> status) {
        assertOpen();
        return this.requestedDependencies[status.ordinal()];
    }

    public K getKey() {
        return this.key;
    }

    public CompletableFuture<Void> getFutureForStatus(ItemStatus<K, V, Ctx> status) {
        synchronized (this) {
            return this.futures[status.ordinal()].thenApply(Function.identity());
        }
    }

    /**
     * Only for trusted methods
     */
    public CompletableFuture<Void> getFutureForStatus0(ItemStatus<K, V, Ctx> status) {
        synchronized (this) {
            return this.futures[status.ordinal()];
        }
    }
    
    public AtomicReference<V> getItem() {
        return this.item;
    }

    /**
     * Get the user data of this item.
     *
     * @apiNote it is the caller's obligation to ensure the holder is not closed
     * @return the user data
     */
    public AtomicReference<UserData> getUserData() {
        return this.userData;
    }

    public int getFlags() {
        return this.flags.get();
    }

    public void setFlag(int flag) {
        assertOpen();
        this.flags.getAndUpdate(operand -> operand | flag);
    }

    /**
     * Note: do not use this unless you know what you are doing
     */
    public void clearFlag(int flag) {
        assertOpen();
        Assertions.assertTrue((flag & FLAG_REMOVED) == 0, "Cannot clear FLAG_REMOVED");
        this.flags.getAndUpdate(operand -> operand & ~flag);
    }

    void release() {
        assertOpen();
        synchronized (this) {
            this.tickets.assertEmpty();
        }
        setFlag(FLAG_REMOVED);
    }

    public void addDependencyTicket(StatusAdvancingScheduler<K, V, Ctx, ?> scheduler, K key, ItemStatus<K, V, Ctx> status, Runnable callback) {
        synchronized (this.dependencyInfos) {
            final DependencyInfo info = this.dependencyInfos.computeIfAbsent(key, k -> new DependencyInfo(status.getAllStatuses().length));
            final int ordinal = status.ordinal();
            if (info.refCnt[ordinal] == -1) {
                info.refCnt[ordinal] = 0;
                info.callbacks[ordinal] = new ArrayList<>();
                scheduler.addTicket(key, ItemTicket.TicketType.DEPENDENCY, this.getKey(), status, () -> {
                    final ArrayList<Runnable> list;
                    synchronized (this.dependencyInfos) {
                        list = info.callbacks[ordinal];
                        if (list != null) {
                            info.callbacks[ordinal] = null;
                        }
                    }
                    if (list != null) {
                        for (Runnable runnable : list) {
                            try {
                                runnable.run();
                            } catch (Throwable t) {
                                t.printStackTrace();
                            }
                        }
                    }
                });
            }
            info.refCnt[ordinal] ++;
            final ArrayList<Runnable> list = info.callbacks[ordinal];
            if (list != null) {
                list.add(callback);
            } else {
                callback.run();
            }
        }
    }

    public void removeDependencyTicket(K key, ItemStatus<K, V, Ctx> status) {
        synchronized (this.dependencyInfos) {
            final DependencyInfo info = this.dependencyInfos.get(key);
            Assertions.assertTrue(info != null);
            final int old = info.refCnt[status.ordinal()]--;
            Assertions.assertTrue(old > 0);
            if (old == 1) {
                dependencyDirty = true;
            }
        }
    }

    public boolean isDependencyDirty() {
        synchronized (this.dependencyInfos) {
            return this.dependencyDirty;
        }
    }

    public boolean holdsDependency() {
        synchronized (this.dependencyInfos) {
            for (Map.Entry<K, DependencyInfo> entry : this.dependencyInfos.entrySet()) {
                final DependencyInfo info = entry.getValue();
                int[] refCnt = info.refCnt;
                for (int i : refCnt) {
                    if (i != -1) return true;
                }
            }
            return false;
        }
    }

    public void cleanupDependencies(StatusAdvancingScheduler<K, V, Ctx, ?> scheduler) {
        synchronized (this.dependencyInfos) {
            if (!dependencyDirty) return;
            for (Iterator<Map.Entry<K, DependencyInfo>> iterator = this.dependencyInfos.entrySet().iterator(); iterator.hasNext(); ) {
                Map.Entry<K, DependencyInfo> entry = iterator.next();
                final K key = entry.getKey();
                final DependencyInfo info = entry.getValue();
                int[] refCnt = info.refCnt;
                boolean isEmpty = true;
                for (int ordinal = 0, refCntLength = refCnt.length; ordinal < refCntLength; ordinal++) {
                    if (refCnt[ordinal] == 0) {
                        scheduler.removeTicket(key, ItemTicket.TicketType.DEPENDENCY, this.getKey(), this.unloadedStatus.getAllStatuses()[ordinal]);
                        refCnt[ordinal] = -1;
                        info.callbacks[ordinal] = null;
                    }
                    if (refCnt[ordinal] != -1) isEmpty = false;
                }
                if (isEmpty)
                    iterator.remove();
            }
            dependencyDirty = false;
        }
    }

    private void assertOpen() {
        Assertions.assertTrue(isOpen());
    }

    public boolean isOpen() {
        return (this.getFlags() & FLAG_REMOVED) == 0;
    }

    private static class DependencyInfo {
        private final int[] refCnt;
        private final ArrayList<Runnable>[] callbacks;

        @SuppressWarnings("unchecked")
        private DependencyInfo(int statuses) {
            this.refCnt = new int[statuses];
            this.callbacks = new ArrayList[statuses];
            Arrays.fill(this.refCnt, -1);
        }
    }
}