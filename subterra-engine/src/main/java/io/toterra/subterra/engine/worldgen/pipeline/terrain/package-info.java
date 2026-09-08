/**
 * worldgen pipeline math-formula terrain integration (p.1.8.10): lets terrain
 * height follow a user math formula over x/y/z, feeding the {@link
 * io.toterra.subterra.engine.worldgen.pipeline.density.Density} seam. Providers
 * are deterministic, immutable and off by default — a provider is only
 * constructed when the user explicitly builds one via {@link
 * FormulaParams}/{@link FormulaTerrain}; nothing is wired into any default
 * generator path.
 * <p>
 * 数学公式地形集成（p.1.8.10）：让地形高度沿用户提供的 x/y/z 数学公式起伏，
 * 接入 {@link io.toterra.subterra.engine.worldgen.pipeline.density.Density}
 * 接口。提供器确定、不可变且默认关闭——仅当用户显式通过
 * {@link FormulaParams}/{@link FormulaTerrain} 构建时才会创建，不接入任何
 * 默认生成路径。
 */
package io.toterra.subterra.engine.worldgen.pipeline.terrain;