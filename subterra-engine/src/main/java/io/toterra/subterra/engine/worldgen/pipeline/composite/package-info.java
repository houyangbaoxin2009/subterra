/**
 * worldgen pipeline composite density-fields core (p.1.8.14): the vanilla-compatible
 * composition of the overworld {@code depth} / {@code initialDensityWithoutJaggedness}
 * / {@code finalDensity} router fields from their dense building blocks —
 * {@link SplineFn} (the {@code CubicSpline$Multipoint} 1-D cubic-Hermite basis),
 * {@link SlideFn} (the {@code NoiseRouterData$slide} / {@code final_density} clamped
 * top / bottom slide), {@link JaggednessFn} (the {@code sloped_cheese} jaggedness
 * term {@code jaggedness * half_negative(jagged)}), {@link ShiftedNoiseFn} (the
 * {@code ShiftedNoise} coordinate-domain-shifted field), {@link NoodleFn} (the
 * {@code overworld/caves/noodle.json} ridge-based noodle-cave arm) and
 * {@link CaveFamilyFn} (the {@code when_out_of_range} cheese/spaghetti/pillars + entrances
 * cave carve). {@link DensityComposite}
 * wires them into the exact 1.21.1 overworld recipe
 * ({@code depth + jaggedness*halfNeg(jagged)} {@code -> quarter_negative -> *factor
 * -> clamp -> slide}), constructed over a {@code router.NoiseRouter}'s climate
 * fields ({@code continents}/{@code erosion}/{@code ridges}).
 *
 * <p>worldgen 管线的组合密度场核心（p.1.8.14）：与原生兼容地、把主世界
 * {@code depth} / {@code initialDensityWithoutJaggedness} / {@code finalDensity}
 * 路由器字段从其密集构件组合而来——{@link SplineFn}（{@code CubicSpline$Multipoint}
 * 一维三次 Hermite 基）、{@link SlideFn}（{@code NoiseRouterData$slide} /
 * {@code final_density} 顶部/底部夹取滑移）、{@link JaggednessFn}
 * （{@code sloped_cheese} 的锯齿项 {@code jaggedness * half_negative(jagged)}）、
 * {@link ShiftedNoiseFn}（{@code ShiftedNoise} 坐标域平移场）、{@link NoodleFn}
 * （{@code overworld/caves/noodle.json} 脊线面条洞穴支）与 {@link CaveFamilyFn}
 * （{@code when_out_of_range} 的 cheese/spaghetti/pillars + entrances 洞穴雕刻）。
 * {@link DensityComposite}
 * 按 1.21.1 主世界配方把三者装配起来
 * （{@code depth + jaggedness*halfNeg(jagged)} {@code -> quarter_negative -> *factor
 * -> clamp -> slide}），建于 {@code router.NoiseRouter} 的气候字段之上
 * （{@code continents}/{@code erosion}/{@code ridges}）。
 */
package io.toterra.subterra.engine.worldgen.pipeline.composite;