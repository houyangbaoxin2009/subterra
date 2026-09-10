package io.toterra.subterra.api.export;

/**
 * 导出端口契约（p.2.16.3）：某个 {@link ExportKindSpec} 的自我描述落点——标明服务哪种种类，
 * 并携带一句 {@code description()} 描述其产出的文档形态。本子项不引入函数式文档生产接口；
 * 真正的导出指令接线留到 p.2.18。端口注册在 {@link ExporterRegistry} 上。api 侧镜像 engine
 * 的 {@code io.toterra.subterra.engine.export.ExportPort}（p.2.9.5 预留）。
 *
 * <p>Export port contract (p.2.16.3): a self-describing landing point for one
 * {@link ExportKindSpec}: it identifies which kind it serves and carries a
 * {@code description()} of the document form it produces. No functional
 * document-production interface is introduced here; the real export command
 * wiring is left to p.2.18. Ports are registered on the {@link ExporterRegistry}.
 * Mirrors the engine-side {@code io.toterra.subterra.engine.export.ExportPort}
 * (p.2.9.5 placeholder).
 */
public interface ExportPortSpec {

    /** 该端口服务的导出种类。The kind this port serves. */
    ExportKindSpec kind();

    /** 描述本文档形态。Describes this document form. */
    String description();
}
