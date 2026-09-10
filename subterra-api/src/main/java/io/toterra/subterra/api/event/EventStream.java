package io.toterra.subterra.api.event;

import java.util.ArrayList;
import java.util.List;

/**
 * p.2.16.4 确定性事件流（事件契约面）：纯内存、固定 append 序、禁时序的单调事件日志。
 * <ul>
 *   <li>{@link #append}：{@code seq} 必须严格递增（{@code > lastSeq}，lastSeq 初值 -1，故首事件
 *       {@code seq>=0} 即可通过）；违例即 {@link IllegalArgumentException} 拒绝——同一门同时覆盖
 *       <b>乱序</b>（{@code seq ≤ lastSeq}）与<b>重放</b>（同 seq 复现亦 ≤ lastSeq），语义对齐
 *       p.2.11 的 monotonic-seq 门（p.2.11.2 以单一 REORDER 覆盖两者）；被拒绝的事件不推进
 *       {@code lastSeq}。</li>
 *   <li>{@link #events}：按 append 序返回不可变副本，可回放（同一流可反复读取，结果恒同）。</li>
 *   <li>{@link #size} / {@link #lastSeq}：确定性只读回读。</li>
 * </ul>
 * 确定性纪律：不依赖时间戳/随机/全局扫描，无外部 I/O；同一 append 输入序列必然产生同一事件序。
 * 调用方负责串行（同 p.2.11.2 的实例级无锁风格）。
 * <p>
 * p.2.16.4 deterministic event stream (the event contract surface): an in-memory, fixed-append-order,
 * time-free monotonic event log.
 * <ul>
 *   <li>{@link #append}: {@code seq} must be strictly increasing ({@code > lastSeq}, with
 *       {@code lastSeq} initially -1, so the first event with {@code seq>=0} passes); otherwise
 *       {@link IllegalArgumentException} rejects — one gate covering both <b>reorder</b> ({@code seq ≤
 *       lastSeq}) and <b>replay</b> (a revisited seq is also {@code ≤ lastSeq}), aligning with the
 *       p.2.11 monotonic-seq gate (p.2.11.2 pins both under one REORDER); a rejected event never
 *       advances {@code lastSeq}.</li>
 *   <li>{@link #events}: an immutable copy in append order, replayable (repeated reads yield identical
 *       results).</li>
 *   <li>{@link #size} / {@link #lastSeq}: deterministic read-backs.</li>
 * </ul>
 * Determinism discipline: no timestamps / randomness / global scans, no external I/O; the same append
 * input sequence always yields the same event order. Callers are responsible for serialization (the
 * p.2.11.2 instance-level lock-free style).
 */
public final class EventStream {

    /** 追加日志（append 序即契约序）。The append log (append order is the contract order). */
    private final List<EventEnvelope> log = new ArrayList<>();

    /** 已接受的最大事件序号；初值 -1 令首事件 {@code seq>=0} 可过。The highest accepted event seq; -1 so the first {@code seq>=0} passes. */
    private long lastSeq = -1L;

    /**
     * 追加一枚事件：{@code seq} 必须严格递增（{@code > lastSeq}），违例抛
     * {@link IllegalArgumentException}（乱序/重放拒绝）且不推进 {@code lastSeq}；{@code event}
     * 为 null 同样抛（调用方程序错误）。
     * Appends one event: {@code seq} must be strictly increasing ({@code > lastSeq}), otherwise
     * {@link IllegalArgumentException} is thrown (reorder/replay rejection) without advancing
     * {@code lastSeq}; a null {@code event} throws too (caller-program error).
     *
     * @param event 事件 / the event.
     * @return 同一事件（链式友好）/ the same event (chain-friendly).
     */
    public EventEnvelope append(EventEnvelope event) {
        if (event == null) {
            throw new IllegalArgumentException("event must be non-null");
        }
        final long s = event.seq();
        if (s <= lastSeq) {
            throw new IllegalArgumentException(
                    "seq must be strictly > lastSeq; got " + s + ", lastSeq " + lastSeq);
        }
        log.add(event);
        lastSeq = s;
        return event;
    }

    /**
     * 按 append 序返回不可变副本（可回放：反复读取结果恒同）。Returns an immutable copy in append
     * order (replayable: repeated reads yield identical results).
     *
     * @return 不可变事件列表 / the immutable event list.
     */
    public List<EventEnvelope> events() {
        return List.copyOf(log);
    }

    /** 已接受事件数。The number of accepted events. */
    public int size() {
        return log.size();
    }

    /** 已接受的最大事件序号（无事件时为 -1）。The highest accepted event seq (-1 when empty). */
    public long lastSeq() {
        return lastSeq;
    }
}
