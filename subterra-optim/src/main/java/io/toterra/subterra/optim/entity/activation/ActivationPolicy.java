package io.toterra.subterra.optim.entity.activation;

import java.util.Objects;

/**
 * Activation throttling policy core (clean-room re-key of the ServerCore
 * "activation-range" concept, GPL surface re-written from behaviour contract,
 * no upstream code): an entity is ticked normally while within {@code range}
 * of any player; beyond it enters a dormant state that is woken only by an
 * interval scan (every {@code tickInterval} ticks, negative = never). A
 * per-type skip may additionally drop one tick out of every
 * {@code skipNonImmuneEvery} ticks for in-range but non-immune entities.
 *
 * <p>Pure JDK; immutable; equality by value.</p>
 *
 * @param range             activation radius in blocks (>= 0)
 * @param tickInterval      wake-up scan period for out-of-range entities;
 *                          negative disables dormant tick activation
 * @param skipNonImmuneEvery every-N-th tick skipped for in-range non-immune
 *                          entities (0 = never skip)
 */
public record ActivationPolicy(int range, int tickInterval, int skipNonImmuneEvery) {

    public static final int DEFAULT_RANGE = 16;
    public static final int DEFAULT_INTERVAL = 20;
    public static final int NEW_ENTITY_GRACE_TICKS = 200; // 10 s

    public ActivationPolicy {
        if (range < 0) {
            throw new IllegalArgumentException("range must be >= 0: " + range);
        }
        if (tickInterval == 0) {
            throw new IllegalArgumentException("tickInterval must be non-zero: " + tickInterval);
        }
        if (skipNonImmuneEvery < 0) {
            throw new IllegalArgumentException("skipNonImmuneEvery must be >= 0: " + skipNonImmuneEvery);
        }
    }

    /** New-entity / out-of-range wake-up scan every tick, radius 16. */
    public static ActivationPolicy defaults() {
        return new ActivationPolicy(DEFAULT_RANGE, DEFAULT_INTERVAL, 0);
    }

    /** One-shot wake-up scan schedule helper: true on interval boundaries. */
    public static boolean wakeTick(ActivationPolicy policy, int tickCount) {
        Objects.requireNonNull(policy, "policy");
        return policy.tickInterval() > 0 && tickCount % policy.tickInterval() == 0;
    }
}