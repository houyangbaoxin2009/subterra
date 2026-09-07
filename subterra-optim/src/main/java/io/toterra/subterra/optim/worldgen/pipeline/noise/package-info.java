/**
 * worldgen pipeline noise layer (p.1.8.6): deterministic, bit-exact vanilla
 * random-sources and noise-salt mixing ({@link XoroRandom},
 * {@link LegacyRandom}, {@link NoiseSalt}), the shared RNG contract the
 * sibling noise modules compile against.
 *
 * worldgen 管线的噪声层（p.1.8.6）：确定性与原生逐位一致的随机源与噪声盐
 * 混合（{@link XoroRandom}、{@link LegacyRandom}、{@link NoiseSalt}），
 * 为相邻噪声模块提供共用的 RNG 契约。
 */
package io.toterra.subterra.optim.worldgen.pipeline.noise;