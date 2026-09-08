package io.toterra.subterra.engine.worldgen.pipeline.dimension;

import io.toterra.subterra.engine.worldgen.pipeline.density.Density;

/**
 * The Plan A per-point diffusion-model seam (design §5, Step 5): a learned
 * model that predicts a scalar at a continuous block coordinate. Interfaces
 * only — no backend is loaded or invoked by any default in this batch.
 * {@link #toDensity()} adapts the prediction directly into a
 * {@link io.toterra.subterra.engine.worldgen.pipeline.density.Density}, adding
 * no buffering. Deterministic: identical {@code (x, y, z)} always yield the
 * identical prediction for a fixed model.
 * <p>
 * Plan A 逐点扩散模型接缝（设计 §5，第 5 步）：在连续方块坐标处预测一个标量的已学习
 * 模型。本批次仅接口——默认构造不加载也不调用任何后端。{@link #toDensity()} 直接把
 * 预测适配为 {@link io.toterra.subterra.engine.worldgen.pipeline.density.Density}，
 * 不引入缓冲。确定：对固定模型，相同的 {@code (x, y, z)} 总是得到相同的预测。
 */
@FunctionalInterface
public interface ModelPointDensity {

    /**
     * Predicts the scalar density at the given continuous block coordinate.
     *
     * @param x the continuous block x
     * @param y the continuous block y
     * @param z the continuous block z
     * @return the predicted scalar
     */
    double predict(double x, double y, double z);

    /**
     * Adapts this model directly into a {@link Density}, returning the
     * per-point prediction with no buffering or interpolation.
     *
     * @return a {@link Density} delegating to {@link #predict} on every sample
     */
    default Density toDensity() {
        return this::predict;
    }
}