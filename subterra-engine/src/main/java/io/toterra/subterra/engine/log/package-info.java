/**
 * 日志核心（纯 JDK）：分级日志总线、模块登记与环式缓冲。
 * Logging core (pure JDK): leveled log bus, module registry and ring buffer.
 * <p>
 * 顶层提供前端 {@link io.toterra.subterra.engine.log.Logger}、汇聚
 * {@link io.toterra.subterra.engine.log.LogHub}、等级
 * {@link io.toterra.subterra.engine.log.LogLevel}、记录
 * {@link io.toterra.subterra.engine.log.LogRecord}、环式缓冲
 * {@link io.toterra.subterra.engine.log.LogRing}、输出口
 * {@link io.toterra.subterra.engine.log.LogSink}（含文件落地
 * {@link io.toterra.subterra.engine.log.FileLogSink}）、模块登记
 * {@link io.toterra.subterra.engine.log.ModuleReg}、崩溃转储
 * {@link io.toterra.subterra.engine.log.CrashDumper} 与线程快照
 * {@link io.toterra.subterra.engine.log.ThreadsSnapshot}。纯 JVM，不碰 MC。
 * <p>
 * Top level offers the front-end {@link io.toterra.subterra.engine.log.Logger},
 * the hub {@link io.toterra.subterra.engine.log.LogHub}, levels
 * {@link io.toterra.subterra.engine.log.LogLevel}, records
 * {@link io.toterra.subterra.engine.log.LogRecord}, ring
 * {@link io.toterra.subterra.engine.log.LogRing}, sinks
 * {@link io.toterra.subterra.engine.log.LogSink} (incl. file
 * {@link io.toterra.subterra.engine.log.FileLogSink}), module registry
 * {@link io.toterra.subterra.engine.log.ModuleReg}, {@link io.toterra.subterra.engine.log.CrashDumper}
 * and thread snapshots {@link io.toterra.subterra.engine.log.ThreadsSnapshot}.
 * Pure JVM, no MC coupling.
 */
package io.toterra.subterra.engine.log;