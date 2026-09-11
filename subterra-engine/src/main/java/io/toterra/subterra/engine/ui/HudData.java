package io.toterra.subterra.engine.ui;

/**
 * The read-only player HUD data surface (p.2.22.1): current hunger, saturation
 * and exhaustion, mapped from upstream AppleSkin's {@code HUDOverlayHandler}
 * reads of the vanilla {@code HungerManager} — {@code getFoodLevel()} /
 * {@code getSaturationLevel()} / {@code getExhaustion()} (see
 * {@code META-INF/third-party/appleskin-3.0.6/NOTICE.md}). All values are pure
 * data; no Minecraft code is touched.
 *
 * <p>{@link #ratio()} computes the satiety ratio deterministically:
 * {@code clamp(saturation / 20f, 0f, 1f)} — the fraction of the (vanilla max-20)
 * saturation bar, clamped to [0, 1] so out-of-range inputs still yield a fixed
 * result. For reference, upstream's exhaustion overlay ratio is
 * {@code clamp(exhaustion / MAX_EXHAUSTION(4f), 0f, 1f)}.
 *
 * <p>玩家 HUD 只读数据面（p.2.22.1）：当前饥饿、饱和度与疲劳，映射上游 AppleSkin
 * {@code HUDOverlayHandler} 对原版 {@code HungerManager} 的读取——{@code getFoodLevel()} /
 * {@code getSaturationLevel()} / {@code getExhaustion()}（见
 * {@code META-INF/third-party/appleskin-3.0.6/NOTICE.md}）。全部为纯数据，不触碰 MC 代码。
 *
 * <p>{@link #ratio()} 确定性计算饱食度比例：{@code clamp(saturation / 20f, 0f, 1f)}——
 * 原版（最高 20）饱和度条的占比，clamp 到 [0, 1]，越界输入也得到固定结果。对照参考：上游
 * 疲劳 overlay 比例为 {@code clamp(exhaustion / MAX_EXHAUSTION(4f), 0f, 1f)}。
 *
 * @param hunger     current food level (vanilla 0..20). 当前饥饿值（原版 0..20）。
 * @param saturation current saturation level (vanilla 0..20, never above hunger).
 *                   当前饱和度（原版 0..20，不高于饥饿）。
 * @param exhaustion current exhaustion (vanilla 0..4). 当前疲劳（原版 0..4）。
 */
public record HudData(int hunger, int saturation, float exhaustion) {

    public HudData {
        if (!Float.isFinite(exhaustion)) {
            throw new IllegalArgumentException("exhaustion must be finite: " + exhaustion);
        }
    }

    /**
     * Satiety ratio: {@code clamp(saturation / 20f, 0f, 1f)} — deterministic
     * pure arithmetic. 饱食度比例：{@code clamp(saturation / 20f, 0f, 1f)}——确定性纯算术。
     */
    public float ratio() {
        return Math.min(1f, Math.max(0f, saturation / 20f));
    }
}
