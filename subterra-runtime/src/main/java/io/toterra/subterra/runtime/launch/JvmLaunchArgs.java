package io.toterra.subterra.runtime.launch;

import java.util.ArrayList;
import java.util.List;

/**
 * L1 launch-layer JVM arguments package (p.1.1).
 * <p>
 * Toterra's foundation targets Minecraft 1.21.1 (Java 21 bytecode) running on
 * Java 25 LTS. These arguments must be injected before the game process starts
 * (see the root build.gradle run configurations); the mod itself validates at
 * boot and logs a remediation hint when required flags are missing.
 * <p>
 * Pure data / pure functions — no Minecraft runtime dependency, exerciseable
 * directly by {@code subterra-probes}.
 */
public final class JvmLaunchArgs {

    private JvmLaunchArgs() {
    }

    /** ZGC baseline: bound garbage-collection pauses on large packs. */
    public static final String ZGC = "-XX:+UseZGC";

    /** Heap policy: start and cap at 75% of physical RAM, pre-touch to avoid runtime heap resizes. */
    public static final String INITIAL_RAM_PERCENTAGE = "-XX:InitialRAMPercentage=75";
    public static final String MAX_RAM_PERCENTAGE = "-XX:MaxRAMPercentage=75";
    public static final String ALWAYS_PRE_TOUCH = "-XX:+AlwaysPreTouch";

    /**
     * Statically verifiable tuning flags. These must all be present at boot;
     * {@link #missingStaticFlags} reports any that are not in the applied args.
     * Fuel-injection values that cannot be validated statically
     * (e.g. {@code -XX:ActiveProcessorCount=N}, equal -Xms/-Xmx, AppCDS archives,
     * client-tiered {@code -XX:TieredStopAtLevel=1}) are documented but dynamic.
     */
    private static final List<String> STATIC_TUNING_FLAGS = List.of(
            ZGC,
            INITIAL_RAM_PERCENTAGE,
            MAX_RAM_PERCENTAGE,
            ALWAYS_PRE_TOUCH
    );

    /**
     * Java 25 strong-encapsulation compatibility opens, injected before start.
     * Start conservative; p.1.2 (L2 compat) tunes this list from real boot logs
     * (RCA-driven), never by guesswork.
     */
    private static final List<String> JAVA25_COMPAT_OPENS = List.of(
            "--add-opens", "java.base/java.lang=ALL-UNNAMED"
    );

    /** Statically verifiable tuning flags (startup contract). */
    public static List<String> staticTuningFlags() {
        return STATIC_TUNING_FLAGS;
    }

    /** Pairs of {@code --add-opens} + module target to inject before start. */
    public static List<String> java25CompatOpens() {
        return JAVA25_COMPAT_OPENS;
    }

    /** Everything the launch package injects before process start. */
    public static List<String> all() {
        List<String> merged = new ArrayList<>(STATIC_TUNING_FLAGS.size() + JAVA25_COMPAT_OPENS.size());
        merged.addAll(STATIC_TUNING_FLAGS);
        merged.addAll(JAVA25_COMPAT_OPENS);
        return List.copyOf(merged);
    }

    /**
     * Reports which statically verifiable tuning flags are missing from the
     * arguments actually applied to the running JVM.
     *
     * @param applied the runtime input arguments ({@code RuntimeMXBean.getInputArguments()})
     * @return flags the boot check should warn about; empty when the contract is met
     */
    public static List<String> missingStaticFlags(List<String> applied) {
        if (applied == null || applied.isEmpty()) {
            return List.copyOf(STATIC_TUNING_FLAGS);
        }
        List<String> missing = new ArrayList<>();
        for (String flag : STATIC_TUNING_FLAGS) {
            if (!applied.contains(flag)) {
                missing.add(flag);
            }
        }
        return missing;
    }
}