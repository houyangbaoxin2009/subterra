# Pehkui-Rebuilt 3.8.5 — Third-Party Notice / 第三方声明

- Upstream original: https://github.com/Virtuoel/Pehkui — original Pehkui library by
  Virtuoel, `Copyright (c) 2019 - 2024 Virtuoel`, MIT.
- Rebuild: https://github.com/wuhenqiubai/Pehkui-Rebuilt — an independent community
  re-build maintaining Pehkui public-API compatibility for modern Fabric/NeoForge. The
  VERBATIM MIT text included here as `LICENSE` (copyright `2019 - 2024 Virtuoel`) was
  fetched from the rebuild repository's `fabric/26.2` branch root; the same MIT
  statement is present on the original Virtuoel/Pehkui repository.
- Version: Pehkui-Rebuilt `3.8.5` line (the data-pack scale-rules feature requires
  `3.8.5+`; the MIT statement is identical across the 3.x line). Directory
  `pehkui-rebuilt-3.8.5`.
- License: MIT — the verbatim MIT text is included in this directory as `LICENSE`,
  copyright and permission notice preserved.
- Scope of inclusion / 直接包含范围: only the MIT license text above is directly included
  verbatim. **No Pehkui or Pehkui-Rebuilt source code is bundled** — this directory
  contains license/notice materials only.

- Adaptation scope / 适配范围说明 (p.2.25.1, `engine.scale`):
  - `engine.scale` (pure JDK) models the *data shape* of Pehkui / Pehkui-Rebuilt core
    scaling: `ScaleType` (dimensioned scale types; the lowercase registration forms keep
    the upstream `pehkui:<type>` ids), `ScaleData` (per-entity, per-dimension scale
    values), `ScaleModifier` (multiplicative scale modifiers), per-tag scaling
    (`ScaledEntityData`) and data-pack scale rules (`ScaleRuleDocument`).
  - The implementations themselves are ORIGINAL deterministic Subterra code; no Pehkui
    implementation source is bundled. Clean-room simplifications:
    1. Only a fixed pure-JDK dimension subset is modelled —
       `base / width / height / depth / eye_height / hitbox_width / hitbox_height /
       model_width / model_height`. Pehkui models many more derived types (`motion`,
       `reach`, `health`, `attack`, `projectiles`, `explosions`, ...) through an
       MC-injected registry plus runtime resolution of derived scales; that is NOT
       ported here.
    2. `depth` here is an added orthogonal dimension (upstream `width` covers
       width/length/depth).
    3. Data-pack rules are expressed in the framework's `td` data language
       (deterministic `fromTd` / `toTd`) rather than upstream JSON, and a rule matches a
       single entity-type tag. Rule semantics follow the upstream "highest priority
       wins, rules do not stack" behaviour collapsed into a deterministic fixed-order
       "last match overrides" model. Upstream `fabric:load_conditions`, EntityPredicate
       conditions beyond entity-type/tag matching, the players-default-exclusion knob
       and periodic server-side checking are NOT ported.
  - No Minecraft code is touched; applying a computed scale to a real entity is left to
    the runtime layer.
- 适配范围：`engine.scale`（纯 JDK）建模了 Pehkui / Pehkui-Rebuilt 核心缩放的数据形态：
  `ScaleType`（按维度缩放类型；小写注册名沿用上游 `pehkui:<type>` id 形式）、`ScaleData`
  （每实体、每维度缩放值）、`ScaleModifier`（乘法缩放修饰符）、按标签缩放（`ScaledEntityData`）
  与数据包缩放规则（`ScaleRuleDocument`）。实现本身为 Subterra 原创确定性代码，未附带任何
  Pehkui 实现源码。clean-room 简化：(1) 仅建模固定的纯 JDK 维度子集
  `base / width / height / depth / eye_height / hitbox_width / hitbox_height /
  model_width / model_height`；Pehkui 以更多派生类型（`motion`/`reach`/`health`/
  `attack`/`projectiles`/`explosions` 等）通过 MC 注入注册表与运行时派生解析实现，此处不移植。
  (2) 本处 `depth` 为新增的正交维度（上游 `width` 同时覆盖 width/length/depth）。(3) 数据包
  规则以本框架 `td` 数据语言表达（确定性 `fromTd` / `toTd`）而非上游 JSON，且一条规则匹配单个
  实体类型标签。规则语义遵循上游「高优先级胜出、规则不叠加」行为，折叠为确定性固定序的「末尾命中
  覆盖」模型；上游 `fabric:load_conditions`、超出 entity-type/标签匹配的 EntityPredicate 条件、
  默认排除玩家开关与周期性服务器端检测均未移植。本子项不触碰 MC 代码；将计算出的缩放应用到真实实
  体留给 runtime 层。