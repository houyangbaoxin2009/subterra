package io.toterra.subterra.engine.render.lod;

import io.toterra.subterra.engine.config.TdTable;
import io.toterra.subterra.engine.config.TdValue;
import io.toterra.subterra.engine.network.integrity.Tsha1f;
import io.toterra.subterra.engine.zd.ZdDocWriter;
import io.toterra.subterra.engine.zd.ZdHeader;
import io.toterra.subterra.engine.zd.ZdVolume;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Base64;

/**
 * The on-disk LOD mesh cache (p.2.28.5 + follow-up, pure JDK): deterministic, tamper-evident persistence
 * of the pinned {@link LodSection} payload keyed by {@link LodCacheKey}, now written as a <b>standard zd v2
 * document</b> (the p.2.3 {@code engine.zd} carrier) — a 10-byte zd v2 header + a fixed-order six-field
 * wire2 TdTable tree ({@link ZdDocWriter} / {@link ZdVolume}) — with per-entry tsha1f fast/strong
 * validation and four deterministic rejection classes (tamper / truncation / version mismatch /
 * checksum mismatch). A cache hit skips section regeneration for the p.2.28.6 runtime.
 *
 * <p><b>File layout</b> (a cache file is one {@link LodCacheKey} = one on-disk file): the whole file is a
 * genuine zd v2 document, byte-for-byte what {@code ZdDocWriter.writeTree(0, doc)} produces
 * (verified in reverse by {@code ZdVolume.readTree}), so it satisfies the framework's zd first-class
 * principle. The doc carries these pinned named fields in fixed order ({@link TdTable} keeps insertion
 * order; no timestamps, no randomness):
 * <pre>{@code
 *  [0..9)     zd v2 header (ZdHeader: "TIEDBZD" + 0x00 0x02 + flags 0)   // 10 bytes
 *  [10..end)  wire2 records of a single TdTable = the cache document with fields, in fixed order:
 *      formatVersion   : int   = FORMAT_VERSION (2)
 *      verifyScheme    : int   = VERIFY_SCHEME (1)
 *      lodLevel        : str   = section.level().form()
 *      originBlockX    : int   = section.originBlockX()
 *      originBlockZ    : int   = section.originBlockZ()
 *      lodSectionBytes : str   = Base64( LodSection.toBytes() )      // the pinned payload
 *      strongDigest    : str   = tsha1f(lodSectionBytes, n=48, base=48)
 *      fastDigest      : str   = tsha1f(lodSectionBytes, n=8,  base=48)
 * }</pre>
 * Every field is fixed and pinned by this javadoc; none may be permuted or dropped. The two digests are
 * base-48 ASCII strings as produced by {@code Tsha1f} and are recomputed over the decoded
 * {@code lodSectionBytes} payload and verified before any decode (the fast/strong dual-tier semantics of
 * p.2.28.5, honoring {@code engine.network.integrity.Tsha1f} and the p.2.10 {@code ChunkVerifier}).
 * The file name is fixed by {@link LodCacheKey#fileName()} (level + chunk coordinates).
 *
 * <p><b>zd v2 first-class carrier (p.2.28 follow-up).</b> Previously this cache used a bespoke binary format
 * (magic {@code "LODC"} + format-version byte + payload length + pinned bytes + trailing digests) that only
 * aligned with {@code engine.zd} semantically. As of this follow-up the file <em>is</em> a standard zd v2
 * document — a real instance of the framework's generic zd v2 carrier, read/written by the same
 * {@link ZdDocWriter}/{@link ZdVolume} used across the p.2.3 track. This is also the framework's Java-side
 * zd file (the "zdjava" interop point): together with tiec {@code --compress-data} (td→zd on the tie-main
 * toolchain) it makes byte-level data-carrier interchange feasible, while the in-process DLL FFM boundary
 * remains scalar/string. The public API ({@code save}/{@code load}/{@code contains}/{@code hit}/
 * {@code CacheStatus}/{@code CacheResult}) and semantics are unchanged.
 *
 * <p><b>Deterministic rejection.</b> {@code load}/{@code hit} never throw on a damaged entry; they return a
 * fixed {@link CacheStatus}: missing file &rarr; {@link CacheStatus#MISS}; bad zd magic / header or
 * payload truncation / malformed zd document / base-64 or {@link LodSection} decode failure / fast-or-strong
 * checksum mismatch (single-byte tamper, truncation, reordering) &rarr; {@link CacheStatus#CORRUPT}; a
 * legacy {@code "LODC"} file, or a valid zd header whose version bytes are not {@code 0x00 0x02}, or a
 * parsed document whose {@code formatVersion} differs from {@link #FORMAT_VERSION}, &rarr;
 * {@link CacheStatus#VERSION_MISMATCH}. Bad data is never silently served.
 *
 * <p><b>Regenerable (p.2.9/p.2.18).</b> A cache entry is a regenerable artifact: it can be included in or
 * excluded from a p.2.9 world pack / archived by p.2.18 export, and recreated on demand. This sub-item only
 * declares the contract ({@link #REGENERABLE}, {@link #isRegenerable()}); it does not implement export.
 *
 * <p>磁盘 LOD 网格缓存（p.2.28.5 + 跟进，纯 JDK）：对以 {@link LodCacheKey} 为键、钉死 {@link LodSection}
 * 载荷的确定性、防篡改持久化，如今以<b>标准 zd v2 文档</b>（p.2.3 {@code engine.zd} 载体）写入——10 字节
 * zd v2 头 + 一个固定序六字段 wire2 TdTable 树（{@link ZdDocWriter} / {@link ZdVolume}）——配合逐条目
 * tsha1f 快/强档校验与四类确定性拒绝（篡改 / 截断 / 版本不符 / 校验不符）。缓存命中跳过区块重生成，供
 * p.2.28.6 运行时使用。
 *
 * <p><b>文件布局</b>（一个缓存文件 = 一个 {@link LodCacheKey} = 一个磁盘文件）：整个文件就是一份货真价实
 * 的 zd v2 文档，逐字节等于 {@code ZdDocWriter.writeTree(0, doc)} 的输出（由 {@code ZdVolume.readTree}
 * 逆向校验），故满足框架 zd 一等原则。文档以固定序承载如下命名字段（{@link TdTable} 保持插入序；无时间戳、
 * 无随机）：
 * <pre>{@code
 *  [0..9)     zd v2 头（ZdHeader："TIEDBZD" + 0x00 0x02 + flags 0）            // 10 字节
 *  [10..end)  单个 TdTable 的 wire2 记录 = 缓存文档，字段固定序：
 *      formatVersion   : int   = FORMAT_VERSION（2）
 *      verifyScheme    : int   = VERIFY_SCHEME（1）
 *      lodLevel        : str   = section.level().form()
 *      originBlockX    : int   = section.originBlockX()
 *      originBlockZ    : int   = section.originBlockZ()
 *      lodSectionBytes : str   = Base64( LodSection.toBytes() )   // 钉死载荷
 *      strongDigest    : str   = tsha1f(lodSectionBytes, n=48, base=48)
 *      fastDigest      : str   = tsha1f(lodSectionBytes, n=8,  base=48)
 * }</pre>
 * 每字段固定且被本 javadoc 钉死；不得重排或舍弃。两档摘要是 {@code Tsha1f} 产出的 ASCII base-48 字符串，
 * 在任何解码前都对解码后的 {@code lodSectionBytes} 载荷重算并校验（p.2.28.5 快/强双档语义，承接
 * {@code engine.network.integrity.Tsha1f} 与 p.2.10 {@code ChunkVerifier}）。文件名由
 * {@link LodCacheKey#fileName()} 固定（level + 区块坐标）。
 *
 * <p><b>zd v2 一等载体（p.2.28 跟进）。</b>此前本缓存用自定二进制格式（魔数 {@code "LODC"} + 格式版本字节 +
 * 载荷长 + 钉死字节 + 尾随摘要），仅与 {@code engine.zd} 语义对齐。本次跟进后文件<b>就是</b>一份标准 zd v2
 * 文档——框架通用 zd v2 载体的真实实例，由 p.2.3 轨通用的同一套 {@link ZdDocWriter}/{@link ZdVolume} 读写。
 * 此亦即框架侧的 Java zd 文件（即「zdjava」互操作点）：连同 tiec {@code --compress-data}（tie-main 工具链
 * 侧 td→zd）使字节级数据载体交换可行，而 DLL 进程内 FFM 边界仍为标量/string。公开 API
 * （{@code save}/{@code load}/{@code contains}/{@code hit}/{@code CacheStatus}/{@code CacheResult}）
 * 与语义不变。
 *
 * <p><b>确定性拒绝。</b>{@code load}/{@code hit} 在受损条目上决不抛异常，而是返回固定 {@link CacheStatus}：
 * 缺文件 &rarr; {@link CacheStatus#MISS}；坏 zd 魔数 / 头或载荷截断 / 畸形 zd 文档 / base-64 或
 * {@link LodSection} 解码失败 / 快或强档校验不符（单字节篡改、截断、重排）&rarr; {@link CacheStatus#CORRUPT}；
 * 旧 {@code "LODC"} 文件、或 zd 头版本字节非 {@code 0x00 0x02}、或解析文档的 {@code formatVersion} 与
 * {@link #FORMAT_VERSION} 不符 &rarr; {@link CacheStatus#VERSION_MISMATCH}。坏数据永不被静默使用。
 *
 * <p><b>可再生（p.2.9/p.2.18）。</b>缓存条目为可再生物：可包含于或排除于 p.2.9 世界包 / 由 p.2.18 导出归档，
 * 并按需重建。本子项仅声明契约（{@link #REGENERABLE}、{@link #isRegenerable()}），不实现导出。
 *
 * @see LodCacheKey
 * @see CacheStatus
 * @see CacheResult
 */
public final class LodCache {

    /** The zd v2 header magic {@code "TIEDBZD"} (7 ASCII bytes), as pinned by {@link ZdHeader}.
     *  / zd v2 头魔数 {@code "TIEDBZD"}（7 ASCII 字节），同 {@link ZdHeader} 钉点。 */
    public static final String MAGIC = ZdHeader.MAGIC;
    private static final byte[] MAGIC_BYTES = MAGIC.getBytes(StandardCharsets.US_ASCII);

    /** Pinned on-disk logical format version; bump on incompatible layout change. Carried in the zd
     *  document and compared on read. / 钉死磁盘逻辑格式版本；不兼容布局变更时递增。随 zd 文档承载并在读取时比对。 */
    public static final int FORMAT_VERSION = 2;

    /** Pinned verification scheme id; part of the doc identity. / 钉死校验方案标识；属文档身份的一部分。 */
    public static final int VERIFY_SCHEME = 1;

    /** Fixed zd-v2 header length before the wire2 record body ({@value} bytes). / wire2 记录体前的固定 zd v2 头长度（{@value} 字节）。 */
    public static final int HEADER_LEN = 10;

    /** Fast-tier tsha1f bit length (n=8). / 快档 tsha1f 位长（n=8）。 */
    public static final int FAST_N = 8;

    /** Strong-tier tsha1f bit length (n=48). / 强档 tsha1f 位长（n=48）。 */
    public static final int STRONG_N = 48;

    /** Regenerable marker (p.2.9/p.2.18): a cache entry is a regenerable artifact — includable in or
     * excludable from a p.2.9 world pack, archivable by p.2.18 export (declared here, export itself is a
     * later sub-item). / 可再生标记（p.2.9/p.2.18）：缓存条目是可再生物——可被 p.2.9 世界包包含/排除、可被
     * p.2.18 导出归档（此处仅声明，导出本身为后续子项）。 */
    public static final boolean REGENERABLE = true;

    // ---- pinned zd-document field names (fixed order, see class doc) / 钉死的 zd 文档字段名（固定序） ----
    private static final String FIELD_FORMAT_VERSION = "formatVersion";
    private static final String FIELD_VERIFY_SCHEME = "verifyScheme";
    private static final String FIELD_LOD_LEVEL = "lodLevel";
    private static final String FIELD_ORIGIN_X = "originBlockX";
    private static final String FIELD_ORIGIN_Z = "originBlockZ";
    private static final String FIELD_SECTION_B64 = "lodSectionBytes";
    private static final String FIELD_STRONG = "strongDigest";
    private static final String FIELD_FAST = "fastDigest";

    /** Legacy {@code "LODC"} magic of the pre-follow-up bespoke format, detected for deterministic
     *  {@link CacheStatus#VERSION_MISMATCH}. / 跟进前自定格式的旧魔数 {@code "LODC"}，用于确定性拒绝为
     *  {@link CacheStatus#VERSION_MISMATCH}。 */
    private static final byte[] LEGACY_MAGIC_BYTES = "LODC".getBytes(StandardCharsets.US_ASCII);

    /** tsha1f output base, fixed at 48 (matches p.2.10 {@code ChunkVerifier}). / tsha1f 输出进制，固定 48（同 p.2.10 {@code ChunkVerifier}）。 */
    private static final int BASE = 48;

    private static final Base64.Decoder B64_DEC = Base64.getDecoder();
    private static final Base64.Encoder B64_ENC = Base64.getEncoder();

    private final Path rootDir;

    /**
     * Creates an on-disk cache rooted at {@code rootDir}. The directory is <em>not</em> created eagerly; it
     * is created on the first {@link #save}. A null path is rejected.
     * / 以 {@code rootDir} 为根的磁盘缓存。目录不预先创建，而是在首次 {@link #save} 时创建。null 路径被拒绝。
     */
    public LodCache(Path rootDir) {
        if (rootDir == null) {
            throw new IllegalArgumentException("rootDir must not be null");
        }
        this.rootDir = rootDir.toAbsolutePath().normalize();
    }

    /**
     * Writes the section for {@code key} and re-reads to verify (save = write + validate re-read). Returns
     * {@link CacheStatus#OK} with the round-tripped section on success; {@link CacheStatus#IO_ERROR} if the
     * directory/file cannot be created or written; {@link CacheStatus#CORRUPT} if the freshly written file
     * does not re-validate (never silently serving it). Deterministic: identical input writes identical bytes.
     * / 写入 {@code key} 的区块并重读校验（存储 = 写入 + 校验重读）。成功返回 {@link CacheStatus#OK}（携往返后的
     * 区块）；目录/文件无法创建或写入则 {@link CacheStatus#IO_ERROR}；新写文件未通过校验则
     * {@link CacheStatus#CORRUPT}（绝不静默使用）。确定性：相同输入写相同字节。
     */
    public CacheResult save(LodCacheKey key, LodSection section) {
        if (key == null || section == null) {
            throw new IllegalArgumentException("key and section must not be null");
        }
        byte[] payload = section.toBytes();
        String strong = Tsha1f.tsha1f(payload, STRONG_N, BASE);
        String fast = Tsha1f.tsha1f(payload, FAST_N, BASE);
        if (strong.length() != STRONG_N || fast.length() != FAST_N) {
            return CacheResult.of(CacheStatus.CORRUPT);
        }
        byte[] file = encodeFile(docFor(section, strong, fast));
        Path target = fileFor(key);
        try {
            Files.createDirectories(rootDir);
            Files.write(target, file);
        } catch (IOException e) {
            return CacheResult.of(CacheStatus.IO_ERROR);
        }
        // validate re-read (save = write + validate)
        byte[] reread;
        try {
            reread = Files.readAllBytes(target);
        } catch (IOException e) {
            return CacheResult.of(CacheStatus.IO_ERROR);
        }
        if (!Arrays.equals(reread, file)) {
            return CacheResult.of(CacheStatus.CORRUPT);
        }
        CacheStatus st = verify(file);
        return st == CacheStatus.OK ? new CacheResult(CacheStatus.OK, toSection(payload))
                : CacheResult.of(st);
    }

    /**
     * Loads and validates the section for {@code key}. Never throws on a damaged entry: missing file &rarr;
     * {@link CacheStatus#MISS}; validation failure &rarr; the fixed rejection status (see class doc);
     * I/O failure &rarr; {@link CacheStatus#IO_ERROR}; success &rarr; {@link CacheStatus#OK} with the
     * {@link LodSection}. / 加载并校验 {@code key} 的区块。在受损条目上决不抛异常：缺文件 &rarr;
     * {@link CacheStatus#MISS}；校验失败 &rarr; 固定拒绝状态（见类文档）；I/O 失败 &rarr;
     * {@link CacheStatus#IO_ERROR}；成功 &rarr; {@link CacheStatus#OK}（携 {@link LodSection}）。
     */
    public CacheResult load(LodCacheKey key) {
        if (key == null) {
            throw new IllegalArgumentException("key must not be null");
        }
        Path target = fileFor(key);
        if (!Files.isRegularFile(target)) {
            return CacheResult.of(CacheStatus.MISS);
        }
        byte[] file;
        try {
            file = Files.readAllBytes(target);
        } catch (IOException e) {
            return CacheResult.of(CacheStatus.IO_ERROR);
        }
        CacheStatus st = verify(file);
        if (st != CacheStatus.OK) {
            return CacheResult.of(st);
        }
        return new CacheResult(CacheStatus.OK, decodeSection(file));
    }

    /**
     * Cheap presence check: true iff a cache file exists as a regular file for {@code key} (no validation).
     * / 廉价存在性检查：当且仅当 {@code key} 的缓存文件作为常规文件存在时为 true（不含校验）。
     */
    public boolean contains(LodCacheKey key) {
        return key != null && Files.isRegularFile(fileFor(key));
    }

    /**
     * Cache-hit probe: true iff a <em>valid, loadable</em> entry exists for {@code key} (i.e. {@link #load}
     * would return {@link CacheStatus#OK}). The runtime uses this to skip regeneration when the cache hits.
     * Deterministic: a missing or damaged entry is a miss.
     * / 缓存命中探测：当且仅当 {@code key} 存在<em>有效、可加载</em>的条目（即 {@link #load} 返回
     * {@link CacheStatus#OK}）时为 true。运行时借此在缓存命中时跳过重生成。确定性：缺失或受损条目即未命中。
     */
    public boolean hit(LodCacheKey key) {
        return load(key).status() == CacheStatus.OK;
    }

    /** The resolved cache root directory. / 解析后的缓存根目录。 */
    public Path rootDir() {
        return rootDir;
    }

    /**
     * True because every cache entry is a regenerable artifact (p.2.9/p.2.18).
     * / 恒为 true，因为每个缓存条目都是可再生物（p.2.9/p.2.18）。
     */
    public boolean isRegenerable() {
        return REGENERABLE;
    }

    /** Files of a key: {@code rootDir}/{@link LodCacheKey#fileName()}. / 键的文件路径：{@code rootDir}/{@link LodCacheKey#fileName()}。 */
    private Path fileFor(LodCacheKey key) {
        return rootDir.resolve(key.fileName());
    }

    /** Wraps the pinned {@link LodSection#fromBytes} decode. / 封装钉死的 {@link LodSection#fromBytes} 解码。 */
    private static LodSection toSection(byte[] payload) {
        return LodSection.fromBytes(payload);
    }

    /** Builds the pinned TdTable cache document in fixed field order. / 按固定字段序构建钉死的 TdTable 缓存文档。 */
    private static TdTable docFor(LodSection section, String strong, String fast) {
        return TdTable.builder()
                .put(FIELD_FORMAT_VERSION, TdValue.of((long) FORMAT_VERSION))
                .put(FIELD_VERIFY_SCHEME, TdValue.of((long) VERIFY_SCHEME))
                .put(FIELD_LOD_LEVEL, TdValue.str(section.level().form()))
                .put(FIELD_ORIGIN_X, TdValue.of((long) section.originBlockX()))
                .put(FIELD_ORIGIN_Z, TdValue.of((long) section.originBlockZ()))
                .put(FIELD_SECTION_B64, TdValue.str(B64_ENC.encodeToString(section.toBytes())))
                .put(FIELD_STRONG, TdValue.str(strong))
                .put(FIELD_FAST, TdValue.str(fast))
                .build();
    }

    /** Encodes the cache file as a standard zd v2 document (10-byte header + wire2 records), nothing else.
     *  / 将缓存文件编码为标准 zd v2 文档（10 字节头 + wire2 记录），无其它附加字节。 */
    private static byte[] encodeFile(TdTable doc) {
        return ZdDocWriter.writeTree(0, doc);
    }

    /**
     * Deterministic single-scan validation of a raw cache file. Returns a fixed {@link CacheStatus} and
     * never throws on malformed input.
     * / 对原始缓存文件的一次性确定性校验。返回固定 {@link CacheStatus}，对畸形输入决不抛异常。
     */
    CacheStatus verify(byte[] file) {
        if (file == null || file.length < HEADER_LEN) {
            return CacheStatus.CORRUPT; // protocol-header truncation
        }
        // legacy "LODC" file (pre-follow-up format) -> deterministic VERSION_MISMATCH
        if (startsWith(file, LEGACY_MAGIC_BYTES)) {
            return CacheStatus.VERSION_MISMATCH;
        }
        if (!startsWith(file, MAGIC_BYTES)) {
            return CacheStatus.CORRUPT; // bad zd magic
        }
        if ((file[7] & 0xFF) != 0x00 || (file[8] & 0xFF) != 0x02) {
            return CacheStatus.VERSION_MISMATCH; // zd v2 header version mismatch
        }
        TdTable doc;
        try {
            doc = ZdVolume.readTree(file);
        } catch (RuntimeException e) {
            return CacheStatus.CORRUPT; // malformed zd document / truncation
        }
        Long formatVersion = tableLong(doc, FIELD_FORMAT_VERSION);
        if (formatVersion == null || formatVersion != (long) FORMAT_VERSION) {
            return CacheStatus.VERSION_MISMATCH; // logical format version differs
        }
        Long verifyScheme = tableLong(doc, FIELD_VERIFY_SCHEME);
        if (verifyScheme == null || verifyScheme != (long) VERIFY_SCHEME) {
            return CacheStatus.CORRUPT; // unparseable scheme marker
        }
        if (tableStr(doc, FIELD_LOD_LEVEL) == null) {
            return CacheStatus.CORRUPT;
        }
        byte[] payload;
        String b64 = tableStr(doc, FIELD_SECTION_B64);
        if (b64 == null) {
            return CacheStatus.CORRUPT;
        }
        try {
            payload = B64_DEC.decode(b64);
        } catch (RuntimeException e) {
            return CacheStatus.CORRUPT; // malformed base-64
        }
        String strongStored = tableStr(doc, FIELD_STRONG);
        String fastStored = tableStr(doc, FIELD_FAST);
        if (strongStored == null || fastStored == null) {
            return CacheStatus.CORRUPT;
        }
        String strongRe = Tsha1f.tsha1f(payload, STRONG_N, BASE);
        if (strongRe.length() != STRONG_N || !strongRe.equals(strongStored)) {
            return CacheStatus.CORRUPT; // strong checksum mismatch (tamper/truncate)
        }
        String fastRe = Tsha1f.tsha1f(payload, FAST_N, BASE);
        if (fastRe.length() != FAST_N || !fastRe.equals(fastStored)) {
            return CacheStatus.CORRUPT; // fast checksum mismatch
        }
        try {
            LodSection.fromBytes(payload); // malformed payload -> reject (not throw out)
        } catch (java.lang.RuntimeException e) {
            return CacheStatus.CORRUPT;
        }
        return CacheStatus.OK;
    }

    /** Re-decodes the (already verified) section from the zd document. / 从（已校验的）zd 文档重新解码区块。 */
    private static LodSection decodeSection(byte[] file) {
        TdTable doc = ZdVolume.readTree(file); // verified OK beforehand: safe
        String b64 = tableStr(doc, FIELD_SECTION_B64);
        return LodSection.fromBytes(B64_DEC.decode(b64));
    }

    private static boolean startsWith(byte[] file, byte[] prefix) {
        if (file.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (file[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    private static Long tableLong(TdTable doc, String key) {
        if (doc == null) {
            return null;
        }
        TdValue v = doc.get(key);
        return v instanceof TdValue.Scalar s
                && (s.kind() == TdValue.Kind.INT || s.kind() == TdValue.Kind.BOOL) ? s.i() : null;
    }

    private static String tableStr(TdTable doc, String key) {
        if (doc == null) {
            return null;
        }
        TdValue v = doc.get(key);
        return v instanceof TdValue.Scalar s && s.kind() == TdValue.Kind.STRING ? s.str() : null;
    }
}