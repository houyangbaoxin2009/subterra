# p.2.36 设计案：世界生成器重构——九维度混合 × 三场扩散定调 × 全面可配置

# p.2.36 Design: Worldgen Refactor — Nine-Dimension Hybrid × Three-Field Diffusion Toning × Fully Configurable

> 状态：**已对齐（2026-09-17 用户定案）**，按 §5 实现序落码。宏观架构不变：api ← engine ← runtime
> 铁律、密度缝（`subterra:density` → `SubterraTerrain.finalDensity`）、tie FFM 桥、装配规则缝、
> 探针门禁全部保留。
>
> Status: **aligned (user decisions 2026-09-17)**, implementing in §5 order. The macro
> architecture is unchanged: the api ← engine ← runtime iron law, the density seam
> (`subterra:density` → `SubterraTerrain.finalDensity`), the tie FFM bridge, the assembly-rule seam
> and the probe gates all stay.

---

## 0. 用户定案（2026-09-17，覆盖 §6 原对齐点）

## 0. User Decisions (2026-09-17, supersede the former §6 alignment points)

1. **九维关系不固定，可修改**：混合关系图不再是固定 DAG——维与维之间的关系/边权全部可配置、
   可改写（含域模组改写）。
2. **九维决定世界上基本所有元素**：地形、地表、水文、植被、矿物、群系、结构、生物、遗迹……
   全部从九维场派生，不存在游离于九维之外的生成决策。
3. **模型只定调**：扩散模型（Trimand）只对九维中**最需要空间连续性的三个维**
   （terrain-elev / climate / mtn——宏观低频场）定调；**具体生成交给噪声等传统算法**
   （性能纪律：模型输出是低频 tile 缓存先验，高频细节不进模型）。
4. **窗口直接替换默认**：`[-128, 592) / sea 127` 成为默认窗口（p.2.36.1 落地），
   且**世界生成时窗口可调节**（配置/预设面注入，见 §4）。
5. **配置入口先用 td**（config UI 未做，落地后 Hub 表单接入）。
6. **双接口硬性要求（全模块理念）**：Subterra 任何功能/模块必须同时提供 **td 与 api 两套接口**；
   可选出入口/出口：**指令（commands）、zd** 等。本设计案（含地形生成器）按此纪律执行，
   后续所有模块沿用。

---

## 1. 现状锚定（已核对源码的事实）

## 1. Ground Truth (verified against source)

* **九维模型**：`api.worldgen.EcoDim.ALL` 固定九维序 `terrain / climate / vegetation / hydro /
  surface / litho / mineral / fauna / relic`（架构 §5.2），支持 `extendWith` 扩展。目前九维
  只用于生物圈数据面（EcoProfile/EcoResolver），**未接入地形管线**。
* **三场扩散定调**：Trimand（p.2.35）产出 `coarse_climate / coarse_elev / coarse_mountain(x,z,seed)`
  tile 缓存（FFM 标量 ABI）；Java 侧 `SubterraTrimand.apply` 在密度缝上是**逐位直通**
  （identity 快照时），即三场目前是「提示」而非「驱动」。
* **地形模型**：p.1.8.34 `SubterraTerrain` 是纯 `(x,y,z)` 密度函数（大陆/丘陵/山脊/三维细节
  四层噪声栈，海平面 127 常量）——**天然窗口无关**。
* **窗口**：现行 [-64, 320)（min_y=-64 / height=384，vanilla 域型），`sea_level=127`
  （JSON ↔ `Kind.OVERWORLD_FINAL(-64, 384, 127.0)` ↔ `TerrainAxisProbe` 三方钉死）。
  p.1.8.33 已完成海平面参数化；td-plus 时代 [-128, 592)+海128 的淹没回归由窗口/锚点错位导致。
* **配置面**：仅 `config/subterra/worldgen.td` 的总开关（`GeneratorOption`）。

---

## 2. 设计总纲：三级混合粒度（既定设计取向的落地）

## 2. Design Skeleton: Three-Level Hybrid Granularity (landing the established design stance)

沿用既定取向「复杂状态用统一接口 + 三级混合粒度」：

Following the established stance "unified interface for complex state + three-level hybrid
granularity":

### 2.1 能力级（capability level）——九维开关
### 2.1 Capability level — the nine-dimension switches

* 九维各自一个能力位：`enable / disable / redirect(vanilla)`。禁用维回退 vanilla 对应机制
  （零行为变化）；redirect 允许把该维指到自有实现或域模组实现。
* 开关在服务器启动时一次性捕获为快照（同 `setAssembly` 纪律），确定性、可热重载。

### 2.2 变量维级（variable-dimension level）——每维参数向量
### 2.2 Variable-dimension level — per-dimension parameter vectors

* 每维暴露参数向量：权重 / 波长 / 幅度 / 带位（band）。全部进配置面（§4），带确定性校验
  （非法值确定性拒绝，IAE/NPE 契约同 `SkyApi`）。

### 2.3 过渡级（transition level）——维间过渡带
### 2.3 Transition level — inter-dimension transition bands

* 维与维之间的过渡（海陆过渡、山地缓入 `mask^2`、群系权接）抽为可配置过渡函数：
  `{width, curve, order}`。曲线族先只提供 clamp-linear 与 smoothstep 两种（够用，不预设扩展）。

---

## 3. 三场定调驱动 + 九维混合（混合 DAG 固定，边权可配置）

## 3. Three-Field Toning + Nine-Dimension Hybrid (fixed DAG, configurable edge weights)

### 3.1 三场 → 层的映射（默认 = 现行为逐位一致）
### 3.1 Field→layer mapping (default = current behaviour, bitwise)

| 场 | 驱动层 | 默认四元组 {gain, bias, band, blend} |
|---|---|---|
| `coarse_elev` | `surface` 基线（融合大陆项 c） | identity 等价（gain=0 旁路，回退自有大陆噪声） |
| `coarse_mountain` | 山脊项（掩码×幅度） | identity 等价（同上） |
| `coarse_climate` | 湿润度 → 下游派生水文/植被/群系权重（**不改地形形状**） | identity 等价 |

* 设计不变式：**Trimand 快照为 identity 时，密度输出与 p.1.8.34 逐位一致**（同 `SubterraTrimand`
  现契约）；三场接入是增量调制，不重写噪声栈。
* tie 桥纪律不变：DLL 陈旧即默认 skip（`-Dsubterra.density.tie` 显式开启），语义变更后 DLL
  必须重导出并过等价探针。

### 3.2 九维混合关系图（可修改，非固定 DAG）
### 3.2 The nine-dimension relation graph (modifiable, NOT a fixed DAG)

* **用户定案**：维与维之间的关系（依赖边、权重、混合函数选择）**全部可配置、可改写**——
  引擎只提供一条**缺省关系链**作为起点（默认值只是起点）：
  `terrain ← (elev, mtn)`；`hydro ← (climate, terrain)`；`vegetation / surface ←
  (climate, terrain, hydro)`；`litho / mineral ← (terrain, surface)`；
  `fauna / relic ← (climate, surface)`。
* 关系图进配置面（§4）：每条边 `{from, to, weight, blend}`，可增删改；域模组经 api 层改写。
* 引擎侧在图加载后做**确定性拓扑排序**（环检测确定性报错），求值序由排序结果决定。
* 每维输出一个 0..1 标量场，走 Trimand stdens 同款 tile 缓存路径（性能与现有三场一致）。

### 3.3 结构生成消费九维（用户定案补充：声明式结构档案）
### 3.3 Structures Consume the Nine Dimensions (user decision: declarative structure profiles)

* 结构（村庄/矿井/域模组结构等）是九维的**一等消费方**。开发者以**声明式结构档案**
  （declarative structure profile）描述一个结构，引擎负责放置与变体解析——**大幅简化新结构
  开发**（不再手写结构集/放置 JSON 与战利品接线）。档案四组项：
  1. **所需维度关系**（allowed dimension relations）：结构**只能生成在哪几个维度的关系上**——
     即声明的九维值域/关系谓词集合（如村庄 `terrain ∈ [a,b] ∧ hydro ∈ [c,d]`）；
  2. **按维度状态的战利品表集**（loot-table set per dimension state）：**生成在哪个维度（状态）
     时，用哪套战利品表集**——按谓词分支声明，如寒带/热带/深域各一套；
  3. **变体选择**（variant selection）：**会使用哪个变体**——由落点维度的九维状态确定性解析
     （同一结构多套模板，按状态选变体；解析规则可配置，缺省为谓词表首匹配）；
  4. **生成密度与上限**（density & cap）：spacing / separation / 概率 + 按结构类型的单世界
     总量上限（确定性计数，网格序消耗配额），防过生成。
* 四组项遵守双接口纪律：td 配置 + api `WorldgenProfile` 同面读写；指令出口提供结构放置
  查询（只读）。
* 开发者最小声明 = 一个结构 id + 一条维度关系谓词 + 一套默认战利品表 + 一个变体；其余全部
  走缺省值（默认值只是起点）。

### 3.4 模型定调范围（性能纪律）
### 3.4 Model Toning Scope (performance discipline)

* 扩散模型**只**驱动三个最需空间连续性的维：`terrain-elev`（地表基线）、`climate`（湿润基调）、
  `mtn`（山带）——三者都是宏观低频场，tile 缓存（stdens 路径）天然适配。
* 其余六维及其高频细节全部由**噪声等传统算法**生成；模型绝不承担逐块生成。

### 3.5 洞穴群系（类型）由九维组合派生（用户定案，2026-09-28）

### 3.5 Cave Biomes (Types) Derived from Dimension Combinations (user decision, 2026-09-28)

* **定案**：洞穴不是单一几何体，而是**群系**——**不同维度的综合产生不同的洞穴群系（类型）**。
  洞穴类型与地表群系同构：都是九维场在落点处的取值组合所决定的产物，不存在游离于九维之外的
  洞穴决策。
* **洞穴群系档案**（declarative cave profile，与 §3.3 结构档案同构、同双接口纪律）五组项：
  1. **所需维度关系**（dimension predicate）：该群系**只能生成在哪些九维取值组合上**
     （如 `litho ∈ 碳酸盐 ∧ hydro ∈ 高` → 岩溶洞；`litho ∈ 火成 ∧ terrain ∈ 深` → 熔岩管）；
  2. **洞道形态参数**：管径 / 粗糙度 / 竖向性 / 分支密度 /  chamber 尺度——同一群系内形态参数
     固定，不同群系不同（这是"类型"最直观的差异面）；
  3. **内衬与流体**：壁面材质 palette、填充、流体类型与水位（含水层规则随群系而变，
     不再有全局单一含水层）；
  4. **特征与富集**：钟乳石 / 矿脉富集 / 生物巢穴 / 战利品表集——按群系声明；
  5. **过渡带**：群系与群系之间的过渡（复用 §2.3 过渡级 `{width, curve, order}`），
     禁止硬边界跳变。
* **解析确定性**：同一 `(x,y,z)` 的九维状态 → **唯一洞穴群系**（谓词表定序首匹配，序可配置）；
  同输入同字节，禁墙钟与随机漂移。
* **与 .6 子项的关系**：p.2.36.6 交付的不是一个"洞穴噪声函数"，而是
  **「九维场 → 群系档案 → 形态/内衬/流体/富集」的整条派生链**；vanilla `CaveFamilyFn`
  只作金标对照（形状机制比对），不作目标（§5.6 原则重申：不追求还原原版）。

---

## 4. 全面可配置：双接口三层配置面（默认值只是起点）

## 4. Fully Configurable: Dual-Interface, Three Configuration Layers (defaults are only starting points)

**双接口纪律（用户定案，全模块适用）**：任何功能/模块必须同时提供 **td 与 api 两套接口**；
可选出入口/出口：**指令（commands）、zd** 等。本设计案的每个子项均按此交付。

**Dual-interface discipline (user decision, all modules)**: every feature/module must expose
**both a td and an api interface**; optional inlets/outlets: **commands, zd**, etc. Every
sub-item of this design ships under this discipline.

| 层 | 载体 | 内容 |
|---|---|---|
| 配置层（td + api） | `config/subterra/worldgen.td`（热重载）+ `api.worldgen.WorldgenProfile` | 窗口 `[min_y=-128, height=720]`、`sea_level=127`、九维能力位、维间关系图（可增删改）、每维参数向量、三场四元组、过渡带参数、结构三组项（§3.3） |
| 数据包层（zd/JSON） | `worldgen/noise_settings` + 装配规则 + 结构集（现状保留，键位对齐配置层） | surface rules 带、装配规则、结构 spacing/separation |
| 指令层（可选出口） | commands（如 worldgen 查询/重载/窗口预览） | 运行时检查面，只读为主 |

* **窗口直接替换默认且世界生成时可调节**（用户定案）：`min_y=-128 / height=720 /
  sea_level=127` 成为**默认值**（p.2.36.1 落地）；世界生成时的调节经配置面 → 数据包注入点
  （`DatapackRegistryInjector` 同路径）在世界加载前改写 `noise.noise` 与 dimension_type，
  **自定义 dimension_type `subterra:overworld` 已随附**（p.2.36.1），注入只改数值不改键。
* surface rules 的 y 带（deepslate/bedrock）与 features 的 y clamp 随窗口参数推导，不再硬编码
  vanilla 带。
* `SubterraTerrain.SEA_LEVEL` 从常量改为快照参数（默认 127.0 逐位不变）。

---

## 5. 实现序（已对齐，不做 forward optional，只分实现序）

## 5. Implementation Order (aligned; no forward-optional, ordering only)

1. **p.2.36.1 窗口重参数化**：Kind/JSON/dimension_type/TerrainAxisProbe → 默认 [-128, 720)/127；
   深域（-128..-64）与高域（320..592) 的 surface 带与特征带随窗口推导。
2. **p.2.36.2 配置面（td + api 双接口）**：`WorldgenProfile` td 契约 + 加载/校验/快照 + 探针
   （含窗口一致性门）；**世界生成时窗口调节**走数据包注入点。
3. **p.2.36.3 三场定调驱动**：identity 直通 → 参数化调制（默认逐位一致）；TerrainAxisProbe 扩展
   调制断言；DLL 重导出 + 等价探针。
4. **p.2.36.4 九维关系图 + 场管线**：可修改关系图（拓扑排序/环检测）+ EcoDim 场管线
   （tile 缓存）+ 探针。
5. **p.2.36.5 结构九维消费**：所需维度关系谓词 + 生成密度 + 每世界上限（td + api 双接口 +
   指令查询出口）。
6. **p.2.36.6 自有洞穴群系与含水层系统**（原 p.1.8.34 遗留 `CaveFamilyFn` 并入本线，编号自
   .6 起；**原则重申：不追求还原原版**）：交付**「九维场 → 洞穴群系档案 → 形态/内衬/流体/富集」**
   整条派生链（档案五组项与解析确定性见 §3.5）；含水层不再是全局单一水位，而是**随洞穴群系
   而变**的流体规则；vanilla 洞穴形状机制只作金标对照、不作目标。
7. **p.2.36.7 验收**（原 .6 顺延）：全探针 + 实机回归（双向 x/z 山海、深域、高山、无淹没、
   结构配额、洞穴贯通性）。

* 排期裁定（2026-09-28 用户定案）：**p.2.36 单线推进**（.2 → .3 → .4 → .5 → .6 → .7），
  Subterra 本轮不并行 p.2.37 SubUI；**密度 DLL 重导出随 .3 一并做**（不再单列、不再后挂）；
  **洞穴族并入 p.2.36 为 .6**，不另立 p.2.38 线。
* 阻塞解除记录：`.3` 的 DLL 重导出原阻于 tiec `--shared` 缺失运行时 arena 初始化
  （2026-09-17 移交 tie 轨道），tie 侧已于 2026-09-18 修复（tiec `9f50709`
  `fix(shared): emit tie$rt_init module ctor for DLL global table init`，双轨：`@llvm.global_ctors`
  装载即跑 + 宿主显式 downcall 兼容）— 详见 `docs/tie-track/tiec-shared-arena-init-issue.md`
  结案段。

---

## 6. 原对齐点（已被 §0 用户定案覆盖，留档）

## 6. Former Alignment Points (superseded by §0, archived)

1. ~~窗口默认~~ → 定案：直接替换默认（§0.4）。
2. ~~三场默认语义~~ → 定案：模型只定调三个连续性维，传统算法生成细节（§0.3）；
   默认快照 identity 时逐位一致不变。
3. ~~九维接管范围~~ → 定案：九维决定基本所有元素，含结构（§0.2、§3.3）。
4. ~~配置入口~~ → 定案：先 td，UI 后接；双接口纪律全模块适用（§0.5、§0.6）。

---

## 7. 风险与守门

## 7. Risks and Gates

* **淹没回归（2026-09-16 教训）**：任何窗口/海平面变更必须「JSON ↔ Kind ↔ dimension_type ↔
  探针」四方一致；TerrainAxisProbe 的窗口一致性断言同步改钉新默认。
* **金标**：VanillaGolden（海 63 vanilla 路径 delta=0）不动——它钉 vanilla 形状机制，本重构钉
  新设计语义，两层互补。
* **性能**：tile 缓存路径复用 Trimand stdens 机制；探针里加内存/求值次数断言（同 p.2.35 纪律）。
* **确定性**：全部快照启动捕获、无墙钟、无随机漂移；禁时序断言由探针守护。
