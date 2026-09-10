package io.toterra.subterra.engine.export;

import io.toterra.subterra.engine.datapack.Datapack;
import io.toterra.subterra.engine.datapack.DatapackExportArchive;

import java.util.Objects;

/**
 * p.2.18.1 数据包导出功能性生产者 —— 把某个 {@link Datapack} 源物经
 * {@link DatapackExportArchive} 确定性地产出为 td 文档（{@code exportTd()}）与其 zd v2 二进制
 * 变体（{@code exportZd()}）。回水化恒等由归档自身的往返契约保证（
 * {@code export(rehydrate(export(dp))).equals(export(dp))} 逐字节）：{@code rehydrateTd}/
 * {@code rehydrateZd} 从输入重建数据包再导出，调用方可断言
 * {@code td.equals(rehydrateTd(td))} 与 {@code Arrays.equals(zd, rehydrateZd(zd))}。
 * 确定性、纯读——源数据包不被改动。
 *
 * <p>p.2.18.1 datapack-export functional producer — deterministically renders a
 * {@link Datapack} source through {@link DatapackExportArchive} as a td document
 * ({@code exportTd()}) and its zd v2 binary variant ({@code exportZd()}). Rehydrate
 * identity is guaranteed by the archive's own round-trip contract (
 * {@code export(rehydrate(export(dp))).equals(export(dp))} byte-for-byte):
 * {@code rehydrateTd}/{@code rehydrateZd} rebuild the datapack from their input and
 * re-export, so the caller may assert {@code td.equals(rehydrateTd(td))} and
 * {@code Arrays.equals(zd, rehydrateZd(zd))}. Deterministic, read-only — the source
 * datapack is never mutated.
 */
public final class DatapackArchiveProducer implements ExportProducer {

    private final Datapack source;

    public DatapackArchiveProducer(Datapack source) {
        this.source = Objects.requireNonNull(source, "datapack source must be non-null");
    }

    @Override
    public String tdType() {
        return ExportKind.DATAPACK.form();
    }

    @Override
    public String exportTd() {
        return DatapackExportArchive.export(source);
    }

    @Override
    public byte[] exportZd() {
        return ExportHub.zdOf(exportTd());
    }

    @Override
    public String rehydrateTd(String td) {
        return DatapackExportArchive.export(DatapackExportArchive.rehydrate(td));
    }

    @Override
    public byte[] rehydrateZd(byte[] zd) {
        return ExportHub.zdOf(DatapackExportArchive.export(DatapackExportArchive.rehydrate(ExportHub.tdOf(zd))));
    }
}
