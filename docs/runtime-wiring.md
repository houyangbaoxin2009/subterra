# Runtime Wiring — 统一接线总表 / Master Runtime Wiring Table

**Status: Active**

Subterra 的全部 runtime 壳/命令接线盘点（p.2.19 盘点素材）：逐类登记既有 runtime 壳/命令的类、包位、门控属性、生命周期钩子、marker 前缀与 E2E 断言点，作为 launch / runtime-fix / worldgen / optim-shell / tie / cfglog 六类目标收编与 export 指令接线的清单。

Bilingual inventory of every existing runtime shell/command (class, package, gate property, lifecycle hook, marker prefix, E2E assertion point) — the checklist that drives the fold-in of the six target categories (launch / runtime-fix / worldgen / optim-shell / tie / cfglog) and the export command wiring.

---

## 总表 / Master table

条目以实际源码为准（核实日期 2026-09-11，分支 main）；未找到的标记「未找到」。状态含义：**已挂接** = boot 直挂（`Subterra.java` 构造调 `bootstrap`）或 `@EventBusSubscriber` 自注册；**门控探针壳** = 默认 no-op、仅门控属性开启时跑确定性闭环；**占位空壳** = 仅 `package-info`；**待补** = p.2.19.x 要新建。

Every row is verified against the source (audited 2026-09-11, branch main); anything not found is marked 「未找到」. Status legend: **已挂接/wired** = boot-mounted via `Subterra.java` constructor `bootstrap` call or `@EventBusSubscriber` self-registration; **门控探针壳/gated probe shell** = no-op by default, runs a deterministic closed loop only when the gate property is set; **占位空壳/placeholder** = `package-info` only; **待补/to-add** = to be built by p.2.19.x.

| 壳/命令 Shell/Command | 类 Classes | 包位 Package | 门控属性 Gate prop (-D/-P) | 生命周期钩子 Lifecycle hooks | Marker 前缀 Marker prefix | E2E 断言点 E2E assertion | 状态 Status |
|---|---|---|---|---|---|---|---|
| datapack | `DatapackRuntime` / `DatapackRegistrar` / `DatapackExportCommand` | `runtime.datapack` | `subterra.probe.export`（启动钩子）；`subterra.datapacks`（目录解析）；`subterra.override`（规则覆盖） | `ServerStarted`(LOWEST) / `ServerStopping`；`RegisterCommandsEvent`（bootstrap 内 `addListener`） | `[Subterra datapack]` | `DatapackE2EProbe` L196/L199（`export cmd ok (packs=1,`、逐 pack `rehydrate=ok`） | 已挂接 |
| export hub | `ExportHubRuntime` / `ExportHubCommand` | `runtime.export` | `subterra.probe.exportHub`（启动钩子，值=目标目录） | `ServerStarted`(LOWEST) / `ServerStopping`；`RegisterCommandsEvent` | `[Subterra export]` | `DatapackE2EProbe` L211/L214/L217（`export hub ok (forms=5`、datapack/language_keys `rehydrate=ok`）；`ExportHubProbe`（纯 JVM 41 项） | 已挂接 |
| rules | `RulesRuntime` / `RuntimeRuleCommand` | `runtime.rules` | `subterra.probe.rule`（启动钩子） | `ServerStarted`(LOWEST) / `ServerStopping`；`RegisterCommandsEvent` | `[Subterra rule]` | `DatapackE2EProbe` L208（`view ok (rules=4, render=…seed_offset=42`）；`RuleSystemProbe`（纯 JVM） | 已挂接 |
| save | `SaveRuntime` | `runtime.save` | `subterra.probe.save` | `ServerStarted`(LOWEST) / `ServerStopping`（`@EventBusSubscriber` 自注册） | `[Subterra save]` | `SaveE2EProbe` L78/L81/L84（`level name=`、`level seed=… time=… rules=`、`zdt td-bytes=… zd-header=TIEDBZD:`） | 已挂接（门控探针壳） |
| saveverify | `SaveVerifyRuntime` | `runtime.saveverify` | `subterra.probe.saveverify`；`subterra.probe.saveverify.seq` | `ServerStarted`(LOWEST) / `ServerStopping`（自注册） | `[Subterra saveverify]` | `AsyncE2EProbe` L171（`PASS seq=`） | 已挂接（门控探针壳） |
| session | `SessionRuntime` | `runtime.session` | `subterra.probe.session` | `ServerStarted`(LOWEST) / `ServerStopping`（自注册） | `[Subterra session]` | `AsyncE2EProbe` L177（`PASS session closed loop OK`） | 已挂接（门控探针壳） |
| sim | `SimRuntime` | `runtime.sim` | `subterra.probe.sim` | `ServerStarted`(LOWEST) / `ServerStopping`（自注册） | `[Subterra sim]` | `AsyncE2EProbe` L153/L156（`sim-shell-gate=on`、`sim-core-composed-ok`） | 已挂接（门控探针壳） |
| world | `WorldRuntime` | `runtime.world` | `subterra.probe.world` | `ServerStarted`(LOWEST) / `ServerStopping`（自注册） | `[Subterra world]` | `AsyncE2EProbe` L162/L165（`world-shell-gate=on`、`world-pack closed-loop OK`） | 已挂接（门控探针壳） |
| network | `EnhancedChannelRuntime` | `runtime.network` | `subterra.probe.network` | `ServerStarted`(LOWEST) / `ServerStopping`（自注册；关停含 relay 清理） | `[Subterra network]` + `[Subterra relay]`（p.2.5.8 关停新行） | `NetworkE2EProbe` L79/L82/L85（`enhanced-channel-initialized`、`original-path-preserved`、`encryption-enabled-by-default=true`） | 已挂接（门控探针壳） |
| worldgen · async | `AsyncChunkRuntime` / `C2meCoexistence` | `runtime.worldgen.async` | `subterra.probe.async`；C2ME 检测无门控 | `ServerStarted`(LOWEST) / `ServerStopping`（自注册）；C2ME 在 `commonSetup` 经 `bootstrap(modContainer)` | `[Subterra async]` / `[subterra_c2me]` | `AsyncE2EProbe` L141/L144/L147（`baseline-io-default-off=true`、`async-io-gate-enabled=true`、`async-chunk-runtime-initialized`） | 已挂接（门控探针壳） |
| worldgen · gen | `SubterraWorldgen` / `WorldgenConfig` | `runtime.worldgen.gen` | 无探针门控；td 门控（`worldgen.td` `use_subterra_generator`） | `bootstrap(modEventBus)`（注册 `subterra:density` 密度函数类型 + 捕获世界种子）/ `bootstrap()`（选项门控日志） | `[subterra_worldgen]`（类内日志风格） | `ServerBootProbe`（boot 门控间接覆盖）；`DensityProbe`/`WorldGenGuardProbe` 等纯 JVM | 已挂接 |
| worldgen · compare | `SameSeedCompareHook` | `runtime.worldgen.compare` | `subterra.compareSeed`（auto-trigger，值=seed long） | `ServerStarted` / `RegisterCommandsEvent`（自注册） | `[SameSeedCompare]` | 无专用 E2E 探针；`/subterra compare <seed>` 命令 + auto-trigger marker | 已挂接（属性门控） |
| worldgen · profiler | `WorldProfilerHook` | `runtime.worldgen.profiler` | `subterra.profile` / `subterra.profileSlice` / `subterra.profileBuild` / `subterra.perfCount` / `subterra.profileCenter` | `ServerStarted` / `ChunkEvent.Load`（residency）/ `RegisterCommandsEvent`（自注册） | `[subterra_profiler]` | `WorldProfileAcceptanceProbe`（纯 JVM 断言 report 契约）；boot 门控间接覆盖 | 已挂接（属性门控） |
| optim · advancement | `InventoryAdvancementAccelerator` (+`InventoryAdvancementRuntime`) | `runtime.optim.server.advancement` | 无探针门控（td config + toml） | `ServerStarted` / `ServerStopping` / `TagsUpdated` / `ServerTick.Post`（bootstrap 注册实例） | `[subterra_invadvopt]`（MOD_ID 风格） | `MobcapProbe`/`MergeProbe` 等纯 JVM；无专用 E2E | 已挂接 |
| optim · threading | `WorkerPoolTuning` | `runtime.optim.server.threading` | 无探针门控（td config） | `bootstrap(modContainer)` 预载 config；Util mixin 懒换后台/IO 执行器 | `[subterra_smoothboot]`（MOD_ID 风格） | 无专用 E2E；纯 JVM/单元侧 | 已挂接 |
| optim · entity-ai | `VillagerLobotomize` | `runtime.optim.entity.ai` | 无探针门控（td config） | `bootstrap(modContainer)` 预载 config；`AbstractVillagerMixin` 驱动 | `[subterra_servercore]`（MOD_ID 风格） | 无专用 E2E | 已挂接 |
| optim · dynamic | `DynamicDistance` (+`DynamicManager`) | `runtime.optim.server.dynamic` | 无探针门控（td config `dynamic.enabled`） | `ServerStarted` / `ServerTick.Post` / `ServerStopping`（bootstrap 注册实例） | `[subterra_servercore]`（MOD_ID 风格） | 无专用 E2E | 已挂接 |
| optim · sync-load | `SyncLoadRuntime` | `runtime.optim.server.loading.shell` | 无探针门控（td config `loading.reduce_sync_loads`） | `bootstrap(modContainer)` 预载 config；调用点壳落后于门 | `[subterra_servercore]`（MOD_ID 风格） | `SyncLoadGuardProbe`（纯 JVM） | 已挂接 |
| optim · item-control | `ItemControl` (+`ItemControlHandler`/`RecipeStripper`/`ItemControlCommands`) | `runtime.optim.server.item_control.shell` | 无探针门控（td config） | `bootstrap(modContainer)` 注册 EVENT_BUS Handler/RecipeStripper/Commands | `[item_control]` | `ItemControlProbe`（纯 JVM） | 已挂接 |
| optim · spawning | `SpawnEnforcementShell` | `runtime.optim.entity.spawning.shell` | 无探针门控（纯引擎门控，默认 off） | 无事件；mixin（spawner/portal/infested/zombie-reinforcement）调用 `allowed()` | —（无自身 marker） | `SpawnEnforcementProbe`（纯 JVM） | 已挂接（引擎门控壳） |
| launch | `JvmEnv` / `JvmLaunchArgs` | `runtime.launch` | 无门控；boot 只读校验 | `Subterra.java` `commonSetup`（`JvmEnv.verify()` + `JvmLaunchArgs.missingStaticFlags()`）；**未做参数注入** | `Subterra L1:`（log 前缀） | `ServerBootProbe`（`Java 25 … verified: true`、`JVM argument package present`）；`LaunchArgsProbe`/`LaunchLayerProbe`（纯 JVM） | 已挂接（校验；注入在 gradle run 块） |
| runtime-fix | `Java25Gaps` | `runtime.fix` | 无门控 | **未挂进 boot**（`Subterra.java` 与各 runtime bootstrap 均未调用；仅 `CompatProbe` 引用） | —（无自身 marker） | `CompatProbe`（纯 JVM 断言 registry 契约） | 待挂接 |
| tie | `package-info` only（占位） | `runtime.tie` | — | — | — | — | 占位空壳（待补） |
| cfglog | `package-info` only（占位） | `runtime.cfglog` | — | — | — | — | 占位空壳（待补） |

---

## 六类目标现状与缺口 / Six target categories — status & gaps

### 1. launch

**现状 / Current:** `JvmEnv`（`MIN_FEATURE=21`，`verify()` 报运行 JVM 版本）+ `JvmLaunchArgs`（静态调优标志：`-XX:+UseZGC`、`-XX:InitialRAMPercentage=75`、`-XX:MaxRAMPercentage=75`、`-XX:+AlwaysPreTouch`；`--add-opens java.base/java.lang=ALL-UNNAMED`）。两者在 `Subterra.java` `commonSetup` 内做**只读校验**（`verify()` 与 `missingStaticFlags()`，缺标志只 warn），实际参数注入在根 `build.gradle` server run 块完成（非 runtime 代码）。`LaunchArgsProbe` / `LaunchLayerProbe`（纯 JVM）与 `ServerBootProbe`（boot 门控，断言 `Java 25 … verified: true` + `JVM argument package present`）覆盖。

**缺口 / Gaps (p.2.19.x 要补):**
- p.2.19.x：launch 迁入统一 runtime 壳（如 `runtime.launch.LaunchRuntime`），把「校验」升级为「校验 + 注入告警/自检接线」，并挂进 `Subterra.java` 构造（目前校验在 `commonSetup` 事件回调，未进构造 bootstrap 序列）。
- p.2.19.x：把 launch 校验结果接入统一 export 指令（boot 时输出确定性 marker 供 E2E 断言，与 save/world 探针壳同模式）。

### 2. runtime-fix

**现状 / Current:** `Java25Gaps` 是纯 RCA 登记 registry（`Status.OPEN/PATCHED`，当前 `REGISTRY = List.of()` 空），**未挂进 boot**——`Subterra.java` 与任何 runtime bootstrap 均未调用它；仅 devkit 侧 `CompatProbe`（纯 JVM）断言 registry 契约（immutable、无 open gaps）。注释声称的 boot 门控（`ServerBootProbe`）目前只断言 boot 契约，不读 `Java25Gaps`。

**缺口 / Gaps (p.2.19.x 要补):**
- p.2.19.x：新建 `runtime.fix.FixRuntime` 壳，把 `Java25Gaps.registry()/hasOpenGaps()` 挂进 boot（`ServerStarted` 或 `commonSetup`），open gap 时打确定性 warn marker；把 `CompatProbe` 的断言面桥到该壳（消除「registry 未被 runtime 引用」的断链）。
- p.2.19.x：fix 状态接入统一 export 指令（可选，作为清单项）。

### 3. worldgen

**现状 / Current:** 四支线均已接线：
- **async**：`AsyncChunkRuntime`（门控探针壳，`subterra.probe.async`，开/关引擎异步 I/O 门并做确定性写队列闭环）+ `C2meCoexistence`（`commonSetup` 经 `bootstrap(modContainer)` 检测官方 C2ME jar 打共存指引）。
- **gen**：`SubterraWorldgen.bootstrap(modEventBus)` 注册 `subterra:density` 密度函数类型并捕获世界种子；`WorldgenConfig.bootstrap()` td 门控（`use_subterra_generator`）。
- **compare**：`SameSeedCompareHook`（`@EventBusSubscriber` 自注册；`subterra.compareSeed` 属性 auto-trigger + `/subterra compare <seed>` 命令；marker `[SameSeedCompare]`）。
- **profiler**：`WorldProfilerHook`（自注册；`subterra.profile`/`profileSlice`/`profileBuild`/`perfCount` 属性门控 + `/subterra profile|slice|profile-dump|profile-resume|profile-pause` 命令；marker `[subterra_profiler]`）。

**缺口 / Gaps (p.2.19.x 要补):**
- p.2.19.x：把四支线收编进统一 `runtime.worldgen` 壳清单（入口分散：两处 `bootstrap` 直挂、两处 `@EventBusSubscriber`），统一启动钩子序列与 marker 命名约定（当前 `[Subterra async]` / `[SameSeedCompare]` / `[subterra_profiler]` 三套前缀）。
- p.2.19.x：worldgen 结果接入统一 export 指令（接 p.2.18 的 world form：当前 `ExportHubCommand` 的 world/save form 为 `skip (no save source)`，本壳无 `SaveContainer` 源物供给）。

### 4. optim-shell

**现状 / Current:** 七壳全部经 `Subterra.java` 构造 `bootstrap(modContainer)` 直挂（均为 ported/clean-room 内部能力，非 `@Mod`）：`InventoryAdvancementAccelerator`（`subterra_invadvopt`）、`WorkerPoolTuning`（`subterra_smoothboot`）、`VillagerLobotomize`（`subterra_servercore`）、`DynamicDistance`（`subterra_servercore`，ServerStarted/Tick/Stopping）、`SyncLoadRuntime`（`subterra_servercore`，servercore.td `loading` 表）、`ItemControl`（`item_control`，EVENT_BUS 注册）；另 `SpawnEnforcementShell` 为纯引擎门控壳（无 bootstrap，mixin 调用 `allowed()`）。配置均走 `config/subterra/*.td`（或独立 toml），**无探针门控、无统一 marker 前缀**。

**缺口 / Gaps (p.2.19.x 要补):**
- p.2.19.x：产出 optim-shell 统一清单（当前缺一张「壳 ↔ 配置档 ↔ marker ↔ 探针」对照表，探针多为纯 JVM 单测风格，无 E2E 启动断言）。
- p.2.19.x：给 optim 壳补统一的 boot marker（如 `[Subterra optim] <shell> ready`）并接入统一 export 指令/探针面（可选，作为清单项）。

### 5. tie

**现状 / Current:** `runtime.tie` 仅 `package-info` 占位（「tiec -> DLL -> FFM loading, hot paths in tie」）。桥接点实际在：**subterra-tie 模块**（独立 gradle 模块，`src/main/java` 当前为空，仅 `build/libs` 残留 `subterra-tie-p.1.8.33.jar`）+ **`TieBridgeProbe`**（devkit，FFM downcall 确定性探针：从资源提取 `tiefib_probe.dll` → `TieLibrary.load` → 符号解析/调用断言；运行期需 `--enable-native-access=ALL-UNNAMED`）。runtime 侧现存 tie 消费点：`DatapackRegistrar.registerTie()` 经 `TieLogicLoader`/`TieFunction.invoke0()` 调用 FUNCTION 条目（引擎 `engine.tie`，非 runtime 壳）。

**缺口 / Gaps (p.2.19.x 要补):**
- p.2.19.x：新建 `runtime.tie` 壳（把 FFM `--enable-native-access` 装载接线、`TieLibrary.load` 生命周期与 `ServerStarted/Stopping` 钩子对齐），承接 subterra-tie 桥（当前只有 devkit 探针证明链路，runtime 无壳）。
- p.2.19.x：tie 壳产出确定性 boot marker（如 `[Subterra tie] bridge loaded`），并入统一探针面。

### 6. cfglog

**现状 / Current:** `runtime.cfglog` 仅 `package-info` 占位（「td hot-reload, SLF4J bridge, crash-export wiring, rule commands」）。现存的规则命令在 `runtime.rules`（`RuntimeRuleCommand`，已挂接），SLF4J 桥在根 sourceSet `SubterraLogging`/`Slf4jLogSink`（非 runtime 模块）。

**缺口 / Gaps (p.2.19.x 要补):**
- p.2.19.x：新建 `runtime.cfglog` 壳——td 热重载、SLF4J 桥接线、crash-export 接线；把 `runtime.rules` 的 rule 命令面与 cfglog 对齐（或明确归并方案）。
- p.2.19.x：cfglog 壳产出确定性 marker 并并入统一探针面。

---

## export 指令现状与收编目标 / Export command — current state & fold-in target

**入口一：`/subterra export [<path>]` — `DatapackExportCommand`（`runtime.datapack`，op2）**
逐 pack 写内容级 export-archive td（`DatapackExportArchive`，每 pack 一个 `<pack-name>.td`），并立即做 `export ∘ rehydrate` 字节恒等校验；marker 每 pack 一行 `export cmd <pack> ok/mismatch (... rehydrate=ok/mismatch)` + 汇总 `export cmd ok (packs=N, bytes=M)`。同一核心 `exportFrom` 也服务启动钩子（`subterra.probe.export` 门控，DatapackRuntime onServerStarted）。

**入口二：`/subterra export <form> [<path>]` — `ExportHubCommand`（`runtime.export`，op2）**
七 form（`world/save/datapack/language_keys/config/registries/migrate_maps`）共享确定性核心 `exportForm/exportAllForms`：经 `ExportHub` 取功能性 `ExportProducer`，产出 td 文档 + zd v2 二进制变体并做双格式回水化恒等；marker 每 form 一行 `export hub <form> ok (tdBytes=.., zdBytes=.., rehydrate=ok)` + 汇总 `export hub ok (forms=N)`。world/save form 当前为 `skip (no save source)`（无活跃 `SaveContainer` 壳）。与入口一同根（`subterra → export`）经 Brigadier 合并并存，form 分支靠 form 感知参数类型胜出。启动钩子：`subterra.probe.exportHub` 门控（ExportHubRuntime onServerStarted）。

**收编目标 / Fold-in target (p.2.19.x):**
- p.2.19.x：两个入口统一收编进 `runtime.export` 壳（`ExportHubRuntime` 已是壳），形成单一 `/subterra export` 指令树文档与单一 marker 约定（`[Subterra export]` 为 hub 前缀；datapack 侧 `export cmd` 前缀保持兼容或明示迁移）。
- p.2.19.x：world/save form 接上活跃源物（worldgen 壳/未来 `SaveContainer` 壳），消除 `skip (no save source)`；与 p.2.18 `engine.export` 注册表（`ExportHub`/`ExporterRegistry`）对齐。
- p.2.19.x：export 指令接入 p.2.18 探针面（当前 `DatapackE2EProbe` 已断言 `export hub ok (forms=5` 等行；后续探针子项按此清单扩展）。

---

## 附注 / Notes

- 根初始化入口 `Subterra.java`（根 sourceSet `io.toterra.subterra`）现挂：`InventoryAdvancementAccelerator`、`WorkerPoolTuning`、`VillagerLobotomize`、`DynamicDistance`、`SyncLoadRuntime`、`ItemControl`、`SubterraWorldgen`、`WorldgenConfig`、`DatapackRuntime`、`RulesRuntime`、`ExportHubRuntime`（构造）；`JvmEnv`/`JvmLaunchArgs`/`SubterraLogging`/`C2meCoexistence`（`commonSetup`）。其余探针壳（save/saveverify/session/sim/world/network/async/compare/profiler）经 `@EventBusSubscriber` 自注册，无需改 `Subterra.java`。
- 探针宿主：`subterra-devkit` 模块 `io.toterra.subterra.probes`；`ServerBootProbe` 为 boot 门控（断言 `Done (`、`Java 25 … verified: true`、`JVM argument package present`）。
