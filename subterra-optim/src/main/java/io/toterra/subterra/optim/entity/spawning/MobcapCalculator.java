package io.toterra.subterra.optim.entity.spawning;

import java.util.Objects;

/**
 * Per-player mobcap accounting core (self-developed; behaviour contract from
 * the per-player-spawns family of ideas and the ServerCore mob-spawning
 * surface, MIT face re-keyed clean-room, no upstream code): instead of one
 * global mobcap shared by the whole server, the cap is divided among the
 * participating players, and a spawn location is allowed while <em>any</em>
 * involved player still has free quota (overlapping spawn areas take the
 * fullest quota). An <em>additional capacity</em> may be granted to special
 * spawn sources (reinforcement, portals, spawners, infested) that are
 * otherwise counted against the shared quota.
 *
 * <p>Pure JDK; deterministic integer math; no O(n²).</p>
 */
public final class MobcapCalculator {

    private MobcapCalculator() {
    }

    /**
     * Immutable per-category quota bound to a group of participating players.
     *
     * @param capacity  base per-category mobcap (>= 0)
     * @param added     additional capacity borrowed by special sources (>= 0)
     * @param players   number of players sharing the quota (>= 1)
     */
    public record Quota(int capacity, int added, int players) {

        public Quota {
            if (capacity < 0) {
                throw new IllegalArgumentException("capacity must be >= 0: " + capacity);
            }
            if (added < 0) {
                throw new IllegalArgumentException("added must be >= 0: " + added);
            }
            if (players < 1) {
                throw new IllegalArgumentException("players must be >= 1: " + players);
            }
        }

        /** Equal per-player share of the whole pool (floor integer division). */
        public int share() {
            return (capacity + added) / players;
        }
    }

    /**
     * True when a spawn location is allowed: at least one participating player
     * still has free quota (overlap takes the fullest quota). Each entry of
     * {@code nearbyCountsPerPlayer} is the mob count counted near that player.
     */
    public static boolean canSpawn(Quota quota, int[] nearbyCountsPerPlayer) {
        Objects.requireNonNull(quota, "quota");
        Objects.requireNonNull(nearbyCountsPerPlayer, "nearbyCountsPerPlayer");
        int share = quota.share();
        for (int count : nearbyCountsPerPlayer) {
            if (count < share) {
                return true;
            }
        }
        return false;
    }

    /** Remaining free quota near a player (negative means over capacity). */
    public static int freeQuota(Quota quota, int count) {
        return quota.share() - count;
    }
}