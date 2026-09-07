package io.toterra.subterra.optim.worldgen.pipeline.surface;

/**
 * Deterministic surface action (p.1.8.5, self-developed): outputs a block-state id
 * string (e.g. {@code "minecraft:grass_block"}) for the given context, or
 * {@code null} to indicate "no override". Pure function, no randomness.
 * <p>
 * 表面动作（p.1.8.5，自研）：为给定上下文输出方块状态 ID 字符串（如{@code "minecraft:grass_block"}），
 * 或返回 {@code null} 表示不覆盖。纯函数、无随机。
 */
@FunctionalInterface
public interface SurfaceAction {

    /** Computes the target block-state id, or {@code null} for no override. */
    String apply(SurfaceContext c);
}