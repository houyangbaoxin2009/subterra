/**
 * worldgen pipeline td dimension-wiring core (p.1.8.19): the td-driven table
 * that ties the three primary worlds — the overworld, the nether and the end —
 * together exactly like vanilla 1.21.1's per-dimension
 * {@code dimension_type}/{@code noise_settings} data. {@link WorldDim} fixes
 * the three ids and their canonical coordinate scales (1.0 / 8.0 / 1.0) plus
 * the vanilla dimension-type flags; {@link DimensionSection} wraps one world's
 * vertical window (reusing
 * {@link io.toterra.subterra.optim.worldgen.pipeline.dimension.OverworldBounds})
 * together with those flags as user-overridable defaults; {@link
 * DimensionWorlds} holds the three {@code DimensionSection}s plus a per-world
 * {@link io.toterra.subterra.optim.worldgen.pipeline.dimension.DimensionPlan},
 * exposes a {@code td()} / {@code fromTd(String)} round-trip, a
 * {@code materializeRouter(seed, WorldDim)} slot (overworld p.1.8.12, nether/end
 * p.1.8.20) and a {@code validate()} health-check. Everything
 * here is pure, deterministic and immutable; the default table IS the vanilla
 * triple so the default pipeline stays bit-identical.
 * <p>
 * worldgen 管线 td 维度接线核心（p.1.8.19）：以 td 驱动的表，把主世界、下界与末地三个
 * 主世界精确地像原生 1.21.1 的逐维 {@code dimension_type}/{@code noise_settings} 那样
 * 联系起来。{@link WorldDim} 固定三个 id 与其规范坐标尺度（1.0 / 8.0 / 1.0）及原版维度型
 * 标志；{@link DimensionSection} 包装一个世界的纵向窗口（复用
 * {@link io.toterra.subterra.optim.worldgen.pipeline.dimension.OverworldBounds}）连同这些
 * 标志作为用户可覆盖默认；{@link DimensionWorlds} 持有三个 {@code DimensionSection} 及逐
 * 世界 {@link io.toterra.subterra.optim.worldgen.pipeline.dimension.DimensionPlan}，提供
 * {@code td()} / {@code fromTd(String)} 往返、{@code materializeRouter(seed, WorldDim)}
 * 槽（主世界 p.1.8.12，下界/末地 p.1.8.20）与 {@code validate()} 健康检查。本包纯粹、确定、
 * 不可变；默认表即原版三元组，故默认管线保持逐位一致。
 */
package io.toterra.subterra.optim.worldgen.pipeline.dimworlds;