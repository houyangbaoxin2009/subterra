# Subterra p.2.2 数据包重构（td 一等公民）· 第四块：深接线（Loot / Worldgen / Structure 真注入 + DataPack→td 导出）· 开发任务提示词

## 0. 角色与目标
你是 Subterra（NeoForge 模组开发框架，服务 Toterra 主模组）的开发代理。
本次目标 = p.2.2 数据包重构的**第四块**：把第三块已交付的三类 staged 索引（loot_table / worldgen / structure）**真正注入原版装载/注册链**，并补 **DataPack→td 导出往返**（从已装入的 RecipeManager 等内容导出为 td，再回灌，往返精确）。
工作方式：先小任务摸底/定最小闭环 → 逐个实现 → 每个小任务完成即提交（报告一句），全部完成后统一 review+清理+推送。

## 1. 项目上下文（必读）
- 系列聚合根 F:\Projects\Toterra-Repo：Subterra（框架，开源 TPL 2.0，origin=github.com/houyangbaoxin2009/subterra）+ Toterra（主内容模组，github.com/houyangbaoxin2009/toterra）。
- **前三块已落地**：
  * 第一块 engine.datapack（纯 JDK，无 JSON）：EntryKind 七类 / DatapackEntry / Datapack（按 id 排序）/ DatapackLoader（目录约定 + pack.td manifest 双发现）/ TieLogicLoader→TieLogicBundle（`<lib>$<fn>` FFM）/ DatapackProbe。
  * 第二块 MC 壳（runtime.datapack）：DatapackRuntime（ServerStarted/Stopping + resolveDatapacksDir：prop→findProperty→env→cwd）/ DatapackRegistrar：tag/lang 索引 + **crafting_shaped→ShapedRecipe→RecipeManager.replaceRecipes** + tie 0 参调用；DatapackE2EProbe（9→10 标记开服断言，与 bootProbe 用 mustRunAfter 串行；staging 隔离 build/tmp/datapacks-e2e）。
  * 第三块：**DatapackPack 打包往返**（目录↔单一 td 文档，base64 content，重载注册表一致）+ **smoking→SmokingRecipe**（buildRecipe 按 type 分派）+ 剩余三类 staged markers（`staged <id> (<kind>, vanilla injection deferred)`）。
- 技术栈：NeoForge 21.1.x / MC 1.21.1；全项目 Java 25；Gradle 9.2；Zulu 25。
- 关键 API 事实（javap neoform jar 已核实）：Registry.get(ResourceLocation)（非 getValue）；ShapedRecipe(String,CraftingBookCategory,ShapedRecipePattern,ItemStack,boolean) + ShapedRecipePattern.of(Map,List)；SmokingRecipe(String,CookingBookCategory,Ingredient,ItemStack,float,int)；RecipeManager.getRecipes/replaceRecipes(Iterable)。LootDataManager 的注入面在 ServerResources 装载链（AddReloadListenerEvent 模式已有 RecipeStripper 参考）。
- 分层铁律：api ← engine ← runtime/migrate；engine 纯 JDK 不碰 MC；MC 壳落根 sourceSet（subterra-runtime source-host）；iron law 探针覆盖。

## 2. 本次范围（第四块，深接线）
1. **loot_table 真注入**：td loot_table 条目 → 原版 LootDataManager（ServerResources 装载窗口，AddReloadListenerEvent 对齐 RecipeStripper；先定最小区间：chest 表桩 + 单 pool + roll，按 LootTable.builder() 构造）→ E2E 断言装载后 LootDataManager 可见（按 ResourceKey 查表非空）。
2. **worldgen 真注入**：RegisterEvent 挂载点确认（modEventBus 的 RegisterEvent，参考 SubterraWorldgen 注册 subterra:density 的先例）——td worldgen 条目最小样例注册进原版 WorldGen 注册表（候选：configured_feature 或 density_function 先择其一手动接线实证），E2E 断言条目在注册表可见（BuiltInRegistries/RegistryAccess 查询）。
3. **structure 真注入**：结构集/结构注册（RegisterEvent 挂 ServerProcessJigsawEvent 链或 data-driven 装载窗），最小样例 = structure_set 条目注册 + Registry 可见断言；若挂载复杂度超出该块预算，保留 staged 索引 + 明确注释，并在提示词更新中如实标注（不硬塞）。
4. **DataPack→td 导出往返**：从已装入的原版内容导出为 td（先行 RecipeManager：取回我们注册的 recipe + 若干原版 recipe → 转 td 表 → 经 DatapackPack/loader 或注册器回灌），断言导出→回灌→再导出逐字节一致（round-trip 精确）。这是「td 是一切数据内容的回程」的闭环证明。
5. 探针：给该块新增/扩展 E2E 标记与纯 JVM 往返断言；接 probeAcceptance。
6. 具体切分以摸底后的最小闭环为准；不确定范围先列出提问，不擅自扩大。

## 3. 子代理纪律（大任务必须用，禁止主代理硬扛全量）
- **大任务尽量拆分下发子代理**：每个子代理只领一个自足的小任务，提示词必须**自包含完整上下文**（路径/ABI/纪律写全）。
- 子代理节奏：**完成一个小任务即提交一次并报告**；全部完成后由它统一提交一次 push。
- 主代理 trust but verify：关键改动（build.gradle/新探针/注入核心）必须读实际文件核对，必要时重跑探针。
- **同文件多处修改禁止并行下发多个子代理**（并行 Edit 互覆盖丢内容，已踩坑多次）；同文件编辑串行单发，改后读文件核对。

## 4. 纪律（硬性，违反即返工）
- 主代理/子代理均：小任务逐个提交；全部完成后统一 review+清理+推送（GitHub）。
- 提交信息一律英文；作者保持本地 jiro；**只推 GitHub**（subterra origin），严禁 git.franj2.top。
- 版本 p.x.x.x 最多三级；禁用「阶段X」与 R1/R2 式标签。
- 文档双语（中英）；本任务尽量不新建文档，优先既有 docs/plans。
- 性能纪律：禁 O(n²)；热路径先建模；注入只在装载窗口做，不阻塞主线程热路径。
- 确定性：验收一律确定性探针；禁时序断言；断言前先做前置动作；失败计数只在失败路径自增。E2E 沿用事件驱动标记（Done 后 ServerStarted/装载完成），不赌 sleep。
- 编译纪律：改探针源后必须重编（compileJava/探针任务），否则 JavaExec 用旧字节码。
- 文件编辑纪律：同文件连续编辑必须串行单发、改后读文件核对；一次消息内同文件只允许一个 Edit。
- 依赖铁律：api ← engine ← runtime/migrate；engine 纯 JDK 不碰 MC；纯 JDK 落 engine.*，MC 壳落根 sourceSet。
- 许可：permissive 可移植带声明；copyleft 仅 clean-room；存疑按不允许（原版结构仅 clean-room 参考）。
- 铁律接线：settings.gradle include 与 iron law 探针覆盖；FFM 探针 JavaExec 需 `--enable-native-access=ALL-UNNAMED`。
- 验收门：`gradlew :subterra-devkit:probeAcceptance` 全绿（含 BootProbe 与 DatapackE2EProbe 两次开服）才算完成。

## 5. 模块完成 → 同格式提示词（延续机制，强制）
- 每完成一个开发模块后，必须生成一个"同格式"的新提示词并纳入记录：
  1. 更新上下文段：新增已落地项、最新 p 轨号、关键路径、ABI/接线变化；
  2. 更新本次范围段：指向**下一个**开发模块（按 p 轨顺序或当前策略）；
  3. 保持第 0/3/4 段结构不变；
  4. 存放：仓库 `docs/plans/prompt-<下一模块>.md`（bilingual），随当次提交一并提交。

## 6. 关键路径速查
- 仓库根：F:\Projects\Toterra-Repo\Subterra；engine.datapack 在 `subterra-engine\...\engine\datapack\`（含 DatapackPack）；runtime.datapack 在 `subterra-runtime\...\runtime\datapack\`（DatapackRuntime/DatapackRegistrar）。
- RecipeStripper（AddReloadListenerEvent + replaceRecipes 参考）：`subterra-runtime\...\optim\server\item_control\shell\RecipeStripper.java`。
- SubterraWorldgen（RegisterEvent 参考）：`subterra-runtime\...\worldgen\gen\SubterraWorldgen.java`。
- tiec：F:\Projects\tie-repo\tie-main\dist\tie-Harbor-2026.1-preview.6\bin\tiec.exe。
- 探针资产：`subterra-devkit\src\main\resources\datapack\mini_dp\` + `resources\tie\dp_logic_probe.{tie,dll}`；探针类 `probes\`（DatapackProbe / DatapackE2EProbe / ConfigPackProbe）。
- probeAcceptance 接线：`subterra-devkit\build.gradle`（E2E 需 mustRunAfter bootProbe；staging 用 build/tmp）。

## 7. 输出与汇报
- 主代理与各子代理均：双语说明（做了什么 + 结论）后立即提交；最后报 probeAcceptance 全量结果 + 新提示词文件路径。