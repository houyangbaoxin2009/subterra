# Subterra Changelog / 变更日志

Reverse chronological. Two-track versioning: `p.x.y.z` pre-release, `r.x.y.z` release (stabilization only). / 倒序排列。双轨编号：p 预发布、r 正式版（仅优化稳定）。

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

