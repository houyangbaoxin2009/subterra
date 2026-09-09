# Subterra p.2.2 数据包重构（td 一等公民）· 第五块：DataPack→td 导出往返 · 开发任务提示词

## 0. 角色与目标
你是 Subterra（NeoForge 模组开发框架，服务 Toterra 主模组）的开发代理。
本次目标 = p.2.2 数据包重构的**第五块**：补 **DataPack→td 导出往返**——从已装入的原版内容（先行 RecipeManager）导出为 td，再经加载链回灌，导出→回灌→再导出逐字节一致（round-trip 精确）。这是「td 是一切数据内容的回程」的闭环证明。
工作方式：先小任务摸底/定最小闭环 → 逐个实现 → 每个小任务完成即提交（报告一句），全部完成后统一 review+清理+推送。

## 1. 项目上下文（必读）
- 系列聚合根 F:\Projects\Toterra-Repo：Subterra（框架，开源 TPL 2.0，origin=github.com/houyangbaoxin2009/subterra）+ Toterra（github.com/houyangbaoxin2009/toterra）。
- **前四块已落地**（提交见 git log）：
  * 第一块 engine.datapack（纯 JDK 无 JSON）：EntryKind 七类 / DatapackEntry / Datapack（按 id 排序）/ DatapackLoader（目录约定 + pack.td manifest 双发现）/ TieLogicLoader→TieLogicBundle（`<lib>$<fn>` FFM）/ DatapackPack 打包往返 / DatapackProbe。
  * 第二块 MC 壳（runtime.datapack）：DatapackRuntime（ServerStarted/Stopping + resolveDatapacksDir）+ DatapackRegistrar：tag/lang 索引、**crafting_shaped+smoking→RecipeManager.replaceRecipes 合并**、tie 0 参调用；DatapackE2EProbe 开服断言（mustRunAfter bootProbe 串行；staging 隔离 build/tmp/datapacks-e2e）。
  * 第三块：DatapackPack 打包往返 + smoking→SmokingRecipe + 剩余三类 staged markers。
  * **第四块（本块已完成）**：loot 真合并 + worldgen/structure 真注册——`DatapackRegistryInjector`（新增，MC 壳）以**公开 unfreeze→register→freeze 窗口**向冻结的 datapack registry 注入；`DatapackRegistrar.buildLootTables` 把 td chest 表合并进 **LOOT_TABLE registry**（1.21.1 无 LootDataManager，注册表在 `ReloadableServerResources.fullRegistries().get()` 的 RELOADABLE 层，**不在 `server.registryAccess()`**——后者不含 RELOADABLE 层，曾踩 `Missing registry: minecraft:loot_table`）；`registerWorldgen` 注册 `ConfiguredFeature`（td configured_feature 最小 schema：`feature="minecraft:simple_block"` + `block`）进 CONFIGURED_FEATURE；`registerStructures` 注册 `Structure`（DesertPyramidStructure 体 + biomes holder，BIOME 也是 datapack registry，经 `registryAccess().registryOrThrow(Registries.BIOME)` 解析）进 STRUCTURE，structure_set schema 再组 `StructureSet` 进 STRUCTURE_SET。seed 资产：worldgen 文件更名 `worldgen/configured_feature/meadow_of_tie.td`；shrine.td 改 structure_set schema；`devkit:obligatory_tower` 保留 `minecraft:structure` schema。DatapackE2EProbe 13→16 标记全绿。
- 关键 API 事实（javap neoform jar 已核实）：`RegisterEvent` **不对 datapack registry 触发**（`GameData.postRegisterEvents` 只遍历静态 BuiltInRegistries；默认 registryBuilderConsumer 为空操作）；datapack registry 在数据装载时冻结（loot 于 `ReloadableServerRegistries.apply`、worldgen 于 `WorldLoader.load`，均早于 ServerStarted）；`MappedRegistry.unfreeze()`/`freeze()`/`register(key,value,RegistrationInfo.BUILT_IN)` 全公开；`LootTable` 无 `getPools()` 公开访问器（pools 计数由构建侧 `BuiltLoot` record 携带）；`Registry.containsKey(ResourceLocation)` / `getHolderOrThrow(ResourceKey)` 公开。
- 分层铁律：api ← engine ← runtime/migrate；engine 纯 JDK 不碰 MC；MC 壳落根 sourceSet（subterra-runtime source-host）；iron law 探针覆盖。

## 2. 本次范围（第五块）
1. **导出器核心**：新增纯 JDK `engine.datapack` 导出器（或复用现有 DatapackPack/EntryKind 表示）：从已装入的原版内容 → td 条目（DatumEntry 同构）。先定最小区间：**RecipeManager 导出**——取回 Subterra 注册的 recipe（`to terra:recipe/example` / `smoke`）+ 若干原版 recipe → 转 td 表（类型/pattern/key/result/ingredient/experience/cooking_time 与现有 tdCanonical 一致）。
2. **回灌往返**：导出 td → 经 DatapackLoader/DatapackRegistrar 或注册器回灌 → 再导出 → 断言**逐字节一致**（round-trip 精确；参考 DatapackPack 的 `Td.write` 逐字比较先例）。
3. **探针**：新增/扩展纯 JVM 往返断言（导出→回灌→再导出逐字节一致），接 probeAcceptance。
4. 具体切分以摸底后的最小闭环为准；不确定范围先列出提问，不擅自扩大。

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
- 确定性：验收一律确定性探针；禁时序断言；断言前先做前置动作；失败计数只在失败路径自增。E2E 沿用事件驱动标记（Done 后 ServerStarted/装载完成），不赌 sleep。
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
- 仓库根：F:\Projects\Toterra-Repo\Subterra；engine.datapack 在 `subterra-engine\...\engine\datapack\`（含 DatapackPack / EntryKind / DatapackEntry）；runtime.datapack 在 `subterra-runtime\...\runtime\datapack\`（DatapackRuntime / DatapackRegistrar / **DatapackRegistryInjector**）。
- 探针资产：`subterra-devkit\src\main\resources\datapack\mini_dp\`（seed：tag/lang/recipe×2/loot_table/worldgen(configured_feature)/structure×2/function×2/extra）；探针 `probes\DatapackProbe`（纯 JVM）/ `DatapackE2EProbe`（16 标记开服）。
- RecipeManager 注入/合并参考：`DatapackRegistrar.registerRecipes`（replaceRecipes）+ `registerTdCanonical`（tdCanonical 规范串，导出器应复用同一套字段语义）。
- probeAcceptance 接线：`subterra-devkit\build.gradle`（E2E 需 mustRunAfter bootProbe；staging 用 build/tmp）。
- tiec：F:\Projects\tie-repo\tie-main\dist\tie-Harbor-2026.1-preview.6\bin\tiec.exe。

## 7. 输出与汇报
- 主代理与各子代理均：双语说明（做了什么 + 结论）后立即提交；最后报 probeAcceptance 全量结果 + 新提示词文件路径。
