# Trimand —— 微型扩散三定调器 / Micro Diffusion Tri-Tone Tuner

日期 / Date: 2026-09-15 · 归属 / Repo: **Subterra**（`tie-src/trimand/`）· 模型名 / Name: **Trimand**

> Trimand：tri-（三个定调面）+ mand（maestro，指挥宏观地形走向）。本模型为 Subterra 地形生成
> 提供三个宏观定调场——**气候（climate）/ 海拔（elev）/ 山带（mtn）**；海岸线/水文不定调，
> 由「气候 + 海拔 + 山带」组合派生（Java 侧融合）。

## 目标 / Goal

用 tie 编写一个小型扩散模型，引导 Subterra 地形生成，保障宏观地形的**整体性**（大陆连片、
海岸线连贯、山带成系、海陆比例自然、气候分区连续）；Subterra 的微观地形与洞穴仍由噪声算法
生成，保证速度与性能。**水文（含水层/河流/湿度）不作为模型输出**——由 climate/elev/mtn
三场在消费方组合计算而来。

## 架构 / Architecture

```
离线训练（tie-src/trimand, tiec --lib-root tlib）        运行时（DLL 自包含）
───────────────────────────────────                    ─────────────────────────────
macrogen 合成三定调先验数据集                          ├─ tile 缓存（全局表）
  ├ 条件草图 c_cont/c_mtn（低频噪声，2ch）              ├─ sample(): 确定性 DDPM 反演
  └ 理想化目标 climate/elev/mtn（3ch）                   │   （seed 派生 Rng + strat=false）
diffunet 微型 UNet（5→4→4→8→4→3, 16×16）               ├─ forward(): 自包含 conv/convT/GN
  ├ forward（与运行时 copy 逐位一致校验）                └─ 导出 coarse_climate/coarse_elev/
  └ backward（训练）                                        coarse_mountain(x,z,seed)
trainer: add_noise → UNet → MSE → backward → adamw          → 查缓存 + 双线性 + 边缘淡化
weights: 训练后经 gen 输出 `*.gen.tie` 字面量        融合（Java 侧）：
                                                    水文 = f(climate, elev, mtn)
                                                    surface = bed + landMask·(mtn·15 + hills·5)
```

## 组件 / Components（归属 tie-src/trimand/）

* **macrogen.tie（namespace mg）**：三定调目标生成。条件草图 2 通道（大陆/山带低频 fbm，
  与 Subterra 核心逐位同源）；理想化目标 3 通道——elev（海岸幂律锐化 + 陆上 ridged）、
  mtn（双高斯山带 × softland × ridged）、**climate（低频湿润基调受海拔/山带/离海调制）**；
  只保留最大 4-连通大陆（elev/mtn 清零，climate 跨海陆连续）。
* **diffunet.tie（namespace du）**：微型 UNet。输入 5ch（2 条件 + 3 加噪目标）×16×16=1280
  （容量档位 1280），conv1 5→4、conv2 4→4、convb 4→8、convT 8→4、conv_out 4→3；总参数
  **NPAR=1083**（单扁平 array<f32,1280> 布局）。forward+backward 成对（调 tlib `<CAP>` 库）。
* **trainer.tie（namespace tr）+ train_full.tie**：DDPM 条件训练（predict-x0，3 通道损失，
  NCH=768）；`train_full` 4000 步产出 `weights/_du_weights_full.bin`（best checkpoint）。
* **runtime_forward.tie（namespace rfwd）**：自包含运行时（零 import）——噪声族、草图条件、
  UNet 前向、seed_gauss、`sample_tile` 确定性三场反演（输出 768 = 3×256）。
* **gen/gen-subterra-runtime.tie + subterra_diffuse_runtime.tmpl.tie**：权重/调度字面量注入
  生成自包含运行时段 `gen/subterra_diffuse_runtime.gen.tie`（生成器把模板与 runtime_forward
  合并为**单一 `rfwd` 命名空间**）——tile 缓存、`coarse_climate/coarse_elev/coarse_mountain(x,z,seed)`
  导出（FFM 标量 ABI；命名空间内其余函数一律非 pub，不进入导出面）。
* **探针（验收）**：macrogen_probe（22 断言）/ diffunet_probe（数值梯度 9 断言）/
  trainer_probe（9 断言）/ sampler_probe（确定性 + 整体性 + 跟踪性 12 断言），全绿。

## 关键数字 / Key Numbers

* tile=512×512，网格 16×16 单元=32 方块；三目标通道 → 输入 5ch×256=1280。
* UNet 参数 1083；单 tile 采样 10 步；缓存 9 槽 × 769（`DG_REC`），≈150KB。
* 权重 1083 个 f32 字面量（.gen.tie 约 46KB）直接编进 DLL，零文件加载。

## tie 能力（本模型受益的语言特性）

* **值参数泛型 `<CAP: i64 = 1024>`**（tie v2）：训练栈库（tlib/ext nn/loss/optim/serde/
  diffusion）签名容量无关，默认 1024 档兼容既有；Trimand 全链显式 `<1280>` 实例化。
* **数组长度常量名** `array<f32, CAP_1280>`：容量档位族治理；内存安全护栏 MAX_ARRAY_LEN=2^18。

## 融合 / Fusion（Subterra 侧）

tie 侧导出符号为 `rfwd$coarse_*`（tiec `--shared` 的命名空间修饰）；Java 侧经 FFM 下行调用：

```
cli  = rfwd.coarse_climate(x,z,seed)   ∈ [-1,1]
elev = rfwd.coarse_elev(x,z,seed)      ∈ [-1,1]
mtn  = rfwd.coarse_mountain(x,z,seed)  ∈ [-1,1]
bed      = sea + elev·24
landMask = clamp(0.6+elev, 0, 1)
水文（hydrology）= f(cli, elev, mtn)      // 组合派生，非模型输出
surface  = bed + 3 + landMask·(mtn·15 + hills_noise·5)
```

## 验收 / Verification

* 训练 loss 单调下降（4000 步 best ≈ 0.0075）；采样确定性（同种子逐位一致）；
  自包含 rfwd.forward 与 std/nn du.forward 逐位/近一致。
* 整体性探针：海陆比例、最大连通大陆、海岸线、山带-海岸相关、elev 符号跟踪率（≥0.53）、
  sampled↔target 相关（≥0.4）。
* 三场值域 [-1,1]；同种子同值；混合 tile 覆盖海/陆/边界。

## 编译链 / Build chain

权威编译器 / Authoritative compiler：`F:\Projects\tie-repo\tiec\compiler\tiec.exe`
（tie 自举编译器的入库 stage0 引导二进制；SHA-256
`27982cea381814a3d63d83b3389aaadb4dd96fc8d547d9815d61ca8a7219a25a`）。判定依据：`tie-repo/tiec`
是 tiec 的**现行开发仓**（编译器全源码 frontend/middle/backend + 回归脚本，近期提交均为
`feat(diag)` 诊断系列），而 `tie-main/compiler/tiec_latest.exe` 等为聚合仓的历史快照副本，版本落后。
内置库根 / Library root：`F:\Projects\tlib`（`std/` `ext/` `rdu/` `sys/`）。

以下命令一律在 `tie-src/trimand/` 下**前台**执行：

```bat
set TIEC=F:\Projects\tie-repo\tiec\compiler\tiec.exe
set LIB=F:\Projects\tlib

:: 生成自包含运行时段：先编译生成器，再执行生成器（tiec 编译不自动运行）
%TIEC% --no-cache --lib-root %LIB% gen\gen-subterra-runtime.tie
gen\gen-subterra-runtime.exe

:: 编译运行时段动态库（标量 ABI 导出）
%TIEC% --no-cache --shared --lib-root %LIB% gen\subterra_diffuse_runtime.gen.tie -o gen\subterra_diffuse.dll

:: 四组验收探针（编译后逐个执行）
%TIEC% --no-cache --lib-root %LIB% probe\macrogen_probe.tie -o probe\macrogen_probe.exe
%TIEC% --no-cache --lib-root %LIB% probe\diffunet_probe.tie  -o probe\diffunet_probe.exe
%TIEC% --no-cache --lib-root %LIB% probe\trainer_probe.tie   -o probe\trainer_probe.exe
%TIEC% --no-cache --lib-root %LIB% probe\sampler_probe.tie   -o probe\sampler_probe.exe
```

* 生成器须「编译 + 执行」两步：tiec 只产出 `.exe`，权重/调度字面量注入由该 `.exe` 运行时完成。
* 本产物权重以源码字面量注入（1083 项 f32，零文件加载），无运行期数据交换，故不使用
  `--compress-data` / zd 载体。
* 探针有意使用浮点等值与整数截断语义，编译期诊断 `W00001`/`W00002` 为预期，非缺陷。

## 产物与符号 / Artifacts & symbols

* 运行时段生成源：`gen/subterra_diffuse_runtime.gen.tie`，1727 行，SHA-256
  `10a1fa8f49246e3e220b51fac11cd2e48d243ccd90079a68ea1286c3dd44e79f`。
* 运行时段动态库：`gen/subterra_diffuse.dll`，220160 字节，SHA-256
  `44b6b29efc49eb9028ac0191d1db66d05a95481ef9a638a72e740db258adbbbc`。
* 导出符号恰为 3 个标量函数（命名空间 `rfwd`，ABI 一律 `(i64, i64, i64) -> f64`）：
  * `rfwd$coarse_climate` —— 气候定调场（湿润正 / 干旱负）
  * `rfwd$coarse_elev` —— 海拔定调场（>0 陆地 / <0 海洋）
  * `rfwd$coarse_mountain` —— 山带定调场（[0,1] 山带强度 ×2−1）
* 命名空间内辅助函数（`cell_field` / `coarse_field` / `forward` / `sample_tile` / `hash3` 等）
  一律不导出；导出面必为 3 个，由生成器的「精确函数名」判定保证。
* **打包资源同源复核（2026-09-16，主理人执行）**：以权威 tiec 独立重建（生成器 → `.gen.tie`
  逐位复现 → `--shared` 重编）并与 `gen/subterra_diffuse.dll` 段级比对——`.text/.data/.pdata/
  .fptable/.reloc/.rdata` 六段全部逐字节一致，导出面均为 3 个。**发现 Java 侧捆绑资源
  （`subterra-tie/src/main/resources/tie/subterra_diffuse.dll`）为旧代产物：导出面 5 个**
  （多出 `rfwd$cell_field` / `rfwd$coarse_field`，早于「精确函数名」导出面修复），已用
  `gen/subterra_diffuse.dll`（SHA-256 `44b6b29e…`）替换；替换后 `TrimandSeamProbe`（FFM
  ok/skip，40 checks）与开服门全绿。文件级 SHA-256 因 PE 时间戳不同而异属预期，功能同源
  以段级哈希与导出面为准。
* 重建等价性：本次重建产物与 Java 侧捆绑资源 DLL 的 `.text / .data / .pdata / .fptable / .reloc`
  五段逐字节一致，仅 PE 时间戳与 `.rdata`（导出表 / 元数据）不同——即可执行代码逐字节同源，
  三符号 FFM 调用输出逐位一致。

## 装载 / Loading（FFM）

* JVM 需 `--enable-native-access=ALL-UNNAMED`（JDK 22+ FFM 下行调用）；ModDevGradle dev run 默认注入。
* 端到端冒烟（纯 JDK，不依赖引擎 / MC）：

```bat
java --enable-native-access=ALL-UNNAMED -cp gen TrimandSmoke
```

* 冒烟断言：三符号可寻址；`(100,200,seed)` 与 `(5120,8192,seed)` 调用确定性（同参同值）、
  值域 ∈ [−1.5, 1.5]；全部通过后输出 `TRIMAND FFM SMOKE PASS`。

## 缺库回退 / Missing-library fallback

* Java 侧定位序固定：系统属性 `subterra.trimand.lib` → 类路径资源 `/tie/subterra_diffuse.dll`。
* 两者皆缺 → `TrimandBridge.locate()` 返回空，调用方按「无 tie 库」**确定性 skip**，走同语义的
  Java 回退实现；不抛异常、不产生静默错误值。
* 两路一致性：同 `(x, z, seed)` 下 tie 路径与 Java 回退路径逐位一致（由 devkit 侧探针断言，
  归属 test-acceptance-engineer）。

## 验收结论 / Verification result

* 四组探针全绿：macrogen 22 / diffunet 9 / trainer 9 / sampler 12（合计 52 断言）。
* 未覆盖项：仓库内无「运行时段 DLL 导出面」的 tie 侧断言探针——导出面由生成器逻辑保证；
  运行期行为由 Java FFM 冒烟（`TrimandSmoke`）与 `TrimandBridge` 探针覆盖。
* 后续：`tie-src/trimand` 产物同步到 `subterra-tie` 捆绑资源 DLL（跨模块写入，由 engine-engineer
  执行）；POI / 低精度 LOD 复用 coarse 查询符号。

## 变更记录 / Change log（tie-src/trimand）

* `gen/gen-subterra-runtime.tie` —— 导出面判定由「前缀 `func c…`」改为「精确函数名」。
  根因（RCA）：旧前缀判定过宽，连带把内部 `cell_field` / `coarse_field` 也标为 pub，
  使 DLL 导出面从契约的 3 个膨胀到 5 个非契约符号。修法：仅
  `coarse_climate` / `coarse_elev` / `coarse_mountain` 保留导出，其余一律非 pub。
  效果：导出面回到 3 个契约符号，重建 DLL 的 `.text` 与原产物逐字节一致（运行行为不变，
  三符号 FFM 输出逐位一致）。