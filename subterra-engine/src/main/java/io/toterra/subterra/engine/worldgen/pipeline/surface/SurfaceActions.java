package io.toterra.subterra.engine.worldgen.pipeline.surface;

import java.util.Objects;

/**
 * Surface-action factory (p.1.8.5, self-developed): constant state actions and a
 * density-split action, mirroring the MC {@code SurfaceRules.StateRule} seam.
 * <p>
 * 表面动作工厂（p.1.8.5，自研）：恒定状态动作与按密度分叉的动作，对应 MC {@code SurfaceRules.StateRule}。
 */
public final class SurfaceActions {

    private SurfaceActions() {
    }

    /** Constant action: always returns {@code id}. */
    public static SurfaceAction state(String id) {
        Objects.requireNonNull(id, "id");
        return c -> id;
    }

    /**
     * Density-split action: {@code density < split} yields {@code lowId}, otherwise
     * {@code highId} (so {@code density == split} maps to {@code highId}).
     */
    public static SurfaceAction byDensity(String lowId, String highId, double split) {
        Objects.requireNonNull(lowId, "lowId");
        Objects.requireNonNull(highId, "highId");
        return c -> c.density() < split ? lowId : highId;
    }
}