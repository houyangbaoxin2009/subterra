package io.toterra.subterra.api.ui;

import java.util.List;

/**
 * p.2.33.5 对外的 UI/HUD 契约门面（final class、静态纯函数、纯 JDK）：确定性接口面，供上层/域模组按
 * 「确定性食物值计算 + 固定 tooltip 行序 + 固定书籍页类型序 + 固定模组元数据字段序」驱动 HUD/书籍/模组面板
 * 数据。语义与 {@code engine.ui} 的 {@code FoodValues}/{@code FoodTooltipRow}/{@code BookPage}/
 * {@code ModMetadata}（p.2.22.1/.2/.3）一致——本处为契约与数据面注入，engine 为实现镜像
 * （{@code engine.ui.UiApiMirror}），api 不依赖 engine。所有契约常量均系从 engine 实际常量/纯函数逐字对照
 * 落于此，禁止拍脑袋数值。
 * <p>确定性：{@link #foodSaturationIncrement} 为纯算术 {@code hunger * mod * 2f}，镜像
 * {@code FoodValues#getSaturationIncrement} 的精确表达式；{@link #foodShankCount} = {@code ceil(|hunger|/2)}，
 * 镜像 {@code FoodTooltipRow} 鸡腿数；{@link #tooltipRowOrder()} 返回固定行序
 * （{@code hunger, saturation, ratio}，镜像 {@code FoodTooltipRow#rows} 的写出序）；{@link #pageTypes()}
 * 返回固定书籍页类型序（{@code text, recipe, entity}，镜像 {@code BookPage.KNOWN_TYPES}）；
 * {@link #modFieldOrder()} 返回固定模组元数据字段序（{@code id, name, description, version, license}，镜像
 * {@code ModMetadata#toTd} 的写出序）。无随机、无墙钟；全部 O(1)、禁 O(n²)。
 * <p>
 * p.2.33.5 the external UI/HUD contract facade (final class, static pure functions, pure JDK): a deterministic
 * interface surface for upper layers / domain mods to drive HUD/book/mod-panel data from a "deterministic
 * food-value computation + fixed tooltip-row order + fixed book-page-type order + fixed mod-metadata field
 * order". Semantics match {@code engine.ui}'s {@code FoodValues}/{@code FoodTooltipRow}/{@code BookPage}/
 * {@code ModMetadata} (p.2.22.1/.2/.3) — this is the contract and data-injection surface for the engine to
 * mirror as its implementation ({@code engine.ui.UiApiMirror}), and the api does not depend on the engine.
 * Every contract constant is pinned verbatim from the engine's actual constants/pure functions — nothing is
 * guessed.
 * <p>Deterministic: {@link #foodSaturationIncrement} is the pure arithmetic {@code hunger * mod * 2f}, mirroring
 * the exact expression of {@code FoodValues#getSaturationIncrement}; {@link #foodShankCount} =
 * {@code ceil(|hunger|/2)}, mirroring the {@code FoodTooltipRow} drumstick count; {@link #tooltipRowOrder()}
 * returns the fixed row order ({@code hunger, saturation, ratio}, mirroring the write order of
 * {@code FoodTooltipRow#rows}); {@link #pageTypes()} returns the fixed book-page-type order ({@code text,
 * recipe, entity}, mirroring {@code BookPage.KNOWN_TYPES}); {@link #modFieldOrder()} returns the fixed
 * mod-metadata field order ({@code id, name, description, version, license}, mirroring the write order of
 * {@code ModMetadata#toTd}). No randomness, no wall-clock; all O(1), no O(n²).
 */
public final class UiApi {

    private UiApi() {
    }

    /** The fixed food-tooltip row order ({@code hunger, saturation, ratio}), mirroring
     *  {@code FoodTooltipRow#rows}. / 固定食物 tooltip 行序（{@code hunger, saturation, ratio}），镜像
     *  {@code FoodTooltipRow#rows}。 */
    public static final List<String> TOOLTIP_ROW_ORDER = List.of("hunger", "saturation", "ratio");

    /** The fixed book-page-type order ({@code text, recipe, entity}), mirroring {@code BookPage.KNOWN_TYPES}.
     *  / 固定书籍页类型序（{@code text, recipe, entity}），镜像 {@code BookPage.KNOWN_TYPES}。 */
    public static final List<String> PAGE_TYPES = List.of("text", "recipe", "entity");

    /** The fixed mod-metadata field order ({@code id, name, description, version, license}), mirroring
     *  {@code ModMetadata#toTd} write order. / 固定模组元数据字段序（{@code id, name, description, version,
     *  license}），镜像 {@code ModMetadata#toTd} 写出序。 */
    public static final List<String> MOD_FIELDS = List.of("id", "name", "description", "version", "license");

    /**
     * The saturation points added by eating: {@code hunger * saturationModifier * 2f}, the exact expression of
     * {@code engine.ui.FoodValues#getSaturationIncrement} (AppleSkin semantics). Pure and deterministic: identical
     * inputs yield identical float bits. / 进食增加的饱和度点：{@code hunger * saturationModifier * 2f}，即
     * {@code engine.ui.FoodValues#getSaturationIncrement} 的精确表达式（AppleSkin 语义）。纯且确定：同输入恒
     * 得同 float 位。
     *
     * @param hunger              restored hunger in half-shanks. 恢复的饥饿值（半鸡腿）。
     * @param saturationModifier  vanilla saturation ratio (finite). 原版饱和度比例（有限）。
     * @return {@code hunger * mod * 2f}. 饱和度增量。
     */
    public static float foodSaturationIncrement(int hunger, float saturationModifier) {
        return hunger * saturationModifier * 2f;
    }

    /**
     * The drumstick count shown by the upstream hunger overlay: {@code ceil(|hunger| / 2)} (1 drumstick = 2
     * hunger), matching {@code engine.ui.FoodTooltipRow#rows}'s hunger row. Pure and deterministic.
     * / 上游饥饿 overlay 显示的鸡腿数：{@code ceil(|hunger| / 2)}（1 鸡腿 = 2 饥饿），与
     * {@code engine.ui.FoodTooltipRow#rows} 的 hunger 行一致。纯且确定。
     *
     * @param hunger restored hunger in half-shanks. 恢复的饥饿值（半鸡腿）。
     * @return the drumstick count. 鸡腿数。
     */
    public static int foodShankCount(int hunger) {
        return (int) Math.ceil(Math.abs(hunger) / 2f);
    }

    /** The fixed food-tooltip row order ({@code hunger, saturation, ratio}). Deterministic. /
     *  固定食物 tooltip 行序（{@code hunger, saturation, ratio}）。确定性。 */
    public static List<String> tooltipRowOrder() {
        return TOOLTIP_ROW_ORDER;
    }

    /** The fixed book-page-type order ({@code text, recipe, entity}). Deterministic. /
     *  固定书籍页类型序（{@code text, recipe, entity}）。确定性。 */
    public static List<String> pageTypes() {
        return PAGE_TYPES;
    }

    /** The fixed mod-metadata field order ({@code id, name, description, version, license}). Deterministic.
     *  / 固定模组元数据字段序（{@code id, name, description, version, license}）。确定性。 */
    public static List<String> modFieldOrder() {
        return MOD_FIELDS;
    }
}