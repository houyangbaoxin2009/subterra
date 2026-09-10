/**
 * 调度基础数据结构：任务调度用的确定性容器与池化助手。
 * Scheduler data structures: deterministic containers and pooling helpers used
 * by task scheduling.
 * <p>
 * 含动态优先队列 {@link io.toterra.subterra.engine.optim.sched.structs.DynamicPriorityQueue}、
 * 单任务优先执行 {@link io.toterra.subterra.engine.optim.sched.structs.OneTaskAtATimeExecutor}、
 * 简单对象池 {@link io.toterra.subterra.engine.optim.sched.structs.SimpleObjectPool} 与配对
 * {@link io.toterra.subterra.engine.optim.sched.structs.Pair}。纯 JDK，禁 O(n²)，不碰 MC。
 * <p>
 * Holds the dynamic priority queue {@link io.toterra.subterra.engine.optim.sched.structs.DynamicPriorityQueue},
 * one-task-at-a-time executor {@link io.toterra.subterra.engine.optim.sched.structs.OneTaskAtATimeExecutor},
 * simple object pool {@link io.toterra.subterra.engine.optim.sched.structs.SimpleObjectPool} and pair
 * {@link io.toterra.subterra.engine.optim.sched.structs.Pair}. Pure JDK, no O(n²), no MC coupling.
 */
package io.toterra.subterra.engine.optim.sched.structs;