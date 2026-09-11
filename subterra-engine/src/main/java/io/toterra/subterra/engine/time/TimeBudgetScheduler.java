// Self-contained engine.time deterministic per-tick budget scheduler (p.2.26.3).
// Pure JDK; no net.minecraft / net.neoforged dependency. REUSES the p.2.8
// engine.sim.budget accounting kernel (BudgetScheduler / TickBudget / RegionKey)
// rather than reimplementing it: each TimeDomain is backed by one
// sim.budget.BudgetScheduler that performs the atomic, overflow-free, fixed-order
// charge/accounting.
package io.toterra.subterra.engine.time;

import io.toterra.subterra.engine.sim.budget.BudgetScheduler;
import io.toterra.subterra.engine.sim.budget.RegionKey;
import io.toterra.subterra.engine.sim.budget.TickBudget;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Deterministic per-tick time-budget scheduler (p.2.26.3). Each {@link TimeDomain}
 * is given a budget cap via {@link #tickBudget(TimeDomain, long)} (registered in
 * fixed enumeration order; re-registering the same domain is rejected). Candidate
 * {@link TimeBudgetKey}s are added via {@link #register(TimeDomain, long)} in
 * fixed order. For a tick, call {@link #reset(long)} to atomically open a fresh,
 * zeroed accounting window, then {@link #schedule(long)} to obtain exactly the
 * keys that run this tick.
 *
 * <p><b>Scheduling determinism.</b> {@link #schedule(long)} walks the domains in
 * fixed {@link TimeDomain} order and, within each domain, the registered simulants
 * in ascending id order. Each key is charged 1 unit against its domain's budget
 * pool; a key is included only while the pool stays within the cap, otherwise it is
 * deterministically deferred (skipped). The result is therefore ordered and
 * reproducible: the same (registration set, cap, tick) always yields the same byte
 * stream. No randomness, no timing, no wall-clock, no iteration-order sensitivity.
 *
 * <p><b>Relation to p.2.8 ({@code engine.sim.budget}).</b> The per-domain budget
 * pool is <em>not</em> reimplemented: each domain is backed by one
 * {@link BudgetScheduler} (with all three tiers {@code = cap}, a fixed per-domain
 * {@link RegionKey}, and one charge per candidate). The scheduling delegate thereby
 * inherits p.2.8's atomic all-or-nothing accounting and its overflow-free
 * {@code units > cap - used} comparisons; an over-cap key is skipped exactly like a
 * p.2.8 {@code DEFERRED}. {@link #reset(long)} atomically allocates fresh kernels so
 * per-tick state starts identically every tick.
 *
 * <p>时间域确定性每 tick 预算调度器（p.2.26.3）。每个 {@link TimeDomain} 经
 * {@link #tickBudget(TimeDomain, long)} 给予一个预算上限（按固定枚举序注册；同域重复注册被拒绝）。
 * 候选 {@link TimeBudgetKey} 经 {@link #register(TimeDomain, long)} 按固定序加入。对某一 tick，先
 * {@link #reset(long)} 原子地开启一个归零的全新记账窗口，再 {@link #schedule(long)} 获得本 tick
 * 恰好要执行的键集合。
 *
 * <p><b>调度确定性。</b>{@link #schedule(long)} 按固定 {@link TimeDomain} 序遍历域，域内按升序
 * simulant 号遍历已注册候选。每个键对其所在域的预算池计费 1 单位，仅当池仍在上限之内才纳入，否则被
 * 确定性推迟（跳过）。故结果有序且可复现：同一（注册集、上限、tick）恒得同字节流。无随机、无时序、
 * 无墙钟、无迭代序敏感。
 *
 * <p><b>与 p.2.8（{@code engine.sim.budget}）的关系。</b>按域预算池并<em>非</em>重新实现：每域由一个
 * {@link BudgetScheduler} 承载（三 tier 皆 {@code = cap}、固定的按域 {@link RegionKey}、每候选计费一次）。
 * 调度委托由此继承 p.2.8 的原子全有或全无记账与无溢出 {@code units > cap - used} 比较；超上限键的跳过
 * 恰同 p.2.8 的 {@code DEFERRED}。{@link #reset(long)} 原子地分配全新内核，使每 tick 初始状态逐位一致。
 */
public final class TimeBudgetScheduler {

    /**
     * Per-domain state: the accounting kernel plus the fixed-order candidate set.
     * / 按域状态：记账内核加固定序候选集。
     */
    private static final class Slot {
        private final long cap;
        private final RegionKey region;
        private BudgetScheduler<Long> scheduler;
        private final TreeSet<Long> registered = new TreeSet<>();

        private Slot(long cap, RegionKey region, BudgetScheduler<Long> scheduler) {
            this.cap = cap;
            this.region = region;
            this.scheduler = scheduler;
        }
    }

    /** Domains keyed in fixed {@link TimeDomain} enumeration order. */
    private final TreeMap<TimeDomain, Slot> slots = new TreeMap<>();

    /** The tick most recently opened by {@link #reset(long)}; -1 means never reset. */
    private long currentTick = -1L;

    /**
     * Registers a budget cap for a domain. The domain is inserted in fixed
     * {@link TimeDomain} enumeration order; registering the same domain more than
     * once is rejected with {@link IllegalArgumentException}. The cap must be
     * {@code >= 1} (validated by the p.2.8 {@link TickBudget}).
     * / 为某域注册预算上限。域按固定 {@link TimeDomain} 枚举序插入；同域重复注册以
     * {@link IllegalArgumentException} 拒绝。上限必须 {@code >= 1}（由 p.2.8 {@link TickBudget} 校验）。
     *
     * @param domain the time domain (non-null).
     * @param cap    the per-domain budget cap in units; must be {@code >= 1}.
     * @return this scheduler, for chaining.
     * @throws IllegalArgumentException if the domain is already registered, or if {@code cap < 1}.
     * @throws NullPointerException     if {@code domain} is null.
     */
    public TimeBudgetScheduler tickBudget(TimeDomain domain, long cap) {
        Objects.requireNonNull(domain, "domain");
        if (slots.containsKey(domain)) {
            throw new IllegalArgumentException("domain already has a tick budget: " + domain);
        }
        // All three tiers equal cap -> the effective per-domain pool is `cap`. A fixed
        // per-domain RegionKey adapts the (non-spatial) time domain to p.2.8's region tier.
        TickBudget b = TickBudget.of(cap, cap, cap);
        Slot slot = new Slot(cap, new RegionKey(domain.ordinal(), 0L), BudgetScheduler.create(b, 0));
        slots.put(domain, slot);
        return this;
    }

    /**
     * Registers a candidate {@link TimeBudgetKey}'s simulant id under a domain, in
     * deterministic ascending order. Registering the same id twice is idempotent.
     * The domain must already have a budget (see {@link #tickBudget(TimeDomain, long)}).
     * / 在某个域下以一个 simulant 号注册候选 {@link TimeBudgetKey}，按确定性升序排列。重复注册同一 id
     * 幂等。该域必须已有预算（见 {@link #tickBudget(TimeDomain, long)}）。
     *
     * @param domain     the budgeted time domain (non-null).
     * @param simulantId the simulant id to register.
     * @return this scheduler, for chaining.
     * @throws IllegalArgumentException if the domain has no budget yet.
     * @throws NullPointerException     if {@code domain} is null.
     */
    public TimeBudgetScheduler register(TimeDomain domain, long simulantId) {
        Objects.requireNonNull(domain, "domain");
        Slot slot = slots.get(domain);
        if (slot == null) {
            throw new IllegalArgumentException("no tick budget registered for domain: " + domain);
        }
        slot.registered.add(simulantId);
        return this;
    }

    /**
     * Atomically opens a fresh, zeroed tick window for {@code tick}. Every domain's
     * accounting kernel is replaced with a new p.2.8 {@link BudgetScheduler} using the
     * same cap, so a subsequent {@link #schedule(long)} computes from identical starting
     * state every time (byte-for-byte reproducible across runs). Prior to the first
     * {@code reset} a call to {@link #schedule(long)} throws.
     * / 原子地开启 {@code tick} 的全新归零记账窗口。每域的记账内核都被替换为使用同一上限的全新 p.2.8
     * {@link BudgetScheduler}，使后续 {@link #schedule(long)} 每次都从逐位一致的初始状态计算（跨多次运行
     * 字节级可复现）。在首次 {@code reset} 之前调用 {@link #schedule(long)} 会抛异常。
     *
     * @param tick the tick to open.
     * @return this scheduler, for chaining.
     */
    public TimeBudgetScheduler reset(long tick) {
        for (Slot slot : slots.values()) {
            slot.scheduler = BudgetScheduler.create(TickBudget.of(slot.cap, slot.cap, slot.cap), 0);
        }
        this.currentTick = tick;
        return this;
    }

    /**
     * Deterministically returns, in fixed key order, exactly the {@link TimeBudgetKey}s
     * that run within the currently open tick's budgets. Domains are walked in fixed
     * {@link TimeDomain} order and, within a domain, registered simulants in ascending
     * order; each key charges 1 unit and is included while its domain pool stays within
     * cap, else it is skipped (p.2.8 {@code DEFERRED}-aligned). Requires that
     * {@link #reset(long)} was called with the same {@code tick} (else
     * {@link IllegalArgumentException}).
     * / 按固定键序确定性地返回当前已开启 tick 预算内恰好要执行的 {@link TimeBudgetKey} 集合。域按固定
     * {@link TimeDomain} 序遍历，域内按升序遍历已注册 simulant；每键计费 1 单位，当其域池仍在限内则纳入，
     * 否则跳过（对齐 p.2.8 {@code DEFERRED}）。要求 {@link #reset(long)} 已以同一 {@code tick} 调用
     * （否则抛 {@link IllegalArgumentException}）。
     *
     * @param tick the tick to schedule (must equal the tick passed to {@link #reset(long)}).
     * @return the keys that run this tick, in fixed deterministic order.
     * @throws IllegalArgumentException if {@code tick != } the currently open tick, or no tick is open.
     */
    public List<TimeBudgetKey> schedule(long tick) {
        if (currentTick != tick) {
            throw new IllegalArgumentException("no reset open for tick " + tick
                    + " (current tick is " + currentTick + ")");
        }
        List<TimeBudgetKey> out = new ArrayList<>();
        for (java.util.Map.Entry<TimeDomain, Slot> e : slots.entrySet()) {
            TimeDomain d = e.getKey();
            Slot slot = e.getValue();
            for (Long id : slot.registered) {
                if (slot.scheduler.charge(id, slot.region, 1L) == BudgetScheduler.ChargeResult.ACCEPTED) {
                    out.add(new TimeBudgetKey(d, id.longValue()));
                }
            }
        }
        return out;
    }
}