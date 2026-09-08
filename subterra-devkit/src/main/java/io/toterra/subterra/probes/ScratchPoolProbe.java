package io.toterra.subterra.probes;

import io.toterra.subterra.engine.optim.util.ScratchPool;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Deterministic acceptance probe for the first self-developed optimization
 * (p.1.4): {@link ScratchPool}. Pure JVM.
 * <p>
 * Asserts the object-pool contract: capacity capping, instance reuse
 * (identity across acquire/release cycles), factory call counting, and
 * allocation avoidance under a borrow/release workload.
 * Exit 0 = PASS, exit 1 = FAIL (gates acceptance; never shipped in the mod jar).
 */
public final class ScratchPoolProbe {

    private ScratchPoolProbe() {
    }

    public static void main(String[] args) {
        int failures = 0;

        AtomicInteger created = new AtomicInteger();
        AtomicLong counter = new AtomicLong();
        ScratchPool<Long> pool = new ScratchPool<>(4, () -> {
            created.incrementAndGet();
            return counter.getAndIncrement();
        });

        if (!check("fresh acquire creates", pool.acquire() != null && created.get() == 1 && pool.borrowCount() == 1)) failures++;
        if (!check("initial idle 0", pool.idleCount() == 0)) failures++;

        Long first = pool.acquire();   // created=2
        pool.release(first);
        if (!check("idle after release", pool.idleCount() == 1)) failures++;
        Long same = pool.acquire();    // must reuse first instance
        if (!check("instance reuse identity", same == first)) failures++;
        if (!check("no extra create on reuse", created.get() == 2)) failures++;

        // Fill the pool to its cap (4), then release 6 more — only 4 stay.
        Long[] held = new Long[4];
        for (int i = 0; i < 4; i++) {
            held[i] = pool.acquire();   // creates 3..6
        }
        if (!check("cap keeps factory bounded", created.get() == 6 && pool.idleCount() == 0)) failures++;
        for (Long h : held) {
            pool.release(h);            // back to 4 idle
        }
        for (int i = 0; i < 6; i++) {
            pool.release(counter.getAndIncrement()); // over the cap: dropped
        }
        if (!check("capacity capped", pool.idleCount() == 4)) failures++;

        // Allocation avoidance: 1000 acquire/release with a warm pool of 4.
        long createsBefore = created.get();
        for (int i = 0; i < 1000; i++) {
            Long v = pool.acquire();
            pool.release(v);
        }
        if (!check("heat avoids allocation", created.get() == createsBefore)) failures++;

        if (failures == 0) {
            System.out.println("[ScratchPoolProbe] PASS (creates=" + created.get()
                    + " borrows=" + pool.borrowCount() + ")");
            System.exit(0);
        } else {
            System.out.println("[ScratchPoolProbe] FAIL: " + failures + " assertion(s)");
            System.exit(1);
        }
    }

    private static boolean check(String what, boolean ok) {
        if (!ok) {
            System.out.println("[ScratchPoolProbe] FAIL " + what);
        }
        return ok;
    }
}