/**
 * 状态推进调度器：按条目状态机推进挂起项的确定性调度核心。
 * Status-advancing scheduler: the deterministic core that advances pending items
 * through a per-item state machine.
 * <p>
 * 含核心 {@link io.toterra.subterra.engine.optim.sched.scheduler.StatusAdvancingScheduler}
 * 及其条目/票证/状态/取消基本类型（{@code ItemHolder}/{@code ItemStatus}/
 * {@code ItemTicket}/{@code TicketSet}/{@code Cancellable}/{@code CancellationSignaller}/
 * {@code BusyRefCounter}/{@code KeyStatusPair}/{@code ExceptionHandlingAction}/
 * {@code ObjectFactory}）。纯 JDK，固定序、可回放，不碰 MC。
 * <p>
 * Holds the core {@link io.toterra.subterra.engine.optim.sched.scheduler.StatusAdvancingScheduler}
 * with its item/ticket/status/cancellation primitives ({@code ItemHolder}/{@code ItemStatus}/
 * {@code ItemTicket}/{@code TicketSet}/{@code Cancellable}/{@code CancellationSignaller}/
 * {@code BusyRefCounter}/{@code KeyStatusPair}/{@code ExceptionHandlingAction}/
 * {@code ObjectFactory}). Pure JDK, fixed order, replayable, no MC coupling.
 */
package io.toterra.subterra.engine.optim.sched.scheduler;