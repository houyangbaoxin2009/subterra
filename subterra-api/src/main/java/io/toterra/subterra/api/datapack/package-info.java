/**
 * p.2.33.1 对外 datapack 消费契约面（纯 JDK，api 不依赖引擎）：供上层/域模组（如 Overturn）确定性消费
 * td 数据包——条目查询（按 kind/id）、规则读取（{@code DatapackRules} 语义：后包胜出 + 存档覆盖胜出）、
 * td 文本规范解析与 export 规范形往返（{@code export∘rehydrate∘export} 逐字节恒等）、空数据包缺省。语义
 * 来源为 {@code engine.datapack} 的 {@code Datapack}/{@code DatapackEntry}/{@code EntryKind}/
 * {@code DatapackRules}/{@code DatapackExporter}/{@code DatapackLoader}（p.2.2 块）。采「api 定义形状 /
 * engine 镜像实现」范式：api 侧 {@link io.toterra.subterra.api.datapack.DatapackApi} 为契约门面，engine
 * 侧新增 {@code engine.datapack.DatapackApiMirror} 逐字导出引擎真实语义（同输入同输出），api 不依赖
 * engine 亦不触碰任何 MC 类。
 * <p>
 * p.2.33.1 the external td-datapack consumption contract surface (pure JDK, api does not depend on the
 * engine): lets upper layers / domain mods (e.g. Overturn) deterministically consume a td datapack — entry
 * lookup (by kind/id), rule reading ({@code DatapackRules} semantics: later-pack-wins + save-override-wins),
 * td-text canonicalization and export-shape round-trip ({@code export∘rehydrate∘export} byte-identical), and
 * empty-pack defaults. The semantics source is {@code engine.datapack}'s {@code Datapack}/{@code DatapackEntry}/
 * {@code EntryKind}/{@code DatapackRules}/{@code DatapackExporter}/{@code DatapackLoader} (p.2.2 blocks). Built
 * on the "api shapes / engine mirrors" pattern: the api-side {@link io.toterra.subterra.api.datapack.DatapackApi}
 * is the contract facade, and the engine-side {@code engine.datapack.DatapackApiMirror} exports the engine's real
 * semantics verbatim (same input → same output); the api does not depend on the engine nor touch any MC class.
 */
package io.toterra.subterra.api.datapack;