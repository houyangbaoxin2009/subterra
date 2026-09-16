// p.2.29.3: 规则→特征/成矿真接缝的确定性验收探针（纯 JVM）。
// 引擎侧核心 FeatureAssembly 的解析/渲染/计划语义（规范序、母岩前置、矿脉护栏、缺省恒等、拒绝面）；
// 引擎复用几何 StructureFootprint 的重叠判定一致性；
// runtime 接缝 SubterraFeatures 的「只加载不初始化」接线盘点（FEATURE / PLACEMENT_MODIFIER_TYPE 注册 +
// gate + marker）与随附数据包（configured/placed feature + biome_modifier）挂载面核对。
// 纯 JVM：不初始化任何 runtime 壳、不碰 Minecraft 类；无时序、无随机；退出码 0 = 全过。
//
// p.2.29.3 deterministic acceptance probe for the rules→feature/ore real seam (pure JVM): the engine
// core FeatureAssembly (parse / render / plan semantics: canonical order, host-rock prerequisite,
// vein guard, default identity, rejection surface), the reused StructureFootprint overlap geometry,
// the runtime seam SubterraFeatures load-only wiring inventory (FEATURE / PLACEMENT_MODIFIER_TYPE
// registration + gate + marker) and the shipped datapack mount surface. Pure JVM: no runtime shell is
// initialized, no Minecraft class touched; no timing, no randomness; exit 0 = all passed.
package io.toterra.subterra.probes;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.toterra.subterra.engine.worldgen.feature.FeatureAssembly;
import io.toterra.subterra.engine.worldgen.guard.StructureFootprint;

/** p.2.29.3 rules→feature/ore seam probe. / p.2.29.3 规则→特征/成矿缝探针。 */
public final class FeatureSeamProbe {

    private static final String MP = FeatureAssembly.MINERAL_PREFIX;      // subterra.worldgen.feature.mineral.
    private static final String VP = FeatureAssembly.VEGETATION_PREFIX;   // subterra.worldgen.feature.vegetation.
    private static final String GB = FeatureAssembly.GUARD_BOX_PREFIX;    // subterra.worldgen.feature.guard.box.
    private static final String GC = FeatureAssembly.RULE_GUARD_CLEARANCE;
    private static final String EN = FeatureAssembly.RULE_ENABLE;

    private static final String FEATURE_RUNTIME =
            "io.toterra.subterra.runtime.worldgen.gen.SubterraFeatures";

    private static final String CFG_RESOURCE = "/data/subterra/worldgen/configured_feature/rule_ore_iron.json";
    private static final String PLACED_RESOURCE = "/data/subterra/worldgen/placed_feature/rule_ore_iron.json";
    private static final String MODIFIER_RESOURCE =
            "/data/subterra/neoforge/biome_modifier/rule_ore_iron.json";

    private static int checks = 0;
    private static int failures = 0;

    private FeatureSeamProbe() {
    }

    public static void main(String[] args) {
        try {
            defaultIdentity();
            planDeterminismAndOrder();
            hostRockPrerequisite();
            veinGuardNonOverlap();
            rejectionSurface();
            wiringInventory();
        } catch (Exception e) {
            failures++;
            System.out.println("[FAIL] probe exception: " + e);
            e.printStackTrace(System.out);
        }
        if (failures == 0) {
            System.out.println("[FeatureSeamProbe] PASS (" + checks + " checks)");
            System.exit(0);
        } else {
            System.out.println("[FeatureSeamProbe] FAIL (" + failures + " of " + checks + " checks)");
            System.exit(1);
        }
    }

    // ---------- (1) default identity (off = zero behaviour change) ----------

    private static void defaultIdentity() {
        FeatureAssembly.Plan plan = FeatureAssembly.of(Map.of());
        check("default: empty rules is identity (off, no mounts/guards/clearance)", plan.isIdentity());
        check("default: mounts() empty", plan.mounts().isEmpty());
        check("default: blocked(0,0) == false (nothing guarded)", !plan.blocked(0, 0));
        check("default: null rules == DEFAULT_PLAN", plan.equals(FeatureAssembly.DEFAULT_PLAN));
        check("default: canonical render 'enable=false; guard_clearance=0; minerals=[]; vegetation=[]; guards=[]'",
                "enable=false; guard_clearance=0; minerals=[]; vegetation=[]; guards=[]"
                        .equals(FeatureAssembly.render(plan)));
    }

    // ---------- (2) determinism + canonical order ----------

    private static void planDeterminismAndOrder() {
        Map<String, String> rules = Map.of(
                EN, "true",
                MP + "zinc.block", "minecraft:iron_ore",
                MP + "alpha.block", "minecraft:gold_ore",
                VP + "reed.block", "minecraft:sugar_cane");
        FeatureAssembly.Plan p1 = FeatureAssembly.of(rules);
        FeatureAssembly.Plan p2 = FeatureAssembly.of(rules);
        check("determinism: same table → equal plan", p1.equals(p2));
        check("determinism: render byte-identical on re-entry",
                FeatureAssembly.render(p1).equals(FeatureAssembly.render(p2)));
        List<String> mountOrder = new ArrayList<>();
        for (FeatureAssembly.Mount m : p1.mounts()) {
            mountOrder.add(m.group() + ":" + m.id());
        }
        check("order: mounts = minerals (id order) first, vegetation after [mineral:alpha, mineral:zinc, vegetation:reed]",
                List.of("mineral:alpha", "mineral:zinc", "vegetation:reed").equals(mountOrder));
        check("order: plan not identity when enabled", !p1.isIdentity());
        check("order: mineral lookup by id works", p1.mineral("alpha").isPresent()
                && p1.mineral("absent").isEmpty());
    }

    // ---------- (3) mineral × host-rock prerequisite ----------

    private static void hostRockPrerequisite() {
        FeatureAssembly.HostRock hr =
                FeatureAssembly.HostRock.parse("minecraft:stone,#subterra:rocks,minecraft:stone");
        check("host_rock: blocks deduped + sorted [minecraft:stone]",
                List.of("minecraft:stone").equals(hr.blocks()));
        check("host_rock: tags split to [#subterra:rocks -> subterra:rocks]",
                List.of("subterra:rocks").equals(hr.tags()));
        check("host_rock: canonical render 'minecraft:stone,#subterra:rocks'",
                "minecraft:stone,#subterra:rocks".equals(hr.render()));
        check("host_rock: not 'any' when constrained", !hr.isAny());
        check("host_rock: blank → any", FeatureAssembly.HostRock.parse("  ").isAny()
                && FeatureAssembly.HostRock.any().isAny());
        check("host_rock: bare '#' tag rejected", rejectsHostRock("#"));
    }

    private static boolean rejectsHostRock(String raw) {
        try {
            FeatureAssembly.HostRock.parse(raw);
            return false;
        } catch (IllegalArgumentException expected) {
            return true;
        }
    }

    // ---------- (4) vein guard non-overlap ----------

    private static void veinGuardNonOverlap() {
        FeatureAssembly.Plan noClearance = FeatureAssembly.of(Map.of(
                GB + "core.box", "0,0,16,16",
                GC, "0"));
        check("guard: box 0,0,16x16 blocks (0,0)", noClearance.blocked(0, 0));
        check("guard: blocks the inclusive edge (15,15)", noClearance.blocked(15, 15));
        check("guard: does not block (16,16)", !noClearance.blocked(16, 16));

        FeatureAssembly.Plan globalClearance = FeatureAssembly.of(Map.of(
                GB + "core.box", "0,0,16,16",
                GC, "4"));
        check("guard: global clearance 4 blocks (19,19)", globalClearance.blocked(19, 19));
        check("guard: global clearance 4 does not block (20,20)", !globalClearance.blocked(20, 20));

        FeatureAssembly.Plan twoBoxes = FeatureAssembly.of(Map.of(
                GB + "a.box", "0,0,16,16",
                GB + "b.box", "8,8,16,16"));
        check("guard: overlapping boxes reported as ['a x b']",
                List.of("a x b").equals(FeatureAssembly.guardConflicts(twoBoxes)));
        check("guard: overlap geometry agrees with StructureFootprint.overlaps",
                StructureFootprint.overlaps(
                        new StructureFootprint("a", 0, 0, 16, 16, 0),
                        new StructureFootprint("b", 8, 8, 16, 16, 0)));

        FeatureAssembly.Plan disjoint = FeatureAssembly.of(Map.of(
                GB + "a.box", "0,0,4,4",
                GB + "b.box", "100,100,4,4"));
        check("guard: disjoint boxes have no conflicts", FeatureAssembly.guardConflicts(disjoint).isEmpty());
    }

    // ---------- (5) rejection surface (all-or-nothing) ----------

    private static void rejectionSurface() {
        check("reject: unknown mineral field", rejects(Map.of(MP + "x.block", "minecraft:iron_ore",
                MP + "x.bogus", "1")));
        check("reject: count out of range (65)", rejects(Map.of(MP + "x.block", "minecraft:iron_ore",
                MP + "x.count", "65")));
        check("reject: unknown vein_trend 'spiral'", rejects(Map.of(MP + "x.block", "minecraft:iron_ore",
                MP + "x.vein_trend", "spiral")));
        check("reject: blank mineral block id", rejects(Map.of(MP + "x.block", "   ")));
        check("reject: mineral missing block field", rejects(Map.of(MP + "x.count", "3")));
        check("reject: guard box wrong arity (1,2,3)", rejects(Map.of(GB + "g.box", "1,2,3")));
        check("reject: min_y > max_y", rejects(Map.of(MP + "x.block", "minecraft:iron_ore",
                MP + "x.min_y", "10", MP + "x.max_y", "5")));
        check("reject: non-boolean enable", rejects(Map.of(EN, "yes")));
        check("reject: negative guard clearance", rejects(Map.of(GC, "-1")));
    }

    private static boolean rejects(Map<String, String> rules) {
        try {
            FeatureAssembly.of(rules);
            return false;
        } catch (IllegalArgumentException expected) {
            return true;
        }
    }

    // ---------- (6) runtime seam wiring inventory (load-only, never initialize) ----------

    private static void wiringInventory() {
        check("wiring: SubterraFeatures present (load-only)", classExists(FEATURE_RUNTIME));
        check("wiring: marker prefix '[Subterra feature]' literal in class bytes",
                classBytesContain(FEATURE_RUNTIME, "[Subterra feature]"));
        check("wiring: gate property 'subterra.probe.feature' literal in class bytes",
                classBytesContain(FEATURE_RUNTIME, "subterra.probe.feature"));
        check("wiring: rule-ore feature id 'subterra:rule_ore' literal in class bytes",
                classBytesContain(FEATURE_RUNTIME, "subterra:rule_ore"));
        check("wiring: rule-vein placer id 'subterra:rule_vein' literal in class bytes",
                classBytesContain(FEATURE_RUNTIME, "subterra:rule_vein"));
        check("wiring: registers into PLACEMENT_MODIFIER_TYPE (real generation path)",
                classBytesContain(FEATURE_RUNTIME, "PLACEMENT_MODIFIER_TYPE"));
        check("wiring: configured_feature datapack uses subterra:rule_ore",
                resourceContains(CFG_RESOURCE, "subterra:rule_ore"));
        check("wiring: placed_feature datapack uses subterra:rule_vein + subterra:rule_ore_iron",
                resourceContains(PLACED_RESOURCE, "subterra:rule_vein")
                        && resourceContains(PLACED_RESOURCE, "subterra:rule_ore_iron"));
        check("wiring: biome_modifier mounts subterra:rule_ore_iron via neoforge:add_features",
                resourceContains(MODIFIER_RESOURCE, "neoforge:add_features")
                        && resourceContains(MODIFIER_RESOURCE, "subterra:rule_ore_iron"));
    }

    // ---------- helpers (load-only) ----------

    private static boolean classExists(String fqcn) {
        try {
            Class.forName(fqcn, false, FeatureSeamProbe.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }

    private static byte[] classBytes(String fqcn) {
        String resource = "/" + fqcn.replace('.', '/') + ".class";
        try (InputStream in = FeatureSeamProbe.class.getResourceAsStream(resource)) {
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
        try (InputStream in = FeatureSeamProbe.class.getResourceAsStream(resource)) {
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
