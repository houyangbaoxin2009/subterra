# Subterra p.2.0 — Module Re-layout / 模块重构计划

**Status / 状态:** Plan (awaiting approval) / 计划（待批准）
**Date / 日期:** 2026-09-08
**Owner / 责任人:** jiro

---

## Context / 背景

p.1 线（骨架 / 启动 / 兼容 / API / 优化集 / td 配置 / 日志 / 世界生成管线，含 p.1.8.20–32 vanilla 保真）已全部落地。按框架设计 `docs/2026-09-08-subterra-framework-design.md` §3 与 `docs/ROAD.md`，p.2.0 = **模块重构：api / engine / runtime / migrate / devkit 分层落地，依赖铁律探针化，探针全绿保持**。

目标状态：
- `subterra-api`（纯 JDK 契约，独立发布 jar）— 现状已符合，不动。
- `subterra-engine`（新 Gradle 模块，纯 JDK 引擎，聚合进主 jar，零 MC 类路径）。
- `subterra-runtime`（source-host 目录挂进 root main sourceSet，MC 层接线，非 Gradle 子项目）。
- `subterra-migrate`（新 Gradle 模块，仅依赖 api，p.2.0 建骨架）。
- `subterra-devkit`（吸收现 subterra-probes，探针 + 未来 datagen/示例模组，永不进发布 jar）。

依赖铁律（探针编译期强制）：

```
api ← engine ← runtime
       ↑        ↑
   migrate ← devkit
```

## 已确认决策 / Confirmed decisions

1. 工作区遗留剖析插桩（SubterraDensity perf 计数器、WorldProfilerHook perfSummary/profileCenter、WorldProfileAcceptanceProbe 新默认值、build.gradle 属性透传）**先提交为 p.1.8.33**，再开始重构。
2. **完整包改名**（忠实设计）：`optim.*` → `engine.*` / `runtime.optim.*`，`config` → `engine.config`，`log` → `engine.log`，`profiler` → `engine.worldgen.profiler`，`compat` → `runtime.fix`，`launch` → `runtime.launch`，root MC `worldgen.*` → `runtime.worldgen.*`。
3. 编号纪律：`mod_version` 每次提交递增（p.2.0.x），CHANGELOG 每次提交即时写入（倒序、双语 `## [p.x.y.z] Title (date)`）。
4. 工作流：小任务逐个提交——每个子项完成 → 提交 + 报告 → 等确认再下一个。

## 包重命名规则（机械替换，最长前缀优先）/ Package rules

| 规则 | 旧前缀 | 新前缀 |
|---|---|---|
| E1 | `io.toterra.subterra.optim.worldgen.pipeline` | `io.toterra.subterra.engine.worldgen.pipeline` |
| E2 | `io.toterra.subterra.optim.worldgen.guard` | `io.toterra.subterra.engine.worldgen.guard` |
| E3 | `io.toterra.subterra.optim.worldgen.ticking` | `io.toterra.subterra.engine.worldgen.ticking` |
| E5 | `io.toterra.subterra.optim.`（engine 模块内） | `io.toterra.subterra.engine.optim.` |
| H0 | `io.toterra.subterra.optim.worldgen.async` | `io.toterra.subterra.runtime.worldgen.async` |
| H1 | `io.toterra.subterra.optim.`（host 壳） | `io.toterra.subterra.runtime.optim.` |
| C | `io.toterra.subterra.config.` | `io.toterra.subterra.engine.config.` |
| L | `io.toterra.subterra.log.` | `io.toterra.subterra.engine.log.` |
| P | `io.toterra.subterra.profiler.` | `io.toterra.subterra.engine.worldgen.profiler.` |
| CP | `io.toterra.subterra.compat.` | `io.toterra.subterra.runtime.fix.` |
| LA | `io.toterra.subterra.runtime.JvmEnv/JvmLaunchArgs` | `io.toterra.subterra.runtime.launch.*` |
| R | `io.toterra.subterra.worldgen.` | `io.toterra.subterra.runtime.worldgen.` |

- host 壳包名取 `runtime.optim.<历史后缀>`（如 `runtime.optim.server.advancement`）→ mixin JSON 与 Subterra.java 引导引用只改 `optim.` → `runtime.optim.` 前缀，改动面最小。
- `@Mod` 入口类（Subterra.java / Config / SubterraClient / SubterraLogging / Slf4jLogSink）**留在 root `io.toterra.subterra`**（jar 入口点）。
- profiler 纯核心进 **engine.worldgen.profiler**（runtime 的 WorldProfilerHook 运行时消费，必须随主 jar 发布；探针/验收工具在 devkit）。

## 子项分解 / Sub-item decomposition

> 每子项：`git mv` 目录 + 规则替换（本文件包声明 + 仓库全量引用 + 探针 import）+ build 文件更新 + `mod_version` 递增 + CHANGELOG 条目。验证 = `probeAcceptance`（p.1.8.33 期间为 `:subterra-probes:probeAcceptance`，此后 `:subterra-devkit:probeAcceptance`）+ 对应 `rg` 零残留门禁。

### p.1.8.33 — 预检：提交遗留插桩
- 提交 4 个未提交文件（build.gradle 属性透传、SubterraDensity perf 计数器、WorldProfilerHook perfSummary/profileCenter、WorldProfileAcceptanceProbe 新默认值）。
- 提交未跟踪的 `.trae/documents/p149-language-agnostic-reading-search.md`（已完成项文档，与已提交的 p1814 文档模式一致）。
- `mod_version=p.1.8.33`。验证：`:subterra-probes:probeAcceptance` + `build`。

### p.2.0.1 — Gradle 脚手架（engine 模块 + runtime 目录 + migrate + devkit 更名）
- `git mv subterra-probes subterra-devkit`（Java 包保持 `io.toterra.subterra.probes`）。
- 新建 `subterra-engine/`：build.gradle（java-library / toolchain 21 / `implementation project(':subterra-api')`）+ `engine/package-info.java` + `engine/export/package-info.java` 占位。
- 新建 `subterra-migrate/`：build.gradle（仅依赖 api）+ `migrate/package-info.java` 骨架。
- 新建 `subterra-runtime/src/main/java/io/toterra/subterra/runtime/`：package-info + `tie/`、`cfglog/` 占位。
- 新建 `subterra-devkit/src/main/java/io/toterra/subterra/devkit/package-info.java`。
- settings.gradle：加 engine/migrate，改名 devkit，**移除空模块 subterra-tie**（仅 build.gradle，已确认无源码）；过渡期保留 optim/config/log/profiler/launch/compat。
- devkit build.gradle：保留全部 42 个 JavaExec 任务 + probeAcceptance；deps 加 api/engine/migrate。
- root build.gradle：sourceSets.main 加 `subterra-runtime/src/main/java`；jar 聚合排除 devkit + migrate；`implementation project(':subterra-engine')`。
- `mod_version=p.2.0.1`。

### p.2.0.2 — worldgen 核心 → engine.worldgen（最大 engine 迁移）
- `git mv subterra-optim/.../optim/worldgen/{pipeline,guard,ticking}/**` → `subterra-engine/.../engine/worldgen/{pipeline,guard,ticking}/**`。规则 E1/E2/E3 全仓库替换。
- 探针 import 更新 19 个：PipelineFaces / Density / NoiseRouter / SurfaceRules / SurfaceBridge / Simplex / FormulaTerrain / Formula / DimensionWorlds / Dimension / NetherEnd / NoiseBase / Perlin / Composite / ChunkGrid / BiomeSource / TerrainCurve / WorldGenGuard / TickingChunkCache。
- root 更新：SubterraDensity.java、SameSeedCompare.java。
- root build.gradle：additionalRuntimeClasspathConfiguration 加 `project(':subterra-engine')`。
- 验证：probeAcceptance + `runWorldCompare -Dsubterra.compareSelfCheck=true`（golden 逐位一致）+ rg `optim\.worldgen\.(pipeline|guard|ticking)` → 0。

### p.2.0.3 — optim 其余 → engine.optim
- `git mv subterra-optim/.../optim/**` → `subterra-engine/.../engine/optim/**`（sched / entity / logic.pronounce / server.{item_control,loading,dynamic} / client / memory / network / render / util）。规则 E5。
- 探针更新 11 个：FlowSched / Activation / SpawnEnforcement / Mobcap / Merge / BreedingCap / Pronounce / ReadingSearch / ItemControl / SyncLoadGuard / ScratchPool。
- 移除 `subterra-optim` include、删空目录、devkit/root deps + run 注入剔除。

### p.2.0.4 — config → engine.config
- `git mv subterra-config/**` → `subterra-engine/.../engine/config/**`。规则 C。
- 探针：ConfigProbe / ConfigPackProbe / LogProbe / WorldProfileProbe。root：WorldgenConfig.java、SubterraLogging.java。
- subterra-log/profiler 的 build.gradle 依赖 `:subterra-config` → `:subterra-engine`；profiler deps 收敛为 `api+engine`。移除 config include/目录/deps。

### p.2.0.5 — log → engine.log
- `git mv subterra-log/**` → `subterra-engine/.../engine/log/**`（含 .analysis）。规则 L。
- 探针：LogProbe。root：SubterraLogging.java、Slf4jLogSink.java。
- 移除 log include/目录/deps。

### p.2.0.6 — profiler → engine.worldgen.profiler
- `git mv subterra-profiler/**` → `subterra-engine/.../engine/worldgen/profiler/{core,facade,report}/**`。规则 P。
- 探针：WorldProfileProbe。root：WorldProfilerHook.java、WorldProfilerConfig.java。
- 移除 profiler include/目录/deps；run 注入此时收敛为 **engine + api** 两个。
- 验证额外跑 `:subterra-devkit:runWorldProfileAcceptance`（显式，不在 probeAcceptance 内）。

### p.2.0.7 — launch + compat → runtime.{launch,fix}
- `git mv subterra-launch/.../runtime/{JvmEnv,JvmLaunchArgs}.java` → `subterra-runtime/.../runtime/launch/`（规则 LA）。
- `git mv subterra-compat/.../compat/Java25Gaps.java` → `subterra-runtime/.../runtime/fix/`（规则 CP）。
- 引用：Subterra.java、LaunchLayerProbe / LaunchArgsProbe / CompatProbe。
- devkit build.gradle 加 `implementation rootProject.sourceSets.main.output`（launch/fix 现在在 root source-host sourceSet 内；单向文件依赖，root jar 排除 devkit 无环；配置缓存报错则回退 `implementation files(rootProject.sourceSets.main.output)`）。
- 移除 launch/compat include/目录/devkit deps/root deps/run 注入。

### p.2.0.8 — root MC worldgen → runtime.worldgen
- `git mv src/main/java/io/toterra/subterra/worldgen/**` → `subterra-runtime/.../runtime/worldgen/**`（gen / profiler / compare 三个子包）。规则 R。
- 引用：Subterra.java 两处全限定 `worldgen.gen.*`；WorldProfilerHook → `runtime.worldgen.gen.SubterraDensity`。
- root build.gradle：`runWorldCompare.mainClass` → `io.toterra.subterra.runtime.worldgen.compare.SameSeedCompare`。
- 验证：probeAcceptance + runWorldCompare 自检。

### p.2.0.9 — optim-shell hosts → runtime.optim（最大 MC 层提交）
- 迁移（规则 H1，c2me 用 H0）：invadvopt / smoothboot / servercore / jec / itemban / c2me 六个 host 目录 → `subterra-runtime/.../runtime/optim/**`（c2me → `runtime/worldgen/async`）；invadvopt 的 `src/test/java` → root `src/test/java`（3 个 JUnit 测试，仅 java 依赖，已核实）。
- **8 个 mixin JSON**（全部在 root `src/main/resources/`，已核实；`neoforge.mods.toml` 只引用文件名，不动）：`package`（+plugin 如有）字段改为 `io.toterra.subterra.runtime.optim.<...>`。`subterra-smoothboot.mixins.json` 为 `required:true`/`defaultRequire:1` → 开机门禁对包名失配硬失败（probeAcceptance 内安全网）。
- Subterra.java 7 处 host 引导引用 + SubterraClient.java 的 ReadingSearch 引用更新。
- root build.gradle：移除 6 条 host srcDir + test srcDir 行；删除 6 个空 host 目录。

### p.2.0.10 — 依赖铁律探针 IronLawProbe
- 新 `subterra-devkit/src/main/java/io/toterra/subterra/probes/IronLawProbe.java`（纯 JDK 零依赖，~100 行）：常量池扫描 `.class`（magic 0xCAFEBABE + 按 tag 跳过，Utf8 项做前缀匹配），按 label 断言：
  - `api`：禁 engine/runtime/migrate/probes + `net/minecraft`/`net/neoforged`。
  - `engine`：禁 runtime/migrate/probes + MC（可引 api）。
  - `runtime`（= root main output）：禁 migrate/probes（可引 engine+api+MC）。
  - `migrate`：禁 engine/runtime/probes + MC（仅 api）。
  - 附赠：跨 root 的 FQCN 重复检查（替代 jar 聚合 `duplicatesStrategy.EXCLUDE` 的静默风险）。
- Gradle：`runIronLawProbe` JavaExec，args 传 4 组 `(label, classesDirs.asPath)`（api/engine/migrate 取子项目 sourceSet 输出，runtime 取 root main output），挂入 probeAcceptance。
- 负例验证一次：临时给 engine 类加 `import ...runtime.launch.JvmEnv` 确认探针 FAIL 后还原。

### p.2.0.11 — 清理 + 文档 + 终版
- 删除空目录树 `net/neoforged/neoforge/event/tick`（未跟踪空目录，已核实）。
- `SubterraLogging.MODULES` 注册表更新为新布局（api / engine / 主 jar / migrate / devkit）+ 新依赖边。
- 探针/root 中过时模块名 Javadoc 顺手清理（subterra-profiler / subterra-launch / subterra-config 字样 → engine）。
- `docs/ROAD.md` 标记 p.2.0 landed；`mod_version=p.2.0.11` + 终版 CHANGELOG。
- 验证：全套（见下）。

## 关键文件 / Critical files

- `build.gradle`（root）：sourceSets srcDirs、jar 聚合排除、deps、additionalRuntimeClasspathConfiguration、runWorldCompare mainClass。
- `settings.gradle`：模块列表过渡至 api/engine/migrate/devkit。
- `subterra-devkit/build.gradle`（由 subterra-probes 更名）：42 探针任务 + probeAcceptance + devkit→root output 依赖 + runIronLawProbe。
- `src/main/java/io/toterra/subterra/Subterra.java`：7 处 host 引导引用 + runtime.launch import（p.2.0.7–9 重写）。
- `gradle.properties`：mod_version 每子项递增。
- 8 个 mixin JSON（root `src/main/resources/`）：p.2.0.9 改 package 字段。

## 验证 / Verification

| 阶段 | 命令 |
|---|---|
| p.1.8.33 | `./gradlew :subterra-probes:probeAcceptance`；`./gradlew build` |
| 每个迁移子项 | `./gradlew :subterra-devkit:probeAcceptance`（纯 JVM 探针 + Java-25 开机门禁）+ 对应 `rg` 零残留 |
| p.2.0.2 / p.2.0.8 | `./gradlew runWorldCompare -Dsubterra.compareSelfCheck=true`（golden 逐位一致） |
| p.2.0.6 / 终版 | `./gradlew :subterra-devkit:runWorldProfileAcceptance`（显式） |
| p.2.0.10+ | 铁律探针入 probeAcceptance；负例验证一次 |
| 终版 p.2.0.11 | 全量 `probeAcceptance`；`build`（CI 对齐）；`runWorldCompare` 自检；`jar tf build/libs/subterra-*.jar`（仅 api/engine/runtime 包，无 devkit/migrate/probes，无重复 FQCN）；`:subterra-api:publish` 确认独立 jar 仅 api；rg `io\.toterra\.subterra\.(optim|config|log|profiler|compat|worldgen)\b` → 0 |

## 风险与对策 / Risks

- **过渡期重复 FQCN**：每子项先清空一个旧树再移除 include；duplicatesStrategy.EXCLUDE 静默风险由铁律探针 FQCN 检查 + rg 零残留门禁 + 终版 `jar tf` 兜底。
- **mixin 包名字符串**：8 个 JSON 与 host 迁移同提交（p.2.0.9）；smoothboot `required:true` 硬卡开机门禁。
- **devkit → root output 依赖**：单向文件依赖，配置缓存异常时回退 `files(...)` 写法；仅 3 个 launch/fix 探针使用，惰性加载 JvmEnv/Java25Gaps。
- **generateModMetadata**：属性驱动、无类引用；`p.` 前缀剥离后 `2.0.x` 可解析，不受影响。
- **发布**：仅 api 有 publishing 块；engine/migrate/devkit 无 publishing；root 聚合 `components.java` 不变。
- **td 配置路径 / lang 键 / datapack JSON / `subterra:density` 注册名**：全部基于文件/注册表名，零类引用，不受影响。
- **CI**：`build.yml` 仅 `./gradlew build`（JDK 21），probeAcceptance 本就不在 build 内，不变。
