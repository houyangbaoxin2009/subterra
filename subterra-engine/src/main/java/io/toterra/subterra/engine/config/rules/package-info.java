/**
 * p.2.17.1 类型化规则核心 + 确定性校验（纯 JDK，依赖 api.config 契约）。规则文档形态 = td 表
 * （读取语义对齐 {@code DatapackRules.fromManifest}）；值载体直接复用
 * {@code api.config.Rule}，本包不造重复值类型；双层配置语义由 {@code api.config.RuleRegistry}
 * 消费（p.2.17 后续：热重载、{@code /subterra rule} 命令）。
 * <p>
 * 类型化规则模型：{@link io.toterra.subterra.engine.config.rules.RuleType}
 * （BOOLEAN/INT/FLOAT/STRING/TABLE，{@code form()/fromForm()/accepts()}）、
 * {@link io.toterra.subterra.engine.config.rules.RuleKey} 与
 * {@link io.toterra.subterra.engine.config.rules.RuleSpec}（defaultValue 须过类型 accepts）。
 * 确定性校验：{@link io.toterra.subterra.engine.config.rules.RuleValidator} →
 * {@link io.toterra.subterra.engine.config.rules.RuleReport} /
 * {@link io.toterra.subterra.engine.config.rules.RuleViolation}
 * （UNKNOWN_KEY/TYPE_VIOLATION/CONFLICT/OTHER，固定序、禁时序）。
 * 规则文档形态：{@link io.toterra.subterra.engine.config.rules.RuleDocument}
 * （{@code fromTd} 读取 / {@code toTd} 规范写出 / {@code sampleDoc} 示例）。
 * <p>
 * p.2.17.1 typed rule core + deterministic validation (pure JDK, over the api.config contract).
 * The rule document shape is a td table (read semantics aligned with
 * {@code DatapackRules.fromManifest}); the value carrier reuses {@code api.config.Rule} — this
 * package creates no duplicate value type; two-tier config semantics are consumed by
 * {@code api.config.RuleRegistry} (later p.2.17 items: hot reload, the {@code /subterra rule}
 * command).
 * <p>
 * Typed rule model: {@link io.toterra.subterra.engine.config.rules.RuleType}
 * (BOOLEAN/INT/FLOAT/STRING/TABLE with {@code form()/fromForm()/accepts()}),
 * {@link io.toterra.subterra.engine.config.rules.RuleKey} and
 * {@link io.toterra.subterra.engine.config.rules.RuleSpec} (a defaultValue must pass its type).
 * Deterministic validation: {@link io.toterra.subterra.engine.config.rules.RuleValidator} →
 * {@link io.toterra.subterra.engine.config.rules.RuleReport} /
 * {@link io.toterra.subterra.engine.config.rules.RuleViolation}
 * (UNKNOWN_KEY/TYPE_VIOLATION/CONFLICT/OTHER, fixed order, no timing).
 * Rule document shape: {@link io.toterra.subterra.engine.config.rules.RuleDocument}
 * ({@code fromTd} read / {@code toTd} canonical write / {@code sampleDoc} sample).
 */
package io.toterra.subterra.engine.config.rules;
