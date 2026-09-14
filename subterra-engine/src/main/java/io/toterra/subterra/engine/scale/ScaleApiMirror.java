package io.toterra.subterra.engine.scale;

import java.util.ArrayList;
import java.util.List;

/**
 * p.2.33.2 引擎侧镜像实现（api 定义形状 / engine 镜像实现范式，对应 {@code api.scale.ScaleApi}）：以
 * {@link ScaleType} / {@link ScaleData} / {@link ScaleRuleDocument} 的<b>真实常量</b>为唯一来源，暴露与
 * {@code api.scale.ScaleApi} 同语义的只读契约面——固定 9 维序、中性缺省缩放、维度名规范化/校验与 rule
 * td 字段序均同输入同输出（供 p.2.33.2 探针对照断言）。本镜像<em>不 import</em> api 包，仅消费 engine
 * 自身类型并把 api 契约值逐字导出，从而在两侧分别实例化后完全一致。
 * <p>确定性：全部方法为纯函数；{@link #normalizeDimension} 经 {@link ScaleType#fromForm} 逐字规范化、
 * 非法维度名确定性拒绝；{@link #isValidDimension} 仅判定不抛。常量来源（勿重猜）：{@link ScaleType}、
 * {@link ScaleData#get}、{@link ScaleRuleDocument#toTd}。
 * <p>
 * p.2.33.2 engine-side mirror implementation (api-shapes / engine-mirrors pattern, counterpart of
 * {@code api.scale.ScaleApi}): using the <b>actual constants</b> of {@link ScaleType} / {@link ScaleData} /
 * {@link ScaleRuleDocument} as the single source of truth, it exposes a read-only surface with the same
 * semantics as {@code api.scale.ScaleApi} — the fixed 9-dimension order, the neutral default scale, the
 * dimension-form normalization/validation and the rule td field order are all same-input-same-output (the
 * p.2.33.2 probe asserts both sides). This mirror does <em>not</em> import the api package; it consumes only
 * engine types and exports the api contract values verbatim, so instantiating both sides yields identical
 * results.
 * <p>Deterministic: every method is a pure function; {@link #normalizeDimension} canonicalizes verbatim via
 * {@link ScaleType#fromForm} with deterministic rejection of illegal dimension forms; {@link #isValidDimension}
 * only tests, never throws. Constant sources (do not re-guess): {@link ScaleType}, {@link ScaleData#get},
 * {@link ScaleRuleDocument#toTd}.
 */
public final class ScaleApiMirror {

    private ScaleApiMirror() {
    }

    /** The fixed 9-dimension order, sourced verbatim from {@link ScaleType#values()} order. /
     *  固定 9 维序，逐字源自 {@link ScaleType#values()} 序。 */
    public static List<String> dimensions() {
        List<String> out = new ArrayList<>(ScaleType.values().length);
        for (ScaleType t : ScaleType.values()) {
            out.add(t.form());
        }
        return List.copyOf(out);
    }

    /** The number of fixed dimensions, sourced from {@link ScaleType#values().length} ({@code 9}). /
     *  固定维度数，源自 {@link ScaleType#values().length}（{@code 9}）。 */
    public static int dimensionCount() {
        return ScaleType.values().length;
    }

    /** The neutral/vanilla root scale {@code 1.0}, sourced from {@link ScaleData#get}'s absent-dimension
     *  default. / 中性/原版根缩放 {@code 1.0}，源自 {@link ScaleData#get} 的未设置维度缺省。 */
    public static double neutralScale() {
        return 1.0D;
    }

    /**
     * Canonicalizes a dimension registration form via {@link ScaleType#fromForm} and returns the canonical
     * lowercase form — the same input→output as {@code api.scale.ScaleApi#normalizeDimension}. Deterministic;
     * an illegal form is rejected with {@link IllegalArgumentException}. / 经 {@link ScaleType#fromForm}
     * 规范化维度注册名并返回规范小写形式——与 {@code api.scale.ScaleApi#normalizeDimension} 同输入同输出。
     * 确定性；非法形式以 {@link IllegalArgumentException} 拒绝。
     *
     * @param form the dimension registration form (non-null).
     * @return the canonical lowercase dimension form.
     * @throws IllegalArgumentException if {@code form} is null or not one of the 9 fixed dimensions.
     */
    public static String normalizeDimension(String form) {
        return ScaleType.fromForm(form).form();
    }

    /** Whether {@code form} is one of the 9 fixed dimensions via {@link ScaleType#fromForm}
     *  (deterministic; never throws). / 经 {@link ScaleType#fromForm} 判定 {@code form} 是否为 9 个固定
     *  维度之一（确定性；绝不抛出）。 */
    public static boolean isValidDimension(String form) {
        try {
            ScaleType.fromForm(form);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /** The fixed per-entry td field order of {@link ScaleRuleDocument}, sourced verbatim from its
     *  {@code toTd()} write order ({@code type, tag, scale}). / {@link ScaleRuleDocument} 的固定每条目 td
     *  字段序，逐字源自其 {@code toTd()} 写出序（{@code type, tag, scale}）。 */
    public static String ruleFieldOrder() {
        return "type,tag,scale";
    }
}