/**
 * Same-seed authoritative compare bridge (p.1.8.17): a dev-only tool that, inside a
 * real MC 1.21.1 NeoForge server, samples the vanilla {@code NoiseRouter} density
 * fields at fixed coordinates for a given world seed and compares them against
 * Subterra's pure-JDK mirror {@code router.NoiseRouter.overworld(seed)} so the two
 * implementations can be measured for exactness (bit-equal or max-abs-diff per field).
 * <p>
 * The pure-JDK mirror side plus the deterministic self-check run headless (plain
 * {@code JavaExec}, no game launch); the vanilla sampling path requires a booted
 * server (falls back to an in-process {@code ServerLifecycleHooks.getCurrentServer()},
 * else prints {@code BLOCKED} and exits 2 without crashing the game).
 * <p>
 * 同种子权威对拍桥（p.1.8.17）：仅限开发的工具，在真实 MC 1.21.1 NeoForge 服务器内对给定
 * 世界种子在固定坐标处采样原生 {@code NoiseRouter} 密度场，并与 Subterra 纯 JDK 镜像
 * {@code router.NoiseRouter.overworld(seed)} 对比，以度量二者精确度（逐场逐位相等或
 * 最大绝对差）。纯 JDK 镜像侧与确定性自检可在无游戏时头部运行（普通 {@code JavaExec}）；
 * 原生采样路径需要已启动的服务器（本进程内通过
 * {@code ServerLifecycleHooks.getCurrentServer()} 取得，否则打印 {@code BLOCKED}
 * 并以 2 退出，绝不使游戏崩溃）。
 */
package io.toterra.subterra.runtime.worldgen.compare;