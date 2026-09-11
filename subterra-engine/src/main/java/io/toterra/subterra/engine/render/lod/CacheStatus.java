package io.toterra.subterra.engine.render.lod;

/**
 * The fixed status enumeration for the LOD mesh cache (p.2.28.5): the single deterministic
 * outcome of every {@link LodCache} {@code load}/{@code hit}/{@code save} call. The set is
 * closed and pinned — callers switch on it and never guess a free-form reason string, so
 * a damaged or tampered cache entry is rejected deterministically rather than silently
 * serving bad data.
 *
 * <ul>
 *   <li>{@link #OK} — the entry exists and passed every validation step (magic, format
 *       version, per-entry tsha1f fast/strong checksum, {@link LodSection#fromBytes});
 *       {@link CacheResult#section()} is present.</li>
 *   <li>{@link #MISS} — no cache file exists for the key (yet). Load/hit return this
 *       deterministically (a skip, not a throw) so the caller re-generates.</li>
 *   <li>{@link #CORRUPT} — a cache file exists but is damaged: bad magic, header/protocol
 *       truncation, payload truncation, malformed {@link LodSection} bytes, or a fast/strong
 *       tsha1f checksum mismatch (single-byte tamper, truncation and reordering all land
 *       here).</li>
 *   <li>{@link #VERSION_MISMATCH} — a cache file exists and parsed its magic, but its
 *       pinned format version does not equal {@link LodCache#FORMAT_VERSION}; deterministically
 *       rejected so an old-format entry is regenerated rather than misread.</li>
 *   <li>{@link #IO_ERROR} — an I/O failure occurred while reading/writing/creating the
 *       cache file. Kept out of {code OK}/{@code MISS}/{@code CORRUPT}/{@code VERSION_MISMATCH}
 *       because it is not a proof of tampering; the caller decides whether to skip
 *       regeneration (the default) or retry.</li>
 * </ul>
 *
 * <p>Statuses {@link #MISS} and {@link #CORRUPT} are deliberately non-fatal: {@link LodCache}
 * never throws on a damaged entry — it returns the fixed status so the caller can regenerate
 * (the cache is a {@linkplain LodCache#REGENERABLE regenerable} artifact).
 *
 * <p>LOD 网格缓存的固定状态枚举（p.2.28.5）：每个 {@link LodCache} {@code load}/{@code hit}/
 * {@code save} 调用的唯一确定性结果。集合封闭且钉死——调用方对其 switch 而非猜测自由文本原因，
 * 故损坏或遭篡改的缓存条目被确定性拒绝而非静默使用坏数据。
 *
 * <ul>
 *   <li>{@link #OK} — 条目存在且通过全部校验步骤（魔数、格式版本、逐条目 tsha1f 快/强档校验、
 *       {@link LodSection#fromBytes}）；{@link CacheResult#section()} 存在。</li>
 *   <li>{@link #MISS} — 该键尚无缓存文件。load/hit 确定性返回此状态（为跳过而非抛异常），
 *       调用方据此重新生成。</li>
 *   <li>{@link #CORRUPT} — 存在缓存文件但受损：坏魔数、头/协议截断、载荷截断、畸形
 *       {@link LodSection} 字节，或快/强档 tsha1f 校验不符（单字节篡改、截断与重排均落此）。
 *       </li>
 *   <li>{@link #VERSION_MISMATCH} — 存在缓存文件且魔数解析通过，但其钉死格式版本不等于
 *       {@link LodCache#FORMAT_VERSION}；确定性拒绝，旧格式条目被重生成而非误读。</li>
 *   <li>{@link #IO_ERROR} — 读/写/建缓存文件时的 I/O 失败。因其并非篡改的证据而单列于
 *       {code OK}/{@code MISS}/{@code CORRUPT}/{@code VERSION_MISMATCH} 之外；由调用方决定
 *       是否跳过重生成（默认）或重试。</li>
 * </ul>
 *
 * <p>{@link #MISS} 与 {@link #CORRUPT} 刻意非致命：{@link LodCache} 在损坏条目上决不抛异常——
 * 而是返回固定状态以便调用方重生成（缓存是 {@linkplain LodCache#REGENERABLE 可再生}产物）。
 *
 * @see LodCache#load(LodCacheKey)
 * @see LodCache#hit(LodCacheKey)
 * @see LodCache#save(LodCacheKey, LodSection)
 */
public enum CacheStatus {

    /** Entry present and fully validated; the section payload is available.
     *  / 条目存在且全部校验通过；区块载荷可用。 */
    OK,

    /** No cache file for this key; caller should (re)generate.
     *  / 该键无缓存文件；调用方应（重新）生成。 */
    MISS,

    /** Cache file present but damaged (magic / truncation / malformed payload / checksum).
     *  / 缓存文件存在但受损（魔数 / 截断 / 畸形载荷 / 校验）。 */
    CORRUPT,

    /** Cache file present but its pinned format version is not {@code FORMAT_VERSION}.
     *  / 缓存文件存在但其钉死格式版本非 {@code FORMAT_VERSION}。 */
    VERSION_MISMATCH,

    /** An I/O failure occurred (not a tamper verdict). / 发生 I/O 失败（非篡改判定）。 */
    IO_ERROR
}