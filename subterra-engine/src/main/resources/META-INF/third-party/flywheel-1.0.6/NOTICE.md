# Flywheel 1.0.6 — Third-Party Notice / 第三方声明

- Upstream: https://github.com/Jozufozu/Flywheel (repository now hosted under the
  Engine-Room organization: https://github.com/Engine-Room/Flywheel)
- Version: v1.0.6 (mc1.21.1 / NeoForge line, `flywheel-neoforge-1.21.1-1.0.6.jar`,
  distributed embedded in Create 6.0.9/6.0.10; Maven coordinates
  `dev.engine-room.flywheel:flywheel-neoforge-api-1.21.1:1.0.6`)
- License: MIT — the verbatim MIT text is included in this directory as `LICENSE`
  (upstream file name `LICENSE.md`, blob `af23f9c…` on the `1.21.1/dev` branch).
- Scope of inclusion / 直接包含范围: the MIT license text above is directly included
  verbatim with the copyright and permission notice preserved. Upstream has no
  `v1.0.6` git tag; the source references for this port are the `mc1.21.1/v1.0.5`
  tag (commit `babeefab`) and the `1.21.1/dev` branch of the same line that ships
  Flywheel 1.0.6.

- Adaptation scope / 适配范围说明:
  - `engine.render.instancing` (pure JDK) models the *interface shape* of Flywheel's
    `api.layout` surface (Layout / LayoutBuilder / ElementType / ValueRepr and the
    scalar/vector/matrix/array element types) and `api.backend` surface
    (Backend — `priority()` / `isSupported()` — and BackendManager's
    highest-priority default selection), as fetched from the `mc1.21.1/v1.0.5` tag.
    The implementations themselves are ORIGINAL deterministic Subterra code; no
    Flywheel implementation source is bundled.
  - `ShaderTemplate` is a clean-room simplified model: the Flywheel 1.0.x shader
    pipeline sources (backend source set) could not be fully retrieved online, so
    this class is an original fixed-order template assembly / placeholder
    substitution model and does NOT claim to be a direct inclusion.
  - No Minecraft or OpenGL code is touched; this sub-item only models data /
    template / SPI shape. Actual GPU work is left to the runtime layer.
  - 适配范围：`engine.render.instancing`（纯 JDK）建模了 Flywheel `api.layout`（Layout /
    LayoutBuilder / ElementType / ValueRepr 及 scalar/vector/matrix/array 元素类型）与
    `api.backend`（Backend 的 `priority()` / `isSupported()`，及 BackendManager 按最高
    优先级选择缺省后端）的接口形态（抓取自 `mc1.21.1/v1.0.5` tag）。实现本身为 Subterra
    原创确定性代码，未附带任何 Flywheel 实现源码。`ShaderTemplate` 为 clean-room 简化
    模型：Flywheel 1.0.x 着色器管线源码（backend source set）未能在线上完整取得，本类为
    原创的固定序模板拼装 / 占位符替换模型，不宣称直接包含。本子项不触碰 MC / OpenGL，
    仅建模数据 / 模板 / SPI 形态；实际 GPU 调用留给 runtime 层。
