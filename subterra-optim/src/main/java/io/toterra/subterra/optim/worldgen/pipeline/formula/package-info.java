package io.toterra.subterra.optim.worldgen.pipeline.formula;

/**
 * worldgen pipeline math-formula terrain engine core (p.1.8.9, self-developed,
 * clean-room reimplementation — no GPL code): a small parser for a pure-math
 * expression language over the world coordinates x/y/z, with a deterministic
 * Math function registry. Terrain height is defined by parsing a user formula
 * once ({@link Expr#parse}) and evaluating it many times ({@link Expr#eval})
 * with zero allocation in the hot path.
 *
 * <p>worldgen 管道层的数学公式地形引擎核心（p.1.8.9，自研、净室重实现，不包含任何
 * GPL 代码）：一个面向世界坐标 x/y/z 的纯数学表达式语言解析器，附带确定性的数学函数
 * 注册表。地形高度通过一次性解析用户公式（{@link Expr#parse}）并多次求值
 * （{@link Expr#eval}）实现，热点路径零分配。参见 {@link EvalContext}、{@link Lexer}、
 * {@link Parser} 与 {@link MathLib}。
 */
interface FormulaPackage {
}