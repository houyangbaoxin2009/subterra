package io.toterra.subterra.engine.export;

/**
 * p.2.18.1 功能性导出生产者 —— 一个 {@link ExportKind} 的正式导出契约：把同一份源物确定性地产出
 * 为两种一等变体——td 文档文本（{@code exportTd()}，本框架数据一等语言）与 zd v2 二进制
 * （{@code exportZd()}，经 engine.zd 写出，同一内容的二进制变体）。生产者持有其源物（例如
 * 一个 {@code SaveContainer} / {@code Datapack} / 世界包源），导出方法零参数、确定性、纯读；
 * 注册在 {@link ExportHub} 上（{@link ExportHub#register(ExportKind, ExportProducer)}），由
 * {@link ExportHub#producer(ExportKind)} 取回。
 *
 * <p>回水化恒等契约（调用方断言，最强往返判据）：
 * <ul>
 *   <li>{@code td.equals(rehydrateTd(td))}——{@code rehydrateTd(td)} 返回「从 td 重建源物再
 *       导出」的 td；</li>
 *   <li>{@code Arrays.equals(zd, rehydrateZd(zd))}——zd 变体同理（zd → td 树 → 重建 → 再导出）。</li>
 * </ul>
 * 若归档的再水化需要额外输入（如世界包需要原数据包目录与 meta），由生产者持有该源物并在重建时
 * 复用（"端口持有源物并缓存重建"）。确定性纪律：固定序、无时序、无随机、同输入同输出。
 *
 * <p>p.2.18.1 functional export producer — the formal export contract of one
 * {@link ExportKind}: it deterministically renders the same source in two first-class
 * variants — a td document text ({@code exportTd()}, the framework's data-first
 * language) and a zd v2 binary ({@code exportZd()}, written via engine.zd, the binary
 * variant of the same content). The producer holds its source (e.g. a
 * {@code SaveContainer} / {@code Datapack} / world-pack source); the export methods are
 * zero-argument, deterministic, read-only. Producers are registered on
 * {@link ExportHub} ({@link ExportHub#register(ExportKind, ExportProducer)}) and
 * retrieved via {@link ExportHub#producer(ExportKind)}.
 *
 * <p>Rehydrate-identity contract (asserted by the caller; the strongest round-trip
 * oracle):
 * <ul>
 *   <li>{@code td.equals(rehydrateTd(td))} — {@code rehydrateTd(td)} returns the td
 *       re-exported after rebuilding the source from td;</li>
 *   <li>{@code Arrays.equals(zd, rehydrateZd(zd))} — the zd variant likewise
 *       (zd → td tree → rebuild → re-export).</li>
 * </ul>
 * When an archive's rehydrate needs extra inputs (e.g. the world pack needs its
 * original datapack directory and meta), the producer holds that source material and
 * reuses it on rebuild ("the port holds the source and caches the rebuild").
 * Determinism discipline: fixed order, no timing, no randomness, same input → same
 * output.
 */
public interface ExportProducer {

    /**
     * 文档类型串（小写登记名，与 {@link ExportKind#form()} 一致，如
     * {@code "world"} / {@code "save"} / {@code "datapack"} / {@code "language_keys"} /
     * {@code "config"} / {@code "registries"} / {@code "migrate_maps"}）。
     *
     * Document type string (lowercase registration name, equal to
     * {@link ExportKind#form()}, e.g. {@code "world"} / {@code "save"} /
     * {@code "datapack"} / {@code "language_keys"} / {@code "config"} /
     * {@code "registries"} / {@code "migrate_maps"}).
     */
    String tdType();

    /**
     * 产出确定性 td 文档文本（同一输入恒同输出；源物为只读快照）。
     * Produces the deterministic td document text (same input → same output; the
     * source is a read-only snapshot).
     */
    String exportTd();

    /**
     * 同一内容的 zd v2 二进制变体（经 engine.zd 写出，逐字节确定；通常为
     * {@code ZdDocWriter.writeTree(0, Td.parse(exportTd()))}）。
     * The zd v2 binary variant of the same content (written via engine.zd,
     * byte-deterministic; typically
     * {@code ZdDocWriter.writeTree(0, Td.parse(exportTd()))}).
     */
    byte[] exportZd();

    /**
     * 从 {@code td} 重建源物再导出，返回重建后的 td 文档文本。调用方断言
     * {@code td.equals(rehydrateTd(td))}。
     * Rebuilds the source from {@code td} and re-exports; returns the rebuilt td
     * document text. The caller asserts {@code td.equals(rehydrateTd(td))}.
     */
    String rehydrateTd(String td);

    /**
     * 从 {@code zd} 重建源物再导出（zd → td 树 → 重建 → 再导出 zd）。调用方断言
     * {@code Arrays.equals(zd, rehydrateZd(zd))}。
     * Rebuilds the source from {@code zd} and re-exports (zd → td tree → rebuild →
     * re-export zd). The caller asserts {@code Arrays.equals(zd, rehydrateZd(zd))}.
     */
    byte[] rehydrateZd(byte[] zd);
}
