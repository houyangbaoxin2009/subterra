/**
 * Engine instancing cut surface (p.2.27.1.1, Flywheel port sentinel — shape A): the
 * pure-JDK deterministic instancing core, modeled on Flywheel 1.0.6
 * ({@code dev.engine_room.flywheel.api.layout} and {@code api.backend}, fetched from
 * the {@code mc1.21.1/v1.0.5} tag of the Flywheel line that ships 1.0.6; license
 * materials under {@code META-INF/third-party/flywheel-1.0.6}). {@link
 * io.toterra.subterra.engine.render.instancing.InstanceFormat} is the deterministic
 * instance layout / format model (field order, scalar type, byte offset, alignment,
 * {@code byteSize()}, fixed-order {@code layout()}, byte-identical canonical
 * encoding); {@link io.toterra.subterra.engine.render.instancing.ShaderTemplate} is a
 * clean-room fixed-order shader template model (parts assembly + fixed-order
 * placeholder substitution, {@code compile()} yielding byte-identical canonical GLSL
 * text); {@link io.toterra.subterra.engine.render.instancing.RenderBackend} is the
 * backend SPI ({@code name()} / {@code supports(InstanceFormat)} / description /
 * priority) and {@link io.toterra.subterra.engine.render.instancing.BackendRegistry}
 * is its deterministic registry (fixed registration order, duplicate-name rejection,
 * lookup, deterministic default selection). Everything here is pure JDK — no
 * Minecraft, no OpenGL; actual GPU work is left to the runtime layer.
 *
 * <p>Engine 实例化切面（p.2.27.1.1，Flywheel 移植前哨——形态 A）：纯 JDK 确定性实例化核心，
 * 以 Flywheel 1.0.6 为形态参考（{@code dev.engine_room.flywheel.api.layout} 与
 * {@code api.backend}，抓取自承载 1.0.6 的 Flywheel 线的 {@code mc1.21.1/v1.0.5} tag；
 * 许可材料见 {@code META-INF/third-party/flywheel-1.0.6}）。{@link
 * io.toterra.subterra.engine.render.instancing.InstanceFormat} 是确定性实例布局/格式模型
 * （字段序、标量类型、字节偏移、对齐、{@code byteSize()}、固定序 {@code layout()}、逐字节一致
 * 的规范编码）；{@link io.toterra.subterra.engine.render.instancing.ShaderTemplate} 是
 * clean-room 固定序着色器模板模型（部件拼装 + 固定序占位符替换，{@code compile()} 产出逐字节
 * 一致的规范 GLSL 文本）；{@link io.toterra.subterra.engine.render.instancing.RenderBackend}
 * 是后端 SPI（{@code name()} / {@code supports(InstanceFormat)} / 描述 / 优先级），
 * {@link io.toterra.subterra.engine.render.instancing.BackendRegistry} 是其确定性注册表
 * （固定注册序、同名拒绝、查表、确定性缺省选择）。全部纯 JDK——不碰 MC、不碰 OpenGL；
 * 实际 GPU 调用留给 runtime 层。
 */
package io.toterra.subterra.engine.render.instancing;
