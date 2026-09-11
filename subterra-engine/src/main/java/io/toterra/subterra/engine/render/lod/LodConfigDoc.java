package io.toterra.subterra.engine.render.lod;

import io.toterra.subterra.engine.config.TdTable;
import io.toterra.subterra.engine.config.TdValue;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * td-ized LOD tier configuration (p.2.28.1, clean-room self-developed): a configuration
 * document that carries the LOD level ladder, quality tier, cache policy and per-level
 * distance thresholds into the framework's {@code td} data language, mirroring the same
 * pattern as {@code engine.time.TimeScaleDoc}. The document shape is
 * <pre>
 *   lod = [ enabled = true, maxLevel = 4, qualityTier = "standard",
 *           cachePolicy = "none", distances = [ [1, 64], [2, 128], ... ] ]
 * </pre>
 * Distance thresholds live here, not in {@link LodLevel}. Deterministic:
 * {@link #fromTd(TdTable)} reads {@code enabled} (default {@code true}), {@code maxLevel}
 * (default {@code 4}), {@code qualityTier} (default {@code "standard"}),
 * {@code cachePolicy} (default {@code "none"}); {@link #toTd(LodConfigDoc)} writes them in
 * fixed key order with no timestamps, so {@link #fromTd(TdTable)} of a written document is a
 * stable, byte-identical round-trip (parse-write-parse-write idempotence). Missing values
 * take their defaults; unknown keys are ignored deterministically; present values are
 * validated with {@link IllegalArgumentException}.
 *
 * <p>td 化 LOD 分档配置（p.2.28.1，clean-room 自研）：把 LOD 层级阶梯、质量档、缓存策略与按级距离阈值带入框架的
 * {@code td} 数据语言，模式与 {@code engine.time.TimeScaleDoc} 一致。文档形态为
 * <pre>
 *   lod = [ enabled = true, maxLevel = 4, qualityTier = "standard",
 *           cachePolicy = "none", distances = [ [1, 64], [2, 128], ... ] ]
 * </pre>
 * 距离阈值归这里，不在 {@link LodLevel}。确定性：{@link #fromTd(TdTable)} 读取 {@code enabled}（缺省
 * {@code true}）、{@code maxLevel}（缺省 {@code 4}）、{@code qualityTier}（缺省 {@code "standard"}）、
 * {@code cachePolicy}（缺省 {@code "none"}）；{@link #toTd(LodConfigDoc)} 按固定键序、无时间戳写出，使
 * {@link #fromTd(TdTable)} 对已写出文档为稳定、逐字节一致的往返（解析-写出-再解析-再写出幂等）。缺失值取缺省；
 * 未知键被确定性忽略；在场取值经 {@link IllegalArgumentException} 校验。
 */
public final class LodConfigDoc {

    /** Allowed quality tiers, in fixed canonical order. / 允许的质量档，固定规范序。 */
    public static final List<String> QUALITY_TIERS = List.of("standard", "high", "low");
    /** Allowed cache policies, in fixed canonical order. / 允许的缓存策略，固定规范序。 */
    public static final List<String> CACHE_POLICIES = List.of("none", "keep", "stream");
    /** Default distance ladder: [{@code level}, {@code distance}]. / 缺省距离阶梯：[{@code level}, {@code distance}]。 */
    public static final List<Distance> DEFAULT_DISTANCES = List.of(
            new Distance(1, 64), new Distance(2, 128), new Distance(3, 256), new Distance(4, 512));

    private final boolean enabled;
    private final int maxLevel;
    private final String qualityTier;
    private final String cachePolicy;
    private final List<Distance> distances;

    private LodConfigDoc(boolean enabled, int maxLevel, String qualityTier,
                         String cachePolicy, List<Distance> distances) {
        this.enabled = enabled;
        this.maxLevel = maxLevel;
        this.qualityTier = qualityTier;
        this.cachePolicy = cachePolicy;
        this.distances = List.copyOf(distances);
    }

    /** A fully-default document. / 全缺省文档。 */
    public static LodConfigDoc defaults() {
        return new LodConfigDoc(true, 4, QUALITY_TIERS.get(0), CACHE_POLICIES.get(0), DEFAULT_DISTANCES);
    }

    /** Whether distant-terrain rendering is enabled. / 是否启用远景地形渲染。 */
    public boolean enabled() {
        return enabled;
    }

    /** Highest enabled detail level index (1..5). / 最高启用细节层级序号（1..5）。 */
    public int maxLevel() {
        return maxLevel;
    }

    /** Quality tier: one of {@code standard}/{@code high}/{@code low}. / 质量档：{@code standard}/{@code high}/{@code low} 之一。 */
    public String qualityTier() {
        return qualityTier;
    }

    /** Cache policy: one of {@code none}/{@code keep}/{@code stream}. / 缓存策略：{@code none}/{@code keep}/{@code stream} 之一。 */
    public String cachePolicy() {
        return cachePolicy;
    }

    /** Per-level distance thresholds in fixed ascending-level order (unmodifiable). / 按级距离阈值，固定升层级序（不可变）。 */
    public List<Distance> distances() {
        return distances;
    }

    /**
     * Parses a {@code lod} table into a doc with defaults and validation (mirrors
     * {@code TimeScaleDoc#fromTd}). / 将 {@code lod} 表解析为文档，含缺省值与校验（镜像
     * {@code TimeScaleDoc#fromTd}）。
     *
     * @param root the {@code lod} table (non-null).
     * @throws NullPointerException     if {@code root} is null.
     * @throws IllegalArgumentException if any present value is invalid.
     */
    public static LodConfigDoc fromTd(TdTable root) {
        Objects.requireNonNull(root, "root must not be null");
        boolean enabled = true;
        TdValue en = root.get("enabled");
        if (en != null) {
            enabled = en.asBool();
        }
        int maxLevel = 4;
        TdValue ml = root.get("maxLevel");
        if (ml != null) {
            maxLevel = (int) ml.asInt();
        }
        String tier = QUALITY_TIERS.get(0);
        TdValue qt = root.get("qualityTier");
        if (qt != null) {
            tier = normalize(tierOf(qt.asString()), QUALITY_TIERS, "qualityTier");
        }
        String policy = CACHE_POLICIES.get(0);
        TdValue cp = root.get("cachePolicy");
        if (cp != null) {
            policy = normalize(policyOf(cp.asString()), CACHE_POLICIES, "cachePolicy");
        }
        List<Distance> dists = new ArrayList<>(DEFAULT_DISTANCES);
        TdValue ds = root.get("distances");
        if (ds != null) {
            List<TdValue> elems = ds.asList();
            if (elems.isEmpty()) {
                throw new IllegalArgumentException("lod distances must not be empty");
            }
            dists.clear();
            for (TdValue e : elems) {
                List<TdValue> pair = e.asList();
                if (pair.size() != 2) {
                    throw new IllegalArgumentException("each distance must be [level, distance]");
                }
                int level = (int) pair.get(0).asInt();
                int distance = (int) pair.get(1).asInt();
                dists.add(new Distance(level, distance));
            }
        }
        validate(maxLevel, tier, policy, dists);
        return new LodConfigDoc(enabled, maxLevel, tier, policy, dists);
    }

    /**
     * Writes a doc back to a {@link TdTable} in fixed key order
     * ({@code enabled, maxLevel, qualityTier, cachePolicy, distances}) with no timestamps, so
     * {@link #fromTd(TdTable)} is a stable, byte-identical round-trip. Deterministic.
     * / 将文档按固定键序（{@code enabled, maxLevel, qualityTier, cachePolicy, distances}）、无时间戳写回
     * {@link TdTable}，使 {@link #fromTd(TdTable)} 为稳定、逐字节一致的往返。确定性。
     *
     * @param doc the {@link LodConfigDoc} to serialize (non-null).
     * @return a {@link TdTable} representing the document.
     * @throws NullPointerException if {@code doc} is null.
     */
    public static TdTable toTd(LodConfigDoc doc) {
        Objects.requireNonNull(doc, "doc must not be null");
        TdTable.Builder b = TdTable.builder();
        b.put("enabled", TdValue.of(doc.enabled));
        b.put("maxLevel", TdValue.of(doc.maxLevel));
        b.put("qualityTier", TdValue.str(doc.qualityTier));
        b.put("cachePolicy", TdValue.str(doc.cachePolicy));
        TdTable.Builder db = TdTable.builder();
        for (Distance d : doc.distances) {
            TdTable.Builder pb = TdTable.builder();
            pb.element(TdValue.of(d.level()));
            pb.element(TdValue.of(d.distance()));
            db.element(pb.build());
        }
        b.put("distances", db.build());
        return b.build();
    }

    private static void validate(int maxLevel, String tier, String policy, List<Distance> dists) {
        if (maxLevel < 1 || maxLevel > 5) {
            throw new IllegalArgumentException("lod maxLevel must be in [1, 5] but was " + maxLevel);
        }
        if (!QUALITY_TIERS.contains(tier)) {
            throw new IllegalArgumentException("invalid lod qualityTier: " + tier);
        }
        if (!CACHE_POLICIES.contains(policy)) {
            throw new IllegalArgumentException("invalid lod cachePolicy: " + policy);
        }
        int prevLevel = 0;
        for (Distance d : dists) {
            if (d.level() < 1 || d.level() > 5) {
                throw new IllegalArgumentException("lod distance level must be in [1, 5] but was " + d.level());
            }
            if (d.level() <= prevLevel) {
                throw new IllegalArgumentException("lod distance levels must be strictly increasing; saw "
                        + prevLevel + " then " + d.level());
            }
            if (d.distance() <= 0) {
                throw new IllegalArgumentException("lod distance must be > 0 but was " + d.distance());
            }
            prevLevel = d.level();
        }
    }

    private static String normalize(String value, List<String> allowed, String what) {
        for (String a : allowed) {
            if (a.equals(value)) {
                return a;
            }
        }
        throw new IllegalArgumentException("unknown lod " + what + ": " + value);
    }

    private static String tierOf(String raw) {
        return raw == null ? "" : raw.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private static String policyOf(String raw) {
        return raw == null ? "" : raw.trim().toLowerCase(java.util.Locale.ROOT);
    }

    @Override
    public boolean equals(Object o) {
        return this == o || (o instanceof LodConfigDoc d && enabled == d.enabled
                && maxLevel == d.maxLevel && qualityTier.equals(d.qualityTier)
                && cachePolicy.equals(d.cachePolicy) && distances.equals(d.distances));
    }

    @Override
    public int hashCode() {
        int h = (enabled ? 1 : 0);
        h = 31 * h + maxLevel;
        h = 31 * h + qualityTier.hashCode();
        h = 31 * h + cachePolicy.hashCode();
        return 31 * h + distances.hashCode();
    }

    @Override
    public String toString() {
        return "LodConfigDoc{enabled=" + enabled + ", maxLevel=" + maxLevel + ", qualityTier="
                + qualityTier + ", cachePolicy=" + cachePolicy + ", distances=" + distances + '}';
    }

    /** A single level-distance threshold; immutable. / 单条层级-距离阈值；不可变。 */
    public record Distance(int level, int distance) {
    }
}