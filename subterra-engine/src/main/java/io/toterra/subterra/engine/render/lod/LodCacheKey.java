package io.toterra.subterra.engine.render.lod;

import java.util.Objects;

/**
 * The fixed cache key of the LOD mesh cache (p.2.28.5): an immutable binding of an
 * {@link LodLevel} and a chunk-grid origin ({@code chunkX}, {@code chunkZ}) into a single
 * deterministic identity, plus its pinned <b>fixed</b> byte encoding and its pinned
 * <b>fixed</b> file name. The identities codify the on-disk layout of {@link LodCache}:
 * the same key always maps to the same bytes and the same file name (level + chunk
 * coordinates, no timestamps, no randomness), so the same logical section is always
 * stored at the same path with the same canonicalized file name.
 *
 * <p>The two pinned artifacts of a key:
 * <ol>
 *   <li><b>Fixed byte encoding</b> ({@link #encode()}), 12 bytes big-endian:
 *       {@code [ levelOrdinal:int ][ chunkX:int ][ chunkZ:int ]}.</li>
 *   <li><b>Fixed file name</b> ({@link #fileName()}):
 *       {@code "sec_l<k>_cx<x>_cz<z>.lod"} where {@code k}={@link LodLevel#k()},
 *       {@code x}={@code chunkX}, {@code z}={@code chunkZ}.</li>
 * </ol>
 * Both artifacts are pinned by javadoc and never change; a future revision must not
 * permute {@link #encode()} fields or the file-name template.
 *
 * <p>LOD 网格缓存的固定键（p.2.28.5）：把 {@link LodLevel} 与区块网格原点（{@code chunkX},
 * {@code chunkZ}）绑定为一个确定性身份的不可变对象，外加其钉死的<b>固定</b>字节编码与钉死的
 * <b>固定</b>文件名。该身份固化 {@link LodCache} 的落盘布局：同一键恒映射到同一字节与同一文件名
 * （level + 区块坐标，无时间戳、无随机），故同一逻辑区块恒以同一规范化文件名存于同一路径。
 *
 * <p>键的两个钉死产物：
 * <ol>
 *   <li><b>固定字节编码</b>（{@link #encode()}），12 字节大端：
 *       {@code [ levelOrdinal:int ][ chunkX:int ][ chunkZ:int ]}。</li>
 *   <li><b>固定文件名</b>（{@link #fileName()}）：
 *       {@code "sec_l<k>_cx<x>_cz<z>.lod"}，其中 {@code k}={@link LodLevel#k()}、
 *       {@code x}={@code chunkX}、{@code z}={@code chunkZ}。</li>
 * </ol>
 * 两产物均被 javadoc 钉死且绝不再改；未来修订不得重排 {@link #encode()} 字段或文件名模板。
 *
 * <p>Semantics: the key is the "section key" = level + block-section chunk coordinate of
 * p.2.28.1 (the {@link LodSection} origin). Equality and {@link #hashCode()} are derived
 * from those three fields, so {@code equals} is deterministic and stable regardless of
 * construction order.
 *
 * <p>语义：该键即「section 键」= level + p.2.28.1 的区块 section 区块坐标（{@link LodSection}
 * 原点）。{@code equals} 与 {@link #hashCode()} 都派生自这三个字段，故不随构造顺序改变而恒稳。
 */
public final class LodCacheKey {

    /** Fixed byte length of {@link #encode()} (3 x int BE). / {@link #encode()} 的固定字节长（3 × int BE）。 */
    public static final int BYTE_SIZE = 12;

    /** Fixed file-name suffix. / 固定文件名后缀。 */
    public static final String FILE_SUFFIX = ".lod";

    private final LodLevel level;
    private final int chunkX;
    private final int chunkZ;

    /**
     * Builds the cache key for a level and a chunk-grid origin. A null level is rejected.
     * / 为某层级与区块网格原点构建缓存键。null 层级被拒绝。
     */
    public LodCacheKey(LodLevel level, int chunkX, int chunkZ) {
        this.level = Objects.requireNonNull(level, "level must not be null");
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
    }

    /** The cached detail level. / 被缓存的细节层级。 */
    public LodLevel level() {
        return level;
    }

    /** Chunk-origin x of the section. / 区块 section 的原点 x。 */
    public int chunkX() {
        return chunkX;
    }

    /** Chunk-origin z of the section. / 区块 section 的原点 z。 */
    public int chunkZ() {
        return chunkZ;
    }

    /**
     * The pinned fixed byte encoding ({@value #BYTE_SIZE} bytes, big-endian):
     * {@code [ levelOrdinal:int ][ chunkX:int ][ chunkZ:int ]}. Byte-identical for the
     * same key; this is the on-disk identity that peers with the {@link LodSection#VERSION}
     * header so the same logical section is always addressable deterministically.
     * / 钉死固定字节编码（{@value #BYTE_SIZE} 字节，大端）：
     * {@code [ levelOrdinal:int ][ chunkX:int ][ chunkZ:int ]}。同一键逐字节一致；此即与
     * {@link LodSection#VERSION} 头配对的落盘身份，使同一逻辑区块恒可用确定性地寻址。
     */
    public byte[] encode() {
        byte[] out = new byte[BYTE_SIZE];
        int i = 0;
        i = writeInt(out, i, level.ordinal());
        i = writeInt(out, i, chunkX);
        writeInt(out, i, chunkZ);
        return out;
    }

    /**
     * The pinned fixed file name for this key:
     * {@code "sec_l<k>_cx<x>_cz<z>.lod"} (k={@link LodLevel#k()}, x/y/z as above). Fixed
     * and canonical — no timestamps, no randomness. / 该键的钉死固定文件名：
     * {@code "sec_l<k>_cx<x>_cz<z>.lod"}（k 如上等）。固定且规范——无时间戳、无随机。
     */
    public String fileName() {
        return "sec_l" + level.k() + "_cx" + chunkX + "_cz" + chunkZ + FILE_SUFFIX;
    }

    @Override
    public boolean equals(Object o) {
        return this == o || (o instanceof LodCacheKey k
                && level == k.level && chunkX == k.chunkX && chunkZ == k.chunkZ);
    }

    @Override
    public int hashCode() {
        int h = 31 * level.hashCode() + chunkX;
        return 31 * h + chunkZ;
    }

    @Override
    public String toString() {
        return "LodCacheKey{level=" + level.form() + " chunk=" + chunkX + "," + chunkZ + "}";
    }

    private static int writeInt(byte[] out, int i, int v) {
        out[i++] = (byte) (v >> 24);
        out[i++] = (byte) (v >> 16);
        out[i++] = (byte) (v >> 8);
        out[i++] = (byte) v;
        return i;
    }
}