# p.1.8.14 方块运动属性（Block Motion Profiles）设计

## Context（背景）

用户需求：**不同种类的生物**走在不同方块上移动速度不同（如蜘蛛在蛛网上不减速、灵魂沙减速、冰面提速）。已与用户对齐的策略：

* 规则**挂在方块上**（非生物）——方块为主角，生物差异作为方块条目内的子覆盖；为将来方块获得更多新特性打底（数据模型按「方块记录 + 能力段」组织，当前实现 `motion` 能力段，未来能力段并列扩展）。
* 机制覆盖三层：**倍率（speed factor）+ 摩擦（friction）+ 粘性（sticky）**。
* 数值语义 = **表达式**：复用现有公式引擎（`subterra-optim/worldgen/pipeline/formula`，来自 FormulaTerrain），字面量与公式共存。
* 默认行为 = **原版镜像**（空表即完全原版，无感）。
* 双接口：**Java API**（模组开发者）+ **td 配置**（普通开发者/玩家），汇入同一张最终表。
* td 匹配粒度：精确 id + 标签 + 通配兜底。

## 设计决策

1. **双层架构**（遵循现有构建模式）：纯 JDK 核心进 `subterra-optim` 新包 `io.toterra.subterra.optim.motion`（数据模型、解析结果、求值、优先级解析、预计算表——探针无需启动 MC 即可测）；MC 粘合层放新源码宿主目录 `subterra-motion/src/main/java`（并入 root `sourceSets.main`，同 subterra-servercore/invadvopt 模式），含 Mixin、td 加载接线、API 实现、reload 钩子。包名统一 `io.toterra.subterra.optim.motion`（核心与粘合层同包，jar 聚合合并，与 activation 核心 + servercore mixin 同包先例一致）。
2. **td 数据模型**（`config/subterra/blocks.td`，方块记录行表 + `motion` 能力段）：

```td
// Subterra 方块运动属性（td），未配置的方块 = 完全原版行为
// speed/friction 支持字面量或公式表达式；变量 base = 该方块该属性的原版值
// 函数见 MathLib（clamp/min/max/abs/...）；// 注释；尾逗号容忍
type tie<data>
blocks = [
  // 蛛网：默认减速到 0.2、粘性；蜘蛛类生物覆盖为不减速
  [ id = "minecraft:cobweb", motion = [
      speed = 0.2,
      sticky = true,
      mob = [
        [ id = "minecraft:spider", speed = 1.0 ],
        [ id = "minecraft:cave_spider", speed = 1.0 ],
        [ tag = "minecraft:arthropod", speed = 0.9 ],
      ],
  ] ],
  // 灵魂沙：公式表达式，相对原版值减半
  [ id = "minecraft:soul_sand", motion = [
      speed = "base * 0.5",
  ] ],
  // 标签匹配整类方块：冰面提速
  [ tag = "minecraft:ice", motion = [
      speed = 1.2,
      friction = 0.85,
  ] ],
  // 通配兜底（可选，未列出时默认为原版）：[ id = "*", motion = [ speed = 1.0 ] ]
]
```

* td 语法本为 tie 表字面量：统一 `[ ]`、map 键为不带引号的标识符、字符串一律双引号、注释 `//`、容忍尾逗号；方块/生物条目为**无键行表数组元素**（同 item_control 的 `blacklist = [ [ id = ... ] ]` 模式），id 是行内字段而非键。
* 数值字段接受标量或字符串：`speed = 0.2`（FLOAT→字面量公式）与 `speed = "base * 0.5"`（STRING→公式源）经 TdValue 类型区分，汇入同一求值路径。

3. **表达式语义**：复用 `Expr.parse` + `MathLib`。**对公式引擎的最小泛化**：新增接口 `VariableContext { double variable(String) }`，现有 `EvalContext` 实现之（保留 x/y/z/seed 绑定，worldgen 调用点零改动）；新增 `MotionEvalContext` 只绑定 `base`（该方块该属性的原版值）。`Expr.eval(EvalContext)` 参数类型改为 `VariableContext`（源码兼容）。
4. **求值与预计算（性能铁律）**：表达式中唯一变量 `base` 对固定（方块, 生物）组合是常量 → **加载期解析并求值一次**，预计算为 `(blockId/mob matcher) → float` 常量表；运行时 Mixin 内 O(1) 查表，零每-tick 表达式求值、零每-tick 字符串。sticky 为布尔表。
5. **挂接机制**（1.21.1 mojmap 名实施时核对反编译源）：
   * `Entity#getSpeedFactor()` Mixin：脚下支撑方块 `blockStateOn` 在表中时返回解析值；不在表内回退 `super`（恒为原版镜像）。
   * 摩擦：Mixin 替换移动时对该方块 `getFriction` 的使用点值。
   * sticky（最小范围）：方块接触碰撞即应用 speed 减速（近似蛛网行为）；不做蜂蜜粘附/史莱姆弹跳——后者属于将来方块能力扩展。
   * Mixin 双端生效（移动双端模拟需一致），td 为全局配置（BOTH）。服务端权威不变。
6. **API**（subterra-api，纯 JDK、不依赖 MC 类，String id/表达式键控；沿用现有 CryptoApi/SerApi 的「final 类 + 静态方法 + record」风格，实现位于 subterra-motion 粘合层并写回同一张最终表）：

```java
// 例：APIMotionApi（最终命名以实现为准）
MotionProfileApi.register("minecraft:cobweb",
        MotionSpec.builder().speed("0.2").sticky(true).build());
MotionProfileApi.registerMobOverride("minecraft:cobweb", "minecraft:spider", "1.0");
```

* **合并优先级（定稿规则）**：td 先加载为基线；API 注册**后注册者覆盖同键条目**，并以影子条目原子替换（只重建受影响键，不全局重建查表层）；重载 td 时清空影子条目回到基线。
7. **配置加载与重载**：subterra-config 的 `Td.parse` 读 `config/subterra/blocks.td`（BOTH 侧）；启动与服务器命令/`/reload` 触发热重载；解析错误带**行表条目定位**上报，坏条目单独跳过、不整表作废（对齐 item_control 的失败降级惯例：坏文件回退默认，不崩）。未知方块/生物 id 与标签解析延迟到注册表就绪（datapack 加载后）。
8. **测试**（subterra-probes 探针，参照 FormulaProbe）：字面量、公式、`base` 语义、mob 优先级（精确 id > 标签 > 块内默认 > 原版）、标签多命中合取、通配兜底、原版镜像回退、非法公式/未知函数报错、预计算表值与逐次求值一致性。
9. **版本与文档**：`gradle.properties` mod_version p.1.8.13 → p.1.8.14；CHANGELOG 双语条目（沿现有风格）；neoforge.mods.toml 增 `[[mixins]] config="subterra-motion.mixins.json"`；root build.gradle `sourceSets.main` 增 `java.srcDir('subterra-motion/src/main/java')`。

## 文件改动清单

### A. subterra-optim（纯 JDK 核心，可探针直测）
1. `subterra-optim/.../worldgen/pipeline/formula/VariableContext.java`（新）：`double variable(String)` 接口。
2. `subterra-optim/.../worldgen/pipeline/formula/EvalContext.java`：`implements VariableContext`，签名不变。
3. `subterra-optim/.../worldgen/pipeline/formula/Expr.java`：`eval(VariableContext ctx)`（参数类型泛化，源码兼容）。
4. `subterra-optim/.../motion/MotionEvalContext.java`（新）：绑定 `base`。
5. `subterra-optim/.../motion/MotionProfile.java`（新）：不可变记录——`speedExpr`/`frictionExpr`（String，null=未配置）、`sticky`（boolean）、`mobOverrides`（按精确 id / 标签分组）。
6. `subterra-optim/.../motion/MotionProfiles.java`（新）：解析后的最终常量表 + 决议：`resolveSpeed(blockId, mobId) -> double`（优先级：mob 精确 id > mob 标签 > 块 speed > 原版，原版由调用方注入 base）；加载期预计算。

### B. subterra-motion（MC 粘合层，新源码宿主目录，并入根源集）
1. `subterra-motion/src/main/java/io/toterra/subterra/optim/motion/MotionLoader.java`：Td.parse → MotionProfile 解析（TdValue 标量→字面量、字符串→公式源）、id/tag/通配解析、注册表就绪后预计算、坏条目跳过、reload 钩子。
2. `subterra-motion/.../motion/mixin/EntityMotionMixin.java`：`Entity#getSpeedFactor` 改写。
3. `subterra-motion/.../motion/mixin/EntityFrictionMixin.java`：摩擦使用点替换。
4. `subterra-motion/.../motion/MotionApiImpl.java`：`MotionProfileApi` 实现，注册写回同一最终表。
5. `subterra-motion/src/main/resources/subterra-motion.mixins.json`（新）；`src/main/templates/META-INF/neoforge.mods.toml` 注册 `[[mixins]]`。
6. `build.gradle`：`sourceSets.main.java.srcDir('subterra-motion/src/main/java')`。

### C. subterra-api（纯 JDK）
1. `subterra-api/.../api/motion/MotionProfileApi.java`（新）：`register(String blockId, MotionSpec)`、`registerMobOverride(String blockId, String mobId, String speedExpr)`。
2. `subterra-api/.../api/motion/MotionSpec.java`（新）：builder（speed/friction/sticky/mobOverrides 的 String 表达）。

### D. subterra-probes（确定性验证）
1. `subterra-probes/.../MotionProbe.java`（新）：断言矩阵见设计决策 §8；PASS 文案 `[MotionProbe] PASS (...)`。
2. `subterra-probes/build.gradle`：`runMotionProbe` 任务 + `probeAcceptance` dependsOn 挂接。

### E. 版本与文档
1. `gradle.properties`：`mod_version=p.1.8.14`。
2. `CHANGELOG.md`：p.1.8.13 条目上方插双语条目（方块运动属性：td blocks 表 + 表达式语义 + 生物覆盖；公式引擎 `VariableContext` 泛化；预计算查表；探针）。

### 引用核查
全仓 `rg` 确认无既有 `motion`/`MotionProfile` 包名冲突；`Expr.eval` 调用点全部走 `EvalContext`（参数泛化不破坏编译）。

## 验证

1. `.\gradlew.bat compileJava` — 编译通过。
2. `.\gradlew.bat :subterra-probes:runMotionProbe` — PASS（断言矩阵含优先级/base/标签/通配/回退/非法公式）。
3. `.\gradlew.bat :subterra-probes:probeAcceptance` — 全探针绿（含开机门禁）。
4. `.\gradlew.bat runClient` — 日志出现 motion 配置加载与预计算信息；蜘蛛站蛛网不减速、村民/玩家站灵魂沙减速、冰面提速；无 mixin 异常。
5. （反例）配置写非法公式（如 `speed = zzz(1)`）：加载仅跳过该条目并告警，其余条目与游戏正常。
6. `git add` 具体文件 → 单一 commit：`feat: block motion profiles — per-block speed/friction/sticky with td + API (p.1.8.14)`。

## 风险与既定取舍

* 旧字段/未知能力段静默忽略（p 阶段不写兼容垫片）。
* sticky 为最小近似（接触减速），蜂蜜粘附/史莱姆弹跳留待后续方块能力；文档声明边界。
* 表达式中仅 `base` 一个变量（无 tick/随机 — 保确定性）；靠 MathLib 函数组合足够覆盖常见需求。
* 双端模拟一致性依赖 BOTH 加载同一 td；纯服务端与客户端行为差异由移动同步机制掩盖（同原版灵魂沙）。
* td 与 API 合并采用「baseline + 影子覆盖」定稿规则（见设计决策 §6），优先级无歧义。