# samples/overturn-minimal — 官方示例模组：最小「颠覆性模组」/ Official Sample Mod: Minimal "Overturn" Mod

## 定位 / Purpose

p.2.20 官方示例模组：展示「颠覆原版一小面」的最小框架消费方——用框架纯 JDK 能力
（api 契约 + engine 核心）写一段「颠覆性」逻辑。p.2.20.3 立结构（两条类型化规则规格 +
td 规则包 + 双层解析/校验/冻结 + 规范渲染）；p.2.20.4 兑现能力面演示：规则双层 / td 数据包
直载 / export 恒等 / tie skip-or-ok，全部确定性。把规则接入实际世界生成路径（如密度偏移）的
MC 装配面由根工程（root moddev 项目）提供。

p.2.20 official sample mod: the minimal framework consumer that "overturns one small facet of
vanilla" — a piece of "overturn" logic written purely against the framework's pure-JDK surface
(api contract + engine core). p.2.20.3 established the structure (two typed rule specs + a td
rules pack + two-tier resolve/validate/freeze + canonical render); p.2.20.4 lands the
capability-surface demo: two-tier rules / direct td datapack load / export identity /
tie skip-or-ok, all deterministic. Wiring the rules into the real worldgen path (e.g. the density
offset) is the MC-assembly surface provided by the root project.

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

## 能力面清单 / Capability surfaces

p.2.20.4 起，示例入口逐一显式调用框架纯 JDK 能力面，并打印确定性报告行（同输入同字节、
禁时序、禁随机）：

Since p.2.20.4 the sample entry explicitly exercises each framework pure-JDK capability surface
and prints one deterministic report line per surface (same input → same bytes, no timing, no
randomness):

| 能力面 / Surface | 用法 / Usage | 报告行 / Report line |
| --- | --- | --- |
| 规则双层 two-tier rules | `RuleStore`：pack.td 全局档 2 条 + 覆盖档（density_offset=3.5）→ `resolve` 覆盖档胜出 | `rules two-tier ok (...)` |
| td 数据包直载 direct td datapack load | `DatapackLoader.load`：classpath 资源 pack.td 暂存为数据包目录直载，manifest 规则直出 | `td datapack ok (...)` |
| export 恒等 export identity | `ConfigExporter`：有效双层配置导出 td + zd，断言 export∘rehydrate∘export 逐字节恒等 | `export ok (...)` |
| tie 装载 tie load | `engine.tie.TieLibrary` 尝试装载（固定序：`subterra.tie.lib` 属性 → 捆绑资源 → 缺省 skip）；无 dll → 确定性 skip，有 → ok，不抛异常 | `tie skip (no lib)` / `tie ok` |
| runtime 壳消费面 runtime shell consumption | 由根工程装配提供（runtime 为根工程源码宿主目录，非 Gradle 子项目）——示例编译期不 import，仅文档化消费面 | —（根工程装配） |

## 结构 / Layout

```
samples/overturn-minimal/
├── README.md
├── build.gradle
└── src/main/
    ├── java/io/toterra/sample/overturn/OverturnMinimal.java   # 入口（纯 JDK）/ entry (pure JDK)
    └── resources/
        ├── META-INF/neoforge.mods.toml                        # 装配元数据声明 / assembly metadata declaration
        └── data/overturn_minimal/pack.td                      # 最小 td 规则包（数据包 manifest 直载源）/
                                                               # minimal td rules pack (direct datapack manifest source)
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

输出为确定性能力面报告行（规则自检 + 四个能力面 + PASS 行）；退出码 0 = 全部能力面通过。
The output is the deterministic capability report (the rule self-check + the four surfaces + the
PASS line); exit code 0 = every capability surface passed.

## 确定性报告样例 / Deterministic report sample

```sh
overturn_minimal ok: 2 rule(s), report=true, render=overturn.worldgen.density_offset=0.0; overturn.worldgen.structures=true
overturn_minimal rules two-tier ok (global=2, overrides=1, effective density_offset=3.5, overrides win)
overturn_minimal td datapack ok (entries=0, rules=2)
overturn_minimal export ok (tdBytes=476, zdBytes=680, rehydrate=ok)
overturn_minimal tie skip (no lib)
overturn_minimal PASS (all capability surfaces: rules two-tier, td datapack, export identity, tie skip-or-ok)
```

## 确定性纪律 / Determinism discipline

固定注册序、禁时序、禁随机、同输入同输出、禁 O(n²)；一切遵循框架确定性范式。
Fixed registration order, no timing, no randomness, same input → same output, no O(n²);
everything follows the framework determinism paradigm.
