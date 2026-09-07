package io.toterra.subterra.optim.worldgen.pipeline.surfacerules;

import io.toterra.subterra.optim.worldgen.pipeline.surface.SurfaceAction;
import io.toterra.subterra.optim.worldgen.pipeline.surface.SurfaceActions;
import io.toterra.subterra.optim.worldgen.pipeline.surface.SurfaceConditions;
import io.toterra.subterra.optim.worldgen.pipeline.surface.SurfaceRule;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Vanilla {@code SurfaceRules} rule-source vocabulary (p.1.8.15, clean-room): a
 * builder-style {@code RuleSource} whose {@link #compile(OverworldPalette)}
 * lowers onto the p.1.8.5 {@link SurfaceRule} seam as a flat, ordered list of
 * {@code (condition, action)} pairs. The p.1.8.5 evaluator traverses that list
 * first-match-wins with decline-on-null — exactly mirroring MC
 * {@code SequenceRule.tryApply}, which returns the first non-{@code null}
 * sub-rule result.
 * <p>
 * Supported sources: {@link #block(String)} state, {@link #condition(VRuleCondition, VRuleSource)},
 * {@link #sequence(VRuleSource...)}, {@link #band(int, int, VRuleSource)} (the overworld
 * "band" vertical layering, e.g. the bedrock-floor band).
 * <p>
 * All compilations are linear in the number of leaf rules (no O(n²)) and
 * deterministic.
 * <p>
 * vanilla {@code SurfaceRules} 规则来源词汇（p.1.8.15，洁净房）：构建式 {@code RuleSource}，其
 * {@link #compile(OverworldPalette)} 把规则降到 p.1.8.5 {@link SurfaceRule} 接缝，成为有序
 * {@code (条件, 动作)} 平面列表。p.1.8.5 求值器按首配命中、遇空拒收遍历该列表——与 MC
 * {@code SequenceRule.tryApply}（返回首个非 null 子规则结果）完全对应。
 */
public interface VRuleSource {

    /**
     * Compiles this source to an ordered list of p.1.8.5 {@link SurfaceRule}s.
     * @param palette resolver of vanilla block-state names to stable ids
     * @return immutable ordered rule list (never {@code null})
     */
    List<SurfaceRule> compile(OverworldPalette palette);

    /** Block state rule (mirrors MC {@code Block}). 方块状态规则。 */
    static VRuleSource block(String stateId) {
        Objects.requireNonNull(stateId, "stateId");
        return new BlockSource(stateId);
    }

    /** Y-band rule (mirrors the overworld band layering): body gated by {@code [bottomY, topY]}. */
    static VRuleSource band(int bottomY, int topY, VRuleSource body) {
        if (bottomY > topY) {
            throw new IllegalArgumentException("band bounds inverted: bottom=" + bottomY + " top=" + topY);
        }
        Objects.requireNonNull(body, "body");
        return new BandSource(bottomY, topY, body);
    }

    /** Conditional rule (mirrors MC {@code ifTrue}): {@code then} only when {@code cond} holds. */
    static VRuleSource condition(VRuleCondition cond, VRuleSource then) {
        Objects.requireNonNull(cond, "cond");
        Objects.requireNonNull(then, "then");
        return new ConditionSource(cond, then);
    }

    /** Sequential rule (mirrors MC {@code sequence}): first-match over the parts. */
    static VRuleSource sequence(VRuleSource... parts) {
        Objects.requireNonNull(parts, "parts");
        return new SequenceSource(List.of(parts));
    }

    /** Steep variant rule: body only on steep columns. 陡坡变体规则。 */
    static VRuleSource steep(VRuleSource then) {
        return condition(VRuleCondition.steep(), then);
    }

    /** Above-preliminary-surface rule: body only above the preliminary surface. 初步表面之上规则。 */
    static VRuleSource abovePreliminarySurface(VRuleSource then) {
        return condition(VRuleCondition.abovePreliminarySurface(), then);
    }

    /** Block-state source. 方块状态来源。 */
    record BlockSource(String stateId) implements VRuleSource {
        public BlockSource {
            Objects.requireNonNull(stateId, "stateId");
        }

        @Override
        public List<SurfaceRule> compile(OverworldPalette palette) {
            SurfaceAction action = SurfaceActions.state(palette.resolve(stateId));
            return List.of(new SurfaceRule(SurfaceConditions.alwaysTrue(), action));
        }
    }

    /** Y-band source. 垂直带来源。 */
    record BandSource(int bottomY, int topY, VRuleSource body) implements VRuleSource {
        @Override
        public List<SurfaceRule> compile(OverworldPalette palette) {
            VRuleCondition band = VRuleCondition.yRange(bottomY, topY);
            List<SurfaceRule> inner = body.compile(palette);
            List<SurfaceRule> out = new ArrayList<>(inner.size());
            for (SurfaceRule r : inner) {
                out.add(wrap(band, r));
            }
            return List.copyOf(out);
        }
    }

    /** Conditional source. 条件来源。 */
    record ConditionSource(VRuleCondition cond, VRuleSource then) implements VRuleSource {
        @Override
        public List<SurfaceRule> compile(OverworldPalette palette) {
            List<SurfaceRule> inner = then.compile(palette);
            List<SurfaceRule> out = new ArrayList<>(inner.size());
            for (SurfaceRule r : inner) {
                out.add(wrap(cond, r));
            }
            return List.copyOf(out);
        }
    }

    /** Sequential source. 序列来源。 */
    record SequenceSource(List<VRuleSource> parts) implements VRuleSource {
        public SequenceSource {
            Objects.requireNonNull(parts, "parts");
        }

        @Override
        public List<SurfaceRule> compile(OverworldPalette palette) {
            List<SurfaceRule> out = new ArrayList<>();
            for (VRuleSource part : parts) {
                out.addAll(part.compile(palette));
            }
            return List.copyOf(out);
        }
    }

    /** Wraps a rule with an extra guard condition (conjunction). 用守卫条件包裹规则（合取）。 */
    private static SurfaceRule wrap(VRuleCondition cond, SurfaceRule r) {
        // Lower the vanilla condition onto the p.1.8.5 SurfaceContext seam.
        io.toterra.subterra.optim.worldgen.pipeline.surface.SurfaceCondition extra = c -> {
            VSurfaceContext v = new VSurfaceContext(c.x(), c.y(), c.z(), c.biomeTag(), c.density(),
                    0, 0, 0, VSurfaceContext.NO_WATER, 0, null);
            return cond.test(v);
        };
        io.toterra.subterra.optim.worldgen.pipeline.surface.SurfaceCondition combined =
                SurfaceConditions.and(extra, r.condition());
        return new SurfaceRule(combined, r.action());
    }
}