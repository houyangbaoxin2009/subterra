/**
 * Vanilla {@code SurfaceRules} rule-source bridge core (p.1.8.15): a clean-room
 * vocabulary for MC 1.21.1 {@code net.minecraft.world.level.levelgen.SurfaceRules}
 * rule/condition sources (block, condition, sequence, band, steep,
 * above-preliminary-surface, not, biome-match, noise-threshold, y-condition,
 * stone-depth, vertical-gradient, water-block, hole) compiled onto the p.1.8.5
 * {@code io.toterra.subterra.engine.worldgen.pipeline.surface} ordered evaluator,
 * together with the overworld default surface-rule set.
 * <p>
 * 主世界表面规则桥接核心（p.1.8.15）：MC 1.21.1 {@code SurfaceRules} 规则/条件来源的
 * 洁净房词汇（方块、条件、序列、带状层、陡坡、初步表面之上、取反、群系匹配、噪声阈值、
 * Y 条件、岩层深度、垂直渐变、水体块、空洞），按 p.1.8.5 的
 * {@code io.toterra.subterra.engine.worldgen.pipeline.surface} 有序求值器编译，并附主世界默认
 * 表面规则集。
 */
package io.toterra.subterra.engine.worldgen.pipeline.surfacerules;