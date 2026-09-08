package io.toterra.subterra.engine.worldgen.pipeline.dimension;

import io.toterra.subterra.api.worldgen.EcoDim;
import io.toterra.subterra.engine.worldgen.pipeline.density.Density;

/**
 * An immutable per-dimension slot (Step 2): pairs the ecosystem dimension
 * {@link EcoDim} with the {@link Density} field that fills it and the
 * {@link DimAlgo} describing how that field is produced. All parts are
 * non-null; the compact constructor validates via
 * {@link IllegalArgumentException}. Pure data — deterministic and thread-safe.
 * <p>
 * 单个不可变的维度槽（第 2 步）：将生态维度 {@link EcoDim} 与填充它的
 * {@link Density} 场以及描述其产出方式的 {@link DimAlgo} 组合在一起。所有部分
 * 均非空；紧凑构造器以 {@link IllegalArgumentException} 校验。纯数据——确定且线程安全。
 *
 * @param dim      the ecosystem dimension (never null)
 * @param density  the field filling the dimension (never null)
 * @param algo     the algorithm producing the field (never null)
 */
public record DimensionSlot(EcoDim dim, Density density, DimAlgo algo) {

    /** Compact constructor enforcing non-null parts via IllegalArgumentException. */
    public DimensionSlot {
        if (dim == null) {
            throw new IllegalArgumentException("dim must not be null");
        }
        if (density == null) {
            throw new IllegalArgumentException("density must not be null");
        }
        if (algo == null) {
            throw new IllegalArgumentException("algo must not be null");
        }
    }
}