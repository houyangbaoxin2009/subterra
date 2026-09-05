# Subterra Changelog / 变更日志

Reverse chronological. Two-track versioning: `p.x.y.z` pre-release, `r.x.y.z` release (stabilization only). / 倒序排列。双轨编号：p 预发布、r 正式版（仅优化稳定）。

## [p.1.0.0] Modular skeleton / 模块化骨架 (2026-09-05)

* Six-submodule split (launch/compat/api/optim/tie/probes) as a modular monolith; only `subterra-api` publishes a standalone jar for domain mods / 六子模块拆分（launch/compat/api/optim/tie/probes）模块化单体；仅 `subterra-api` 独立发 jar 供领域模组依赖
* Iron-law dependency direction `launch ← compat ← api ← optim/tie ← probes` enforced at module level / 模块级依赖方向铁律
* L1 launch-layer `JvmEnv` runtime check moved into `subterra-launch` / L1 启动层 `JvmEnv` 运行期校验迁入 `subterra-launch`
* `subterra-probes` acceptance-only, excluded from the shipped mod jar; first deterministic probe `LaunchLayerProbe` (pure JVM, PASS on feature>=21) / `subterra-probes` 仅验收不进发布 jar；首个确定性探针 `LaunchLayerProbe`（纯 JVM，feature>=21 全 PASS）
* Root aggregator jar coalesces submodule outputs; MDK template cleaned (mod id `subterra`, NeoForge 21.1.249, Gradle 9.2.1, Java 21 bytecode on Java 25 JVM) / 根聚合 jar 合并子模块输出；MDK 模板完成清洗（mod id `subterra`、NeoForge 21.1.249、Gradle 9.2.1、Java 21 字节码运行于 Java 25 JVM）