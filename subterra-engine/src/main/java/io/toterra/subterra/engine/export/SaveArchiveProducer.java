package io.toterra.subterra.engine.export;

import io.toterra.subterra.engine.save.SaveContainer;
import io.toterra.subterra.engine.save.SaveExportArchive;

import java.util.Objects;

/**
 * p.2.18.1 存档导出功能性生产者 —— 把某个 {@link SaveContainer} 源物经
 * {@link SaveExportArchive} 确定性地产出为 td 文档（{@code exportTd()}）与其 zd v2 二进制变体
 * （{@code exportZd()}）。回水化恒等由归档自身的往返契约保证（
 * {@code export(rehydrate(export(c))).equals(export(c))} 逐字节）：{@code rehydrateTd}/{@code
 * rehydrateZd} 从输入重建容器再导出，调用方可断言 {@code td.equals(rehydrateTd(td))} 与
 * {@code Arrays.equals(zd, rehydrateZd(zd))}。确定性、纯读——源容器不被改动。
 *
 * <p>p.2.18.1 save-export functional producer — deterministically renders a
 * {@link SaveContainer} source through {@link SaveExportArchive} as a td document
 * ({@code exportTd()}) and its zd v2 binary variant ({@code exportZd()}). Rehydrate
 * identity is guaranteed by the archive's own round-trip contract (
 * {@code export(rehydrate(export(c))).equals(export(c))} byte-for-byte):
 * {@code rehydrateTd}/{@code rehydrateZd} rebuild the container from their input and
 * re-export, so the caller may assert {@code td.equals(rehydrateTd(td))} and
 * {@code Arrays.equals(zd, rehydrateZd(zd))}. Deterministic, read-only — the source
 * container is never mutated.
 */
public final class SaveArchiveProducer implements ExportProducer {

    private final SaveContainer source;

    public SaveArchiveProducer(SaveContainer source) {
        this.source = Objects.requireNonNull(source, "save source must be non-null");
    }

    @Override
    public String tdType() {
        return ExportKind.SAVE.form();
    }

    @Override
    public String exportTd() {
        return SaveExportArchive.export(source);
    }

    @Override
    public byte[] exportZd() {
        return ExportHub.zdOf(exportTd());
    }

    @Override
    public String rehydrateTd(String td) {
        return SaveExportArchive.export(SaveExportArchive.rehydrate(td));
    }

    @Override
    public byte[] rehydrateZd(byte[] zd) {
        return ExportHub.zdOf(SaveExportArchive.export(SaveExportArchive.rehydrate(ExportHub.tdOf(zd))));
    }
}
