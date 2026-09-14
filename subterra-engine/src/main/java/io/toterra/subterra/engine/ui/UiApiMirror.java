package io.toterra.subterra.engine.ui;

import java.util.List;

/**
 * p.2.33.5 引擎侧镜像实现（api 定义形状 / engine 镜像实现范式，对应 {@code api.ui.UiApi}）：以
 * {@link FoodValues} / {@link FoodTooltipRow} / {@link BookPage} / {@link ModMetadata} 的<b>真实常量与纯函数
 * </b>为唯一来源，暴露与 {@code api.ui.UiApi} 同语义的只读契约面——确定性食物值计算、固定 tooltip 行序、
 * 固定书籍页类型序与固定模组元数据字段序均同输入同输出（供 p.2.33.5 探针对照断言）。本镜像<em>不 import</em>
 * api 包，仅消费 engine 自身类型并把 api 契约值逐字导出，从而在两侧分别实例化后完全一致。
 * <p>确定性：全部方法为纯函数；{@link #foodSaturationIncrement} 直接求 {@link FoodValues#getSaturationIncrement}
 * 的精确表达式；{@link #foodShankCount} 经 {@link FoodTooltipRow#rows} 的 hunger 行导出；
 * {@link #tooltipRowOrder}/{@link #pageTypes}/{@link #modFieldOrder} 逐字来自 engine 常量的固定写出序。
 * 常量来源（勿重猜）：{@link FoodValues}、{@link FoodTooltipRow#rows}、{@link BookPage#KNOWN_TYPES}、
 * {@link ModMetadata#toTd}。
 * <p>
 * p.2.33.5 engine-side mirror implementation (api-shapes / engine-mirrors pattern, counterpart of
 * {@code api.ui.UiApi}): using the <b>actual constants and pure functions</b> of {@link FoodValues} /
 * {@link FoodTooltipRow} / {@link BookPage} / {@link ModMetadata} as the single source of truth, it exposes a
 * read-only surface with the same semantics as {@code api.ui.UiApi} — the deterministic food-value computation,
 * fixed tooltip-row order, fixed book-page-type order and fixed mod-metadata field order are all
 * same-input-same-output (the p.2.33.5 probe asserts both sides). This mirror does <em>not</em> import the api
 * package; it consumes only engine types and exports the api contract values verbatim, so instantiating both
 * sides yields identical results.
 * <p>Deterministic: every method is a pure function; {@link #foodSaturationIncrement} evaluates the exact
 * expression of {@link FoodValues#getSaturationIncrement}; {@link #foodShankCount} is derived through
 * {@link FoodTooltipRow#rows}'s hunger row; {@link #tooltipRowOrder}/{@link #pageTypes}/{@link #modFieldOrder}
 * are read verbatim from the engine constants' fixed write orders. Constant sources (do not re-guess):
 * {@link FoodValues}, {@link FoodTooltipRow#rows}, {@link BookPage#KNOWN_TYPES}, {@link ModMetadata#toTd}.
 */
public final class UiApiMirror {

    private UiApiMirror() {
    }

    /** The saturation points added by eating, sourced verbatim from {@link FoodValues#getSaturationIncrement}
     *  ({@code hunger * saturationModifier * 2f}). / 进食增加的饱和度点，逐字源自
     *  {@link FoodValues#getSaturationIncrement}（{@code hunger * saturationModifier * 2f}）。 */
    public static float foodSaturationIncrement(int hunger, float saturationModifier) {
        return new FoodValues(hunger, saturationModifier).getSaturationIncrement();
    }

    /** The drumstick count of the upstream hunger overlay, sourced from {@link FoodTooltipRow#rows}'s hunger
     *  row ({@code ceil(|hunger| / 2)}). / 上游饥饿 overlay 的鸡腿数，源自 {@link FoodTooltipRow#rows} 的
     *  hunger 行（{@code ceil(|hunger| / 2)}）。 */
    public static int foodShankCount(int hunger) {
        return Integer.parseInt(FoodTooltipRow.rows(new FoodValues(hunger, 1f)).get(0).value());
    }

    /** The fixed food-tooltip row order, sourced from {@link FoodTooltipRow#rows}'s write order
     *  ({@code hunger, saturation, ratio}). / 固定食物 tooltip 行序，源自 {@link FoodTooltipRow#rows} 的写出序
     *  （{@code hunger, saturation, ratio}）。 */
    public static List<String> tooltipRowOrder() {
        return FoodTooltipRow.rows(new FoodValues(1, 1f)).stream().map(FoodTooltipRow::label).toList();
    }

    /** The fixed book-page-type order, sourced verbatim from {@link BookPage#KNOWN_TYPES}
     *  ({@code text, recipe, entity}). / 固定书籍页类型序，逐字源自 {@link BookPage#KNOWN_TYPES}
     *  （{@code text, recipe, entity}）。 */
    public static List<String> pageTypes() {
        return List.copyOf(BookPage.KNOWN_TYPES);
    }

    /** The fixed mod-metadata field order, sourced from {@link ModMetadata#toTd}'s write order ({@code id, name,
     *  description, version, license}). / 固定模组元数据字段序，源自 {@link ModMetadata#toTd} 的写出序
     *  （{@code id, name, description, version, license}）。 */
    public static List<String> modFieldOrder() {
        return List.of("id", "name", "description", "version", "license");
    }
}