package io.toterra.subterra.engine.export;

import java.util.Map;

/**
 * p.2.18.3 配置功能性生产者 —— 把某个双层配置源物（构造注入的 {@code global} 与 {@code overrides}
 * 两层 {@code Map<String,String>}，允许空层）经 {@link ConfigExporter} 确定性地产出为 td 文档
 * （{@code exportTd()}）与其 zd v2 二进制变体（{@code exportZd()}）。回水化恒等由导出器的往返契约
 * 保证（export∘rehydrate∘export 逐字节）：{@code rehydrateTd}/{@code rehydrateZd} 从输入重建两层
 * 配置再导出，调用方可断言 {@code td.equals(rehydrateTd(td))} 与
 * {@code Arrays.equals(zd, rehydrateZd(zd))}。构造时对两层源做防御性快照（TreeMap 字典序，经
 * {@code ConfigExporter} 共享校验：null 层 / null key / 空白 key / null value 一律拒绝），导出纯读、
 * 确定性——源表在两次调用间的外部改动不影响输出。注册于 {@link ExportHub}
 * （{@code ExportKind.CONFIG}）。
 *
 * <p>p.2.18.3 config functional producer — deterministically renders a two-tier configuration
 * source (a constructor-injected {@code global} and {@code overrides} pair of
 * {@code Map<String,String>}; empty layers are allowed) through {@link ConfigExporter} as a td
 * document ({@code exportTd()}) and its zd v2 binary variant ({@code exportZd()}). Rehydrate
 * identity is guaranteed by the exporter's round-trip contract (export∘rehydrate∘export
 * byte-for-byte): {@code rehydrateTd}/{@code rehydrateZd} rebuild the two layers from their input
 * and re-export, so the caller may assert {@code td.equals(rehydrateTd(td))} and
 * {@code Arrays.equals(zd, rehydrateZd(zd))}. Both layers are defensively snapshotted (sorted
 * TreeMap, through the {@code ConfigExporter} shared validation: a null layer / null key / blank
 * key / null value is rejected) at construction; exports are read-only and deterministic —
 * external mutations between calls do not affect the output. Registered on {@link ExportHub}
 * ({@code ExportKind.CONFIG}).
 */
public final class ConfigProducer implements ExportProducer {

    private final Map<String, String> global;
    private final Map<String, String> overrides;

    public ConfigProducer(Map<String, String> global, Map<String, String> overrides) {
        this.global = ConfigExporter.snapshot(global, "config global");
        this.overrides = ConfigExporter.snapshot(overrides, "config overrides");
    }

    @Override
    public String tdType() {
        return ExportKind.CONFIG.form();
    }

    @Override
    public String exportTd() {
        return ConfigExporter.exportTd(global, overrides);
    }

    @Override
    public byte[] exportZd() {
        return ConfigExporter.exportZd(global, overrides);
    }

    @Override
    public String rehydrateTd(String td) {
        return ConfigExporter.exportTd(ConfigExporter.rehydrate(td));
    }

    @Override
    public byte[] rehydrateZd(byte[] zd) {
        return ConfigExporter.exportZd(ConfigExporter.rehydrateZd(zd));
    }
}
