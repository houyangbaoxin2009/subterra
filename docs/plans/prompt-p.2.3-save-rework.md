# Subterra p.2.3 存档重构（zd + td 混合，革命）· 全轨一次性交接单（p.2.3.1–p.2.3.6）· 开发任务提示词

## 0. 角色与目标
你是 Subterra（NeoForge 模组开发框架，服务 Toterra 主模组）的开发代理。
本提示词是一个**单会话一次性做完整个 p.2.3 里程碑**的交接单：p.2.3 =「现代化存档形态——**zd + td 混合替代原版 NBT `.dat`**（level / player / 侧数据），减小体积、加快加载（zd 压缩变体 / 字典 / 零拷贝）；统一 SaveContainer（世界 + 配置 + 藏录 Ledger + 领域档案 + 遗物条目 + 台账）；双层配置（全局 + 存档覆盖，接 p.2.17）；迁移走 subterra-migrate；元数据可导出（接 p.2.18 export）」（ROAD 表原文）。
**执行模式（与 p.2.2 收官完全一致）**：主代理只做任务下发与 trust-but-verify；摸底/设计、实现、探针、验收、文档、推送**全部由子代理执行**；每个子代领取一个自足小任务、完成即提交一次（报告一句）；全部完成后由收尾子代理统一 review+清理+推送（GitHub，仅 origin）。编号全程用 **p.2.3.x**（≤三级），禁止「阶段X / 第N块 / R1 / block N」等表述。
工作方式：先小任务摸底/定全轨最小闭环 → 逐子项实现 → 每个小任务完成即提交 → 全部子项完成后统一 review+清理+推送。

## 1. 项目上下文（必读）
- 系列聚合根 F:\Projects\Toterra-Repo：Subterra（框架，开源 TPL 2.0，origin=github.com/houyangbaoxin2009/subterra）+ Toterra（github.com/houyangbaoxin2009/toterra）。仓库内**当前不存在** save/zd 模块（`**/save/**`、`**/zd*/**` 无 java），p.2.3 基本为绿地。
- **p.2.2 数据包重构已全部落地并推 GitHub**（提交见 git log；编号 p.2.2.1–p.2.2.9）：
  * p.2.2.1 engine.datapack（纯 JDK 无 JSON）：EntryKind 七类 / DatapackEntry / Datapack / DatapackLoader / tie 逻辑链（TieLogicLoader→TieLogicBundle，`<lib>$<fn>` FFM）。
  * p.2.2.2 MC 壳（runtime.datapack）：DatapackRuntime（ServerStarted/Stopping）+ DatapackRegistrar：tag/lang 索引、recipe 合并、tie 0 参调用。
  * p.2.2.3 DatapackPack 打包往返 + smoking→SmokingRecipe + 剩余三类 staged。
  * p.2.2.4 loot 真合并 + worldgen/structure 真注册，`DatapackRegistryInjector` 公开 unfreeze→register→freeze 窗口。
  * p.2.2.5 DataPack→td 导出往返（recipe 先行），`RecipeDatum` + `DatapackExporter.exportRecipeTd`。
  * p.2.2.6 导出全七类 + 内容级导出档案 Hub（`DatapackExportArchive`，`type tie<data>` 单文档 `export=[version,entries]`，export∘rehydrate 逐字节恒等）。
  * p.2.2.7 `/subterra export [<path>]` 指令（op 级 2）：逐包经 `DatapackExportArchive` 导出 + export∘rehydrate 逐字节回灌校验；E2E 经确定性 `-Psubterra.probe.export` 属性钩子在启动即产 markers（**stdin 经 gradle-forked server JVM 双层中转不可靠，已弃用 stdin 触发**——p.2.3 的 E2E 确定性驱动一律走 `-P`/`-D` 转发属性钩子，不依赖 stdin 命令往返）。
  * p.2.2.8 数据包双接口（td + API）：engine `DatapackLookup`（entry/byKind/tdText）；tie 消费路径=导出 td + tiec parse_data 同构 schema（FFM 桥接见 p.2.1）。
  * p.2.2.9 数据包级规则 + 存档覆盖：pack.td `rules`（`[ [ k, v ] ]`，任意 td 值）→ engine `DatapackRules`（fromManifest/resolve/render；later pack 覆盖 earlier、save override 覆盖全部）；runtime 经 `subterra.override` 属性/文件读入存档覆盖；marker `rules <k> = <v>`。**这是 p.2.3 双层配置（全局 + 存档覆盖）的直接语义基座**，p.2.3.5 在其上扩展为全局/存档两级。
  * E2E 探针现断言 **31 markers**（seen[0..30]），probeAcceptance 全绿（含两次开服）。
- **zd 关键事实（已核实）**：tie-main 侧已实现 tdzd / zdwrite（`--compress-data`，`compiler/tdzd.tie` + `compiler/zdwrite.tie`）与 `std/tink.tie` 帧协议（crc32/frame_encode/frame_next/frame_skip）；zd v2 头 10 字节已定稿：`TIEDBZD`(7) + 2 位 base-48 版本（v2=0x00 0x02）+ 1 字节 flags（bit0 字典 / bit1 列式 / bit2 ext / bit3 流式 / bit4 压缩变体）；记录固定字段号 1=kind 2=key 3=value_i64 4=value_f64 5=value_str 6=child_count；扩展名统一 `.zd`。**Subterra 仓库内唯一 Java zd 触点是 `subterra-engine\...\engine\worldgen\profiler\report\ZdWriter.java`**（profiler 报告场景，已按深度优先树写 10 字节头 + 数据；WorldProfileProbe 断言其头字节精确）——p.2.3.1 应把该头部规范**抽出/泛化为 engine 通用 zd 载体**（勿改其语义，已有探针钉死），其余 zd 读写为纯 JDK 实现（字节构建、字典、列式可按最小子集）。
- **migrate 现状**：`subterra-migrate` 模块目前仅剩 `package-info.java`（占位）；p.2.3.5 填充其迁移管线（原版 `.dat` → zd/td 混合），迁移门禁铁律：migrate 仅依赖 api。
- **复用优先设施**：td（engine.config `Td`/`TdTable`/`TdValue`，parse_data 语义）；ConfigPack（engine.config，td 多文件打包/解包往返精确）；tiec（dist preview.6 捆绑 LLVM，解析/写 td 可用 tiec 同构 parse_data 委托，可选）；FFM 桥 engine.tie（p.2.1）；DatapackRules（p.2.2.9）；DatapackExportArchive（p.2.2.6，p.2.3.6 导出同构）。
- 分层铁律：api ← engine ← runtime/migrate；engine 纯 JDK 不碰 MC；MC 壳落根 sourceSet（subterra-runtime source-host）；iron law 探针覆盖（新增 engine.zd / engine.save 包自动被常量池扫描覆盖）。

## 2. 本次范围（p.2.3 全轨，六子项；一次性做完）
总原则：每子项=一个自足最小闭环（纯 JDK 优先、探针确定性命中验收；E2E 仅在真实生命周期必要处加确定性 `-P` 钩子 marker，**保持既有 31 markers 稳定**除非某子项真需在服断言）；每子项独立提交。子项划分如下，以摸底后最小闭环为准可微调，**不擅自扩大范围**；不确定处先列出提问再动手。

1. **p.2.3.1 engine.zd 载体 + SaveContainer 骨架（纯 JDK）**：zd v2 通用序列化载体（抽自 ZdWriter 头部规范：10 字节头精确 + 最小字段/记录读写 + 字典位/列式位按需实现子集，往返逐字节）；SaveContainer 统一持有者骨架（类型化槽：世界 / 配置 / 藏录 Ledger / 领域档案 / 遗物条目 / 台账 + 规则槽），允许逐槽挂载数据与规则覆盖（复用 DatapackRules 语义）。探针：zd 往返逐字节 + 槽位挂载 + 规则槽叠加。
2. **p.2.3.2 level .dat→zd 迁移 + runtime 壳最小接线**：读原版 level NBT `.dat` 的语义核心（世界名 / 种子 / 时间 / 规则等，字段以 javap 核实现存 API 为准），映射为 **zd 载荷 + td 元数据**混合文档（约定扩展名，如 `level.zdt`）；写读往返。runtime 壳在 ServerLevel/存档生命周期旁路挂载（默认不接管原版，渐进增强），E2E 用确定性 `-P` 钩子 marker。探针：迁移往返逐字段无损（纯 JVM 侧自测；真实 level.dat 解码按最小字段子集）。
3. **p.2.3.3 player / 侧数据迁移**：玩家数据与侧数据（每玩家/每器官/半径等，场所以摸底为准）→ zd 混合形；往返探针。
4. **p.2.3.4 藏录 Ledger + 领域档案 + 遗物条目 + 台账槽填充**：纯 JDK 数据模型 + td 元数据文档落槽；字段级往返探针（含确定性排序、防 O(n²)）。
5. **p.2.3.5 双层配置 + subterra-migrate 管线**：全局（datapacks/config 级）与存档覆盖（save 级）两层配置，扩展 `DatapackRules.resolve` 语义为两级（全局→存档，存档胜出）；`subterra-migrate` 填充首块真实迁移（原版 `.dat` 目录约定 → zd/td 落地，接口 stub→可执行入口），铁律：migrate 仅依赖 api。探针：配置优先级链 + 迁移往返。
6. **p.2.3.6 元数据导出（接 p.2.18 export hub）**：SaveContainer → td 导出档案（与 DatapackExportArchive 同构的 `SaveExportArchive` 或复用之，导出∘再水化逐字节）；可并入 `/subterra export` 或独立 `engine.export` 落点注释。探针：导出往返逐字节。
收尾：ROAD p.2.3 行状态 → `landed / 已落地` + 逐项落地方案 bullet（bilingual，p.2.3.x 编号）；生成 p.2.4 续接提示词（见 §5）。

## 3. 子代理纪律（大任务必须用，禁止主代理硬扛全量）
- **整个 p.2.3 由子代理集群执行**：摸底/设计（只读，产出全轨任务拆分）、engine 子代理、runtime 子代理、migrate 子代理、探针子代理、收尾子代理（门禁 + 文档 + 推送）。按文件归属拆分，保证**同一文件同一时刻只有一个子代理编辑**；有编译/API 依赖时严格串行（engine 先于 runtime/探测编译）。
- **子代理节奏**：完成一个小任务即提交一次并报告；全部完成后由收尾子代理统一 review+清理+提交一次 push。
- 主代理 trust but verify：关键改动（build.gradle/新探针/注入核心）必须读实际文件核对，必要时重跑探针。
- **同文件多处修改禁止并行下发多个子代理**（并行 Edit 互覆盖丢内容，已踩坑多次）；同文件编辑串行单发，改后读文件核对；一次消息内同文件只允许一个 Edit。

## 4. 纪律（硬性，违反即返工）
- 主代理/子代理均：小任务逐个提交；全部完成后统一 review+清理+推送（GitHub）。
- 提交信息一律英文；作者保持本地 jiro；**只推 GitHub**（subterra origin），严禁 git.franj2.top。
- 版本 p.x.x.x 最多三级；禁用「阶段X / 第N块 / R1/R2 / block N」标签，p.2.3 子项一律 **p.2.3.x**。
- 文档双语（中英）；本任务尽量不新建文档，优先既有 docs/plans（本文件即全轨交接单）。
- 性能纪律：禁 O(n²)；热路径先建模（存档写盘频率可调、只增量）；注入只在装载窗口做，不阻塞主线程热路径。
- 确定性：验收一律确定性探针；禁时序断言；断言前先做前置动作；失败计数只在失败路径自增。E2E 沿用事件驱动标记 + **`-P`/`-D` 转发属性钩子**（Done 后 ServerStarted/装载完成），不赌 sleep、不依赖 stdin 命令往返。
- 编译纪律：改探针源后必须重编（compileJava/探针任务），否则 JavaExec 用旧字节码。
- 文件编辑纪律：同文件连续编辑必须串行单发、改后读文件核对。
- 依赖铁律：api ← engine ← runtime/migrate；engine 纯 JDK 不碰 MC；纯 JDK 落 engine.*，MC 壳落根 sourceSet；migrate 仅依赖 api。
- 许可：permissive 可移植带声明；copyleft 仅 clean-room；存疑按不允许（原版/ NeoForge 内部先 javap 核实，不从他人实现照抄）。
- 铁律接线：settings.gradle include 与 iron law 探针覆盖；FFM 探针 JavaExec 需 `--enable-native-access=ALL-UNNAMED`。
- 验收门：`gradlew :subterra-devkit:probeAcceptance` 全绿（含 BootProbe 与 DatapackE2EProbe 两次开服）才算完成。

## 5. 模块完成 → 同格式提示词（延续机制，强制）
- p.2.3 全轨完成后，必须生成 p.2.4 同格式提示词并纳入记录：
  1. 更新上下文段：p.2.3 各子项已落地、最新 p 轨号（p.2.3.6）、关键路径（engine.save / engine.zd / runtime.save / migrate）、ABI/接线变化（zd 载体、SaveContainer、双层配置、migrate 入口）；
  2. 更新本次范围段：指向 p.2.4（服务端交互重构，自原 p.4.3 前移：tink v2 帧 + tsha1f 帧级强校验 + zd 载荷序列化，接 p.2.3 的 zd 载体）；
  3. 保持第 0/3/4 段结构不变；
  4. 存放：仓库 `docs/plans/prompt-p.2.4-server-interaction.md`（bilingual），随当次提交一并提交。

## 6. 关键路径速查
- 仓库根：F:\Projects\Toterra-Repo\Subterra；p.2.3 工作区建议：`subterra-engine\...\engine\zd\`（zd 载体）与 `subterra-engine\...\engine\save\`（SaveContainer / 各槽 / 迁移核心，纯 JDK）；`subterra-runtime\...\runtime\save\`（ServerLevel 生命周期壳，待发现接线点）；`subterra-migrate\src\main\java\...\migrate\`（p.2.3.5 填充）。
- **zd 头规范 Java 侧唯一钉点**：`subterra-engine\...\engine\worldgen\profiler\report\ZdWriter.java`（10 字节头；WorldProfileProbe 断言头字节精确，勿改语义）；tie-main 侧参考 `compiler/tdzd.tie` + `compiler/zdwrite.tie`（F:\Projects\tie-repo\tie-main）。
- 参考既有接线范式：engine.datapack（Datapack / DatapackEntry / DatapackRules / DatapackExportArchive / DatapackLookup）+ runtime.datapack（DatapackRuntime / DatapackRegistrar / DatapackExportCommand）+ 确定性钩子（build.gradle server 段 subterra.datapacks / subterra.override / subterra.probe.export）。
- 探针资产：`subterra-devkit\src\main\java\io\toterra\subterra\probes\`（DatapackProbe / DatapackE2EProbe，31 markers）；新 SaveProbe 落同处；probeAcceptance 接线：`subterra-devkit\build.gradle`（E2E 需 mustRunAfter bootProbe；staging 用 build/tmp）。
- tiec：F:\Projects\tie-repo\tie-main\dist\tie-Harbor-2026.1-preview.6\bin\tiec.exe（可选：td 读写委托 tiec 同构 parse_data；热点路径 tie 化见性能纪律）。

## 7. 输出与汇报
- 主代理与各子代理均：双语说明（做了什么 + 结论）后立即提交；收尾子代理报 probeAcceptance 全量结果（含 E2E markers 数与两次开服）+ ROAD p.2.3 landed + 新提示词文件路径 + push 结果；最终给用户汇报完整子项清单与验收证据。