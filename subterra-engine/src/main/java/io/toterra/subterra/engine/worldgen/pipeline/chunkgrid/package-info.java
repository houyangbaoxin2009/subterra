package io.toterra.subterra.engine.worldgen.pipeline.chunkgrid;

/**
 * worldgen pipeline chunk-grid layer (p.1.8.13): an allocation-free clean-room
 * mirror of MC 1.21.1's {@code NoiseChunk} cell-sampling / trilinear
 * interpolation core and its density→height "find surface" mapping (see
 * {@link GridSettings}, {@link DensityGrid}, {@link Trilinear},
 * {@link HeightMapper}).
 * <p>
 * worldgen 管线区块网格层（p.1.8.13）：对 MC 1.21.1 {@code NoiseChunk} 单元采样 / 三线性
 * 插值核心与密度→高度"找地表"映射的无分配洁室镜像（参见 {@link GridSettings}、
 * {@link DensityGrid}、{@link Trilinear}、{@link HeightMapper}）。
 */