/**
 * worldgen pipeline perlin noise layer (p.1.8.7): the clean-room, bit-exact
 * vanilla-compatible {@code OctaveNoise<ImprovedNoise>} value-noise lattice
 * family — {@link PerlinNoise} (octave octave-variable family) over
 * {@link ImprovedNoise} (the 3-D gradient-lattice cell). Deterministic for a
 * fixed seed; the lattice permutation and per-cell interpolation are mirrored
 * exactly from Minecraft 1.21.1's
 * {@code net.minecraft.world.level.levelgen.synth} classes.
 *
 * worldgen 管线的 perlin 噪声层（p.1.8.7）：以净室方式、与原生逐位一致的
 * {@code OctaveNoise<ImprovedNoise>} 值噪声晶格族——基于 {@link ImprovedNoise}
 * （三维梯度晶格单元）的 {@link PerlinNoise}（八度族）。固定种子下确定；
 * 晶格置换表与逐单元插值均照 Minecraft 1.21.1 的
 * {@code net.minecraft.world.level.levelgen.synth} 类逐位复现。
 */
package io.toterra.subterra.engine.worldgen.pipeline.noise.perlin;