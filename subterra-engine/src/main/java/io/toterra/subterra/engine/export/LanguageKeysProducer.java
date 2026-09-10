package io.toterra.subterra.engine.export;

import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * p.2.18.2 语言键功能性生产者 —— 把某个语言键源物（构造注入的 {@code Map<String,String>}，
 * 允许空表）经 {@link LanguageKeysExporter} 确定性地产出为 td 文档（{@code exportTd()}）与其
 * zd v2 二进制变体（{@code exportZd()}）。回水化恒等由导出器的往返契约保证（export∘rehydrate∘
 * export 逐字节）：{@code rehydrateTd}/{@code rehydrateZd} 从输入重建键表再导出，调用方可断言
 * {@code td.equals(rehydrateTd(td))} 与 {@code Arrays.equals(zd, rehydrateZd(zd))}。构造时对源
 * 键表做防御性拷贝（TreeMap 字典序快照），导出纯读、确定性——源表在两次调用间的外部改动不影响
 * 输出。注册于 {@link ExportHub}（{@code ExportKind.LANGUAGE_KEYS}）。
 *
 * <p>p.2.18.2 language-keys functional producer — deterministically renders a
 * language-keys source (a constructor-injected {@code Map<String,String>}; an empty
 * table is allowed) through {@link LanguageKeysExporter} as a td document
 * ({@code exportTd()}) and its zd v2 binary variant ({@code exportZd()}). Rehydrate
 * identity is guaranteed by the exporter's round-trip contract (export∘rehydrate∘export
 * byte-for-byte): {@code rehydrateTd}/{@code rehydrateZd} rebuild the key table from
 * their input and re-export, so the caller may assert
 * {@code td.equals(rehydrateTd(td))} and {@code Arrays.equals(zd, rehydrateZd(zd))}.
 * The source map is defensively copied (sorted TreeMap snapshot) at construction;
 * exports are read-only and deterministic — external mutations between calls do not
 * affect the output. Registered on {@link ExportHub} ({@code ExportKind.LANGUAGE_KEYS}).
 */
public final class LanguageKeysProducer implements ExportProducer {

    private final Map<String, String> keys;

    public LanguageKeysProducer(Map<String, String> keys) {
        this.keys = new TreeMap<>(Objects.requireNonNull(keys, "language keys must be non-null"));
    }

    @Override
    public String tdType() {
        return ExportKind.LANGUAGE_KEYS.form();
    }

    @Override
    public String exportTd() {
        return LanguageKeysExporter.exportTd(keys);
    }

    @Override
    public byte[] exportZd() {
        return LanguageKeysExporter.exportZd(keys);
    }

    @Override
    public String rehydrateTd(String td) {
        return LanguageKeysExporter.exportTd(LanguageKeysExporter.rehydrate(td));
    }

    @Override
    public byte[] rehydrateZd(byte[] zd) {
        return LanguageKeysExporter.exportZd(LanguageKeysExporter.rehydrateZd(zd));
    }
}
