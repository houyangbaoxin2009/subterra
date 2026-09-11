/**
 * p.2.28.6 LOD 契约面：固定序质量档 {@link io.toterra.subterra.api.lod.LodQualitySpec STANDARD/HIGH/LOW} +
 * 距离档值对象 {@link io.toterra.subterra.api.lod.LodDistanceSpec} + 确定性门面
 * {@link io.toterra.subterra.api.lod.LodApi}（纯 JDK，api 不依赖引擎）。采用「api 定义形状 / engine 镜像
 * 实现」范式：所有契约常量（档位序、{@code maxLevel}=4、缺省距离表 {@code L1@64..L4@512}、每区块 16 方块）
 * 均逐字对照 {@code engine.render.lod.LodConfigDoc}/{@code LodLevel}/{@code LodDistanceSelector} 实际常量
 * 落于此，engine 侧新增 {@code engine.render.lod.LodApiMirror} 提供同输入同输出镜像实现。
 * <p>
 * p.2.28.6 LOD contract surface: fixed-order quality tiers
 * {@link io.toterra.subterra.api.lod.LodQualitySpec STANDARD/HIGH/LOW} + the distance-tier value object
 * {@link io.toterra.subterra.api.lod.LodDistanceSpec} + the deterministic facade
 * {@link io.toterra.subterra.api.lod.LodApi} (pure JDK, no engine dependency). Built on the "api shapes /
 * engine mirrors" pattern: every contract constant (tier order, {@code maxLevel}=4, the default distance table
 * {@code L1@64..L4@512}, 16 blocks per chunk) is pinned here verbatim against the engine's actual constants in
 * {@code engine.render.lod.LodConfigDoc}/{@code LodLevel}/{@code LodDistanceSelector}, and the api.lod iron
 * law is zero engine dependency.
 */
package io.toterra.subterra.api.lod;