// Ported from FlowSched (MIT, Copyright (c) 2023 ishland, https://github.com/ishland/FlowSched)
// into io.toterra.subterra.engine.optim.sched (p.1.4.23). FlowSched is distributed under the MIT license.
package io.toterra.subterra.engine.optim.sched.scheduler;

public enum ExceptionHandlingAction {

    /**
     * Ignore the exception and proceed to continue
     */
    PROCEED,
    /**
     * Abort the transaction and clear all dependencies, marking it broken
     */
    MARK_BROKEN,

}