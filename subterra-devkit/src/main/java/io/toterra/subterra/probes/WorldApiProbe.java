// p.2.33.10: world-pack domain contract mirror acceptance probe — asserts the API
// contract (api.world.WorldApi) and the engine mirror (engine.world.WorldApiMirror) are
// bit-for-bit identical on identical inputs, plus determinism re-entry and deterministic
// rejection of invalid inputs. Pure JVM: no wall-clock, no randomness, no MC classes.
package io.toterra.subterra.probes;

import io.toterra.subterra.api.world.WorldApi;
import io.toterra.subterra.engine.world.WorldApiMirror;

import java.nio.file.Path;
import java.util.List;

/**
 * p.2.33.10 世界包域契约镜像验收探针 —— 断言 api 层 {@link WorldApi} 与 engine 镜像 {@link WorldApiMirror}
 * 对同输入逐位一致（世界包顶层标记 / 格式版本 / 固定段序 version→meta→save→datapack / 路径穿越防护语义），
 * 并断言确定性再入与不合格输入的确定性拒绝（null 路径 → {@link NullPointerException}）。每项失败计数 +1 并
 * 给出诊断；全过才输出 {@code [WorldApiProbe] PASS (n checks)} 并 exit 0，否则 FAIL 计数 exit 1。全部线性
 * 遍历，无 O(n²)、无时序、无随机。
 * <p>
 * p.2.33.10 world-pack domain contract mirror probe: asserts the api-layer {@link WorldApi} and the engine
 * mirror {@link WorldApiMirror} are bit-for-bit identical for identical inputs (world-pack top-level marker /
 * format version / fixed section order version→meta→save→datapack / path-traversal guard semantics), plus
 * determinism re-entry and deterministic rejection of invalid inputs (null path →
 * {@link NullPointerException}). Every failure is counted and diagnosed; PASS only when all pass, then exit 0,
 * else FAIL with counts and exit 1. All traversals linear, no O(n²), no timing, no randomness.
 */
public final class WorldApiProbe {

    private static int checks = 0;
    private static int failures = 0;

    private WorldApiProbe() {
    }

    public static void main(String[] args) {
        markersAndSections();
        pathGuard();
        if (failures == 0) {
            System.out.println("[WorldApiProbe] PASS (" + checks + " checks)");
            System.exit(0);
        } else {
            System.out.println("[WorldApiProbe] FAIL (" + failures + " of " + checks + " checks)");
            System.exit(1);
        }
    }

    private static void markersAndSections() {
        check("worldMarker api == mirror", WorldApi.worldMarker().equals(WorldApiMirror.worldMarker()));
        check("worldMarker value", "world".equals(WorldApi.worldMarker()));
        check("version api == mirror", WorldApi.version() == WorldApiMirror.version());
        check("version value", WorldApi.version() == 1L);
        check("sectionKeys fixed order version/meta/save/datapack",
                List.of("version", "meta", "save", "datapack").equals(WorldApi.sectionKeys()));
        check("sectionKeys api == mirror", WorldApi.sectionKeys().equals(WorldApiMirror.sectionKeys()));
        check("sectionKeys deterministic re-entry", WorldApi.sectionKeys().equals(WorldApi.sectionKeys()));
    }

    private static void pathGuard() {
        Path tmp = Path.of(System.getProperty("java.io.tmpdir")).toAbsolutePath().normalize();
        Path target = tmp.resolve("subterra-probe-target");
        Path inside = target.resolve("ledger").resolve("doc.data.tie");
        Path escaping = target.resolve("..").resolve("subterra-probe-evil");
        Path sibling = tmp.resolve("subterra-probe-sibling");

        boolean a1 = WorldApi.pathWithin(target, inside);
        boolean a2 = WorldApiMirror.pathWithin(target, inside);
        check("pathWithin inside candidate -> true both sides", a1 && a1 == a2);
        check("pathWithin candidate == target -> true both sides",
                WorldApi.pathWithin(target, target) && WorldApiMirror.pathWithin(target, target));
        boolean e1 = WorldApi.pathWithin(target, escaping);
        boolean e2 = WorldApiMirror.pathWithin(target, escaping);
        check("pathWithin escaping .. candidate -> false api == mirror", !e1 && !e2);
        check("pathWithin sibling candidate -> false",
                !WorldApi.pathWithin(target, sibling));
        check("pathWithin deterministic re-entry",
                WorldApi.pathWithin(target, inside) == WorldApi.pathWithin(target, inside));
        check("pathWithin rejects null both sides",
                throwsNPE(() -> WorldApi.pathWithin(null, inside)) && throwsNPE(() -> WorldApiMirror.pathWithin(target, null)));
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

    private static boolean throwsNPE(Runnable r) {
        try {
            r.run();
            return false;
        } catch (NullPointerException e) {
            return true;
        }
    }
}