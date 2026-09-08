package io.toterra.subterra.engine.optim.entity.merging;

import java.util.function.DoubleSupplier;

/**
 * Deterministic merge decision core (ServerCore merging surface): two pickups
 * of the same identity may merge when the policy is enabled, they are within
 * {@code radius} and the fraction roll passes. The random source is injected
 * ({@link DoubleSupplier} in [0,1)) so callers keep full control and probes
 * stay deterministic. Pure JDK.
 */
public final class Merger {

    private Merger() {
    }

    /**
     * Decides whether two same-identity pickups merge.
     *
     * @param identity the pickup identity (must be equal for a merge)
     * @param other    the peer identity (must equal {@code identity})
     * @param distSq   squared horizontal distance between the pair, in blocks
     * @param policy   active merge policy
     * @param roll     uniform random source in [0,1)
     */
    public static boolean canMerge(String identity, String other, double distSq,
                                   MergePolicy policy, DoubleSupplier roll) {
        if (!policy.enabled() || !identity.equals(other)) {
            return false;
        }
        if (distSq > policy.radiusSq()) {
            return false;
        }
        return roll.getAsDouble() < policy.fraction();
    }
}