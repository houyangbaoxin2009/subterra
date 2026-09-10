/**
 * 任务执行器：单一/并发工作线程与任务抽象的调度执行层。
 * Task executor: the scheduling-execution layer of workers and task abstractions.
 * <p>
 * 含执行管理 {@link io.toterra.subterra.engine.optim.sched.executor.ExecutorManager}、
 * 任务接口 {@link io.toterra.subterra.engine.optim.sched.executor.Task}、
 * 简单实现 {@link io.toterra.subterra.engine.optim.sched.executor.SimpleTask}、
 * 工作线程 {@link io.toterra.subterra.engine.optim.sched.executor.WorkerThread} 与锁令牌
 * {@link io.toterra.subterra.engine.optim.sched.executor.LockToken}。纯 JDK，不碰 MC。
 * <p>
 * Holds the manager {@link io.toterra.subterra.engine.optim.sched.executor.ExecutorManager},
 * task interface {@link io.toterra.subterra.engine.optim.sched.executor.Task}, simple
 * implementation {@link io.toterra.subterra.engine.optim.sched.executor.SimpleTask}, worker
 * {@link io.toterra.subterra.engine.optim.sched.executor.WorkerThread} and lock token
 * {@link io.toterra.subterra.engine.optim.sched.executor.LockToken}. Pure JDK, no MC coupling.
 */
package io.toterra.subterra.engine.optim.sched.executor;