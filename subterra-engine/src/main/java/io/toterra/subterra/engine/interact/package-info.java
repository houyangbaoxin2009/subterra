/**
 * p.2.14.1 世界内交互规范模型（engine 纯 JDK，不碰 MC）—— 以世界内语言描述"可被玩家交互的世界内实体"，
 * 作为「Diegetic interaction 世界内交互」的规范底座。本包只产出交互规范的<b>数据模型与 td 编解码</b>；
 * 确定性规则求值在 p.2.14.2、零 HUD 渲染规则/表面映射在 p.2.14.3，不在此包。
 * <p>
 * <b>「无系统气味」词汇约束（设计污染写死）</b>：本包词汇面只允许世界内表达——交互实体
 * （NPC / 碑文 / 卷轴 / 信件 / 遗物）、态度、世界内交互动作（交谈 / 阅读 / 呈奉 / 端详）。
 * 出现任何"HUD / 面板（panel）/ 进度条 / 数值计数 / 任务日志 / 等级图标 / 系统弹窗 / 系统提示"语义的名词，
 * 即视为设计污染，禁止写入本包。状态（如数值、等级、计数、日志、弹窗）都不属于世界内语言，不在此建模；
 * 本包只定型实体可交互面与其确定性的世界内表达。
 * <p>
 * <b>确定性</b>：枚举 `values()` 序固定（权威序）、动作集合用固定枚举集（TreeSet 序确定性）、
 * 交互规范以固定文档序承载；parse/toTd 往返形态稳定、同输入两次逐字节一致；不引入时间戳/随机/时序。
 * <p>
 * The p.2.14.1 in-world interaction-spec model (engine, pure JDK, no Minecraft) — it describes
 * "in-world entities the player can interact with" purely in in-world language, as the spec base of
 * "Diegetic interaction". This package only produces the <b>data model and td codec of an interaction
 * spec</b>; the deterministic rule evaluation is p.2.14.2 and the zero-HUD rendering rules / surface
 * mapping are p.2.14.3 — neither is here.
 * <p>
 * <b>"No system scent" vocabulary constraint (design pollution is written down)</b>: this package's
 * vocabulary is restricted to in-world expression — interactable entities (NPC / inscription / scroll /
 * letter / relic), attitudes, and in-world interaction actions (converse / read / offer / examine).
 * Any term with the semantics of "HUD / panel / progress bar / numeric counter / task log / level icon /
 * system popup / system prompt" is design pollution and is prohibited here. State (such as numbers,
 * levels, counters, logs, pop-ups) is not in-world language and is not modelled here; this package only
 * types the interactable surface of an entity and its deterministic in-world expression.
 * <p>
 * <b>Determinism</b>: enum `values()` order is fixed (authoritative), the action set is a fixed enum set
 * (deterministic TreeSet order), and an interaction spec is carried in fixed document order; the
 * parse/toTd round-trip is shape-stable and two calls for identical input are byte-identical; no
 * timestamp / random / timing is introduced.
 */
package io.toterra.subterra.engine.interact;
