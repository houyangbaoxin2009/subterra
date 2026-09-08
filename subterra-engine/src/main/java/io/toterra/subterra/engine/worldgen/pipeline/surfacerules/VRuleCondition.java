package io.toterra.subterra.engine.worldgen.pipeline.surfacerules;

import io.toterra.subterra.engine.worldgen.pipeline.density.Density;

import java.util.Objects;

/**
 * Vanilla {@code SurfaceRules} condition vocabulary (p.1.8.15, clean-room): a
 * pure predicate {@code VSurfaceContext -> boolean}, mirroring the MC 1.21.1
 * {@code ConditionSource}/{@code Condition} seam. Factory methods pin the exact
 * semantics verified against the 1.21.1 bytecode:
 * <ul>
 *   <li>y-condition bounds are inclusive on {@code [min, max]};</li>
 *   <li>stone-depth is true while within {@code 1 + offset + (addSurfaceDepth?surfaceDepth:0) + secondaryDepth}
 *       layers of the floor ({@code stoneDepthAbove}) or ceiling ({@code stoneDepthBelow});</li>
 *   <li>steep compares neighbouring column surface heights with a {@code +4} offset;</li>
 *   <li>noise-threshold evaluates the noise at {@code (x, 0, z)} and is true when
 *       {@code min <= value <= max} (inclusive);</li>
 *   <li>not negates, biome-match is an OR over names/tags;</li>
 *   <li>water-block is true when there is no water source or the block is at/above the
 *       adjusted water line.</li>
 * </ul>
 * All conditions are O(1) and free of randomness.
 * <p>
 * vanilla {@code SurfaceRules} 条件词汇（p.1.8.15，洁净房）：纯谓词
 * {@code VSurfaceContext -> boolean}，对应 MC 1.21.1 {@code ConditionSource}/{@code Condition}。
 * 工厂方法钉住了经 1.21.1 字节码核验的精确语义（见英文列表）。全部条件 O(1) 且无随机。
 */
@FunctionalInterface
public interface VRuleCondition {

    /** Tests the condition for the given extended context. */
    boolean test(VSurfaceContext c);

    /** Constant true. 恒真。 */
    static VRuleCondition alwaysTrue() {
        return c -> true;
    }

    /** Constant false. 恒假。 */
    static VRuleCondition alwaysFalse() {
        return c -> false;
    }

    /** Negation of {@code c} (mirrors mc {@code Not}). 取反。 */
    static VRuleCondition not(VRuleCondition c) {
        Objects.requireNonNull(c, "c");
        return ctx -> !c.test(ctx);
    }

    /** Conjunction; empty list is true. 合取；空列表为真。 */
    static VRuleCondition and(VRuleCondition... all) {
        return ctx -> {
            for (VRuleCondition cond : all) {
                if (!cond.test(ctx)) {
                    return false;
                }
            }
            return true;
        };
    }

    /** Disjunction; empty list is false. 析取；空列表为假。 */
    static VRuleCondition or(VRuleCondition... any) {
        return ctx -> {
            for (VRuleCondition cond : any) {
                if (cond.test(ctx)) {
                    return true;
                }
            }
            return false;
        };
    }

    /**
     * Y-condition: true when {@code min <= y <= max} (inclusive both edges).
     * Y 条件：{@code min <= y <= max}（两端含）。
     */
    static VRuleCondition yRange(int min, int max) {
        if (min > max) {
            throw new IllegalArgumentException("yRange bounds inverted: min=" + min + " max=" + max);
        }
        return c -> c.y() >= min && c.y() <= max;
    }

    /** True when {@code y >= min} (inclusive). 当 {@code y >= min}（含）时为真。 */
    static VRuleCondition aboveY(int min) {
        return c -> c.y() >= min;
    }

    /** True when {@code y < max}. 当 {@code y < max} 时为真。 */
    static VRuleCondition belowY(int max) {
        return c -> c.y() < max;
    }

    /**
     * Biome match: true when the context biome tag equals any of the given
     * names/tags (OR). Each entry is a literal string, {@code null}-safe.
     * 群系匹配：上下文的群系标签等于任一给定名称/标签（析取）时为真。逐字比对、空安全。
     */
    static VRuleCondition biomeMatch(String... names) {
        Objects.requireNonNull(names, "names");
        return c -> {
            String tag = c.biomeTag();
            if (tag == null) {
                return false;
            }
            for (String n : names) {
                if (tag.equals(n)) {
                    return true;
                }
            }
            return false;
        };
    }

    /** True when {@code density > threshold} (strictly greater). 密度严格大于阈值。 */
    static VRuleCondition densityAbove(double threshold) {
        return c -> c.density() > threshold;
    }

    /**
     * Noise-threshold condition: evaluates {@code densityField} at
     * {@code (x, 0, z)} (mirroring the XZ noise {@code getValue}) and is true when
     * {@code min <= value <= max} (inclusive both edges). For a single-valued
     * threshold pass {@code min == max}.
     * 噪声阈值条件：在 {@code (x, 0, z)} 求 {@code densityField}（对应 XZ 噪声 {@code getValue}），
     * {@code min <= value <= max}（两端含）时为真。
     */
    static VRuleCondition noiseThreshold(Density densityField, double min, double max) {
        Objects.requireNonNull(densityField, "densityField");
        if (min > max) {
            throw new IllegalArgumentException("noiseThreshold bounds inverted: min=" + min + " max=" + max);
        }
        return c -> {
            double v = densityField.eval(c.x(), 0.0, c.z());
            return v >= min && v <= max;
        };
    }

    /** Single-valued noise-threshold (requires {@code value == threshold}). 单值噪声阈值。 */
    static VRuleCondition noiseThreshold(Density densityField, double threshold) {
        return noiseThreshold(densityField, threshold, threshold);
    }

    /**
     * Steep condition (mirrors MC {@code Context$SteepMaterialCondition}): compares
     * the neighbour column surface heights with a {@code +4} offset:
     * {@code h(x, z+1) >= h(x, z-1) + 4} or {@code h(x-1, z) >= h(x+1, z) + 4}.
     * The surface must rise at least 4 blocks in one column; fully flat columns
     * (and columns without a height provider) report false.
     * 陡坡条件（对应 MC {@code Context$SteepMaterialCondition}）：以 {@code +4} 偏移比较相邻列
     * 表面高度 {@code h(x, z+1) >= h(x, z-1) + 4} 或 {@code h(x-1, z) >= h(x+1, z) + 4}。
     * 表面须在一列内抬升至少 4 格；完全平坦的列（及无高度提供者的列）判定为否。
     */
    static VRuleCondition steep() {
        return c -> {
            VSurfaceContext.HeightAt h = c.surfaceHeightAt();
            if (h == null) {
                return false;
            }
            int zNeg = Math.max(c.z() - 1, 0);
            int zPos = Math.min(c.z() + 1, 15);
            if (h.height(c.x(), zPos) >= h.height(c.x(), zNeg) + 4) {
                return true;
            }
            int xNeg = Math.max(c.x() - 1, 0);
            int xPos = Math.min(c.x() + 1, 15);
            return h.height(xNeg, c.z()) >= h.height(xPos, c.z()) + 4;
        };
    }

    /**
     * Stone-depth condition ({@code StoneDepthCheck}): true while within
     * {@code 1 + offset + (addSurfaceDepth ? surfaceDepth : 0) + secondary}
     * layers of the {@code FLOOR} ({@code stoneDepthAbove}) or {@code CEILING}
     * ({@code stoneDepthBelow}) surface, where {@code secondary} maps
     * {@code surfaceSecondary} from [-1, 1] onto {@code [0, secondaryDepthRange]}
     * when that range is nonzero.
     * 岩层深度条件（{@code StoneDepthCheck}）：当处于 {@code FLOOR}（{@code stoneDepthAbove}）或
     * {@code CEILING}（{@code stoneDepthBelow}）表面 {@code 1 + offset + (addSurfaceDepth ? surfaceDepth : 0) + secondary}
     * 层内为真；其中 {@code secondary} 在 {@code secondaryDepthRange} 非零时把 {@code surfaceSecondary}
     * 从 [-1, 1] 映射到 [0, range]。
     */
    static VRuleCondition stoneDepth(int offset, boolean addSurfaceDepth, int secondaryDepthRange, CaveSurface surfaceType) {
        Objects.requireNonNull(surfaceType, "surfaceType");
        return c -> {
            int depth = (surfaceType == CaveSurface.CEILING) ? c.stoneDepthBelow() : c.stoneDepthAbove();
            int addDepth = addSurfaceDepth ? c.surfaceDepth() : 0;
            int secondary = 0;
            if (secondaryDepthRange != 0) {
                secondary = (int) map01(c.surfaceSecondary(), secondaryDepthRange);
            }
            return depth <= 1 + offset + addDepth + secondary;
        };
    }

    /** Convenience: floor-side stone depth without secondary variation. 便捷：地面侧岩层深度。 */
    static VRuleCondition onFloorStoneDepth(int depth) {
        return stoneDepth(depth, true, 0, CaveSurface.FLOOR);
    }

    /**
     * Water-block condition ({@code WaterConditionSource}(offset, surfaceDepthMultiplier, addStoneDepth)):
     * true when the column has no water source ({@link VSurfaceContext#NO_WATER}) or when
     * {@code y + (addStoneDepth ? stoneDepthAbove : 0) >= waterHeight + offset + surfaceDepth * surfaceDepthMultiplier}.
     * 水体块条件（{@code WaterConditionSource}）：当该列无水（{@link VSurfaceContext#NO_WATER}）或
     * {@code y + (addStoneDepth ? stoneDepthAbove : 0) >= waterHeight + offset + surfaceDepth * surfaceDepthMultiplier} 时为真。
     */
    static VRuleCondition waterBlock(int offset, int surfaceDepthMultiplier, boolean addStoneDepth) {
        return c -> {
            int wh = c.waterHeight();
            if (wh == VSurfaceContext.NO_WATER) {
                return true;
            }
            int y = c.y() + (addStoneDepth ? c.stoneDepthAbove() : 0);
            return y >= wh + offset + c.surfaceDepth() * surfaceDepthMultiplier;
        };
    }

    /** Convenience: default water-block condition (offset 0, no multiplier, no stone depth). */
    static VRuleCondition water() {
        return waterBlock(0, 0, false);
    }

    /**
     * Vertical-gradient condition ({@code VerticalGradientConditionSource}): true with a
     * probability that ramps linearly from {@code trueAtAndBelow} (always true) to
     * {@code falseAtAndAbove} (always false). The random draw is a clean-room
     * deterministic hash of {@code randomName} and {@code (x, z)} in {@code [0, 1)}
     * (a stand-in for MC's {@code PositionalRandomFactory}), so results are reproducible.
     * 垂直渐变条件（{@code VerticalGradientConditionSource}）：以从 {@code trueAtAndBelow}（恒真）到
     * {@code falseAtAndAbove}（恒假）线性抬升的概率为真。随机抽取为 {@code randomName} 与 {@code (x, z)}
     * 的洁净房确定性哈希（替代 MC {@code PositionalRandomFactory}），故结果可复现。
     */
    static VRuleCondition verticalGradient(String randomName, int trueAtAndBelow, int falseAtAndAbove) {
        Objects.requireNonNull(randomName, "randomName");
        if (trueAtAndBelow > falseAtAndAbove) {
            throw new IllegalArgumentException(
                    "verticalGradient anchors inverted: trueAtAndBelow=" + trueAtAndBelow + " falseAtAndAbove=" + falseAtAndAbove);
        }
        return c -> {
            if (c.y() <= trueAtAndBelow) {
                return true;
            }
            if (c.y() >= falseAtAndAbove) {
                return false;
            }
            double ratio = (double) (c.y() - trueAtAndBelow) / (double) (falseAtAndAbove - trueAtAndBelow);
            double draw = hashUnit(randomName, c.x(), c.z());
            return draw < ratio;
        };
    }

    /** Hole condition (mirrors MC {@code Context$HoleCondition}): true when {@code surfaceDepth <= 0}. */
    static VRuleCondition hole() {
        return c -> c.surfaceDepth() <= 0;
    }

    /**
     * Above-preliminary-surface condition (mirrors MC {@code AbovePreliminarySurface}):
     * true when the block sits at or above the preliminary surface level, modelled here as
     * the column surface height ({@code 0} on flat ground without a provider).
     * 初步表面之上条件（对应 MC {@code AbovePreliminarySurface}）：本块处于或高于初步表面时为真，
     * 此处以列表面高度建模。
     */
    static VRuleCondition abovePreliminarySurface() {
        return c -> c.y() >= c.surfaceHeight();
    }

    /** Maps {@code [-1, 1]} onto {@code [0, range]}, clamped, without {@code Mth}. */
    private static int map01(double v, int range) {
        double clamped = Math.max(-1.0, Math.min(1.0, v));
        return (int) Math.floor((clamped + 1.0) / 2.0 * range);
    }

    /** Deterministic hash of {@code name + x + z} into {@code [0, 1)}. */
    private static double hashUnit(String name, int x, int z) {
        long h = 0x9E3779B97F4A7C15L;
        for (int i = 0; i < name.length(); i++) {
            h ^= (name.charAt(i) & 0xFFL) * 0x100000001B3L;
            h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
            h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
        }
        h ^= (long) x * 0x100000001B3L;
        h ^= (long) z * 0xC6A4A7935BD1E995L;
        h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
        h &= 0x7FFFFFFFFFFFFFFFL;
        return (double) h / (double) Long.MAX_VALUE;
    }
}