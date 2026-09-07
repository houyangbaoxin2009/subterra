/**
 * worldgen pipeline multi-noise biome-source core (p.1.8.16): a clean-room mirror
 * of MC 1.21.1's {@code MultiNoiseBiomeSource} climate-parameter selection. It
 * re-implements the 6-Dimension climate <em>target</em> model ({@link ClimateParam}
 * + {@link ClimateParam.ClimateBand}), the quantized nearest-parameter search that
 * picks a biome from a temperature/humidity/continentalness/erosion/depth/ridges
 * sample ({@link MultiNoiseBiomeSourceCore#pick}), and the climate <em>sampler</em>
 * that reads those six values off the p.1.8.12 {@code router.NoiseRouter} fields at
 * the block coordinates implied by a climate cell ({@link ClimateNoise}). All
 * behaviour is verified via {@code javap} against the 1.21.1 joined classes: the
 * quantization factor (10000), the linear distance-to-range plus squared aggregation
 * (the {@code Climate.Parameter.distance} / {@code ParameterPoint.fitness}
 * reduction), the nearest selection with lowest-index tie-breaking, and the
 * {@code QuartPos.toBlock} (=x{@code <<}2) sample scale.
 *
 * <p>worldgen 管线的多噪声生物群系源核心（p.1.8.16）：以 clean-room 方式镜像 MC 1.21.1
 * {@code MultiNoiseBiomeSource} 的气候参数选择。它重实现六维气候<em>目标</em>模型
 * （{@link ClimateParam} + {@link ClimateParam.ClimateBand}）、根据
 * 温度/湿度/大陆性/侵蚀/深度/山脊噪声样本选取生物群系的量化最近参数搜索
 * （{@link MultiNoiseBiomeSourceCore#pick}），以及从 p.1.8.12 {@code router.NoiseRouter}
 * 字段在气候单元所对应的方块坐标读取六个值的气候<em>采样器</em>（{@link ClimateNoise}）。
 * 所有行为均经 {@code javap} 对照 1.21.1 joined 类验证：量化因子（10000）、
 * “到区间的线性距离再平方聚合”（{@code Climate.Parameter.distance} /
 * {@code ParameterPoint.fitness} 归约）、取最小 fitness 并按最低下标平局取胜的最近
 * 选择、以及 {@code QuartPos.toBlock}（=x{@code <<}2）采样尺度。
 */
package io.toterra.subterra.optim.worldgen.pipeline.biomesrc;