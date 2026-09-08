package io.toterra.subterra.engine.optim.util;

import java.util.ArrayDeque;
import java.util.function.Supplier;

/**
 * Self-developed optimization (p.1.4): a bounded object pool for hot paths
 * (particles, network buffers, scratch records) that avoid per-use allocation.
 * <p>
 * Contract: {@link #acquire()} returns either a recycled instance or a freshly
 * factory-created one; {@link #release(Object)} returns an instance to the pool
 * (idempotently ignored when the pool is full). The pool never outgrows its
 * capacity, so memory stays capped. Deterministic, probe-gated, pure JVM.
 */
public final class ScratchPool<T> {

    private final ArrayDeque<T> recycled;
    private final Supplier<T> factory;
    private final int capacity;
    private long creates;
    private long borrows;

    /**
     * @param capacity maximum pooled (idle) instances; {@code <= 0} disables pooling
     * @param factory creates a fresh instance on demand
     */
    public ScratchPool(int capacity, Supplier<T> factory) {
        if (factory == null) {
            throw new IllegalArgumentException("factory must not be null");
        }
        this.capacity = Math.max(0, capacity);
        this.factory = factory;
        this.recycled = new ArrayDeque<>(Math.min(this.capacity, 16));
    }

    /** Takes an instance from the pool or creates one; never returns null. */
    public T acquire() {
        T item = recycled.pollFirst();
        if (item == null) {
            item = factory.get();
            creates++;
        }
        borrows++;
        return item;
    }

    /** Returns an instance to the pool (dropped when the pool is at capacity). */
    public void release(T item) {
        if (item == null || recycled.size() >= capacity) {
            return;
        }
        recycled.addLast(item);
    }

    /** Currently idle (recycled, immediately reusable) instances. */
    public int idleCount() {
        return recycled.size();
    }

    /** Total instances created by the factory (allocation avoidance metric). */
    public long createCount() {
        return creates;
    }

    /** Total times acquire() was called. */
    public long borrowCount() {
        return borrows;
    }
}