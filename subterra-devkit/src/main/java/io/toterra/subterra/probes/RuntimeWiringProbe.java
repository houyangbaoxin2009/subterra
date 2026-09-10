// p.2.19.6: deterministic runtime-wiring inventory probe — a pure-JVM static wiring check that
// anchors the p.2.19 fold-in (the launch / runtime-fix / worldgen / optim-shell / tie / cfglog
// six categories + the export fold-in) to the compiled runtime outputs: class presence,
// gate-property constants, marker-prefix constants and the engine.export form enum order.
// Pure JVM: no MC runtime, no reflective bootstrap, no timing, no random.
package io.toterra.subterra.probes;

import io.toterra.subterra.engine.export.ExportKind;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * p.2.19.6 — 确定性 runtime 接线盘点探针：把 p.2.19 六类目标（launch / runtime-fix / worldgen /
 * optim-shell / tie / cfglog）与 export 收编的「静态接线契约」锚定到 runtime 编译输出上。
 * 纯 JVM、确定性、无时序；<b>不做反射 bootstrap、不触碰 Minecraft 类</b>——bootstrap 挂接
 * NeoForge 事件总线，生命周期只能在 boot E2E（AsyncE2EProbe / DatapackE2EProbe / ServerBootProbe）
 * 覆盖；本探针只核对编译产物层面的接线盘点。四组断言：
 * <ol>
 *   <li><b>六类落点盘点</b>：launch / fix / cfglog / tie 四壳精确核对（类存在 + marker 前缀字面量
 *       存在）；worldgen（async / gen / compare / profiler 四线各一代表类）与 optim-shell
 *       （SpawnEnforcementShell，docs 注明无自身 marker）抽样核对类存在。bootstrap() 不反射调用
 *       ——它注册 NeoForge 监听器（MC 事件总线），属 boot E2E 断言面。</li>
 *   <li><b>export 收编</b>：ExportCommandCore 存在 + HUB_MARKER 字段与字面量存在 + execute /
 *       exportFrom / exportForm / exportAllForms 方法名存在（方法名与字符串字面量同存常量池，
 *       字节扫描核对；execute 需要 MC {@code CommandContext}，永不调用）+ engine {@link ExportKind}
 *       七 form 枚举序 + export / exportHub 门控属性串在 ExportRuntime 字节存在。注意：
 *       ExportCommandCore 类初始化经 MC {@code LogUtils}，纯 JVM 不初始化该类
 *       （{@code Class.forName(fqcn, false)} 只加载），其共享核心 markers 文本生成确定性由
 *       DatapackE2EProbe（boot E2E）与 ExportHubProbe（engine 纯 JVM，p.2.18.6）覆盖。</li>
 *   <li><b>门控属性常量</b>：docs/runtime-wiring.md 总表全 14 属性串 → 对应壳类 .class 字节包含
 *       该字面量（{@code System.getProperty("subterra.probe.x")} 的字符串字面量编译进常量池，
 *       字节扫描 = 源码存在性的编译期等价核对；属性串全 ASCII，modified UTF-8 与原始字节一致）。</li>
 *   <li><b>marker 前缀清单</b>：各已挂接壳的 MARKER 字段名 + 前缀字面量在对应类字节（常量池）
 *       存在性核对——不用字段反射：{@code getDeclaredField} 会解析全部字段的声明类型（如
 *       {@code org.slf4j.Logger}，属 MC classpath），纯 JVM 运行期必然缺失；常量池字节扫描是
 *       只加载不初始化的等价核对（字段名与字符串字面量均以 UTF-8 存于常量池）。</li>
 * </ol>
 * 每项失败计数 +1 并给出诊断；全过输出 {@code [RuntimeWiringProbe] PASS (n checks)} exit 0，
 * 否则 FAIL exit 1。确定性纪律：固定序、无时序、无随机；全部线性遍历、禁 O(n²)；失败只在失败
 * 路径自增。纯 JVM——任何 runtime 壳都只加载不初始化（{@code Class.forName(fqcn, false)}）。
 *
 * <p>p.2.19.6 — deterministic runtime-wiring inventory probe: anchors the p.2.19 six target
 * categories (launch / runtime-fix / worldgen / optim-shell / tie / cfglog) and the export
 * fold-in to the compiled runtime outputs as a static wiring contract. Pure JVM, deterministic,
 * no timing; <b>no reflective bootstrap, no Minecraft classes touched</b> — the bootstrap hooks
 * sit on the NeoForge event bus, so the boot lifecycle is covered only by the E2E gates
 * (AsyncE2EProbe / DatapackE2EProbe / ServerBootProbe); this probe checks the compile-artifact
 * wiring inventory only. Four groups:
 * <ol>
 *   <li><b>six-category landing</b>: the launch / fix / cfglog / tie shells are checked exactly
 *       (class present + MARKER constant value); worldgen (one representative class per
 *       async / gen / compare / profiler line) and optim-shell (SpawnEnforcementShell, which
 *       per the docs carries no own marker) are sampled for class presence. bootstrap() is never
 *       reflected — it registers NeoForge listeners (MC event bus) and belongs to the boot E2E
 *       assertion surface.</li>
 *   <li><b>export fold-in</b>: ExportCommandCore present + HUB_MARKER field and literal present +
 *       execute / exportFrom / exportForm / exportAllForms method names present (method names live
 *       in the constant pool as UTF-8 like string literals, so the same load-only byte scan
 *       applies; execute needs the MC {@code CommandContext}, never invoked) + the engine
 *       {@link ExportKind} seven-form enum order + the export / exportHub gate strings present in
 *       the ExportRuntime bytes. Note: ExportCommandCore initializes through the MC {@code LogUtils},
 *       so the probe loads it without initializing ({@code Class.forName(fqcn, false)} loads only);
 *       the marker-text determinism of its shared cores is covered by DatapackE2EProbe (boot E2E)
 *       and ExportHubProbe (engine pure JVM, p.2.18.6).</li>
 *   <li><b>gate-property constants</b>: all 14 gate strings from the docs/runtime-wiring.md
 *       master table → the mapped shell class bytes contain that literal (the
 *       {@code System.getProperty("subterra.probe.x")} string literal is compiled into the
 *       constant pool; byte scanning is the compile-artifact equivalent of source presence; the
 *       strings are pure ASCII, so modified UTF-8 equals the raw bytes).</li>
 *   <li><b>marker-prefix checklist</b>: each wired shell's MARKER field name + prefix literal
 *       presence in the mapped class bytes (constant pool) — field reflection is deliberately not
 *       used: {@code getDeclaredField} resolves every declared field's type (e.g.
 *       {@code org.slf4j.Logger}, on the MC classpath), which is necessarily missing on the
 *       pure-JVM runtime classpath; the constant-pool byte scan is the load-only equivalent
 *       (field names and string literals both live in the constant pool as UTF-8).</li>
 * </ol>
 * Every failure is counted and diagnosed; PASS only when all checks pass, then exit 0, else
 * FAIL with counts and exit 1. Determinism discipline: fixed order, no timing, no randomness;
 * all traversals linear (no O(n²)); failures are counted only on failing paths. Pure JVM — no
 * runtime shell is ever initialized ({@code Class.forName(fqcn, false)} loads only).
 */
public final class RuntimeWiringProbe {

    private static int checks = 0;
    private static int failures = 0;

    private RuntimeWiringProbe() {
    }

    public static void main(String[] args) {
        try {
            sixCategoryLanding();
            exportFoldIn();
            gatePropertyConstants();
            markerPrefixChecklist();
        } catch (Exception e) {
            failures++;
            System.out.println("[FAIL] probe exception: " + e);
            e.printStackTrace(System.out);
        }
        if (failures == 0) {
            System.out.println("[RuntimeWiringProbe] PASS (" + checks + " checks)");
            System.exit(0);
        } else {
            System.out.println("[RuntimeWiringProbe] FAIL (" + failures + " of " + checks + " checks)");
            System.exit(1);
        }
    }

    // ---------- (1) six-category landing presence ----------

    private static void sixCategoryLanding() {
        // p.2.19.2 launch: bootstrap() is NOT reflected here — it registers NeoForge listeners
        // (MC event bus); the boot lifecycle is covered by the E2E gates (AsyncE2EProbe /
        // ServerBootProbe). Only the class + gate-property + marker constant contract is checked.
        shellLanding("launch", "io.toterra.subterra.runtime.launch.LaunchRuntime", "[Subterra launch]");
        shellLanding("fix", "io.toterra.subterra.runtime.fix.FixRuntime", "[Subterra fix]");
        // p.2.19.3 cfglog: two shells share one gate property and one marker prefix.
        shellLanding("cfglog.config", "io.toterra.subterra.runtime.cfglog.ConfigRuntime", "[Subterra cfglog]");
        shellLanding("cfglog.log", "io.toterra.subterra.runtime.cfglog.LogRuntime", "[Subterra cfglog]");
        shellLanding("tie", "io.toterra.subterra.runtime.tie.TieRuntime", "[Subterra tie]");
        // worldgen: one representative class per line (async / gen / compare / profiler).
        check("six-category landing [worldgen.async]: AsyncChunkRuntime present",
                classExists("io.toterra.subterra.runtime.worldgen.async.AsyncChunkRuntime"));
        check("six-category landing [worldgen.gen]: SubterraWorldgen present",
                classExists("io.toterra.subterra.runtime.worldgen.gen.SubterraWorldgen"));
        check("six-category landing [worldgen.compare]: SameSeedCompareHook present",
                classExists("io.toterra.subterra.runtime.worldgen.compare.SameSeedCompareHook"));
        check("six-category landing [worldgen.profiler]: WorldProfilerHook present",
                classExists("io.toterra.subterra.runtime.worldgen.profiler.WorldProfilerHook"));
        // optim-shell: SpawnEnforcementShell is a pure engine-gated shell and, per the docs
        // master table, carries no own marker prefix — presence is the contract checked here.
        check("six-category landing [optim.spawning]: SpawnEnforcementShell present",
                classExists("io.toterra.subterra.runtime.optim.entity.spawning.shell.SpawnEnforcementShell"));
    }

    private static void shellLanding(String label, String fqcn, String markerPrefix) {
        check("six-category landing [" + label + "]: class " + fqcn + " present", classExists(fqcn));
        check("six-category landing [" + label + "]: marker prefix '" + markerPrefix
                + "' literal present in class bytes", classBytesContain(fqcn, markerPrefix));
    }

    // ---------- (2) export fold-in ----------

    private static void exportFoldIn() {
        String core = "io.toterra.subterra.runtime.export.ExportCommandCore";
        check("export fold-in: ExportCommandCore present", classExists(core));
        check("export fold-in: HUB_MARKER declared and '[Subterra export]' literal present in class bytes",
                classBytesContain(core, "HUB_MARKER") && classBytesContain(core, "[Subterra export]"));
        // execute needs the MC CommandContext (never invoked in pure JVM) — presence only.
        // Method names live in the constant pool as UTF-8, so the same load-only byte scan
        // applies (reflection would resolve the MC signature types and fail on this classpath).
        check("export fold-in: execute (MC CommandContext, never invoked) method name present",
                classBytesContain(core, "execute"));
        check("export fold-in: exportFrom shared datapack core method name present",
                classBytesContain(core, "exportFrom"));
        check("export fold-in: exportForm shared hub core method name present",
                classBytesContain(core, "exportForm"));
        check("export fold-in: exportAllForms shared hub core method name present",
                classBytesContain(core, "exportAllForms"));
        check("export fold-in: engine ExportKind seven-form enum order "
                + "[world,save,datapack,language_keys,config,registries,migrate_maps]",
                sevenForms());
        check("export fold-in: gate 'subterra.probe.export' literal present in ExportRuntime",
                classBytesContain("io.toterra.subterra.runtime.export.ExportRuntime",
                        "subterra.probe.export"));
        check("export fold-in: gate 'subterra.probe.exportHub' literal present in ExportRuntime",
                classBytesContain("io.toterra.subterra.runtime.export.ExportRuntime",
                        "subterra.probe.exportHub"));
    }

    private static boolean sevenForms() {
        ExportKind[] kinds = ExportKind.values();
        String[] expected = {"world", "save", "datapack", "language_keys", "config",
                "registries", "migrate_maps"};
        if (kinds.length != expected.length) {
            return false;
        }
        for (int i = 0; i < expected.length; i++) {
            if (!kinds[i].form().equals(expected[i])) {
                return false;
            }
        }
        return true;
    }

    // ---------- (3) gate-property constants (all 14, per docs/runtime-wiring.md master table) ----------

    private static void gatePropertyConstants() {
        String[][] gates = {
                {"subterra.probe.launch", "io.toterra.subterra.runtime.launch.LaunchRuntime"},
                {"subterra.probe.fix", "io.toterra.subterra.runtime.fix.FixRuntime"},
                {"subterra.probe.cfglog", "io.toterra.subterra.runtime.cfglog.ConfigRuntime"},
                {"subterra.probe.tie", "io.toterra.subterra.runtime.tie.TieRuntime"},
                {"subterra.probe.export", "io.toterra.subterra.runtime.export.ExportRuntime"},
                {"subterra.probe.exportHub", "io.toterra.subterra.runtime.export.ExportRuntime"},
                {"subterra.probe.rule", "io.toterra.subterra.runtime.rules.RulesRuntime"},
                {"subterra.probe.save", "io.toterra.subterra.runtime.save.SaveRuntime"},
                {"subterra.probe.world", "io.toterra.subterra.runtime.world.WorldRuntime"},
                {"subterra.probe.network", "io.toterra.subterra.runtime.network.EnhancedChannelRuntime"},
                {"subterra.probe.saveverify", "io.toterra.subterra.runtime.saveverify.SaveVerifyRuntime"},
                {"subterra.probe.session", "io.toterra.subterra.runtime.session.SessionRuntime"},
                {"subterra.probe.sim", "io.toterra.subterra.runtime.sim.SimRuntime"},
                {"subterra.probe.async", "io.toterra.subterra.runtime.worldgen.async.AsyncChunkRuntime"},
        };
        for (String[] g : gates) {
            check("gate property '" + g[0] + "' literal present in " + g[1],
                    classBytesContain(g[1], g[0]));
        }
    }

    // ---------- (4) marker-prefix checklist ----------

    private static void markerPrefixChecklist() {
        marker("launch", "io.toterra.subterra.runtime.launch.LaunchRuntime",
                "MARKER", "[Subterra launch]");
        marker("fix", "io.toterra.subterra.runtime.fix.FixRuntime",
                "MARKER", "[Subterra fix]");
        marker("cfglog", "io.toterra.subterra.runtime.cfglog.ConfigRuntime",
                "MARKER", "[Subterra cfglog]");
        marker("tie", "io.toterra.subterra.runtime.tie.TieRuntime",
                "MARKER", "[Subterra tie]");
        marker("export-hub", "io.toterra.subterra.runtime.export.ExportCommandCore",
                "HUB_MARKER", "[Subterra export]");
        marker("datapack", "io.toterra.subterra.runtime.datapack.DatapackRegistrar",
                "MARKER", "[Subterra datapack]");
        marker("rule", "io.toterra.subterra.runtime.rules.RuntimeRuleCommand",
                "MARKER", "[Subterra rule]");
        marker("save", "io.toterra.subterra.runtime.save.SaveRuntime",
                "MARKER", "[Subterra save]");
        marker("saveverify", "io.toterra.subterra.runtime.saveverify.SaveVerifyRuntime",
                "MARKER", "[Subterra saveverify]");
        marker("session", "io.toterra.subterra.runtime.session.SessionRuntime",
                "MARKER", "[Subterra session]");
        marker("sim", "io.toterra.subterra.runtime.sim.SimRuntime",
                "MARKER", "[Subterra sim]");
        marker("world", "io.toterra.subterra.runtime.world.WorldRuntime",
                "MARKER", "[Subterra world]");
        marker("network", "io.toterra.subterra.runtime.network.EnhancedChannelRuntime",
                "MARKER", "[Subterra network]");
        marker("async", "io.toterra.subterra.runtime.worldgen.async.AsyncChunkRuntime",
                "MARKER", "[Subterra async]");
    }

    private static void marker(String label, String fqcn, String field, String expected) {
        check("marker checklist [" + label + "]: " + field + " declared and prefix '" + expected
                + "' literal present in class bytes",
                classBytesContain(fqcn, field) && classBytesContain(fqcn, expected));
    }

    // ---------- helpers (load-only, never initialize runtime shells) ----------

    /** 只加载不初始化地确认类存在。Loads without initializing — presence only. */
    private static boolean classExists(String fqcn) {
        try {
            Class.forName(fqcn, false, RuntimeWiringProbe.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }

    /** 读类的 .class 资源字节（classpath 编译输出）；缺失返回 null。Reads the class-file resource
     * bytes from the classpath compiled outputs; null when absent. */
    private static byte[] classBytes(String fqcn) {
        String resource = "/" + fqcn.replace('.', '/') + ".class";
        try (InputStream in = RuntimeWiringProbe.class.getResourceAsStream(resource)) {
            if (in == null) {
                return null;
            }
            return in.readAllBytes();
        } catch (IOException e) {
            return null;
        }
    }

    /** 断言类的 .class 字节包含某字符串字面量（常量池 UTF-8）。Asserts the class bytes contain a
     * string literal (constant-pool UTF-8). The property strings are pure ASCII, so modified UTF-8
     * in the constant pool equals the raw bytes; ISO-8859-1 is a byte-identity mapping, making
     * contains() a linear substring scan (no O(n²)). */
    private static boolean classBytesContain(String fqcn, String literal) {
        byte[] bytes = classBytes(fqcn);
        if (bytes == null) {
            return false;
        }
        return new String(bytes, StandardCharsets.ISO_8859_1).contains(literal);
    }

    private static void check(String name, boolean ok) {
        checks++;
        if (ok) {
            System.out.println("[PASS] " + name);
        } else {
            failures++;
            System.out.println("[FAIL] " + name);
        }
    }
}
