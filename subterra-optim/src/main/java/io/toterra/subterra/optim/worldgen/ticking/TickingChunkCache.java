package io.toterra.subterra.optim.worldgen.ticking;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * Ticking-chunk cache core (self-developed; behaviour contract mirrors the
 * ServerCore "cache-ticking-chunks" concept, MIT/GPL-mixed surface re-keyed
 * as a clean-room idea): the server-side per-tick full iteration over all
 * loaded chunks (to decide which receive random ticks / spawning / ticking
 * block entities) is replaced by a cached snapshot that is rebuilt once per
 * second (every {@value #REFRESH_INTERVAL} ticks) instead of every tick.
 * Chunk enter/leave takes effect with at most a 1-second delay.
 *
 * <p>Pure JDK, deterministic, no O(n²): lookups are binary searches over a
 * sorted immutable snapshot (a {@code long[]} of chunk coordinates).</p>
 */
public final class TickingChunkCache {

    /** Refresh period in ticks: the snapshot is rebuilt once per second. */
    public static final int REFRESH_INTERVAL = 20;

    private long[] active = new long[0]; // sorted snapshot, never mutated after rebuild
    private int lastRefreshTick = -1;

    /**
     * Feeds the full current set of ticking chunk positions once per tick
     * (positions in {@code ChunkPos.toLong(x, z)} encoding). The snapshot is
     * only rebuilt when {@code tickCount} reaches the next refresh boundary.
     */
    public void update(int tickCount, Collection<Long> ticking) {
        if (lastRefreshTick < 0 || tickCount > lastRefreshTick && isRefreshBoundary(tickCount)) {
            long[] next = new long[ticking.size()];
            int i = 0;
            for (long pos : ticking) {
                next[i++] = pos;
            }
            Arrays.sort(next);
            this.active = next;
            this.lastRefreshTick = tickCount;
        }
    }

    private static boolean isRefreshBoundary(int tickCount) {
        return tickCount % REFRESH_INTERVAL == 0;
    }

    /** True when the position is in the current (cached) ticking snapshot. */
    public boolean isTicking(long chunkPos) {
        return Arrays.binarySearch(active, chunkPos) >= 0;
    }

    /** Number of chunks in the current snapshot. */
    public int size() {
        return active.length;
    }

    /** Immutable view of the current snapshot (allocated per call). */
    public List<Long> snapshot() {
        Long[] copy = new Long[active.length];
        for (int i = 0; i < active.length; i++) {
            copy[i] = active[i];
        }
        return List.of(copy);
    }

    /** Exact snapshot array (for tests); do not mutate. */
    public long[] activeArray() {
        return active;
    }
}