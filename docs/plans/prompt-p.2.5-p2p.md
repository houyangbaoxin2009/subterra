# Subterra p.2.5 P2P 去中心化网络（自原 p.4.1 前移）· 全轨一次性交接单（摸底定子项）· 开发任务提示词

## 0. 角色与目标
你是 Subterra（NeoForge 模组开发框架，服务 Toterra 主模组）的开发代理。
本提示词是一个**单会话一次性做完整个 p.2.5 里程碑**的交接单：p.2.5 =「P2P 去中心化网络」——tink v2 + tsha1f 帧级强校验（**复用 engine.network 同栈**）；**zd 作为自定义载荷通道通信介质**（先可行性基准）；去中心化原则——打洞直连为主、DHT 无中心发现、志愿 relay 兜底、无中心账号/会话（ROAD 表原文）。
**执行模式（与 p.2.4 收官完全一致）**：主代理只做任务下发与 trust-but-verify；摸底/设计、实现、探针、验收、文档、推送**全部由子代理执行**；每个子代领取一个自足小任务、完成即提交一次（报告一句）；全部完成后由收尾子代理统一 review+清理+推送（GitHub，仅 origin）。编号全程用 **p.2.5.x**（≤三级），禁止「阶段X / 第N块 / R1 / block N」等表述。
工作方式：先**摸底/可行性基准**（读 tie-main、评估 zd 作载荷通道吞吐与打洞可行性）并产出全轨子项拆分（6–8 个）→ 逐子项实现 → 每个小任务完成即提交 → 全部子项完成后统一 review+清理+推送。

## 1. 项目上下文（必读）
- 系列聚合根 F:\Projects\Toterra-Repo：Subterra（框架，开源 TPL 2.0，origin=github.com/houyangbaoxin2009/subterra）+ Toterra（github.com/houyangbaoxin2009/toterra）。
- **p.2.4 服务端交互重构已全部落地并推 GitHub**（提交见 git log；编号 p.2.4.1–p.2.4.6，最新 p 轨号 **p.2.4.6**）。engine.network 五子引擎层纯 JDK 落地：
  * **p.2.4.1 engine.network.frame**：tink v1 crc32 帧载体（`FrameV1`／`FrameIterator`／`Crc32Ieee`）+ v2 ABI 常量（`FrameConst`：magic "tk" / version 2 / flags bit0 强校验 / 保留位 bit5..7 + ext 常量）+ zd 载荷桥（`ZdFrameBridge`，zd byte[] 作 payload）。语言无关 ABI，**p.2.5 同栈基座**。
  * **p.2.4.2 engine.network.integrity**：`tsha1f` 便携 Java（tie-main 金标向量 KAT 全中，含 n=8 快 8B 与 n=48 强档 32B）+ `FrameV2` 帧编解码（快/强校验槽、ext TLV 未知键跳过、v1 兼容读、保留位拒绝、流式分块 `streamSplit`/`streamJoin`）+ 单字节篡改即拒。
  * **p.2.4.3 engine.network.strategy**：`StrategySelector`／`PayloadLevel`（L1/L2/L3）／`IncrementalDiff`／`ChangeStream`——三级载荷策略，确定性选择器 + 增量 diff 往返 + 变更流有序回放。
  * **p.2.4.4 engine.network.bandwidth**：`BandwidthOptimizer`／`BandwidthTechnique`——增量同步/状态降频插值/按需订阅量化权重 + argmax 选型（固定枚举序破平局），确定性命中计数，L3 加密适用性读局域网可信开关。
  * **p.2.4.5 engine.network.crypto**：`SecureChannel`／`EncryptionConfig`——x25519 协商 + SHA-256 KDF → AES-256-GCM（12B nonce = 8B BE seq + 4B 零），往返/篡改拒绝/错钥拒绝，局域网可信默认开（`-D`/`-P` 可关）。
  * **p.2.4.6 runtime.network.EnhancedChannelRuntime**：ServerStartedEvent 旁路壳，`-Dsubterra.probe.network` 门控（默认 no-op、原版握手零接触）；接线断言组件装载 + `original-path-preserved` + E2E 第四次开服 `NetworkE2EProbe`。
- **ABI 事实（p.2.4 已钉死，p.2.5 直接复用）**：`FrameV2` 线格式 = `[magic u8[2]="tk"][version u8=2][flags u8][len u32 BE][ext_len u16 BE][ext][payload][integrity]`；flags bit0 = INTEGRITY_STRONG：0→快校验段 8B = from_ascii(tsha1f(payload,8,48))、1→强校验段 32B = digest 全宽 hex 转字节取前 32（模型/位宽按 ext key1/key2 覆盖）；保留位 bit5..7 必须 0 否则拒绝；首两字节非 magic → v1 兼容读委托 `FrameV1` crc 校验；未知 ext TLV 键一律跳过。`tsha1f` KAT 向量见 engine.network.integrity（纯 Java，可用于任意载荷通道自校验）。
- **zd 载荷关键事实（p.2.3 已钉死，p.2.5 直接复用）**：`engine.zd` `ZdHeader` 10 字节头（`"TIEDBZD"`(7) + `0x00 0x02` 版本 + 1 flags）、`ZdRow`/`ZdDocWriter`/`ZdVolume` 树平铺、`ZdtTransfer` 混合文档。p.2.5 评估 **zd 作自定义载荷通道通信介质**：先做可行性基准（编码体积 / 建头开销 / 往返吞吐），再接帧栈。
- **tink / tie 参照（tie-main）**：`std/tink_v2.tie`（`tink2` 命名空间，tink v1 crc32 + v2 帧）与 `tsha1f` 金标向量、`std/tink.tie`（crc32 / frame_encode/next/skip）——语言无关规范；p.2.4 已把帧协议与 tsha1f 移植为纯 JDK，p.2.5 不再从 tie 侧重新移植（复用即好）。aggregate 参考 tie-main：**F:\Projects\tie-repo\tie-main**。**复用优先设施**：engine.network（frame/integrity/strategy/bandwidth/crypto 全栈），engine.zd（载荷序列化），engine.api（SPI/service-loader 范式），td（engine.config `Td/`TdTable`），既有 runtime MC 壳范式（`DatapackRuntime`/`SaveRuntime`/`EnhancedChannelRuntime`，确定性 `-D`/`-P` 钩子）。分层铁律：api ← engine ← runtime/migrate；engine 纯 JDK 不碰 MC；MC 壳落根 sourceSet；iron law 探针自动覆盖新增 engine.p2p 包（常量池扫描，新增非 engine 归属的 P2P 核心务必放 engine.*）。

## 2. 本次范围（p.2.5 全轨，子项以摸底后最小闭环为准）
总原则：每子项=一个自足最小闭环（纯 JDK 优先、探针确定性命中验收；E2E 仅在真实生命周期必要处加确定性 `-D`/`-P` 钩子 marker，**保持既有 markers 稳定**）；每子项独立提交。**先摸底/可行性基准**再定 6–8 个子项拆分——本段为示意，最终以摸底后最小闭环为准，可微调，**不擅自扩大范围**；不确定处先列出提问再动手。示意方向：
1. **P2P 端点寻址与握手**：节点 ID（可寻址标识）、密钥绑定（接口 source 复用 p.2.4.5 x25519）、打洞/relay 地址簿与确定性握手（UDP 打洞优先；握手经 p.2.4 帧加密）。探针：确定性握手往返 + 地址解析。
2. **帧级传输层（复用 FrameV2 栈）**：以 tink v2 帧为传输单元的自定义载荷通道（复用 engine.network.frame/integrity）；会话/序号、流式分块重传语义（复用 `streamSplit`/`streamJoin`）。探针：多帧往返 + 乱序/丢包确定性处理。
3. **zd 自定义载荷通道 ABI（可行性基准核心）**：`zd` 编码载荷作为通道数据单元（复用 engine.zd + `ZdtTransfer`），先量化基准（编码/解码吞吐、体积比），确认是否满足 p.2.5 传输需求。探针：确定性基准数字 + 编解码往返一致。
4. **可行性基准探针（先做，定全轨）**：tink 帧 + zd 载荷 + tsha1f 强校验的组合端到端吞吐与体积基准，产出数字与结论，反推后续子项是否需要调整。
提示：以上四点是示意，最终子项（6–8 个）以摸底后最小闭环为准；去中心化原则（打洞直连为主、DHT 无中心发现、志愿 relay 兜底、无中心账号/会话）贯穿每次设计。

## 3. 子代理纪律（大任务必须用，禁止主代理硬扛全量）
- **整个 p.2.5 由子代理集群执行**：摸底/可行性基准（只读 + 基准探针，产出全轨任务拆分）、engine 子代理、runtime 子代理、探针子代理、收尾子代理（门禁 + 文档 + 推送）。按文件归属拆分，保证**同一文件同一时刻只有一个子代理编辑**；有编译/API 依赖时严格串行（engine 先于 runtime/探测编译）。
- **子代理节奏**：完成一个小任务即提交一次并报告；全部完成后由收尾子代理统一 review+清理+提交一次 push。
- 主代理 trust but verify：关键改动（build.gradle/新探针/注入核心）必须读实际文件核对，必要时重跑探针。
- **同文件多处修改禁止并行下发多个子代理**（并行 Edit 互覆盖丢内容，已踩坑多次）；同文件编辑串行单发，改后读文件核对；一次消息内同文件只允许一个 Edit。

## 4. 纪律（硬性，违反即返工）
- 主代理/子代理均：小任务逐个提交；全部完成后统一 review+清理+推送（GitHub）。
- 提交信息一律英文；作者保持本地 jiro；**只推 GitHub**（subterra origin），严禁 git.franj2.top。
- 版本 p.x.x.x 最多三级；禁用「阶段X / 第N块 / R1/R2 / block N」标签，p.2.5 子项一律 **p.2.5.x**。
- 文档双语（中英）；本任务尽量不新建文档，优先既有 docs/plans（本文件即全轨交接单）。
- 性能纪律：禁 O(n²)；热路径先建模（网络写传输可调节流 / 只增量）；注入只在装载窗口做，不阻塞主线程热路径。
- 确定性：验收一律确定性探针；禁时序断言；断言前先做前置动作；失败计数只在失败路径自增。E2E 沿用事件驱动标记 + **`-D`/`-P` 转发属性钩子**（Done 后装载完成），不赌 sleep、不依赖 stdin 命令往返。
- 编译纪律：改探针源后必须重编（compileJava/探针任务），否则 JavaExec 用旧字节码。
- 文件编辑纪律：同文件连续编辑必须串行单发、改后读文件核对。
- 依赖铁律：api ← engine ← runtime/migrate；engine 纯 JDK 不碰 MC；纯 JDK 落 engine.*，MC 壳落根 sourceSet；migrate 仅依赖 api。
- 许可：permissive 可移植带声明；copyleft 仅 clean-room；存疑按不允许处理（原版 / NeoForge 内部先 javap 核实，不从他人实现照抄）。
- 铁律接线：settings.gradle include 与 iron law 探针覆盖；FFM 探针 JavaExec 需 `--enable-native-access=ALL-UNNAMED`。
- 验收门：`gradlew :subterra-devkit:probeAcceptance` 全绿（含 BootProbe / DatapackE2EProbe / SaveE2EProbe / NetworkE2EProbe 四次开服）才算完成。

## 5. 模块完成 → 同格式提示词（延续机制，强制）
- p.2.5 全轨完成后，必须生成 p.2.6 同格式提示词并纳入记录：
  1. 更新上下文段：p.2.5 各子项已落地、最新 p 轨号（p.2.5.x）、关键路径（engine.p2p 等）、ABI/接线变化（P2P 端点/握手、帧级传输层、zd 载荷通道可行性结论）；
  2. 更新本次范围段：指向 p.2.6（C2ME bundled，自原 p.3.0 前移：engine.worldgen.async 核心 + runtime 异步壳，随框架发布，MIT 声明）；
  3. 保持第 0/3/4 段结构不变；
  4. 存放：仓库 `docs/plans/prompt-p.2.6-c2me.md`（bilingual），随当次提交一并提交。

## 6. 关键路径速查
- 仓库根：F:\Projects\Toterra-Repo\Subterra；p.2.5 工作区建议：`subterra-engine\...\engine\network\`（帧/完整性/策略/带宽/加密，已就绪同栈基座，**复用优先**）与候选新包 `subterra-engine\...\engine\p2p\`（端点/握手/传输/zd 载荷通道，纯 JDK）；MC 壳落 `subterra-runtime\...\runtime\network\`（EnhancedChannelRuntime 挂后续通道接线 / 关停钩子已预留）。
- **帧栈触点（p.2.4 已落地，直接复用）**：engine.network.frame `FrameV1`/`FrameIterator`/`Crc32Ieee`/`FrameConst`/`ZdFrameBridge`；engine.network.integrity `tsha1f`/`FrameV2`/`FrameV2Result`（含 `streamSplit`/`streamJoin`）；engine.network.crypto `SecureChannel`/`EncryptionConfig`；engine.network.bandwidth `BandwidthOptimizer`/`BandwidthTechnique`；engine.network.strategy `StrategySelector`/`PayloadLevel`/`IncrementalDiff`/`ChangeStream`。
- **ez 载荷触点（p.2.3 已落地，直接复用）**：`engine.zd` `ZdHeader`/`ZdPrimitives`/`ZdRow`/`ZdDocWriter`/`ZdVolume` + `engine.save.migrate.ZdtTransfer`；tie-main 侧参考 `std/tink_v2.tie`（F:\Projects\tie-repo\tie-main）。
- 参考既有接线范式：engine.datapack / engine.save + runtime.datapack（`DatapackRuntime`）/ runtime.save（`SaveRuntime`）/ runtime.network（`EnhancedChannelRuntime`）+ 确定性钩子（build.gradle server 段 subterra.override / subterra.probe.network / subterra.probe.save）。
- 探针资产：`subterra-devkit\src\main\java\io\toterra\subterra\probes\`（Network*Probe 六探针已接入）；新 P2P 探针落同处；probeAcceptance 接线：`subterra-devkit\build.gradle`（E2E 需 mustRunAfter 既有开服门；staging 用 build/tmp）。
- 关联里程碑：p.2.4 同栈（已落地）；p.2.11 tink hub 形态 B（会话可编程，外部进程经 tink 帧驱动）。

## 7. 输出与汇报
- 主代理与各子代理均：双语说明（做了什么 + 结论）后立即提交；收尾子代理报 probeAcceptance 全量结果（含 E2E markers 数与四次开服）+ ROAD p.2.5 landed + 新提示词文件路径 + push 结果；最终给用户汇报完整子项清单与验收证据。