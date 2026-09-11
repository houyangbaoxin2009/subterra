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
 * <h3>p.2.28.6（1/2）：api.lod 契约面 + engine 镜像实现</h3>
 *
 * <p><b>Contract surface &amp; mirror.</b> The LOD quality-tier ladder, distance-tier model and resolution
 * facade are pinned as pure-JDK contracts in {@code api.lod}
 * ({@link io.toterra.subterra.api.lod.LodQualitySpec LodQualitySpec} STANDARD/HIGH/LOW,{@link
 * io.toterra.subterra.api.lod.LodDistanceSpec LodDistanceSpec},{@link
 * io.toterra.subterra.api.lod.LodApi LodApi}) and mirrored here by {@link LodApiMirror}: every contract
 * constant (tier order, {@code maxLevel}=4, default distance table {@code L1@64..L4@512}, 16 blocks per
 * chunk) is sourced verbatim from {@link LodConfigDoc} / {@link LodLevel} / {@link LodDistanceSelector},
 * and both sides are same-input-same-output for the p.2.28.6 probe. The dependency iron-law holds: api does
 * not depend on engine; engine depends only on api.
 *
 * <p><b>契约面 + 镜像。</b>LOD 质量档位、距离档模型与解析门面被钉死为 {@code api.lod} 的纯 JDK 契约
 * （{@link io.toterra.subterra.api.lod.LodQualitySpec LodQualitySpec} STANDARD/HIGH/LOW、{@link
 * io.toterra.subterra.api.lod.LodDistanceSpec LodDistanceSpec}、{@link
 * io.toterra.subterra.api.lod.LodApi LodApi}），并在此由 {@link LodApiMirror} 镜像实现：每个契约常量
 * （档位序、{@code maxLevel}=4、缺省距离表 {@code L1@64..L4@512}、每区块 16 方块）都逐字源自
 * {@link LodConfigDoc} / {@link LodLevel} / {@link LodDistanceSelector}，两侧同输入同输出以供 p.2.28.6
 * 探针断言。依赖铁律成立：api 不依赖 engine；engine 仅依赖 api。
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
 *
 * <h3>p.2.28.4：渲染后端（复用 p.2.28.1 实例化基座 + 接缝规则）</h3>
 *
 * <p><b>Render backend.</b> {@link
 * io.toterra.subterra.engine.render.lod.LodRenderBackend} is the LOD mesh render backend for
 * p.2.28.4, registered into the p.2.27.1 {@link io.toterra.subterra.engine.render.instancing.BackendRegistry}
 * via {@link io.toterra.subterra.engine.render.lod.LodRenderBackend#registerInto()} (fixed
 * registration order, duplicate-name rejection and deterministic default selection all inherited
 * from the registry — none of the instancing classes are modified). It converts a
 * {@link io.toterra.subterra.engine.render.lod.LodSection}'s column-profile / horizontal-quad data
 * into a fixed-order instanced data model ({@link
 * io.toterra.subterra.engine.render.lod.LodRenderBackend.InstantiatedMesh}), consuming the p.2.28.1
 * {@link io.toterra.subterra.engine.render.lod.LodVertexLayout#COLUMN}/{@link
 * io.toterra.subterra.engine.render.lod.LodVertexLayout#QUAD} layout specs and the
 * {@code InstanceFormat} instance-layout paradigm — pure data, no real GL/GPU calls. {@link
 * io.toterra.subterra.engine.render.lod.LodShaderTemplate} is the clean-room fixed-order shader
 * (quantized-coordinate decode, color index&rarr;palette index, distance fog, seam {@code stitch}
 * bit) assembled through the p.2.27.1 {@code ShaderTemplate} parts/substitution mechanism,
 * byte-identical. {@link
 * io.toterra.subterra.engine.render.lod.LodSeamRules} pins the crack-free geometry rules — level-0
 * edge &rarr; full-detail block-grid alignment, adjacent-section shared-edge integer rounding +
 * {@code stitch} bit, and a monotone per-{@link io.toterra.subterra.engine.render.lod.LodDistanceSelector}
 * -tier distance-fog strength (0..255) — all integer/fixed-point, same input &rarr; same bytes. These
 * exact constants are the p.2.28.6 probe golden target. Everything remains pure JDK, fixed order,
 * zero MC/OpenGL imports.
 *
 * <p><b>渲染后端。</b>{@link
 * io.toterra.subterra.engine.render.lod.LodRenderBackend} 是 p.2.28.4 的 LOD 网格渲染后端，经 {@link
 * io.toterra.subterra.engine.render.lod.LodRenderBackend#registerInto()} 注册进 p.2.27.1
 * {@link io.toterra.subterra.engine.render.instancing.BackendRegistry}（固定注册序、同名拒绝与确定性缺省
 * 选择全部承自注册表——不改任何 instancing 类）。它把 {@link
 * io.toterra.subterra.engine.render.lod.LodSection} 的列柱剖面/水平 quad 数据转换为固定序实例化数据模型
 * （{@link io.toterra.subterra.engine.render.lod.LodRenderBackend.InstantiatedMesh}），消费 p.2.28.1
 * {@link io.toterra.subterra.engine.render.lod.LodVertexLayout#COLUMN}/{@link
 * io.toterra.subterra.engine.render.lod.LodVertexLayout#QUAD} 布局规格与 {@code InstanceFormat} 实例布局
 * 范式——纯数据，不做真实 GL/GPU 调用。{@link
 * io.toterra.subterra.engine.render.lod.LodShaderTemplate} 是 clean-room 固定序着色器（量化坐标解码、颜色
 * 索引&rarr;调色板索引、距离雾、接缝 {@code stitch} 位），经 p.2.27.1 {@code ShaderTemplate} 部件/占位符替换
 * 逐字节一致。{@link
 * io.toterra.subterra.engine.render.lod.LodSeamRules} 钉死无缝几何规则——level-0 边缘&rarr;全细节方块网格
 * 对齐、相邻 section 共享边缘整数取整 + {@code stitch} 位、按 {@link
 * io.toterra.subterra.engine.render.lod.LodDistanceSelector} 档位单调的距离雾强度（0..255）——全部整数/定点、
 * 同输入&rarr;同字节。这些精确常量即 p.2.28.6 探针的黄金目标。全程仍纯 JDK、固定序、零 MC/OpenGL import。
 *
 * <h3>p.2.28.5：engine.render.lod 持久化（LodCache）</h3>
 *
 * <p><b>On-disk persistence.</b> {@link
 * io.toterra.subterra.engine.render.lod.LodCache} is the deterministic, tamper-evident,
 * pure-JDK on-disk cache over the pinned {@link
 * io.toterra.subterra.engine.render.lod.LodSection#toBytes()} payload, keyed by the fixed
 * {@link io.toterra.subterra.engine.render.lod.LodCacheKey} (level + chunk coordinates &rarr;
 * one fixed file name, no timestamps/randomness). The file is a fixed layout &mdash; magic
 * {@code "LODC"} + format-version byte + verify-scheme byte + BE payload length + pinned
 * payload + strong (n=48) + fast (n=8) tsha1f digest (semantic alignment with the p.2.3
 * {@code engine.zd} generic zd v2 carrier &mdash; fixed header + versioned payload +
 * integrity; the bytes here are written directly, not as a TdTable tree). Every access
 * returns a fixed {@link io.toterra.subterra.engine.render.lod.CacheStatus} /
 * {@link io.toterra.subterra.engine.render.lod.CacheResult}: missing file &rarr;
 * {@code MISS}; bad magic / truncation / malformed section / fast-or-strong checksum
 * mismatch (single-byte tamper) &rarr; {@code CORRUPT}; parsed magic but unexpected pinned
 * format version &rarr; {@code VERSION_MISMATCH}; I/O failure &rarr; {@code IO_ERROR}. The
 * fast/strong digests honor {@code engine.network.integrity.Tsha1f} and the p.2.10
 * {@code ChunkVerifier} both-tiers semantics (not a copied implementation). {@code save} =
 * write + validate re-read; {@code load}/{@code hit} deterministically skip (never throw)
 * on a damaged entry, so a cache hit skips section regeneration for the p.2.28.6 runtime.
 * Every entry is marked {@linkplain io.toterra.subterra.engine.render.lod.LodCache#REGENERABLE
 * regenerable} (p.2.9 world-pack includable/excludable, p.2.18 exportable) — declared here,
 * export itself is a later sub-item.
 *
 * <p><b>磁盘持久化。</b>{@link io.toterra.subterra.engine.render.lod.LodCache} 是对钉死
 * {@link io.toterra.subterra.engine.render.lod.LodSection#toBytes()} 载荷的确定性、防篡改、纯 JDK
 * 磁盘缓存，以固定 {@link io.toterra.subterra.engine.render.lod.LodCacheKey} 为键（level + 区块坐标
 * &rarr; 一个固定文件名，无时间戳/随机）。文件为固定布局 &mdash; 魔数 {@code "LODC"} + 格式版本字节 +
 * 校验方案字节 + 大端载荷长 + 钉死载荷 + 强档（n=48）+ 快档（n=8）tsha1f 摘要（与 p.2.3
 * {@code engine.zd} 通用 zd v2 载体语义对齐 &mdash; 固定头 + 版本化载荷 + 完整性；此处字节直写而非
 * TdTable 树）。每次访问返回固定 {@link io.toterra.subterra.engine.render.lod.CacheStatus} /
 * {@link io.toterra.subterra.engine.render.lod.CacheResult}：缺文件 &rarr; {@code MISS}；坏魔数 /
 * 截断 / 畸形 section / 快或强档校验不符（单字节篡改）&rarr; {@code CORRUPT}；魔数解析通过但钉死格式
 * 版本不符 &rarr; {@code VERSION_MISMATCH}；I/O 失败 &rarr; {@code IO_ERROR}。快/强档摘要承接
 * {@code engine.network.integrity.Tsha1f} 与 p.2.10 {@code ChunkVerifier} 双档语义（非复制实现）。
 * {@code save} = 写入 + 校验重读；{@code load}/{@code hit} 对受损条目确定性跳过（决不抛异常），故缓存
 * 命中为 p.2.28.6 运行时跳过区块重生成。每个条目标记为
 * {@linkplain io.toterra.subterra.engine.render.lod.LodCache#REGENERABLE 可再生}（p.2.9 世界包
 * 可包含/可排除、p.2.18 可导出）——此处仅声明，导出本身为后续子项。
 */
package io.toterra.subterra.engine.render.lod;