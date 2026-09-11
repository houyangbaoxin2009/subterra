package io.toterra.subterra.engine.render.lod;

import java.util.Locale;

/**
 * The fixed detail-level ladder of the LOD system (p.2.28.1, clean-room self-developed):
 * six levels {@code L0}..{@code L5}, one per scale step {@code k} of the local chunk grid.
 * Level {@code k} {@linkplain #chunkSpan() spans} {@code 2^k} chunks per side
 * ({@code L0=1, L1=2, L2=4, ...}) and {@linkplain #blockSpan() spans} {@code 16 * chunkSpan}
 * blocks per side. The enumeration order <em>is</em> the fixed canonical order: {@link #values()}
 * returns {@code L0..L5} deterministically, and distance thresholds are <em>not</em> hard-coded
 * here — they belong to {@link LodConfigDoc}. Deterministic: {@link #form()} yields the
 * lowercase registration name, {@link #fromForm(String)} maps back deterministically, and
 * {@link #canonicalText()} is byte-identical for the same level (no input, no timing).
 *
 * <p>LOD 系统固定细节层级阶梯（p.2.28.1，clean-room 自研）：六个层级 {@code L0}..{@code L5}，对应本地
 * 区块网格的每个缩放步 {@code k}。层级 {@code k} 每边 {@linkplain #chunkSpan() 覆盖} {@code 2^k}
 * 个区块（{@code L0=1, L1=2, L2=4, ...}），每边 {@linkplain #blockSpan() 覆盖} {@code 16 * chunkSpan}
 * 个方块。枚举序即为固定规范序：{@link #values()} 确定性返回 {@code L0..L5}；距离阈值不写死在此——归
 * {@link LodConfigDoc} 管。确定性：{@link #form()} 给出小写注册名，{@link #fromForm(String)}
 * 确定性回映射，{@link #canonicalText()} 对同一层级逐字节一致（无输入、无时序）。
 */
public enum LodLevel {

    /** Most detailed level; spans {@code 2^0 = 1} chunk per side. / 最细层级；每边 {@code 2^0 = 1} 区块。 */
    L0(0),
    /** Coarser level; spans {@code 2^1 = 2} chunks per side. / 次细层级；每边 {@code 2^1 = 2} 区块。 */
    L1(1),
    /** Coarser level; spans {@code 2^2 = 4} chunks per side. / 较粗层级；每边 {@code 2^2 = 4} 区块。 */
    L2(2),
    /** Coarser level; spans {@code 2^3 = 8} chunks per side. / 更粗层级；每边 {@code 2^3 = 8} 区块。 */
    L3(3),
    /** Coarser level; spans {@code 2^4 = 16} chunks per side. / 更粗层级；每边 {@code 2^4 = 16} 区块。 */
    L4(4),
    /** Coarsest level; spans {@code 2^5 = 32} chunks per side. / 最粗层级；每边 {@code 2^5 = 32} 区块。 */
    L5(5);

    private final int k;

    LodLevel(int k) {
        this.k = k;
    }

    /** The level's scale step {@code k} (@{@code 0..5}). / 层级缩放步 {@code k}（{@code 0..5}）。 */
    public int k() {
        return k;
    }

    /** The chunk span of the level, {@code 1 << k}. / 层级区块跨度，{@code 1 << k}。 */
    public int chunkSpan() {
        return 1 << k;
    }

    /** The block span of the level, {@code 16 * chunkSpan}. / 层级方块跨度，{@code 16 * chunkSpan}。 */
    public int blockSpan() {
        return 16 * chunkSpan();
    }

    /** Lowercase registration name. / 小写注册名。 */
    public String form() {
        return name().toLowerCase(Locale.ROOT);
    }

    /**
     * Maps a lowercase (or any-case) registration form back to a level. Deterministic;
     * throws {@link IllegalArgumentException} for unknown forms.
     * / 将小写（或任意大小写）注册名回映射为层级。确定性；未知形式抛 {@link IllegalArgumentException}。
     */
    public static LodLevel fromForm(String form) {
        if (form == null) {
            throw new IllegalArgumentException("lod level form must not be null");
        }
        String f = form.trim().toLowerCase(Locale.ROOT);
        for (LodLevel l : values()) {
            if (l.form().equals(f)) {
                return l;
            }
        }
        throw new IllegalArgumentException("unknown lod level form: " + form);
    }

    /**
     * Canonical fixed-order text of this level: {@code <NAME> k=N chunkSpan=NNN blockSpan=NNN}.
     * Byte-identical for the same level under all conditions (no input, no timing).
     * / 本层级的规范固定序文本：{@code <NAME> k=N chunkSpan=NNN blockSpan=NNN}。对同一层级在任何条件下
     * 均逐字节一致（无输入、无时序）。
     */
    public String canonicalText() {
        return name() + " k=" + k + " chunkSpan=" + chunkSpan() + " blockSpan=" + blockSpan();
    }

    /**
     * The full fixed-order ladder as canonical text, one line per level in
     * {@link #values()} order ({@code L0..L5}). Deterministic; identical on every call.
     * / 完整固定序阶梯的规范文本，按 {@link #values()} 序（{@code L0..L5}）每层级一行。确定性；每次调用
     * 均相同。
     */
    public static String canonicalLadder() {
        StringBuilder sb = new StringBuilder();
        for (LodLevel l : values()) {
            sb.append(l.canonicalText()).append('\n');
        }
        return sb.toString();
    }
}