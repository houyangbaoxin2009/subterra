// Ported from FlowSched (MIT, Copyright (c) 2023 ishland, https://github.com/ishland/FlowSched)
// into io.toterra.subterra.optim.sched (p.1.4.23). FlowSched is distributed under the MIT license.
package io.toterra.subterra.optim.sched.scheduler;

import io.toterra.subterra.optim.sched.util.Assertions;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

// fastutil ReferenceList (identity list) -> java.util.ArrayList. Only add /
// toArray / clear / isEmpty are used, and the stored runnables are unique
// instances, so equals-vs-identity is irrelevant here.
public class BusyRefCounter {

    private final List<Runnable> onComplete = new ArrayList<>();
    private Runnable onCompleteOnce = null;
    private volatile int counter = 0;

    public synchronized boolean isBusy() {
        return counter != 0;
    }

    public void addListener(Runnable runnable) {
        Objects.requireNonNull(runnable);
        boolean runNow = false;
        synchronized (this) {
            if (!isBusy()) {
                runNow = true;
            } else {
                onComplete.add(runnable);
            }
        }
        if (runNow) {
            runnable.run();
        }
    }

    public void addListenerOnce(Runnable runnable) {
        Objects.requireNonNull(runnable);
        boolean runNow = false;
        synchronized (this) {
            if (!isBusy()) {
                runNow = true;
            } else {
                onCompleteOnce = runnable;
            }
        }
        if (runNow) {
            runnable.run();
        }
    }

    public synchronized void incrementRefCount() {
        counter ++;
    }

    public void decrementRefCount() {
        Runnable[] onCompleteArray = null;
        Runnable onCompleteOnce = null;
        synchronized (this) {
            Assertions.assertTrue(counter > 0);
            if (--counter == 0) {
                onCompleteArray = onComplete.toArray(Runnable[]::new);
                onComplete.clear();
            }
            onCompleteOnce = this.onCompleteOnce;
            this.onCompleteOnce = null;
        }
        if (onCompleteArray != null) {
            for (Runnable runnable : onCompleteArray) {
                try {
                    runnable.run();
                } catch (Throwable t) {
                    t.printStackTrace();
                }
            }
        }
        if (onCompleteOnce != null) {
            try {
                onCompleteOnce.run();
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }
    }

}