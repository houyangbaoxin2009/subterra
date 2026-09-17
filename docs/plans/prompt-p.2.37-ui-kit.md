# p.2.37 设计案：Subterra UI 库（SubUI Kit）——组件·布局·主题·绘制命令，全面可配置

# p.2.37 Design: The Subterra UI Library (SubUI Kit) — Components · Layout · Theming · Draw Lists, Fully Configurable

> 状态：**待对齐**（结构级设计，落码前确认 §7）。宏观架构不变：api ← engine ← runtime 铁律；
> **双接口纪律（用户定案，全模块适用）**：td + api 两套接口，可选出入口/出口：指令、zd 等。
> clean-room：参考登记制（vanilla Screen/AbstractWidget 语义、ModMenu MIT 借鉴已有、Flywheel
> MIT 实例化基座已有），零 copyleft 源码包含。
>
> Status: **pending alignment** (structural design; confirm §7 before implementation). The macro
> architecture is unchanged (the api ← engine ← runtime iron law); the **dual-interface
> discipline** (td + api for every module, optional inlets/outlets: commands, zd) applies.
> Clean-room with a reference registry (vanilla Screen/AbstractWidget semantics, the existing
> ModMenu MIT borrow, the existing Flywheel MIT instancing base); zero copyleft source inclusion.

---

## 1. 现状锚定（已核对源码的事实）

## 1. Ground Truth (verified against source)

* `engine.ui`（p.2.22）：**数据面**——FoodValues/HudData（AppleSkin Unlicense 移植）、
  TooltipRule/TooltipBook、DocBookTd/BookDef/BookPage、ModListViewCore/ModDetailViewCore/
  LicenseViewCore（ModMenu MIT 借鉴）；`runtime.ui.UiRuntime` 门控壳（`subterra.probe.ui`）。
* `engine.hub`（p.2.23）：**表单系统**——FormStructure/FormField（`name, kind, widgetHint`
  固定字段序）/FormRenderData/HubFormGen/HubFormValues/HubConfigEditor，td 驱动配置编辑。
* `engine.render`（p.2.27 系）：原版保真渲染面模型 + Flywheel 实例化基座
  （InstanceFormat/ShaderTemplate/BackendRegistry SPI）+ LOD 线——**无 UI 组件库**。
* `api.ui.UiApi`（p.2.33.5）：数据面契约（固定 tooltip 行序/书籍页型序/模组字段序）。
* 结论：数据面与表单面已就绪，缺的是**通用组件/布局/主题/绘制执行**这一层——本设计案补齐。

---

## 2. 分层（宏观架构不变）

## 2. Layering (macro architecture unchanged)

| 层 | 包 | 职责 | 依赖纪律 |
|---|---|---|---|
| 引擎核心 | `engine.ui.kit`（纯 JDK） | 组件模型、布局引擎、主题 token、状态机、事件模型、**绘制命令列表** | 零 MC/GL import；同输入同字节 |
| 运行时执行 | `runtime.ui.kit` | 绘制命令 → `GuiGraphics` 执行器；MC 输入事件 → 组件事件；Screen/HUD 注册与注入 | 仅消费 draw list，不做布局决策 |
| 契约门面 | `api.ui.kit`（新增）+ `UiApi` 扩展 | 开发者面：组件工厂、屏幕注册、主题 API；与 engine 镜像范式一致 | api 不依赖 engine |

---

## 3. 组件模型（九维理念不适用此处，但「可修改关系」精神一致：组合自由、缺省有序）

## 3. Component Model

* **组件树**：容器 `Panel / Stack(叠层) / Row / Column / Grid`；控件 `Label / Button /
  TextField / Toggle / Slider / Dropdown / List(虚拟滚动) / Tooltip / ProgressBar /
  Sprite(纹理区域) / NineSlice / CustomWidget(域模组自绘)`。第一批清单见 §7 对齐点 1。
* **双入口同树**：组件树可由 **td 声明**（数据包 `ui/screens/*.td`，结构化字段固定序）构建，
  也可由 **api 构建**（`api.ui.kit` builder）——两种入口产出**同一棵树、同一份绘制命令**
  （确定性对拍探针钉死）。
* **布局引擎**（纯 JDK、确定性）：盒模型（padding/margin/border）；三种布局算法
  `flow`（固定扫描序）/ `anchor`（锚点+偏移）/ `grid`（行列跨度）；测量-排列两阶段；
  布局结果可逐字节 golden 断言。
* **状态机**：`normal / hover / active / disabled / focus` 五态固定；焦点导航序 = 组件树
  固定序（键盘可达性）。
* **动画**：过渡由配置声明（时长/曲线）；帧时间由 runtime 注入，engine 侧为**纯函数采样**
  （禁墙钟）——同帧同字节。
* **多语言底层支持（用户定案）**：本地化是引擎核心的一等公民——`LocaleTable` 持有
  key → 多语言文本表与确定性 fallback 链（请求 locale → 回退链 → `en_us`），组件只持
  translation key + 参数；locale 表可 td 声明、可由 api 注册，engine 侧解析结果确定
  （同 key 同 locale 同字节）。
* **事件**：`enter / exit / click / change / submit` 固定事件集 + 确定性派发序（树序）。

---

## 4. 绘制命令列表（确定性核心 ↔ MC 执行的唯一边界）

## 4. Draw Lists (the single deterministic core ↔ MC execution boundary)

* 组件树每帧（或脏时）产出**绘制命令列表**：`rect / text / sprite / scissor(push/pop) /
  nine-slice / custom(callout to 域模组)`，固定序编码、逐字节可断言。
* engine 侧：树 + 布局 + 主题 + 状态 → 命令列表（纯函数）。runtime 侧：命令列表 →
  GuiGraphics/GL 执行——**执行器永不反向影响布局**。
* 性能：rect/sprite 命令可映射到 p.2.27.1 实例化基座**一次 bake 批量绘制**；脏树标记
  （dirty-flag）避免逐帧重建；HUD 低频层与屏幕层分离预算。
* zd 出口：绘制命令快照序列化为 zd 载体（复用 engine.zd），供离线 golden 对拍与回归——
  可选出口之一。

---

## 5. 主题与全面可配置（双接口）

## 5. Theming and Full Configurability (dual interface)

* **主题 token 制**：`color / font / spacing / corner / animation` 五族 token，td 文档声明；
  **视觉方式由消费者决定（用户定案）**——缺省主题只是**中性起点**（不钉 vanilla 拟真），
  消费者（Hub/域模组/主题包）整包替换；SubUI 核心对视觉风格零假设。
* **td 面**：`config/subterra/ui.td`（全局主题/缩放/动画开关，热重载）+ 数据包
  `ui/screens/*.td`（屏幕/组件声明）+ `ui/themes/*.td`（主题包）。
* **api 面**：`api.ui.kit`——`SubUiScreens.register`（屏幕注册）、组件 builder、
  主题 token 读写；与 td 面同参同校验（同一校验器双入口复用）。
* **指令出口**（可选）：`/subterra ui reload`（主题热重载）、`/subterra ui preview <screen>`
  （离屏渲染快照到 zd/截图）。
* **校验纪律**：非法 token/组件字段确定性拒绝（IAE/NPE 契约同 `SkyApi`）。

---

## 6. 与既有面的整合（不重写，只接驳）

## 6. Integration with the Existing Surfaces (adapt, never rewrite)

* **Hub 表单迁移**：`HubFormGen` 改为产出 SubUI 组件树（`kind → widgetHint → widget` 映射
  可配置）；表单校验/值往返（HubFormValues）不变——表单只是组件树的一种 td 来源。
* **数据面适配**：HudData/TooltipBook/DocBook/ModListViewCore 作为 SubUI 的数据源适配层；
  AppleSkin HUD 数据层不动，仅渲染执行换 SubUI。
* **门控纪律延续**：`runtime.ui.kit` 门控壳并入 `subterra.probe.ui`（缺省 no-op），
  marker 并入 AsyncE2EProbe 同开服。
* **interact 边界**：玩家侧交互仍走 p.2.14 interact（面板仅管理视图的既定纪律不变）。

---

## 7. 对齐点（2026-09-17 已全部定案）

## 7. Alignment Points (all resolved 2026-09-17)

1. **第一批组件清单**：按建议裁剪——`Panel / Stack / Row / Column / Label / Button /
   TextField / Toggle / Slider / List / NineSlice` 先行；`Dropdown / ProgressBar / Sprite /
   CustomWidget` 随后续子项补。
2. **视觉方向**：**消费者决定**（用户定案）——缺省主题为中性起点，整包可替换，核心零风格假设。
3. **Hub 迁移节奏**：先并行，迁移独立子项（p.2.37.4 仅接驳，不切换）。
4. **版本号**：p.2.37 新线（世界生成 p.2.36 线并行）。

---

## 8. 实现序（不做 forward optional，只分实现序）

## 8. Implementation Order (no forward-optional; ordering only)

1. **p.2.37.1** 组件模型 + 布局引擎（纯 JDK）+ `UiKitProbe`（布局 golden + 确定性）。
2. **p.2.37.2** 绘制命令列表 + nine-slice/scissor 语义 + 逐字节断言探针。
3. **p.2.37.3** 主题 token + `ui.td` 配置面（td 双入口校验复用）+ 探针。
4. **p.2.37.4** runtime 执行器（MC 接线、门控 no-op）+ Hub 表单接驳。
5. **p.2.37.5** `api.ui.kit` 契约门面 + 指令出口（reload/preview）+ 探针。
6. **p.2.37.6** 验收：全探针 + 实机回归（HUD/书籍/Hub/域模组示例屏幕）。

---

## 9. 风险与守门

## 9. Risks and Gates

* **确定性**：布局与绘制命令同输入同字节；探针逐字节对拍（含 td 入口 ↔ api 入口同树对拍）。
* **性能**：实例化批量 bake、脏树标记、HUD/屏幕预算分离；探针内加求值次数/命令数断言。
* **许可证**：clean-room 参考登记（vanilla 语义对照、ModMenu/Flywheel 已有材料），零源码包含。
* **回归**：既有 `UiProbe`（30 checks）不回归——数据面只加不改；`engine.hub` 表单迁移独立子项、
  迁移前旧行为逐位保持。
