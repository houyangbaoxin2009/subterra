package io.toterra.subterra.engine.worldgen.pipeline.terrain;

import io.toterra.subterra.engine.worldgen.pipeline.density.Density;
import io.toterra.subterra.engine.worldgen.pipeline.density.ValueNoise;
import io.toterra.subterra.engine.worldgen.pipeline.formula.EvalContext;
import io.toterra.subterra.engine.worldgen.pipeline.formula.Expr;
import io.toterra.subterra.engine.worldgen.pipeline.noise.perlin.PerlinNoise;
import io.toterra.subterra.engine.worldgen.pipeline.noise.simplex.NormalNoise;
import io.toterra.subterra.engine.worldgen.pipeline.noise.simplex.SimplexNoise;

/**
 * Math-formula terrain integration provider (p.1.8.10, clean-room, off by
 * default). Implements the {@link Density} seam ({@code (x,y,z)->double}) so
 * terrain height follows a user math formula over x/y/z, optionally overlaid and
 * smoothed. Deterministic: the same params + coordinates always give the same
 * double across instances and JVMs.
 * <p>
 * The feature is <em>off by default</em>: a provider is only constructed when the
 * user explicitly builds one from {@link FormulaParams} (a td string, a builder
 * or {@link FormulaParams#defaults()}). Nothing here is wired into any default
 * generator path; {@link #enabled()} simply reports that this provider is active.
 * <p>
 * <strong>Semantics.</strong> The formula is parsed once in the constructor
 * (a parse failure surfaces as {@link IllegalArgumentException} carrying the
 * formula source). Each evaluation substitutes the <em>scaled</em> coordinates
 * into the formula context:
 * <pre>
 *   sx = x * scale      sy = y * height        sz = z * scale
 *   base   = formula.eval(sx, sy, sz)
 *   overlay= noise( sx, sy, sz ) * variation          // 0 when noise = NONE
 *   raw    = base + overlay
 *   result = smoothing==0 ? raw : raw + (neighbAvg - raw) * smoothing
 * </pre>
 * {@code scale} stretches the horizontal (x/z) plane; {@code height} is the
 * vertical (y) multiplier applied as coordinate scaling (so a formula {@code y}
 * with {@code height = h} at world-y returns {@code y*h} — not a post-multiply of
 * an unscaled result). {@code variation} scales the noise overlay sampled in the
 * same scaled space. {@code smoothing} is a low-pass blend factor in [0,1] whose
 * weight 0 is the identity and weight 1 returns the arithmetic mean of the raw
 * field over the six orthogonal neighbours (a single local pass, O(1) per sample,
 * never O(n²)). The overlay noise flavour comes from
 * {@link FormulaParams.Noise}.
 * <p>
 * The seed that {@link EvalContext} carries is also installed for the formula's
 * seeded {@link FormulaNoiseFunctions} (rand/randrange/perlin/... ) so they stay
 * deterministic per seed.
 * <p>
 * 数学公式地形集成提供器（p.1.8.10，净室，默认关闭）：实现 {@link Density} 接口
 * （{@code (x,y,z)->double}），让地形高度沿用户数学公式起伏，可选叠加与平滑。
 * 本特性<em>默认关闭</em>：仅当用户显式从 {@link FormulaParams}（td 字符串、
 * 构建器或 {@link FormulaParams#defaults()}）构建时才会创建提供器；不接入任何
 * 默认生成路径。公式在构造器中只解析一次；每次求值把<em>缩放后</em>坐标代入
 * 公式上下文。{@code scale} 拉伸水平面；{@code height} 为纵向倍率，以坐标缩放
 * 方式施加。{@code variation} 缩放同一缩放空间内采样的噪声叠加。{@code
 * smoothing} ∈ [0,1] 为低通混合因子，权重 0 保持恒等、权重 1 返回原始场在六个
 * 正交邻居上的算术均值（单次局部通扫，每样本 O(1)，绝无 O(n²)）。
 */
public final class FormulaTerrain implements Density {

    /** Overlay {@code VALUE}/in-formula lattice cell size used when building the overlay. */
    private static final double OVERLAY_SCALE = 16.0;

    private final FormulaParams params;
    private final Expr expr;
    /** If non-null, {@code variation * handle.eval(sx,sy,sz)} is added to the base. */
    private final Density overlay;

    /**
     * Builds a provider from explicit params. The formula is parsed here so a
     * bad formula fails fast in the constructor.
     *
     * @throws IllegalArgumentException if params is null, or the formula fails
     *         to parse (message includes the formula source).
     */
    public FormulaTerrain(FormulaParams params) {
        if (params == null) {
            throw new IllegalArgumentException("params must not be null");
        }
        this.params = params;
        FormulaNoiseFunctions.install(); // ensure rand/perlin/... are registered
        final Expr parsed;
        try {
            parsed = Expr.parse(params.formula());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "invalid formula '" + params.formula() + "': " + e.getMessage(), e);
        }
        this.expr = parsed;
        this.overlay = buildOverlay(params);
    }

    /** The immutable params this provider was built from. */
    public FormulaParams params() {
        return params;
    }

    /** Whether this provider is active. Always true here — constructing it is the opt-in. */
    public boolean enabled() {
        return true;
    }

    @Override
    public double eval(double x, double y, double z) {
        double center = raw(x, y, z);
        double s = params.smoothing();
        if (s == 0.0) {
            return center;
        }
        double avg = (raw(x - 1, y, z) + raw(x + 1, y, z)
                + raw(x, y - 1, z) + raw(x, y + 1, z)
                + raw(x, y, z - 1) + raw(x, y, z + 1)) / 6.0;
        return center + (avg - center) * s;
    }

    /** The raw field (formula base + overlay) at unscaled world coords. */
    private double raw(double x, double y, double z) {
        double sx = x * params.scale();
        double sy = y * params.height();
        double sz = z * params.scale();
        double overlayV = overlay == null ? 0.0 : overlay.eval(sx, sy, sz) * params.variation();
        long prev = FormulaNoiseFunctions.begin(params.seed());
        double base;
        try {
            base = expr.eval(new EvalContext(sx, sy, sz, params.seed()));
        } finally {
            FormulaNoiseFunctions.end(prev);
        }
        return base + overlayV;
    }

    /** Builds the (nullable) overlay handle for the requested noise flavour. */
    private static Density buildOverlay(FormulaParams p) {
        long seed = p.seed();
        switch (p.noise()) {
            case NONE:
                return null;
            case PERLIN:
                return PerlinNoise.create(seed, 0, new double[]{1.0})::getValue;
            case SIMPLEX:
                return SimplexNoise.fromSeed(seed)::getValue;
            case NORMAL:
                return NormalNoise.create(seed, 0, 1.0)::getValue;
            case VALUE:
                return new ValueNoise(seed, OVERLAY_SCALE)::eval;
            default:
                throw new IllegalStateException("unhandled noise: " + p.noise());
        }
    }
}