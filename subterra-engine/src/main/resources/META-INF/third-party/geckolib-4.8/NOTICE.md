# GeckoLib 4.8 — Third-Party Notice / 第三方声明

- Upstream: https://github.com/bernie-g/geckolib
- Version: 4.x line for Minecraft 1.21.1 (GeckoLib 4.8, e.g. Forge
  `geckolib-forge-1.21.1-4.8`; Maven coordinates
  `software.bernie.geckolib:geckolib-forge-1.21.1:4.8`). The `1.21.1` branch of the
  upstream repository carries no `LICENSE` file at its root; the MIT statement text
  included here as `LICENSE` is the verbatim upstream MIT text taken from the same
  repository's `main` branch (`Copyright (c) 2026 GeckoLib`). The upstream
  `geckolib-core` repository carries the same MIT text with
  `Copyright (c) 2021 GeckoThePecko`. Both are MIT; no copyleft material is involved.
- License: MIT — the verbatim MIT text is included in this directory as `LICENSE`.
- Scope of inclusion / 直接包含范围: only the MIT license text is directly included
  verbatim with the copyright and permission notice preserved. **No GeckoLib source
  code is bundled** — this directory contains license/notice materials only.

- Adaptation scope / 适配范围说明 (p.2.21.1, `engine.anim`):
  - `engine.anim` (pure JDK) models the *data shape* of GeckoLib 4's core animation
    concepts as fetched from the upstream `1.21.1` branch
    (`software.bernie.geckolib.animation`: `Animation(name, length, loopType,
    boneAnimations)`, per-bone `rotation` / `position` / `scale` keyframe channels,
    `Keyframe(length, startValue, endValue, easingType)`; rotation is Euler angles in
    degrees with shortest-arc interpolation — NOT quaternions).
  - The implementations themselves are ORIGINAL deterministic Subterra code. The
    keyframe model is a clean-room simplification: GeckoLib 4 consumes keyframes as
    (length, start, end) transitions advanced tick-by-tick by `AnimationController`;
    `engine.anim` instead samples by absolute animation time `t` with a documented
    fixed linear-interpolation / clamp semantics (rotations shortest-arc, angles in
    degrees). GeckoLib's 30+ `EasingType` set, `RawAnimation` stage lists, event /
    sound / particle keyframes, bone hierarchies and MC renderer integration are NOT
    ported. This sub-item touches no Minecraft code; it is a pure-JDK data /
    sampling / state-machine model. Actual rendering is left to the runtime layer.
  - 适配范围：`engine.anim`（纯 JDK）建模了 GeckoLib 4 核心动画概念的数据形态（抓取自上游
    `1.21.1` 分支的 `software.bernie.geckolib.animation`：`Animation(name, length,
    loopType, boneAnimations)`、逐骨骼 `rotation` / `position` / `scale` 关键帧通道、
    `Keyframe(length, startValue, endValue, easingType)`；rotation 为欧拉角（度）并采用
    最短弧插值——非四元数）。实现本身为 Subterra 原创确定性代码。关键帧模型为 clean-room
    简化：GeckoLib 4 以（时长, 起始值, 结束值）过渡形式由 `AnimationController` 逐 tick 消费
    关键帧；`engine.anim` 改为按绝对动画时间 `t` 采样，并文档化固定线性插值 / clamp 语义
    （rotation 最短弧、角度单位为度）。GeckoLib 的 30+ `EasingType`、`RawAnimation`
    阶段列表、事件/音效/粒子关键帧、骨骼层级与 MC 渲染集成均未移植。本子项不触碰 MC 代码，
    仅为纯 JDK 的数据 / 采样 / 状态机模型；实际渲染留给 runtime 层。
