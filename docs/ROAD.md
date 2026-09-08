# Subterra Roadmap / Subterra 路线图

**Status / 状态:** Active / 生效中
**Date / 日期:** 2026-09-08（自系列 ROAD 拆分 / split from series ROAD）
**Version discipline / 编号纪律:** Development uses the pre-release p-track only — `p.<release>.<module>.<sub-item>` (max three levels, no "stage/session" grouping). The release track `r.x.y.z` is cut from a specific p version at stabilization time and introduces no new features. Planning never uses milestone-style labels such as R1/R2. / 开发计划一律只用 p 轨 `p.<发布档>.<模块>.<子项>`（最多三级，禁用「阶段X」分组与 R1/R2 式标签）；`r.x.y.z` 为正式发行轨，在基于某个 p 版本时切出，只做优化与稳定、不引新功能。

## Frame / 框架基线

* Subterra 为全栈开发框架模组（NeoForge 21.1.x / MC 1.21.1），设计见聚合仓库 `docs/2026-09-08-subterra-framework-design.md`。 / Subterra is a full-stack development framework mod (NeoForge 21.1.x / MC 1.21.1); design in aggregate-repo `docs/2026-09-08-subterra-framework-design.md`.
* p.1 线（骨架 / L1 启动 / L2 兼容 / L3 API / 优化集 / td 配置 / 日志 / 世界生成管线）全部落地，构成框架化基线；vanilla 保真工作（p.1.8.20–32）保留为默认预设并固化为 golden tests。 / The p.1 line is fully landed as the framework baseline; vanilla-fidelity work stays as the default preset and is frozen into golden tests.
* p.2 线起为框架化：模块重构为 api / engine / runtime / migrate / devkit，服务大型、颠覆原版、不考虑兼容性的模组（首位消费方 Toterra）。 / From p.2 the line is the framework: modules re-laid as api / engine / runtime / migrate / devkit, serving large, vanilla-overturning, compatibility-agnostic mods (first consumer Toterra).

## Milestone Map / 里程碑映射

* Every milestone below is a p-track item; sub-items continue the track per "one library = one sub-item". / 以下里程碑均为 p 轨条目；子项沿 p 轨推进，一库一子项。
* Acceptance is deterministic probes only (server boots / client enters world / probes PASS), never timing assertions. / 验收一律确定性探针：服务端开服、客户端进世界、探针断言全 PASS；禁时序断言。
* 2026-09-08 — 新增三项数据层重构：**数据包重构**（td 为数据包内容一等语言，数据包逻辑可直接以 tie 编写，替代手写 JSON）+ **存档重构**（zd+td 混合替代原版 NBT `.dat`，减小体积 / 加快加载）+ **服务端交互重构**（增强通道：tink v2 帧 + zd 载荷 + 增量差分，性能更好带宽更小）。 / Added three data-layer reworks: datapack (td first-class, logic in tie), save (zd+td hybrid), and server-interaction (enhanced channel: tink v2 frames + zd payloads + delta sync).
* 2026-09-09 — p.2.2 数据包重构第一块落地：**纯 td 直载**（不产出 JSON，不依赖原版数据包管道）——engine.datapack 容器 + 注册表（EntryKind 七类 / DatapackEntry / Datapack 按 id 确定性排序 / DatapackLoader 双发现：目录约定 `data/<ns>/<kind>/<path>.td` + 可选 pack.td manifest 带 tie 库声明与增量条目）；tie 逻辑装载链（TieLogicLoader → TieLogicBundle：lib 声明 → TieLibrary FFM，FUNCTION 条目按 payload lib/fn 绑定 `<lib>$<fn>`，未导出即抛）；DatapackProbe 探针级闭环 26 断言全绿 + 顺带修复共享 Td 解析器 header 误杀 `type = "..."`
  数据键与注册表排序丢失两个 bug。MC 壳注册接线为下一块。 / p.2.2 first block landed: pure td direct loading (no JSON), engine.datapack registry + tie logic chain; probe closed loop green; two shared-parser bugs fixed along the way.

| p-track / p 轨 | Milestone / 里程碑 | Status / 状态 |
| --- | --- | --- |
| p.1.0–p.1.8 | Framework baseline / 框架化基线：模块骨架、L1 启动、L2 Java25 兼容修补、L3 API 库、优化集（ScratchPool + 移植核心）、td 配置、日志、世界生成管线（九维模型 / 密度函数 / router / surface / 默认 vanilla 预设 / 探针） | landed / 已落地 |
| p.2.0 | Module re-layout / 模块重构（收尾中）：api / engine / runtime / migrate / devkit 分层落地，依赖铁律探针化，探针全绿保持 | landed / 已落地（收尾） |
| p.2.1 | tie bridge / tie 桥（**革命基座**，自原 p.4.0 前移）：tiec → DLL → FFM 加载调用，热点路径 tie 化 + 性能验证 | landed / 已落地 |
| p.2.2 | Datapack rework / 数据包重构（**革命**，自原 p.2.10 前移）：td 为数据包内容一等语言——函数 / 配方 / 战利品表 / 世界生成 / 结构 / 标签 / 本地化 td 声明 + **纯 td 直载**（engine.datapack 注册表 + tie 逻辑链，探针闭环已落地）；数据包逻辑可直接以 tie 编写（tiec 编译 → tie bridge 装载，接 p.2.1），热点路径 tie 化；数据包打包分发（接 config 包）；数据包级规则可被存档覆盖（接 p.2.17 rules）；td + API（含 tie）双接口；MC 壳注册接线进行中（下一块） | in progress / 进行中 |
| p.2.3 | Save rework / 存档重构（**革命**，自原 p.2.11 前移）：现代化存档形态——**zd + td 混合替代原版 NBT `.dat`**（level / player / 侧数据），减小体积、加快加载（zd 压缩变体 / 字典 / 零拷贝）；统一 SaveContainer（世界 + 配置 + 藏录 Ledger + 领域档案 + 遗物条目 + 台账）；双层配置（全局 + 存档覆盖，接 p.2.17）；迁移走 subterra-migrate；元数据可导出（接 p.2.18 export） | pending / 待开工 |
| p.2.4 | Server-interaction rework / 服务端交互重构（**革命**，自原 p.4.3 前移）：客户端 ⇄ 服务端**增强通道**——tink v2 帧 + tsha1f 帧级强校验 + zd 载荷序列化（与 p.2.5 P2P 同栈，语言无关 ABI）；三级载荷策略（L1 高频增量差分 / 兴趣域订阅 · L2 中频快照 + 变更流 · L3 低频加密）；带宽削减 = 增量同步 / 状态降频插值 / 按需订阅；默认强加密（x25519 + AEAD，局域网可信可关）；原版协议路径保留（渐进增强，原版客户端仍可连） | pending / 待开工 |
| p.2.5 | P2P decentralized networking / P2P 去中心化网络（自原 p.4.1 前移）：tink v2 + tsha1f 帧级强校验；zd 作为自定义载荷通道通信介质（先可行性基准） | pending / 待开工 |
| p.2.6 | C2ME bundled / C2ME 直接包含（自原 p.3.0 前移）：engine.worldgen.async 核心 + runtime 异步壳，随框架发布（MIT 声明） | pending / 待开工 |
| p.2.7 | Deterministic parallel / 确定性并行执行 engine.parallel（**革命·新增**）：多核并行（sim / AI / 区块）**不破种子确定性**——lock-free + 顺序保持的确定性任务调度底座；engine.sim / C2ME 的地基 | pending / 待开工 |
| p.2.8 | World sim / 世界推演 engine.sim（**革命·新增**）：时间 / 物理 / 生态 / 经济 / 势力的**增量确定性模拟底座**——预算分级、分区空间索引、无全局扫描（接 engine.parallel p.2.7）；服务 Toterra 生态层（食物链 / 季节 / 迁徙）、势力扩张与贸易网络、自建聚落；与 engine.time（p.2.26）衔接 | pending / 待开工 |
| p.2.9 | World-as-artifact / 世界即产物（**革命·新增**）：export + save + 数据包 + 藏录一键打包为「世界包」，可搬移、可再水化（接 p.2.3 / p.2.18）；服务 Toterra 镜像层与存档互导 | pending / 待开工 |
| p.2.10 | Save verifiability / 存档可验证（**革命·新增**）：zd 区块 + ed25519 / tsha1f 签名，损坏自检与防篡改——存档 = 可验证账本（接 p.2.3） | pending / 待开工 |
| p.2.11 | Session programmable / 会话可编程（**革命·新增**）：外部进程（调试器 / 编辑器 / tie 脚本 / 工具）经 tink 帧驱动游戏运行时——tink hub 形态 B（接 p.2.5）；游戏成为可编程运行时 | pending / 待开工 |
| p.2.12 | Schema-first scaffolding（**革命·新增**）：td schema → 自动生成注册码 / 探针 / 文档 / 表单——模组编写从「写代码」变「写数据」 | pending / 待开工 |
| p.2.13 | Deterministic PCG / 确定性内容生成 engine.pcg（**革命·新增**）：名称 / 文本 / 配方 / 遗物 / 群系变种 / 本地化的**生成器编排底座**——纯函数、同种子一致（接 schema-first p.2.12 / worldgen）；「无尽探索」的框架化底座 | pending / 待开工 |
| p.2.14 | Diegetic interaction / engine.interact（**革命·新增**）：「无系统气味」的可落实交互层——世界内交互规范与零 HUD 化渲染规则（与 engine.ui p.2.22 调和，panels 在 p.2.22 仅作管理视图） | pending / 待开工 |
| p.2.15 | Engine migration + golden tests / engine 迁移（自原 p.2.2 顺移）：现有 optim / config / log 纯 JDK 核心迁入 engine.*；vanilla 数值对照固化为 golden tests 随改动回归 | pending / 待开工 |
| p.2.16 | API contract surface / api 契约面（自原 p.2.1 顺移）：worldgen / config(rules) / export / event 契约 + Exporter 注册表 + 规则注册 API | pending / 待开工 |
| p.2.17 | Rule system / 规则系统 engine.config.rules（自原 p.2.4 顺移，参考 RollingGate 模型）：类型化规则、双层配置（全局 + 存档覆盖）、校验、热重载、`/subterra rule` 命令 | pending / 待开工 |
| p.2.18 | Export hub / 导出器 engine.export（自原 p.2.3 顺移）：language-keys / config / world / registries / migrate-maps，td/zd 双格式，`/subterra export` 指令 | pending / 待开工 |
| p.2.19 | Runtime wiring migration / runtime 接线迁移（自原 p.2.5 顺移）：launch / runtime-fix / worldgen / optim-shell / tie / cfglog 迁入，export 指令接线（接 p.2.18） | pending / 待开工 |
| p.2.20 | devkit complete / devkit 完整化 + 官方示例模组脚手架（自原 p.3.1 前移，**可用判据**）：probes 全量接线 + 最小「颠覆性模组」示例（兼作框架自举验收） | pending / 待开工 |
| p.2.21 | Animation / 动画 engine.anim（自原 p.2.6 顺移）：GeckoLib 核心移植（MIT 声明 + 捆绑库声明）+ runtime anim binding | pending / 待开工 |
| p.2.22 | UI / HUD core / engine.ui（自原 p.2.7 顺移）：AppleSkin 数据层移植（Unlicense）+ ModMenu 交互模型借鉴（MIT）+ 数据驱动 tooltip（clean-room 参考 DataTip GPL-3.0，td 格式）+ 文档书籍 GUI（clean-room 参考 Patchouli CC-BY-NC-SA，td 驱动）+ 列表/详情/许可视图核心（面板仅作管理视图，玩家侧交互走 p.2.14 interact） | pending / 待开工 |
| p.2.23 | In-game mod hub / 游戏内模组 Hub（自原 p.2.8 顺移）：td schema 表单自动生成、配置编辑 + 热重载、入口接线、服务器端 op 配置命令 | pending / 待开工 |
| p.2.24 | AI behavior core / engine.ai（自原 p.2.9 顺移）：clean-room Brain 编排核心（参考 SmartBrainLib 模型）+ runtime ai binding | pending / 待开工 |
| p.2.25 | Entity scale / 实体缩放 engine.scale（自原 p.3.2 前移入主线）：Pehkui-Rebuilt 核心移植（MIT，保留 Virtuoel 原始版权与重建声明）：ScaleType/ScaleData/ScaleModifier 模型、按维度/标签缩放、数据包缩放规则 + runtime scale binding | pending / 待开工 |
| p.2.26 | Time scaling / 时间缩放 engine.time（自原 p.3.3 前移入主线，clean-room 自研）：TimeDomain 流速比例模型（全局/实体/区域/玩家域）、tick 预算确定性调度（BudgetScheduler）、逻辑级节流 + 感知级插值，TimeScaleApi + runtime 接线（接 engine.sim p.2.8） | pending / 待开工 |
| p.2.27 | Self-developed rendering / 自研渲染管线（自原 p.4.2 顺移，clean-room，参考 Sodium/Embeddium 等登记项） | pending / 待开工 |

## Sequence Notes / 顺序说明

* 小任务逐个提交：每完成一个小任务即报告并提交一次，得确认后再做下一个。 / Small tasks are submitted one at a time; each is reported and committed before the next starts.
* 所有子任务完成后统一 review + 清理 + 推送。 / After all sub-tasks complete: one review, cleanup, and push.
* 正式发行 r.x.y.z 从对应 p 版本切出，只做优化与稳定性验证，不引新功能。 / A release `r.x.y.z` is cut from its p version and only stabilizes.
* 许可纪律：permissive（MIT / Apache-2.0 / BSD / Unlicense / ISC）可移植并保留声明；copyleft（GPL / LGPL / AGPL / MPL）仅 clean-room 参考；存疑按不允许处理；完整登记册见框架设计 §7。 / License discipline: permissive may be ported with notices; copyleft is clean-room reference only; uncertain defaults to not allowed; full register in framework design §7.

## Port Allocation Map / 移植落点分配表

* 功能分类（非来源分类）：所有移植按功能域落进 engine.*（纯 JDK 核心）与 runtime.*（MC 层接线）；纯逻辑核心独立纯 JDK 包并可探针。 / Functional organization: every port lands in a functional domain under engine.* (pure-JDK core) or runtime.* (MC wiring); pure-logic cores stay standalone pure-JDK with probes.

| Pending target / 待移植目标 | Function / 功能 | Landing / 落点 | License path / 许可路径 | Status / 状态 |
| --- | --- | --- | --- | --- |
| GeckoLib | 3D 关键帧动画引擎（实体/方块/物品/盔甲） | engine.anim + runtime.anim | MIT 移植，保留声明与捆绑库声明 | planned / 已立项 |
| AppleSkin | HUD 数据层（饱食/饱和度覆盖、食物 tooltip） | engine.ui | Unlicense 全量移植 | planned / 已立项 |
| ModMenu | 模组列表交互模型与元数据 API 模式 | engine.ui + runtime.ui | MIT 借鉴（NeoForge 接线自研） | planned / 已立项 |
| SmartBrainLib | Brain 编排模型 | engine.ai | MPL-2.0 clean-room 参考 | planned / 已立项 |
| C2ME | 异步区块生成/加载/I/O + FlowSched | engine.worldgen.async + runtime | MIT 直接包含（NeoForge ver/1.21.1） | planned / 已立项（p.1.4.13 可选采纳 → 升级为直接包含） |
| Export-Language-Keys-for-Compasses | 语言键全量导出 | engine.export | MIT 借鉴 | planned / 已立项 |
| RollingGate | 规则系统模型（类型化规则 / 双层配置 / 命令面板） | engine.config.rules | LGPL-3.0 clean-room 模型参考；资产 CC-BY-NC-ND 不用 | planned / 已立项 |
| ServerCore 系列 | 实体激活范围 / mobcap / 村民脑死亡 / 同步加载 / 区块 tick 缓存 / 动态距离 / 繁殖合并 | engine.optim + runtime.optim-shell | MIT 部分直接 + GPL 部分 clean-room | landed / 已落地（p.1.4 全线） |
| JEC 读音搜索 | 语言无关读音搜索（zh/ja/ko/gr） | engine.optim.logic.pronounce + runtime.optim-shell | MIT | landed / 已落地（p.1.4.8-.10） |
| InvAdvOpt / SmoothBoot | 库存推进加速 / 线程调优 | runtime.optim-shell | MIT | landed / 已落地（p.1.4.4/.5） |
| Traveler Title / Weather / Super Resolution / 皮肤补丁 | 标题提示 / 天气 / 超分 / 皮肤（自研化） | engine.* + runtime.* | LGPL/GPL clean-room | planned / 已立项 |
| Physics Mod | 方块/生物物理 | — | All Rights Reserved，不适用 | dropped / 剔除 |
| TimeScaleLib | 时间缩放（子弹时间） | — | PolyForm Shield 非标准变体（Noncompete，存疑）→ 不适用；能力 clean-room 自研 | dropped / 剔除（自研替代） |
| DataTip | 数据驱动 tooltip（物品→tooltip 行） | engine.ui | GPL-3.0 clean-room 参考（td 化，零 json） | planned / 已立项 |
| Patchouli | 数据驱动书籍/文档 GUI（手册/图鉴/教程） | engine.ui.docbook | CC-BY-NC-SA 3.0 clean-room 参考（td 化，零 json） | planned / 已立项 |
| Feature Recycler / VoxelBridge / OptiCores | 待定义 | — | All Rights Reserved 或语义不匹配 | dropped / 剔除 |
| StellarRTP | 随机传送 | engine.optim.server.teleport | GPLv3 clean-room 参考 | planned / 已立项 |
| Itemban | 物品使用控制 | engine.optim.server.item_control | Apache-2.0 | pending / 待开工 |
| mcwifipnp | LAN 打洞 | runtime.network.holepunch | 移植为原生模块 | pending / 待开工 |
| Pehkui-Rebuilt | 实体尺寸缩放（ScaleType / ScaleData / ScaleModifier / ScaleRegistries、按维度/标签、数据包规则） | engine.scale + runtime.scale | MIT 移植，保留 Virtuoel 原始版权与重建声明 | planned / 已立项 |

## Progress Log / 进度记录

* 2026-09-08 — p.2.0 landed: module re-layout api / engine / runtime / migrate / devkit + IronLawProbe dependency iron law, probes green / 2026-09-08 — p.2.0 落地：api / engine / runtime / migrate / devkit 模块重构 + IronLawProbe 依赖铁律，探针全绿。
* 2026-09-08 — Patchouli assessed / Patchouli 评估：CC-BY-NC-SA 3.0 → 仅思想参考；文档书籍 GUI clean-room 自研（engine.ui.docbook，td 驱动零 json）。 / 2026-09-08 — Patchouli 评估：CC-BY-NC-SA 3.0 → 仅思想参考；文档书籍 GUI clean-room 自研（engine.ui.docbook，td 驱动零 json）。
* 2026-09-08 — DataTip assessed / DataTip 评估：GPL-3.0 → 仅思想参考；数据驱动 tooltip 数据层 clean-room 自研（engine.ui，td 化，零 json，与 AppleSkin 协同）。 / 2026-09-08 — DataTip 评估：GPL-3.0 → 仅思想参考；数据驱动 tooltip 数据层 clean-room 自研（engine.ui，td 化，零 json，与 AppleSkin 协同）。
* 2026-09-08 — TimeScaleLib assessed / TimeScaleLib 评估：PolyForm Shield 非标准变体（Noncompete）→ 不可移植，框架时间缩放能力 clean-room 自研（engine.time，p.3.3）。 / 2026-09-08 — TimeScaleLib 评估：PolyForm Shield 非标准变体（Noncompete）→ 不可移植，时间缩放能力 clean-room 自研（engine.time，p.3.3）。
* 2026-09-08 — Pehkui-Rebuilt registered / Pehkui-Rebuilt 入册：实体缩放能力纳入框架（engine.scale，MIT 移植，保留 Virtuoel 原始版权）。 / 2026-09-08 — Pehkui-Rebuilt 入册：实体缩放能力纳入框架（engine.scale，MIT 移植，保留 Virtuoel 原始版权）。
* 2026-09-08 — Roadmap split per repo / 路线图按仓库拆分：Subterra 框架线迁入本文件，系列 ROAD 仅保留总览。 / 2026-09-08 — 路线图按仓库拆分：Subterra 框架线迁入本文件，系列 ROAD 仅保留总览。
* 2026-09-08 — **Framework design finalized / 框架设计定稿**: Subterra promoted to a full-stack development framework mod (design `docs/2026-09-08-subterra-framework-design.md`); port register extended (GeckoLib / AppleSkin / ModMenu / SmartBrainLib / C2ME / Export-Language-Keys-for-Compasses / RollingGate; Physics Mod dropped as All Rights Reserved). / 2026-09-08 — **框架设计定稿**：Subterra 升格为全栈开发框架模组；移植登记册扩展（…；Physics Mod 因 All Rights Reserved 剔除）。
* 2026-09-08 — p.1.8.32 landed (perf: dedup climate-spline/sloped-cheese evals, CachedDensity single-slot ThreadLocal) — pre-framework line last item. / 2026-09-08 — p.1.8.32 落地（性能：气候样条/乳酪去重缓存）——框架化前最后一项。