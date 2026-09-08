package io.toterra.subterra.engine.worldgen.pipeline.surface;

/**
 * Deterministic predicate deciding whether a surface action applies at a block
 * (p.1.8.5, self-developed, mirroring the MC {@code SurfaceRules} condition seam
 * without upstream code): a pure function {@code SurfaceContext -> boolean} that
 * is constant-time and free of randomness.
 * <p>
 * 表面规则条件谓词（p.1.8.5，自研）：{@code SurfaceContext -> boolean} 的纯函数，常数时间、无随机。
 */
@FunctionalInterface
public interface SurfaceCondition {

    /** Tests whether the condition holds for the given context. */
    boolean test(SurfaceContext c);
}