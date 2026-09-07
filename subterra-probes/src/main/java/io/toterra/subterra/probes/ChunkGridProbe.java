package io.toterra.subterra.probes;

import io.toterra.subterra.optim.worldgen.pipeline.chunkgrid.DensityGrid;
import io.toterra.subterra.optim.worldgen.pipeline.chunkgrid.GridSettings;
import io.toterra.subterra.optim.worldgen.pipeline.chunkgrid.HeightMapper;
import io.toterra.subterra.optim.worldgen.pipeline.chunkgrid.Trilinear;
import io.toterra.subterra.optim.worldgen.pipeline.density.Densities;
import io.toterra.subterra.optim.worldgen.pipeline.density.Density;
import io.toterra.subterra.optim.worldgen.pipeline.dimension.OverworldBounds;

/**
 * Deterministic acceptance probe for the p.1.8.13 NoiseChunk grid interpolation
 * + density→height mapping core: GridSettings validation, DensityGrid cell
 * arithmetic and corner sampling, Trilinear exactness/symmetry/boundary-clamp,
 * and HeightMapper surface/underwater semantics. Pure JVM.
 */
public final class ChunkGridProbe {

    private ChunkGridProbe() {
    }

    private static int failures = 0;
    private static int checks = 0;

    private static void check(String name, boolean ok) {
        checks++;
        if (ok) {
            System.out.println("[PASS] " + name);
        } else {
            failures++;
            System.out.println("[FAIL] " + name);
        }
    }

    private static boolean near(double a, double b) {
        return Math.abs(a - b) < 1.0e-9;
    }

    private static boolean throwsIAE(Runnable r) {
        try {
            r.run();
            return false;
        } catch (IllegalArgumentException e) {
            return true;
        }
    }

    public static void main(String[] args) {
        vanillaWindowChecks();
        cellArithmeticChecks();
        samplingChecks();
        trilinearChecks();
        heightMapperChecks();

        if (failures == 0) {
            System.out.println("[ChunkGridProbe] PASS (grid interpolation + height mapping, " + checks + " checks)");
            System.exit(0);
        } else {
            System.out.println("[ChunkGridProbe] FAIL: " + failures + " assertion(s)");
            System.exit(1);
        }
    }

    // ---- GridSettings configuration & validation. ----
    private static void vanillaWindowChecks() {
        check("g s default cell width is 4",
                GridSettings.DEFAULT_CELL_WIDTH == 4);
        check("g s default cell height is 8",
                GridSettings.DEFAULT_CELL_HEIGHT == 8);
        check("g s reject non-positive cellWidth", throwsIAE(() ->
                new GridSettings(0, 8, -64, 384, 63, 4, 0, 0)));
        check("g s reject non-positive cellHeight", throwsIAE(() ->
                new GridSettings(4, 0, -64, 384, 63, 4, 0, 0)));
        check("g s reject non-positive height", throwsIAE(() ->
                new GridSettings(4, 8, 0, 0, 63, 4, 0, 0)));
        check("g s reject negative height", throwsIAE(() ->
                new GridSettings(4, 8, -64, -384, 63, 4, 0, 0)));
        check("g s reject height not a multiple of cellHeight", throwsIAE(() ->
                new GridSettings(4, 8, -64, 385, 63, 4, 0, 0)));
        check("g s reject non-positive cellCountXZ", throwsIAE(() ->
                new GridSettings(4, 8, -64, 384, 63, 0, 0, 0)));
        check("g s reject seaLevel outside window", throwsIAE(() ->
                new GridSettings(4, 8, -64, 384, 400, 4, 0, 0)));
        check("g s accepts [0,7] height divisible by cellHeight",
                new GridSettings(4, 8, 3, 32, 10, 4, 0, 0).cellCountY() == 4);

        GridSettings vanilla = GridSettings.from(OverworldBounds.vanilla());
        check("g s vanilla window height divisible by cellHeight",
                vanilla.height() % vanilla.cellHeight() == 0);
        check("g s vanilla cellCountY == 48", vanilla.cellCountY() == 48);
        check("g s vanilla minY/maxY", vanilla.minY() == -64 && vanilla.maxY() == 320);
        check("g s vanilla xSize is one chunk", vanilla.xSize() == 16);

        GridSettings tall = GridSettings.from(OverworldBounds.tall());
        check("g s tall window height 512 % 8 == 0", tall.height() % tall.cellHeight() == 0);
        check("g s tall cellCountY == 64", tall.cellCountY() == 64);
    }

    // ---- DensityGrid cell-index arithmetic. ----
    private static void cellArithmeticChecks() {
        GridSettings s = new GridSettings(4, 8, -64, 384, 63, 4, 0, 0);
        DensityGrid g = new DensityGrid(s, Densities.constant(1.0));
        check("cellX at origin 0", g.cellX(0) == 0);
        check("cellX mid-cell", g.cellX(3) == 0 && g.cellX(4) == 1);
        check("cellX negative floor-div", g.cellX(-1) == -1 && g.cellX(-4) == -1 && g.cellX(-5) == -2);
        check("cellY at minY", g.cellY(-64) == 0);
        check("cellY step = cellHeight", g.cellY(-56) == 1);
        check("cellStartX round-trip", g.cellStartX(7) == 4 && g.cellStartX(-5) == -8);
        check("cellStartY round-trip", g.cellStartY(50) == 48 && g.cellStartY(-70) == -72);
        check("corner count XZ", g.cellCountXZ() == 4 && g.cellCountY() == 48);
    }

    // ---- DensityGrid corner sampling & determinism. ----
    private static void samplingChecks() {
        GridSettings s = new GridSettings(4, 8, -64, 384, 63, 4, 0, 0);
        // Field linear in all axes, hand-verifiable: 2x + 3y + 5z + 1.
        Density lin = (x, y, z) -> 2.0 * x + 3.0 * y + 5.0 * z + 1.0;
        DensityGrid g = new DensityGrid(s, lin);
        check("corner 000 matches direct eval",
                near(g.corner(0, 0, 0), lin.eval(s.originX(), s.minY(), s.originZ())));
        check("corner interior matches direct eval",
                near(g.corner(2, 20, 3),
                        lin.eval(s.originX() + 2 * s.cellWidth(),
                                s.minY() + 20 * s.cellHeight(),
                                s.originZ() + 3 * s.cellWidth())));
        check("corner top matches direct eval",
                near(g.corner(4, 48, 4),
                        lin.eval(s.originX() + 4 * s.cellWidth(),
                                s.minY() + 48 * s.cellHeight(),
                                s.originZ() + 4 * s.cellWidth())));
        // Determinism: re-created grid matches.
        DensityGrid g2 = new DensityGrid(s, lin);
        boolean det = true;
        for (int i = 0; i <= 4 && det; i++) {
            for (int j = 0; j <= 48 && det; j++) {
                for (int k = 0; k <= 4 && det; k++) {
                    if (g.corner(i, j, k) != g2.corner(i, j, k)) {
                        det = false;
                    }
                }
            }
        }
        check("determinism: identical re-created grids", det);
        check("valueAt constant field equals constant",
                near(g.valueAt(3, 10, 5), 2.0 * 3 + 3.0 * 10 + 5.0 * 5 + 1.0));
        // valueAt at a cell corner reproduces the exact corner value.
        check("valueAt at corner equals corner", near(g.valueAt(4, -48, 8), 2.0 * 4 + 3.0 * -48 + 5.0 * 8 + 1.0));
    }

    // ---- Trilinear interpolation. ----
    private static void trilinearChecks() {
        // Distinct corners so every weight is exercised.
        double v000 = 1, v100 = 2, v010 = 3, v110 = 4;
        double v001 = 5, v101 = 6, v011 = 7, v111 = 8;
        check("tri corner v000 exact", near(
                Trilinear.interpolate(v000, v100, v010, v110, v001, v101, v011, v111, 0, 0, 0), v000));
        check("tri corner v100 exact", near(
                Trilinear.interpolate(v000, v100, v010, v110, v001, v101, v011, v111, 1, 0, 0), v100));
        check("tri corner v001 (highY) exact", near(
                Trilinear.interpolate(v000, v100, v010, v110, v001, v101, v011, v111, 0, 1, 0), v001));
        check("tri corner v111 exact", near(
                Trilinear.interpolate(v000, v100, v010, v110, v001, v101, v011, v111, 1, 1, 1), v111));
        // Constant field at cell centre: corner-mean.
        double m = (v000 + v100 + v010 + v110 + v001 + v101 + v011 + v111) / 8.0;
        check("tri returns corner-mean at centre", near(
                Trilinear.interpolate(v000, v100, v010, v110, v001, v101, v011, v111, 0.5, 0.5, 0.5), m));
        // lerp endpoint + interior.
        check("lerp endpoints", near(Trilinear.lerp(2, 6, 0), 2) && near(Trilinear.lerp(2, 6, 1), 6));
        check("lerp midpoint", near(Trilinear.lerp(2, 6, 0.25), 3.0));
        // Axis-order symmetry: my Y→X→Z order equals an X→Z→Y manual composition.
        double dX = 0.3, dY = 0.6, dZ = 0.9;
        double viaSequential = Trilinear.interpolate(v000, v100, v010, v110, v001, v101, v011, v111, dX, dY, dZ);
        double xz00 = Trilinear.lerp(v000, v001, dY); // (x0,z0)
        double xz10 = Trilinear.lerp(v100, v101, dY); // (x1,z0)
        double xz01 = Trilinear.lerp(v010, v011, dY); // (x0,z1)
        double xz11 = Trilinear.lerp(v110, v111, dY); // (x1,z1)
        double z0 = Trilinear.lerp(xz00, xz10, dX);
        double z1 = Trilinear.lerp(xz01, xz11, dX);
        double manual = Trilinear.lerp(z0, z1, dZ);
        check("tri matches manual Y→X→Z composition", near(viaSequential, manual));

        // Boundary clamp: y-ramp grid, density = y.
        GridSettings s = new GridSettings(4, 8, -64, 384, 63, 4, 0, 0);
        DensityGrid g = new DensityGrid(s, (x, y, z) -> (double) y);
        check("tri boundary clamp below minY equals edge",
                near(g.valueAt(3, -100, 3), g.valueAt(3, -64, 3))
                        && near(g.valueAt(3, -100, 3), -64.0));
        check("tri boundary clamp above maxY equals edge",
                near(g.valueAt(3, 500, 3), g.valueAt(3, 320, 3))
                        && near(g.valueAt(3, 500, 3), 320.0));
        check("tri in-window y ramp monotone",
                g.valueAt(3, 0, 3) < g.valueAt(3, 200, 3));
    }

    // ---- HeightMapper surface/underwater semantics. ----
    private static void heightMapperChecks() {
        GridSettings vanilla = GridSettings.from(OverworldBounds.vanilla());
        HeightMapper hm = new HeightMapper(vanilla);

        // Constant +1: entirely solid -> surface at top, height = build ceiling.
        DensityGrid solid = new DensityGrid(vanilla, Densities.constant(1.0));
        check("solid const +1 surface at maxY-1", hm.getSurfaceY(solid, 3, 3) == vanilla.maxY() - 1);
        check("solid const +1 height == build ceiling", hm.getHeight(solid, 3, 3) == vanilla.maxY());
        check("solid const +1 not underwater", !hm.isUnderwater(solid, 3, 3));

        // Constant -1: entirely air -> void (minY).
        DensityGrid air = new DensityGrid(vanilla, Densities.constant(-1.0));
        check("air const -1 surface == minY/void", hm.getSurfaceY(air, 5, 5) == vanilla.minY());
        check("air const -1 height == minY/void", hm.getHeight(air, 5, 5) == vanilla.minY());

        // Y-ramp crossing at y = 100: surface == 100, above sea level.
        DensityGrid cross = new DensityGrid(vanilla, (x, y, z) -> 100.0 - y);
        check("y-ramp cross surface at 100", hm.getSurfaceY(cross, 7, 2) == 100);
        check("y-ramp interpolated density crosses ~0 at surface",
                near(cross.valueAt(7, 100, 2), 0.0));
        check("y-ramp cross height == 101", hm.getHeight(cross, 7, 2) == 101);
        check("y-ramp surface 100 not underwater", !hm.isUnderwater(cross, 7, 2));

        // Y-ramp crossing at y = 30: below sea level -> underwater.
        DensityGrid shallow = new DensityGrid(vanilla, (x, y, z) -> 30.0 - y);
        check("shallow ramp surface at 30", hm.getSurfaceY(shallow, 1, 1) == 30);
        check("shallow ramp underwater (30 < 63)", hm.isUnderwater(shallow, 1, 1));

        // Linear ramp in x: columns split solid/air.
        GridSettings rampS = new GridSettings(4, 8, -64, 384, 63, 8, -12, 0);
        DensityGrid ramp = new DensityGrid(rampS, Densities.linearX(1.0, 0.0));
        HeightMapper rhm = new HeightMapper(rampS);
        check("x-ramp negative column is void", rhm.getSurfaceY(ramp, -5, 4) == rampS.minY());
        check("x-ramp positive column at top", rhm.getSurfaceY(ramp, 5, 4) == rampS.maxY() - 1);
        check("x-ramp zero column counts solid (top)",
                rhm.getSurfaceY(ramp, 0, 4) == rampS.maxY() - 1);

        // Determinism across re-created grids.
        DensityGrid again = new DensityGrid(vanilla, (x, y, z) -> 100.0 - y);
        check("determinism: identical mapper result", hm.getSurfaceY(cross, 7, 2) == hm.getSurfaceY(again, 7, 2));

        // Both vanilla 384 and tall 512 windows build and map correctly.
        GridSettings tall = GridSettings.from(OverworldBounds.tall());
        HeightMapper thm = new HeightMapper(tall);
        DensityGrid tallCross = new DensityGrid(tall, (x, y, z) -> 150.0 - y);
        check("tall 512 window cellCountY rebuild", new DensityGrid(tall, Densities.constant(0.0)).cellCountY() == 64);
        check("tall 512 window surface mapping", thm.getSurfaceY(tallCross, 3, 3) == 150);
        check("tall 512 constant +1 height == 448 build ceiling",
                thm.getHeight(new DensityGrid(tall, Densities.constant(1.0)), 3, 3) == tall.maxY());
    }
}