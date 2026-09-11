/**
 * LOD multi-level-of-detail render data model (p.2.28.1, clean-room self-developed):
 * the deterministic, pure-JDK core that renders far-from-viewer terrain with gradually
 * reduced detail. The detail is modelled as a fixed ladder of levels
 * ({@link io.toterra.subterra.engine.render.lod.LodLevel L0..L5}, each level {@code k}
 * spanning {@code 2^k} chunks per side), and as two complementary geometric structures:
 * per-column vertical {@link
 * io.toterra.subterra.engine.render.lod.LodColumnStack} sections ({@link
 * io.toterra.subterra.engine.render.lod.LodColumnStack.LodProfileEntry profile segments}
 * quantized to int16 block ranges) and horizontal merged {@link
 * io.toterra.subterra.engine.render.lod.LodQuad} cells. {@link
 * io.toterra.subterra.engine.render.lod.LodSection} is the fixed-order container whose
 * {@code toBytes()/fromBytes()} byte format is pinned as the payload ("bytes in &rarr;
 * bytes out") ABI contract for the tie hot path of p.2.28.2. Compact quantized vertex
 * layouts are exposed by {@link io.toterra.subterra.engine.render.lod.LodVertexLayout}
 * (reusing {@code engine.render.VertexLayout.builder()}), and the tiered configuration
 * document is td-ized by {@link io.toterra.subterra.engine.render.lod.LodConfigDoc}.
 *
 * <p>Clean-room stance: LOD is implemented in-house. The general ideas — an LOD mip
 * pyramid, column-stack cross-sections, horizontal-surface merging, frustum culling,
 * and progressively coarser voxel meshing — are public, common computer-graphics
 * knowledge. The Distant Horizons mod (LGPL-3.0) was assessed as reference-only: its
 * ideas are consulted at most, and <em>zero</em> of its source code is included. No MC,
 * no NeoForge, no OpenGL: everything here is pure JDK plus the repository's own
 * {@code engine.config.TdTable}/{@code TdValue} and {@code engine.render.VertexLayout}
 * types.
 *
 * <p>Deterministic paradigm (self-imposed): fixed field order everywhere, per-byte
 * consistency, no timing, no randomness, no timestamps — identical input always
 * produces identical bytes, and every {@code toBytes()/fromBytes()}/{@code canonicalText()}
 * round-trips to an identical, re-serialization-stable form.
 *
 * <p>LOD 多级细节渲染数据模型（p.2.28.1，clean-room 自研）：把原版视距外地形以逐级降低的细节渲染的
 * 确定性纯 JDK 核心。细节建模为固定层级阶梯（{@link
 * io.toterra.subterra.engine.render.lod.LodLevel L0..L5}，每级 {@code k} 每边覆盖 {@code 2^k}
 * 个区块），以及两种互补几何结构：单列垂直 {@link
 * io.toterra.subterra.engine.render.lod.LodColumnStack} 剖面（{@link
 * io.toterra.subterra.engine.render.lod.LodColumnStack.LodProfileEntry 剖面段}量化到 int16
 * 方块范围）与水平合并 {@link io.toterra.subterra.engine.render.lod.LodQuad} 单元。{@link
 * io.toterra.subterra.engine.render.lod.LodSection} 是固定序容器，其 {@code
 * toBytes()/fromBytes()} 字节格式被钉死为 p.2.28.2 tie 热点的载荷（「字节进→字节出」）ABI 契约。
 * 紧凑量化顶点布局由 {@link io.toterra.subterra.engine.render.lod.LodVertexLayout} 暴露
 * （复用 {@code engine.render.VertexLayout.builder()}），分档配置文档由 {@link
 * io.toterra.subterra.engine.render.lod.LodConfigDoc} td 化。
 *
 * <p>clean-room 立场：LOD 为自研实现。其中的通用思想——LOD mip 金字塔、列柱剖面、水平面合并、视锥剔除、
 * 逐级变粗的体素网格化——均属公开的计算机图形学常识。Distant Horizons 模组（LGPL-3.0）仅评估为思想上的
 * 参考：至多参考其公开思想，<em>零</em> 源码包含在内。无 MC、无 NeoForge、无 OpenGL：这里全部为纯 JDK
 * 外加本仓库自己的 {@code engine.config.TdTable}/{@code TdValue} 与
 * {@code engine.render.VertexLayout} 类型。
 *
 * <p>确定性范式（自约束）：处处固定字段序、逐字节一致、无时序、无随机、无时间戳——相同的输入恒产生相同
 * 的字节，且每个 {@code toBytes()/fromBytes()}/{@code canonicalText()} 都往返为一致、再序列化不变
 * 的形式。
 */
package io.toterra.subterra.engine.render.lod;