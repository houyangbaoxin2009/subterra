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
 *
 * <h3>p.2.28.2：生成管线（JDK 确定性内核 + tie 标量原语下沉）</h3>
 *
 * <p><b>Generation pipeline.</b> The level-0 generator is {@link
 * io.toterra.subterra.engine.render.lod.LodPolygonizer} (input {@link
 * io.toterra.subterra.engine.render.lod.LodPolygonizer.LodRegionSample}, top-most surface rule +
 * pinned scan-order greedy quad merge); the level {@code k→k+1} mip merge is {@link
 * io.toterra.subterra.engine.render.lod.LodMerger} (2×2 pinned corner decision on four children,
 * recursing the same merge function, reusing {@code LodSection}'s pinned bytes throughout); the
 * incremental dirty set and its deterministic fixed-order iteration are {@link
 * io.toterra.subterra.engine.render.lod.LodDirtyTracker}; the facade with budgeted bounded-batch
 * rebuild {@link io.toterra.subterra.engine.render.lod.LodPipeline#rebuildBatch} (p.2.7 bounded
 * batch / p.2.8 budget semantics, pure partition of the ordered dirty set, no timing) is {@link
 * io.toterra.subterra.engine.render.lod.LodPipeline}. The pure-integer hot spots (top-profile
 * sampler and 2×2 corner decision) are sunk as tie scalar primitives
 * ({@code lod$profile_top}/{@code lod$merge_corner}) and mounted by {@link
 * io.toterra.subterra.engine.render.lod.LodTieAccelerator} over the p.2.1 engine.tie FFM bridge.
 *
 * <p><b>Measured tie DLL boundary (conclusion, do not re-guess):</b> tiec {@code --shared}
 * exports scalars (i64/f64/bool/trit/char) and string only; private funcs are not exported;
 * custom pointer / byte-buffer {@code FunctionDescriptor} pass-through is not supported by the
 * tiec export surface today — so the LOD hot spots sink as scalar i64 primitives, and the zd
 * byte-through {@code LodSection} ABI is explicitly deferred to a future tie toolchain extension
 * rather than gambled on. JDK golden kernels ({@code LodPolygonizer.profileTop},
 * {@code LodMerger.mergeCorner}) and the tie primitives are byte-identical; without the DLL the
 * accelerator is a deterministic skip and the pipeline falls back to JDK only.
 *
 * <p><b>生成管线。</b>level-0 生成器为 {@link io.toterra.subterra.engine.render.lod.LodPolygonizer}
 * （输入 {@link io.toterra.subterra.engine.render.lod.LodPolygonizer.LodRegionSample}，顶表规则 + 钉死扫描
 * 序贪心 quad 合并）；level {@code k→k+1} mip 合并为 {@link io.toterra.subterra.engine.render.lod.LodMerger}
 * （对四子段的 2×2 钉死 corner 裁决，同一合并函数递归复用，全程复用 {@code LodSection} 的钉死字节）；增量脏集合
 * 及其确定性固定序迭代为 {@link io.toterra.subterra.engine.render.lod.LodDirtyTracker}；带预算有界批次重组的
 * 门面 {@link io.toterra.subterra.engine.render.lod.LodPipeline#rebuildBatch}（承接 p.2.7 有界批次 /
 * p.2.8 预算语义，对有序脏集合的纯划分、无时序）为 {@link io.toterra.subterra.engine.render.lod.LodPipeline}。
 * 纯整数热点（顶采样与 2×2 corner 裁决）被下沉为 tie 标量原语（{@code lod$profile_top}/{@code lod$merge_corner}），
 * 由 {@link io.toterra.subterra.engine.render.lod.LodTieAccelerator} 经 p.2.1 engine.tie FFM 桥挂载。
 *
 * <p><b>实测 tie DLL 边界（结论，勿再猜）：</b>tiec {@code --shared} 仅导出标量（i64/f64/bool/trit/char）
 * 与 string；私有函数不导出；tiec 导出面今天不支持自定义指针/字节缓冲 {@code FunctionDescriptor} 直传——
 * 故 LOD 热点以标量 i64 原语下沉，zd 字节直通 {@code LodSection} ABI 明确推迟到未来 tie 工具链扩展而非作赌注。
 * JDK 金样内核（{@code LodPolygonizer.profileTop}、{@code LodMerger.mergeCorner}）与 tie 原语逐字节一致；无
 * DLL 时加速器为确定性 skip，管线仅走 JDK。
 *
 * <h3>p.2.28.3：确定性视景管理（距离选择 / 剔除 / 预算调度）</h3>
 *
 * <p><b>Deterministic view management.</b> {@link
 * io.toterra.subterra.engine.render.lod.LodDistanceSelector} maps each chunk coordinate to an
 * expected {@code LodLevel} from the {@code LodConfigDoc} distance ladder using pure integer
 * squared-block-distance arithmetic (farther → coarser, same distance → same level, with the
 * pinned fixed default tier table {@code L1@64..L4@512} as the p.2.28.6 golden target). {@link
 * io.toterra.subterra.engine.render.lod.LodViewCuller} culls with an all-integer Q8.24
 * {@link io.toterra.subterra.engine.render.lod.LodViewCuller.ViewFrustum} (six outward planes
 * against a block-space AABB via the p-vertex test) plus facing-bit backface culling. {@link
 * io.toterra.subterra.engine.render.lod.LodBudgetScheduler} is a thin adapter over the p.2.8
 * {@code engine.sim.budget} kernel (per-level = kernel per-simulant, per-region = RegionKey,
 * per-tick global) driving deterministic fixed key-order over-budget skip with {@code downgradeTo}
 * hints. {@link io.toterra.subterra.engine.render.lod.ViewPlan} (and its reusable
 * {@link io.toterra.subterra.engine.render.lod.ViewPlan.ViewPlanner}) is the immutable
 * deterministic snapshot — player → per-cell level + cull flag + budget batch membership — that
 * p.2.28.4/.6 consume. Everything is pure JDK, integer/fixed-point, fixed order, no randomness, no
 * timing, no hash-order: same input gives the same plan.
 *
 * <p><b>确定性视景管理。</b>{@link
 * io.toterra.subterra.engine.render.lod.LodDistanceSelector} 用纯整数方块距离平方算术把每个区块坐标映射为
 * 期望 {@code LodLevel}（取自 {@code LodConfigDoc} 距离阶梯；越远越粗、同距同级，钉死的固定缺省阈值表
 * {@code L1@64..L4@512} 为 p.2.28.6 的黄金目标）。{@link
 * io.toterra.subterra.engine.render.lod.LodViewCuller} 用全整数 Q8.24
 * {@link io.toterra.subterra.engine.render.lod.LodViewCuller.ViewFrustum}（六个朝外平面对方块空间 AABB
 * 做 p-vertex 测试）外加按朝向位的背面剔除。{@link
 * io.toterra.subterra.engine.render.lod.LodBudgetScheduler} 是 p.2.8 {@code engine.sim.budget} 内核的
 * 薄适配（每级=内核 per-simulant、每区域=RegionKey、每 tick 全局），驱动确定性固定键序超预算跳过并给出
 * {@code downgradeTo} 提示。{@link io.toterra.subterra.engine.render.lod.ViewPlan}（及其可复用
 * {@link io.toterra.subterra.engine.render.lod.ViewPlan.ViewPlanner}）是不可变确定性快照——玩家→每单元层级+
 * 剔除标志+预算批次成员，供 p.2.28.4/.6 消费。全程纯 JDK、整数/定点、固定序、无随机、无时序、无哈希序：
 * 同输入得同计划。
 */
package io.toterra.subterra.engine.render.lod;