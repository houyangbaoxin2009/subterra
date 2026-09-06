package io.toterra.subterra.optim.entity.activation;

import java.util.Objects;

/**
 * Per-entity activation predicate core (clean-room re-key of the ServerCore
 * "activation-range" behaviour, no upstream code): decides whether an entity
 * may run its full AI tick this tick. Rules, in order:
 * <ol>
 *   <li>new entities (age &lt; {@link ActivationPolicy#NEW_ENTITY_GRACE_TICKS})
 *       are always active — spawn/load grace;</li>
 *   <li>immune states (falling, recently hurt, panicking) are always
 *       active — never frozen mid-motion or in combat;</li>
 *   <li>in-range entities are active unless the per-type skip-non-immune
 *       rule drops this tick (every N-th tick is skipped);</li>
 *   <li>out-of-range entities are woken on the interval scan boundary
 *       (a negative {@code tickInterval} disables dormant activation).</li>
 * </ol>
 * Pure JDK; deterministic; no state.
 */
public final class Activator {

    private Activator() {
    }

    /**
     * @param policy       activation policy for the entity's kind
     * @param distanceSq   squared horizontal distance to the nearest player
     * @param ageTicks     ticks since the entity exists (0 for fresh spawns)
     * @param tickCount    server tick counter (for interval wake-up / skip)
     * @param falling      immunity: entity has not landed yet
     * @param recentlyHurt immunity: entity is still in the hurt/invulnerable window
     * @param panicking    immunity: entity is in a panic/combat activity
     */
    public static boolean isActive(
            ActivationPolicy policy,
            double distanceSq, long ageTicks, int tickCount,
            boolean falling, boolean recentlyHurt, boolean panicking) {
        Objects.requireNonNull(policy, "policy");
        if (ageTicks < ActivationPolicy.NEW_ENTITY_GRACE_TICKS) {
            return true;
        }
        if (falling || recentlyHurt || panicking) {
            return true;
        }
        double r = policy.range();
        boolean inRange = distanceSq <= r * r;
        if (inRange) {
            int skipEvery = policy.skipNonImmuneEvery();
            return skipEvery == 0 || tickCount % skipEvery != 0;
        }
        int interval = policy.tickInterval();
        if (interval < 0) {
            return false; // dormant activation disabled
        }
        return interval > 0 && tickCount % interval == 0;
    }
}