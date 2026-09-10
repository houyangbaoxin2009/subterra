/**
 * p.2.16.2 配置规则契约面：Rule/RuleSet 载体 + 确定性 RuleRegistry（固定注册序、同 key 拒绝、
 * 双层解析与 DatapackRules 对齐；纯 JDK，api 不依赖 engine）。
 * <p>
 * p.2.16.2 config rule contract surface: Rule/RuleSet carriers + deterministic RuleRegistry
 * (fixed registration order, duplicate-key rejection, two-tier resolve aligned with
 * DatapackRules; pure JDK, no engine dependency).
 */
package io.toterra.subterra.api.config;
