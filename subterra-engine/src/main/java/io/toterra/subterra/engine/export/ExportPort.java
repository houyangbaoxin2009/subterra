package io.toterra.subterra.engine.export;

/**
 * p.2.9.5 export port (p.2.18 landing point) — a self-describing landing point
 * for one {@link ExportKind}: it identifies which kind it serves and carries a
 * two-sentence {@code description()} of the document form it produces. Since
 * p.2.18.1 the formal functional document production lives in the separate
 * {@link ExportProducer} contract (the {@link ExportHub} holds ports and producers
 * side by side); this interface stays descriptive and backward compatible (existing
 * anonymous implementations only need {@code kind()}/{@code description()}). Ports
 * are registered on the {@link ExportHub}.
 *
 * <p>p.2.9.5 导出端口（p.2.18 落点）——某个 {@link ExportKind} 的自我描述落点：标明服务哪种
 * 种类，并携带一个双语句 {@code description()} 描述它产出的文档形态。p.2.18.1 起正式的函数式
 * 文档生产由独立的 {@link ExportProducer} 契约承担（{@link ExportHub} 同时持有端口与生产者）；
 * 本接口保持描述性、向后兼容（既有匿名实现仅需 {@code kind()}/{@code description()}）。
 * 端口注册在 {@link ExportHub} 上。
 */
public interface ExportPort {

    /** 该端口服务的导出种类。The kind this port serves. */
    ExportKind kind();

    /** 双语句：描述本文档形态。Two sentences describing this document form. */
    String description();
}