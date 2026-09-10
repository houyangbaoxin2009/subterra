# samples/overturn-minimal — 官方示例模组：最小「颠覆性模组」/ Official Sample Mod: Minimal "Overturn" Mod

## 定位 / Purpose

p.2.20 官方示例模组骨架：展示「颠覆原版一小面」的最小框架消费方——用框架纯 JDK 能力
（api 契约 + engine 核心）写一段「颠覆性」逻辑。本骨架只立结构与清单式最小形态；语义能力
（把规则接入实际世界生成路径）随 p.2.20.4 兑现。

p.2.20 official sample skeleton: the minimal framework consumer that "overturns one small facet
of vanilla" — a piece of "overturn" logic written purely against the framework's pure-JDK
surface (api contract + engine core). This skeleton only establishes the structure and a minimal
checklist shape; the semantics (wiring the rules into the real worldgen path) land with p.2.20.4.

## 颠覆目标 / What it overturns

同一世界种子下，原版世界生成按固定密度场产出地形。本示例用 td 规则系统改写**世界生成密度偏移
（global terrain density offset）**：`overturn.worldgen.density_offset` 把地形密度整体上移/下沉
一个固定量，`overturn.worldgen.structures` 可关闭原版结构生成——全部经由框架
`engine.config.rules.RuleStore`（双层规则配置）驱动，确定性、固定序、逐字节一致。

Under a fixed world seed, vanilla worldgen produces terrain from a fixed density field. This
sample rewrites the **global terrain density offset** through the td rule system:
`overturn.worldgen.density_offset` shifts the terrain density up/down by a fixed amount, and
`overturn.worldgen.structures` can turn vanilla structure generation off — all driven by the
framework's `engine.config.rules.RuleStore` (two-tier rule config), deterministic, fixed-order,
byte-for-byte consistent.

## 结构 / Layout

```
samples/overturn-minimal/
├── README.md
├── build.gradle
└── src/main/
    ├── java/io/toterra/sample/overturn/OverturnMinimal.java   # 入口（纯 JDK）/ entry (pure JDK)
    └── resources/
        ├── META-INF/neoforge.mods.toml                        # 装配元数据声明 / assembly metadata declaration
        └── data/overturn_minimal/pack.td                      # 最小 td 规则包 / minimal td rules pack
```

* 纯 Java 库形态：不引 MC、不引 NeoForge userdev 插件；MC 装配在根工程（root moddev 项目）。
  Pure Java library shape: no MC, no NeoForge userdev plugin; MC assembly lives in the root
  moddev project.
* `neoforge.mods.toml` 在该模块内不被 FML 消费，作为框架装配元数据的声明形态（entrypoint 指
  OverturnMinimal），README 与文件内注释均有说明。
  The `neoforge.mods.toml` is not consumed by FML here; it declares the framework assembly
  metadata (entrypoint -> OverturnMinimal), as documented in this README and the in-file comments.

## 构建与运行 / Build & run

```sh
gradlew :samples:overturn-minimal:compileJava        # 编译（Java 25 工具链）/ compile (Java 25 toolchain)
gradlew :samples:overturn-minimal:processResources   # td / mods.toml 资源拷贝 / resource copy
```

运行入口（纯 JDK，无需游戏启动；Windows 类路径分隔符为 `;`）：

```sh
gradlew :samples:overturn-minimal:classes
java -cp samples/overturn-minimal/build/classes/java/main;samples/overturn-minimal/build/resources/main;subterra-api/build/classes/java/main;subterra-engine/build/classes/java/main io.toterra.sample.overturn.OverturnMinimal
```

输出为确定性自检行（规则数 + 校验报告 + 规范渲染）。The output is a deterministic
self-check line (rule count + validation report + canonical render).

## 确定性纪律 / Determinism discipline

固定注册序、禁时序、禁随机、同输入同输出、禁 O(n²)；一切遵循框架确定性范式。
Fixed registration order, no timing, no randomness, same input → same output, no O(n²);
everything follows the framework determinism paradigm.
