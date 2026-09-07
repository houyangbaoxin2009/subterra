/**
 * worldgen pipeline router / composition core (p.1.8.12): the vanilla-compatible
 * noise-router composition layer. It mirrors MC 1.21.1's
 * {@code net.minecraft.world.level.levelgen.NoiseRouter} 15-field assembly table,
 * the per-noise seed chain ({@link PositionalRand} reproducing
 * {@code RandomSupport.seedFromHashOf} / {@code forkPositional} / {@code
 * fromHashOf}), and the octave-blend computers {@link BlendedNoise} /
 * {@link InterpolatedNoise} as legacy seams. Router fields implement the
 * {@link io.toterra.subterra.optim.worldgen.pipeline.density.Density} seam so the
 * chunk-grid sibling and later pipeline stages can sample them deterministically.
 *
 * <p>worldgen 管线的 router / 组合核心（p.1.8.12）：与原生兼容的噪声路由器组合
 * 层。它镜像 MC 1.21.1 的 {@code NoiseRouter} 15 字段装配表、每噪声种子链
 * （{@link PositionalRand} 复现 {@code RandomSupport.seedFromHashOf} /
 * {@code forkPositional} / {@code fromHashOf}），并把 {@link BlendedNoise} /
 * {@link InterpolatedNoise} 作为遗留接缝实现。路由器字段实现
 * {@link io.toterra.subterra.optim.worldgen.pipeline.density.Density} 接缝，
 * 使块格平级模块与后续管线阶段可确定性采样。
 */
package io.toterra.subterra.optim.worldgen.pipeline.router;