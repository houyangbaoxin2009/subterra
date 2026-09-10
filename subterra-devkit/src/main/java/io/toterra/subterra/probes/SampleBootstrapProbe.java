// p.2.20.5: sample bootstrap acceptance probe — "sample-is-the-acceptance": runs the
// official OverturnMinimal.main as a self-bootstrap check inside this probe (System.out
// redirected to a capture buffer and restored in a finally), then asserts the sample's
// fixed-order deterministic report lines and inventories the sample structure from
// classpath resources. Pure JVM: no MC runtime, no wall-clock, no timestamps.
package io.toterra.subterra.probes;

import io.toterra.sample.overturn.OverturnMinimal;
import io.toterra.subterra.engine.config.Td;
import io.toterra.subterra.engine.config.TdTable;
import io.toterra.subterra.engine.config.TdValue;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * p.2.20.5 示例模组自举验收探针 —— p.2.20 里程碑判据「示例即验收」的确定性探针：把官方示例
 * {@link OverturnMinimal} 作为自举装置在本探针内跑通，再对「每一个框架能力面都被示例命中」
 * 逐项断言。三组校验：
 * <ol>
 *   <li><b>示例自检运行</b>：System.out 重定向到 ByteArrayOutputStream（try/finally 恢复），
 *       调用 {@code OverturnMinimal.main(new String[0])}——示例自身的能力面断言失败会抛异常，
 *       本探针捕获后计 FAIL；恢复输出后按固定序断言示例确定性报告行：{@code rules two-tier ok} /
 *       {@code td datapack ok} / {@code export ok (…rehydrate=ok)} / tie 契约行（skip-or-ok——
 *       devkit 运行类路径捆绑 TieBridgeProbe 夹具 dll 时命中 → {@code tie ok (lib=…)}，无 dll →
 *       {@code tie skip (no lib)}，不可装载 → {@code tie mismatch}）/ {@code overturn_minimal PASS
 *       (all capability surfaces}（逐行出现 + 指针推进单趟线性核对固定顺序，禁 O(n²)、禁时序）。</li>
 *   <li><b>示例结构盘点</b>：从 classpath 资源断言 {@code /data/overturn_minimal/pack.td} 可读且
 *       经 engine.config.Td 解析出 {@code rules} 表（2 行、每行 k+v、规则 key 恰为
 *       overturn.worldgen.density_offset + overturn.worldgen.structures）；{@code META-INF/neoforge.mods.toml}
 *       为通用资源名（根工程装配面与示例各一份）→ 用 {@code getResources} 枚举全部副本，断言至少
 *       一份声明 {@code overturn_minimal}（即示例 manifest）；并以示例自身的
 *       {@code OverturnMinimal.RULES_RESOURCE} 常量锚定被解析的资源路径。</li>
 *   <li><b>能力面 ↔ 示例命中映射</b>（以 javadoc 说明为准）：报告行即能力面命中证明——{@code rules
 *       two-tier ok} → engine.config.rules 双层覆盖；{@code td datapack ok} → engine.datapack
 *       td 数据包直载；{@code export ok (…rehydrate=ok)} → engine.export 导出恒等；tie 行 → engine.tie
 *       装载面；{@code PASS} → 全能力面通过门。</li>
 * </ol>
 * 每项失败计数 +1 并给出明确诊断；全过才输出 {@code [SampleBootstrapProbe] PASS (n checks)} 并
 * exit 0，否则 FAIL 计数 exit 1。确定性纪律：固定序、无时序、无随机；全部线性遍历（禁 O(n²)）；
 * 失败计数只在失败路径自增。纯 JVM——绝不触碰 Minecraft 类。
 *
 * <p>p.2.20.5 sample bootstrap acceptance probe — the p.2.20 milestone criterion
 * "sample-is-the-acceptance" as a deterministic probe: runs the official sample
 * {@link OverturnMinimal} as a self-bootstrap device inside this probe, then asserts one by
 * one that every framework capability surface is hit by the sample. Three groups:
 * <ol>
 *   <li><b>Sample self-check run</b>: System.out redirected to a ByteArrayOutputStream
 *       (restored in try/finally) while {@code OverturnMinimal.main(new String[0])} runs — the
 *       sample's own capability assertions throw on failure, which the probe catches and counts
 *       as FAIL; after restoration, the sample's fixed-order deterministic report lines are
 *       asserted: {@code rules two-tier ok} / {@code td datapack ok} /
 *       {@code export ok (…rehydrate=ok)} / the tie contract line (skip-or-ok — the devkit
 *       runtime classpath bundles the TieBridgeProbe fixture dll → {@code tie ok (lib=…)},
 *       no dll → {@code tie skip (no lib)}, unloadable → {@code tie mismatch}) /
 *       {@code overturn_minimal PASS (all capability surfaces} (verbatim contains + one
 *       pointer-advancing linear pass for the fixed order; no O(n²), no timing).</li>
 *   <li><b>Sample structure inventory</b>: from classpath resources asserts
 *       {@code /data/overturn_minimal/pack.td} is readable and parses via engine.config.Td to a
 *       {@code rules} table (2 rows, k+v per row, rule keys exactly
 *       overturn.worldgen.density_offset + overturn.worldgen.structures);
 *       {@code META-INF/neoforge.mods.toml} is a generic resource name (the root assembly
 *       project ships one too) → {@code getResources} enumerates all copies and asserts at least
 *       one declares {@code overturn_minimal} (the sample's manifest); the sample's own
 *       {@code OverturnMinimal.RULES_RESOURCE} constant anchors the parsed resource path.</li>
 *   <li><b>Capability surface ↔ sample hit mapping</b> (documented here; the report lines are
 *       the hit proof): {@code rules two-tier ok} → engine.config.rules two-tier override;
 *       {@code td datapack ok} → engine.datapack direct td datapack load;
 *       {@code export ok (…rehydrate=ok)} → engine.export export identity;
 *       the tie line → engine.tie load surface; {@code PASS} → full-surface pass gate.</li>
 * </ol>
 * Every failure is counted and diagnosed; PASS only when all checks pass, then exit 0, else FAIL
 * with counts and exit 1. Determinism discipline: fixed order, no timing, no randomness; all
 * traversals linear (no O(n²)); failures are counted only on failing paths. Pure JVM — never
 * touches Minecraft classes.
 */
public final class SampleBootstrapProbe {

    /** 示例确定性报告行的固定出现序（contains/契约断言 + 指针推进线性顺序核对）。
     *  Fixed occurrence order of the sample's deterministic report lines (contains/contract
     *  assertions + pointer-advancing linear order check). The tie line is the sample's own
     *  documented skip-or-ok contract: on the devkit runtime classpath the TieBridgeProbe
     *  fixture dll (/tie/tiefib_probe.dll) is bundled, so the sample's bundled-resource fallback
     *  hits it → {@code tie ok}; without a dll → {@code tie skip (no lib)}; present-but-unloadable
     *  → {@code tie mismatch}. All three branches are deterministic per environment. */
    private static final List<Pattern> REPORT_MARKERS = List.of(
            Pattern.compile(Pattern.quote("rules two-tier ok")),
            Pattern.compile(Pattern.quote("td datapack ok")),
            Pattern.compile("export ok \\(.*rehydrate=ok\\)"),
            Pattern.compile("overturn_minimal tie (skip \\(no lib\\)|ok \\(lib=.*\\)|mismatch \\(lib load failed\\))"),
            Pattern.compile(Pattern.quote("overturn_minimal PASS (all capability surfaces")));

    private static int checks = 0;
    private static int failures = 0;

    private SampleBootstrapProbe() {
    }

    public static void main(String[] args) {
        // 1) 运行示例自检：重定向 System.out 到捕获缓冲，try/finally 恢复（确定性：同输入同输出）。
        //    Run the sample self-check: System.out redirected to a capture buffer, restored in
        //    try/finally (deterministic: same input → same output).
        PrintStream original = System.out;
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        Throwable sampleFailure = null;
        try {
            System.setOut(new PrintStream(buffer, true, StandardCharsets.UTF_8));
            OverturnMinimal.main(new String[0]);
        } catch (Throwable t) {
            sampleFailure = t;
        } finally {
            System.setOut(original);
        }
        String captured = buffer.toString(StandardCharsets.UTF_8);
        if (sampleFailure != null) {
            failures++;
            System.out.println("[FAIL] OverturnMinimal.main threw: " + sampleFailure);
            sampleFailure.printStackTrace(System.out);
            System.out.println("--- OverturnMinimal captured output (diagnostic) ---");
            System.out.println(captured);
        } else {
            System.out.println("--- OverturnMinimal self-bootstrap report (captured) ---");
            System.out.print(captured);
            assertReportLines(captured);
            structureChecks();
        }
        if (failures == 0) {
            System.out.println("[SampleBootstrapProbe] PASS (" + checks + " checks)");
            System.exit(0);
        } else {
            System.out.println("[SampleBootstrapProbe] FAIL (" + failures + " of " + checks + " checks)");
            System.exit(1);
        }
    }

    // ---------- (1) sample report lines: verbatim presence + fixed order ----------

    private static void assertReportLines(String captured) {
        check("sample report line 'rules two-tier ok' present", REPORT_MARKERS.get(0).matcher(captured).find());
        check("sample report line 'td datapack ok' present", REPORT_MARKERS.get(1).matcher(captured).find());
        check("sample report line 'export ok (tdBytes=.., zdBytes=.., rehydrate=ok)' present",
                REPORT_MARKERS.get(2).matcher(captured).find());
        check("sample tie capability line present (skip (no lib) | ok (lib=..) | mismatch (lib load failed))",
                REPORT_MARKERS.get(3).matcher(captured).find());
        check("sample report line 'overturn_minimal PASS (all capability surfaces' present",
                REPORT_MARKERS.get(4).matcher(captured).find());

        // Fixed order via a single pointer-advancing pass over the lines (linear, no O(n²)).
        String[] lines = captured.split("\\R");
        int next = 0;
        for (int i = 0; i < lines.length && next < REPORT_MARKERS.size(); i++) {
            if (REPORT_MARKERS.get(next).matcher(lines[i]).find()) {
                next++;
            }
        }
        String broken = next < REPORT_MARKERS.size()
                ? " (first out-of-order/missing: " + REPORT_MARKERS.get(next).pattern() + ")"
                : "";
        check("sample report lines in fixed order (rules two-tier -> td datapack -> export ok -> tie -> PASS)"
                + broken, next == REPORT_MARKERS.size());
    }

    // ---------- (2) sample structure inventory from classpath resources ----------

    private static void structureChecks() {
        String packTd = readResource(OverturnMinimal.RULES_RESOURCE);
        check("sample pack.td classpath resource readable (" + OverturnMinimal.RULES_RESOURCE + ")", packTd != null);
        if (packTd != null) {
            TdTable root = Td.parse(packTd);
            TdValue rules = root.get("rules");
            List<TdValue> rows = rules instanceof TdTable t ? t.elements() : List.of();
            check("sample pack.td carries a 'rules' table with 2 rows", rows.size() == 2);
            boolean rowsCarryKV = true;
            TreeSet<String> keys = new TreeSet<>();
            for (TdValue row : rows) {
                if (row instanceof TdTable r) {
                    TdValue k = r.get("k");
                    rowsCarryKV = rowsCarryKV && k != null && r.get("v") != null;
                    if (k != null) {
                        keys.add(k.asString());
                    }
                } else {
                    rowsCarryKV = false;
                }
            }
            check("sample pack.td rules rows carry k+v pairs", rowsCarryKV);
            check("sample pack.td rule keys == {overturn.worldgen.density_offset, overturn.worldgen.structures}",
                    keys.equals(new TreeSet<>(List.of(
                            "overturn.worldgen.density_offset", "overturn.worldgen.structures"))));
        }
        // META-INF/neoforge.mods.toml is a generic resource name: the root assembly project
        // also ships one on this classpath, so enumerate all copies and require at least one to
        // declare the sample's modId (first-match would read the root's manifest instead).
        boolean tomlPresent = false;
        boolean overturnToml = false;
        try {
            var urls = SampleBootstrapProbe.class.getClassLoader().getResources("META-INF/neoforge.mods.toml");
            while (urls.hasMoreElements()) {
                tomlPresent = true;
                String content = readUrl(urls.nextElement());
                if (content != null && content.contains("overturn_minimal")) {
                    overturnToml = true;
                }
            }
        } catch (Exception e) {
            // enumeration failure counts as absent
        }
        check("a neoforge.mods.toml classpath resource is present", tomlPresent);
        check("a neoforge.mods.toml declares overturn_minimal (sample manifest)", overturnToml);
        check("OverturnMinimal.RULES_RESOURCE anchors the parsed pack.td",
                OverturnMinimal.RULES_RESOURCE.equals("/data/overturn_minimal/pack.td"));
    }

    /** 读取 classpath 资源（缺失/异常 → null）。Reads a classpath resource (null on missing/error). */
    private static String readResource(String path) {
        try (InputStream in = SampleBootstrapProbe.class.getResourceAsStream(path)) {
            if (in == null) {
                return null;
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

    /** 读取单个资源 URL 的文本（异常 → null）。Reads a resource URL's text (null on error). */
    private static String readUrl(java.net.URL url) {
        try (InputStream in = url.openStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

    // ---------- helpers ----------

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
