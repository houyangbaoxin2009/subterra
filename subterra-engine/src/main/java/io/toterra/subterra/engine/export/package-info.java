/**
 * Engine export cut surface (p.2.9.5, forward port for p.2.18): the engine-level
 * export aspects. {@link io.toterra.subterra.engine.export.ExportKind} enumerates
 * the stable kind identities ({@code world} / {@code save} / {@code datapack} /
 * {@code language_keys} / {@code config} / {@code registries} /
 * {@code migrate_maps}), {@link io.toterra.subterra.engine.export.ExportPort} is a
 * self-describing landing point, and {@link io.toterra.subterra.engine.export.ExportHub}
 * binds the two deterministically. Since p.2.18.1 the functional document production
 * lives in {@link io.toterra.subterra.engine.export.ExportProducer} (td/zd dual
 * format, rehydrate identity), with the world/save/datapack forms backed by
 * {@code WorldPackProducer} / {@code SaveArchiveProducer} /
 * {@code DatapackArchiveProducer}; the four new content forms keep descriptive
 * placeholder ports, their producers land in p.2.18.x.
 *
 * <p>Engine 导出切面（p.2.9.5，p.2.18 预留口）：engine 级导出切面。
 * {@link io.toterra.subterra.engine.export.ExportKind} 枚举稳定种类标识（{@code world} /
 * {@code save} / {@code datapack} / {@code language_keys} / {@code config} /
 * {@code registries} / {@code migrate_maps}），
 * {@link io.toterra.subterra.engine.export.ExportPort} 是自我描述落点，
 * {@link io.toterra.subterra.engine.export.ExportHub} 确定性地把二者绑定。p.2.18.1 起正式的
 * 函数式文档生产由 {@link io.toterra.subterra.engine.export.ExportProducer} 承担（td/zd 双格式、
 * 回水化恒等），world/save/datapack 形态分别由 {@code WorldPackProducer} /
 * {@code SaveArchiveProducer} / {@code DatapackArchiveProducer} 支撑；四个新内容形态保留描述性
 * 占位端口，其生产者由 p.2.18.x 补齐。
 */
package io.toterra.subterra.engine.export;
