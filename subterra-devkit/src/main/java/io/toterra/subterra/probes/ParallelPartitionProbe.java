// Deterministic acceptance probe for the p.2.7.1 generic key partition base
// (io.toterra.subterra.engine.parallel). NOT shipped in the mod jar.
package io.toterra.subterra.probes;

import io.toterra.subterra.engine.parallel.KeyPartition;
import io.toterra.subterra.engine.worldgen.async.ChunkPartition;

/**
 * Deterministic acceptance probe for the p.2.7.1 generic deterministic key
 * partition base (io.toterra.subterra.engine.parallel). Asserts bankCount
 * normalization, index range, key determinism, per-bank coverage, and the
 * p.2.6 ChunkPartition interoperability (bit-for-bit identical dual-long
 * mapping). Every check is a final-state deterministic assertion — never a
 * wall-clock/timing assertion. Exit 0 = PASS, 1 = FAIL (never shipped in the
 * mod jar).
 *
 * <p>p.2.7.1 通用确定性键分区底座（io.toterra.subterra.engine.parallel）的确定性验收
 * 探针。断言 bankCount 归一化、索引范围、键确定性、逐 bank 覆盖，以及与 p.2.6
 * ChunkPartition 的互操作（双 long 映射逐位一致）。所有检查都是终态确定性断言，绝不
 * 依赖墙钟/时序。退出码 0 = PASS，1 = FAIL（永不随 mod jar 发布）。
 */
public final class ParallelPartitionProbe {

    private ParallelPartitionProbe() {
    }

    private static int failures = 0;
    private static int checks = 0;

    private static void check(String name, boolean ok) {
        checks++;
        if (ok) {
            System.out.println("[PASS] " + name);
        } else {
            failures++;
            System.out.println("[FAIL] " + name);
        }
    }

    /** Stable immutable value-object key (record: equals/hashCode contract fixed). */
    private record P(int a, int b) {
    }

    public static void main(String[] args) {
        // ---- 1. bankCount normalization -----------------------------------------
        int[] desired = {0, -3, 1, 2, 3, 4, 5, 7, 8, 9, 16};
        int[] expected = {1, 1, 1, 2, 4, 4, 8, 8, 8, 8, 8};
        boolean norm = true;
        for (int i = 0; i < desired.length; i++) {
            int b = KeyPartition.bankCount(desired[i]);
            if (b < 1 || b > KeyPartition.MAX_BANKS || (b & (b - 1)) != 0 || b != expected[i]) {
                norm = false;
            }
        }
        check("bankCount: desired {0,-3,1,2,3,4,5,7,8,9,16} -> expected {1,1,1,2,4,4,8,8,8,8,8},"
                + " all in [1,8] powers of two (identical to ChunkPartition.bankCount)", norm);

        // ---- 2. range: all three overloads stay in [0, bankCount) ----------------
        boolean range = true;
        for (int b : new int[]{1, 2, 4, 8}) {
            for (long v = 0; v < 256 && range; v++) {
                int i = KeyPartition.bankIndex(v, b);
                if (i < 0 || i >= b) {
                    range = false;
                }
            }
            for (long x = 0; x < 128 && range; x++) {
                for (long z = 0; z < 128 && range; z++) {
                    int i = KeyPartition.bankIndex(x, z, b);
                    if (i < 0 || i >= b) {
                        range = false;
                    }
                }
            }
            for (int i = 0; i < 512 && range; i++) {
                int j = KeyPartition.bankIndex("key-" + i, b);
                if (j < 0 || j >= b) {
                    range = false;
                }
            }
        }
        check("range: long-hash / dual-long / Object overloads all stay in [0, bankCount)", range);

        // ---- 3. determinism: two independent calls map identically ---------------
        boolean det = true;
        for (int b : new int[]{2, 4, 8}) {
            for (long v = 0; v < 256 && det; v++) {
                if (KeyPartition.bankIndex(v, b) != KeyPartition.bankIndex(v, b)) {
                    det = false;
                }
            }
            for (long x = -64; x < 64 && det; x++) {
                for (long z = -64; z < 64 && det; z++) {
                    if (KeyPartition.bankIndex(x, z, b) != KeyPartition.bankIndex(x, z, b)) {
                        det = false;
                    }
                }
            }
            for (int i = 0; i < 512 && det; i++) {
                Object key = (i % 2 == 0) ? ("key-" + i) : new P(i % 64, i % 64);
                if (KeyPartition.bankIndex(key, b) != KeyPartition.bankIndex(key, b)) {
                    det = false;
                }
            }
        }
        check("determinism: repeated independent calls map identical (all three overloads)", det);

        // ---- 4. coverage: every bank hit for bankCount in {1,2,4,8} --------------
        boolean cov = true;
        for (int b : new int[]{1, 2, 4, 8}) {
            boolean[] seenHash = new boolean[b]; // long-hash path: 0..255
            for (long v = 0; v < 256; v++) {
                seenHash[KeyPartition.bankIndex(v, b)] = true;
            }
            boolean[] seenDual = new boolean[b]; // dual-long path: [0,64)x[0,64)
            for (long x = 0; x < 64; x++) {
                for (long z = 0; z < 64; z++) {
                    seenDual[KeyPartition.bankIndex(x, z, b)] = true;
                }
            }
            boolean[] seenObj = new boolean[b]; // Object path: strings + record grid
            for (int i = 0; i < 256; i++) {
                seenObj[KeyPartition.bankIndex("k" + i, b)] = true;
            }
            for (int a = 0; a < 64; a++) {
                for (int c = 0; c < 64; c++) {
                    seenObj[KeyPartition.bankIndex(new P(a, c), b)] = true;
                }
            }
            for (int i = 0; i < b; i++) {
                if (!seenHash[i] || !seenDual[i] || !seenObj[i]) {
                    cov = false;
                }
            }
        }
        check("coverage: every bank hit at bankCount in {1,2,4,8}"
                + " (long-hash 0..255 / dual-long [0,64)^2 / Object strings+record)", cov);

        // ---- 5. interop: bit-for-bit identical to p.2.6 ChunkPartition -----------
        boolean interop = true;
        for (int b : new int[]{2, 4, 8}) {
            for (long x = 0; x < 128 && interop; x++) {
                for (long z = 0; z < 128 && interop; z++) {
                    if (KeyPartition.bankIndex(x, z, b) != ChunkPartition.bankIndex(x, z, b)) {
                        interop = false;
                    }
                }
            }
        }
        check("interop: KeyPartition.bankIndex(x,z,b) == ChunkPartition.bankIndex(x,z,b)"
                + " for (x,z) in [0,128)^2, b in {2,4,8}", interop);

        if (failures == 0) {
            System.out.println("[ParallelPartitionProbe] PASS (" + checks + " checks)");
            System.exit(0);
        } else {
            System.out.println("[ParallelPartitionProbe] FAIL: " + failures + " assertion(s) of " + checks);
            System.exit(1);
        }
    }
}
