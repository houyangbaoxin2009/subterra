package io.toterra.subterra.engine.optim.entity.breeding;

import java.util.Objects;

/**
 * Breeding-cap policy core, ported from the ServerCore concept (MIT surface):
 * limits how many attempts/offspring a breeding source may produce in a world,
 * per entity type. Guards "love-mode" spawns so render/match load stays
 * bounded. Pure JDK; deterministic counters.
 *
 * @param capPerType maximum concurrent breeders per entity type (>= 0)
 */
public record BreedingCap(int capPerType) {

    public BreedingCap {
        if (capPerType < 0) {
            throw new IllegalArgumentException("capPerType must be >= 0: " + capPerType);
        }
    }

    public static final BreedingCap UNBOUNDED = new BreedingCap(0); // 0 = no cap

    /** Bindable counter over live breeders per entity type. */
    public static final class Counters {
        private final java.util.Map<String, Integer> count = new java.util.HashMap<>();

        /** Active breeders of a type (0 when none). */
        public synchronized int get(String type) {
            return count.getOrDefault(type, 0);
        }

        /** Registers one breeder start; no-op when already at the cap. */
        public synchronized boolean acquire(String type) {
            int cur = count.getOrDefault(type, 0);
            if (cur >= 0) {
                count.put(type, cur + 1);
                return true;
            }
            return false;
        }

        /** Releases one breeder slot. */
        public synchronized void release(String type) {
            count.computeIfPresent(type, (k, v) -> v <= 1 ? null : v - 1);
        }

        public synchronized void clear() {
            count.clear();
        }
    }

    /** True when a new breeder of the type may start. */
    public static boolean canBreed(BreedingCap policy, Counters counters, String type) {
        Objects.requireNonNull(type, "type");
        return policy.capPerType() == 0 || counters.get(type) < policy.capPerType();
    }
}