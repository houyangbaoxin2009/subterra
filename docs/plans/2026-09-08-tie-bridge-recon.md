# tie bridge Recon / tie 桥摸底（p.2.1）

**Status / 状态:** Feasibility proven / 可行性已验证
**Date / 日期:** 2026-09-08

## 目标 / Goal

p.2.1 tie bridge：`tiec → DLL → FFM` 加载调用。本文件记录摸底结论与可行性探针结果。

## 摸底结论 / Recon Findings

* `subterra-tie` 模块**无源码**（仅有历史构建 jar）——桥实现为空壳待填。 / subterra-tie has no sources; the bridge is an empty shell.
* tie 工具链已具备动态库能力：示例 `tie-main/examples/lib_math_dyn`（`run.ps1` 全流程），dist `tie-Harbor-2026.1-preview.6` 绑捆绑 LLVM（`bin\llvm`），**本机免设 TIE_LLVM_HOME 直接编译成功**。 / tiec already produces shared libraries; the dist bundles LLVM.
* **ABI 规格**（来自 `lib_math_dyn.tie` / `main.c`）：
  * 编译：`tiec x.tie -o x.dll`（显式 `.dll` 扩展）或 `--shared`。 / build with explicit .dll or --shared.
  * 导出面 = 顶层函数 + 命名空间 `pub func`；符号名 = 命名空间全名转 `$`（`mathdyn::add` → `mathdyn$add`）。 / exports = pub funcs; symbol = namespace-name with `$`.
  * 边界：仅标量（i64/f64/bool/trit/char）与 string 可跨边界；表/struct/map 等堆类型在动态库模式被编译器拒绝。 / scalar/string boundary only.
  * 私有函数不导出（llvmgen 不标 dllexport）。 / private funcs are not exported.
* 环境：Zulu **25.0.2**（FFM `java.lang.foreign` 稳定）。 / Java 25 with stable FFM.

## 可行性探针 / Feasibility Probe

* 链：`lib_math_dyn.tie` → tiec（preview.6）→ `lib_math_dyn_p6.dll` → Java 25 `SymbolLookup.libraryLookup` + `Linker.nativeLinker().downcallHandle`。 / full chain exercised.
* 结果（`FfmSmoke.java`，scratch `f:\Projects\_tmp_tiebridge`，不入库）：**全部 PASS**——6 个 pub 符号解析成功；`add(2,3)=5 / mul(6,7)=42 / sub(10,4)=6 / max2(9,4)=9 / max2(-3,5)=5 / neg(7)=-7 / use_private(3)=301`；私有 `mathdyn$private_helper` 未导出（find 返回 empty）。与 C 侧 `main.c` 断言逐一致。 / all assertions PASS, identical to the C smoke.
* 注：编译期仅一条 warning（module target triple override，无害）。 / one benign warning.

## 结论与下一步 / Conclusion & Next Step

* **可行性成立**：tiec→DLL→Java25 FFM 链路可行，ABI 与 C 调用方一致（`long` ↔ `long long`），无需任何桥层中间代码。 / Feasible; no intermediate bridge code needed.
* 待确认下一步（小任务 2/2）：把可行性探针**接线进 subterra**——`subterra-tie` 填实现（`TieBridge`：libraryLookup + 符号解析 + 调用面）＋ `subterra-devkit` 内探针（`TieBridgeProbe`，随 `probeAcceptance` 跑），并定 tie 产物获取策略（开发期现场 tiec 编译 vs 仓库内捆绑测试 mini tie 库）。 / Next: wire into subterra (module + devkit probe) and fix the tie-artifact sourcing strategy.