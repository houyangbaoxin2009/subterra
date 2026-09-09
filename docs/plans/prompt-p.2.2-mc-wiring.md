# Subterra p.2.2 数据包重构（td 一等公民）· p.2.2.2：MC 壳注册接线 · 开发任务提示词

## 0. 角色与目标
你是 Subterra（NeoForge 模组开发框架，服务 Toterra 主模组）的开发代理。
本次目标 = p.2.2 数据包重构的**p.2.2.2**：把 p.2.2.1 已落地的纯 td 直载注册表**拉到 MC 运行时**——数据包装载时机挂钩（开服装载全部 td 包）→ 条目注册进原版注册表（tag/lang/recipe 先行）→ E2E 确定性探针验证注册生效。
工作方式：先小任务摸底/定最小闭环 → 逐个实现 → 每个小任务完成即提交（报告一句），全部完成后统一 review+清理+推送。

## 1. 项目上下文（必读）
- 系列聚合根 F:\Projects\Toterra-Repo：Subterra（框架，开源 TPL 2.0，origin=github.com/houyangbaoxin2009/subterra）+ Toterra（主内容模组，github.com/houyangbaoxin2009/toterra）。
- **p.2.2.1 已落地（2026-09-09，提交 633b3ae/192fde1）**：
  * `subterra-engine/.../engine/datapack/`：`EntryKind`（七类 function/recipe/loot_table/worldgen/structure/tag/lang）+ `DatapackEntry`（kind/ns/path/payload，规范 id `ns:kind/path`）+ `Datapack`（按 id 确定性排序注册表 + tie 库声明）+ `DatapackLoader`（**双发现**：目录约定扫描 `data/<ns>/<kind>/<path>.td` + 可选 `pack.td` manifest：name/title/tie 库声明 `[lib,dll]`/增量条目 `[kind,ns,path,file]`；坏 td 抛错带文件路径；重复 id 抛错）+ `TieLogicLoader`→`TieLogicBundle`（AutoCloseable；FUNCTION 条目 payload `lib/fn` → 符号 `<lib>$<fn>`，fn 缺省=条目路径，未导出/未声明即抛）。
  * **决策定案：纯 td 直载，不产出 JSON、不依赖原版数据包管道**（原「td → 原版 JSON 兼容落位」改为「注册进内存注册表，由 MC 壳直接注册进原版注册表」）。
  * `DatapackProbe`（devkit，26 断言全绿，已接 probeAcceptance）：迷你包 mini_dp（9 条目 7 类）+ 捆绑 dll。
  * 顺带修复共享解析器 bug：`Td.headerStripped` 旧逻辑误杀 `type = "..."` 数据键（现仅剥 `type tie<...>` 头）；`Datapack.entries()` 不再用 `Map.copyOf`（丢序）。
- 技术栈：NeoForge 21.1.x / MC 1.21.1；**全项目 Java 25 字节码**（root `options.release=25`，api/engine/migrate/devkit toolchain 25）；Gradle 9.2；Zulu 25。
- 既有设施（复用优先，禁止绕路重造）：td（engine.config `Td`/`TdTable`/`TdValue`，parse_data 语义）；zd v2；tink v2 帧 + tsha1f；tiec（dist preview.6 捆绑 LLVM，免 env）；FFM 桥 engine.tie（TieLibrary/TieFunction/TieBridgeException）。
- tie 动态库 ABI：符号 `<namespace>$<fn>`；导出面 = 顶层/命名空间 pub func，仅标量(i64/f64/bool/trit/char)+string 跨边界；`type tie<class>` 头必须在第 1 行；私有 func 不导出。
- 分层铁律：api ← engine ← runtime/migrate；engine 纯 JDK 不碰 MC 类路径；**MC 壳（数据包登记、注册表写入、NeoForge 事件）落根 sourceSet**（F:\Projects\Toterra-Repo\Subterra\src\main\java\io\toterra\subterra）；设置侧 `run/config/subterra/*.td` 为配置样例（注意：`type = "..."` 数据键合法，不再被劫持）。

## 2. 本次范围（p.2.2.2，MC 壳注册接线）
1. **数据包装载时机**：开服装载钩子——在正确的生命周期（建议 `RegisterEvent`/世界数据包加载链）扫描 `datapacks/` 目录（或子目录）装载全部 td 包（复用 `DatapackLoader`），失败记录不崩服；包与包之间按 id 合并冲突策略明确（报错或后到覆盖，先定策略）。
2. **条目注册进原版注册表（先行三类）**：
   * tag → TagManager 注册（td values/replace 语义映射原版 HolderSet）；
   * lang → 本地化注册（td k/v 对注入 LanguageManager 或资源）；
   * recipe → RecipeManager 注册（td recipe 表 → 原版 Recipe，先做 crafting_shaped 一类即算闭环）。
   * loot_table/worldgen/structure/function：register 壳仅为占位 + 明确「下一模块再接线」注释，不在本块硬做全 schema。
3. **tie 逻辑链路进 MC**：FUNCTION 条目经 TieLogicBundle 装载后，注册为可被 MC 侧调用的事件函数（入参出参按 ABI 标量）；最小闭环为「装载成功 + 符号存在 + 调用一次」的 E2E 断言，不接具体 MC 事件。
4. **E2E 确定性探针**：接 bootProbe 风格——开服装载一个真实 td 数据包，断言：注册表条目数、tag 在 TagManager 可见、recipe 在 RecipeManager 可见、tie 符号可调用。探针自包含资源（bundled dll 不进 E2E，探针用 tie 逻辑以 i64/i64 标量验证）。
5. 具体切分以摸底后的最小闭环为准；不确定范围先列出提问，不擅自扩大。

## 3. 子代理纪律（大任务必须用，禁止主代理硬扛全量）
- **大任务尽量拆分下发子代理**：每个子代理只领一个自足的小任务，提示词必须**自包含完整上下文**（它看不到本会话，路径/ABI/纪律都要写全）。
- 子代理节奏：**完成一个小任务即提交一次并报告**；它负责的所有小任务完成后，由它统一提交一次 push。
- 主代理收到的子代理汇报是"意图"而非"事实"——**trust but verify**：关键改动（build.gradle/新探针/装载器核心）必须读实际文件核对，必要时重跑探针。
- **同文件的多处修改禁止并行下发多个子代理**（并行 Edit 会互相覆盖丢内容，已踩坑）；同一文件编辑串行单发，改后读文件核对。

## 4. 纪律（硬性，违反即返工）
- 主代理/子代理均：小任务逐个提交；全部完成后统一 review+清理+推送（GitHub）。
- 提交信息一律英文；作者保持本地 jiro；**只推 GitHub**（subterra origin），严禁 git.franj2.top。
- 版本 p.x.x.x 最多三级；禁用「阶段X」与 R1/R2 式标签。
- 文档双语（中英）；本任务尽量不新建文档，优先既有 docs/plans。
- 性能纪律：禁 O(n²)；热路径先建模。注册链注意原版注册表写权限窗口（只在对应阶段注入）。
- 确定性：验收一律确定性探针；禁时序断言；断言前先做前置动作；失败计数只在失败路径自增。E2E 探针沿用事件驱动断言（如 ServerStarted 完成标记），不赌 sleep。
- 编译纪律：改探针源后必须重编（compileJava/探针任务），否则 JavaExec 用旧字节码。
- 文件编辑纪律：同一文件连续编辑必须串行单发、改后读文件核对。
- 依赖铁律：api ← engine ← runtime/migrate；engine 纯 JDK 不碰 MC 类路径；纯 JDK 核心落 engine.*，MC 壳落根 sourceSet。
- 许可：permissive 可移植带声明；copyleft 仅 clean-room；存疑按不允许。
- 铁律接线：settings.gradle include 与 iron law 探针（p.2.0.10）覆盖新模块；FFM 探针 JavaExec 需 `--enable-native-access=ALL-UNNAMED`。
- 验收门：`gradlew :subterra-devkit:probeAcceptance` 全绿（含 BootProbe 开服）才算完成。

## 5. 模块完成 → 同格式提示词（延续机制，强制）
- 每完成一个开发模块（p.x.y 或探针级闭环）后，必须生成一个"同格式"的新提示词并纳入记录：
  1. 更新上下文段：新增已落地项、最新 p 轨号、关键路径、ABI/接线变化；
  2. 更新本次范围段：指向**下一个**开发模块（按 p 轨顺序或当前策略）；
  3. 保持第 0/3/4 段结构不变；
  4. 存放：仓库 `docs/plans/prompt-<下一模块>.md`（bilingual 标题可中文正文），并随当次提交一并提交。

## 6. 关键路径速查
- 仓库根：F:\Projects\Toterra-Repo\Subterra；settings.gradle 已含 api/engine/migrate/devkit；根 sourceSet MC 壳在 `src/main/java/io/toterra/subterra`。
- engine.datapack：`subterra-engine\src\main\java\io\toterra\subterra\engine\datapack\`
- tiec：F:\Projects\tie-repo\tie-main\dist\tie-Harbor-2026.1-preview.6\bin\tiec.exe。
- engine.tie：`subterra-engine\...\engine\tie\{TieLibrary,TieFunction,TieBridgeException}.java`。
- 探针资源样例：`subterra-devkit\src\main\resources\datapack\mini_dp\`（pack.td + data/）与 `resources\tie\`（tiefib_probe / dp_logic_probe）。
- probeAcceptance 接线：`subterra-devkit\build.gradle`（每探针一个 JavaExec + probeAcceptance dependsOn 行；bootProbe workingDir=rootProject；E2E 新探针照抄 bootProbe 的 workingDir/systemProperty 风格）。

## 7. 输出与汇报
- 主代理与各子代理均：双语说明（做了什么 + 结论）后立即提交；最后报 probeAcceptance 全量结果 + 新提示词文件路径。