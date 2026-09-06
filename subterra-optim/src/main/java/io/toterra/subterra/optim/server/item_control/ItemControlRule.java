// Ported from itemban (github.com/lnsanes/itemban), Apache-2.0 (c) lnsanes;
// adapted to Subterra's NeoForge 1.21.1 internal capability.
package io.toterra.subterra.optim.server.item_control;

import java.util.Objects;

/**
 * Pure-JDK core of an item/block blacklist rule (the MC-layer shell in
 * subterra-itemban supplies the actual NBT deep-match via {@link NbtMatcher}).
 *
 * <p>This class never touches Minecraft/NBT runtime classes, so the
 * deterministic probe (subterra-probes) can exercise it on a plain JVM:
 * exact-id matching, empty NBT-rule wildcard semantics, NBT-constrained
 * matching and rule identity (used for duplicate suppression). NBT data is
 * passed through as an opaque {@code Object} — the shell decides what it
 * carries.
 *
 * <p>Rules are immutable. {@code id} is normalized (trimmed, never blank);
 * an empty {@code nbtSnbt} is treated as "no NBT constraint". A rule without
 * an NBT constaint matches any instance of its id (wildcard over NBT).
 */
public final class ItemControlRule {

    /**
     * NBT matching carried out by the MC-layer shell. The subterra-itemban
     * implementation parses the configured SNBT and performs the subset
     * containment check; implementations must be thread-safe because scans
     * match on a background thread.
     */
    public interface NbtMatcher {
        boolean matches(Object itemNbt);
    }

    private final String id;
    private final String nbtSnbt;
    private final NbtMatcher nbt;

    private ItemControlRule(String id, String nbtSnbt, NbtMatcher nbt) {
        this.id = id;
        this.nbtSnbt = nbtSnbt;
        this.nbt = nbt;
    }

    /** Wildcard rule: bans every instance of {@code id}, any NBT. */
    public static ItemControlRule of(String id) {
        return of(id, null, null);
    }

    /**
     * Rule with an (optional) NBT constraint. An empty/blank {@code nbtSnbt}
     * yields the wildcard form. {@code matcher} may be null only when
     * {@code nbtSnbt} is empty.
     */
    public static ItemControlRule of(String id, String nbtSnbt, NbtMatcher matcher) {
        if (id == null) {
            throw new IllegalArgumentException("id must not be null");
        }
        String trimmed = id.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        String nbt = normalize(nbtSnbt);
        return new ItemControlRule(trimmed, nbt, nbt == null ? null : matcher);
    }

    /** {@code id} as configured (trimmed). */
    public String id() {
        return id;
    }

    /** Raw SNBT constraint string ({@code null} = no constraint). */
    public String nbtSnbt() {
        return nbtSnbt;
    }

    /** True when this rule requires an NBT match (not a wildcard over NBT). */
    public boolean hasNbtConstraint() {
        return nbt != null;
    }

    /**
     * Matches an item/block id and its NBT. Ids are compared exactly
     * (case-sensitive, matching MC registry keys); a rule with no NBT
     * constraint matches any NBT for that id.
     */
    public boolean matches(String itemId, Object itemNbt) {
        if (itemId == null || !id.equals(itemId)) {
            return false;
        }
        if (nbt == null) {
            return true;
        }
        return nbt.matches(itemNbt);
    }

    /**
     * Identity used for duplicate suppression: same normalized id and the
     * same NBT constraint string collapse to one effective rule.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ItemControlRule that)) {
            return false;
        }
        return id.equals(that.id) && Objects.equals(nbtSnbt, that.nbtSnbt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, nbtSnbt);
    }

    @Override
    public String toString() {
        return nbtSnbt == null ? id : id + nbtSnbt;
    }

    /** Trims and nulls out blank NBT constraint strings. */
    private static String normalize(String nbtSnbt) {
        if (nbtSnbt == null) {
            return null;
        }
        String trimmed = nbtSnbt.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}