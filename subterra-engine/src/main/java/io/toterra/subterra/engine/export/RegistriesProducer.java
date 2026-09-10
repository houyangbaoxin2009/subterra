package io.toterra.subterra.engine.export;

import java.util.List;
import java.util.Map;

/**
 * p.2.18.4 注册表功能性生产者 —— 把某个注册表快照源物（构造注入的
 * {@code Map<String,List<String>>}，注册表名 → 条目 id 列表，允许空注册表）经
 * {@link RegistriesExporter} 确定性地产出为 td 文档（{@code exportTd()}）与其 zd v2 二进制变体
 * （{@code exportZd()}）。回水化恒等由导出器的往返契约保证（export∘rehydrate∘export 逐字节）：
 * {@code rehydrateTd}/{@code rehydrateZd} 从输入重建快照再导出，调用方可断言
 * {@code td.equals(rehydrateTd(td))} 与 {@code Arrays.equals(zd, rehydrateZd(zd))}。构造时对源做
 * 防御性快照（TreeMap 注册表名字典序、ids 保持输入序，经 {@code RegistriesExporter} 共享校验：
 * null 注册表 / null 或空白注册表名 / null id 列表 / null 或空白 id 一律拒绝），导出纯读、确定
 * 性——源表在两次调用间的外部改动不影响输出。注册于 {@link ExportHub}
 * （{@code ExportKind.REGISTRIES}）。
 *
 * <p>p.2.18.4 registries functional producer — deterministically renders a registries snapshot
 * source (a constructor-injected {@code Map<String,List<String>>}, registry name → entry-id list;
 * empty registries are allowed) through {@link RegistriesExporter} as a td document
 * ({@code exportTd()}) and its zd v2 binary variant ({@code exportZd()}). Rehydrate identity is
 * guaranteed by the exporter's round-trip contract (export∘rehydrate∘export byte-for-byte):
 * {@code rehydrateTd}/{@code rehydrateZd} rebuild the snapshot from their input and re-export, so
 * the caller may assert {@code td.equals(rehydrateTd(td))} and
 * {@code Arrays.equals(zd, rehydrateZd(zd))}. The source is defensively snapshotted (TreeMap,
 * registry names sorted, ids in input order, through the {@code RegistriesExporter} shared
 * validation: a null registries map / null or blank registry name / null ids list / null or blank
 * id is rejected) at construction; exports are read-only and deterministic — external mutations
 * between calls do not affect the output. Registered on {@link ExportHub}
 * ({@code ExportKind.REGISTRIES}).
 */
public final class RegistriesProducer implements ExportProducer {

    private final Map<String, List<String>> registries;

    public RegistriesProducer(Map<String, List<String>> registries) {
        this.registries = RegistriesExporter.snapshot(registries, "registries");
    }

    @Override
    public String tdType() {
        return ExportKind.REGISTRIES.form();
    }

    @Override
    public String exportTd() {
        return RegistriesExporter.exportTd(registries);
    }

    @Override
    public byte[] exportZd() {
        return RegistriesExporter.exportZd(registries);
    }

    @Override
    public String rehydrateTd(String td) {
        return RegistriesExporter.exportTd(RegistriesExporter.rehydrate(td));
    }

    @Override
    public byte[] rehydrateZd(byte[] zd) {
        return RegistriesExporter.exportZd(RegistriesExporter.rehydrateZd(zd));
    }
}
