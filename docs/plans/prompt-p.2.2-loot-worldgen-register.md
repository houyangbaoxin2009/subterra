# Subterra p.2.2 数据包重构（td 一等公民）· p.2.2.4：Loot 真合并 + Worldgen/Structure 真注册 · 开发任务提示词

## 0. 角色与目标
你是 Subterra（NeoForge 模组开发框架，服务 Toterra 主模组）的开发代理。
本次目标 = p.2.2 数据包重构的**p.2.2.4**：把已经能"构建出对象"的 loot_table 真正合并进原版 LootDataManager，并把 worldgen / structure 条目真正注册进原版注册表（RegisterEvent 链）。
工作方式：先小任务摸底/定最小闭环 → 逐个实现 → 每个小任务完成即提交（报告一句），全部完成后统一 review+清理+推送。

## 1. 项目上下文（必读）
- 系列聚合根 F:\Projects\Toterra-Repo：Subterra（框架，开源 TPL 2.0，origin=github.com/houyangbaoxin2009/subterra）+ Toterra（github.com/houyangbaoxin2009/toterra）。
- **已落地到 p.2.2.4 第 1 片**（提交见 git log）：
  * engine.datapack（纯 JDK 无 JSON）+ DatapackLoader 双发现 + TieLogicBundle + DatapackPack 打包往返。
  * runtime.datapack：DatapackRegistrar 已支持——tag/lang 索引、**crafting_shaped+smoking→RecipeManager.replaceRecipes 合并**、tie 0 参调用、**recipe 规范形往返（canonical ok markers：td→vanilla 对象→规范串逐字符一致）**、**loot_table→LootTable 对象构建（pools=N marker，尚未合并进 LootDataManager）**、worldgen/structure staged markers。
  * DatapackE2EProbe 13 断言 + DatapackProbe 31 断言全绿；probeAcceptance 全绿（含两次开服，mustRunAfter 串行）。
- 关键 API 事实（javap neoform jar 已核实）：LootTable.lootTable()/LootPool.lootPool()/setRolls(ConstantValue.exactly(f))/LootItem.lootTableItem(item).setWeight(w)；LootPool.Builder.add(Builder) 与 withPool(Builder) 均收 Builder 不收成品；ShapedRecipe.pattern 是 public final 字段（非方法）、无 pattern()/keyByChar() 访问器（被 refactor 进 Optional<Data> 且不公开）；ShapedRecipePattern.ingredients()/width()/height() 公开；AbstractCookingRecipe.getExperience()/getCookingTime() 公开、ingredient 字段 protected（用 getIngredients().get(0)）。
- 分层铁律：api ← engine ← runtime/migrate；engine 纯 JDK 不碰 MC；MC 壳落根 sourceSet（subterra-runtime source-host）；iron law 探针覆盖。
- RegisterEvent 先例：SubterraWorldgen.registerDensityFunctionType（modEventBus RegisterEvent → event.register(Registries.X, id, ()->codec)）；server registryAccess 可见性断言参考其 onServerStarting。

## 2. 本次范围（p.2.2.4）
1. **loot 真合并**：把 buildLootTable 产出的 LootTable 合并进原版装载链——首选 ServerResources 的 AddReloadListenerEvent（RecipeStripper 同款）在数据装载时把 td 表以 LootDataType.TABLE 语义并入 LootDataManager（先确认 LootDataManager 1.21.1 的注入面：javap LootDataManager getElement/parse；若公开注入面不存在，则记录与 lod 表并存策略：以 Resources 级监听器持有并在 ServerStarted 后通过 reflect/accessor 合并，或明确锁归 staged 并在本提示词更新中如实标注）。E2E 断言：开服后经 server.getLootData().getElement(key) 可查到 toterra:loot_table/chest/bonus 的 LootTable 且 pools 数正确。
2. **worldgen 真注册**：RegisterEvent（modEventBus，Registries.CONFIGURED_FEATURE 或 STRUCTURE_SET 中择一可实证的最小样例）——td worldgen 条目最小 schema → 注册进原版注册表 → E2E 用 server.registryAccess().registryOrThrow(...).containsKey(id) 断言可见。
3. **structure 真注册**：structure_set（或 structure）经 RegisterEvent 注册 + Registry 可见断言；若挂载复杂度超预算，保留 staged 索引 + 明确注释，如实标注（不硬塞）。
4. 探针：扩 DatapackE2EProbe 标记（loot 可见 / worldgen 可见 / structure 可见）；接 probeAcceptance。
5. 具体切分以摸底后的最小闭环为准；不确定范围先列出提问，不擅自扩大。

## 3. 子代理纪律（大任务必须用，禁止主代理硬扛全量）
- **大任务尽量拆分下发子代理**：每个子代理只领一个自足的小任务，提示词必须**自包含完整上下文**（路径/ABI/纪律写全）。
- 子代理节奏：**完成一个小任务即提交一次并报告**；全部完成后由它统一提交一次 push。
- 主代理 trust but verify：关键改动（build.gradle/新探针/注入核心）必须读实际文件核对，必要时重跑探针。
- **同文件多处修改禁止并行下发多个子代理**（并行 Edit 互覆盖丢内容，已踩坑多次）；同文件编辑串行单发，改后读文件核对；一次消息内同文件只允许一个 Edit。

## 4. 纪律（硬性，违反即返工）
- 主代理/子代理均：小任务逐个提交；全部完成后统一 review+清理+推送（GitHub）。
- 提交信息一律英文；作者保持本地 jiro；**只推 GitHub**（subterra origin），严禁 git.franj2.top。
- 版本 p.x.x.x 最多三级；禁用「阶段X」与 R1/R2 式标签。
- 文档双语（中英）；本任务尽量不新建文档，优先既有 docs/plans。
- 性能纪律：禁 O(n²)；热路径先建模；注入只在装载窗口做，不阻塞主线程热路径。
- 确定性：验收一律确定性探针；禁时序断言；断言前先做前置动作；失败计数只在失败路径自增。E2E 沿用事件驱动标记（Done 后装载完成/ServerStarted），不赌 sleep。
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
- 仓库根：F:\Projects\Toterra-Repo\Subterra；engine.datapack 在 `subterra-engine\...\engine\datapack\`（含 DatapackPack）；runtime.datapack 在 `subterra-runtime\...\runtime\datapack\`（DatapackRuntime/DatapackRegistrar）。
- RecipeStripper（AddReloadListenerEvent 参考）：`subterra-runtime\...\optim\server\item_control\shell\RecipeStripper.java`。
- SubterraWorldgen（RegisterEvent + registryAccess 断言参考）：`subterra-runtime\...\worldgen\gen\SubterraWorldgen.java`。
- 探针资产：`subterra-devkit\src\main\resources\datapack\mini_dp\`（seed：tag/lang/recipe×2/loot_table/worldgen/structure/function×2/extra）。
- probeAcceptance 接线：`subterra-devkit\build.gradle`（E2E 需 mustRunAfter bootProbe；staging 用 build/tmp；`-Psubterra.datapacks=<build/tmp/datapacks-e2e>` 经 runServer 转发）。
- tiec：F:\Projects\tie-repo\tie-main\dist\tie-Harbor-2026.1-preview.6\bin\tiec.exe。

## 7. 输出与汇报
- 主代理与各子代理均：双语说明（做了什么 + 结论）后立即提交；最后报 probeAcceptance 全量结果 + 新提示词文件路径。