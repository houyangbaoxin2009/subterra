// Ported from FlowSched (MIT, Copyright (c) 2023 ishland, https://github.com/ishland/FlowSched)
// into io.toterra.subterra.engine.optim.sched (p.1.4.23). FlowSched is distributed under the MIT license.
package io.toterra.subterra.engine.optim.sched.scheduler;

import java.util.concurrent.atomic.AtomicBoolean;

public class Cancellable {

    private final AtomicBoolean cancelled = new AtomicBoolean(false);

    public void cancel() {
        this.cancelled.set(true);
    }

    public boolean isCancelled() {
        return this.cancelled.get();
    }

}