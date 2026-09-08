package io.toterra.subterra.engine.optim.entity.spawning;

import java.util.Set;

/**
 * Special-spawn-source enforcement core (clean-room re-key of the ServerCore
 * mob-spawning "enforce-mobcap"/"additional-capacity" concept, GPL surface
 * re-written from contract, no upstream code): monster reinforcements,
 * nether-portal piglins, spawners and infested silverfish normally bypass the
 * mobcap; with enforcement on, their spawns are counted against the
 * category's mob population and may borrow an {@code additionalCapacity}
 * reserve before being refused.
 *
 * <p>Pure JDK; immutable; deterministic.</p>
 */
public final class SpawnEnforcement {

    public static final String ZOMBIE_REINFORCEMENT = "zombie_reinforcement";
    public static final String PORTAL_PIGLIN = "portal_piglin";
    public static final String SPAWNER = "spawner";
    public static final String INFESTED = "infested";

    /** Known special-source ids (registry; config tokens are validated against it). */
    public static final Set<String> KNOWN_SOURCES = Set.of(
            ZOMBIE_REINFORCEMENT, PORTAL_PIGLIN, SPAWNER, INFESTED);

    private SpawnEnforcement() {
    }

    /**
     * Per-source setting.
     *
     * @param enforce            count this source against the category mobcap
     * @param additionalCapacity borrowed reserve above the category cap (>= 0)
     */
    public record Source(boolean enforce, int additionalCapacity) {

        public Source {
            if (additionalCapacity < 0) {
                throw new IllegalArgumentException("additionalCapacity must be >= 0: " + additionalCapacity);
            }
        }

        public static Source enabled() {
            return new Source(true, 0);
        }

        public static Source disabled() {
            return new Source(false, 0);
        }
    }

    /**
     * Whether a special source spawn is allowed at a location with
     * {@code nearbyCount} mobs of the category already counted:
     * enforcement off → always allowed (vanilla bypass); enforcement on →
     * refused once {@code nearbyCount} reaches the category cap plus the
     * borrowed additional reserve.
     */
    public static boolean allowed(Source source, int nearbyCount, int categoryCapacity) {
        if (!source.enforce()) {
            return true;
        }
        return nearbyCount < categoryCapacity + source.additionalCapacity();
    }
}