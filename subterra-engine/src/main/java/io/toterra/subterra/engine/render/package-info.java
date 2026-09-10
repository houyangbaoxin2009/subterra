/**
 * Engine vanilla-fidelity render surface core (p.2.27.1.2): the clean-room,
 * deterministic model of the Minecraft 1.21.1 vanilla rendering surfaces and vertex
 * formats — stage classification, render-surface / material-surface identity, and
 * vertex layout. Reference only: the stage names and semantics mirror the 1.21.1
 * vanilla {@code RenderType} surface set (as documented by the Yarn 1.21.1
 * {@code RenderLayer} / {@code VertexFormat} mappings); no vanilla, Sodium or
 * Embeddium source code is included. {@link
 * io.toterra.subterra.engine.render.RenderStage} is the fixed-order stage enum
 * (lowercase {@code form()}, {@code transparent()} and {@code sortOnCpu()} boolean
 * semantics); {@link io.toterra.subterra.engine.render.RenderSurface} and {@link
 * io.toterra.subterra.engine.render.MaterialSurface} are the deterministic surface /
 * material-surface identifiers (record identity + canonical fixed-order forms);
 * {@link io.toterra.subterra.engine.render.VertexLayout} is the vanilla-fidelity
 * vertex-layout model (fixed field order, per-field type / byte offset / alignment,
 * deterministic {@code byteSize()} / {@code layout()}, built-in {@code BLOCK} and
 * {@code ENTITY} constants). Everything here is pure JDK — no Minecraft, no OpenGL —
 * and complements the {@code engine.render.instancing} base. Deterministic
 * paradigm: fixed order, no timing, same input, same bytes.
 *
 * <p>Engine 原版保真渲染面核心（p.2.27.1.2）：Minecraft 1.21.1 原版渲染面与 vertex 格式的
 * clean-room 确定性模型——阶段分类、渲染面/材质面标识、顶点布局。仅作对照参考：阶段名与语义对应
 * 1.21.1 原版 {@code RenderType} 渲染面集合（按 Yarn 1.21.1 {@code RenderLayer} /
 * {@code VertexFormat} 映射记录）；不包含任何原版、Sodium 或 Embeddium 源码。{@link
 * io.toterra.subterra.engine.render.RenderStage} 为固定序阶段枚举（小写 {@code form()}、
 * {@code transparent()} 与 {@code sortOnCpu()} 布尔语义）；{@link
 * io.toterra.subterra.engine.render.RenderSurface} 与 {@link
 * io.toterra.subterra.engine.render.MaterialSurface} 为确定性渲染面/材质面标识（record 身份 +
 * 规范固定序形式）；{@link io.toterra.subterra.engine.render.VertexLayout} 为原版保真顶点布局模型
 * （固定字段序、每字段类型/字节偏移/对齐、确定性 {@code byteSize()} / {@code layout()}、内建
 * {@code BLOCK} 与 {@code ENTITY} 常量）。全部纯 JDK——无 MC、无 OpenGL——并与
 * {@code engine.render.instancing} 基座互补。确定性范式：固定序、无时序、同输入、同字节。
 */
package io.toterra.subterra.engine.render;
