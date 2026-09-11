package io.toterra.subterra.engine.ui;

/**
 * Food nutritional values (p.2.22.1, AppleSkin Unlicense port): the hunger a food
 * restores and its saturation modifier. Modeled verbatim on upstream
 * {@code squeek.appleskin.api.food.FoodValues}
 * (github.com/squeek502/AppleSkin, The Unlicense — license materials under
 * {@code META-INF/third-party/appleskin-3.0.6/NOTICE.md}):
 * <ul>
 *   <li>{@code hunger} is in half-shanks (vanilla hunger points; 1 shank = 2
 *       hunger, matching {@code FoodComponent.getHunger()}); negative values are
 *       legal and carry upstream's semantics (e.g. rotten / negative foods);</li>
 *   <li>{@code saturationModifier} is the vanilla saturation ratio
 *       ({@code FoodComponent.getSaturationModifier()});</li>
 *   <li>{@link #getSaturationIncrement()} reproduces upstream's
 *       {@code hunger * saturationModifier * 2f} — the saturation points (0..20
 *       scale) added by eating;</li>
 *   <li>{@link #withModifier(float)} derives a new value with the same hunger and
 *       the given modifier — the deterministic analog of upstream's
 *       {@code FoodValuesEvent} modified-value replacement.</li>
 * </ul>
 * As a canonical record, {@code equals}/{@code hashCode} compare {@code hunger}
 * exactly and {@code saturationModifier} via {@code Float.compare} (record
 * semantics — matches upstream's hand-written equals/hashCode bit-for-bit). All
 * computation is pure arithmetic with no randomness and no timing, so identical
 * inputs yield identical bits.
 *
 * <p>食物营养值（p.2.22.1，AppleSkin Unlicense 移植）：食物恢复的饥饿值与其饱和度比例。
 * 逐字建模上游 {@code squeek.appleskin.api.food.FoodValues}
 * （github.com/squeek502/AppleSkin，The Unlicense——许可材料见
 * {@code META-INF/third-party/appleskin-3.0.6/NOTICE.md}）：
 * <ul>
 *   <li>{@code hunger} 单位为半鸡腿（原版饥饿点；1 鸡腿 = 2 饥饿，与
 *       {@code FoodComponent.getHunger()} 一致）；允许负值并保有上游语义（如腐烂/负面食物）；</li>
 *   <li>{@code saturationModifier} 为原版饱和度比例
 *       （{@code FoodComponent.getSaturationModifier()}）；</li>
 *   <li>{@link #getSaturationIncrement()} 复刻上游 {@code hunger * saturationModifier * 2f}——
 *       进食增加的饱和度点（0..20 刻度）；</li>
 *   <li>{@link #withModifier(float)} 派生同 hunger、指定 modifier 的新值——上游
 *       {@code FoodValuesEvent} 修改值替换的确定性对应。</li>
 * </ul>
 * 作为规范 record，{@code equals}/{@code hashCode} 精确比较 {@code hunger}、以
 * {@code Float.compare} 比较 {@code saturationModifier}（record 语义——与上游手写
 * equals/hashCode 逐位一致）。全部计算为纯算术、无随机无时序，同输入恒得同字节。
 *
 * @param hunger              restored hunger in half-shanks. 恢复的饥饿值（半鸡腿）。
 * @param saturationModifier  vanilla saturation ratio. 原版饱和度比例。
 */
public record FoodValues(int hunger, float saturationModifier) {

    public FoodValues {
        if (!Float.isFinite(saturationModifier)) {
            throw new IllegalArgumentException("saturationModifier must be finite: " + saturationModifier);
        }
    }

    /**
     * The saturation points added by eating — upstream's
     * {@code hunger * saturationModifier * 2f}, verbatim (deterministic).
     * 进食增加的饱和度点——逐字复刻上游 {@code hunger * saturationModifier * 2f}（确定性）。
     */
    public float getSaturationIncrement() {
        return hunger * saturationModifier * 2f;
    }

    /**
     * A new {@link FoodValues} with the same hunger and the given modifier —
     * deterministic derivative used to model "modified" food values (upstream's
     * {@code FoodValuesEvent} replacement).
     * 同 hunger、给定 modifier 的新 {@link FoodValues}——用于建模"修改后"食物值的确定性派生
     * （上游 {@code FoodValuesEvent} 的替换）。
     *
     * @param modifier the replacement saturation modifier (finite).
     *                 替换的饱和度比例（有限）。
     * @return the derived value. 派生值。
     */
    public FoodValues withModifier(float modifier) {
        return new FoodValues(hunger, modifier);
    }
}
