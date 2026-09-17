package io.toterra.subterra.runtime.client.sky.mixin;

import io.toterra.subterra.api.sky.SkyApi;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.OptionalDouble;

/**
 * p.2.0.13 云层高度覆盖接线（Sky API 的 runtime 渲染侧消费端，client-only）：把
 * {@code LevelRenderer.renderClouds} 里唯一的云高取值调用
 * ({@code DimensionSpecialEffects#getCloudHeight()}) 重定向到 {@code api.sky.SkyApi} 的按维度覆盖表——
 * 当前渲染维度带覆盖（含 {@code NaN} = 隐藏云层）即取覆盖值，否则回落原版实例原值。
 * <p>确定性/诚实界限：只改一行取值调用（vanilla 云形/颜色/漂移常量不动）；维度键取
 * {@code level.dimension().location()} 的 {@code namespace:path} 字符串形式，与
 * {@code SkyApi#normalizeDimensionId} 的规范化形式对齐（小写、同序）。无覆盖时行为与 vanilla 逐位一致
 * （直接透传原方法返回值）。{@code defaultRequire = 0}（软失败）：重定向缺失只降级为不生效，不崩游戏。
 * <p>
 * p.2.0.13 cloud-height override wiring (the Sky API's runtime render-side consumer, client-only): it
 * redirects the single cloud-height lookup inside {@code LevelRenderer.renderClouds}
 * ({@code DimensionSpecialEffects#getCloudHeight()}) to the {@code api.sky.SkyApi} per-dimension override
 * table — when the currently rendered dimension carries an override (including {@code NaN} = hide clouds)
 * the override wins, otherwise the vanilla instance value falls through.
 * <p>Determinism / honest boundary: only the one height-lookup call is touched (vanilla cloud shape/color/
 * drift constants untouched); the dimension key is {@code level.dimension().location()}'s
 * {@code namespace:path} string form, aligned with {@code SkyApi#normalizeDimensionId}'s canonical form
 * (lowercase, same order). With no override the behaviour is bitwise vanilla (the original return value is
 * passed through). {@code defaultRequire = 0} (soft fail): a missing redirect degrades to inert, never
 * crashes the game.
 */
@Mixin(LevelRenderer.class)
public abstract class CloudHeightMixin {

    @Shadow
    private ClientLevel level;

    private CloudHeightMixin() {
    }

    /**
     * The redirect itself guards the shadowed level for null before dereferencing, so degraded paths never
     * NPE. / 重定向本体在解引用前对 shadowed level 判空，降级路径不产生 NPE。
     */
    @Redirect(
            method = "renderClouds",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/DimensionSpecialEffects;getCloudHeight()F"
            ),
            require = 0
    )
    private float subterra$cloudHeightOverride(DimensionSpecialEffects effects) {
        if (this.level != null) {
            String dimensionId = this.level.dimension().location().toString();
            OptionalDouble override = SkyApi.cloudHeight(dimensionId);
            if (override.isPresent()) {
                return (float) override.getAsDouble();
            }
        }
        return effects.getCloudHeight();
    }
}
