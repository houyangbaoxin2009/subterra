/**
 * tie bridge (L4): tiec -> DLL -> FFM loading, hot paths in tie.
 * p.2.19.4 landed: {@link io.toterra.subterra.runtime.tie.TieRuntime} folds the engine.tie FFM
 * bridge into the boot lifecycle (subterra.probe.tie ServerStarted gate, ok/skip/mismatch
 * markers, load via subterra.tie.lib property or the bundled /tie/tiefib_probe.dll resource).
 * p.2.19.4 落地：{@link io.toterra.subterra.runtime.tie.TieRuntime} 将 engine.tie 的 FFM 桥收编进
 * boot 生命周期（subterra.probe.tie 的 ServerStarted 门控，ok/skip/mismatch marker，装载走
 * subterra.tie.lib 属性或捆绑资源 /tie/tiefib_probe.dll）。
 */
package io.toterra.subterra.runtime.tie;
