package io.toterra.subterra.api.worldgen;

/**
 * Region-name unlocking contract for the Traveler Title module (architecture:
 * Subterra ships the API, Toterra implements the world/map logic). A region
 * name starts hidden ({@code ???}), becomes a clue after discovery, and turns
 * into its custom name once the player names the region. Pure JDK; exact
 * display strings are deterministic so the UI layer renders consistently.
 */
public final class RegionAnnouncement {

    /** Display states of a region name. */
    public enum Visibility {
        /** No clue obtained yet — display {@code ???}. */
        UNKNOWN,
        /** A clue was obtained — display the hint-name. */
        CLUED,
        /** The player named the region — display the custom name. */
        NAMED
    }

    /** The generic placeholder shown before any clue. */
    public static final String UNKNOWN_LABEL = "???";

    private RegionAnnouncement() {
    }

    /**
     * Resolves the display name for a region.
     *
     * @param regionId    region identity (must be non-blank)
     * @param visibility  current unlock state
     * @param hint        hint-name (used when CLUED)
     * @param customName  player-given name (used when NAMED)
     */
    public static String displayName(String regionId, Visibility visibility,
                                     String hint, String customName) {
        if (regionId == null || regionId.isBlank()) {
            throw new IllegalArgumentException("region id must be non-blank");
        }
        return switch (visibility) {
            case UNKNOWN -> UNKNOWN_LABEL;
            case CLUED -> hint == null || hint.isBlank() ? UNKNOWN_LABEL : hint;
            case NAMED -> customName == null || customName.isBlank()
                    ? UNKNOWN_LABEL
                    : customName;
        };
    }

    /** Convenience for the common flow: hidden until clued, then shows the hint. */
    public static String name(String regionId, boolean clued, String hint) {
        return displayName(regionId, clued ? Visibility.CLUED : Visibility.UNKNOWN, hint, null);
    }
}