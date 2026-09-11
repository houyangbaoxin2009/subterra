package io.toterra.subterra.api.lod;

import java.util.Locale;

/**
 * p.2.28.6 固定序质量档位契约（对外契约面，纯 JDK）：唯一字段即固定档位序，语义与
 * {@code engine.render.lod.LodConfigDoc.QUALITY_TIERS}（{@code standard, high, low}）逐字一致——
 * 本处为契约形状、engine 为实现侧镜像，api 不依赖 engine。固定序为
 * {@code STANDARD, HIGH, LOW}（即 engine 档序原样 {@code standard→high→low}）；{@link #defaultQuality()}
 * 由 {@code STANDARD} 承担（engine 默认档为 {@code QUALITY_TIERS.get(0)}）。
 * <p>确定性：{@link #values()} 序固定，为本包统一规范迭代序；{@link #form()} 给出小写注册名，
 * {@link #fromForm(String)} 确定性回映射（未知形式被拒绝）；{@link #maxLevel()} 与
 * {@link #distances()} 是对 engine 钉死缺省配置（{@code LodConfigDoc} 的 {@code maxLevel}=4、
 * {@code DEFAULT_DISTANCES}）的逐档忠实读取——engine 当前为所有质量档共用同一份缺省配置，api 如实
 * 暴露，不发明任何逐档差异。无随机、无时序。
 * <p>
 * p.2.28.6 the fixed-order quality-tier contract (the external contract surface, pure JDK): the only
 * thing it fixes is the tier order, and its semantics are verbatim-identical to
 * {@code engine.render.lod.LodConfigDoc.QUALITY_TIERS} ({@code standard, high, low}) — this is the contract
 * shape, the engine mirrors as its implementation, and the api does not depend on the engine. The fixed order
 * is {@code STANDARD, HIGH, LOW} (the engine tier order taken as-is, {@code standard→high→low});
 * {@link #defaultQuality()} is served by {@code STANDARD} (the engine default is {@code QUALITY_TIERS.get(0)}).
 * <p>Deterministic: {@link #values()} order is fixed and is the canonical iteration order used across this
 * package; {@link #form()} yields the lowercase registration name and {@link #fromForm(String)} maps back
 * deterministically (unknown forms are rejected); {@link #maxLevel()} and {@link #distances()} are faithful
 * per-tier reads of the engine's pinned default configuration ({@code LodConfigDoc}'s {@code maxLevel}=4,
 * {@code DEFAULT_DISTANCES}) — the engine currently shares one default configuration across all quality tiers,
 * and the api mirrors that truth without inventing any per-tier difference. No randomness, no timing.
 */
public enum LodQualitySpec {

    /** Standard quality; the engine default tier {@code QUALITY_TIERS.get(0)}. / 标准质量档；
     *  engine 默认档 {@code QUALITY_TIERS.get(0)}。 */
    STANDARD,
    /** High quality tier. / 高细节质量档。 */
    HIGH,
    /** Low quality tier. / 低细节质量档。 */
    LOW;

    /** Lowercase registration name ({@code standard}/{@code high}/{@code low}). / 小写注册名。 */
    public String form() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** The max detail level pinned for this tier: {@link LodApi#DEFAULT_MAX_LEVEL} (= 4, mirrors
     *  {@code LodConfigDoc} default). / 本档钉死最大细节层：{@link LodApi#DEFAULT_MAX_LEVEL}（= 4，
     *  镜像 {@code LodConfigDoc} 缺省）。 */
    public int maxLevel() {
        return LodApi.DEFAULT_MAX_LEVEL;
    }

    /** The fixed distance-threshold spec for this tier: {@link LodApi#defaultDistanceSpec()} (mirrors
     *  {@code LodConfigDoc.DEFAULT_DISTANCES}). / 本档固定距离阈值 spec：
     *  {@link LodApi#defaultDistanceSpec()}（镜像 {@code LodConfigDoc.DEFAULT_DISTANCES}）。 */
    public LodDistanceSpec distances() {
        return LodApi.defaultDistanceSpec();
    }

    /**
     * Maps a lowercase (or any-case) registration form back to a {@link LodQualitySpec}. Deterministic;
     * throws {@link IllegalArgumentException} for unknown forms. / 将小写（或任意大小写）注册名回映射为
     * {@link LodQualitySpec}。确定性；未知形式抛 {@link IllegalArgumentException}。
     *
     * @param form the registration form (non-null).
     * @return the matching {@link LodQualitySpec}.
     * @throws IllegalArgumentException if the form is null or not one of {@code standard}/{@code high}/{@code low}.
     */
    public static LodQualitySpec fromForm(String form) {
        if (form == null) {
            throw new IllegalArgumentException("lod quality form must not be null");
        }
        String f = form.trim().toLowerCase(Locale.ROOT);
        for (LodQualitySpec q : values()) {
            if (q.form().equals(f)) {
                return q;
            }
        }
        throw new IllegalArgumentException("unknown lod quality form: " + form);
    }
}