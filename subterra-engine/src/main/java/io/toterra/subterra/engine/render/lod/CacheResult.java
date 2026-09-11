package io.toterra.subterra.engine.render.lod;

/**
 * The fixed result record of every {@link LodCache} access (p.2.28.5): an immutable
 * {@link CacheStatus} paired with the loaded {@link LodSection} payload (present only when
 * {@link #status()} is {@link CacheStatus#OK}). The pairing is the cache's deterministic
 * contract to callers — a {@link CacheStatus#MISS} or {@link CacheStatus#CORRUPT} yields a
 * status with a {@code null} section (a skip to regenerate), never a throw bounded to bad
 * data. Deterministic: the same call over the same on-disk bytes yields the same status and
 * byte-identical section (or {@code null}).
 *
 * <p>Every {@link LodCache} 访问的固定结果记录（p.2.28.5）：一个不可变 {@link CacheStatus}
 * 与所加载 {@link LodSection} 载荷（仅当 {@link #status()} 为 {@link CacheStatus#OK} 时存在）
 * 配对。该配对即缓存给调用方的确定性契约——{@link CacheStatus#MISS} 或 {@link CacheStatus#CORRUPT}
 * 给出带 {@code null} 区块的状态（提示重生成的跳过），而非抛出与被坏数据绑定。确定性：对同一磁盘
 * 字节的同一调用恒得相同状态与逐字节一致的区块（或 {@code null}）。
 *
 * @param status the fixed outcome / 固定结果状态
 * @param section the loaded section payload, or null unless status is {@link CacheStatus#OK}
 *               / 所加载区块载荷；仅当状态为 {@link CacheStatus#OK} 时非 null
 * @see CacheStatus
 * @see LodCache
 */
public record CacheResult(CacheStatus status, LodSection section) {

    /**
     * A short-cut for a section-less result of the given status (always used for
     * {@link CacheStatus#MISS}/{@link CacheStatus#CORRUPT}/{@link CacheStatus#VERSION_MISMATCH}/
     * {@link CacheStatus#IO_ERROR}).
     * / 给定状态的无区块结果的快捷方式（恒用于 {@link CacheStatus#MISS}/
     * {@link CacheStatus#CORRUPT}/{@link CacheStatus#VERSION_MISMATCH}/{@link CacheStatus#IO_ERROR}）。
     *
     * @param status the status to wrap, must not be null / 待包装状态，不可为 null
     * @return an immutable result with a null section / 带 null 区块的不可变结果
     */
    public static CacheResult of(CacheStatus status) {
        return new CacheResult(status, null);
    }

    /**
     * The loaded section payload; null unless {@link #status()} is {@link CacheStatus#OK}.
     * / 所加载区块载荷；仅当 {@link #status()} 为 {@link CacheStatus#OK} 时非 null。
     */
    @Override
    public LodSection section() {
        return status == CacheStatus.OK ? section : null;
    }
}