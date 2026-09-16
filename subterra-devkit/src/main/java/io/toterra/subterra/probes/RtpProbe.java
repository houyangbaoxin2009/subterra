package io.toterra.subterra.probes;

import io.toterra.subterra.engine.optim.server.teleport.RtpPlanner;

/**
 * p.2.31.1 确定性探针（纯 JVM）：RtpPlanner 规划核心 —— 缺省参数合法、非法值
 * 确定性拒绝、同输入同结果（再入深度相等）、环带半径界、表面回调次数 == 预算
 * （全拒时）、y_offset 语义、预算穷尽确定性 found=false、规范渲染逐字节再入、
 * runtime 壳接线盘点（marker / 门控 / 命令字面量，class-bytes）。禁时序断言。
 *
 * <p>The p.2.31.1 deterministic probe (pure JVM): the RtpPlanner core — default
 * params validity, deterministic rejection of illegal values, identical-input
 * identity (deep re-entry equality), annulus radius bounds, surface-callback
 * invocations == budget (when everything is rejected), y_offset semantics,
 * deterministic found=false on exhaustion, byte-identical canonical renders, and
 * the runtime-shell wiring inventory (marker / gate / command literals via
 * class bytes). No timing assertions.
 */
public final class RtpProbe {

    private static int checks;
    private static int failures;

    public static void main(String[] args) {
        paramsAndRender();
        rejections();
        determinism();
        radiusBounds();
        budgetAndSurfaceCalls();
        yOffsetAndFlatSurface();
        wiringInventory();
        System.out.println("[RtpProbe] " + (failures == 0 ? "PASS (" + checks + " checks)"
                : "FAIL (" + failures + " of " + checks + " checks failed)"));
        if (failures > 0) {
            System.exit(1);
        }
    }

    private static void paramsAndRender() {
        RtpPlanner.Params p = new RtpPlanner.Params(500, 5000, 64, -60, 320, 1);
        check("params: defaults valid", p.minRadius() == 500 && p.attempts() == 64);
        check("params: canonical render", p.render().equals(
                "min_radius=500 max_radius=5000 attempts=64 min_y=-60 max_y=320 y_offset=1"));
        check("params: render byte-identical re-entry", p.render().equals(p.render()));
    }

    private static void rejections() {
        reject("min_radius negative", () -> new RtpPlanner.Params(-1, 5000, 64, -60, 320, 1));
        reject("max_radius < min_radius", () -> new RtpPlanner.Params(500, 499, 64, -60, 320, 1));
        reject("attempts zero", () -> new RtpPlanner.Params(0, 5000, 0, -60, 320, 1));
        reject("min_y > max_y", () -> new RtpPlanner.Params(0, 5000, 64, 320, -60, 1));
        reject("y_offset out of range", () -> new RtpPlanner.Params(0, 5000, 64, -60, 320, 33));
    }

    private static void determinism() {
        RtpPlanner.Params p = new RtpPlanner.Params(500, 5000, 64, -60, 320, 1);
        RtpPlanner.SurfaceFn flat = (x, z) -> 64;
        RtpPlanner.Target a = RtpPlanner.locate(44905237L, 100, -200, p, flat);
        RtpPlanner.Target b = RtpPlanner.locate(44905237L, 100, -200, p, flat);
        check("determinism: identical inputs deep-equal", a.equals(b));
        check("determinism: found with flat surface", a.found() && a.y() == 65);
        RtpPlanner.Target c = RtpPlanner.locate(44905237L, 0, 0, p, flat);
        check("determinism: canonical render prefix", a.render().startsWith("rtp target x="));
        check("determinism: fork differs by center", !a.render().equals(c.render()));
    }

    private static void radiusBounds() {
        RtpPlanner.Params p = new RtpPlanner.Params(500, 5000, 256, -60, 320, 0);
        long seed = 12345678L;
        boolean allWithin = true;
        for (int cx = -1000; cx <= 1000; cx += 500) {
            for (int cz = -1000; cz <= 1000; cz += 500) {
                RtpPlanner.Target t = RtpPlanner.locate(seed, cx, cz, p, (x, z) -> 64);
                if (!t.found()) {
                    allWithin = false;
                    break;
                }
                long dx = t.x() - cx;
                long dz = t.z() - cz;
                long distSq = dx * dx + dz * dz;
                // 取整允许 ≤ √2 的偏移。 / Rounding allows a ≤ √2 drift.
                if (distSq > (long) (5000 + 2) * (5000 + 2) || distSq < (long) (500 - 2) * (500 - 2)) {
                    allWithin = false;
                }
            }
        }
        check("radius: every hit within [min, max] (± rounding)", allWithin);
    }

    private static void budgetAndSurfaceCalls() {
        RtpPlanner.Params p = new RtpPlanner.Params(100, 200, 7, -60, 320, 1);
        int[] calls = {0};
        RtpPlanner.Target t = RtpPlanner.locate(44905237L, 0, 0, p, (x, z) -> {
            calls[0]++;
            return -61; // 恒低于 min_y → 全拒。 / Always below min_y → all rejected.
        });
        check("budget: exhausted is deterministic found=false",
                !t.found() && t.attempts() == 7 && calls[0] == 7);
        check("budget: exhausted canonical render",
                t.render().equals("rtp exhausted attempts=7"));
    }

    private static void yOffsetAndFlatSurface() {
        RtpPlanner.Params p = new RtpPlanner.Params(0, 10, 4, 0, 320, 5);
        RtpPlanner.Target t = RtpPlanner.locate(42L, 0, 0, p, (x, z) -> 64);
        check("y_offset: applied above surface", t.found() && t.y() == 69);
    }

    private static void wiringInventory() {
        check("wiring: RtpRuntime present (load-only)", present("io.toterra.subterra.runtime.optim.server.teleport.shell.RtpRuntime"));
        check("wiring: RtpCommand present (load-only)", present("io.toterra.subterra.runtime.optim.server.teleport.shell.RtpCommand"));
        check("wiring: marker literal '[Subterra rtp]' in RtpRuntime bytes",
                classBytesContain("io.toterra.subterra.runtime.optim.server.teleport.shell.RtpRuntime", "[Subterra rtp]"));
        check("wiring: gate literal 'subterra.probe.rtp' in RtpRuntime bytes",
                classBytesContain("io.toterra.subterra.runtime.optim.server.teleport.shell.RtpRuntime", "subterra.probe.rtp"));
        check("wiring: salt literal 'subterra.rtp' in RtpPlanner bytes",
                classBytesContain("io.toterra.subterra.engine.optim.server.teleport.RtpPlanner", "subterra.rtp"));
    }

    private static void reject(String name, Runnable r) {
        try {
            r.run();
            check("reject: " + name, false);
        } catch (IllegalArgumentException e) {
            check("reject: " + name, e.getMessage() != null && !e.getMessage().isBlank());
        }
    }

    private static boolean present(String fqcn) {
        try {
            Class.forName(fqcn, false, RtpProbe.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }

    private static byte[] classBytes(String fqcn) {
        String resource = "/" + fqcn.replace('.', '/') + ".class";
        try (java.io.InputStream in = RtpProbe.class.getResourceAsStream(resource)) {
            return in == null ? null : in.readAllBytes();
        } catch (java.io.IOException e) {
            return null;
        }
    }

    private static boolean classBytesContain(String fqcn, String literal) {
        byte[] bytes = classBytes(fqcn);
        return bytes != null && new String(bytes, java.nio.charset.StandardCharsets.ISO_8859_1).contains(literal);
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
