package io.toterra.subterra.engine.worldgen.pipeline.dimension;

import io.toterra.subterra.engine.worldgen.pipeline.density.Density;

/**
 * The Plan A diffusion-model field-generator seam (design §5, Step 5): a pure
 * contract that fills a {@link FieldRegion} buffer with the output of a learned
 * model backend. Interfaces only — no backend is loaded or invoked by any
 * default in this batch; {@link ModelBackend#available()} is still {@code
 * false}. A concrete implementor (local OpenVINO / tink-form, per the
 * decentralization &amp; performance discipline) fills {@code region} cell by
 * cell and returns; the adapter {@link #toDensity(ModelFieldGenerator,
 * RegionCoverer, OverworldBounds)} then exposes the filled region as a
 * deterministic {@link Density} with no further model contact on the hot path.
 * <p>
 * Plan A 扩散模型场生成接缝（设计 §5，第 5 步）：将一个已学习模型后端的输出填充到
 * {@link FieldRegion} 缓冲的纯粹契约。本批次仅接口——默认构造不加载也不调用任何后端；
 * {@link ModelBackend#available()} 仍为 {@code false}。具体实现（本地 OpenVINO /
 * tink-form，符合去中心化与性能纪律）逐格填充 {@code region} 后返回；适配器
 * {@link #toDensity(ModelFieldGenerator, RegionCoverer, OverworldBounds)} 随后把已
 * 填充区域暴露为确定的 {@link Density}，热路径上不再接触模型。
 */
@FunctionalInterface
public interface ModelFieldGenerator {

    /**
     * Fills the given region with this generator's field output. Implementations
     * write exactly {@code region.data().length} cells deterministically and
     * return; they must not touch any other state.
     *
     * @param region the buffer to fill (never null)
     */
    void fill(FieldRegion region);

    /**
     * A callback that produces the {@link FieldRegion} buffering the model's
     * output over the given {@link OverworldBounds}. It picks the finite
     * block-space box (vertical extent typically from {@code bounds}; a finite
     * horizontal window chosen by the caller) that {@code fill} will populate
     * and that the resulting {@link Density} can sample.
     * <p>
     * 生成缓冲模型的 {@link FieldRegion} 的回调，覆盖给定的 {@link OverworldBounds}。
     * 由调用方挑选有限方块空间盒（纵向范围通常取自 {@code bounds}；水平窗口为有限值），
     * 随后 {@code fill} 将填充它，得到的 {@link Density} 将采样它。
     */
    @FunctionalInterface
    interface RegionCoverer {

        /**
         * Builds the region covering {@code bounds}.
         *
         * @param bounds the overworld vertical extent the region covers
         * @return the region to fill (never null)
         */
        FieldRegion cover(OverworldBounds bounds);
    }

    /**
     * Adapts a generator plus a region-covering callback into a deterministic
     * {@link Density}. The workflow of the adapter is:
     * <ol>
     *   <li>obtain the covering region via {@code coverer.cover(bounds)};</li>
     *   <li>fill it once via {@code gen.fill(region)};</li>
     *   <li>return a {@link Density} whose {@code eval} samples that now-fixed
     *       buffer ({@link FieldRegion#sample(double, double, double)}).</li>
     * </ol>
     * Purity contract: the adapter itself is a side-effect-free bridge — with a
     * deterministic {@code gen} and {@code coverer} the resulting {@link Density}
     * is deterministic, performs no model inference after {@code fill}, and its
     * {@code eval} is allocation-free. The only mutation is the one-time {@code
     * fill} of the freshly allocated buffer; {@code eval} throws
     * {@link IllegalArgumentException} when a sample falls outside the buffered
     * region, and does not silently invent values.
     * <p>
     * 纯函数契约：适配器本身是一段无副作用的桥接——在 {@code gen} 与 {@code coverer} 均
     * 确定的前提下，得到的 {@link Density} 确定，{@code fill} 之后不再执行任何模型推理，
     * 且其 {@code eval} 无分配。唯一的变化是一次性地将新分配缓冲执行 {@code fill}；
     * 当采样点落在缓冲区域之外时，{@code eval} 抛出 {@link IllegalArgumentException}，
     * 并不静默编造数值。
     *
     * @param gen    the field generator whose output is buffered (never null)
     * @param coverer the callback producing the buffered region (never null)
     * @param bounds the overworld extent to cover (never null)
     * @return a {@link Density} reading the filled region
     * @throws IllegalArgumentException if any argument is null, or
     *         {@code coverer.cover(bounds)} returns null
     */
    static Density toDensity(ModelFieldGenerator gen, RegionCoverer coverer, OverworldBounds bounds) {
        if (gen == null) {
            throw new IllegalArgumentException("gen must not be null");
        }
        if (coverer == null) {
            throw new IllegalArgumentException("coverer must not be null");
        }
        if (bounds == null) {
            throw new IllegalArgumentException("bounds must not be null");
        }
        FieldRegion region = coverer.cover(bounds);
        if (region == null) {
            throw new IllegalArgumentException("coverer.cover(bounds) must not return null");
        }
        gen.fill(region);
        return region::sample;
    }
}