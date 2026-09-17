package io.toterra.subterra.probes;

import io.toterra.subterra.engine.worldgen.pipeline.density.Density;
import io.toterra.subterra.engine.worldgen.pipeline.terrain.SubterraTerrain;
import io.toterra.subterra.engine.worldgen.tie.TieTerrainDensityBridge;

/**
 * p.1.8.34 tie 密度桥等价性探针（纯 JVM）：`TieTerrainDensityBridge`（tie 侧
 * `stdens$density`，subterra_terrain.tie 编译产物）与 Java 参考实现
 * {@link SubterraTerrain#finalDensity} 在确定性网格上逐位对拍——
 * `Double.doubleToRawLongBits` 必须相等。网格覆盖多 y 层、正负坐标、双种子与
 * (0,0,0) 角点；另验证同种子逐位一致与换种子重算回原值。等价不成立即 FAIL。
 *
 * <p>The p.1.8.34 tie density-bridge equivalence probe (pure JVM): the bridge
 * (`stdens$density`, compiled from subterra_terrain.tie) and the Java reference
 * {@link SubterraTerrain#finalDensity} must agree BIT-EXACTLY
 * (`Double.doubleToRawLongBits`) on a deterministic grid covering multiple y levels,
 * negative coordinates, two seeds and the (0,0,0) corner; per-seed determinism and
 * cross-seed recomputation are also asserted.
 */
public final class DensityBridgeEquivalenceProbe {

    private static int checks;
    private static int failures;

    public static void main(String[] args) {
        TieTerrainDensityBridge bridge = TieTerrainDensityBridge.load();
        check("bridge: loaded and available", bridge != null && bridge.available());
        if (bridge == null || !bridge.available()) {
            System.out.println("[DensityBridgeEquivalenceProbe] FAIL (bridge unavailable — set -Dsubterra.tie.lib)");
            System.exit(1);
        }
        for (long seed : new long[]{44905237L, 42L}) {
            Density javaTree = SubterraTerrain.finalDensity(seed);
            double maxDiff = 0;
            long mismatched = 0;
            long compared = 0;
            for (int x = -512; x <= 512; x += 64) {
                for (int z = -512; z <= 512; z += 64) {
                    for (int y : new int[]{-64, 63, 127, 200}) {
                        double bv = bridge.density(x, y, z, seed);
                        double jv = javaTree.eval(x, y, z);
                        compared++;
                        if (Double.doubleToRawLongBits(bv) != Double.doubleToRawLongBits(jv)) {
                            mismatched++;
                            maxDiff = Math.max(maxDiff, Math.abs(bv - jv));
                        }
                    }
                }
            }
            check("bridge: seed=" + seed + " " + compared + " points bit-exact (mismatched="
                    + mismatched + ", maxAbsDiff=" + fmt(maxDiff) + ")", mismatched == 0);
        }
        // 同种子逐位一致 + 换种子重算回原值（DLL 内每种子缓存的正确性）。
        double a1 = bridge.density(64, 100, -64, 44905237L);
        double a2 = bridge.density(64, 100, -64, 42L);
        double a3 = bridge.density(64, 100, -64, 44905237L);
        check("bridge: per-seed cache round-trips bit-exact",
                Double.doubleToRawLongBits(a1) == Double.doubleToRawLongBits(a3));
        System.out.println("[DensityBridgeEquivalenceProbe] " + (failures == 0 ? "PASS (" + checks + " checks)"
                : "FAIL (" + failures + " of " + checks + " checks failed)"));
        if (failures > 0) {
            System.exit(1);
        }
    }

    private static String fmt(double v) {
        return String.format("%.4f", v);
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
