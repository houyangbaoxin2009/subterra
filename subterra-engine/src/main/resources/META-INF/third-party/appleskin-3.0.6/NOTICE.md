# AppleSkin 3.0.6 — Third-Party Notice / 第三方声明

- Upstream: https://github.com/squeek502/AppleSkin
- Version: 3.0.x line. `mod_version = 3.0.6` as declared in `gradle.properties`
  of the upstream `1.21.5-fabric` branch (latest release tag v3.0.10, 2026-06).
  The HUD data-layer semantics ported here were fetched from the
  `1.20.1-fabric` branch (classic `squeek.appleskin.api.food.FoodValues` shape,
  `TooltipOverlayHandler`, `HUDOverlayHandler`, `FoodHelper`) and the
  `1.21.5-fabric` branch; the data semantics are stable across the 2.x–3.x line.
- License: The Unlicense (public domain) — the verbatim upstream `LICENSE` text
  is included in this directory as `LICENSE` (fetched from the repository root;
  the same text is present on all branches).
- Scope of inclusion / 直接包含范围: the Unlicense text above is directly included
  verbatim. **No AppleSkin source code is bundled** — this directory contains
  license/notice materials only.

- Adaptation scope / 适配范围说明 (p.2.22.1, `engine.ui`):
  - `engine.ui` (pure JDK) reproduces the *data shape* of AppleSkin's HUD data
    layer: `FoodValues(hunger, saturationModifier)` with
    `getSaturationIncrement() = hunger * saturationModifier * 2f` is ported
    verbatim (`io.toterra.subterra.engine.ui.FoodValues`); the food-tooltip data
    rows (`FoodTooltipRow`) map upstream `TooltipOverlayHandler` data — hunger
    displayed in drumsticks (`ceil(|hunger| / 2)`, 1 drumstick = 2 hunger
    points), saturation as the increment value; the HUD read-only surface
    (`HudData` = hunger / saturation / exhaustion) maps upstream
    `HUDOverlayHandler` reads of the vanilla `HungerManager`
    (`getFoodLevel()` / `getSaturationLevel()` / `getExhaustion()`, upstream
    exhaustion ratio `clamp(exhaustion / MAX_EXHAUSTION(4f), 0f, 1f)`).
  - The implementations themselves are ORIGINAL deterministic Subterra code; no
    AppleSkin implementation source is bundled. Clean-room simplifications:
    upstream renders icon-bar overlays (it has no text rows), so the
    `FoodTooltipRow` label/value rows (fixed order hunger → saturation → ratio)
    and the ratio row are original Subterra data shapes with the semantics
    documented on each class; `HudData.ratio()` is `clamp(saturation / 20f, 0f,
    1f)` per the task spec (upstream exhaustion-ratio formula documented on the
    class for reference). ModMenu / DataTip / Patchouli surfaces are NOT ported
    here. This sub-item touches no Minecraft code; it is a pure-JDK
    deterministic data model.
  - 适配范围：`engine.ui`（纯 JDK）复刻了 AppleSkin HUD 数据层的数据形态：
    `FoodValues(hunger, saturationModifier)` 及其
    `getSaturationIncrement() = hunger * saturationModifier * 2f` 逐字移植
    （`io.toterra.subterra.engine.ui.FoodValues`）；食物 tooltip 数据行
    （`FoodTooltipRow`）映射上游 `TooltipOverlayHandler` 数据——饥饿按鸡腿显示
    （`ceil(|hunger| / 2)`，1 鸡腿 = 2 饥饿点），饱和为增量值；HUD 只读数据面
    （`HudData` = hunger / saturation / exhaustion）映射上游 `HUDOverlayHandler`
    对原版 `HungerManager` 的读取（`getFoodLevel()` / `getSaturationLevel()` /
    `getExhaustion()`，上游疲劳比例 `clamp(exhaustion / MAX_EXHAUSTION(4f), 0f, 1f)`）。
    实现本身为 Subterra 原创确定性代码，未附带任何 AppleSkin 实现源码。clean-room 简化：
    上游以图标条 overlay 渲染（无文本行），故 `FoodTooltipRow` 的 label/value 行（固定序
    hunger → saturation → ratio）与比值行为 Subterra 原创数据形态，语义已在各类注释中
    文档化；`HudData.ratio()` 按本子项规格为 `clamp(saturation / 20f, 0f, 1f)`（上游疲劳
    比例公式已在类注释中对照注明）。ModMenu / DataTip / Patchouli 界面不在此移植。本子项
    不触碰 MC 代码，为纯 JDK 确定性数据模型。
