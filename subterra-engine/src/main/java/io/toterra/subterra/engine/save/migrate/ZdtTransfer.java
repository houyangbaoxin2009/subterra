package io.toterra.subterra.engine.save.migrate;

import io.toterra.subterra.engine.config.Td;
import io.toterra.subterra.engine.config.TdTable;
import io.toterra.subterra.engine.config.TdValue;
import io.toterra.subterra.engine.zd.ZdDocWriter;
import io.toterra.subterra.engine.zd.ZdHeader;
import io.toterra.subterra.engine.zd.ZdRow;

import java.util.Base64;
import java.util.List;

/**
 * 混合文档「td 元数据 + zd 载荷」的通用载体（p.2.3.3 泛化自 p.2.3.2 的 {@code LevelZdt}）。
 * p.2.3 各迁移目标（level / player / p.2.3.6 导出）的 `.zdt` 文档形状几乎完全相同：一层顶层具名表
 * {@code kind = [ version, meta, zd ]}，其中 {@code meta} 是 {@link TdTable} 元数据、
 * {@code zd} 是 {@code ZdDocWriter.write(0, rows)} 出来的 base64 载荷（flags=0）。把这一共同形状
 * 抽到一处，写入 / 反解析 / 取 zd 三者统一，避免每类各复制一遍。生成文本与 p.2.3.2 的
 * {@code LevelZdt} 产出逐字节一致（21 断言钉住）。
 * <p>
 * Generic carrier for the "td metadata + zd payload" hybrid document (p.2.3.3, generalised from
 * p.2.3.2's {@code LevelZdt}). Each p.2.3 migration target (level / player / the p.2.3.6 export)
 * shares the same `.zdt` document shape: one top-level named table {@code kind = [ version, meta,
 * zd ]} with {@code meta} a {@link TdTable} and {@code zd} a {@code ZdDocWriter.write(0, rows)}
 * Base64-embedded payload (flags = 0). This shared shape lives here so write / parse / extract-zd
 * are unified in one place rather than copied per kind. The emitted text is byte-identical to
 * p.2.3.2's {@code LevelZdt} output (pinned by its 21 assertions).
 */
public final class ZdtTransfer {

    private static final String VERSION_KEY = "version";
    private static final String META_KEY = "meta";
    private static final String ZD_KEY = "zd";

    private ZdtTransfer() {
    }

    /** 已解析的混合文档视图 / a parsed hybrid-document view. */
    public record TableDoc(String kind, long version, TdTable meta, byte[] zdBytes) {
    }

    /**
     * 序列化一份 {@code kind = [ version, meta, zd ]} 的 td 文档。载荷经
     * {@code ZdDocWriter.write(0, payload)} 序列化（flags=0，最小子集）、Base64 嵌入
     * {@code zd} 字段。Writes the td doc {@code kind = [ version, meta, zd ]}; the payload is
     * serialised via {@code ZdDocWriter.write(0, payload)} (flags = 0, the minimal subset) and
     * Base64-embedded into the {@code zd} field.
     */
    public static String write(String kind, long version, TdTable meta, List<ZdRow> payload) {
        String zdB64 = Base64.getEncoder().encodeToString(ZdDocWriter.write(0, payload));
        TdTable doc = TdTable.builder()
                .put(VERSION_KEY, TdValue.of(version))
                .put(META_KEY, meta)
                .put(ZD_KEY, TdValue.str(zdB64))
                .build();
        // 顶层具名表（kind = [...]），Td.parse 反解析时剥名 —— 与 DatapackExportArchive 同构。
        return "type tie<data>\n" + Td.write(TdTable.builder().put(kind, doc).build());
    }

    /**
     * 反解析一份混合文档 {@link TableDoc}。校验单一顶层 kind 表、version（&gt;= 1）、meta 表与 zd 头
     * 合法；任一不符抛 {@link IllegalArgumentException}。Parses a hybrid document into
     * {@link TableDoc}. Validates the single top-level kind table, the version (&gt;= 1), the meta
     * table and the zd header; a mismatch raises {@link IllegalArgumentException}.
     */
    public static TableDoc parse(String tdText) {
        TdTable outer = Td.parse(tdText);
        if (outer.keys().size() != 1) {
            throw new IllegalArgumentException("zdt document must have a single kind table, got "
                    + outer.keys().size());
        }
        String kind = outer.keys().get(0);
        TdValue kindValue = outer.get(kind);
        if (!(kindValue instanceof TdTable doc)) {
            throw new IllegalArgumentException("zdt document '" + kind + "' must be a table");
        }
        long version = doc.get(VERSION_KEY) != null ? doc.get(VERSION_KEY).asInt() : -1L;
        if (version < 1) {
            throw new IllegalArgumentException("zdt document missing or invalid version: " + version);
        }
        TdValue metaValue = doc.get(META_KEY);
        if (!(metaValue instanceof TdTable meta)) {
            throw new IllegalArgumentException("zdt document missing 'meta' table");
        }
        TdValue zdValue = doc.get(ZD_KEY);
        if (!(zdValue instanceof TdValue.Scalar s) || s.kind() != TdValue.Kind.STRING) {
            throw new IllegalArgumentException("zdt document missing 'zd' string");
        }
        byte[] zdBytes = Base64.getDecoder().decode(s.str());
        if (!ZdHeader.isZd(zdBytes) || ZdHeader.parseVersion(zdBytes, 0) != 2) {
            throw new IllegalArgumentException("zdt document zd payload has an invalid header");
        }
        return new TableDoc(kind, version, meta, zdBytes);
    }

    /**
     * 取内嵌 zd 载荷字节（含头校验）。Extracts the embedded zd payload bytes (header validated).
     */
    public static byte[] zdBytes(String tdText) {
        return parse(tdText).zdBytes();
    }
}