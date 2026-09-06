package io.toterra.subterra.probes;

import io.toterra.subterra.optim.worldgen.ticking.TickingChunkCache;

import java.util.ArrayList;
import java.util.List;

/**
 * Deterministic acceptance probe for the ticking-chunk cache core (p.1.4.11,
 * ServerCore "cache-ticking-chunks" concept re-keyed clean-room): snapshot
 * freshness, 1-second refresh boundaries, delayed enter/leave, immutability
 * and bounded lookup cost. Pure JVM.
 */
public final class TickingChunkCacheProbe {

    private TickingChunkCacheProbe() {
    }

    private static int failures = 0;

    private static void check(String name, boolean ok) {
        if (ok) {
            System.out.println("[PASS] " + name);
        } else {
            failures++;
            System.out.println("[FAIL] " + name);
        }
    }

    private static long pos(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }

    public static void main(String[] args) {
        TickingChunkCache c = new TickingChunkCache();

        // Empty cache: nothing ticking.
        check("empty cache", c.size() == 0 && !c.isTicking(pos(0, 0)));

        // Feed chunk (0,0); first update always builds the snapshot.
        c.update(0, List.of(pos(0, 0)));
        check("first update snapshots", c.isTicking(pos(0, 0)) && c.size() == 1);

        // New chunk (1,0) appears during the interval: not visible until the
        // next refresh boundary (tick 20).
        c.update(1, List.of(pos(0, 0), pos(1, 0)));
        c.update(2, List.of(pos(0, 0), pos(1, 0)));
        check("enter hidden within interval", !c.isTicking(pos(1, 0)));
        check("old snapshot retained", c.isTicking(pos(0, 0)));

        // Chunk (1,0) runs inside the interval: stays visible until rebuild.
        c.update(19, List.of(pos(1, 0)));
        check("leave delayed within interval", c.isTicking(pos(0, 0)));
        check("size frozen in interval", c.size() == 1);

        // Refresh boundary: leave of (0,0) and enter of (2,0) both apply.
        c.update(20, List.of(pos(2, 0)));
        check("refresh boundary applies", !c.isTicking(pos(0, 0)) && c.isTicking(pos(2, 0)));
        check("snapshot replaces", c.size() == 1);

        // Another boundary 40 ticks later rebuilds again.
        c.update(40, List.of(pos(3, 0), pos(3, 1), pos(3, 2)));
        check("second rebuild", c.size() == 3 && c.isTicking(pos(3, 1)));

        // Snapshot is immutable: mutating the fed collection does not touch it.
        List<Long> live = new ArrayList<>(List.of(pos(9, 9)));
        c.update(60, live);
        live.add(pos(8, 8));
        check("snapshot immutable", c.size() == 1 && !c.isTicking(pos(8, 8)));

        // Deterministic ordering inside the snapshot.
        TickingChunkCache ordered = new TickingChunkCache();
        ordered.update(0, List.of(pos(5, 5), pos(0, 0), pos(-3, 7)));
        long[] arr = ordered.activeArray();
        check("snapshot sorted", arr.length == 3 && arr[0] < arr[1] && arr[1] < arr[2]);

        // Bounded lookup (sorted array, no O(n²) scan): 10k chunks, one query.
        TickingChunkCache big = new TickingChunkCache();
        List<Long> many = new ArrayList<>();
        for (int i = 0; i < 10_000; i++) {
            many.add(pos(i, i));
        }
        big.update(0, many);
        check("large snapshot", big.size() == 10_000 && big.isTicking(pos(9999, 9999))
                && !big.isTicking(pos(-1, -1)));

        if (failures == 0) {
            System.out.println("[TickingChunkCacheProbe] PASS (chunk-ticking cache core, " + 13 + " checks)");
            System.exit(0);
        } else {
            System.out.println("[TickingChunkCacheProbe] FAIL: " + failures + " assertion(s)");
            System.exit(1);
        }
    }
}