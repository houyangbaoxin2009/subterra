// Deterministic acceptance probe for the p.2.7.2 bounded serial worker bank
// (io.toterra.subterra.engine.parallel). NOT shipped in the mod jar.
package io.toterra.subterra.probes;

import io.toterra.subterra.engine.parallel.WorkerBank;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Deterministic acceptance probe for the p.2.7.2 bounded serial worker bank
 * (io.toterra.subterra.engine.parallel), the key-agnostic generalization of the
 * p.2.6 {@code AsyncWorkerBank}. Asserts exactly-once single-bank execution,
 * idempotent awaitIdle final-state join, shutdown drain (accepted tasks all run),
 * idempotent shutdown, closed-bank submit rejection, a 4-bank concurrent
 * final-state, and the documented CAPACITY contract. Every check is a final-state
 * assertion after the join — never a wall-clock/timing assertion. Exit 0 = PASS,
 * 1 = FAIL (never shipped in the mod jar).
 *
 * <p>p.2.7.2 通用有界串行工作 lane（io.toterra.subterra.engine.parallel）的确定性
 * 验收探针，是 p.2.6 {@code AsyncWorkerBank} 的键无关通用化。断言单 bank 恰好一次
 * 执行、awaitIdle 幂等终态 join、shutdown drain（已接受任务全部执行完）、shutdown
 * 幂等、关闭后提交拒绝、4 bank 并发终态，以及 CAPACITY 文档契约值。所有检查都是
 * join 之后的终态断言，绝不依赖墙钟/时序。退出码 0 = PASS，1 = FAIL（永不随
 * mod jar 发布）。
 */
public final class ParallelBankProbe {

    private ParallelBankProbe() {
    }

    private static int failures = 0;
    private static int checks = 0;

    private static void check(String name, boolean ok) {
        checks++;
        if (ok) {
            System.out.println("[PASS] " + name);
        } else {
            failures++;
            System.out.println("[FAIL] " + name);
        }
    }

    public static void main(String[] args) {
        // ---- 1. exactly-once single bank ---------------------------------------
        WorkerBank bank = new WorkerBank(0);
        AtomicInteger counter = new AtomicInteger();
        for (int i = 0; i < 8192; i++) {
            bank.submit(counter::incrementAndGet);
        }
        bank.awaitIdle();
        check("exactly-once: submit 8192 -> submitted==executed==8192, counter==8192, queued()==0",
                bank.submittedCount() == 8192 && bank.executedCount() == 8192
                        && counter.get() == 8192 && bank.queued() == 0);

        // ---- 2. awaitIdle idempotent final-state join --------------------------
        int before = counter.get();
        bank.awaitIdle();
        check("awaitIdle: second awaitIdle returns immediately, counter unchanged (idempotent join)",
                counter.get() == before && bank.queued() == 0);

        // ---- 3. shutdown drain: accepted tasks all run, no awaitIdle -----------
        WorkerBank drainBank = new WorkerBank(1);
        AtomicInteger drainCounter = new AtomicInteger();
        for (int i = 0; i < 2048; i++) {
            drainBank.submit(drainCounter::incrementAndGet);
        }
        drainBank.shutdown();
        check("shutdown drain: submit 2048 then shutdown() directly -> executed==submitted==2048,"
                + " counter==2048", drainBank.executedCount() == 2048
                && drainBank.submittedCount() == 2048 && drainCounter.get() == 2048);

        // ---- 4. shutdown idempotent --------------------------------------------
        boolean secondShutdownOk = true;
        try {
            drainBank.shutdown();
        } catch (RuntimeException re) {
            secondShutdownOk = false;
        }
        check("shutdown idempotent: second shutdown() on same bank throws nothing",
                secondShutdownOk);

        // ---- 5. closed bank rejects submit, counts unchanged --------------------
        boolean rejected = false;
        try {
            drainBank.submit(() -> {
            });
        } catch (IllegalStateException ise) {
            rejected = true;
        }
        check("closed submit reject: submit after shutdown throws IllegalStateException,"
                + " submitted/executed counts unchanged",
                rejected && drainBank.submittedCount() == 2048 && drainBank.executedCount() == 2048);

        // ---- 6. multi-bank concurrent final-state ------------------------------
        int banks = 4;
        int perBank = 2000;
        WorkerBank[] multi = new WorkerBank[banks];
        AtomicInteger total = new AtomicInteger();
        for (int i = 0; i < banks; i++) {
            multi[i] = new WorkerBank(10 + i);
            for (int j = 0; j < perBank; j++) {
                multi[i].submit(total::incrementAndGet);
            }
        }
        boolean allIdle = true;
        for (int i = 0; i < banks; i++) {
            multi[i].awaitIdle();
            if (multi[i].submittedCount() != perBank || multi[i].executedCount() != perBank) {
                allIdle = false;
            }
        }
        check("multi-bank: 4 banks x 2000 -> each submitted==executed==2000, total==8000",
                allIdle && total.get() == banks * perBank);

        // ---- 7. CAPACITY contract ----------------------------------------------
        check("constant: CAPACITY==4096 (>0, documented contract value)",
                WorkerBank.CAPACITY == 4096 && WorkerBank.CAPACITY > 0);

        if (failures == 0) {
            System.out.println("[ParallelBankProbe] PASS (" + checks + " checks)");
            System.exit(0);
        } else {
            System.out.println("[ParallelBankProbe] FAIL: " + failures + " assertion(s) of " + checks);
            System.exit(1);
        }
    }
}
