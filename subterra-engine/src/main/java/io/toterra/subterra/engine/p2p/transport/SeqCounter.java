package io.toterra.subterra.engine.p2p.transport;

/**
 * p.2.5.4 monotonic session sequence counter. The initial value comes from the p.2.5.3
 * handshake's {@code initialSeq}; every {@link #next()} vends the current value and advances
 * it. This is a plain, deterministic counter with no atomics — the transport core is
 * single-threaded by contract (network IO is the runtime shell's job, not this core's).
 * <p>
 * 中文：p.2.5.4 单调会话序号计数器，初值来自 p.2.5.3 握手 {@code initialSeq}；每次
 * {@link #next()} 发放当前值并自增。无原子操作——传输核心按单线程确定性契约定制（网络 IO
 * 交由 runtime 壳负责，不属本核心）。
 */
public final class SeqCounter {

    private long next;

    /** @param initialSeq the channel's starting sequence (non-negative) */
    public SeqCounter(long initialSeq) {
        this.next = initialSeq;
    }

    /** Vends {@code current()} and advances. */
    public long next() {
        return next++;
    }

    /** The next sequence value that will be vended. */
    public long current() {
        return next;
    }
}