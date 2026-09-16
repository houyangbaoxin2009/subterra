// p.2.35 / p.2.29.4: Trimand 三场装配面 + 世界生成装配单一入口的确定性验收探针（纯 JVM）。
// 引擎侧 TrimandAssembly（三场 → 密度提示融合）+ AssemblySeam（一次解析密度面 + Trimand 面，
// 六键固定规范序、整表 all-or-nothing 拒绝、缺省逐位恒等）+ DensityAssembly 组合语义；
// tie 侧 TrimandBridge 的 FFM ok/skip 两路径（缺 DLL 时确定性 skip 且不改变真值）；
// runtime 壳 SubterraTrimand / SubterraWorldgen 启动快照的「只加载不初始化」接线盘点。
// 纯 JVM：调用 runtime 壳一律只加载不初始化；无时序、无随机；退出码 0 = 全过。
//
// p.2.35 / p.2.29.4 deterministic acceptance probe for the Trimand three-field assembly surface and the
// single worldgen-assembly entry (pure JVM): the engine TrimandAssembly (three-field → density-hint
// blend) + AssemblySeam (one parse of the density + Trimand carriers, six keys in fixed canonical
// order, whole-table all-or-nothing rejection, bit-identical identity default) + the DensityAssembly
// composition; the tie TrimandBridge FFM ok/skip two paths (a missing DLL is a deterministic skip that
// never changes the truth); the runtime shells SubterraTrimand / SubterraWorldgen boot-snapshot
// load-only wiring inventory. Pure JVM: runtime shells are loaded, never initialized; no timing, no
// randomness; exit 0 = all passed.
package io.toterra.subterra.probes;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import io.toterra.subterra.engine.worldgen.assembly.AssemblySeam;
import io.toterra.subterra.engine.worldgen.assembly.DensityAssembly;
import io.toterra.subterra.engine.worldgen.assembly.TrimandAssembly;
import io.toterra.subterra.tie.TrimandBridge;
import io.toterra.subterra.tie.TrimandBridgeException;

/** p.2.35 / p.2.29.4 Trimand seam probe. / p.2.35 / p.2.29.4 Trimand 缝探针。 */
public final class TrimandSeamProbe {

    /** Fixed deterministic seed (no randomness). / 固定确定性种子（无随机）。 */
    private static final long SEED = 20260915L;

    private static final String TRIMAND_RUNTIME =
            "io.toterra.subterra.runtime.worldgen.gen.SubterraTrimand";
    private static final String WORLDGEN_RUNTIME =
            "io.toterra.subterra.runtime.worldgen.gen.SubterraWorldgen";

    private static int checks = 0;
    private static int failures = 0;

    private TrimandSeamProbe() {
    }

    public static void main(String[] args) {
        try {
            trimandDefaultsAndDeterminism();
            trimandRejectionAndFields();
            assemblySeamComposition();
            bridgeOkSkipTwoPaths();
            runtimeWiringInventory();
        } catch (Exception e) {
            failures++;
            System.out.println("[FAIL] probe exception: " + e);
            e.printStackTrace(System.out);
        }
        if (failures == 0) {
            System.out.println("[TrimandSeamProbe] PASS (" + checks + " checks)");
            System.exit(0);
        } else {
            System.out.println("[TrimandSeamProbe] FAIL (" + failures + " of " + checks + " checks)");
            System.exit(1);
        }
    }

    // ---------- (1) TrimandAssembly defaults + determinism + apply semantics ----------

    private static void trimandDefaultsAndDeterminism() {
        check("trimand default: empty rules == DEFAULT_PARAMS (off, weight 0) and identity",
                TrimandAssembly.of(Map.of()).equals(TrimandAssembly.DEFAULT_PARAMS)
                        && TrimandAssembly.isIdentity(TrimandAssembly.DEFAULT_PARAMS));
        TrimandAssembly.Fields fields = new TrimandAssembly.Fields(0.5, -0.25, 0.75);
        check("trimand default: apply() passes the raw density through bit-for-bit (skip = truth unchanged)",
                bits(TrimandAssembly.apply(TrimandAssembly.DEFAULT_PARAMS, 3.25, fields))
                        == bits(3.25));

        Map<String, String> rules = Map.of(
                TrimandAssembly.RULE_TRIMAND_ENABLED, "true",
                TrimandAssembly.RULE_TRIMAND_WEIGHT, "0.5");
        TrimandAssembly.Params p1 = TrimandAssembly.of(rules);
        TrimandAssembly.Params p2 = TrimandAssembly.of(rules);
        check("trimand resolve: deterministic re-entry (equal params)", p1.equals(p2));
        check("trimand resolve: canonical render 'trimand_enabled=true; trimand_weight=0.5'",
                "trimand_enabled=true; trimand_weight=0.5".equals(TrimandAssembly.render(p1))
                        && TrimandAssembly.render(p1).equals(TrimandAssembly.render(p2)));
        check("trimand resolve: enabled + weight > 0 is NOT identity", !TrimandAssembly.isIdentity(p1));

        // non-finite raw density → deterministic pass-through (never throws, never fabricates).
        check("trimand apply: non-finite raw passes through",
                Double.isNaN(TrimandAssembly.apply(p1, Double.NaN, fields))
                        && Double.isInfinite(TrimandAssembly.apply(p1, Double.POSITIVE_INFINITY, fields)));

        // apply() = raw + weight * hint(fields); hint(0,1,0) = 0.5 ⇒ 1.0 + 0.5*0.5 = 1.25.
        TrimandAssembly.Params half = TrimandAssembly.of(Map.of(
                TrimandAssembly.RULE_TRIMAND_ENABLED, "true",
                TrimandAssembly.RULE_TRIMAND_WEIGHT, "0.5"));
        check("trimand apply: raw + weight*hint == 1.25 for raw=1.0, weight=0.5, fields(0,1,0)",
                TrimandAssembly.apply(half, 1.0, new TrimandAssembly.Fields(0.0, 1.0, 0.0)) == 1.25);
    }

    private static void trimandRejectionAndFields() {
        check("trimand fields: out-of-range hard-clamped to [-1,1] (2.0,-2.0,0.5)",
                new TrimandAssembly.Fields(2.0, -2.0, 0.5).equals(new TrimandAssembly.Fields(1.0, -1.0, 0.5)));
        check("trimand fields: non-finite rejected", rejectsFields(Double.NaN, 0, 0));
        check("trimand hint: canonical blend 0.25*clim + 0.50*elev + 0.25*mtn",
                TrimandAssembly.hint(new TrimandAssembly.Fields(1.0, 0.0, 0.0)) == 0.25
                        && TrimandAssembly.hint(new TrimandAssembly.Fields(0.0, 1.0, 0.0)) == 0.5
                        && TrimandAssembly.hint(new TrimandAssembly.Fields(0.0, 0.0, 1.0)) == 0.25);
        check("trimand reject: blank boolean", rejectsTrimand(Map.of(
                TrimandAssembly.RULE_TRIMAND_ENABLED, "  ")));
        check("trimand reject: non-boolean", rejectsTrimand(Map.of(
                TrimandAssembly.RULE_TRIMAND_ENABLED, "maybe")));
        check("trimand reject: weight NaN", rejectsTrimand(Map.of(
                TrimandAssembly.RULE_TRIMAND_WEIGHT, "NaN")));
        check("trimand reject: weight < 0", rejectsTrimand(Map.of(
                TrimandAssembly.RULE_TRIMAND_WEIGHT, "-0.1")));
        check("trimand reject: weight > 1", rejectsTrimand(Map.of(
                TrimandAssembly.RULE_TRIMAND_WEIGHT, "1.1")));
    }

    private static boolean rejectsFields(double c, double e, double m) {
        try {
            new TrimandAssembly.Fields(c, e, m);
            return false;
        } catch (IllegalArgumentException expected) {
            return true;
        }
    }

    private static boolean rejectsTrimand(Map<String, String> rules) {
        try {
            TrimandAssembly.of(rules);
            return false;
        } catch (IllegalArgumentException expected) {
            return true;
        }
    }

    // ---------- (2) AssemblySeam: one parse, one render, one apply ----------

    private static void assemblySeamComposition() {
        check("seam: RULE_KEYS = six keys in fixed canonical order (four density + two trimand)",
                List.of("subterra.worldgen.density_offset", "subterra.worldgen.density_scale",
                        "subterra.worldgen.surface_palette", "subterra.worldgen.ore_density",
                        "subterra.worldgen.trimand_enabled", "subterra.worldgen.trimand_weight")
                        .equals(AssemblySeam.RULE_KEYS));

        check("seam default: empty rules identity", AssemblySeam.of(Map.of()).equals(AssemblySeam.DEFAULTS)
                && AssemblySeam.isIdentity(AssemblySeam.of(Map.of())));
        check("seam default: null rules identity", AssemblySeam.isIdentity(AssemblySeam.of(null)));

        Map<String, String> six = Map.of(
                DensityAssembly.RULE_DENSITY_OFFSET, "2.0",
                DensityAssembly.RULE_DENSITY_SCALE, "1.5",
                DensityAssembly.RULE_SURFACE_PALETTE, "custom",
                DensityAssembly.RULE_ORE_DENSITY, "3.0",
                TrimandAssembly.RULE_TRIMAND_ENABLED, "true",
                TrimandAssembly.RULE_TRIMAND_WEIGHT, "0.5");
        AssemblySeam.Resolved r1 = AssemblySeam.of(six);
        AssemblySeam.Resolved r2 = AssemblySeam.of(six);
        check("seam resolve: deterministic re-entry", r1.equals(r2));
        String expected = "density_offset=2.0; density_scale=1.5; surface_palette=custom; ore_density=3.0; "
                + "trimand_enabled=true; trimand_weight=0.5";
        check("seam resolve: combined canonical render [" + expected + "]",
                expected.equals(AssemblySeam.render(r1)));
        check("seam resolve: combined render byte-identical on re-entry",
                AssemblySeam.render(r1).equals(AssemblySeam.render(r2)));
        check("seam resolve: non-identity when overridden", !AssemblySeam.isIdentity(r1));

        // all-or-nothing: a single invalid key rejects the WHOLE table.
        Map<String, String> oneBad = Map.of(
                DensityAssembly.RULE_DENSITY_OFFSET, "2.0",
                DensityAssembly.RULE_DENSITY_SCALE, "9.0");
        check("seam reject: any single invalid value rejects the whole table (all-or-nothing)",
                rejectsSeam(oneBad));

        // applyDensity under DEFAULTS is bit-identical to the raw density.
        check("seam applyDensity: DEFAULTS bit-identical to raw",
                bits(AssemblySeam.applyDensity(AssemblySeam.DEFAULTS, -8.0,
                        new TrimandAssembly.Fields(0.3, 0.4, 0.5))) == bits(-8.0));
        // 1.0*1.5+2.0 = 3.5; hint(0,1,0)=0.5, +0.5*0.5 = 3.75.
        check("seam applyDensity: density then trimand == 3.75 (raw 1.0, offset 2, scale 1.5, weight 0.5)",
                AssemblySeam.applyDensity(r1, 1.0, new TrimandAssembly.Fields(0.0, 1.0, 0.0)) == 3.75);
    }

    private static boolean rejectsSeam(Map<String, String> rules) {
        try {
            AssemblySeam.of(rules);
            return false;
        } catch (IllegalArgumentException expected) {
            return true;
        }
    }

    // ---------- (3) TrimandBridge FFM ok / skip two paths ----------

    private static void bridgeOkSkipTwoPaths() {
        check("bridge: marker == '[Subterra trimand]'", "[Subterra trimand]".equals(TrimandBridge.MARKER));

        // OK path: the bundled dll is present on the acceptance classpath. The bridge's own
        // deterministic report() is the canonical ok/skip surface (never timing-based, never throws).
        String report1 = TrimandBridge.report();
        String report2 = TrimandBridge.report();
        check("bridge ok: deterministic report() starts with '[Subterra trimand] ok' (bundled dll loaded)",
                report1.startsWith("[Subterra trimand] ok (dll="));
        check("bridge ok: report() byte-identical on re-entry (determinism, no timing)",
                report1.equals(report2));

        TrimandBridge bridge = TrimandBridge.locate().orElse(null);
        check("bridge ok: bundled Trimand dll located (deterministic load)", bridge != null);
        if (bridge != null) {
            try {
                double c1 = bridge.coarseClimate(100, 200, SEED);
                double e1 = bridge.coarseElev(100, 200, SEED);
                double m1 = bridge.coarseMountain(100, 200, SEED);
                double c2 = bridge.coarseClimate(100, 200, SEED);
                double e1b = bridge.coarseElev(100, 200, SEED);
                double e2 = bridge.coarseElev(5120, 8192, SEED);
                check("bridge ok: coarse_climate deterministic (same x,z,seed → same value)",
                        bits(c1) == bits(c2));
                check("bridge ok: coarse_elev deterministic (same x,z,seed → same value)",
                        bits(e1) == bits(e1b));
                check("bridge ok: three coarse fields finite and within the bridge envelope ±4.0",
                        finite(c1) && finite(e1) && finite(m1)
                                && Math.abs(c1) <= 4.0 && Math.abs(e1) <= 4.0 && Math.abs(m1) <= 4.0);
                check("bridge ok: coarse_elev finite at a different point (5120,8192)", finite(e2));
                // The assembly boundary normalises whatever the FFM layer returns into [-1,1].
                TrimandAssembly.Fields f = new TrimandAssembly.Fields(c1, e1, m1);
                check("bridge ok: assembly boundary accepts the FFM triple and clamps into [-1,1]",
                        f.climate() >= -1.0 && f.climate() <= 1.0
                                && f.elevation() >= -1.0 && f.elevation() <= 1.0
                                && f.mountain() >= -1.0 && f.mountain() <= 1.0);
            } finally {
                bridge.close();
            }
        }

        // SKIP path: a missing dll file is a deterministic rejection (TrimandBridgeException) — the
        // "no dll" branch that must leave the density truth untouched (proven by the identity
        // pass-through assertions above).
        Path absent = Path.of(System.getProperty("java.io.tmpdir"), "subterra_toterra_absent.dll");
        check("bridge skip: loading a missing dll throws TrimandBridgeException (deterministic 'no dll')",
                rejectsLoad(absent));
    }

    private static boolean rejectsLoad(Path dll) {
        try {
            TrimandBridge.load(dll).close();
            return false;
        } catch (TrimandBridgeException expected) {
            return true;
        } catch (RuntimeException other) {
            return false;
        }
    }

    // ---------- (4) runtime seam wiring inventory (load-only, never initialize) ----------

    private static void runtimeWiringInventory() {
        check("wiring: SubterraTrimand present (load-only)", classExists(TRIMAND_RUNTIME));
        check("wiring: SubterraTrimand marker '[Subterra trimand]' literal in class bytes",
                classBytesContain(TRIMAND_RUNTIME, "[Subterra trimand]"));
        check("wiring: SubterraTrimand deterministic skip branches ('skip (disabled' / 'skip (no dll') present",
                classBytesContain(TRIMAND_RUNTIME, "skip (disabled")
                        && classBytesContain(TRIMAND_RUNTIME, "skip (no dll"));
        check("wiring: SubterraWorldgen boot-snapshot marker '[Subterra assembly]' literal in class bytes",
                classBytesContain(WORLDGEN_RUNTIME, "[Subterra assembly]"));
        check("wiring: SubterraWorldgen gate 'subterra.probe.assembly' literal in class bytes",
                classBytesContain(WORLDGEN_RUNTIME, "subterra.probe.assembly"));
    }

    // ---------- helpers (load-only) ----------

    private static boolean finite(double v) {
        return Double.isFinite(v);
    }

    private static long bits(double v) {
        return Double.doubleToLongBits(v);
    }

    private static boolean classExists(String fqcn) {
        try {
            Class.forName(fqcn, false, TrimandSeamProbe.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }

    private static byte[] classBytes(String fqcn) {
        String resource = "/" + fqcn.replace('.', '/') + ".class";
        try (InputStream in = TrimandSeamProbe.class.getResourceAsStream(resource)) {
            return in == null ? null : in.readAllBytes();
        } catch (IOException e) {
            return null;
        }
    }

    private static boolean classBytesContain(String fqcn, String literal) {
        byte[] bytes = classBytes(fqcn);
        return bytes != null && new String(bytes, StandardCharsets.ISO_8859_1).contains(literal);
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
