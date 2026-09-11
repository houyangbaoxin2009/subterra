package io.toterra.subterra.engine.render.lod;

import io.toterra.subterra.engine.network.integrity.Tsha1f;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/**
 * The on-disk LOD mesh cache (p.2.28.5, pure JDK): deterministic, tamper-evident persistence
 * of the pinned {@link LodSection} payload keyed by {@link LodCacheKey}, with a fixed file
 * layout, per-entry tsha1f fast/strong validation, and four deterministic rejection classes
 * (tamper / truncation / version mismatch / checksum mismatch). A cache hit skips section
 * regeneration for the p.2.28.6 runtime.
 *
 * <p><b>Fixed file layout</b> (a cache file is one {@link LodCacheKey} = one on-disk file;
 * all byte offsets are 0-based, integers big-endian, digests ASCII base-48 as produced by
 * {@code Tsha1f}):
 * <pre>{@code
 *  [0..4)   MAGIC = "LODC"                     // 4 bytes 0x4C 0x4F 0x44 0x43
 *  [4]      FORMAT_VERSION = 0x01              // 1 byte
 *  [5]      VERIFY_SCHEME   = 0x01             // 1 byte
 *  [6..10)  payloadLen                         // 4 bytes BE = payload length
 *  [10..)   payload                            // payloadLen bytes = LodSection.toBytes()
 *  [..+48)  strong digest                      // 48 ASCII bytes, tsha1f(payload, 48, 48)
 *  [..+8)   fast digest                        // 8 ASCII bytes,  tsha1f(payload, 8, 48)
 * }</pre>
 * Every field is fixed and pinned by this javadoc; none may be permuted or dropped. The
 * same logical section always produces the same bytes (no timestamps, no randomness), so
 * identical input and an identical on-disk file give byte-identical results. The file name
 * is fixed by {@link LodCacheKey#fileName()} (level + chunk coordinates).
 *
 * <p><b>zd-ABI alignment (p.2.3).</b> The payload is the pinned {@link LodSection#toBytes()}
 * "bytes-in &rarr; bytes-out" zd ABI from p.2.28.1, and the on-disk identity keys it with
 * {@link LodCacheKey#encode()} (level ordinal + chunk coords, big-endian ints) so the whole
 * file is a plain, versioned byte carrier whose transport semantics align with the p.2.3
 * {@code engine.zd} generic zd v2 carrier: a fixed header (magic + version) ahead of a
 * versioned payload, verified before any decode. Here the header/payload/checksum are written
 * directly over the pinned bytes rather than as a TdTable tree; the alignment is semantic
 * (fixed header + versioned payload + integrity), not a byte-for-byte reuse of the tree
 * encoder.
 *
 * <p><b>Deterministic rejection.</b> {@code load}/{@code hit} never throw on a damaged entry;
 * they return a fixed {@link CacheStatus}: missing file &rarr; {@link CacheStatus#MISS};
 * bad magic / protocol-header truncation / payload truncation / malformed {@link LodSection}
 * bytes / fast or strong checksum mismatch (single-byte tamper, truncation, reordering)
 * &rarr; {@link CacheStatus#CORRUPT}; a parsed magic with an unexpected pinned format version
 * &rarr; {@link CacheStatus#VERSION_MISMATCH}. Bad data is never silently served.
 *
 * <p><b>Regenerable (p.2.9/p.2.18).</b> A cache entry is a regenerable artifact: it can be
 * included in or excluded from a p.2.9 world pack / archived by p.2.18 export, and recreated
 * on demand. This sub-item only declares the contract ({@link #REGENERABLE}, {@link
 * #isRegenerable()}); it does not implement export.
 *
 * <p>磁盘 LOD 网格缓存（p.2.28.5，纯 JDK）：对以 {@link LodCacheKey} 为键、钉死 {@link LodSection}
 * 载荷的确定性、防篡改持久化，固定文件布局 + 逐条目 tsha1f 快/强档校验 + 四类确定性拒绝（篡改 /
 * 截断 / 版本不符 / 校验不符）。缓存命中跳过区块重生成，供 p.2.28.6 运行时使用。
 *
 * <p><b>固定文件布局</b>（一个缓存文件 = 一个 {@link LodCacheKey} = 一个磁盘文件；所有偏移自 0 起、
 * 整数大端、摘要为 {@code Tsha1f} 产出的 ASCII base-48）：
 * <pre>{@code
 *  [0..4)   MAGIC = "LODC"                     // 4 字节 0x4C 0x4F 0x44 0x43
 *  [4]      FORMAT_VERSION = 0x01              // 1 字节
 *  [5]      VERIFY_SCHEME   = 0x01             // 1 字节
 *  [6..10)  payloadLen                         // 4 字节大端 = 载荷长度
 *  [10..)   payload                            // payloadLen 字节 = LodSection.toBytes()
 *  [..+48)  强档摘要                            // 48 ASCII 字节，tsha1f(payload, 48, 48)
 *  [..+8)   快档摘要                            // 8 ASCII 字节，  tsha1f(payload, 8, 48)
 * }</pre>
 * 每字段固定且被本 javadoc 钉死；不得重排或舍弃。同一逻辑区块恒产生相同字节（无时间戳、无随机），
 * 故相同输入与相同磁盘文件给逐字节一致结果。文件名由 {@link LodCacheKey#fileName()} 固定
 * （level + 区块坐标）。
 *
 * <p><b>zd ABI 对齐（p.2.3）。</b>载荷即 p.2.28.1 钉死的 {@link LodSection#toBytes()}「字节进&rarr;
 * 字节出」zd ABI，并由 {@link LodCacheKey#encode()}（level 序 + 区块坐标，大端整数）在盘上寻址，
 * 使整个文件是一个普通、带版本的字节载体，其传输语义对齐 p.2.3 {@code engine.zd} 通用 zd v2 载体：
 * 固定头（魔数 + 版本）+ 版本化载荷 + 解前校验。此处头/载荷/校验是直接写在钉死字节上而非编码为
 * TdTable 树；对齐是语义性的（固定头 + 版本化载荷 + 完整性），并非逐字节复用树编码器。
 *
 * <p><b>确定性拒绝。</b>{@code load}/{@code hit} 在受损条目上决不抛异常，而是返回固定
 * {@link CacheStatus}：缺文件 &rarr; {@link CacheStatus#MISS}；坏魔数 / 协议头截断 / 载荷截断 /
 * 畸形 {@link LodSection} 字节 / 快或强档校验不符（单字节篡改、截断、重排）&rarr;
 * {@link CacheStatus#CORRUPT}；魔数解析通过但钉死格式版本不符 &rarr; {@link CacheStatus#VERSION_MISMATCH}。
 * 坏数据永不被静默使用。
 *
 * <p><b>可再生（p.2.9/p.2.18）。</b>缓存条目为可再生物：可包含于或排除于 p.2.9 世界包 / 由 p.2.18
 * 导出归档，并按需重建。本子项仅声明契约（{@link #REGENERABLE}、{@link #isRegenerable()}），
 * 不实现导出。
 *
 * @see LodCacheKey
 * @see CacheStatus
 * @see CacheResult
 */
public final class LodCache {

    /** On-disk magic {@code "LODC"} (4 ASCII bytes). / 磁盘魔数 {@code "LODC"}（4 ASCII 字节）。 */
    public static final String MAGIC = "LODC";
    private static final byte[] MAGIC_BYTES = MAGIC.getBytes(StandardCharsets.US_ASCII);

    /** Pinned on-disk format version; bump on incompatible layout change. / 钉死磁盘格式版本；不兼容布局变更时递增。 */
    public static final int FORMAT_VERSION = 1;

    /** Pinned verification scheme id; part of the format identity. / 钉死校验方案标识；属格式身份的一部分。 */
    public static final int VERIFY_SCHEME = 1;

    /** Fixed header length before {@code payloadLen}/payload ({@value} bytes). / payloadLen/载荷前的固定头长度（{@value} 字节）。 */
    public static final int HEADER_LEN = 4 + 1 + 1 + 4;

    /** Fast-tier tsha1f bit length (n=8). / 快档 tsha1f 位长（n=8）。 */
    public static final int FAST_N = 8;

    /** Strong-tier tsha1f bit length (n=48). / 强档 tsha1f 位长（n=48）。 */
    public static final int STRONG_N = 48;

    /** Regenerable marker (p.2.9/p.2.18): a cache entry is a regenerable artifact — includable
     * in or excludable from a p.2.9 world pack, archivable by p.2.18 export (declared here,
     * export itself is a later sub-item). / 可再生标记（p.2.9/p.2.18）：缓存条目是可再生物——可被
     * p.2.9 世界包包含/排除、可被 p.2.18 导出归档（此处仅声明，导出本身为后续子项）。 */
    public static final boolean REGENERABLE = true;

    /** tsha1f output base, fixed at 48 (matches p.2.10 {@code ChunkVerifier}). / tsha1f 输出进制，固定 48（同 p.2.10 {@code ChunkVerifier}）。 */
    private static final int BASE = 48;

    private final Path rootDir;

    /**
     * Creates an on-disk cache rooted at {@code rootDir}. The directory is <em>not</em>
     * created eagerly; it is created on the first {@link #save}. A null path is rejected.
     * / 以 {@code rootDir} 为根的磁盘缓存。目录不预先创建，而是在首次 {@link #save} 时创建。
     * null 路径被拒绝。
     */
    public LodCache(Path rootDir) {
        if (rootDir == null) {
            throw new IllegalArgumentException("rootDir must not be null");
        }
        this.rootDir = rootDir.toAbsolutePath().normalize();
    }

    /**
     * Writes the section for {@code key} and re-reads to verify (save = write + validate
     * re-read). Returns {@link CacheStatus#OK} with the round-tripped section on success;
     * {@link CacheStatus#IO_ERROR} if the directory/file cannot be created or written;
     * {@link CacheStatus#CORRUPT} if the freshly written file does not re-validate (never
     * silently serving it). Deterministic: identical input writes identical bytes.
     * / 写入 {@code key} 的区块并重读校验（存储 = 写入 + 校验重读）。成功返回
     * {@link CacheStatus#OK}（携往返后的区块）；目录/文件无法创建或写入则
     * {@link CacheStatus#IO_ERROR}；新写文件未通过校验则 {@link CacheStatus#CORRUPT}
     * （绝不静默使用）。确定性：相同输入写相同字节。
     */
    public CacheResult save(LodCacheKey key, LodSection section) {
        if (key == null || section == null) {
            throw new IllegalArgumentException("key and section must not be null");
        }
        byte[] payload = section.toBytes();
        byte[] strong = Tsha1f.tsha1f(payload, STRONG_N, BASE).getBytes(StandardCharsets.US_ASCII);
        byte[] fast = Tsha1f.tsha1f(payload, FAST_N, BASE).getBytes(StandardCharsets.US_ASCII);
        if (strong.length != STRONG_N || fast.length != FAST_N) {
            return CacheResult.of(CacheStatus.CORRUPT);
        }
        byte[] file = encodeFile(payload, strong, fast);
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
        return st == CacheStatus.OK ? new CacheResult(CacheStatus.OK, subsection(payload))
                : CacheResult.of(st);
    }

    /**
     * Loads and validates the section for {@code key}. Never throws on a damaged entry:
     * missing file &rarr; {@link CacheStatus#MISS}; validation failure &rarr; the fixed
     * rejection status (see class doc); I/O failure &rarr; {@link CacheStatus#IO_ERROR};
     * success &rarr; {@link CacheStatus#OK} with the {@link LodSection}.
     * / 加载并校验 {@code key} 的区块。在受损条目上决不抛异常：缺文件 &rarr;
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
        return new CacheResult(CacheStatus.OK, subsection(extractPayload(file)));
    }

    /**
     * Cheap presence check: true iff a cache file exists as a regular file for {@code key}
     * (no validation). / 廉价存在性检查：当且仅当 {@code key} 的缓存文件作为常规文件存在时为 true
     * （不含校验）。
     */
    public boolean contains(LodCacheKey key) {
        return key != null && Files.isRegularFile(fileFor(key));
    }

    /**
     * Cache-hit probe: true iff a <em>valid, loadable</em> entry exists for {@code key}
     * (i.e. {@link #load} would return {@link CacheStatus#OK}). The runtime uses this to skip
     * regeneration when the cache hits. Deterministic: a missing or damaged entry is a miss.
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
     * True because every cache entry is a regenerable artifact (p.2.9/p.2.18). / 恒为 true，
     * 因为每个缓存条目都是可再生物（p.2.9/p.2.18）。
     */
    public boolean isRegenerable() {
        return REGENERABLE;
    }

    /** Files of a key: {@code rootDir}/{@link LodCacheKey#fileName()}. / 键的文件路径：{@code rootDir}/{@link LodCacheKey#fileName()}。 */
    private Path fileFor(LodCacheKey key) {
        return rootDir.resolve(key.fileName());
    }

    /**
     * Wraps the raw pinned {@link LodSection#fromBytes} decode. Malformed payload bytes are
     * rejected deterministically ({@link CacheStatus#CORRUPT}) rather than surfaced as a
     * throw. / 封装钉死的 {@link LodSection#fromBytes} 解码。畸形载荷字节被确定性拒绝
     * （{@link CacheStatus#CORRUPT}）而非以抛异常形式冒泡。
     */
    private static LodSection subsection(byte[] payload) {
        return LodSection.fromBytes(payload);
    }

    /** Reads the payload region of a verified file; caller must have confirmed length via
     * {@link #verify}. / 读取已校验文件的载荷区；调用方须先经 {@link #verify} 确认长度。 */
    private static byte[] extractPayload(byte[] file) {
        int payloadLen = readInt(file, 6);
        return Arrays.copyOfRange(file, HEADER_LEN, HEADER_LEN + payloadLen);
    }

    /**
     * Encodes a cache file with the pinned layout. / 按钉死布局编码缓存文件。
     */
    private static byte[] encodeFile(byte[] payload, byte[] strong, byte[] fast) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(HEADER_LEN + payload.length + STRONG_N + FAST_N);
        out.writeBytes(MAGIC_BYTES);
        out.write(FORMAT_VERSION);
        out.write(VERIFY_SCHEME);
        writeBeInt(out, payload.length);
        out.writeBytes(payload);
        out.writeBytes(strong);
        out.writeBytes(fast);
        return out.toByteArray();
    }

    /**
     * Deterministic single-scan validation of a raw cache file. Returns a fixed
     * {@link CacheStatus} and never throws on malformed input.
     * / 对原始缓存文件的一次性确定性校验。返回固定 {@link CacheStatus}，对畸形输入决不抛异常。
     */
    CacheStatus verify(byte[] file) {
        if (file == null || file.length < HEADER_LEN) {
            return CacheStatus.CORRUPT; // protocol-header truncation
        }
        for (int i = 0; i < MAGIC_BYTES.length; i++) {
            if (file[i] != MAGIC_BYTES[i]) {
                return CacheStatus.CORRUPT; // bad magic
            }
        }
        if ((file[4] & 0xFF) != FORMAT_VERSION) {
            return CacheStatus.VERSION_MISMATCH;
        }
        if ((file[5] & 0xFF) != VERIFY_SCHEME) {
            return CacheStatus.CORRUPT; // unparseable scheme marker
        }
        int payloadLen = readInt(file, 6);
        if (payloadLen < 0) {
            return CacheStatus.CORRUPT;
        }
        long expected = (long) HEADER_LEN + payloadLen + STRONG_N + FAST_N;
        if (file.length < expected) {
            return CacheStatus.CORRUPT; // payload/digest truncation
        }
        byte[] payload = Arrays.copyOfRange(file, HEADER_LEN, HEADER_LEN + payloadLen);
        byte[] strong = Arrays.copyOfRange(file, HEADER_LEN + payloadLen, HEADER_LEN + payloadLen + STRONG_N);
        byte[] fast = Arrays.copyOfRange(file, HEADER_LEN + payloadLen + STRONG_N,
                HEADER_LEN + payloadLen + STRONG_N + FAST_N);
        byte[] sRe = Tsha1f.tsha1f(payload, STRONG_N, BASE).getBytes(StandardCharsets.US_ASCII);
        if (sRe.length != STRONG_N || !Arrays.equals(sRe, strong)) {
            return CacheStatus.CORRUPT; // strong checksum mismatch (tamper/truncate)
        }
        byte[] fRe = Tsha1f.tsha1f(payload, FAST_N, BASE).getBytes(StandardCharsets.US_ASCII);
        if (fRe.length != FAST_N || !Arrays.equals(fRe, fast)) {
            return CacheStatus.CORRUPT; // fast checksum mismatch
        }
        try {
            LodSection.fromBytes(payload); // malformed payload -> reject (not throw out)
        } catch (IllegalArgumentException e) {
            return CacheStatus.CORRUPT;
        }
        return CacheStatus.OK;
    }

    private static int readInt(byte[] d, int i) {
        return ((d[i] & 0xFF) << 24) | ((d[i + 1] & 0xFF) << 16)
                | ((d[i + 2] & 0xFF) << 8) | (d[i + 3] & 0xFF);
    }

    private static void writeBeInt(ByteArrayOutputStream out, int v) {
        out.write((v >>> 24) & 0xFF);
        out.write((v >>> 16) & 0xFF);
        out.write((v >>> 8) & 0xFF);
        out.write(v & 0xFF);
    }
}