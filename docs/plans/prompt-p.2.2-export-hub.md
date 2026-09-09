# Subterra p.2.2 数据包重构（td 一等公民）· 第六块：DataPack 导出扩展 + 导出档案 Hub（接 p.2.18 前哨）· 开发任务提示词

## 0. 角色与目标
你是 Subterra（NeoForge 模组开发框架，服务 Toterra 主模组）的开发代理。
本次目标 = p.2.2 数据包重构的**第六块**：把第五块证明可行的「导出→回灌→再导出逐字节一致」闭环**扩展到全部七类条目**，并把导出产物收敛为一个**数据包导出档案**（registered content → 单一 td 文档，可再水化 = 经 DatapackLoader/DatapackRegistrar 复原），为 p.2.18 `engine.export` 导出 Hub 打前哨。第五块的 closed-loop 假说「td 是一切数据内容的回程」从 recipe 一种推广到所有种类。
工作方式：先小任务摸底/定最小闭环 → 逐个实现 → 每个小任务完成即提交（报告一句），全部完成后统一 review+清理+推送。

## 1. 项目上下文（必读）
- 系列聚合根 F:\Projects\Toterra-Repo：Subterra（框架，开源 TPL 2.0，origin=github.com/houyangbaoxin2009/subterra）+ Toterra（github.com/houyangbaoxin2009/toterra）。
- **前五块已落地**（提交见 git log）：
  * 第一块 engine.datapack（纯 JDK 无 JSON）：EntryKind 七类 / DatapackEntry / Datapack（按 id 排序）/ DatapackLoader（目录约定 + pack.td manifest 双发现）/ TieLogicLoader→TieLogicBundle（`<lib>$<fn>` FFM）/ DatapackProbe。
  * 第二块 MC 壳（runtime.datapack）：DatapackRuntime（ServerStarted/Stopping + resolveDatapacksDir）+ DatapackRegistrar：tag/lang 索引、crafting_shaped+smoking→RecipeManager.replaceRecipes 合并、tie 0 参调用；DatapackE2EProbe 开服断言。
  * 第三块：DatapackPack 打包往返 + smoking→SmokingRecipe + 剩余三类 staged markers。
  * 第四块：loot 真合并（LOOT_TABLE datapack registry，`ReloadableServerResources.fullRegistries().get()` RELOADABLE 层）与 worldgen/structure 真注册（CONFIGURED_FEATURE / STRUCTURE / STRUCTURE_SET），`DatapackRegistryInjector` 公开 unfreeze→register→freeze 窗口；E2E 16 标记。
  * **第五块（本块已完成）**：DataPack→td 导出往返（recipe 先行）——engine.datapack `RecipeDatum`（record：read/write 规范 td，count/cooking_time clamp≥1、experience 经 `cleanExperience` float 往返归一化，镜像 buildShaped/buildSmoking 语义）+ `DatapackExporter.exportRecipeTd` 导出前门；runtime `DatapackRecipeExporter.exportTd(holder, ra)`（holder→datum；ShapedRecipe 对象不保留源字符，pattern 行按格首次出现确定性重建 A,B,C…）+ `DatapackRegistrar.runExportRoundTrip()`（导出→buildRecipe 回灌→再导出，字节级比对，marker `export roundtrip <id> ok (bytes=N)`）；纯 JVM 探针：导出→DatapackLoader 文件重载→再导出逐字节一致 + 三连稳定 + 导出字段无损（=源 payload 序列化）；E2E 标记 16→18 全绿；probeAcceptance 全绿（2m 15s，两次开服）。
- 关键 API 事实（javap neoform jar 已核实）：`ShapedRecipePattern` 无 key 访问器（仅 width()/height()/ingredients()）；`ShapedRecipe.pattern` public final；`Ingredient.isEmpty()`/`getItems()` 公开；`SmokingRecipe.getExperience()/getCookingTime()` 公开；`RegisterEvent` 不对 datapack registry 触发；datapack registry 数据装载时冻结；`MappedRegistry.unfreeze()/freeze()/register(key,value,RegistrationInfo.BUILT_IN)` 全公开；`LootTable` 无 getPools()（计数由构建侧携带）。
- 分层铁律：api ← engine ← runtime/migrate；engine 纯 JDK 不碰 MC；MC 壳落根 sourceSet（subterra-runtime source-host）；iron law 探针覆盖。

## 2. 本次范围（第六块）
1. **导出器扩展**：engine.datapack 导出器从 recipe 扩展到**全部七类**（template：recipe 的 RecipeDatum/DatapackExporter——各 `engine.datapack.XxxDatum` record + read/write 规范 td + 确定性归一化，镜像注册侧消费语义；tag/lang/loot_table/worldgen/structure/function 的导出源=注册侧已装入索引/对象）。schema-free：先按 mini_dp seed 的实际 schema 覆盖（tag `values`+`replace`、lang `[k,v]` 对、loot chest `type/pools[rolls,entries[item,weight]]`、worldgen configured_feature `feature/block`、structure 两 schema、function 无表载荷可导出条目元数据）。
2. **回灌全类型**：每类导出 td → 经 DatapackLoader 文件重载 + DatapackRegistrar 既有寄存器回灌 → 再导出 → 断言逐字节一致（五类各加 E2E marker 或纯 JVM 断言；loot/worldgen/structure 已注册进 registry 的，回灌=注册侧 build 函数重跑）。
3. **导出档案 Hub**：`DatapackExporter.exportPack(Datapack)` 把某 pack（或已注册内容）的所有导出条目写为**单一 td 文档**（档案 = `type tie<data>` + `[ [kind,ns,path,payload]... ]` 或复用 DatapackPack 的 base64 files 形态，二选一，最小闭环为准），`DatapackExportArchive.load`/`rehydrate` 复原为 Datapack / 直灌注册器；档案→再导出档案逐字节一致（与 DatapackPack 打包往返并行存在，档案是「内容级」而非「文件级」）。为 p.2.18（language-keys/config/world/registries/migrate-maps，td/zd 双格式，`/subterra export` 指令）留 engine.export 落点注释。
4. **探针**：纯 JVM 全类型往返断言（seed 七类）+ E2E 扩展 markers（可新增 `DatapackE2EProbe` 断言，如 `export roundtrip` 延伸或 `archive rehydrate <kind> ok`）；接 probeAcceptance。
5. 具体切分以摸底后的最小闭环为准；不确定范围先列出提问，不擅自扩大（例如 function 无载荷条目先导出元数据/跳过并如实标注）。

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
- 仓库根：F:\Projects\Toterra-Repo\Subterra；engine.datapack 在 `subterra-engine\...\engine\datapack\`（DatapackPack / EntryKind / DatapackEntry / DatapackLoader / **RecipeDatum / DatapackExporter**）；runtime.datapack 在 `subterra-runtime\...\runtime\datapack\`（DatapackRuntime / DatapackRegistrar / DatapackRegistryInjector / **DatapackRecipeExporter**）。
- 探针资产：`subterra-devkit\src\main\resources\datapack\mini_dp\`（seed：tag/lang/recipe×2/loot_table/worldgen(configured_feature)/structure×2/function×2/extra）；探针 `probes\DatapackProbe`（纯 JVM，31+6 断言）/ `DatapackE2EProbe`（18 标记开服）。
- RecipeManager 注入/合并参考：`DatapackRegistrar.registerRecipes`（replaceRecipes）+ `registerTdCanonical`；loot/worldgen/structure 注入参考：`DatapackRegistryInjector.inject` + `buildLootTables` / `registerWorldgen` / `registerStructures`。
- probeAcceptance 接线：`subterra-devkit\build.gradle`（E2E 需 mustRunAfter bootProbe；staging 用 build/tmp）。
- tiec：F:\Projects\tie-repo\tie-main\dist\tie-Harbor-2026.1-preview.6\bin\tiec.exe。

## 7. 输出与汇报
- 主代理与各子代理均：双语说明（做了什么 + 结论）后立即提交；最后报 probeAcceptance 全量结果 + 新提示词文件路径。