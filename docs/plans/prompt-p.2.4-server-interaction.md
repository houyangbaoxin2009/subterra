# Subterra p.2.4 服务端交互重构（增强通道，革命）· 全轨一次性交接单（p.2.4.1–p.2.4.6）· 开发任务提示词

## 0. 角色与目标
你是 Subterra（NeoForge 模组开发框架，服务 Toterra 主模组）的开发代理。
本提示词是一个**单会话一次性做完整个 p.2.4 里程碑**的交接单：p.2.4 =「服务端交互重构——客户端 ⇄ 服务端**增强通道**：tink v2 帧 + tsha1f 帧级强校验 + zd 载荷序列化（与 p.2.5 P2P 同栈，语言无关 ABI）；三级载荷策略（L1 高频增量差分 / 兴趣域订阅 · L2 中频快照 + 变更流 · L3 低频加密）；带宽削减 = 增量同步 / 状态降频插值 / 按需订阅；默认强加密（x25519 + AEAD，局域网可信可关）；原版协议路径保留（渐进增强，原版客户端仍可连）」（ROAD 表原文）。
**执行模式（与 p.2.3 收官完全一致）**：主代理只做任务下发与 trust-but-verify；摸底/设计、实现、探针、验收、文档、推送**全部由子代理执行**；每个子代领取一个自足小任务、完成即提交一次（报告一句）；全部完成后由收尾子代理统一 review+清理+推送（GitHub，仅 origin）。编号全程用 **p.2.4.x**（≤三级），禁止「阶段X / 第N块 / R1 / block N」等表述。
工作方式：先小任务摸底/定全轨最小闭环 → 逐子项实现 → 每个小任务完成即提交 → 全部子项完成后统一 review+清理+推送。

## 1. 项目上下文（必读）
- 系列聚合根 F:\Projects\Toterra-Repo：Subterra（框架，开源 TPL 2.0，origin=github.com/houyangbaoxin2009/subterra）+ Toterra（github.com/houyangbaoxin2009/toterra）。仓库内**当前不存在** network/交互增强模块（`**/network/**`、`**/interaction/**` 无 java），p.2.4 基本为绿地（服务端已有多处 `runtime.*` MC 壳接线，但无协议增强层）。
- **p.2.3 存档重构已全部落地并推 GitHub**（提交见 git log；编号 p.2.3.1–p.2.3.6，最新 p 轨号 **p.2.3.6**）：
  * p.2.3.1 **engine.zd** 通用 zd v2 载体（`ZdHeader`／`ZdPrimitives`／`ZdRow`／`ZdDocWriter`／`ZdVolume`，pure JDK）：头部规范抽自 profiler `ZdWriter`，**10 字节头精确保持** + 固定六字段 wire2 记录读写 + 树平铺往返；`SaveContainer` 六槽骨架（世界 / 配置 / 藏录 / 领域档案 / 遗物条目 / 台账 + 规则槽，规则叠加 = `DatapackRules` 语义）。
  * p.2.3.2 **runtime.save.SaveRuntime** 壳 + level.dat 迁移：engine.save.migrate（`LevelDatReader`／`LevelZdt`）把 level.dat 语义（世界名 / 种子 / 时间 / 规则）映射为 **zdt 混合文档**（td 元数据 + base64 zd 载荷）；`SaveRuntime` 在 `ServerStartedEvent` 旁路挂载，`-Dsubterra.probe.save` 门控 no-op（渐进增强）；E2E marker `[Subterra save]`。
  * p.2.3.3 player 迁移：NBT 根复合 → `PlayerDatum` → zdt；**通用 `ZdtTransfer` 载体**（`kind = [ version, meta, zd ]`）统一 level / player / 各侧数据。
  * p.2.3.4 engine.save.doc 四槽类型化文档（`LedgerDoc`／`DomainDoc`／`RelicDoc`／`RegisterDoc`）：确定性排序 + 线性构建防 O(n²)、字段级往返。
  * p.2.3.5 双层配置（`DatapackRules.resolveTiered`：全局 → 存档胜出）+ **subterra-migrate 首块迁移管线**：`api.migrate`（`SaveMigrator` / `MigrationReport` SPI）+ engine 提供实现（`ZdtSaveMigrator`）+ `migrate.Runner` 经 `ServiceLoader` 运行时发现；**migrate 仅依赖 api**（铁律）。
  * p.2.3.6 **`SaveExportArchive`** td 导出档案（与 `DatapackExportArchive` 同构，export∘rehydrate 逐字节）；`/subterra export` 的 save 接线留 p.2.18。
- **zd 载荷关键事实（p.2.3 已钉死，p.2.4 直接复用）**：`engine.zd` `ZdHeader` 10 字节头（`"TIEDBZD"`(7) + `0x00 0x02` 版本 + 1 flags 字节：bit0 字典 / bit1 列式 / bit2 ext / bit3 流式 / bit4 压缩变体）；记录固定字段号 1=kind 2=key 3=value_i64 4=value_f64 5=value_str 6=child_count；`ZdDocWriter.write(flags, rows)` / `ZdRow` 读写 + `ZdVolume` 树平铺；`ZdtTransfer` 提供混合文档的 write/parse/extract-zd（Base64 内嵌）。**语言无关规范与 tie-main `compiler/tdzd.tie` + `std/tink.tie` 一致**。
- **tink 帧协议（tie-main 侧已有，p.2.4 需 Java 侧移植）**：`std/tink.tie` 定义帧（crc32 + `frame_encode`/`frame_next`/`frame_skip`）；`tsha1f` 为帧级强校验（SHA-1 变体）。**本项目目前仅有 tie 实现，无 Java/网络 ABI 层**——p.2.4.1 把帧协议抽取为纯 JDK 载体（语言无关 ABI），与 zd 载荷序列化对接，并作为 p.2.5 P2P 同栈基座。
- **复用优先设施**：engine.zd 载体（p.2.3）；engine.api（`ZdtSaveMigrator` 迁移与 `api.migrate` SPI 已示范 factory / ServiceLoader 接线）；td（engine.config `Td`/`TdTable`/`TdValue`）；`DatapackRules`（p.2.2.9，双层配置）；既有 runtime MC 壳范式（`DatapackRuntime` / `SaveRuntime`，确定性 `-D`/`-P` 钩子）。
- 分层铁律：api ← engine ← runtime/migrate；engine 纯 JDK 不碰 MC；MC 壳落根 sourceSet（subterra-runtime source-host）；iron law 探针自动覆盖新增 engine.zd / engine.network 包（常量池扫描）。

## 2. 本次范围（p.2.4 全轨，六子项；一次性做完）
总原则：每子项=一个自足最小闭环（纯 JDK 优先、探针确定性命中验收；E2E 仅在真实生命周期必要处加确定性 `-D`/`-P` 钩子 marker，**保持既有 markers 稳定**除非某子项真需在服断言）；每子项独立提交。子项划分如下，以摸底后最小闭环为准可微调，**不擅自扩大范围**；不确定处先列出提问再动手。

1. **p.2.4.1 engine.network 帧载体（纯 JDK，语言无关 ABI）**：把 tie-main `std/tink.tie` 的帧协议（crc32 / frame_encode / frame_next / frame_skip）**抽取/移植为纯 JDK 通用帧载体**（字节构建 + 帧头 + 载荷 + 拆帧迭代），并定义与 engine.zd 载荷序列化的对接（zd byte[] 作为帧 payload）；提供语言无关 ABI（`tink v2` 帧格式常量），作为 p.2.5 P2P 同栈基座。探针：帧往返逐字节 + crc32 校验 + 拆帧边界（多帧 / 截断 / 脏字节）。
2. **p.2.4.2 tsha1f 帧级强校验（纯 JDK）**：`tsha1f`（SHA-1 帧校验）实现 + 帧级完整性校验接入（每帧校验；篡改一字节即拒绝）。与 crc32 分层（crc = 快速头校验，tsha1f = 强校验）。探针：合法帧通过、单字节篡改拒绝、错误拒绝路径计数只自增。
3. **p.2.4.3 三级载荷策略核心（纯 JDK）**：L1 高频增量差分 / 兴趣域订阅、L2 中频快照 + 变更流、L3 低频加密（策略选择器 + 增量 diff 核心 + 变更流枚举语义）；抽象引擎层策略（不接 MC 实体），服务端前端（ServerLevel ticking）在 p.2.4.4/p.2.4.5 或 runtime 壳挂载。探针：各级策略选择确定性 + 增量差分往返 + 变更流有序。
4. **p.2.4.4 带宽削减优化器（纯 JDK）**：增量同步 / 状态降频插值 / 按需订阅三种策略的量化权重与选择模型（接 p.2.4.3 策略器）；产生确定性 marker（策略命中计数）。探针：各策略计数 + 组合并行确定性。
5. **p.2.4.5 强加密载荷（纯 JDK）**：默认 **x25519 + AEAD** 强加密（`java.crypto`，与 `ApiProbe aes-gcm` 同栈），**局域网可信可关**（配置开关）；密钥交换 + 载荷加密 / 解密往返；L3 低频加密策略接入。探针：x25519+AEAD 往返 + 篡改拒绝 + 开关语义。
6. **p.2.4.6 runtime 壳 + 原版协议渐进增强（E2E 门）**：`runtime.network` MC 壳在既有通道旁挂增强通道（客户端 ⇄ 服务端），**保留原版协议路径**（原版客户端仍可连，渐进增强——默认不接管、不阻塞原版握手）；E2E 用确定性 `-D`/`-P` 钩子 marker 在真实生命周期断言增强帧收发 + 原版路径保留。探针：E2E marker + 原版路径保留断言。
收尾：ROAD p.2.4 行状态 → `landed / 已落地` + 逐项落地方案 bullet（bilingual，p.2.4.x 编号）；生成 p.2.5 续接提示词（见 §5）。

## 3. 子代理纪律（大任务必须用，禁止主代理硬扛全量）
- **整个 p.2.4 由子代理集群执行**：摸底/设计（只读，产出全轨任务拆分）、engine 子代理、runtime 子代理、探针子代理、收尾子代理（门禁 + 文档 + 推送）。按文件归属拆分，保证**同一文件同一时刻只有一个子代理编辑**；有编译/API 依赖时严格串行（engine 先于 runtime/探测编译）。
- **子代理节奏**：完成一个小任务即提交一次并报告；全部完成后由收尾子代理统一 review+清理+提交一次 push。
- 主代理 trust but verify：关键改动（build.gradle/新探针/注入核心）必须读实际文件核对，必要时重跑探针。
- **同文件多处修改禁止并行下发多个子代理**（并行 Edit 互覆盖丢内容，已踩坑多次）；同文件编辑串行单发，改后读文件核对；一次消息内同文件只允许一个 Edit。

## 4. 纪律（硬性，违反即返工）
- 主代理/子代理均：小任务逐个提交；全部完成后统一 review+清理+推送（GitHub）。
- 提交信息一律英文；作者保持本地 jiro；**只推 GitHub**（subterra origin），严禁 git.franj2.top。
- 版本 p.x.x.x 最多三级；禁用「阶段X / 第N块 / R1/R2 / block N」标签，p.2.4 子项一律 **p.2.4.x**。
- 文档双语（中英）；本任务尽量不新建文档，优先既有 docs/plans（本文件即全轨交接单）。
- 性能纪律：禁 O(n²)；热路径先建模（网络写传输可调节流 / 只增量）；注入只在装载窗口做，不阻塞主线程热路径。
- 确定性：验收一律确定性探针；禁时序断言；断言前先做前置动作；失败计数只在失败路径自增。E2E 沿用事件驱动标记 + **`-D`/`-P` 转发属性钩子**（Done 后装载完成），不赌 sleep、不依赖 stdin 命令往返。
- 编译纪律：改探针源后必须重编（compileJava/探针任务），否则 JavaExec 用旧字节码。
- 文件编辑纪律：同文件连续编辑必须串行单发、改后读文件核对。
- 依赖铁律：api ← engine ← runtime/migrate；engine 纯 JDK 不碰 MC；纯 JDK 落 engine.*，MC 壳落根 sourceSet；migrate 仅依赖 api。
- 许可：permissive 可移植带声明；copyleft 仅 clean-room；存疑按不允许处理（原版 / NeoForge 内部先 javap 核实，不从他人实现照抄）。
- 铁律接线：settings.gradle include 与 iron law 探针覆盖；FFM 探针 JavaExec 需 `--enable-native-access=ALL-UNNAMED`（本里程碑不强依赖 FFM，但沿用既有 tink/zd 语义）。
- 验收门：`gradlew :subterra-devkit:probeAcceptance` 全绿（含 BootProbe / DatapackE2EProbe / SaveE2EProbe 三次开服）才算完成。

## 5. 模块完成 → 同格式提示词（延续机制，强制）
- p.2.4 全轨完成后，必须生成 p.2.5 同格式提示词并纳入记录：
  1. 更新上下文段：p.2.4 各子项已落地、最新 p 轨号（p.2.4.6）、关键路径（engine.network / runtime.network），ABI/接线变化（tink v2 帧载体、tsha1f、三级载荷策略、x25519+AEAD、增强通道渐进增强）；
  2. 更新本次范围段：指向 p.2.5（P2P 去中心化网络，自原 p.4.1 前移：tink v2 + tsha1f 帧级强校验；zd 作为自定义载荷通道通信介质，先可行性基准）；
  3. 保持第 0/3/4 段结构不变；
  4. 存放：仓库 `docs/plans/prompt-p.2.5-p2p.md`（bilingual），随当次提交一并提交。

## 6. 关键路径速查
- 仓库根：F:\Projects\Toterra-Repo\Subterra；p.2.4 工作区建议：`subterra-engine\...\engine\network\`（tink v2 帧 / tsha1f / 三级载荷策略 / 带宽优化 / 加密核心，纯 JDK）与 `subterra-runtime\...\runtime\network\`（增强通道 MC 壳，待发现接线点）。
- **ez 载体触点（p.2.3 已落地，直接复用）**：`engine.zd` `ZdHeader` / `ZdPrimitives` / `ZdRow` / `ZdDocWriter` / `ZdVolume` + `engine.save.migrate.ZdtTransfer`；tie-main 侧参考 `std/tink.tie`（F:\Projects\tie-repo\tie-main）——p.2.4 把 tink 帧协议移植为纯 JDK 语言无关 ABI。
- 参考既有接线范式：engine.datapack / engine.save + runtime.datapack（`DatapackRuntime`）/ runtime.save（`SaveRuntime`）+ 确定性钩子（build.gradle server 段 subterra.datapacks / subterra.override / subterra.probe.export / subterra.probe.save）。
- 探针资产：`subterra-devkit\src\main\java\io\toterra\subterra\probes\`（SaveExportProbe 等）；新网络探针落同处；probeAcceptance 接线：`subterra-devkit\build.gradle`（E2E 需 mustRunAfter 既有开服门；staging 用 build/tmp）。
- 关联里程碑：p.2.5 P2P 同栈（tink v2 + tsha1f + zd 自定义载荷）；p.2.11 tink hub 形态 B（会话可编程，外部进程经 tink 帧驱动）。

## 7. 输出与汇报
- 主代理与各子代理均：双语说明（做了什么 + 结论）后立即提交；收尾子代理报 probeAcceptance 全量结果（含 E2E markers 数与三次开服）+ ROAD p.2.4 landed + 新提示词文件路径 + push 结果；最终给用户汇报完整子项清单与验收证据。