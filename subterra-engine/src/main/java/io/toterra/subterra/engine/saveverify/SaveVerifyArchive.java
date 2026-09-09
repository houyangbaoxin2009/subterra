package io.toterra.subterra.engine.saveverify;

import io.toterra.subterra.engine.config.Td;
import io.toterra.subterra.engine.config.TdTable;
import io.toterra.subterra.engine.config.TdValue;
import io.toterra.subterra.engine.saveverify.chunk.Chunk;
import io.toterra.subterra.engine.saveverify.chunk.ChunkPlan;
import io.toterra.subterra.engine.saveverify.chunk.ChunkVerifier;
import io.toterra.subterra.engine.saveverify.sig.Ed25519Keys;
import io.toterra.subterra.engine.saveverify.sig.SaveSigner;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * p.2.10.3 可验证存档案信封 —— 把 p.2.10.1 的 zd 块划分与 p.2.10.2 的 ed25519 强签名拼进一个
 * "可验证存档案"（verifiable-save archive）单一 td 文档。与 {@code WorldPack}/zdt 同构：具名顶层表
 * + {@code type tie<data>} 头 + 固定段序 {@code version → meta → config → payload → chunks → sig}。
 * <p>
 * The verifiable-save archive envelope (p.2.10.3): the p.2.10.1 zd-chunk partition and the p.2.10.2
 * ed25519 strong signature are combined into a single td document, isomorphic to {@code WorldPack}/
 * zdt: a named top-level table + {@code type tie<data>} header + the fixed section order
 * {@code version → meta → config → payload → chunks → sig}.
 *
 * <p>文档形状（纯 JDK，无 JSON）：Document shape (pure JDK, no JSON):
 * <pre>{@code
 * type tie<data>
 * saveverify = [
 *   version = 1,
 *   meta = [ [ k = "algo", v = "ed25519" ], [ k = "kind", v = "saveverify" ], [ k = "seq", v = N ] ],
 *   config = [ chunkSize = 256, hash = "tsha1f" ],
 *   payload = "base64(受保护内容字节)",
 *   chunks = [ [ index = 0, offset = 0, length = ..., fast = "...", strong = "..." ], ... ],
 *   sig = "base64(ed25519 签名)",
 * ]
 * }</pre>
 *
 * <p><b>签名头（canonical head）解析态单源</b>：签名头字节<b>必须从『解析后的序列化形态』构建</b>，
 * 进度保证 encode 与 verify 两侧字节恒等 —— encode 先序列化得文本 → 自己 decode → 从每一解析片构建 head
 * → 签名 → 合成最终文本；verify 同样 decode → 从解析片构建 head → 验签。禁止从编码前内存对象直接拼 head。
 * <p>
 * <b>Signature-head single-source of truth (parsed state)</b>: the head bytes <b>must be built from the
 * parsed serialized form</b>, guaranteeing byte-equality on both the encode and verify sides — encode
 * serializes first, decodes itself, builds the head from each parsed piece, signs, and only then emits the
 * final text; verify likewise decodes, builds the head from the parsed pieces, and verifies. Building the
 * head from pre-encode in-memory objects is forbidden.
 *
 * <p>确定性（p.2.10.2）：无时钟/时间戳/随机；{@code meta}/{@code config} 以固定作者序写入；
 * {@code chunks} 按 index 严格升序 {@code element()} 追加；{@code Td.write} 对相同表逐字节确定。
 * 签名本身 RFC 8032 确定性（同 keys+head 恒同 64B，无需 SecureRandom）。
 * <p>
 * Determinism: no clock/timestamp/random; {@code meta}/{@code config} written in a fixed authoring order;
 * {@code chunks} appended strictly index-ascending via {@code element()}; {@code Td.write} is byte-for-byte
 * deterministic for the same table. The signature itself is RFC 8032 deterministic.
 */
public final class SaveVerifyArchive {

    /** 当前档案格式版本。Current archive format version. */
    public static final long VERSION = 1;

    /** 顶层具名表标记。Top-level named-table marker. */
    public static final String MARKER = "saveverify";

    /** 默认固定切块大小。Default fixed chunk size. */
    public static final int DEFAULT_CHUNK_SIZE = 256;

    private static final String CONFIG_HASH = "tsha1f";
    private static final String META_ALGO = "ed25519";
    private static final String META_KIND = "saveverify";

    private SaveVerifyArchive() {
    }

    /**
     * 编码一个可验证存档案：以默认 {@link #DEFAULT_CHUNK_SIZE} 分块 → 建 meta/config/chunks → 序列化
     * → 自己 decode → 从解析片构建 head → {@link SaveSigner#sign} → base64 进 sig → 合成最终文本。
     *
     * @param keys    密钥对（null 抛 {@link IllegalArgumentException}）。
     * @param payload 受保护内容字节（不透明字节；null 视为空载荷）。
     * @param seq     档案序号，必须 &gt;= 1，否则抛 {@link IllegalArgumentException}。
     */
    public static String encode(Ed25519Keys keys, byte[] payload, long seq) {
        if (keys == null) {
            throw new IllegalArgumentException("keys must not be null");
        }
        if (seq < 1) {
            throw new IllegalArgumentException("seq must be >= 1, got " + seq);
        }
        ChunkPlan plan = ChunkVerifier.partition(payload, DEFAULT_CHUNK_SIZE);
        TdTable meta = metaTable(seq);
        TdTable config = configTable(DEFAULT_CHUNK_SIZE);
        TdTable chunks = chunksTable(plan.chunks());
        String payloadB64 = b64(payload == null ? new byte[0] : payload);

        // Sig unknown yet: serialize a shell (no sig), decode it self, then build the canonical head
        // from the parsed pieces (single source of truth) and sign.
        Core core = parseCore(wrap(doc(meta, config, chunks, payloadB64, null)));
        byte[] head = buildHead(core.version(), core.seq(), core.chunkSize(), core.chunks());
        byte[] sig = SaveSigner.sign(keys, head);

        return wrap(doc(meta, config, chunks, payloadB64, b64(sig)));
    }

    /**
     * 解析一个可验证存档案文档为解析视图。校验单 kind 表/version/meta(config/payload/chunks/sig 齐全且类型
     * 正确；畸形输入抛 {@link IllegalArgumentException}（校验方 {@code SaveVerifyLedger} 会捕获并映射为
     * {@code shape} 判负）。
     *
     * @param tdText td 文档文本。
     * @return 解析视图（{@link ParsedArchive}）。
     */
    public static ParsedArchive decode(String tdText) {
        Core core = parseCore(tdText);
        TdValue sigValue = core.doc().get("sig");
        if (!(sigValue instanceof TdValue.Scalar s) || s.kind() != TdValue.Kind.STRING) {
            throw new IllegalArgumentException("saveverify archive missing or non-string 'sig'");
        }
        String sigB64 = s.str();
        return new ParsedArchive(core.version(), core.seq(), core.chunkSize(),
                core.payloadBytes(), core.chunks(), sigB64, unb64(sigB64));
    }

    /**
     * 从解析视图重编码（可选内部工具）：按视图的 seq/chunkSize/chunks/payload 重建并重新签名，输出
     * 与 {@link #encode} 结构一致的文本。视图若能解析自 {@link #encode} 的产物，则逐字节一致。
     */
    public static String reencode(Ed25519Keys keys, ParsedArchive view) {
        if (keys == null || view == null) {
            throw new IllegalArgumentException("keys and view must not be null");
        }
        TdTable meta = metaTable(view.seq());
        TdTable config = configTable(view.chunkSize());
        TdTable chunks = chunksTable(view.chunks());
        String payloadB64 = b64(view.payloadBytes());
        Core core = parseCore(wrap(doc(meta, config, chunks, payloadB64, null)));
        byte[] head = buildHead(core.version(), core.seq(), core.chunkSize(), core.chunks());
        byte[] sig = SaveSigner.sign(keys, head);
        return wrap(doc(meta, config, chunks, payloadB64, b64(sig)));
    }

    // ---------- parsing (core shape, no sig required for the encode-side self-decode) ----------

    /** 内部解析态核心：未含 sig 的解析结果（encode 自取 + verify 共用）。 */
    private record Core(TdTable doc, long version, long seq, int chunkSize,
                        byte[] payloadBytes, List<Chunk> chunks) {
    }

    /**
     * 解析并校验 shape：缺/非表 {@code saveverify}、version 非 1、meta/config/payload/chunks 缺或类型
     * 错误、meta 无 seq、config chunkSize 非法或 hash 不符 → 抛 {@link IllegalArgumentException}。
     * 不要求 sig 在场（encode 侧前置于签名的自取阶段没有 sig）。
     */
    private static Core parseCore(String tdText) {
        if (tdText == null) {
            throw new IllegalArgumentException("td text must not be null");
        }
        TdTable root = Td.parse(tdText);
        TdValue markerValue = root.get(MARKER);
        if (!(markerValue instanceof TdTable doc)) {
            throw new IllegalArgumentException("not a verifiable save archive (missing '" + MARKER + "')");
        }
        long version = doc.get("version") != null ? doc.get("version").asInt() : -1;
        if (version != VERSION) {
            throw new IllegalArgumentException("unsupported verifiable save archive version: " + version);
        }
        TdValue metaValue = doc.get("meta");
        if (!(metaValue instanceof TdTable meta)) {
            throw new IllegalArgumentException("saveverify archive missing or non-table 'meta'");
        }
        Map<String, TdValue> metaMap = readMeta(meta);
        String algo = metaMap.get("algo") != null ? metaMap.get("algo").asString() : "";
        String kind = metaMap.get("kind") != null ? metaMap.get("kind").asString() : "";
        if (!META_ALGO.equals(algo) || !META_KIND.equals(kind)) {
            throw new IllegalArgumentException("saveverify archive meta algo/kind mismatch");
        }
        TdValue seqValue = metaMap.get("seq");
        long seq = seqValue != null ? seqValue.asInt() : -1;

        TdValue configValue = doc.get("config");
        if (!(configValue instanceof TdTable config)) {
            throw new IllegalArgumentException("saveverify archive missing or non-table 'config'");
        }
        long chunkSizeL = config.get("chunkSize") != null ? config.get("chunkSize").asInt() : -1;
        if (chunkSizeL <= 0) {
            throw new IllegalArgumentException("saveverify archive invalid chunkSize: " + chunkSizeL);
        }
        int chunkSize = (int) chunkSizeL;
        String hash = config.get("hash") != null ? config.get("hash").asString() : "";
        if (!CONFIG_HASH.equals(hash)) {
            throw new IllegalArgumentException("saveverify archive unknown config hash: " + hash);
        }

        TdValue payloadValue = doc.get("payload");
        if (!(payloadValue instanceof TdValue.Scalar ps) || ps.kind() != TdValue.Kind.STRING) {
            throw new IllegalArgumentException("saveverify archive missing or non-string 'payload'");
        }
        byte[] payloadBytes = unb64(ps.str());

        TdValue chunksValue = doc.get("chunks");
        if (!(chunksValue instanceof TdTable chunksT)) {
            throw new IllegalArgumentException("saveverify archive missing or non-table 'chunks'");
        }
        List<Chunk> chunks = readChunks(chunksT);

        return new Core(doc, version, seq, chunkSize, payloadBytes, chunks);
    }

    /** 读取 meta 行表 {@code [ [ k = ..., v = ... ], ... ]} 为键值映射。 */
    private static Map<String, TdValue> readMeta(TdTable list) {
        Map<String, TdValue> out = new LinkedHashMap<>();
        for (TdValue item : list.elements()) {
            if (!(item instanceof TdTable t)) {
                continue;
            }
            TdValue k = t.get("k");
            if (k == null || k.asString().isBlank()) {
                continue;
            }
            out.put(k.asString(), t.get("v"));
        }
        return out;
    }

    /** 读取 chunks 行表 {@code [ [ index/offset/length/fast/strong ], ... ]} 为块元数据（保持文档序）。 */
    private static List<Chunk> readChunks(TdTable list) {
        List<Chunk> out = new ArrayList<>();
        for (TdValue item : list.elements()) {
            if (!(item instanceof TdTable t)) {
                throw new IllegalArgumentException("saveverify archive chunk is not a table");
            }
            long index = t.get("index") != null ? t.get("index").asInt() : -1;
            long offset = t.get("offset") != null ? t.get("offset").asInt() : -1;
            long length = t.get("length") != null ? t.get("length").asInt() : -1;
            String fast = t.get("fast") != null ? t.get("fast").asString() : "";
            String strong = t.get("strong") != null ? t.get("strong").asString() : "";
            out.add(new Chunk((int) index, offset, length, fast, strong));
        }
        return out;
    }

    /**
     * 从解析视图重建签名头（包内桥接，供 {@link SaveVerifyLedger} 复用同一解析态单源构建）。
     */
    static byte[] hashHeadFromParsed(long version, long seq, int chunkSize, List<Chunk> chunks) {
        return buildHead(version, seq, chunkSize, chunks);
    }

    // ---------- canonical head (single source of truth: the parsed state) ----------

    /**
     * 从解析态构建确定拼接的签名头（UTF-8 字节）。作者序固定：meta 按 algo/kind/seq，config 按
     * chunkSize/hash；chunks 按 `index:offset:length:fast:strong` 以逗号连接、文档序（正常恒为升序）。
     * encode 与 verify 两侧都从解析片构建，故字节恒等。
     */
    private static byte[] buildHead(long version, long seq, int chunkSize, List<Chunk> chunks) {
        StringBuilder sb = new StringBuilder();
        sb.append(MARKER).append('|').append(version)
                .append("|seq|").append(seq)
                .append("|meta|").append(Td.write(metaTable(seq)))
                .append("|config|").append(Td.write(configTable(chunkSize)))
                .append("|chunks|");
        for (int i = 0; i < chunks.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            Chunk c = chunks.get(i);
            sb.append(c.index()).append(':').append(c.offset()).append(':').append(c.length())
                    .append(':').append(c.digestFast()).append(':').append(c.digestStrong());
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    // ---------- authoring tables (fixed authoring order -> reproducible text) ----------

    /** meta 表，作者序固定 algo→kind→seq。 */
    private static TdTable metaTable(long seq) {
        TdTable.Builder b = TdTable.builder();
        b.element(row("algo", TdValue.str(META_ALGO)));
        b.element(row("kind", TdValue.str(META_KIND)));
        b.element(row("seq", TdValue.of(seq)));
        return b.build();
    }

    /** config 表，作者序固定 chunkSize→hash。 */
    private static TdTable configTable(int chunkSize) {
        return TdTable.builder()
                .put("chunkSize", TdValue.of(chunkSize))
                .put("hash", TdValue.str(CONFIG_HASH))
                .build();
    }

    /** chunks 行表：按住 lambda 提供序（升序）逐行追加。 */
    private static TdTable chunksTable(List<Chunk> chunks) {
        TdTable.Builder b = TdTable.builder();
        for (Chunk c : chunks) {
            b.element(TdTable.builder()
                    .put("index", TdValue.of(c.index()))
                    .put("offset", TdValue.of(c.offset()))
                    .put("length", TdValue.of(c.length()))
                    .put("fast", TdValue.str(c.digestFast()))
                    .put("strong", TdValue.str(c.digestStrong()))
                    .build());
        }
        return b.build();
    }

    /** 单行 { k = ..., v = ... }。 */
    private static TdTable row(String key, TdValue v) {
        return TdTable.builder().put("k", TdValue.str(key)).put("v", v).build();
    }

    /** 组装档案文档表（sig 可为 null，用于 encode 前置于签名的 shell 阶段）。 */
    private static TdTable doc(TdTable meta, TdTable config, TdTable chunks,
                               String payloadB64, String sigB64) {
        TdTable.Builder b = TdTable.builder();
        b.put("version", TdValue.of(VERSION));
        b.put("meta", meta);
        b.put("config", config);
        b.put("payload", TdValue.str(payloadB64));
        b.put("chunks", chunks);
        if (sigB64 != null) {
            b.put("sig", TdValue.str(sigB64));
        }
        return b.build();
    }

    /** 具名顶层表包装 + type 头（与 WorldPack/zdt 同构）。 */
    private static String wrap(TdTable doc) {
        return "type tie<data>\n" + Td.write(TdTable.builder().put(MARKER, doc).build());
    }

    private static String b64(byte[] data) {
        return Base64.getEncoder().encodeToString(data);
    }

    private static byte[] unb64(String s) {
        try {
            return Base64.getDecoder().decode(s);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("saveverify archive base64 decode failed", e);
        }
    }

    /**
     * 解析视图（record）。{@code Chunk} 直接复用 {@code engine.saveverify.chunk.Chunk}，故
     * {@code ChunkPlan}/{@code ChunkVerifier} 可直接吃。{@code sigHexOrB64} 为文档中读到的原样
     * base64 签名串，{@code sigBytes} 为其解码字节。
     *
     * @param version     档案版本。
     * @param seq         档案序号。
     * @param chunkSize   固定切块大小。
     * @param payloadBytes 受保护内容字节（已解码）。
     * @param chunks      块校验元数据（文档序，正常为 index 升序）。
     * @param sigHexOrB64 原样 base64 签名串。
     * @param sigBytes    解码后的 64B 签名。
     */
    public record ParsedArchive(long version, long seq, int chunkSize, byte[] payloadBytes,
                                List<Chunk> chunks, String sigHexOrB64, byte[] sigBytes) {
    }
}