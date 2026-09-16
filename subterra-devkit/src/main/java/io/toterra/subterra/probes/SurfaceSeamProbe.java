// p.2.29.2: 规则→表面真接缝的确定性验收探针（纯 JVM）。
// 引擎侧核心 SurfacePaletteResolution 的解析/渲染/apply/拒绝 + 规范序 + 缺省恒等；
// api 契约 WorldgenAssemblyApi 的 palette 选择子一致性；
// runtime 接缝 SubterraSurfaceRules 的「只加载不初始化」接线盘点（rule-source 类型注册 + gate + marker）
// 与随附数据包 noise_settings 的 surface_rule 顶层引用核对。
// 纯 JVM：不初始化任何 runtime 壳、不碰 Minecraft 类；无时序、无随机；退出码 0 = 全过。
//
// p.2.29.2 deterministic acceptance probe for the rules→surface real seam (pure JVM): the engine core
// SurfacePaletteResolution (parse / render / reject + canonical order + default identity), the
// api-contract palette-selector consistency (WorldgenAssemblyApi), the runtime seam
// SubterraSurfaceRules load-only wiring inventory (rule-source registration + gate + marker) and the
// shipped noise_settings surface_rule top-level reference. Pure JVM: no runtime shell is ever
// initialized, no Minecraft class is touched; no timing, no randomness; exit 0 = all passed.
package io.toterra.subterra.probes;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import io.toterra.subterra.api.worldgen.assembly.WorldgenAssemblyApi;
import io.toterra.subterra.engine.worldgen.pipeline.surfacerules.SurfacePaletteResolution;

/** p.2.29.2 rules→surface seam probe. / p.2.29.2 规则→表面缝探针。 */
public final class SurfaceSeamProbe {

    /** The palette selector rule key (mirrors DensityAssembly#RULE_SURFACE_PALETTE). /
     *  palette 选择子规则键（逐字镜像）。 */
    private static final String PALETTE_PROP = "subterra.worldgen.surface_palette";

    /** Fixed rebind keys for canonical overworld states (no randomness). / 固定重绑键（无随机）。 */
    private static final String STONE_KEY = SurfacePaletteResolution.KEY_PREFIX + "minecraft:stone";
    private static final String DIRT_KEY = SurfacePaletteResolution.KEY_PREFIX + "minecraft:dirt";

    /** The runtime seam class (loaded without initializing). / runtime 接缝类（只加载不初始化）。 */
    private static final String SURFACE_RUNTIME =
            "io.toterra.subterra.runtime.worldgen.gen.SubterraSurfaceRules";

    /** The shipped noise_settings that references the seam at the top of {@code surface_rule}. /
     *  随附数据包（在 {@code surface_rule} 顶层引用接缝）。 */
    private static final String NOISE_SETTINGS_RESOURCE =
            "/data/subterra/worldgen/noise_settings/subterra_overworld.json";

    private static int checks = 0;
    private static int failures = 0;

    private SurfaceSeamProbe() {
    }

    public static void main(String[] args) {
        try {
            defaultIdentity();
            resolveDeterminismAndOrder();
            rejectionSurface();
            apiConsistency();
            wiringInventory();
        } catch (Exception e) {
            failures++;
            System.out.println("[FAIL] probe exception: " + e);
            e.printStackTrace(System.out);
        }
        if (failures == 0) {
            System.out.println("[SurfaceSeamProbe] PASS (" + checks + " checks)");
            System.exit(0);
        } else {
            System.out.println("[SurfaceSeamProbe] FAIL (" + failures + " of " + checks + " checks)");
            System.exit(1);
        }
    }

    // ---------- (1) default identity (zero change on the default preset) ----------

    private static void defaultIdentity() {
        SurfacePaletteResolution.Resolved empty = SurfacePaletteResolution.fromRules(Map.of());
        SurfacePaletteResolution.Resolved nullRules = SurfacePaletteResolution.fromRules(null);
        check("default: empty rules == DEFAULT and identity() (vanilla, no rebind)",
                empty.equals(SurfacePaletteResolution.DEFAULT) && empty.identity());
        check("default: null rules == DEFAULT (missing keys take defaults)",
                nullRules.equals(SurfacePaletteResolution.DEFAULT));
        check("default: canonical render 'palette=vanilla; remap=0'",
                "palette=vanilla; remap=0".equals(SurfacePaletteResolution.render(empty)));
        check("default: identity() reported on the constant",
                SurfacePaletteResolution.DEFAULT.identity());
    }

    // ---------- (2) resolve determinism + canonical order (same input → same bytes) ----------

    private static void resolveDeterminismAndOrder() {
        // Two rebinds supplied out of order — the canonical render orders by state name (dirt<stone).
        Map<String, String> rules = Map.of(
                PALETTE_PROP, "custom",
                STONE_KEY, "minecraft:diamond_ore",
                DIRT_KEY, "minecraft:grass_block");
        SurfacePaletteResolution.Resolved r1 = SurfacePaletteResolution.fromRules(rules);
        SurfacePaletteResolution.Resolved r2 = SurfacePaletteResolution.fromRules(rules);
        check("resolve: custom palette selected", "custom".equals(r1.palette()));
        check("resolve: rebind table size 2", r1.mapping().size() == 2);
        check("resolve: deterministic re-entry (equal carrier)", r1.equals(r2));
        String expected = "palette=custom; remap=2; minecraft:dirt->minecraft:grass_block; "
                + "minecraft:stone->minecraft:diamond_ore";
        check("resolve: canonical render ordered by state name [" + expected + "]",
                expected.equals(SurfacePaletteResolution.render(r1)));
        check("resolve: render byte-identical on re-entry",
                SurfacePaletteResolution.render(r1).equals(SurfacePaletteResolution.render(r2)));
        check("resolve: custom + rebind is NOT identity", !r1.identity());

        // vanilla palette normalises the rebind table to empty (unambiguous canonical form).
        Map<String, String> vanillaWithRebind = Map.of(
                PALETTE_PROP, "vanilla",
                STONE_KEY, "minecraft:diamond_ore");
        SurfacePaletteResolution.Resolved normalised = SurfacePaletteResolution.fromRules(vanillaWithRebind);
        check("normalise: vanilla palette drops the rebind table (identity, remap=0)",
                normalised.identity() && normalised.mapping().isEmpty()
                        && "palette=vanilla; remap=0".equals(SurfacePaletteResolution.render(normalised)));

        // unknown keys must be ignored deterministically.
        SurfacePaletteResolution.Resolved withUnknown = SurfacePaletteResolution.fromRules(Map.of(
                "overturn.worldgen.something", "true",
                "subterra.worldgen.trimand_enabled", "true"));
        check("unknown/foreign keys ignored (still identity default)",
                withUnknown.equals(SurfacePaletteResolution.DEFAULT));
    }

    // ---------- (3) deterministic rejection surface ----------

    private static void rejectionSurface() {
        check("reject: unknown palette id", rejects(Map.of(PALETTE_PROP, "bogus")));
        check("reject: unknown surface state", rejects(Map.of(
                SurfacePaletteResolution.KEY_PREFIX + "minecraft:not_a_state", "minecraft:dirt")));
        check("reject: blank target block id", rejects(Map.of(STONE_KEY, "  ")));
        Map<String, String> nullTarget = new HashMap<>();
        nullTarget.put(PALETTE_PROP, "custom");
        nullTarget.put(STONE_KEY, null);
        check("reject: null-valued rebind target", rejects(nullTarget));
    }

    private static boolean rejects(Map<String, String> rules) {
        try {
            SurfacePaletteResolution.fromRules(rules);
            return false;
        } catch (IllegalArgumentException expected) {
            return true;
        }
    }

    // ---------- (4) consistency with the api contract palette selector ----------

    private static void apiConsistency() {
        check("api consistency: defaults palette agree (vanilla)",
                WorldgenAssemblyApi.defaults().surfacePalette()
                        .equals(SurfacePaletteResolution.fromRules(Map.of()).palette()));
        Map<String, String> custom = Map.of(PALETTE_PROP, "custom");
        check("api consistency: custom selector agree (api == engine resolution)",
                WorldgenAssemblyApi.resolve(custom).surfacePalette()
                        .equals(SurfacePaletteResolution.fromRules(custom).palette()));
    }

    // ---------- (5) runtime seam wiring inventory (load-only, never initialize) ----------

    private static void wiringInventory() {
        check("wiring: SubterraSurfaceRules present (load-only)", classExists(SURFACE_RUNTIME));
        check("wiring: TYPE_ID literal 'subterra:surface_palette' in class bytes",
                classBytesContain(SURFACE_RUNTIME, "subterra:surface_palette"));
        check("wiring: gate property 'subterra.probe.surface' literal in class bytes",
                classBytesContain(SURFACE_RUNTIME, "subterra.probe.surface"));
        check("wiring: marker prefix '[Subterra surface]' literal in class bytes",
                classBytesContain(SURFACE_RUNTIME, "[Subterra surface]"));
        check("wiring: registers into Registries.MATERIAL_RULE (real chunk-gen surface path)",
                classBytesContain(SURFACE_RUNTIME, "MATERIAL_RULE"));
        check("wiring: shipped noise_settings surface_rule references subterra:surface_palette + base",
                resourceContains(NOISE_SETTINGS_RESOURCE, "subterra:surface_palette")
                        && resourceContains(NOISE_SETTINGS_RESOURCE, "\"base\""));
    }

    // ---------- helpers (load-only) ----------

    private static boolean classExists(String fqcn) {
        try {
            Class.forName(fqcn, false, SurfaceSeamProbe.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }

    private static byte[] classBytes(String fqcn) {
        String resource = "/" + fqcn.replace('.', '/') + ".class";
        try (InputStream in = SurfaceSeamProbe.class.getResourceAsStream(resource)) {
            return in == null ? null : in.readAllBytes();
        } catch (IOException e) {
            return null;
        }
    }

    private static boolean classBytesContain(String fqcn, String literal) {
        byte[] bytes = classBytes(fqcn);
        return bytes != null && new String(bytes, StandardCharsets.ISO_8859_1).contains(literal);
    }

    private static boolean resourceContains(String resource, String literal) {
        try (InputStream in = SurfaceSeamProbe.class.getResourceAsStream(resource)) {
            if (in == null) {
                return false;
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).contains(literal);
        } catch (IOException e) {
            return false;
        }
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
