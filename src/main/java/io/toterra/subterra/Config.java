package io.toterra.subterra;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Subterra configuration. Minimal for the skeleton; expands as L1/L2 features land.
 */
public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    // If true, Subterra logs its L1 JVM verification report at startup.
    public static final ModConfigSpec.BooleanValue L1_VERIFY_ON_BOOT = BUILDER
            .comment("Run the L1 Java runtime verification at boot and log the report")
            .define("l1.verifyOnBoot", true);

    static final ModConfigSpec SPEC = BUILDER.build();

    private Config() {
    }
}