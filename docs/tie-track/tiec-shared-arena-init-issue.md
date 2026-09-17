# tiec --shared 缺陷移交单：共享库缺失运行时 arena 初始化（0xc0000005）

日期 / Date: 2026-09-17 · 提出方 / Reporter: Subterra 团队（subterra-residual）· 归属 / Owner: **tie 轨道（tie-main）**

## 现象 / Symptom

`tiec --shared` 产物（DLL）中，任何**触发 tie 运行时分配**的导出函数在 FFM 宿主
**首次调用**即崩溃：`EXCEPTION_ACCESS_VIOLATION (0xc0000005) at <dll>+0x101a`。
同一份源以 `type tie<logic>` 编译为独立 exe 则运行完全正常。纯标量零分配导出
不受影响。

/ In the `tiec --shared` output (DLL), any exported function that triggers a tie
runtime allocation crashes on the host's FIRST FFM call: access violation at
`<dll>+0x101a`. The same source compiled as a standalone `tie<logic>` exe runs
fine. Pure-scalar zero-allocation exports are unaffected.

## 最小复现 / Minimal repro（产物在 F://Projects//Toterra-Repo//_mini//）

* `m1_add.tie`：纯标量导出（无分配）→ `--shared` 后 FFM 调用 add3(1,2,3)=6 正常；
* `m2_md5.tie`：m1 + 一个调用 tlib md5_hex（字符串分配）的函数 → add3 正常，
  **md5_probe 首调在 m2_md5.dll+0x101a 崩溃**（与业务 DLL 崩溃 RVA 完全相同）；
* `m2_logic.exe`：同一份源编 exe → 正常（exit 0）。

驱动源：`_mini/TempMiniRepro.java`（FFM 调用，Java 25 Zulu，`--enable-native-access=ALL-UNNAMED`）。

## 反汇编证据 / Disassembly evidence（exe=smoke.exe，dll=subterra_density.dll，均权威 tiec 产出）

* hs_err：pc=dll+0x101a，读取地址 0x8（null+8）；调用链 density(0xa0e8)→0x70b4→md5_hex(0x45bf)→0x101a；
* dll+0x1000：`mov rax,[rip+…](→.data 0x2ab60); cmp [rax+8],0` —— 运行时 arena 根指针全局，
  dll 内对 0x2ab60 仅 3 处读、**0 处写**；
* exe 同一函数：对应全局 0x140034b90 在 0x14000a6d1 **被写**——写入者是运行时初始化函数
  （exe 0x14000a6c0：三次分配 arena 并落全局），由 exe CRT 启动路径 pre-main 调用（call @0x1400139ca）；
* dll 中该初始化函数**整个不存在**（无 `sub rsp,0x788` 前导、无 DllMain/TLS 回调补位、TLS 目录为空）；
  dll 的模块级惰性初始化钩子（0xa650）只覆盖模块全局，不建 arena。

## 结论 / Conclusion

`--shared` 产物缺失运行时 arena 初始化：exe 由 CRT pre-main 调用运行时初始化建 arena，
DLL 无等效路径 → 首个分配即空指针解引用。

## 修复方向（二选一，供 tie 轨道裁定）/ Fix directions (tie track's call)

a) `--shared` 产物在 DllMain/TLS callback 或模块惰性钩子中补 arena 初始化；
b) 导出运行时初始化符号（如 `tie$rt_init`），宿主 FFM load 后显式调用一次
   （Subterra 桥可立刻配合：装载后可选 downcall，符号缺失时忽略，向后兼容）。

## 影响面 / Impact

所有含运行时分配路径的 `--shared` 库（如 Subterra 的 subterra_density.dll 密度求值——
种子派生 MD5 串分配 + 40×259 晶格表）。纯标量库（如 trimand 的 coarse_* 查询面）不受影响。
