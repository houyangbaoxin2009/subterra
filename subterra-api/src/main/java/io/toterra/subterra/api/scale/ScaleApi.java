package io.toterra.subterra.api.scale;

import java.util.Locale;
import java.util.List;

/**
 * p.2.33.2 对外的实体缩放契约门面（final class、静态纯函数、纯 JDK）：确定性接口面，供上层/域模组按
 * 「固定维度序 + 维度/标签缩放查询语义」解析实体缩放。语义与 {@code engine.scale} 的
 * {@code ScaleType}/{@code ScaleData}/{@code ScaleRuleDocument}（p.2.25.1）一致——本处为契约与数据面
 * 注入，engine 为实现镜像（{@code engine.scale.ScaleApiMirror}），api 不依赖 engine。所有契约常量均系
 * 从 engine 实际常量逐字对照落于此，禁止拍脑袋数值。
 * <p>确定性：{@link #dimensions()} 返回固定 9 维序（{@code base, width, height, depth, eye_height,
 * hitbox_width, hitbox_height, model_width, model_height}，镜像 {@code ScaleType.values()} 序）；
 * {@link #neutralScale()} 返回 {@code 1.0}（engine {@code ScaleData#get} 缺省中性值）；
 * {@link #normalizeDimension} 为纯规范化（trim + 小写）、非法维度名确定性拒绝（镜像
 * {@code ScaleType#fromForm}）；{@link #ruleFieldOrder()} 报告 {@code ScaleRuleDocument} td 往返的固定
 * 每条目字段序（{@code type,tag,scale}）。无随机、无墙钟、无迭代序依赖；全部线性遍历（禁 O(n²)）。
 * <p>
 * p.2.33.2 the external entity-scale contract facade (final class, static pure functions, pure JDK): a
 * deterministic interface surface for upper layers / domain mods to resolve entity scaling from a "fixed
 * dimension order + per-dimension/per-tag scale query semantics". Semantics match {@code engine.scale}'s
 * {@code ScaleType}/{@code ScaleData}/{@code ScaleRuleDocument} (p.2.25.1) — this is the contract and
 * data-injection surface for the engine to mirror as its implementation
 * ({@code engine.scale.ScaleApiMirror}), and the api does not depend on the engine. Every contract constant
 * here is pinned verbatim from the engine's actual constants — nothing is guessed.
 * <p>Deterministic: {@link #dimensions()} returns the fixed 9-dimension order ({@code base, width, height,
 * depth, eye_height, hitbox_width, hitbox_height, model_width, model_height}, mirroring
 * {@code ScaleType.values()} order); {@link #neutralScale()} returns {@code 1.0} (the engine
 * {@code ScaleData#get} default neutral value); {@link #normalizeDimension} is a pure normalization
 * (trim + lowercase) with deterministic rejection of illegal dimension names (mirroring
 * {@code ScaleType#fromForm}); {@link #ruleFieldOrder()} reports the fixed per-entry field order of the
 * {@code ScaleRuleDocument} td round-trip ({@code type,tag,scale}). No randomness, no wall-clock, no
 * iteration-order dependence; all traversals linear (no O(n²)).
 */
public final class ScaleApi {

    /** Neutral/vanilla scale value ({@code 1.0}), the engine {@code ScaleData#get} default for an absent
     *  dimension. / 中性/原版缩放值（{@code 1.0}），为 engine {@code ScaleData#get} 对未设置维度的缺省。 */
    public static final double NEUTRAL_SCALE = 1.0D;

    /** Fixed per-entry td field order of {@code engine.scale.ScaleRuleDocument} ({@code type, tag, scale}),
     *  mirroring {@code ScaleRuleDocument#toTd} verbatim. / {@code engine.scale.ScaleRuleDocument} 的固定
     *  每条目 td 字段序（{@code type, tag, scale}），逐字镜像 {@code ScaleRuleDocument#toTd}。 */
    public static final String RULE_FIELD_ORDER = "type,tag,scale";

    private ScaleApi() {
    }

    /**
     * The fixed 9-dimension order ({@code base, width, height, depth, eye_height, hitbox_width,
     * hitbox_height, model_width, model_height}), mirroring {@code ScaleType.values()}. Deterministic;
     * identical on every call. / 固定 9 维序（{@code base, width, height, depth, eye_height, hitbox_width,
     * hitbox_height, model_width, model_height}），镜像 {@code ScaleType.values()}。确定性；每次调用均相同。
     */
    public static List<String> dimensions() {
        return List.of("base", "width", "height", "depth", "eye_height",
                "hitbox_width", "hitbox_height", "model_width", "model_height");
    }

    /** The number of fixed dimensions ({@code 9}, mirroring {@code ScaleType.values().length}). /
     *  固定维度数（{@code 9}，镜像 {@code ScaleType.values().length}）。 */
    public static int dimensionCount() {
        return dimensions().size();
    }

    /** The neutral/vanilla root scale {@code 1.0} (the engine {@code ScaleData#get} absent-dimension
     *  default). / 中性/原版根缩放 {@code 1.0}（engine {@code ScaleData#get} 对未设置维度的缺省）。 */
    public static double neutralScale() {
        return NEUTRAL_SCALE;
    }

    /**
     * Canonicalizes a (case-insensitive, whitespace-tolerant) dimension registration form into its fixed
     * lowercase form, with the same semantics as {@code engine.scale.ScaleType#fromForm}. Deterministic;
     * an illegal/non-null-but-unknown form is rejected with {@link IllegalArgumentException}.
     * / 将（大小写不敏感、容忍空白）的维度注册名规范化为其固定小写形式，与
     * {@code engine.scale.ScaleType#fromForm} 同语义。确定性；非法/未知形式以 {@link IllegalArgumentException}
     * 拒绝。
     *
     * @param form the dimension registration form (non-null).
     * @return the canonical lowercase dimension form.
     * @throws IllegalArgumentException if {@code form} is null or not one of the 9 fixed dimensions.
     */
    public static String normalizeDimension(String form) {
        if (form == null) {
            throw new IllegalArgumentException("scale dimension form must not be null");
        }
        String f = form.trim().toLowerCase(Locale.ROOT);
        for (String d : dimensions()) {
            if (d.equals(f)) {
                return d;
            }
        }
        throw new IllegalArgumentException("unknown scale dimension form: " + form);
    }

    /** Whether {@code form} is one of the 9 fixed dimensions (deterministic; never throws). /
     *  {@code form} 是否为 9 个固定维度之一（确定性；绝不抛出）。 */
    public static boolean isValidDimension(String form) {
        try {
            normalizeDimension(form);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /** The fixed per-entry td field order of {@code ScaleRuleDocument} ({@code type, tag, scale}). /
     *  {@code ScaleRuleDocument} 的固定每条目 td 字段序（{@code type, tag, scale}）。 */
    public static String ruleFieldOrder() {
        return RULE_FIELD_ORDER;
    }
}