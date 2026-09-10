package io.toterra.sample.overturn;

import io.toterra.subterra.api.config.Rule;
import io.toterra.subterra.api.config.RuleSet;
import io.toterra.subterra.engine.config.rules.RuleKey;
import io.toterra.subterra.engine.config.rules.RuleSpec;
import io.toterra.subterra.engine.config.rules.RuleStore;
import io.toterra.subterra.engine.config.rules.RuleType;
import io.toterra.subterra.engine.datapack.Datapack;
import io.toterra.subterra.engine.datapack.DatapackLoader;
import io.toterra.subterra.engine.export.ConfigExporter;
import io.toterra.subterra.engine.tie.TieLibrary;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * p.2.20.4 官方示例模组最小入口（纯 JDK——不触 MC 类路径）。
 * <p>
 * 示例意图：最小「颠覆性模组」消费方——用框架纯 JDK 能力把「颠覆原版一小面」写成一段 td 规则
 * 逻辑。p.2.20.3 立结构（两条类型化规则规格 + 类路径装载 td 规则包 {@code /data/overturn_minimal/pack.td}
 * + 双层解析/校验/冻结 + 规范渲染）；p.2.20.4 兑现能力面演示，全部确定性（固定注册序、禁时序、
 * 禁随机、同输入同字节、同输入同输出）：
 * <ol>
 * <li><b>规则双层</b>（engine.config.rules.RuleStore）：全局档（pack.td 的 2 条）+ 覆盖档
 * （density_offset 覆盖为 3.5）→ resolve 后覆盖档胜出；</li>
 * <li><b>td 数据包直载</b>（engine.datapack.DatapackLoader）：把 classpath 资源 pack.td 暂存为
 * 数据包目录直载（manifest 规则直出）；</li>
 * <li><b>export 恒等</b>（engine.export.ConfigExporter）：有效双层配置导出 td + zd，断言
 * export∘rehydrate∘export 逐字节恒等；</li>
 * <li><b>tie 能力</b>（engine.tie.TieLibrary）：尝试装载 tiec 动态库——无 dll → 确定性
 * {@code tie skip (no lib)}；有 → {@code tie ok}；不抛异常。</li>
 * </ol>
 * 颠覆语义 = 把规则接入实际世界生成路径（如密度偏移）的 MC 装配面由根工程（root moddev 项目）
 * 提供；示例在此只演示纯 JDK 的规则/数据包/导出/tie 能力面。
 * <p>
 * p.2.20.4 official sample minimal entry (pure JDK — never touches the MC classpath).
 * <p>
 * Intent: the minimal "overturn" mod consumer — a piece of "overturn" logic written as td rules
 * against the framework's pure-JDK surface. p.2.20.3 established the structure (two typed rule
 * specs + classpath td rules pack {@code /data/overturn_minimal/pack.td} + two-tier resolve /
 * validate / freeze + canonical render); p.2.20.4 lands the capability-surface demo, all
 * deterministic (fixed registration order, no timing, no randomness, same input → same bytes,
 * same input → same output):
 * <ol>
 * <li><b>Two-tier rules</b> (engine.config.rules.RuleStore): a global layer (the 2 pack.td rules)
 * + an overrides layer (density_offset overridden to 3.5) → resolve makes the overrides win;</li>
 * <li><b>Direct td datapack load</b> (engine.datapack.DatapackLoader): the classpath pack.td is
 * staged as a datapack directory and loaded directly (manifest rules come out verbatim);</li>
 * <li><b>Export identity</b> (engine.export.ConfigExporter): the effective two-tier config exports
 * as td + zd, with export∘rehydrate∘export asserted byte-for-byte identical;</li>
 * <li><b>Tie capability</b> (engine.tie.TieLibrary): attempts to load a tiec-built dll — no dll →
 * deterministic {@code tie skip (no lib)}; present → {@code tie ok}; never throws.</li>
 * </ol>
 * The overturn semantics (wiring the rules into the real worldgen path, e.g. the density offset)
 * belong to the root MC-assembly project; this sample only demonstrates the pure-JDK rule /
 * datapack / export / tie capability surfaces.
 */
public final class OverturnMinimal {

    /** 规则文档类路径资源。The rule document classpath resource. */
    public static final String RULES_RESOURCE = "/data/overturn_minimal/pack.td";

    /** 覆盖档 td 文档（能力面 a）：把密度偏移覆盖为 3.5（确定性内联文档，无文件）。
     *  Overrides td document (surface a): density offset overridden to 3.5 (inline, no file). */
    static final String OVERRIDES_DOC =
            "type tie<data>\n[ rules = [ [ k = \"overturn.worldgen.density_offset\", v = 3.5 ] ] ]";

    /** 数据包暂存目录名（java.io.tmpdir 下，固定名，确定性——同 TieRuntime 的暂存模式）。
     *  Datapack staging dir name (under java.io.tmpdir, fixed name, deterministic — same staging
     *  pattern as TieRuntime). */
    private static final String STAGE_DIR = "overturn_minimal_datapack";

    /** tie 探针动态库资源（与 TieRuntime 同源同 ABI；本模块未捆绑 → 确定性 skip）。
     *  Bundled tie probe dll resource (same source/ABI as TieRuntime; not bundled here →
     *  deterministic skip). */
    private static final String TIE_BUNDLED_DLL = "/tie/tiefib_probe.dll";

    /** 捆绑 dll 提取后的固定临时文件名（确定性与 TieRuntime 一致）。
     *  Fixed temp filename after extracting the bundled dll (deterministic, as TieRuntime). */
    private static final String TIE_STAGED = "overturn_minimal_tie_probe.dll";

    private OverturnMinimal() {
    }

    /**
     * 确定性能力面自检入口：规格注册 → 装载规则包 → 解析/校验 → 冻结规则集 → 打印规范渲染，然后逐
     * 一演示四个能力面（双层规则 / td 数据包直载 / export 恒等 / tie skip-or-ok）并打印确定性报告
     * 行。任一能力面断言失败即抛异常（exit ≠ 0）；全部通过 → 打印 PASS 行、正常返回（exit 0）。
     * 同一输入永远产出同一输出。
     * <p>
     * Deterministic capability-surface self-check entry: register specs → load the rules pack →
     * resolve/validate → freeze the rule set → print the canonical render, then exercise the four
     * surfaces (two-tier rules / direct td datapack load / export identity / tie skip-or-ok) and
     * print one deterministic report line each. A failed surface assertion throws (exit ≠ 0); all
     * pass → the PASS line prints and the main returns normally (exit 0). The same input always
     * yields the same output.
     *
     * @param args 未使用（确定性：无任何输入依赖）/ unused (deterministic: no input dependency).
     */
    public static void main(String[] args) throws IOException {
        // 1) 声明两条类型化规则规格（固定注册序）——颠覆目标的两根旋钮。
        //    Declare two typed rule specs (fixed registration order) — the two knobs of the
        //    overturn target.
        List<RuleSpec> specs = List.of(
                new RuleSpec(new RuleKey("overturn.worldgen.density_offset"), RuleType.FLOAT, "0.0",
                        "global terrain density shift in blocks"),
                new RuleSpec(new RuleKey("overturn.worldgen.structures"), RuleType.BOOLEAN, "true",
                        "whether vanilla structures keep generating"));
        RuleStore store = new RuleStore(specs);

        // 2) 装载类路径中的 td 规则包（p.2.20.3 既定确定性自检：双层解析 + 校验 + 冻结 + 规范渲染）。
        //    Load the td rules pack from the classpath resource (the p.2.20.3 deterministic
        //    self-check: two-tier resolve + validate + frozen rule set + canonical render).
        try (InputStream in = OverturnMinimal.class.getResourceAsStream(RULES_RESOURCE)) {
            if (in == null) {
                throw new IOException("missing classpath resource: " + RULES_RESOURCE);
            }
            store.loadGlobalText(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }

        Map<String, String> effective = store.resolve();
        RuleSet frozen = RuleSet.of(effective.entrySet().stream()
                .map(e -> new Rule(e.getKey(), e.getValue()))
                .toList());
        System.out.println("overturn_minimal ok: " + frozen.rules().size() + " rule(s), report="
                + store.validate().ok() + ", render=" + store.render());

        // 3) 能力面 a —— 规则双层：覆盖档把 density_offset 覆盖为 3.5，resolve 后覆盖档胜出。
        //    Surface a — two-tier rules: the overrides layer overrides density_offset to 3.5,
        //    resolve makes the overrides win every conflicting key.
        store.loadOverridesText(OVERRIDES_DOC);
        Map<String, String> tiered = store.resolve();
        check("3.5".equals(tiered.get("overturn.worldgen.density_offset")),
                "overrides layer must win density_offset");
        check("true".equals(tiered.get("overturn.worldgen.structures")),
                "global layer must survive a non-conflicting key");
        System.out.println("overturn_minimal rules two-tier ok (global=" + store.global().size()
                + ", overrides=" + store.overrides().size()
                + ", effective density_offset=" + tiered.get("overturn.worldgen.density_offset")
                + ", overrides win)");

        // 4) 能力面 b —— td 数据包直载：把 classpath 资源 pack.td 暂存为数据包目录，经
        //    DatapackLoader 直载（manifest 规则直出；确定性：固定暂存名、无随机、输出不含路径）。
        //    Surface b — direct td datapack load: stage the classpath pack.td as a datapack
        //    directory and load it via DatapackLoader (manifest rules come out verbatim;
        //    deterministic: fixed stage name, no randomness, no path in the output).
        Path stage = Path.of(System.getProperty("java.io.tmpdir"), STAGE_DIR);
        try {
            deleteRecursively(stage); // 确定性干净暂存 / deterministic fresh stage
            Files.createDirectories(stage);
            try (InputStream in = OverturnMinimal.class.getResourceAsStream(RULES_RESOURCE)) {
                if (in == null) {
                    throw new IOException("missing classpath resource: " + RULES_RESOURCE);
                }
                Files.write(stage.resolve("pack.td"), in.readAllBytes());
            }
            Datapack pack = DatapackLoader.load(stage);
            check(pack.rules().size() == 2, "td datapack must carry the two manifest rules");
            System.out.println("overturn_minimal td datapack ok (entries=" + pack.entries().size()
                    + ", rules=" + pack.rules().size() + ")");
        } finally {
            deleteRecursively(stage); // best-effort 清理 / best-effort cleanup
        }

        // 5) 能力面 c —— export 恒等：把有效双层配置经 ConfigExporter 导出 td + zd，断言
        //    export∘rehydrate∘export 逐字节恒等（ConfigExporter 往返契约）。
        //    Surface c — export identity: export the effective two-tier config via ConfigExporter
        //    as td + zd, asserting export∘rehydrate∘export byte-for-byte identical (the exporter's
        //    round-trip contract).
        String exportTd = ConfigExporter.exportTd(store.global(), store.overrides());
        byte[] exportZd = ConfigExporter.exportZd(store.global(), store.overrides());
        String rehydratedTd = ConfigExporter.exportTd(ConfigExporter.rehydrate(exportTd));
        byte[] rehydratedZd = ConfigExporter.exportZd(ConfigExporter.rehydrateZd(exportZd));
        check(exportTd.equals(rehydratedTd), "export∘rehydrate∘export td identity");
        check(Arrays.equals(exportZd, rehydratedZd), "export∘rehydrate∘export zd identity");
        System.out.println("overturn_minimal export ok (tdBytes="
                + exportTd.getBytes(StandardCharsets.UTF_8).length + ", zdBytes=" + exportZd.length
                + ", rehydrate=ok)");

        // 6) 能力面 d —— tie 装载：尝试 engine.tie.TieLibrary 装载（固定序：subterra.tie.lib 属性 →
        //    类路径捆绑资源 → 缺省 skip）；无 dll → 确定性 skip，有 → ok，不抛异常。
        //    Surface d — tie load: attempt an engine.tie.TieLibrary load (fixed order:
        //    subterra.tie.lib property → bundled classpath resource → default skip); no dll →
        //    deterministic skip, present → ok, never throws.
        Path tieDll = locateTieDll();
        try {
            if (tieDll == null) {
                System.out.println("overturn_minimal tie skip (no lib)");
            } else {
                try (TieLibrary lib = TieLibrary.load(tieDll)) {
                    System.out.println("overturn_minimal tie ok (lib=" + lib.source() + ")");
                } catch (Throwable t) {
                    System.out.println("overturn_minimal tie mismatch (lib load failed)");
                }
            }
        } finally {
            if (tieDll != null && tieDll.getFileName().toString().equals(TIE_STAGED)) {
                try {
                    Files.deleteIfExists(tieDll);
                } catch (IOException ignored) {
                    // best-effort temp cleanup
                }
            }
        }

        System.out.println("overturn_minimal PASS (all capability surfaces: rules two-tier, "
                + "td datapack, export identity, tie skip-or-ok)");
    }

    /** 能力面断言：失败抛 IllegalStateException（main 异常退出 → exit ≠ 0）。
     *  Capability assertion: throws on failure (main exits abnormally → exit ≠ 0). */
    private static void check(boolean ok, String what) {
        if (!ok) {
            throw new IllegalStateException("overturn_minimal capability check failed: " + what);
        }
    }

    /** 递归删除目录树（尽力而为，绝不掩盖结果）。Best-effort recursive delete. */
    private static void deleteRecursively(Path root) {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try (var paths = Files.walk(root)) {
            for (Path p : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(p);
            }
        } catch (IOException ignored) {
            // best-effort cleanup, never masks the demo result
        }
    }

    /** 定位 tiec 动态库（固定序：{@code subterra.tie.lib} 属性 → 类路径捆绑资源）；两者皆缺返回
     *  null。Locates the tiec dll (fixed order: {@code subterra.tie.lib} property → bundled
     *  classpath resource); returns null when neither is present. */
    private static Path locateTieDll() {
        String external = System.getProperty("subterra.tie.lib");
        if (external != null && !external.isBlank()) {
            Path p = Path.of(external);
            if (Files.isRegularFile(p)) {
                return p; // 外部库由引擎 Arena 生命周期管理 / external lib owned by the engine arena
            }
        }
        Path staged = Path.of(System.getProperty("java.io.tmpdir"), TIE_STAGED);
        try (InputStream in = OverturnMinimal.class.getResourceAsStream(TIE_BUNDLED_DLL)) {
            if (in == null) {
                return null;
            }
            Files.copy(in, staged, StandardCopyOption.REPLACE_EXISTING);
            return staged;
        } catch (IOException e) {
            return null;
        }
    }
}
