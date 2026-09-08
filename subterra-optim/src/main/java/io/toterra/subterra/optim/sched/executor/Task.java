// Ported from FlowSched (MIT, Copyright (c) 2023 ishland, https://github.com/ishland/FlowSched)
// into io.toterra.subterra.optim.sched (p.1.4.23). FlowSched is distributed under the MIT license.
package io.toterra.subterra.optim.sched.executor;

public interface Task {

    void run(Runnable releaseLocks);

    void propagateException(Throwable t);

    LockToken[] lockTokens();

    int priority();

}