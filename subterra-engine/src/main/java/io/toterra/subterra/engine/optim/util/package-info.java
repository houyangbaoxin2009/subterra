/**
 * 优化通用工具：热路径共用的纯 JDK 助手（对象池等）。
 * Optimization utilities: pure JDK helpers shared by hot paths (object pools etc).
 * <p>
 * 含有界对象池 {@link io.toterra.subterra.engine.optim.util.ScratchPool}。纯 JVM，
 * 确定性、可探针，不碰 MC。
 * <p>
 * Holds the bounded object pool {@link io.toterra.subterra.engine.optim.util.ScratchPool}.
 * Pure JVM, deterministic, probe-gated, no MC coupling.
 */
package io.toterra.subterra.engine.optim.util;