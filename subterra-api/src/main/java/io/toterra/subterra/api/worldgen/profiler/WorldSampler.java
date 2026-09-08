package io.toterra.subterra.api.worldgen.profiler;

/**
 * Abstract reading of a Minecraft world into pure data (p.1.8.30), so the
 * world-profiler engine can run decoupled from any MC probe. A concrete binding
 * (supplied by the MC layer or a pure-JDK mirror) answers world queries; the
 * engine only depends on this interface and the report records.
 * <p>
 * 把 Minecraft 世界抽象读为纯数据（p.1.8.30），使分析器引擎可脱离 MC 探针运行。
 * 具体绑定（由 MC 层或纯 JDK 镜像提供）答复世界查询；引擎只依赖此接口与报告记录。
 */
public interface WorldSampler {

    /**
     * Returns the first solid surface block's Y at a column, or the dimension
     * volume midpoint when the column is fully open to the sky.
     */
    int surfaceY(int x, int z);

    /** The namespaced block id at a position, e.g. "minecraft:stone". */
    String block(int x, int y, int z);

    /** The namespaced biome id at a position, e.g. "minecraft:plains". */
    String biome(int x, int y, int z);

    /**
     * The router density-array field value at a position; field names match
     * NoiseRouter's FIELD_NAMES.
     */
    double routerField(String fieldName, int x, int y, int z);

    /** The sea-level Y of the dimension. */
    int seaLevel();

    /** The lowest build Y of the dimension. */
    int minY();

    /** The exclusive upper build Y of the dimension. */
    int maxY();
}