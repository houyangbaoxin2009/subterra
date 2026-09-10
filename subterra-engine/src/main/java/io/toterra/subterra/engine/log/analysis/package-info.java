/**
 * 日志分析层：对日志记录做离线/在线诊断的后端分析器集合。
 * Log analysis layer: the set of analyzers that diagnose log records.
 * <p>
 * 含后端注册 {@link io.toterra.subterra.engine.log.analysis.Analyzers}、错误
 * {@link io.toterra.subterra.engine.log.analysis.ErrorAnalyzer}、规则
 * {@link io.toterra.subterra.engine.log.analysis.RulesAnalyzer} 与 AI 辅助
 * {@link io.toterra.subterra.engine.log.analysis.AiAnalyzer}。纯 JVM，不碰 MC；
 * 顺序固定、无时序副作用。
 * <p>
 * Holds the backend registry {@link io.toterra.subterra.engine.log.analysis.Analyzers},
 * the error {@link io.toterra.subterra.engine.log.analysis.ErrorAnalyzer}, rules
 * {@link io.toterra.subterra.engine.log.analysis.RulesAnalyzer} and AI-assisted
 * {@link io.toterra.subterra.engine.log.analysis.AiAnalyzer} analyzers. Pure JVM,
 * no MC coupling; fixed order, no timing side effects.
 */
package io.toterra.subterra.engine.log.analysis;