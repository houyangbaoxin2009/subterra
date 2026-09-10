// OverturnMinimal 示例规则包（p.2.20.3 骨架；语义随 p.2.20.4 兑现）。
// 规则文档形态 = 根表 rules = [ [ k = .., v = .. ], ... ]（RuleDocument.fromTd）。
// 颠覆目标：同一世界种子下，用规则改写世界生成密度偏移（全局地形密度上移/下沉）并控制
// 原版结构生成；两条规则由 engine.config.rules.RuleStore 装载、校验、渲染（确定性）。
//
// OverturnMinimal sample rules pack (p.2.20.3 skeleton; semantics land with p.2.20.4).
// Rule document shape = root table rules = [ [ k = .., v = .. ], ... ] (RuleDocument.fromTd).
// Overturn target: under a fixed world seed, rewrite the worldgen density offset (global terrain
// density shift) and gate vanilla structure generation; both rules are loaded, validated and
// rendered by engine.config.rules.RuleStore (deterministic).
type tie<data>
[ rules = [
  [ k = "overturn.worldgen.density_offset", v = 0.0 ],
  [ k = "overturn.worldgen.structures", v = true ],
] ]
