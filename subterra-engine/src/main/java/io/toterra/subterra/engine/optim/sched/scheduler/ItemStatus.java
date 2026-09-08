// Ported from FlowSched (MIT, Copyright (c) 2023 ishland, https://github.com/ishland/FlowSched)
// into io.toterra.subterra.engine.optim.sched (p.1.4.23). FlowSched is distributed under the MIT license.
// RxJava replacement: the four reactive hooks below previously returned
// io.reactivex.rxjava3.core.Completable. Completable is a void async op, so it
// maps 1:1 onto a CompletableFuture<Void> (see StatusAdvancingScheduler for how
// the completion chains are replumbed into pure-JDK continuations).
package io.toterra.subterra.engine.optim.sched.scheduler;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Represents the status of an item.
 * <p>
 * Implementations must also implement {@link Comparable}, and higher statuses must be greater than lower statuses.
 *
 * @param <Ctx> the context type
 */
public interface ItemStatus<K, V, Ctx> {

    @SuppressWarnings("rawtypes")
    static KeyStatusPair[] EMPTY_DEPENDENCIES = new KeyStatusPair[0];

    default ItemStatus<K, V, Ctx> getPrev() {
        if (this.ordinal() > 0) {
            return getAllStatuses()[this.ordinal() - 1];
        } else {
            return null;
        }
    }

    default ItemStatus<K, V, Ctx> getNext() {
        final ItemStatus<K, V, Ctx>[] allStatuses = getAllStatuses();
        if (this.ordinal() < allStatuses.length - 1) {
            return allStatuses[this.ordinal() + 1];
        } else {
            return null;
        }
    }

    ItemStatus<K, V, Ctx>[] getAllStatuses();

    int ordinal();

    CompletableFuture<Void> upgradeToThis(Ctx context, Cancellable cancellable);

    CompletableFuture<Void> postUpgradeToThis(Ctx context);

    CompletableFuture<Void> preDowngradeFromThis(Ctx context, Cancellable cancellable);

    /**
     * @implNote cancelling the given cancellable here is discouraged. If implementations do cancel here, postUpgrade hook will not be called after this.
     */
    CompletableFuture<Void> downgradeFromThis(Ctx context, Cancellable cancellable);

    /**
     * Get the dependencies of the given item at the given status.
     * <p>
     * The returned collection must not contain the given item itself.
     *
     * @param holder the item holder
     * @return the dependencies
     */
    KeyStatusPair<K, V, Ctx>[] getDependencies(ItemHolder<K, V, Ctx, ?> holder);

    // fastutil ObjectOpenHashSet (value semantics) -> java.util.HashSet. It is
    // used to diff current vs next dependencies; KeyStatusPair provides real
    // equals/hashCode, so value semantics are required and set-difference works.
    default KeyStatusPair<K, V, Ctx>[] getDependenciesToRemove(ItemHolder<K, V, Ctx, ?> holder) {
        final KeyStatusPair<K, V, Ctx>[] curDep = holder.getDependencies(this);
        final KeyStatusPair<K, V, Ctx>[] newDep = this.getDependencies(holder);
        final Set<KeyStatusPair<K, V, Ctx>> toRemove = new HashSet<>(curDep.length * 2);
        for (KeyStatusPair<K, V, Ctx> pair : curDep) {
            toRemove.add(pair);
        }
        for (KeyStatusPair<K, V, Ctx> pair : newDep) {
            toRemove.remove(pair);
        }
        return toRemove.toArray(new KeyStatusPair[0]);
    }

    default KeyStatusPair<K, V, Ctx>[] getDependenciesToAdd(ItemHolder<K, V, Ctx, ?> holder) {
        final KeyStatusPair<K, V, Ctx>[] curDep = holder.getDependencies(this);
        final KeyStatusPair<K, V, Ctx>[] newDep = this.getDependencies(holder);
        final Set<KeyStatusPair<K, V, Ctx>> toAdd = new HashSet<>(newDep.length * 2);
        for (KeyStatusPair<K, V, Ctx> pair : newDep) {
            toAdd.add(pair);
        }
        for (KeyStatusPair<K, V, Ctx> pair : curDep) {
            toAdd.remove(pair);
        }
        return toAdd.toArray(new KeyStatusPair[0]);
    }

}