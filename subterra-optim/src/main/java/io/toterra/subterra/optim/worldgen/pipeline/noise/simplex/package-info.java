/**
 * worldgen pipeline simplex noise layer (p.1.8.8): the bit-exact vanilla
 * {@link SimplexNoise} and the amplitude-scaled {@link NormalNoise} that wraps
 * two {@link io.toterra.subterra.optim.worldgen.pipeline.noise.perlin.PerlinNoise}
 * layers. Together these are the workhorses of the NoiseRouter's climate
 * fields (temperature / humidity / continentalness / erosion / depth / ridges).
 * Note: 1.21.1 has no {@code DoublePerlinNoise} type; the two-layer normal-noise
 * module reproduces {@code net.minecraft.world.level.levelgen.synth.NormalNoise}.
 *
 * worldgen 管线的 simplex 噪声层（p.1.8.8）：与原生逐位一致的
 * {@link SimplexNoise}，以及组合两个
 * {@link io.toterra.subterra.optim.worldgen.pipeline.noise.perlin.PerlinNoise}
 * 层并按振幅缩放的 {@link NormalNoise}。它们是 NoiseRouter 气候场（温度/湿度/
 * 大陆性/侵蚀/深度/山脊）的基石。注意：1.21.1 并无 {@code DoublePerlinNoise}；
 * 本双层普通噪声模块复现的是 {@code net.minecraft.world.level.levelgen.synth.NormalNoise}。
 */
package io.toterra.subterra.optim.worldgen.pipeline.noise.simplex;