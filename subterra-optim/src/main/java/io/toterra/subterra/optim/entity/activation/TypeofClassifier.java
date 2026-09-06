package io.toterra.subterra.optim.entity.activation;

import java.util.Collection;
import java.util.Comparator;

/**
 * Kind-of-entity classifier core for activation types (clean-room re-key of
 * the ServerCore "typeof:" matcher, no upstream code): activation types are
 * matched either by exact entity-type id (MC-layer concern) or by a
 * {@code typeof:<kind>} token. A pure {@link Kind} registry with a fixed
 * priority order lets the MC shell resolve an entity to {@link #best(Collection)}
 * of the kinds it implements, and lets config tokens be validated/parsed
 * without touching the MC runtime.
 */
public final class TypeofClassifier {

    /** Priority: lower number wins when multiple kinds match an entity. */
    public enum Kind {
        PROJECTILE(0),
        VILLAGER(1),
        FLYING_ANIMAL(2),
        WATER_ANIMAL(3),
        ANIMAL(4),
        NEUTRAL(5),
        AMBIENT(6),
        MONSTER(7),
        MOB(8);

        private final int priority;

        Kind(int priority) {
            this.priority = priority;
        }

        public int priority() {
            return priority;
        }

        /** Parse a config token {@code typeof:<kind>}; null when not a typeof token. */
        public static Kind ofTypeofToken(String token) {
            if (token == null || !token.startsWith("typeof:")) {
                return null;
            }
            String name = token.substring("typeof:".length());
            for (Kind k : values()) {
                if (k.name().equalsIgnoreCase(name)) {
                    return k;
                }
            }
            return null;
        }
    }

    private TypeofClassifier() {
    }

    /**
     * Highest-priority kind among the given matches; {@code MOB} when the
     * collection is empty or null (generic mob fallback).
     */
    public static Kind best(Collection<Kind> kinds) {
        if (kinds == null || kinds.isEmpty()) {
            return Kind.MOB;
        }
        return kinds.stream().min(Comparator.comparingInt(Kind::priority)).orElse(Kind.MOB);
    }
}