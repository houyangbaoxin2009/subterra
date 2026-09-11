package io.toterra.subterra.engine.scale;

import java.util.Iterator;
import java.util.Objects;

/**
 * A named multiplicative scale modifier. Applying a modifier multiplies every dimension
 * of a {@link ScaleData} by its factor, producing a full-dimension result; compositing
 * several modifiers in registration order via {@link #applyAll(Iterable, ScaleData)} is
 * deterministic — same inputs yield the same double bit pattern for every dimension
 * (double multiplication is order-sensitive across distinct modifiers, so the documented
 * fixed composition order is load-bearing).
 * <p>
 * See {@code META-INF/third-party/pehkui-rebuilt-3.8.5/NOTICE.md} for scale
 * representation semantics.
 *
 * <p>带名称的乘法缩放修饰符。应用修饰符即把 {@link ScaleData} 的每个维度乘以该因子，产出含全部
 * 维度的结果；经 {@link #applyAll(Iterable, ScaleData)} 按注册序组合多个修饰符是确定性的——同输入
 * 对每个维度恒得相同 double 位模式（跨不同修饰符的 double 乘法对顺序敏感，故文档化的固定组合序不可
 * 省略）。
 */
public record ScaleModifier(String name, double multiplier) {

    /** @throws IllegalArgumentException if name is null or the multiplier is not finite and > 0 */
    public ScaleModifier {
        if (name == null) {
            throw new IllegalArgumentException("name must not be null");
        }
        if (!Double.isFinite(multiplier) || multiplier <= 0.0D) {
            throw new IllegalArgumentException("multiplier must be finite and > 0: " + multiplier);
        }
    }

    /** Scales a single value multiplicatively. / 对单个取值做乘法缩放。 */
    public double modify(double value) {
        return value * multiplier;
    }

    /**
     * Applies this modifier to every dimension of {@code base} (in
     * {@link ScaleType#values() values()} order), returning a full-dimension
     * {@link ScaleData}. / 将此修饰符应用到 {@code base} 的每个维度（按
     * {@link ScaleType#values() values()} 序），返回含全部维度的 {@link ScaleData}。
     */
    public ScaleData apply(ScaleData base) {
        Objects.requireNonNull(base, "base must not be null");
        ScaleData result = base;
        for (ScaleType t : ScaleType.values()) {
            result = result.with(t, base.get(t) * multiplier);
        }
        return result;
    }

    /**
     * Folds {@code modifiers} in iteration (registration) order over {@code base},
     * each via {@link #apply(ScaleData)}. Deterministic for the same modifier sequence.
     * / 按迭代（注册）序将 {@code modifiers} 依次经 {@link #apply(ScaleData)} 折叠到
     * {@code base}。对同一修饰符序列确定性。
     */
    public static ScaleData applyAll(Iterable<ScaleModifier> modifiers, ScaleData base) {
        Objects.requireNonNull(modifiers, "modifiers must not be null");
        Objects.requireNonNull(base, "base must not be null");
        ScaleData result = base;
        Iterator<ScaleModifier> it = modifiers.iterator();
        while (it.hasNext()) {
            result = it.next().apply(result);
        }
        return result;
    }
}