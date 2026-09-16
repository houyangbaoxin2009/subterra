package io.toterra.subterra.runtime.worldgen.gen;

import java.util.Map;
import java.util.Objects;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.SurfaceRules;

/**
 * p.2.29.2 表面规则接缝的包装 rule-source：{@code {"type":"subterra:surface_palette","base":<rule>}}。
 * 求值时先委派 {@code base} 的 {@link SurfaceRules.RuleSource#apply}，再把产出的
 * {@link BlockState} 按当前材质重绑表确定性替换（材质面 palette）。无重绑表时<b>恒等</b>：直接返回
 * {@code base} 自己的 {@code SurfaceRule} 对象，逐位不变、零逐块开销。
 * <p>
 * 访问控制说明：MC 1.21.1 把 {@code SurfaceRules.Context} 与 {@code SurfaceRules.SurfaceRule} 声明为
 * {@code SurfaceRules} 的 {@code protected} 嵌套类型（这是原版有意为之：只允许包内实现 rule source）。
 * 本类<b>继承 {@code SurfaceRules}</b>（一个只有静态成员与公开无参构造的持有类）以获得这两个受保护
 * 嵌套类型的合法访问，从而在不使用 mixin、不使用 access-transformer、不做包注入的前提下实现
 * {@link SurfaceRules.RuleSource}。这是框架侧「补缝」的显式手段。
 * <p>
 * p.2.29.2 the wrapping rule source of the surface-rule seam:
 * {@code {"type":"subterra:surface_palette","base":<rule>}}. On evaluation it first delegates to
 * {@code base}'s {@link SurfaceRules.RuleSource#apply} and then deterministically rebinds the produced
 * {@link BlockState} through the active material rebind table (the surface palette). With an empty
 * rebind table it is <b>identity</b>: it hands back {@code base}'s own {@code SurfaceRule} object —
 * bit-for-bit unchanged, zero per-block cost.
 * <p>
 * Access-control note: MC 1.21.1 declares {@code SurfaceRules.Context} and
 * {@code SurfaceRules.SurfaceRule} as {@code protected} nested types of {@code SurfaceRules} (this is
 * deliberate: only in-package rule sources are meant to be implemented). This class
 * <b>extends {@code SurfaceRules}</b> (a holder class with only static members and a public no-arg
 * constructor) to obtain lawful access to those two protected nested types, implementing
 * {@link SurfaceRules.RuleSource} without a mixin, without an access transformer and without package
 * injection — the explicit framework-side gap fill.
 */
public final class PaletteRuleSource extends SurfaceRules implements SurfaceRules.RuleSource {

    /** {@code { "base": <rule source> }}, the body of the {@code subterra:surface_palette} JSON. /
     *  {@code { "base": <rule source> }}，{@code subterra:surface_palette} JSON 的体。 */
    public static final MapCodec<PaletteRuleSource> DATA_CODEC =
            RecordCodecBuilder.mapCodec(instance -> instance.group(
                    SurfaceRules.RuleSource.CODEC.fieldOf("base")
                            .forGetter(PaletteRuleSource::base)
            ).apply(instance, PaletteRuleSource::new));

    /** The key-dispatched codec; its {@code MapCodec} is what we register. /
     *  键派发编解码器；其 {@code MapCodec} 是注册的内容。 */
    public static final KeyDispatchDataCodec<PaletteRuleSource> CODEC =
            KeyDispatchDataCodec.of(DATA_CODEC);

    private final SurfaceRules.RuleSource base;

    /** @param base the wrapped rule source (the vanilla material tree); never null. */
    public PaletteRuleSource(SurfaceRules.RuleSource base) {
        this.base = Objects.requireNonNull(base, "base");
    }

    /** The wrapped rule source. / 被包装的规则源。 */
    public SurfaceRules.RuleSource base() {
        return base;
    }

    @Override
    public KeyDispatchDataCodec<? extends SurfaceRules.RuleSource> codec() {
        return CODEC;
    }

    @Override
    public SurfaceRules.SurfaceRule apply(SurfaceRules.Context context) {
        SurfaceRules.SurfaceRule inner = base.apply(context);
        Map<Block, Block> current = SubterraSurfaceRules.activeRebind();
        if (current.isEmpty()) {
            // Identity: hand back the vanilla rule object unchanged (bit-for-bit path, zero overhead).
            return inner;
        }
        return (x, y, z) -> {
            BlockState state = inner.tryApply(x, y, z);
            if (state == null) {
                return null;
            }
            Block target = current.get(state.getBlock());
            return target == null ? state : target.defaultBlockState();
        };
    }

    @Override
    public String toString() {
        return "subterra:surface_palette[base=" + base + "]";
    }
}
