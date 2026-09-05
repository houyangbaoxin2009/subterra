# Subterra Changelog / 变更日志

Reverse chronological. Two-track versioning: `p.x.y.z` pre-release, `r.x.y.z` release (stabilization only). / 倒序排列。双轨编号：p 预发布、r 正式版（仅优化稳定）。

## [p.1.5.1] Configuration package / 配置包 (2026-09-06)

* `ConfigPack` (subterra-config): one-click import/export of a set of td config files as a single td document — `files = [ [ name = ..., config = [...] ] ]` entries (file names as string values, never bare keys), deterministic name order, exact round-trips, version field / `ConfigPack`（subterra-config）：一组 td 配置文件的单文档一键导入/导出——`files` 用「name/config 子表」数组项（文件名走字符串值而非裸键）、按名排序、往返保真、带版本号
* Deterministic probe `ConfigPackProbe` (11 checks) wired into `probeAcceptance` — round-trip fidelity (incl. top-level-name stripping semantics of parse_data), re-export idempotence, malformed rejection / 确定性探针 `ConfigPackProbe`（11 项断言）接入 `probeAcceptance`——往返保真（含 parse_data 顶层命名表剥离语义）、再导出幂等、非法输入拒绝

## [p.1.4.1] Ported optimization, first item: entity merging core / 移植优化首批：实体合并核心 (2026-09-06)

* Ported the ServerCore `merging` surface as a pure-JDK core (MIT-side logic; MC mixin shell later): `MergePolicy` (enabled/radius/fraction with constructor validation, squared-radius bounds) + deterministic `Merger.canMerge` with injected RNG — no O(n²), no MC runtime / 以纯 JDK 核心移植 ServerCore `merging` 功能面（MIT 侧逻辑；MC mixin 薄壳后置）：`MergePolicy`（enabled/radius/fraction 构造校验、平方半径边界）+ 注入 RNG 的确定性 `Merger.canMerge`——无 O(n²)、无 MC 运行时
* Deterministic probe `MergeProbe` (13 checks) wired into `probeAcceptance` — policy validation, radius bound, probability extremes, same-seed determinism / 确定性探针 `MergeProbe`（13 项断言）接入 `probeAcceptance`——策略校验、半径边界、概率极值、同种子确定性

## [p.1.6.2] Thread snapshot in crash diagnostics / 崩溃诊断线程快照 (2026-09-06)

* `ThreadsSnapshot` (subterra-log) renders every live thread's name/state/top frames, embedded into `CrashDumper.diagnosticsBlock` — worker pools (C2ME/FlowSched, storage I/O, lighting, vanilla io) are visible at failure time; LogProbe 41 checks green / `ThreadsSnapshot`（subterra-log）导出全部存活线程的名称/状态/栈顶帧，并入 `CrashDumper.diagnosticsBlock`——崩溃时可见各工作线程池（C2ME/FlowSched、存储 IO、光照、原版 io）；LogProbe 41 项全绿

## [p.1.8.1] Structure collision guard / 结构防碰撞护栏 (2026-09-06)

* New pure-JDK core `io.toterra.subterra.optim.worldgen.guard`: clearance-aware `StructureFootprint` AABB, spatial-hash `StructureLayout` (insert + nearest-free-spot spiral search are O(neighbourhood), never O(n²); footprints indexed across every spanned cell so cross-cell lookups always hit), explicit all-pairs `conflicts()` for tooling / 新增纯 JDK 核心 `io.toterra.subterra.optim.worldgen.guard`：带间隙的 `StructureFootprint` AABB、空间哈希 `StructureLayout`（插入与最近空位螺旋搜索 O(邻域)、杜绝 O(n²)；足迹按跨格全索引保证跨单元格查找命中）、显式全对 `conflicts()` 供工具使用
* Deterministic probe `WorldGenGuardProbe` (18 checks) wired into `probeAcceptance` — clearance semantics, cross-cell detection, spiral placement, density smoke under 2s / 确定性探针 `WorldGenGuardProbe`（18 项断言）接入 `probeAcceptance`——间隙语义、跨格检出、螺旋落位、2 秒内密集性能冒烟

## [p.1.8.0] EcoDims nine-dimension biome model / 九维群系模型 (2026-09-06)

* New pure-JDK API surface `io.toterra.subterra.api.worldgen` (architecture §5.2): `EcoDim` (nine registered dimensions), `EcoDimValue`, total `EcoProfile` builder, default-disallow/allow-exceptions `EcoRelations` (undirected coexist chain), `validate()` health check (no fully-forbidden value, satisfiable rule set), deterministic `EcoResolver` with priority fallback (single-dimension concession first, then two-dimension; terrain yields last), unhappiness-free vanilla-equivalent defaults in `VanillaDefaults` + rule chain in `VanillaRules` for all listed biomes / 新增纯 JDK API 面 `io.toterra.subterra.api.worldgen`（架构 §5.2）：`EcoDim`（九个注册维度）、`EcoDimValue`、强制性完整 `EcoProfile` 构建器、白名单式 `EcoRelations`（无向共存关系链）、`validate()` 健康检查（无全禁值、规则集可满足）、确定性 `EcoResolver` 优先级逐级回退（先单维让步、再双维、地形最后让步）、`VanillaDefaults`/`VanillaRules` 覆盖原版等价默认映射全部合法
* Deterministic probe `EcoDimsProbe` (30 checks) wired into `probeAcceptance` — dimension registry, coexist semantics, default legality per biome, validator negative-path, resolver concession order / 确定性探针 `EcoDimsProbe`（30 项断言）接入 `probeAcceptance`——维度注册、共存语义、各群系默认合法性、校验器反例、回退次序

## [p.1.6.1] Logging boot wiring / 日志开机接线 (2026-09-06)

* Dev-run classpath fix: `SubterraLogging` (which loads `log.td`, registers module versions/deps and the crash-report diagnostics callable) caused `NoClassDefFoundError: LogSink` at `FMLCommonSetupEvent` — subterra-log / subterra-config now injected into `runs.configureEach` `additionalRuntimeClasspathConfiguration` alongside subterra-launch / 修复 dev-run 类路径：`SubterraLogging`（加载 `log.td`、登记模块版本/依赖、注册崩溃报告诊断块）在 `FMLCommonSetupEvent` 抛 `NoClassDefFoundError: LogSink` —— subterra-log / subterra-config 现随 subterra-launch 一并注入 `runs.configureEach` 的 `additionalRuntimeClasspathConfiguration`
* Boot-time log wiring verified on the dev server (boot gate PASS): `log.td` (level/ring/file/analysis) drives `LogHub`, records bridge into the game log via `Slf4jLogSink`, optional rolling `FileLogSink` writes `logs/subterra/subterra-<ts>.log`, module set registered, diagnostics block embedded into crash reports via `CrashReportCallables.registerCrashCallable` / 开机日志接线已在开发服验证（开机门禁 PASS）：`log.td` 驱动 `LogHub`，`Slf4jLogSink` 桥接进游戏日志，可选滚动 `FileLogSink` 写 `logs/subterra/subterra-<ts>.log`，模块集登记，诊断块经 `CrashReportCallables.registerCrashCallable` 嵌入崩溃报告
* `CrashDumper.diagnosticsBlock()` exposed for the crash callable (system info + modules + log tail), covered by two new LogProbe checks (40 total) / `CrashDumper.diagnosticsBlock()` 供崩溃回调复用（系统信息+模块+日志尾），LogProbe 新增两项断言（共 40）
* Roadmap: p.1.6 marked landed; p.1.7 planned — tie lightweight AI log analyzer as a tink-form standalone local component (Intel GPU/NPU inference, default off) / 路线图：p.1.6 标记落地；p.1.7 立项——tie 轻量级 AI 日志分析器（tink 形态独立本地组件，Intel GPU/NPU 推理，默认关闭）

## [p.1.6.0] Logging module / 日志模块 (2026-09-05)

* New module `subterra-log` (pure JDK): level-gated facade `Logger` + `LogHub` (thread-safe tail ring + sinks), NeoForge-style layout `[HH:mm:ss.SSS] [thread/LEVEL] [logger]: msg`, threshold fast-path drop, `FileLogSink` with per-launch rolling and keep-N pruning (Paper/NeoForge policy), never-throwing sinks / 新模块 `subterra-log`（纯 JDK）：分级门面 `Logger` + `LogHub`（线程安全尾部环形缓冲 + 输出端）、NeoForge 布局、阈值前置快速丢弃、`FileLogSink` 启动滚动 + 保留 N 份（Paper/NeoForge 策略）、输出端永不抛错
* Crash auto-export `CrashDumper`: dumps exception + system info + `Modules:` dependency block + recent log tail to `crash-<ts>.txt`; `ModuleReg` registers modules/versions/dependencies and detects dependency cycles via three-colour DFS (self-loop and pair cycles covered) / 崩溃自动导出 `CrashDumper`：异常 + 系统信息 + 模块依赖块 + 最近日志尾部 → `crash-<ts>.txt`；`ModuleReg` 登记模块/版本/依赖并以三色 DFS 检出依赖环（自环/二元环已覆盖）
* td-driven `LogConfig` (via subterra-config): `log.td` with level/ring_size/file(dir/keep)/analysis(enabled/device/model); unknown fields fall back to defaults / td 驱动 `LogConfig`（经 subterra-config）：`log.td` 含 level/ring_size/file(dir/keep)/analysis(enabled/device/model)；非法值回退默认
* Pluggable crash analyzers: `ErrorAnalyzer` interface, zero-dependency `RulesAnalyzer` (known-crash regex set: OOM/port/mixin/NPE/stack/classpath/JVM-native/mod-load), opt-in `AiAnalyzer` hook for local Intel GPU/NPU mini-AI (default off, falls back to unknown until the inference endpoint is wired) / 可插拔崩溃分析器：`ErrorAnalyzer` 接口、零依赖 `RulesAnalyzer`（已知崩溃正则集）、本地 Intel GPU/NPU 微型 AI 的 `AiAnalyzer` 接入点（默认关闭，推理端接线前回落 unknown）
* Deterministic probe `LogProbe` (38 checks) wired into `probeAcceptance` / 确定性探针 `LogProbe`（38 项断言）接入 `probeAcceptance`

## [p.1.5.0] td configuration module / td 配置模块 (2026-09-05)

* New module `subterra-config` (pure JDK): `Td` parser/writer for the tie-data subset used by tiec `config.parse_data` — header strip (`type tie<data>`), optional table name, bare tables, named entries, arrays, nested tables, strings with escapes, int/float/bool, `//` comments, trailing-comma tolerance / 新模块 `subterra-config`（纯 JDK）：td（tie 数据）解析/写出器——支持 tiec `config.parse_data` 同语法子集（头剥离、可选表名、裸表、命名项、数组、嵌套表、转义字符串、int/float/bool、`//` 注释、容忍尾逗号）
* Immutable model `TdTable` + sealed `TdValue` (string/int/float/bool/table); writer round-trips the model back to td text / 不可变模型 `TdTable` + sealed `TdValue`；写出器把模型回写出 td 文本
* Deterministic probe `ConfigProbe`: scalar/nesting/array/named-table/escape round-trips and malformed-input rejection — wired into `probeAcceptance` (now seven probes + boot gate) / 确定性探针 `ConfigProbe`：标量/嵌套/数组/命名表/转义往返与非法输入拒绝——接入 `probeAcceptance`（现七探针 + 开机门禁）

## [p.1.4.0] Optimization set: module skeleton + first self-developed optimization / 优化集：模块骨架 + 首项自研优化 (2026-09-05)

* `subterra-optim` functional skeleton lands: eight category packages (memory / logic / worldgen / entity / network / server / client / render) with javadoc anchors; depends on `subterra-api` only (iron-law direction) / `subterra-optim` 功能骨架落地：八个功能包（内存/逻辑/世界生成/实体/网络/服务端/客户端/渲染）带文档锚点；仅依赖 `subterra-api`（依赖铁律方向）
* First self-developed optimization `ScratchPool` (optim/util): bounded object pool for hot paths — capacity-capped, factory-created on miss, instance-reusing on hit; deterministic probe `ScratchPoolProbe` asserts identity reuse, factory counting, capacity capping, and allocation avoidance under a 1000-borrow/release heat workload / 首项自研优化 `ScratchPool`（optim/util）：热路径有界对象池——容量封顶、miss 时工厂创建、hit 时实例复用；确定性探针 `ScratchPoolProbe` 断言同一性复用、工厂计数、容量封顶与 1000 次借还热度负载下零分配
* `probeAcceptance` now runs six probes (launch / launch-args / compat / api / pool / boot gate) / `probeAcceptance` 现含六探针（启动 / 启动参数 / 兼容登记 / API 库 / 对象池 / 开机门禁）

## [p.1.3.0] L3 API library / L3 API 库 (2026-09-05)

* `subterra-api` ships its first content (pure Java, zero Minecraft dependency): `TimeApi` (monotonic clock, ISO-8601 round-trip, human durations), `NetApi` (IPv4/port validation, URL encode/decode, best-effort HTTP GET with caller timeout, never throws), `SerApi` (hex and base64), `CryptoApi` (SHA-256/SHA-1, HMAC-SHA-256, AES-256-GCM with per-call random IV and tamper-detecting tags) / `subterra-api` 首发内容（纯 Java、零 MC 依赖）：`TimeApi`（单调钟、ISO-8601 往返、人性化时长）、`NetApi`（IPv4/端口校验、URL 编解码、带调用方超时的尽力 HTTP GET 不抛异常）、`SerApi`（hex 与 base64）、`CryptoApi`（SHA-256/SHA-1、HMAC-SHA-256、带每次随机 IV 与防篡改标签的 AES-256-GCM）
* `subterra-api` publishes a standalone jar (maven-publish → local file repo) — the only module domain mods depend on / `subterra-api` 独立发 jar（maven-publish → 本地文件仓）——领域模组唯一依赖
* New deterministic probe `ApiProbe` (pure JVM): boundary cases, known vectors (sha256/hmac FIPS/RFC), round-trips, AES-GCM tamper detection — wired into `probeAcceptance` (now five probes + boot gate) / 新增确定性探针 `ApiProbe`（纯 JVM）：边界、已知向量（sha256/hmac）、往返、AES-GCM 防篡改——接入 `probeAcceptance`（现五探针 + 开机门禁）

## [p.1.2.0] L2 compatibility layer / L2 兼容层 (2026-09-05)

* End-to-end boot gate `ServerBootProbe` (subterra-probes): boots the dev server on the Java 25 toolchain with the L1 argument package, asserts the deterministic boot contract from the log (`Done (...)`, `Java 25 ... verified: true`, `JVM argument package present`, no FATAL), then stops the server gracefully via the console `stop` command — event-based, never timing-based / 端到端开机门禁 `ServerBootProbe`：在 Java 25 工具链开机开发服，按日志断言确定性开机契约（`Done (...)`、`Java 25 ... verified: true`、参数包齐备、无 FATAL），随后经控制台 `stop` 优雅停服——事件驱动、禁时序断言

* Java 25 gap registry `Java25Gaps` (subterra-compat): RCA-driven registry for future incompatibilities — boot-verified baseline (MC 1.21.1 + NeoForge 21.1.249 on JVM 25) starts empty; an OPEN entry fails acceptance until patched / Java 25 兼容坑登记 `Java25Gaps`（subterra-compat）：以 RCA 驱动的兼容坑登记结构——开机验证基线（JVM 25 上 MC 1.21.1 + NeoForge 21.1.249）为空；出现 OPEN 条目即在修补前判拒不通过

* `CompatProbe` (pure JVM) guards the registry contract; `probeAcceptance` now runs four probes (launch / launch-args / compat / boot gate) / `CompatProbe`（纯 JVM）守卫登记契约；`probeAcceptance` 现含四探针（启动 / 启动参数 / 兼容登记 / 开机门禁）

## \[p.1.1.1] Boot Minecraft 1.21.1 on Java 25 (dev-run plumbing) / 在 Java 25 上开机 (2026-09-05)

* Development server boots on Java 25 LTS with the Subterra argument package injected; L1 runtime validation logs `Java 25 (verified: true)` and `JVM argument package present (4 static flags)` before `Done (...)` / 开发服在 Java 25 LTS 上完整开机，L1 校验日志 `Java 25 (verified: true)` + 参数包齐备，随后 `Done (...)`；MC 1.21.1 + NeoForge 21.1.249 在 JVM 25 上未出现需要修补的兼容坑

* `neoforge.mods.toml` mirrors the p-track as a parseable game version (`mod_version_game`, strips the `p.` prefix) because FML rejects pre-release versions like `p.1.1.0` (`Illegal version number`) / `neoforge.mods.toml` 用 `mod_version_game` 镜像 p 轨（剥离 `p.` 前缀）；FML 拒绝 `p.1.1.0` 这类预发布版本号

* Dev runs expose the launch module to the game layer via `RunModel.additionalRuntimeClasspathConfiguration` (previously `NoClassDefFoundError: JvmEnv`) / 开发运行通过 `RunModel.additionalRuntimeClasspathConfiguration` 把 launch 模块暴露给游戏层（修复 `NoClassDefFoundError: JvmEnv`）

* Toolchain moved to Java 25 with `--release 21` (bytecode stays Java 21 / major 65, game JVM is Java 25) / 工具链升至 Java 25 + `--release 21`（字节码保持 Java 21 / major 65，游戏 JVM 为 Java 25）

* Dev-run assets resolved from a pre-seeded local asset cache (region-throttled Mojang CDN worked around with high-concurrency downloads); `java.net.preferIPv4Stack` required for NeoForm's asset downloader (IPv6 route dead here) / 开发运行资源使用预置本地资产缓存（境外 CDN 单连接限速，用高并发下载绕过）；NeoForm 下载器需 `java.net.preferIPv4Stack`（本网络 IPv6 路由不通）

## \[p.1.1.0] L1 launch layer / L1 启动层 (2026-09-05)

* JVM argument package `JvmLaunchArgs` (subterra-launch): ZGC baseline, heap policy (75% initial=max, AlwaysPreTouch) as statically verified flags; Java 25 compat opens injected before start (conservative seed, tuned RCA-driven in p.1.2) / JVM 参数包 `JvmLaunchArgs`（subterra-launch）：ZGC 基线、堆策略（初始=最大 75%、AlwaysPreTouch）作为静态可验标志；Java 25 兼容 add-opens 启动前注入（保守种子，p.1.2 依实启动日志 RCA 微调）

* Default run configurations (client/server/gameTestServer/data) inject the argument package via `runs.configureEach`；mod verifies applied flags at boot and logs a remediation hint when missing / 默认运行配置经 `runs.configureEach` 注入参数包；模组启动时校验实参并缺标志时报修复提示

* New deterministic probe `LaunchArgsProbe` (pure JVM) wired into `probeAcceptance` / 新增确定性探针 `LaunchArgsProbe`（纯 JVM）接入 `probeAcceptance`

* Dynamic tuning (ActiveProcessorCount, equal -Xms/-Xmx, AppCDS, client tiered) documented as opt-in, not statically verified / 动态项（CPU 钉扎、等大小堆、AppCDS、客户端分层）文档化按需启用，不做静态校验

## \[p.1.0.0] Modular skeleton / 模块化骨架 (2026-09-05)

* Six-submodule split (launch/compat/api/optim/tie/probes) as a modular monolith; only `subterra-api` publishes a standalone jar for domain mods / 六子模块拆分（launch/compat/api/optim/tie/probes）模块化单体；仅 `subterra-api` 独立发 jar 供领域模组依赖

* Iron-law dependency direction `launch ← compat ← api ← optim/tie ← probes` enforced at module level / 模块级依赖方向铁律

* L1 launch-layer `JvmEnv` runtime check moved into `subterra-launch` / L1 启动层 `JvmEnv` 运行期校验迁入 `subterra-launch`

* `subterra-probes` acceptance-only, excluded from the shipped mod jar; first deterministic probe `LaunchLayerProbe` (pure JVM, PASS on feature>=21) / `subterra-probes` 仅验收不进发布 jar；首个确定性探针 `LaunchLayerProbe`（纯 JVM，feature>=21 全 PASS）

* Root aggregator jar coalesces submodule outputs; MDK template cleaned (mod id `subterra`, NeoForge 21.1.249, Gradle 9.2.1, Java 21 bytecode on Java 25 JVM) / 根聚合 jar 合并子模块输出；MDK 模板完成清洗（mod id `subterra`、NeoForge 21.1.249、Gradle 9.2.1、Java 21 字节码运行于 Java 25 JVM）

