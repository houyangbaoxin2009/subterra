package io.toterra.subterra.optim.entity.merging;

/**
 * Item/XP merging policy, ported from the ServerCore concept (MIT surface):
 * merges nearby same-identity pickups when they are within {@link #radius}
 * blocks and the probabilistic {@link #fraction} roll passes. Pure data;
 * validation at construction so a broken config fails fast.
 *
 * @param enabled  whether merging is active at all
 * @param radius   merge distance in blocks (>= 0)
 * @param fraction merge probability per candidate pair in [0,1]
 */
public record MergePolicy(boolean enabled, double radius, double fraction) {

    public MergePolicy {
        if (radius < 0 || Double.isNaN(radius) || Double.isInfinite(radius)) {
            throw new IllegalArgumentException("invalid merge radius: " + radius);
        }
        if (fraction < 0.0 || fraction > 1.0 || Double.isNaN(fraction)) {
            throw new IllegalArgumentException("fraction must be in [0,1]: " + fraction);
        }
    }

    public static MergePolicy disabled() {
        return new MergePolicy(false, 2.0, 1.0);
    }

    /** Squared merge radius, for distance comparisons. */
    public double radiusSq() {
        return radius * radius;
    }
}