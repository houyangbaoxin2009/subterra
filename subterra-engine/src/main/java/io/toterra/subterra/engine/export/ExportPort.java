package io.toterra.subterra.engine.export;

/**
 * p.2.9.5 export port (p.2.18 landing point) — a self-describing landing point
 * for one {@link ExportKind}: it identifies which kind it serves and carries a
 * two-sentence {@code description()} of the document form it produces. This
 * sub-item deliberately introduces no functional document-production interface;
 * the real export command wiring is left to p.2.18. Ports are registered on the
 * {@link ExportHub}.
 *
 * <p>p.2.9.5 导出端口（p.2.18 落点）——某个 {@link ExportKind} 的自我描述落点：标明服务哪种
 * 种类，并携带一个双语句 {@code description()} 描述它产出的文档形态。本子项刻意不引入函数式
 * 文档生产接口；真正的导出指令接线留到 p.2.18。端口注册在 {@link ExportHub} 上。
 */
public interface ExportPort {

    /** 该端口服务的导出种类。The kind this port serves. */
    ExportKind kind();

    /** 双语句：描述本文档形态。Two sentences describing this document form. */
    String description();
}