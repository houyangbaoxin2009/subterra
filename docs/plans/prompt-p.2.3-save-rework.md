# Subterra p.2.3 存档重构（zd + td 混合，革命）· p.2.3.1：SaveContainer 骨架 + level 迁移 · 开发任务提示词

## 0. 角色与目标
你是 Subterra（NeoForge 模组开发框架，服务 Toterra 主模组）的开发代理。
本次目标 = p.2.3 存档重构的**p.2.3.1**：搭出**统一 SaveContainer 骨架**并把**世界 level 数据从原版 NBT `.dat` 迁移到 zd + td 混合形态**——先打一个最小闭环：一个统一持有者（世界 + 配置 + 藏录 Ledger + 领域档案 + 遗物条目 + 台账的容器骨架），一个 level 数据 zd 迁移路径，再加一个确定性探针断言迁移往返无损。p.2.3 整体以「zd + td 混合替代原版 NBT `.dat`（level / player / 侧数据），减小体积、加快加载」为目标，本块先落**第一小步**（SaveContainer + level .dat→zd），为后续 player / 侧数据与双层配置（全局 + 存档覆盖，接 p.2.17）铺轨。
工作方式：先小任务摸底/定最小闭环 → 逐个实现 → 每个小任务完成即提交（报告一句），全部完成后统一 review+清理+推送。

## 1. 项目上下文（必读）
- 系列聚合根 F:\Projects\Toterra-Repo：Subterra（框架，开源 TPL 2.0，origin=github.com/houyangbaoxin2009/subterra）+ Toterra（github.com/houyangbaoxin2009/toterra）。
- **p.2.2 数据包重构已全部落地**（六+三个子项，提交见 git log）：
  * p.2.2.1 engine.datapack（纯 JDK 无 JSON）：EntryKind 七类 / DatapackEntry / Datapack / DatapackLoader / tie 逻辑链（TieLogicLoader→TieLogicBundle，`<lib>$<fn>` FFM）。
  * p.2.2.2 MC 壳（runtime.datapack）：DatapackRuntime（ServerStarted/Stopping）+ DatapackRegistrar：tag/lang 索引、recipe 合并、tie 0 参调用。
  * p.2.2.3 DatapackPack 打包往返 + smoking→SmokingRecipe + 剩余三类 staged。
  * p.2.2.4 loot 真合并 + worldgen/structure 真注册，`DatapackRegistryInjector` 公开 unfreeze→register→freeze 窗口。
  * p.2.2.5 DataPack→td 导出往返（recipe 先行），`RecipeDatum` + `DatapackExporter.exportRecipeTd`。
  * p.2.2.6 导出全七类 + 内容级导出档案 Hub（`DatapackExportArchive`，`type tie<data>` 单文档 `export=[version,entries]`，export∘rehydrate 逐字节恒等）。
  * p.2.2.7 `/subterra export [<path>]` 指令接通（op 级 2），经 `DatapackExportArchive` 逐包导出为内容级 td 档案 + export∘rehydrate 逐字节回灌校验；E2E 经确定性钩子（`subterra.probe.export` 属性经 gradle 可靠转发）驱动，非 stdin 命令往返（stdin 经 gradle-forked server JVM 双层中转不可靠，已弃用 stdin 触发）；E2E 新增 2 marker。
  * p.2.2.8 数据包双接口（td + API）：engine `DatapackLookup`（entry/byKind/tdText）定型类型化读 API，api 直达同一 DatapackEntry 实例、td 路由逐字节等于导出规范形（tie 消费路径=导出 td + tiec parse_data 同构 schema，FFM 桥接见 p.2.1）；纯 JVM 探针断言双路径逐字节一致 + Td 可解析。
  * p.2.2.9 数据包级规则 + 存档覆盖：pack.td `rules`（`[ [ k, v ] ]`，任意 td 值）→ engine `DatapackRules`（fromManifest/resolve/render，确定性；later pack 覆盖 earlier、save override 覆盖全部）；runtime 经 `subterra.override` 属性或覆盖文件读入存档覆盖，logRules 输出 `rules <k> = <v>` 确定性 marker；接 p.2.17 规则系统前哨。
  * E2E 探针现断言 **31 markers**（seen[0..30]），probeAcceptance 全绿。
- 关键 API 事实（javap 已核实）：`ShapedRecipePattern` 无 key 访问器；`RegisterEvent` 不对 datapack registry 触发；datapack registry 数据装载时冻结；`MappedRegistry.unfreeze()/freeze()/register(key,value,RegistrationInfo.BUILT_IN)` 全公开。
- **p.2.3 里程碑文本**（ROAD 表内）：`zd + td 混合替代原版 NBT .dat（level / player / 侧数据），减小体积、加快加载（zd 压缩变体 / 字典 / 零拷贝）；统一 SaveContainer（世界 + 配置 + 藏录 Ledger + 领域档案 + 遗物条目 + 台账）；双层配置（全局 + 存档覆盖，接 p.2.17）；迁移走 subterra-migrate；元数据可导出（接 p.2.18 export）`。
- **p.2.3 依赖的前哨事实**：① p.2.1 tie 桥已落地（tiec → DLL → FFM 加载调用），可把归档/序列化热点路径 tie 化、也可用 tiec parse_data 把 td 解析/写出委托给 tie 同构实现；② p.2.2.9 数据包级规则已证明「存档覆盖优先」（save override 覆盖全部），p.2.3 的双层配置（全局 + 存档覆盖）直接在该语义上扩展为全局/存档两级。
- 分层铁律：api ← engine ← runtime/migrate；engine 纯 JDK 不碰 MC；MC 壳落根 sourceSet（subterra-runtime source-host）；iron law 探针覆盖。

## 2. 本次范围（p.2.3.1 / next module）
选中 p.2.3 轨（c，存档重构第一小步）：**SaveContainer 骨架 + level 数据 .dat→zd 迁移的最小闭环**。理由：p.2.2 已确立「td 是一切数据内容的回程」，p.2.3 是与之对称的「zd 存档动线」；从统一容器 + 一个可迁移文件类型（level）起手，闭环最短、语义衔接顺、无中间断层。
1. **SaveContainer 统一持有者骨架**：一个 engine 容器（纯 JDK）作为存档根，字段覆盖「世界 + 配置 + 藏录 Ledger + 领域档案 + 遗物条目 + 台账」的占位/槽位（先骨架：枚举/类型化槽 + 可挂载 Datapack 数据与规则覆盖），允许后续逐槽填充真实内容。
2. **level 数据 .dat→zd 迁移**：读原版 level 的 NBT `.dat`，把语义核心（世界名 / 种子 / 维生 / 规则）映射到 **zd 载荷 + td 元数据**的混合形态（zd 存二进制大字段/区块级数据，td 存可读 schema 化的语义字段），写出为 `level.zdt`（或等价约定）；定义读写往返。
3. **迁移最小闭环**：subterra-migrate 仅提供最小占位（入口/桩，不实现全部迁移逻辑），真实迁移逻辑先在 engine 探针内自测往返；保证「读出→写出→再读出」字段无损。
4. **探针**：`SaveProbe`（纯 JVM）断言 level 数据 zd 迁移往返逐字节/逐字段一致 + SaveContainer 槽位可挂载 + 规则覆盖槽可叠加（接 p.2.2.9 语义）；E2E 可不新增 markers（本块偏纯 JDK），也可沿用既有 E2E 视需要加确定性 marker；接 probeAcceptance。执行性（幂等/无功壳）优先，先最小闭环，不确定范围先列出提问，不擅自扩大。
5. 也可改走 (a) 纯 zd 载体（暂不引入 td 元数据）或 (b) SaveContainer 全槽一次铺满，但需一句说明为何改轨；默认按 (c) SaveContainer 骨架 + level 迁移最小闭环。

## 3. 子代理纪律（大任务必须用，禁止主代理硬扛全量）
- **大任务尽量拆分下发子代理**：每个子代理只领一个自足的小任务，提示词必须**自包含完整上下文**（路径/ABI/纪律写全）。
- 子代理节奏：**完成一个小任务即提交一次并报告**；全部完成后由它统一提交一次 push。
- 主代理 trust but verify：关键改动（build.gradle/新探针/注入核心）必须读实际文件核对，必要时重跑探针。
- **同文件多处修改禁止并行下发多个子代理**（并行 Edit 互覆盖丢内容，已踩坑多次）；同文件编辑串行单发，改后读文件核对；一次消息内同文件只允许一个 Edit。

## 4. 纪律（硬性，违反即返工）
- 主代理/子代理均：小任务逐个提交；全部完成后统一 review+清理+推送（GitHub）。
- 提交信息一律英文；作者保持本地 jiro；**只推 GitHub**（subterra origin），严禁 git.franj2.top。
- 版本 p.x.x.x 最多三级；禁「阶段X」与 R1/R2 式标签（p.2.3 子项用 p.2.3.x）。
- 文档双语（中英）；本任务尽量不新建文档，优先既有 docs/plans。
- 性能纪律：禁 O(n²)；热路径先建模；注入只在装载窗口做，不阻塞主线程热路径。
- 确定性：验收一律确定性探针；禁时序断言；断言前先做前置动作；失败计数只在失败路径自增。E2E 沿用事件驱动标记（Done 后 ServerStarted/装载完成），不赌 sleep；控制台 stdin 经 gradle-forked server JVM 不可靠，确定性驱动一律走 `-P`/`-D` 转发属性钩子，不依赖 stdin 命令往返。
- 编译纪律：改探针源后必须重编（compileJava/探针任务），否则 JavaExec 用旧字节码。
- 文件编辑纪律：同文件连续编辑必须串行单发、改后读文件核对。
- 依赖铁律：api ← engine ← runtime/migrate；engine 纯 JDK 不碰 MC；纯 JDK 落 engine.*，MC 壳落根 sourceSet。
- 许可：permissive 可移植带声明；copyleft 仅 clean-room；存疑按不允许（原版/ NeoForge 内部先 javap 核实，不从他人实现照抄）。
- 铁律接线：settings.gradle include 与 iron law 探针覆盖；FFM 探针 JavaExec 需 `--enable-native-access=ALL-UNNAMED`。
- 验收门：`gradlew :subterra-devkit:probeAcceptance` 全绿（含 BootProbe 与 DatapackE2EProbe 两次开服）才算完成。

## 5. 模块完成 → 同格式提示词（延续机制，强制）
- 每完成一个开发模块后，必须生成一个"同格式"的新提示词并纳入记录：
  1. 更新上下文段：新增已落地项、最新 p 轨号、关键路径、ABI/接线变化；
  2. 更新本次范围段：指向**下一个**开发模块（按 p 轨顺序或当前策略）；
  3. 保持第 0/3/4 段结构不变；
  4. 存放：仓库 `docs/plans/prompt-<下一模块>.md`（bilingual），随当次提交一并提交。

## 6. 关键路径速查
- 仓库根：F:\Projects\Toterra-Repo\Subterra；存档重构工作区建议落 engine 纯 JDK 包（`subterra-engine\...\engine\save\`，如 SaveContainer / SaveLevel 迁移 / zd 载体）与 runtime 壳（`subterra-runtime\...\runtime\save\`，接线 ServerLevel 生命周期，待发现）；`subterra-migrate` 迁移模块提供最小占位。
- 参考既有接线范式：engine.datapack（`subterra-engine\...\engine\datapack\`：Datapack / DatapackEntry / DatapackRules / DatapackExportArchive）+ runtime.datapack（`subterra-runtime\...\runtime\datapack\`：DatapackRuntime / DatapackRegistrar）。
- 探针资产：`subterra-devkit\src\main\java\io\toterra\subterra\probes\`（DatapackProbe / DatapackE2EProbe 等），新 SaveProbe 落同处；deterministic 钩子沿用 `-P`/`-D` 属性转发（见 build.gradle server 段 subterra.datapacks/override/probe.export）。
- probeAcceptance 接线：`subterra-devkit\build.gradle`（E2E 需 mustRunAfter bootProbe；staging 用 build/tmp）。
- tiec：F:\Projects\tie-repo\tie-main\dist\tie-Harbor-2026.1-preview.6\bin\tiec.exe（p.2.1 tie 桥已落地，可用同构 parse_data 做 td 读写委托）。

## 7. 输出与汇报
- 主代理与各子代理均：双语说明（做了什么 + 结论）后立即提交；最后报 probeAcceptance 全量结果 + 新提示词文件路径。