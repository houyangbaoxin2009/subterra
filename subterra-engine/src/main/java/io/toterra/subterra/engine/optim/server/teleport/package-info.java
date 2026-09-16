/**
 * p.2.31.1 StellarRTP clean-room 核心（随机传送目标规划）。纯 JDK、确定性：
 * 同输入同结果，非法值确定性拒绝；运行期高度查询经 {@link
 * io.toterra.subterra.engine.optim.server.teleport.RtpPlanner.SurfaceFn}
 * 回调注入，engine 不引用 MC。上游 GPLv3 仅思想参考。
 *
 * <p>The p.2.31.1 StellarRTP clean-room core (random-teleport planning). Pure
 * JDK, deterministic: identical inputs give identical results, illegal values
 * are rejected deterministically; the runtime height query is injected via the
 * {@link io.toterra.subterra.engine.optim.server.teleport.RtpPlanner.SurfaceFn}
 * callback, so the engine never references MC. The GPLv3 upstream is
 * reference-of-ideas only.
 */
package io.toterra.subterra.engine.optim.server.teleport;
