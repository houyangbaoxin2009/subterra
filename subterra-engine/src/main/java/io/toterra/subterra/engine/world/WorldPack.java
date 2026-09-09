package io.toterra.subterra.engine.world;

import io.toterra.subterra.engine.config.Td;
import io.toterra.subterra.engine.config.TdTable;
import io.toterra.subterra.engine.config.TdValue;
import io.toterra.subterra.engine.datapack.DatapackPack;
import io.toterra.subterra.engine.save.SaveContainer;
import io.toterra.subterra.engine.save.SaveExportArchive;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * p.2.9.1 world-pack artifact form — the "world as artifact" envelope that makes
 * a whole world portable: a single td document bundling the {@link
 * SaveExportArchive} serialization of a {@link SaveContainer} and the {@link
 * DatapackPack} package of a datapack directory, each as a base64 payload, plus
 * a sorted metadata map. Structurally isomorphic to the archive counterparts
 * ({@link SaveExportArchive} / {@code DatapackExportArchive}): named top-level
 * table ({@code world}) + {@code type tie<data>} header + {@code version}, so
 * {@code Td.parse} strips the optional table name on rehydration.
 *
 * <p>Document shape (pure JDK, no JSON):
 * <pre>{@code
 * type tie<data>
 * world = [
 *   version = 1,
 *   meta = [ [ k = "world.id", v = "mirror-001" ], ... ],
 *   save = "base64(SaveExportArchive.export(saveContainer))",
 *   datapack = "base64(DatapackPack.export(datapackDir))",
 * ]
 * }</pre>
 * Determinism (p.2.9.2): section order is fixed ({@code version → meta →
 * save → datapack}); {@code meta} keys are sorted via a {@link TreeMap} and
 * always emitted (empty table when none); the {@code save}/{@code datapack}
 * payloads are themselves deterministic ({@link SaveExportArchive} /
 * {@link DatapackPack} guarantee it). No wall-clock, no timestamps, no
 * default-seed randomness — a deterministic archive.
 *
 * <p>Round-trip contract (asserted by the p.2.9.4 probe):
 * {@code export(rehydrate(export(w))).equals(export(w))} byte-for-byte; the
 * rehydrated {@link SaveContainer} is field-level equal to the one exported
 * ({@code slots()} identical, each slot's document text identical, overlays /
 * global rules identical); {@code datapackDocument} equals the decoded value.
 * This is the stable-seed bridge to p.2.8: {@code meta} carries deterministic
 * steady-state keys such as {@code world.seed} / {@code world.tick}.
 *
 * <p>格式示例（纯 JDK，无 JSON）：
 * <pre>{@code
 * type tie<data>
 * world = [
 *   version = 1,
 *   meta = [ [ k = "world.id", v = "mirror-001" ], ... ],
 *   save = "base64(SaveExportArchive.export(saveContainer))",
 *   datapack = "base64(DatapackPack.export(datapackDir))",
 * ]
 * }</pre>
 * 确定性（p.2.9.2）：段序固定（{@code version → meta → save → datapack}）；{@code meta}
 * 键经 {@link TreeMap} 排序并恒输出（无则空表）；{@code save}/{@code datapack} 载荷本身
 * 确定（由 {@link SaveExportArchive} / {@link DatapackPack} 保证）。无时钟、无时间戳、
 * 无默认种子随机——确定性归档。
 *
 * <p>往返契约（由 p.2.9.4 探针断言）：
 * {@code export(rehydrate(export(w))).equals(export(w))} 逐字节一致；再水化的
 * {@link SaveContainer} 与导出前的容器字段级等值（{@code slots()} 一致、每槽文档文本一致、
 * overlay / 全局规则一致）；{@code datapackDocument} 与解码值一致。这是 p.2.8 稳态衔接：
 * {@code meta} 携带 {@code world.seed} / {@code world.tick} 等确定性稳态键。
 */
public final class WorldPack {

    /** Current world-pack format version. */
    public static final long VERSION = 1;

    private static final String MARKER = "world";

    private WorldPack() {
    }

    /**
     * Serializes a world into a single td document: the whole {@link SaveContainer}
     * via {@link SaveExportArchive} and the datapack directory via
     * {@link DatapackPack}, each base64-encoded, plus the sorted {@code meta}
     * (null tolerated as empty). Deterministic and pure — the container is never
     * mutated.
     *
     * 把整个世界序列化为单一 td 文档：整个 {@link SaveContainer} 经
     * {@link SaveExportArchive}、数据包目录经 {@link DatapackPack}，各自 base64 编码后装入，
     * 外加排序过的 {@code meta}（null 视为空表）。确定性、纯函数——容器不被改动。
     */
    public static String export(SaveContainer save, Path datapackDir, Map<String, TdValue> meta) {
        TdTable.Builder metaB = TdTable.builder();
        Map<String, TdValue> sorted = meta == null ? Map.of() : new TreeMap<>(meta);
        for (Map.Entry<String, TdValue> e : sorted.entrySet()) {
            metaB.element(TdTable.builder()
                    .put("k", TdValue.str(e.getKey()))
                    .put("v", e.getValue())
                    .build());
        }
        TdTable doc = TdTable.builder()
                .put("version", TdValue.of(VERSION))
                .put("meta", metaB.build())
                .put("save", TdValue.str(encode(SaveExportArchive.export(save))))
                .put("datapack", TdValue.str(encode(DatapackPack.export(datapackDir))))
                .build();
        // Named top-level table (same trick as SaveExportArchive / DatapackPack):
        // wrap so the document is `world = [...]`; Td.parse strips the name on
        // rehydration.
        return "type tie<data>\n" + Td.write(TdTable.builder().put(MARKER, doc).build());
    }

    /**
     * Parses a world-pack document back into a {@link WorldPackResult}: the
     * {@code save} payload rehydrates into a {@link SaveContainer} via
     * {@link SaveExportArchive}, the {@code datapack} payload is kept as the
     * decoded document verbatim, {@code meta} is read back key-sorted. Malformed
     * input throws {@link IllegalArgumentException}: missing/non-table {@code world},
     * out-of-range {@code version}, a missing or non-string {@code save}/{@code
     * datapack} field, or a failed base64 decode.
     *
     * 把世界包文档解析回 {@link WorldPackResult}：{@code save} 载荷经
     * {@link SaveExportArchive} 再水化为 {@link SaveContainer}；{@code datapack} 载荷保留为
     * 解码后的原样文档；{@code meta} 按键序读回。畸形输入抛 {@link IllegalArgumentException}：
     * 缺/非表 {@code world}、{@code version} 越界、缺/非字符串 {@code save}/{@code datapack}
     * 字段、或 base64 解码失败。
     */
    public static WorldPackResult rehydrate(String source) {
        TdTable root = Td.parse(source);
        TdValue markerValue = root.get(MARKER);
        if (!(markerValue instanceof TdTable doc)) {
            throw new IllegalArgumentException("not a world pack (missing 'world')");
        }
        long version = doc.get("version") != null ? doc.get("version").asInt() : -1;
        if (version != VERSION) {
            throw new IllegalArgumentException("unsupported world pack version: " + version);
        }
        SaveContainer container = SaveExportArchive.rehydrate(decode(requiredString(doc, "save")));
        String datapackDocument = decode(requiredString(doc, "datapack"));
        return new WorldPackResult(container, datapackDocument, readMeta(doc.get("meta")));
    }

    // ---------- helpers ----------

    /** Read the {@code save}/{@code datapack} base64 field; missing/non-string → throw. */
    private static String requiredString(TdTable doc, String key) {
        TdValue v = doc.get(key);
        if (v == null) {
            throw new IllegalArgumentException("world pack missing '" + key + "' field");
        }
        if (!(v instanceof TdValue.Scalar s) || s.kind() != TdValue.Kind.STRING) {
            throw new IllegalArgumentException("world pack '" + key + "' field is not a string");
        }
        return s.str();
    }

    /** Reads the {@code meta} list {@code [ [ k = ..., v = ... ], ... ]} back into a map. */
    private static Map<String, TdValue> readMeta(TdValue value) {
        Map<String, TdValue> out = new LinkedHashMap<>();
        if (!(value instanceof TdTable list)) {
            return out; // absent/malformed meta → empty, schema-free
        }
        for (TdValue item : list.elements()) {
            if (!(item instanceof TdTable t)) {
                continue;
            }
            String k = t.get("k") != null ? t.get("k").asString() : "";
            if (k.isBlank()) {
                continue;
            }
            TdValue v = t.get("v");
            if (v != null) {
                out.put(k, v);
            }
        }
        return out;
    }

    private static String encode(String s) {
        return Base64.getEncoder().encodeToString(s.getBytes(StandardCharsets.UTF_8));
    }

    private static String decode(String s) {
        try {
            return new String(Base64.getDecoder().decode(s), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("world pack base64 decode failed", e);
        }
    }
}