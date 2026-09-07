/**
 * worldgen pipeline dimension layer (Step 2): the nine-dimension terrain set
 * keyed by {@link io.toterra.subterra.api.worldgen.EcoDim}. Composes immutable
 * {@link io.toterra.subterra.optim.worldgen.pipeline.density.Density} fields
 * (p.1.8.3) into per-dimension {@link DimensionSlot}s, each carrying an
 * algorithm from {@link DimAlgo}. Defaults are vanilla-compatible mirror
 * fields (terrain = octave Perlin, climate = normal noise, hydro = constant
 * sea level 63, litho/mineral = mirror primitives); vegetation/fauna/relic are
 * reserved slots; the surface slot exposes the p.1.8.5 SurfaceEvaluator seam
 * via {@link DimensionTerrain#surfaceRules()}. Everything here is pure,
 * deterministic and immutable; the formula path (p.1.8.10) enters via
 * {@link DimensionPlan} (FORMULA entries build a p.1.8.10 FormulaTerrain
 * field) or {@link DimensionTerrain#with}. The default mirrors themselves do
 * not depend on the terrain/formula packages.
 * <p>
 * worldgen 管线维度层（第 2 步）：以
 * {@link io.toterra.subterra.api.worldgen.EcoDim} 为键的九维地形集合。将不可变的
 * {@link io.toterra.subterra.optim.worldgen.pipeline.density.Density} 场（p.1.8.3）
 * 组合为每个维度的 {@link DimensionSlot}，每槽携带 {@link DimAlgo} 算法。默认为原版可
 * 兼容镜像场（terrain = 八度 Perlin、climate = 普通噪声、hydro = 常量海平面 63、
 * litho/mineral = 镜像原语）；vegetation/fauna/relic 为预留槽位；surface 槽经
 * {@link DimensionTerrain#surfaceRules()} 暴露 p.1.8.5 SurfaceEvaluator 接缝。本包
 * 纯粹、确定且不可变；公式路径（p.1.8.10）经 {@link DimensionPlan}（FORMULA 条目构建
 * p.1.8.10 FormulaTerrain 场）或 {@link DimensionTerrain#with} 进入。默认镜像本身不依
 * 赖 terrain 或 formula 包。
 */
package io.toterra.subterra.optim.worldgen.pipeline.dimension;