# Subterra p.2.2 数据包重构（td 一等公民）· 第三块：剩余四类注册 + 打包往返 · 开发任务提示词

## 0. 角色与目标
你是 Subterra（NeoForge 模组开发框架，服务 Toterra 主模组）的开发代理。
本次目标 = p.2.2 数据包重构的**第三块**：把第二块已跑通的「td 直载 → 原版注册」接线**补齐剩余四类**（loot_table / worldgen / structure / 更多 recipe 类型），并打通**数据包打包分发（接 engine.config ConfigPack）与配方/战利品往返**。
工作方式：先小任务摸底/定最小闭环 → 逐个实现 → 每个小任务完成即提交（报告一句），全部完成后统一 review+清理+推送。

## 1. 项目上下文（必读）
- 系列聚合根 F:\Projects\Toterra-Repo：Subterra（框架，开源 TPL 2.0，origin=github.com/houyangbaoxin2009/subterra）+ Toterra（主内容模组，github.com/houyangbaoxin2009/toterra）。
- **第一块已落地**（probe 级闭环，提交 b176587/633b3ae/192fde1）：engine.datapack 容器+注册表 + tie 逻辑链（EntryKind 七类 / Datapack / DatapackLoader 双发现 / TieLogicBundle），纯 td 直载无 JSON；DatapackProbe 26 断言。
- **第二块已落地**（MC 壳接线，提交 fdfb00c/aea4631/<recipe+tie+E2E>）：runtime.datapack（DatapackRuntime 挂 ServerStarted/Stopping；DatapackRegistrar 扫描装载 + 四类注册）；tag/lang 索引 + 确定性 markers；**recipe（crafting_shaped）编译进原版 ShapedRecipe → RecipeManager.replaceRecipes 合并**；**function 经 TieLogicLoader/TieLogicBundle → engine.tie 0 参调用**；DatapackE2EProbe 开服加载 seed 包断言 9 标记全绿（57s）。build.gradle runServer 转发 `subterra.datapacks`。
- 技术栈：NeoForge 21.1.x / MC 1.21.1；全项目 Java 25；Gradle 9.2；Zulu 25。
- 既有设施：td（engine.config）；zd v2；tink v2；tiec（dist preview.6 捆绑 LLVM）；FFM 桥 engine.tie；**ConfigPack（engine.config，td 多文件打包/解包，往返精确）**——数据包打包分发直接复用。
- tie ABI：符号 `<namespace>$<fn>`；仅标量+string 跨边界；`type tie<class>` 第 1 行；私有不导出。
- 分层铁律：api ← engine ← runtime/migrate；engine 纯 JDK 不碰 MC；MC 壳落根 sourceSet（subterra-runtime source-host）。

## 2. 本次范围（第三块）
1. **loot_table 注册**：td 声明 → 原版 LootDataManager 注册（最小闭环建议一个 vanilla 桩表类型：minecraft:chest + 单一 pool/bonus roll；或先只做「注册表可见 + 生命周期挂载」占位并明确下一子项的 schema 面）。实时性确认：LootData 装载窗口 = 服务端 datapack reload（ServerResources），与 RecipeManager 的 addReloadListener 模式对齐。
2. **worldgen / structure 注册**：这两类走 Registry/Worldgen 注册链（BiomeSource/StructureSet/ConfiguredFeature 等），确认挂载点（RegisterEvent 阶段）后至少打通「td 条目 → 原版注册物」各一个最小样例（如 biome 相关或 structure_set）；若挂载点复杂，先以「注册表可见 + 明确注释的接线点」交付。
3. **recipe 类型扩展 + 往返**：在第一块 crafting_shaped 之外补 cooking/smelting（单类即可）与/或 shapeless；DataPack→td 往返：从已装载的原版 RecipeManager 内容导出为 td（复用 ConfigPack 往返模式，round-trip 精确断言）。
4. **数据包打包分发（接 ConfigPack）**：DataPackPack = 目录 → ConfigPack.export（多文件 td 单文档）；DataPackUnpack = ConfigPack.parse → 临时目录 → DatapackLoader.load；确定性断言打包→解包→装载往返一致（含 pack.td manifest 与 tie 库声明携带）。
5. **E2E/探针**：第三块探针接 probeAcceptance（纯 JVM 探针覆盖打包往返与 recipe 扩展；世界生成/structure 的 E2E 视挂载复杂度决定是否进开服门禁）。
6. 具体切分以摸底后的最小闭环为准；不确定范围先列出提问，不擅自扩大。

## 3. 子代理纪律（大任务必须用，禁止主代理硬扛全量）
- **大任务尽量拆分下发子代理**：每个子代理只领一个自足的小任务，提示词必须**自包含完整上下文**（它看不到本会话，路径/ABI/纪律都要写全）。
- 子代理节奏：**完成一个小任务即提交一次并报告**；它负责的所有小任务完成后，由它统一提交一次 push。
- 主代理收到的子代理汇报是"意图"而非"事实"——**trust but verify**：关键改动（build.gradle/新探针/装载器核心）必须读实际文件核对，必要时重跑探针。
- **同文件的多处修改禁止并行下发多个子代理**（并行 Edit 会互相覆盖丢内容，已踩坑多次）；同一文件编辑串行单发，改后读文件核对。

## 4. 纪律（硬性，违反即返工）
- 主代理/子代理均：小任务逐个提交；全部完成后统一 review+清理+推送（GitHub）。
- 提交信息一律英文；作者保持本地 jiro；**只推 GitHub**（subterra origin），严禁 git.franj2.top。
- 版本 p.x.x.x 最多三级；禁用「阶段X」与 R1/R2 式标签。
- 文档双语（中英）；本任务尽量不新建文档，优先既有 docs/plans。
- 性能纪律：禁 O(n²)；热路径先建模；**活体注册链只在该装载窗口注入，不阻塞主线程热路径**。
- 确定性：验收一律确定性探针；禁时序断言；断言前先做前置动作；失败计数只在失败路径自增。E2E 沿用事件驱动断言（Done 后 ServerStarted 标记），不赌 sleep。
- 编译纪律：改探针源后必须重编（compileJava/探针任务），否则 JavaExec 用旧字节码。
- 文件编辑纪律：同一文件连续编辑必须串行单发、改后读文件核对；**一次消息内同文件只允许一个 Edit**。
- 依赖铁律：api ← engine ← runtime/migrate；engine 纯 JDK 不碰 MC 类路径；纯 JDK 核心落 engine.*，MC 壳落根 sourceSet。
- 许可：permissive 可移植带声明；copyleft 仅 clean-room；存疑按不允许（loot/worldgen 相关原版结构仅作 clean-room 参考）。
- 铁律接线：settings.gradle include 与 iron law 探针（p.2.0.10）覆盖新模块；FFM 探针 JavaExec 需 `--enable-native-access=ALL-UNNAMED`。
- 验收门：`gradlew :subterra-devkit:probeAcceptance` 全绿（含 BootProbe 与 DatapackE2EProbe 两次开服）才算完成。

## 5. 模块完成 → 同格式提示词（延续机制，强制）
- 每完成一个开发模块后，必须生成一个"同格式"的新提示词并纳入记录：
  1. 更新上下文段：新增已落地项、最新 p 轨号、关键路径、ABI/接线变化；
  2. 更新本次范围段：指向**下一个**开发模块（按 p 轨顺序或当前策略）；
  3. 保持第 0/3/4 段结构不变；
  4. 存放：仓库 `docs/plans/prompt-<下一模块>.md`（bilingual），随当次提交一并提交。

## 6. 关键路径速查
- 仓库根：F:\Projects\Toterra-Repo\Subterra；engine.datapack 在 `subterra-engine\...\engine\datapack\`；runtime.datapack 在 `subterra-runtime\...\runtime\datapack\`。
- ConfigPack：`subterra-engine\...\engine\config\ConfigPack.java`（export/parse，VERSION=1）。
- tiec：F:\Projects\tie-repo\tie-main\dist\tie-Harbor-2026.1-preview.6\bin\tiec.exe。
- 探针资源：`subterra-devkit\src\main\resources\datapack\mini_dp\` + `resources\tie\dp_logic_probe.{tie,dll}`；探针类在 `probes\`（DatapackProbe / DatapackE2EProbe / ConfigPackProbe 是打包往返的既有先例）。
- probeAcceptance 接线：`subterra-devkit\build.gradle`。

## 7. 输出与汇报
- 主代理与各子代理均：双语说明（做了什么 + 结论）后立即提交；最后报 probeAcceptance 全量结果 + 新提示词文件路径。