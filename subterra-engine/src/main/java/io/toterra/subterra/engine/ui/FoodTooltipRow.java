package io.toterra.subterra.engine.ui;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;

/**
 * A single food-tooltip data row: a fixed label and its deterministic text value
 * (p.2.22.1). Upstream AppleSkin renders the food tooltip as icon-bar overlays —
 * a drumstick row plus a saturation row — with no text rows; this class is the
 * original clean-room text-row shape carrying the same data (semantics below are
 * taken from upstream's {@code TooltipOverlayHandler}; see
 * {@code META-INF/third-party/appleskin-3.0.6/NOTICE.md}).
 *
 * <p>Rows from {@link #rows(FoodValues)} have a fixed order
 * ({@code hunger}, {@code saturation}, {@code ratio}):
 * <ol>
 *   <li>{@code "hunger"} — the drumstick count shown by the upstream overlay,
 *       {@code ceil(|hunger| / 2)} (1 drumstick = 2 hunger points);</li>
 *   <li>{@code "saturation"} — the saturation increment
 *       ({@link FoodValues#getSaturationIncrement()}), one decimal place,
 *       HALF_UP, locale-independent;</li>
 *   <li>{@code "ratio"} — clean-room extension row: saturation increment per
 *       hunger point ({@code increment / hunger}, {@code 0.0} when
 *       {@code hunger == 0}), one decimal place — upstream advertises a
 *       "saturation ratio" for food comparison but computes no such row.</li>
 * </ol>
 *
 * <p>单个食物 tooltip 数据行：固定 label 与其确定性文本值（p.2.22.1）。上游 AppleSkin 以
 * 图标条 overlay 渲染食物 tooltip——一行鸡腿加一行饱和度条，无文本行；本类为原创 clean-room
 * 文本行形态，承载相同数据（以下语义取自上游 {@code TooltipOverlayHandler}，见
 * {@code META-INF/third-party/appleskin-3.0.6/NOTICE.md}）。
 *
 * <p>{@link #rows(FoodValues)} 产出的行固定序（{@code hunger}、{@code saturation}、
 * {@code ratio}）：
 * <ol>
 *   <li>{@code "hunger"}——上游 overlay 显示的鸡腿数，{@code ceil(|hunger| / 2)}
 *       （1 鸡腿 = 2 饥饿点）；</li>
 *   <li>{@code "saturation"}——饱和度增量（{@link FoodValues#getSaturationIncrement()}），
 *       一位小数，HALF_UP，与语言环境无关；</li>
 *   <li>{@code "ratio"}——clean-room 扩展行：每点饥饿对应的饱和度增量
 *       （{@code increment / hunger}，{@code hunger == 0} 时为 {@code 0.0}），一位小数——
 *       上游宣传"饱和度比值"用于食物比较，但并无该行的计算。</li>
 * </ol>
 *
 * @param label the fixed row label. 固定行标签。
 * @param value the deterministic text value. 确定性文本值。
 */
public record FoodTooltipRow(String label, String value) {

    public FoodTooltipRow {
        Objects.requireNonNull(label, "label must be non-null");
        Objects.requireNonNull(value, "value must be non-null");
    }

    /**
     * The fixed-order tooltip rows for the given food values:
     * {@code [hunger, saturation, ratio]} (semantics of each row in the class
     * javadoc). The returned list is immutable and its iteration order is fixed.
     * 给定食物值的固定序 tooltip 行：{@code [hunger, saturation, ratio]}（各行语义见类注释）。
     * 返回列表不可变，迭代序固定。
     *
     * @param values the food values, non-null. 食物营养值，非空。
     * @return the three rows in fixed order. 固定序的三行。
     */
    public static List<FoodTooltipRow> rows(FoodValues values) {
        Objects.requireNonNull(values, "values must be non-null");
        float increment = values.getSaturationIncrement();
        return List.of(
                new FoodTooltipRow("hunger", Integer.toString(shanks(values.hunger()))),
                new FoodTooltipRow("saturation", formatOneDecimal(increment)),
                new FoodTooltipRow("ratio", formatOneDecimal(ratio(values, increment))));
    }

    /**
     * Drumstick count of the upstream hunger overlay: {@code ceil(|hunger| / 2)}.
     * 上游饥饿 overlay 的鸡腿数：{@code ceil(|hunger| / 2)}。
     */
    private static int shanks(int hunger) {
        return (int) Math.ceil(Math.abs(hunger) / 2f);
    }

    /**
     * Saturation increment per hunger point; {@code 0.0} for {@code hunger == 0}
     * (clean-room row, see class javadoc). 每点饥饿的饱和度增量；{@code hunger == 0}
     * 时为 {@code 0.0}（clean-room 行，见类注释）。
     */
    private static float ratio(FoodValues values, float increment) {
        return values.hunger() == 0 ? 0f : increment / values.hunger();
    }

    /**
     * One decimal place, HALF_UP, locale-independent — deterministic for equal
     * float inputs. 一位小数，HALF_UP，与语言环境无关——等输入恒等输出。
     */
    static String formatOneDecimal(float f) {
        return BigDecimal.valueOf(f).setScale(1, RoundingMode.HALF_UP).toPlainString();
    }
}
