package io.toterra.subterra.engine.export;

import java.util.Map;

/**
 * p.2.18.4 迁移映射功能性生产者 —— 把某个迁移映射源物（构造注入的 {@code Map<String,String>}，
 * 源键 → 目标键，允许空映射）经 {@link MigrateMapsExporter} 确定性地产出为 td 文档
 * （{@code exportTd()}）与其 zd v2 二进制变体（{@code exportZd()}）。回水化恒等由导出器的往返
 * 契约保证（export∘rehydrate∘export 逐字节）：{@code rehydrateTd}/{@code rehydrateZd} 从输入
 * 重建映射再导出，调用方可断言 {@code td.equals(rehydrateTd(td))} 与
 * {@code Arrays.equals(zd, rehydrateZd(zd))}。构造时对源做防御性快照（TreeMap {@code from} 键
 * 字典序，经 {@code MigrateMapsExporter} 共享校验：null 映射 / null {@code from} 键 / 空白
 * {@code from} 键 / null {@code to} 值一律拒绝），导出纯读、确定性——源表在两次调用间的外部改动
 * 不影响输出。注册于 {@link ExportHub}（{@code ExportKind.MIGRATE_MAPS}）。
 *
 * <p>p.2.18.4 migrate-maps functional producer — deterministically renders a migration-map source
 * (a constructor-injected {@code Map<String,String>}, source key → target key; an empty map is
 * allowed) through {@link MigrateMapsExporter} as a td document ({@code exportTd()}) and its zd v2
 * binary variant ({@code exportZd()}). Rehydrate identity is guaranteed by the exporter's
 * round-trip contract (export∘rehydrate∘export byte-for-byte): {@code rehydrateTd}/{@code
 * rehydrateZd} rebuild the map from their input and re-export, so the caller may assert
 * {@code td.equals(rehydrateTd(td))} and {@code Arrays.equals(zd, rehydrateZd(zd))}. The source is
 * defensively snapshotted (TreeMap, {@code from} keys sorted, through the {@code
 * MigrateMapsExporter} shared validation: a null map / null {@code from} key / blank {@code from}
 * key / null {@code to} value is rejected) at construction; exports are read-only and
 * deterministic — external mutations between calls do not affect the output. Registered on
 * {@link ExportHub} ({@code ExportKind.MIGRATE_MAPS}).
 */
public final class MigrateMapsProducer implements ExportProducer {

    private final Map<String, String> migrationMap;

    public MigrateMapsProducer(Map<String, String> migrationMap) {
        this.migrationMap = MigrateMapsExporter.snapshot(migrationMap, "migrate_maps");
    }

    @Override
    public String tdType() {
        return ExportKind.MIGRATE_MAPS.form();
    }

    @Override
    public String exportTd() {
        return MigrateMapsExporter.exportTd(migrationMap);
    }

    @Override
    public byte[] exportZd() {
        return MigrateMapsExporter.exportZd(migrationMap);
    }

    @Override
    public String rehydrateTd(String td) {
        return MigrateMapsExporter.exportTd(MigrateMapsExporter.rehydrate(td));
    }

    @Override
    public byte[] rehydrateZd(byte[] zd) {
        return MigrateMapsExporter.exportZd(MigrateMapsExporter.rehydrateZd(zd));
    }
}
