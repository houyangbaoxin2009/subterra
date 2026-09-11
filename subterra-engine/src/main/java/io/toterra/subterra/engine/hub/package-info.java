/**
 * In-game mod hub 表单自动生成核心（p.2.23.1，纯 JDK）：由 td schema 确定性产出
 * 类型化表单结构（{@link io.toterra.subterra.engine.hub.FormStructure} /
 * {@link io.toterra.subterra.engine.hub.FormField}）、表单渲染数据
 * （{@link io.toterra.subterra.engine.hub.FormRenderData}）与门面
 * {@link io.toterra.subterra.engine.hub.HubFormGen}（schema → 结构 → td 往返）。
 * 承接 p.2.12.4 {@code FormDataGen} 的 kind→widget 固定映射与形契约，但产出类型化
 * 结构而非纯文本；表单 td 文档（{@code form = [...]}）为本框架数据一等语言形态，
 * 固定字段序、禁时间戳、逐字节一致。纯 JVM，不碰 MC；确定性范式与全 engine 一致。
 * <p>
 * In-game mod hub auto-form core (p.2.23.1, pure JDK): deterministically derives a
 * typed form structure ({@link io.toterra.subterra.engine.hub.FormStructure} /
 * {@link io.toterra.subterra.engine.hub.FormField}), form render data
 * ({@link io.toterra.subterra.engine.hub.FormRenderData}) and the facade
 * {@link io.toterra.subterra.engine.hub.HubFormGen} (schema → structure → td
 * round-trip) from a td schema. It carries over p.2.12.4 {@code FormDataGen}'s fixed
 * kind→widget map and per-root shapes, but yields typed structures rather than plain
 * text; the form td document ({@code form = [...]}) is a first-class data shape of
 * this framework — fixed field order, no timestamps, byte-identical. Pure JVM, no MC
 * coupling; determinism follows the whole-engine paradigm.
 */
package io.toterra.subterra.engine.hub;
