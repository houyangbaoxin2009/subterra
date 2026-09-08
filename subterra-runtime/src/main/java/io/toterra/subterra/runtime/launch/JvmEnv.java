package io.toterra.subterra.runtime.launch;

/**
 * L1 launch-layer runtime verification.
 * <p>
 * Toterra's foundation targets bytecode compiled at Java 21 (Minecraft 1.21.1),
 * verified to run on Java 25 LTS. This class reports the running JVM so the
 * launch package can act accordingly (JVM args themselves must be injected
 * before the game process starts; this runs inside the game as a check).
 */
public final class JvmEnv {

    /** Minimum supported Java feature version (Minecraft 1.21.1 ships Java 21). */
    public static final int MIN_FEATURE = 21;

    private JvmEnv() {
    }

    public record Report(int javaVersion, boolean verified) {
    }

    /**
     * Verify the running JVM meets the supported baseline.
     */
    public static Report verify() {
        int feature = Runtime.version().feature();
        return new Report(feature, feature >= MIN_FEATURE);
    }
}