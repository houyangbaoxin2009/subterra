/**
 * UI / HUD data core (p.2.22.1, AppleSkin Unlicense port sentinel): the
 * deterministic read-only data layer of the HUD — food nutritional values
 * ({@link io.toterra.subterra.engine.ui.FoodValues}), fixed-order food-tooltip
 * rows ({@link io.toterra.subterra.engine.ui.FoodTooltipRow}), the player HUD
 * data surface ({@link io.toterra.subterra.engine.ui.HudData}) and the
 * hunger/saturation restore overlay
 * ({@link io.toterra.subterra.engine.ui.FoodOverlay}). The data shape is modeled
 * on the AppleSkin HUD data layer fetched from
 * {@code github.com/squeek502/AppleSkin} (The Unlicense — license materials under
 * {@code META-INF/third-party/appleskin-3.0.6}); implementations are original
 * deterministic Subterra code with the clean-room simplifications documented in
 * the classes and in the NOTICE. All classes are pure JDK, immutable records
 * with fixed iteration order, no randomness and no timing — identical inputs
 * yield identical bits; no Minecraft code is touched.
 *
 * <p>UI / HUD 数据核心（p.2.22.1，AppleSkin Unlicense 移植前哨）：HUD 的确定性只读数据层——
 * 食物营养值（{@link io.toterra.subterra.engine.ui.FoodValues}）、固定序食物 tooltip 行
 * （{@link io.toterra.subterra.engine.ui.FoodTooltipRow}）、玩家 HUD 数据面
 * （{@link io.toterra.subterra.engine.ui.HudData}）与饱食/饱和度恢复覆盖
 * （{@link io.toterra.subterra.engine.ui.FoodOverlay}）。数据形态以抓取自
 * {@code github.com/squeek502/AppleSkin} 的 AppleSkin HUD 数据层为参考（The Unlicense——
 * 许可材料见 {@code META-INF/third-party/appleskin-3.0.6}）；实现为 Subterra 原创确定性
 * 代码，clean-room 简化已文档化于各注释与 NOTICE。全部类纯 JDK、不可变 record、固定迭代序、
 * 无随机无时序——同输入恒得同字节；不触碰任何 MC 代码。
 */
package io.toterra.subterra.engine.ui;
