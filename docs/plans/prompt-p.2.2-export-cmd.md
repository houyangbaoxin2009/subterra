# Subterra p.2.2 数据包重构（td 一等公民）· 第七块：engine.export 指令接通（/subterra export + registries 导出落位）· 开发任务提示词

## 0. 角色与目标
你是 Subterra（NeoForge 模组开发框架，服务 Toterra 主模组）的开发代理。
本次目标 = p.2.2 数据包重构的**第七块**：把第六块打好的**导出档案 Hub 前哨**（DatapackExportArchive + 全七类导出器）**接通到 p.2.18 的 `engine.export` 面向玩家/Hub 的导出指令**——`/subterra export` 指令把已注册内容（数据包七类 + 附加源）以 td 文档导出落位到可指定目录，并把**各 datapack registry 的已注册内容**（registry→导出一致）接到同一导出 Hub，作为 engine.export 的第一条可用出口。第六块的 closed-loop 假说「td 是一切数据内容的回程」从「引擎内测试」推进到「玩家/开发者可触达的命令出口」。
工作方式：先小任务摸底/定最小闭环 → 逐个实现 → 每个小任务完成即提交（报告一句），全部完成后统一 review+清理+推送。

## 1. 项目上下文（必读）
- 系列聚合根 F:\Projects\Toterra-Repo：Subterra（框架，开源 TPL 2.0，origin=github.com/houyangbaoxin2009/subterra）+ Toterra（github.com/houyangbaoxin2009/toterra）。
- **前六块已落地**（提交见 git log）：
  * 第一块 engine.datapack（纯 JDK 无 JSON）：EntryKind 七类 / DatapackEntry / Datapack（按 id 排序）/ DatapackLoader（目录约定 + pack.td manifest 双发现）/ TieLogicLoader→TieLogicBundle（`<lib>$<fn>` FFM）/ DatapackProbe。
  * 第二块 MC 壳（runtime.datapack）：DatapackRuntime（ServerStarted/Stopping + resolveDatapacksDir）+ DatapackRegistrar：tag/lang 索引、crafting_shaped+smoking→RecipeManager.replaceRecipes 合并、tie 0 参调用；DatapackE2EProbe 开服断言。
  * 第三块：DatapackPack 打包往返 + smoking→SmokingRecipe + 剩余三类 staged markers。
  * 第四块：loot 真合并（LOOT_TABLE datapack registry）与 worldgen/structure 真注册（CONFIGURED_FEATURE / STRUCTURE / STRUCTURE_SET），`DatapackRegistryInjector` 公开 unfreeze→register→freeze 窗口；E2E 16 标记。
  * 第五块：DataPack→td 导出往返（recipe 先行）——engine `RecipeDatum` + `DatapackExporter.exportRecipeTd`；runtime `DatapackRecipeExporter` + `DatapackRegistrar.runExportRoundTrip`（导出→buildRecipe 回灌→再导出字节级比对）；E2E 18 标记。
  * **第六块（本块已完成）**：导出全七类 + 内容级导出档案 Hub——engine.datapack 新增 `TagDatum`/`LangDatum`（tag `replace`+`values`、lang `[k,v]` 对归一化）+ `DatapackExportArchive`（`type tie<data>` 单文档 `export=[version,entries]`，export∘rehydrate 逐字节恒等，与 DatapackPack 文件级档案并行的「内容级」第二档案形态）+ `DatapackExporter.exportEntryTd`（RECIPE 对象级逐字节往返；TAG/LANG 归一化；LOOT_TABLE/WORLDGEN/STRUCTURE/FUNCTION canonical pass-through）；runtime `DatapackRegistrar` 统一 marker `export roundtrip <kind> <id> ok` + `export archive roundtrip ok (entries=10)`；纯 JVM 探针 10 条目文件重载往返逐字节一致 + 档案往返 + 字段无损；E2E 标记 18→27；probeAcceptance 全绿。
- 关键 API 事实（javap neoform jar 已核实）：`ShapedRecipePattern` 无 key 访问器（仅 width()/height()/ingredients()）；`ShapedRecipe.pattern` public final；`SmokingRecipe.getExperience()/getCookingTime()` 公开；`RegisterEvent` 不对 datapack registry 触发；datapack registry 数据装载时冻结；`MappedRegistry.unfreeze()/freeze()/register(key,value,RegistrationInfo.BUILT_IN)` 全公开；`LootTable` 无 getPools()（计数由构建侧携带）；导出的 ShapedRecipe 不保留源键字符，pattern 行按格首次出现确定性重建（A,B,C…），故 recipe 往返取**对象级**恒等（holder→td→buildRecipe→td），非源 payload 恒等。
- 分层铁律：api ← engine ← runtime/migrate；engine 纯 JDK 不碰 MC；MC 壳落根 sourceSet（subterra-runtime source-host）；iron law 探针覆盖。

## 2. 本次范围（第七块 / next module）
选中 p.2.2 轨（c，接第六块引擎导出主题）：**导出接通 p.2.18 engine.export 指令**——把第六块的导出档案从「引擎内测试闭环」接成**玩家/开发者可用命令出口**，理由：第六块已把导出能力与档案 Hub 打满作为 p.2.18 前哨，本块把该前哨物化为第一条可用指令，语义衔接最顺、无中间断层。
1. **`/subterra export` 指令骨架**：注册一个 `/subterra export <target> [<path>]` 分支（子指令），把当前已注册内容（数据包七类 + 可选附加源）经 `DatapackExportArchive` 导出为单一 td 档案文档，落位到指定目录（缺省可写工作目录某约定子目录）；幂等、确定性、报告导出路径与条目数。
2. **registries 导出落位**：各 datapack registry（LOOT_TABLE / CONFIGURED_FEATURE / STRUCTURE / STRUCTURE_SET 等）的当前已注册内容经导出器转成 entry 形态并入同一档案（registry→导出一致；已注册即已是消费方产出的可再装载内容），使档案开始「检视运行时实际注册内容」而不仅是 seed 文件。
3. **读回闭环**：指令导出的档案能经 `DatapackExportArchive.rehydrate` + `DatapackLoader`/注册器复原（可再装载），与第六块档案一致性检验同语义；导出→还原→再导出逐字节一致断言沿用。
4. **探针**：`DatapackE2EProbe` 新增 marker（如 `subterra export <path> ok (entries=N)` 或 `export cmd rehydrate ok`）+ 纯 JVM 断言档案字段；接 probeAcceptance。执行性（幂等/无功壳）优先，先最小闭环，不确定范围先列出提问，不擅自扩大。
5. 也可改走 (a) 数据包双接口（td + tie 读通道）或 (b) 数据包级规则，但需一句说明为何改轨；默认按 (c)。

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
- 仓库根：F:\Projects\Toterra-Repo\Subterra；engine.datapack 在 `subterra-engine\...\engine\datapack\`（DatapackPack / EntryKind / DatapackEntry / DatapackLoader / RecipeDatum / TagDatum / LangDatum / **DatapackExportArchive** / **DatapackExporter**）；runtime.datapack 在 `subterra-runtime\...\runtime\datapack\`（DatapackRuntime / DatapackRegistrar / DatapackRegistryInjector / DatapackRecipeExporter）。
- 命令接线参考：仓库内既有命令注册范式（server 侧根命令/子命令注册）；导出档案消费路径 `DatapackExportArchive.export/rehydrate` + `DatapackExporter.exportEntryTd`。
- 探针资产：`subterra-devkit\src\main\resources\datapack\mini_dp\`（seed 七类）；探针 `probes\DatapackProbe`（纯 JVM）/ `DatapackE2EProbe`（27 标记开服）。
- probeAcceptance 接线：`subterra-devkit\build.gradle`（E2E 需 mustRunAfter bootProbe；staging 用 build/tmp）。
- tiec：F:\Projects\tie-repo\tie-main\dist\tie-Harbor-2026.1-preview.6\bin\tiec.exe。

## 7. 输出与汇报
- 主代理与各子代理均：双语说明（做了什么 + 结论）后立即提交；最后报 probeAcceptance 全量结果 + 新提示词文件路径。