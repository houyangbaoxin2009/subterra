package io.toterra.subterra.engine.ui;

import java.util.List;
import java.util.Objects;

/**
 * Hunger/saturation restore overlay data (p.2.22.1, optional): the net hunger and
 * saturation a food would restore at the player's current level, mapped from
 * upstream AppleSkin's {@code HUDOverlayHandler} "held-food" overlay — the new
 * food level is {@code min(20, foodLevel + hunger)}, the new saturation is
 * clamped to the new food level (vanilla rule: saturation never exceeds hunger).
 * See {@code META-INF/third-party/appleskin-3.0.6/NOTICE.md}.
 *
 * <p>饱食/饱和度恢复覆盖数据（p.2.22.1，可选）：在当前玩家数值下食物净恢复的饥饿与饱和度，
 * 映射上游 AppleSkin {@code HUDOverlayHandler} 的"手持食物"overlay——新饥饿值为
 * {@code min(20, foodLevel + hunger)}，新饱和度 clamp 到新饥饿值（原版规则：饱和度不高于
 * 饥饿）。见 {@code META-INF/third-party/appleskin-3.0.6/NOTICE.md}。
 *
 * @param hungerRestored     net hunger restored (clamped ≥ 0). 净恢复饥饿（clamp ≥ 0）。
 * @param saturationRestored net saturation restored (clamped ≥ 0). 净恢复饱和度（clamp ≥ 0）。
 */
public record FoodOverlay(int hungerRestored, float saturationRestored) {

    public FoodOverlay {
        if (!Float.isFinite(saturationRestored)) {
            throw new IllegalArgumentException("saturationRestored must be finite: " + saturationRestored);
        }
    }

    /**
     * Unclamped values straight from the food itself: its hunger and its
     * saturation increment. 食物本身的裸值：其饥饿与其饱和度增量（未 clamp）。
     *
     * @param values the food values, non-null. 食物营养值，非空。
     * @return the unclamped overlay. 未 clamp 的覆盖。
     */
    public static FoodOverlay of(FoodValues values) {
        Objects.requireNonNull(values, "values must be non-null");
        return new FoodOverlay(values.hunger(), values.getSaturationIncrement());
    }

    /**
     * Fixed-order restore rows ({@code [hungerRestored, saturationRestored]}) at
     * the given current level, with AppleSkin's clamping semantics. 在给定当前数值下
     * 的固定序恢复行（{@code [hungerRestored, saturationRestored]}），采用 AppleSkin 的
     * clamp 语义。
     *
     * @param values            the food values, non-null. 食物营养值，非空。
     * @param currentFoodLevel  the player's current food level. 玩家当前饥饿值。
     * @param currentSaturation the player's current saturation. 玩家当前饱和度。
     * @return the two rows in fixed order. 固定序的两行。
     */
    public static List<FoodTooltipRow> rows(FoodValues values, int currentFoodLevel, float currentSaturation) {
        Objects.requireNonNull(values, "values must be non-null");
        int newFoodLevel = Math.min(20, currentFoodLevel + values.hunger());
        int hungerRestored = Math.max(0, newFoodLevel - currentFoodLevel);
        float newSaturation = Math.min(newFoodLevel, currentSaturation + values.getSaturationIncrement());
        float saturationRestored = Math.max(0f, newSaturation - currentSaturation);
        return List.of(
                new FoodTooltipRow("hungerRestored", Integer.toString(hungerRestored)),
                new FoodTooltipRow("saturationRestored", FoodTooltipRow.formatOneDecimal(saturationRestored)));
    }
}
