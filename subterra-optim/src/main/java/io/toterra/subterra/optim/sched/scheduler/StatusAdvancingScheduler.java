// Ported from FlowSched (MIT, Copyright (c) 2023 ishland, https://github.com/ishland/FlowSched)
// into io.toterra.subterra.optim.sched (p.1.4.23). FlowSched is distributed under the MIT license.
//
// RxJava/fastutil removal notes (see report p.1.4.23):
// - The advance/downgrade "transactions" were assembled from rxjava Completable operators
//   (defer/andThen/doOnEvent/onErrorResumeNext/cache/create/onErrorComplete). They are
//   re-plumbed here as pure-JDK continuation passing driven by CompletableFuture.whenComplete
//   callbacks (private advanceStatus0/downgradeStatus0 helpers), preserving the exact
//   execution order and the thread on which each step runs (the critical-section executor
//   thread, since no observeOn/subscribeOn existed in the original chain).
// - Completable cache() (run-once, share result) is a no-op in this single-drive rewrite:
//   each stage future completes once and its whenComplete fires once.
// - getSchedulerBackedByBackgroundExecutor: rxjava Scheduler returned by Schedulers.from(...)
//   -> returns the underlying java Executor (Schedulers.from was a pure wrapper).
// - items: fastutil Object2ReferenceOpenHashMap (with a grow-only rehash hack + StampedLock
//   guarded access) -> HashMap. The rehash hack was only a memory optimization; keys are
//   value objects with proper equals/hashCode, matching the fastutil Object-map semantics.
package io.toterra.subterra.optim.sched.scheduler;

import io.toterra.subterra.optim.sched.util.Assertions;

import java.lang.invoke.VarHandle;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.StampedLock;

/**
 * A scheduler that advances status of items.
 *
 * @param <K> the key type
 * @param <V> the item type
 * @param <Ctx> the context type
 */
public abstract class StatusAdvancingScheduler<K, V, Ctx, UserData> {

    public static final Runnable NO_OP = () -> {
    };

    private final StampedLock itemsLock = new StampedLock();
    private final Map<K, ItemHolder<K, V, Ctx, UserData>> items = new HashMap<>();
    private final ObjectFactory objectFactory;

    protected StatusAdvancingScheduler() {
        this(new ObjectFactory.DefaultObjectFactory());
    }

    protected StatusAdvancingScheduler(ObjectFactory objectFactory) {
        this.objectFactory = Objects.requireNonNull(objectFactory);
    }

    protected abstract Executor getBackgroundExecutor();

    // rxjava Schedulers.from(getBackgroundExecutor()) was a pure wrapper around the same
    // executor; expose the underlying Executor directly as the JDK analog.
    protected Executor getSchedulerBackedByBackgroundExecutor() {
        return getBackgroundExecutor();
    }

    protected abstract ItemStatus<K, V, Ctx> getUnloadedStatus();

    protected abstract Ctx makeContext(ItemHolder<K, V, Ctx, UserData> holder, ItemStatus<K, V, Ctx> nextStatus, KeyStatusPair<K, V, Ctx>[] dependencies, boolean isUpgrade);

    protected ExceptionHandlingAction handleTransactionException(ItemHolder<K, V, Ctx, UserData> holder, ItemStatus<K, V, Ctx> nextStatus, boolean isUpgrade, Throwable throwable) {
        throwable.printStackTrace();
        return ExceptionHandlingAction.MARK_BROKEN;
    }

    protected void handleUnrecoverableException(Throwable throwable) {
    }

    /**
     * Called when an item is created.
     *
     * @implNote This method is called before the item is added to the internal map. Make sure to not access the item from the map.
     *           May get called from any thread.
     * @param holder
     */
    protected void onItemCreation(ItemHolder<K, V, Ctx, UserData> holder) {
    }

    /**
     * Called when an item is deleted.
     *
     * @implNote This method is called when the monitor of the holder is held.
     * @param holder
     */
    protected void onItemRemoval(ItemHolder<K, V, Ctx, UserData> holder) {
    }

    protected void onItemUpgrade(ItemHolder<K, V, Ctx, UserData> holder, ItemStatus<K, V, Ctx> statusReached) {
    }

    protected void onItemDowngrade(ItemHolder<K, V, Ctx, UserData> holder, ItemStatus<K, V, Ctx> statusReached) {
    }

    void tickHolder0(ItemHolder<K, V, Ctx, UserData> holder) {
        final K key = holder.getKey();
        if (getHolder(key) != holder) return;
//        holder.sanitizeSetStatus = Thread.currentThread();
        if (holder.isBusy()) {
            tickHandleBusy0(holder);
            return;
        }

        final ItemStatus<K, V, Ctx> current;
        ItemStatus<K, V, Ctx> nextStatus;
        synchronized (holder) {
            if (holder.isBusy()) {
                holder.executeCriticalSectionAndBusy(() -> this.tickHandleBusy0(holder));
                return;
            }
            current = holder.getStatus();
            nextStatus = getNextStatus(current, holder.getTargetStatus());
            Assertions.assertTrue(holder.getStatus() == current);
            holder.validateCompletedFutures(current);
//        holder.sanitizeSetStatus = null;
            if (nextStatus == current) {
                holder.flushUnloadedStatus(current);
                holder.validateAllFutures();
                if (current.equals(getUnloadedStatus())) {
                    if (holder.isDependencyDirty()) {
                        holder.executeCriticalSectionAndBusy(() -> holder.cleanupDependencies(this));
                        holder.markDirty(this);
                        return;
                    }
                    if (holder.holdsDependency()) {
                        if (holder.isDependencyDirty()) {
                            holder.markDirty(this); // should rarely happen
                            return;
                        }
                        System.err.println(String.format("BUG: %s still holds some dependencies when ready for unloading", holder.getKey()));
                    }
//                    System.out.println("Unloaded: " + key);
                    this.onItemRemoval(holder);
                    holder.release();
                    final long lock = this.itemsLock.writeLock();
                    try {
                        this.items.remove(key);
                    } finally {
                        this.itemsLock.unlockWrite(lock);
                    }
                    return;
                }
                holder.executeCriticalSectionAndBusy(() -> holder.cleanupDependencies(this));
                return;
            }
        }

        Assertions.assertTrue(holder.getStatus() == current);
        if (current.ordinal() < nextStatus.ordinal()) {
            if ((holder.getFlags() & ItemHolder.FLAG_BROKEN) != 0) {
                return;
            }
//            holder.submitOp(CompletableFuture.runAsync(() -> advanceStatus0(holder, nextStatus, key), getBackgroundExecutor()));
            Assertions.assertTrue(holder.getStatus() == current);
            holder.busyRefCounter().incrementRefCount();
            try {
                advanceStatus0(holder, nextStatus, key);
            } finally {
                holder.busyRefCounter().decrementRefCount();
            }
        } else {
            holder.busyRefCounter().incrementRefCount();
            try {
                downgradeStatus0(holder, current, nextStatus, key);
            } finally {
                holder.busyRefCounter().decrementRefCount();
            }
        }
    }

    private void tickHandleBusy0(ItemHolder<K, V, Ctx, UserData> holder) {
        // this is not protected by any synchronization, data can be unstable here
        final ItemStatus<K, V, Ctx> upgradingStatusTo = holder.upgradingStatusTo();
        final ItemStatus<K, V, Ctx> current = holder.getStatus();
        ItemStatus<K, V, Ctx> nextStatus = getNextStatus(current, holder.getTargetStatus());
        ItemStatus<K, V, Ctx> projectedCurrent = upgradingStatusTo != null ? upgradingStatusTo : current;
        if (projectedCurrent.ordinal() > nextStatus.ordinal()) {
            holder.tryCancelUpgradeAction();
        }
        holder.consolidateMarkDirty(this);
        return;
    }

    private void downgradeStatus0(ItemHolder<K, V, Ctx, UserData> holder, ItemStatus<K, V, Ctx> current, ItemStatus<K, V, Ctx> nextStatus, K key) {
        // Downgrade
        final KeyStatusPair<K, V, Ctx>[] dependencies = holder.getDependencies(current);
        Assertions.assertTrue(dependencies != null, "No dependencies for downgrade");

        Cancellable cancellable = new Cancellable();

        AtomicReference<Ctx> contextRef = new AtomicReference<>(null);
        AtomicBoolean hasDowngraded = new AtomicBoolean(false);

        // The whole downgrade transaction tails into this gate; subscribeOp decrements the
        // busy ref-count when it completes (mirrors the original subscribeOp(completable)).
        final CompletableFuture<Void> pipeline = new CompletableFuture<>();
        holder.subscribeOp(pipeline);

        try {
            Assertions.assertTrue(holder.isBusy());
            final Ctx ctx = makeContext(holder, current, dependencies, false);
            Assertions.assertTrue(ctx != null);
            contextRef.set(ctx);
            final CompletableFuture<Void> stage;
            try {
                stage = current.preDowngradeFromThis(ctx, cancellable);
            } catch (Throwable t) {
                handleDowngradeTerminal(holder, current, nextStatus, cancellable, hasDowngraded, t, pipeline);
                return;
            }
            stage.whenComplete((unused, t) -> {
                if (t != null) {
                    handleDowngradeTerminal(holder, current, nextStatus, cancellable, hasDowngraded, t, pipeline);
                } else {
                    try {
                        final boolean success = holder.setStatus(nextStatus, false);
                        Assertions.assertTrue(success, "setStatus on downgrade failed");

                        hasDowngraded.set(true);

                        final Ctx ctx2 = contextRef.get();
                        Objects.requireNonNull(ctx2);
                        final CompletableFuture<Void> stage2;
                        try {
                            stage2 = current.downgradeFromThis(ctx2, cancellable);
                        } catch (Throwable t2) {
                            handleDowngradeTerminal(holder, current, nextStatus, cancellable, hasDowngraded, t2, pipeline);
                            return;
                        }
                        stage2.whenComplete((unused2, t2) -> handleDowngradeTerminal(holder, current, nextStatus, cancellable, hasDowngraded, t2, pipeline));
                    } catch (Throwable t3) {
                        handleDowngradeTerminal(holder, current, nextStatus, cancellable, hasDowngraded, t3, pipeline);
                    }
                }
            });
        } catch (Throwable t) {
            handleDowngradeTerminal(holder, current, nextStatus, cancellable, hasDowngraded, t, pipeline);
        }
    }

    /**
     * Terminal handler of the downgrade transaction - replaces the original rxjava doOnEvent
     * block (which also ran for successful completion).
     */
    private void handleDowngradeTerminal(ItemHolder<K, V, Ctx, UserData> holder, ItemStatus<K, V, Ctx> current, ItemStatus<K, V, Ctx> nextStatus, Cancellable cancellable, AtomicBoolean hasDowngraded, Throwable throwable, CompletableFuture<Void> pipeline) {
        try {
            Assertions.assertTrue(holder.isBusy());

            {
                if (throwable != null) {
                    Throwable actual = throwable;
                    while (actual instanceof CompletionException ex) actual = ex.getCause();
                    if (cancellable.isCancelled() && actual instanceof CancellationException) {
                        if (hasDowngraded.get()) {
                            holder.setStatus(current, true);
                        }
                        holder.consolidateMarkDirty(this);
                        pipeline.complete(null);
                        return;
                    }
                }
            }

            final ExceptionHandlingAction action = this.tryHandleTransactionException(holder, nextStatus, false, throwable);
            switch (action) {
                case PROCEED -> {
                    releaseDependencies(holder, current);
                }
                case MARK_BROKEN -> {
                    holder.setFlag(ItemHolder.FLAG_BROKEN);
                    clearDependencies0(holder, current);
                }
            }
            holder.consolidateMarkDirty(this);
            this.onItemDowngrade(holder, nextStatus);
            pipeline.complete(null);
        } catch (Throwable t) {
            t.printStackTrace();
            pipeline.complete(null);
        }
    }

    private void advanceStatus0(ItemHolder<K, V, Ctx, UserData> holder, ItemStatus<K, V, Ctx> nextStatus, K key) {
        // Advance
        final KeyStatusPair<K, V, Ctx>[] dependencies = nextStatus.getDependencies(holder);
        final CancellationSignaller dependencyCompletable = getDependencyFuture0(dependencies, holder, nextStatus);
        Cancellable cancellable = new Cancellable();

        CancellationSignaller signaller = new CancellationSignaller(unused -> {
            cancellable.cancel();
            dependencyCompletable.cancel();
        });

        AtomicReference<Ctx> contextRef = new AtomicReference<>(null);

        // NOTE on faithfulness: the original fired the signaller inside its doOnEvent, i.e.
        // right after the upgrade succeeded and *before* the postUpgrade step. Here the firing
        // lands at pipeline completion (after postUpgrade). This is behaviourally equivalent
        // because a new upgrade cannot start until the busy ref-count reaches zero (pipeline
        // completion), so clearing runningUpgradeAction a moment earlier has no observable effect.
        // Single shared completion gate for the whole upgrade transaction. It is completed
        // exactly once at the end of the (upgrade + postUpgrade) flow, mirroring the rxjava
        // "Completable ... .onErrorComplete().cache()" that was subscribed twice (fireComplete
        // via the second subscribe part; bus-ref bookkeeping via subscribeOp).
        final CompletableFuture<Void> pipeline = new CompletableFuture<>();
        pipeline.whenComplete((unused, throwable) -> {
            try {
                signaller.fireComplete(null);
            } catch (Throwable t) {
                t.printStackTrace();
            }
        });

        holder.submitUpgradeAction(signaller, nextStatus);
        holder.subscribeOp(pipeline);

        // Start the chain: wait for dependencies. If already satisfied, this runs the whole
        // (possibly synchronous) upgrade synchronously on the current thread - matching the
        // original subscribe-driven behaviour.
        dependencyCompletable.addListener(depThrowable -> {
            if (depThrowable != null) {
                handleUpgradeError(holder, nextStatus, key, contextRef, cancellable, depThrowable, pipeline);
                return;
            }
            upgradeStage0(holder, nextStatus, key, dependencies, contextRef, cancellable, pipeline);
        });

        Assertions.assertTrue(holder.isBusy() || (cancellable.isCancelled() || holder.getStatus() == nextStatus));
    }

    /**
     * Runs the upgradeToThis stage after the dependencies are satisfied.
     */
    private void upgradeStage0(ItemHolder<K, V, Ctx, UserData> holder, ItemStatus<K, V, Ctx> nextStatus, K key, KeyStatusPair<K, V, Ctx>[] dependencies, AtomicReference<Ctx> contextRef, Cancellable cancellable, CompletableFuture<Void> pipeline) {
        try {
            Assertions.assertTrue(holder.isBusy());
            final Ctx ctx = makeContext(holder, nextStatus, dependencies, false);
            Assertions.assertTrue(ctx != null);
            contextRef.set(ctx);
            final CompletableFuture<Void> stage;
            try {
                stage = nextStatus.upgradeToThis(ctx, cancellable);
            } catch (Throwable t) {
                handleUpgradeError(holder, nextStatus, key, contextRef, cancellable, t, pipeline);
                return;
            }
            stage.whenComplete((unused, t) -> {
                if (t != null) {
                    handleUpgradeError(holder, nextStatus, key, contextRef, cancellable, t, pipeline);
                } else {
                    upgradeSuccess0(holder, nextStatus, key, contextRef, cancellable, pipeline);
                }
            });
        } catch (Throwable t) {
            handleUpgradeError(holder, nextStatus, key, contextRef, cancellable, t, pipeline);
        }
    }

    /**
     * Error handler of the upgrade transaction - replaces the original rxjava
     * onErrorResumeNext at the upgrade step.
     */
    private void handleUpgradeError(ItemHolder<K, V, Ctx, UserData> holder, ItemStatus<K, V, Ctx> nextStatus, K key, AtomicReference<Ctx> contextRef, Cancellable cancellable, Throwable throwable, CompletableFuture<Void> pipeline) {
        try {
            Assertions.assertTrue(holder.isBusy());

            {
                Throwable actual = throwable;
                while (actual instanceof CompletionException ex) actual = ex.getCause();
                if (cancellable.isCancelled() && actual instanceof CancellationException) {
                    if (holder.getDependencies(nextStatus) != null) {
                        releaseDependencies(holder, nextStatus);
                    }
                    holder.consolidateMarkDirty(this);
                    pipeline.complete(null);
                    return;
                }
            }

            Assertions.assertTrue(holder.getDependencies(nextStatus) != null);

            final ExceptionHandlingAction action = this.tryHandleTransactionException(holder, nextStatus, true, throwable);
            switch (action) {
                case PROCEED -> {
                    // rxjava: on error -> Completable.complete() -> continues as success
                }
                case MARK_BROKEN -> {
                    holder.setFlag(ItemHolder.FLAG_BROKEN);
                    clearDependencies0(holder, nextStatus);
                    holder.consolidateMarkDirty(this);
                    pipeline.complete(null);
                    return;
                }
                default -> {
                    throw new IllegalStateException("Unexpected value: " + action);
                }
            }
            // PROCEED reaches the success handler.
            upgradeSuccess0(holder, nextStatus, key, contextRef, cancellable, pipeline);
        } catch (Throwable t) {
            t.printStackTrace();
            if (throwable != null) {
                throwable.addSuppressed(t);
            }
            pipeline.complete(null);
        }
    }

    /**
     * Success handler of the upgrade transaction - replaces the original rxjava doOnEvent block.
     */
    private void upgradeSuccess0(ItemHolder<K, V, Ctx, UserData> holder, ItemStatus<K, V, Ctx> nextStatus, K key, AtomicReference<Ctx> contextRef, Cancellable cancellable, CompletableFuture<Void> pipeline) {
        try {
            holder.setStatus(nextStatus, false);
            rerequestDependencies(holder, nextStatus);
            holder.consolidateMarkDirty(this);
            this.onItemUpgrade(holder, nextStatus);
        } catch (Throwable t) {
            try {
                holder.setFlag(ItemHolder.FLAG_BROKEN);
                clearDependencies0(holder, nextStatus);
                holder.consolidateMarkDirty(this);
            } catch (Throwable t1) {
                t.addSuppressed(t1);
            }
            t.printStackTrace();
        }

        // rxjava: andThen -> postUpgradeToThis step
        postUpgrade0(holder, nextStatus, key, contextRef, cancellable, pipeline);
    }

    /**
     * Runs the postUpgradeToThis stage - replaces rxjava's
     * andThen(Completable.defer(postUpgradeToThis).cache().onErrorResumeNext(...)).
     */
    private void postUpgrade0(ItemHolder<K, V, Ctx, UserData> holder, ItemStatus<K, V, Ctx> nextStatus, K key, AtomicReference<Ctx> contextRef, Cancellable cancellable, CompletableFuture<Void> pipeline) {
        final CompletableFuture<Void> stage;
        try {
            final Ctx ctx = contextRef.get();
            Assertions.assertTrue(ctx != null);
            stage = nextStatus.postUpgradeToThis(ctx);
        } catch (Throwable t) {
            handlePostUpgradeError(holder, nextStatus, key, t, pipeline);
            return;
        }
        stage.whenComplete((unused, t) -> {
            if (t != null) {
                handlePostUpgradeError(holder, nextStatus, key, t, pipeline);
            } else {
                pipeline.complete(null);
            }
        });
    }

    /**
     * Error handler for the postUpgrade step - replaces the rxjava onErrorResumeNext there.
     * The outer chain ended with .onErrorComplete(), so the pipeline always completes
     * successfully at this point (errors are swallowed) - mirroring the original behaviour.
     */
    private void handlePostUpgradeError(ItemHolder<K, V, Ctx, UserData> holder, ItemStatus<K, V, Ctx> nextStatus, K key, Throwable throwable, CompletableFuture<Void> pipeline) {
        try {
            final ExceptionHandlingAction action = this.tryHandleTransactionException(holder, nextStatus, true, throwable);
            switch (action) {
                case PROCEED -> {
                    // Completable.complete()
                }
                case MARK_BROKEN -> {
                    holder.setFlag(ItemHolder.FLAG_BROKEN);
                    holder.consolidateMarkDirty(this);
                    holder.executeCriticalSectionAndBusy(() -> {
                        holder.busyRefCounter().incrementRefCount();
                        try {
                            downgradeStatus0(holder, nextStatus, nextStatus.getPrev(), key);
                        } finally {
                            holder.busyRefCounter().decrementRefCount();
                        }
                    });
                }
                default -> {
                    throw new IllegalStateException("Unexpected value: " + action);
                }
            }
        } catch (Throwable t) {
            t.printStackTrace();
            if (throwable != null) {
                throwable.addSuppressed(t);
            }
        } finally {
            pipeline.complete(null);
        }
    }

    private void rerequestDependencies(ItemHolder<K, V, Ctx, UserData> holder, ItemStatus<K, V, Ctx> status) { // sync externally
        final KeyStatusPair<K, V, Ctx>[] curDep = holder.getDependencies(status);
        final KeyStatusPair<K, V, Ctx>[] newDep = status.getDependencies(holder);
        final KeyStatusPair<K, V, Ctx>[] toAdd = status.getDependenciesToAdd(holder);
        final KeyStatusPair<K, V, Ctx>[] toRemove = status.getDependenciesToRemove(holder);
        holder.setDependencies(status, null);
        holder.setDependencies(status, newDep);
        for (KeyStatusPair<K, V, Ctx> pair : toAdd) {
            holder.addDependencyTicket(this, pair.key(), pair.status(), NO_OP);
        }
        for (KeyStatusPair<K, V, Ctx> pair : toRemove) {
            holder.removeDependencyTicket(pair.key(), pair.status());
        }
    }

    public ItemHolder<K, V, Ctx, UserData> getHolder(K key) {
        long stamp = this.itemsLock.tryOptimisticRead();
        if (stamp != 0L) {
            try {
                ItemHolder<K, V, Ctx, UserData> holder = this.items.get(key);
                if (this.itemsLock.validate(stamp)) {
                    return holder;
                }
                // fall through
            } catch (Throwable ignored) {
                // fall through
            }
        }

        stamp = this.itemsLock.readLock();
        try {
            return this.items.get(key);
        } finally {
            this.itemsLock.unlockRead(stamp);
        }
    }

    private ItemHolder<K, V, Ctx, UserData> getOrCreateHolder(K key) {
        final ItemHolder<K, V, Ctx, UserData> holder = getHolder(key);
        if (holder != null) {
            return holder;
        }
        final long lock = this.itemsLock.writeLock();
        try {
            return this.items.computeIfAbsent(key, this::createHolder);
        } finally {
            this.itemsLock.unlockWrite(lock);
        }
    }

    public int itemCount() {
        VarHandle.acquireFence();
        return this.items.size();
    }

    protected void wakeUp() {
    }

    private CancellationSignaller getDependencyFuture0(KeyStatusPair<K, V, Ctx>[] dependencies, ItemHolder<K, V, Ctx, UserData> holder, ItemStatus<K, V, Ctx> nextStatus) {
        AtomicInteger satisfied = new AtomicInteger(0);
        final int size = dependencies.length;
        holder.setDependencies(nextStatus, dependencies);
        if (size == 0) {
            return CancellationSignaller.COMPLETED;
        }

        AtomicBoolean finished = new AtomicBoolean(false);
        final CancellationSignaller signaller = new CancellationSignaller(signaller1 -> {
            if (satisfied.get() == 0); // intentional no-op preserved from original
            if (finished.compareAndSet(false, true)) {
                releaseDependencies(holder, nextStatus);
                signaller1.fireComplete(new CancellationException());
            }
        });
        try {
            final KeyStatusPair<K, V, Ctx> keyStatusPair = new KeyStatusPair<>(holder.getKey(), nextStatus);
            for (KeyStatusPair<K, V, Ctx> dependency : dependencies) {
                Assertions.assertTrue(!dependency.key().equals(holder.getKey()));
                holder.addDependencyTicket(this, dependency.key(), dependency.status(), () -> {
//                    Assertions.assertTrue(this.getHolder(dependency.key()).getStatus().ordinal() >= dependency.status().ordinal());
                    final int incrementAndGet = satisfied.incrementAndGet();
                    Assertions.assertTrue(incrementAndGet <= size, "Satisfied more than expected");
                    if (incrementAndGet == size) {
                        if (finished.compareAndSet(false, true)) {
                            holder.getCriticalSectionExecutor().execute(() -> signaller.fireComplete(null));
                        }
                    }
                });
            }
        } catch (Throwable t) {
            signaller.fireComplete(t);
        }
        return signaller;
    }

    public ItemHolder<K, V, Ctx, UserData> addTicket(K key, ItemStatus<K, V, Ctx> targetStatus, Runnable callback) {
        return this.addTicket(key, key, targetStatus, callback);
    }

    public ItemHolder<K, V, Ctx, UserData> addTicket(K key, Object source, ItemStatus<K, V, Ctx> targetStatus, Runnable callback) {
        return this.addTicket(key, ItemTicket.TicketType.EXTERNAL, source, targetStatus, callback);
    }

    public ItemHolder<K, V, Ctx, UserData> addTicket(K key, ItemTicket.TicketType type, Object source, ItemStatus<K, V, Ctx> targetStatus, Runnable callback) {
        return this.addTicket0(key, new ItemTicket<>(type, source, targetStatus, callback));
    }

    private ItemHolder<K, V, Ctx, UserData> addTicket0(K key, ItemTicket<K, V, Ctx> ticket) {
        if (this.getUnloadedStatus().equals(ticket.getTargetStatus())) {
            throw new IllegalArgumentException("Cannot add ticket to unloaded status");
        }
        try {
            while (true) {
                ItemHolder<K, V, Ctx, UserData> holder = this.getOrCreateHolder(key);

                synchronized (holder) {
                    if (!holder.isOpen()) {
                        // holder got removed before we had chance to add a ticket to it, retry
//                        System.out.println(String.format("Retrying addTicket0(%s, %s)", key, ticket));
                        continue;
                    }
                    holder.busyRefCounter().incrementRefCount();
                }
                try {
                    holder.addTicket(ticket);
                    holder.consolidateMarkDirty(this);
                } finally {
                    holder.busyRefCounter().decrementRefCount();
                }
                return holder;
            }
        } catch (Throwable t) {
            t.printStackTrace();
            throw new RuntimeException(t);
        }
    }

    private ItemHolder<K, V, Ctx, UserData> createHolder(K k) {
        final ItemHolder<K, V, Ctx, UserData> holder1 = new ItemHolder<>(this.getUnloadedStatus(), k, this.objectFactory, this.getBackgroundExecutor());
        this.onItemCreation(holder1);
        VarHandle.fullFence();
        return holder1;
    }

    public void removeTicket(K key, ItemStatus<K, V, Ctx> targetStatus) {
        this.removeTicket(key, ItemTicket.TicketType.EXTERNAL, key, targetStatus);
    }

    public void removeTicket(K key, ItemTicket.TicketType type, Object source, ItemStatus<K, V, Ctx> targetStatus) {
        ItemHolder<K, V, Ctx, UserData> holder = this.getHolder(key);
        if (holder == null) {
            throw new IllegalStateException("No such item");
        }
        holder.removeTicket(new ItemTicket<>(type, source, targetStatus, null));
        // holder may have been removed at this point, only mark it dirty if it still exists
        holder.tryMarkDirty(this);
    }

    private ItemStatus<K, V, Ctx> getNextStatus(ItemStatus<K, V, Ctx> current, ItemStatus<K, V, Ctx> target) {
        Assertions.assertTrue(target != null);
        final int compare = Integer.compare(current.ordinal(), target.ordinal());
        if (compare < 0) {
            return current.getNext();
        } else if (compare == 0) {
            return current;
        } else {
            return current.getPrev();
        }
    }

    private ExceptionHandlingAction tryHandleTransactionException(ItemHolder<K, V, Ctx, UserData> holder, ItemStatus<K, V, Ctx> nextStatus, boolean isUpgrade, Throwable throwable) {
        if (throwable == null) { // no exception to handle
            return ExceptionHandlingAction.PROCEED;
        }
        try {
            return this.handleTransactionException(holder, nextStatus, isUpgrade, throwable);
        } catch (Throwable t) {
            t.printStackTrace();
            return ExceptionHandlingAction.MARK_BROKEN;
        }
    }

    private void clearDependencies0(final ItemHolder<K, V, Ctx, UserData> holder, final ItemStatus<K, V, Ctx> fromStatus) { // sync externally
        for (int i = fromStatus.ordinal(); i > 0; i--) {
            final ItemStatus<K, V, Ctx> status = this.getUnloadedStatus().getAllStatuses()[i];
            this.releaseDependencies(holder, status);
            holder.setDependencies(status, new KeyStatusPair[0]);
        }
    }

    private void releaseDependencies(ItemHolder<K, V, Ctx, UserData> holder, ItemStatus<K, V, Ctx> status) {
        final KeyStatusPair<K, V, Ctx>[] dependencies = holder.getDependencies(status);
        for (KeyStatusPair<K, V, Ctx> dependency : dependencies) {
            holder.removeDependencyTicket(dependency.key(), dependency.status());
        }
        holder.setDependencies(status, null);
    }

}