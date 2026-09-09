/**
 * Engine export cut surface (p.2.9.5, forward port for p.2.18): the placeholder
 * aspect for engine-level export commands. {@link
 * io.toterra.subterra.engine.export.ExportKind} enumerates the stable kind
 * identities ({@code world} / {@code save} / {@code datapack}),
 * {@link io.toterra.subterra.engine.export.ExportPort} is a self-describing
 * landing point, and {@link io.toterra.subterra.engine.export.ExportHub} binds
 * the two deterministically (with two built-in ports: the world-pack form and
 * the save-export archive form). No functional document-production interface is
 * introduced yet — the real {@code /subterra export <form>} command wiring is a
 * p.2.18 concern.
 *
 * <p>Engine 导出切面（p.2.9.5，p.2.18 预留口）：engine 级导出指令的占位切面。
 * {@link io.toterra.subterra.engine.export.ExportKind} 枚举稳定种类标识（{@code world} /
 * {@code save} / {@code datapack}），{@link io.toterra.subterra.engine.export.ExportPort}
 * 是自我描述落点，{@link io.toterra.subterra.engine.export.ExportHub} 确定性地把二者绑定
 * （含两个内建端口：世界包形态与存档导出档案形态）。暂不引入函数式文档生产接口——真正的
 * {@code /subterra export <form>} 指令接线是 p.2.18 的事。
 */
package io.toterra.subterra.engine.export;
