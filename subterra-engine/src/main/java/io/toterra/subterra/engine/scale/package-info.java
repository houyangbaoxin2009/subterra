/**
 * Entity-scaling core (p.2.25.1, Pehkui-Rebuilt core port sentinel): the pure-JDK
 * deterministic data model for per-entity scaling, modeled on the core concepts of Pehkui
 * (Virtuoel, MIT) and its community rebuild Pehkui-Rebuilt as fetched from the rebuild's
 * {@code fabric/26.2} branch (MIT — license materials under
 * {@code META-INF/third-party/pehkui-rebuilt-3.8.5}).
 * <p>
 * {@link io.toterra.subterra.engine.scale.ScaleType} is the fixed, dimensioned set of scale
 * types ({@code base/width/height/depth/eye_height/hitbox_width/hitbox_height/model_width/model_height}),
 * where {@code BASE}
 * is the root scale other dimensions derive from. {@link io.toterra.subterra.engine.scale.ScaleData}
 * carries per-entity per-dimension values normalized to the {@code ScaleType} enumeration
 * order. {@link io.toterra.subterra.engine.scale.ScaleModifier} composes multiplicative
 * modifiers in a fixed application order, and
 * {@link io.toterra.subterra.engine.scale.ScaledEntityData} stores per-tag base scales.
 * {@link io.toterra.subterra.engine.scale.ScaleRuleDocument} models data-pack scale rules
 * td-ized as {@code scale_rules} documents. All classes are pure JDK, immutable or
 * fixed-order, with no randomness and no timing — identical inputs yield identical double
 * bit patterns; no Minecraft code is touched. See
 * {@code META-INF/third-party/pehkui-rebuilt-3.8.5/NOTICE.md} for the adaptation scope
 * (clean-room simplifications: a fixed pure-JDK dimension subset, multiplicative per-tag
 * merge, rule override semantics, rules expressed in {@code td} rather than upstream JSON;
 * derived runtime scale types and MC injection are not ported).
 *
 * <p>实体缩核心（p.2.25.1，Pehkui-Rebuilt 核心移植前哨）：按实体缩放的纯 JDK 确定性数据模型，以
 * Pehkui（Virtuoel，MIT）及其社区重建版 Pehkui-Rebuilt 的核心概念为形态参考（抓取自重建版
 * {@code fabric/26.2} 分支，MIT——许可材料见 {@code META-INF/third-party/pehkui-rebuilt-3.8.5}）。
 * <p>{@link io.toterra.subterra.engine.scale.ScaleType} 为固定的按维度缩放类型集
 * （{@code base/width/height/depth/eye_height/hitbox_width/hitbox_height/model_width/model_height}），其中 {@code BASE} 为派生其它
 * 维度的根缩放。{@link io.toterra.subterra.engine.scale.ScaleData} 承载每实体每维度缩放值，并统一
 * 归一化为 {@code ScaleType} 枚举序。{@link io.toterra.subterra.engine.scale.ScaleModifier} 以固定
 * 应用序组合乘法修饰符；{@link io.toterra.subterra.engine.scale.ScaledEntityData} 保存按标签的基础
 * 缩放。{@link io.toterra.subterra.engine.scale.ScaleRuleDocument} 建模数据包缩放规则，td 化为
 * {@code scale_rules} 文档。全部类纯 JDK、不可变或固定序、无随机无时序——同输入恒得同 double 位模式；
 * 不触碰任何 MC 代码。适配范围（clean-room 简化：固定纯 JDK 维度子集、按标签乘法合并、规则覆盖语义、
 * 规则以 {@code td} 而非上游 JSON 表达；运行期派生缩放类型与 MC 注入不移植）见
 * {@code META-INF/third-party/pehkui-rebuilt-3.8.5/NOTICE.md}。
 */
package io.toterra.subterra.engine.scale;