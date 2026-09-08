// Ported from FlowSched (MIT, Copyright (c) 2023 ishland, https://github.com/ishland/FlowSched)
// into io.toterra.subterra.engine.optim.sched (p.1.4.23). FlowSched is distributed under the MIT license.
package io.toterra.subterra.engine.optim.sched.structs;

/**
 * An immutable pair of values. Minimal JDK replacement for fastutil's
 * {@code it.unimi.dsi.fastutil.Pair}, which was used by the ported FlowSched
 * scheduler (left/right accessors and {@link #of(Object,Object)} factory).
 * This pair replaces the external fastutil dependency with pure JDK code.
 */
public record Pair<L, R>(L left, R right) {

    public static <L, R> Pair<L, R> of(L left, R right) {
        return new Pair<>(left, right);
    }

}