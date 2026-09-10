package io.toterra.subterra.engine.export;

import io.toterra.subterra.engine.config.TdValue;
import io.toterra.subterra.engine.save.SaveContainer;
import io.toterra.subterra.engine.world.WorldPack;
import io.toterra.subterra.engine.world.WorldPackResult;

import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;

/**
 * p.2.18.1 世界包功能性生产者 —— 把某个世界包源物（{@link SaveContainer} + 数据包目录 +
 * {@code meta}）经 {@link WorldPack} 确定性地产出为 td 文档（{@code exportTd()}）与其 zd v2
 * 二进制变体（{@code exportZd()}）。回水化恒等由世界包的往返契约保证（p.2.9.4 探针：
 * {@code export(rehydrate(w).save(), srcDatapack, meta).equals(w)} 逐字节）：世界包归档的
 * {@code rehydrate} 需要原数据包目录与 {@code meta} 作为再导出输入，故按「端口持有源物并缓存
 * 重建」由本生产者持有二者并在重建时复用；{@code rehydrateTd}/{@code rehydrateZd} 从输入重建
 * 出容器后再导出，调用方可断言 {@code td.equals(rehydrateTd(td))} 与
 * {@code Arrays.equals(zd, rehydrateZd(zd))}。确定性、纯读——源容器不被改动；数据包目录在两次
 * 调用间保持不变（与 p.2.9.4 探针判据一致）。
 *
 * <p>p.2.18.1 world-pack functional producer — deterministically renders a world-pack
 * source ({@link SaveContainer} + datapack directory + {@code meta}) through
 * {@link WorldPack} as a td document ({@code exportTd()}) and its zd v2 binary variant
 * ({@code exportZd()}). Rehydrate identity is guaranteed by the world pack's round-trip
 * contract (p.2.9.4 probe:
 * {@code export(rehydrate(w).save(), srcDatapack, meta).equals(w)} byte-for-byte): the
 * world-pack archive's {@code rehydrate} needs the original datapack directory and
 * {@code meta} as re-export inputs, so per "the port holds the source and caches the
 * rebuild" this producer holds both and reuses them on rebuild;
 * {@code rehydrateTd}/{@code rehydrateZd} rebuild the container from their input and
 * re-export, so the caller may assert {@code td.equals(rehydrateTd(td))} and
 * {@code Arrays.equals(zd, rehydrateZd(zd))}. Deterministic, read-only — the source
 * container is never mutated; the datapack directory must stay unchanged between calls
 * (same oracle as the p.2.9.4 probe).
 */
public final class WorldPackProducer implements ExportProducer {

    private final SaveContainer save;
    private final Path datapackDir;
    private final Map<String, TdValue> meta;

    public WorldPackProducer(SaveContainer save, Path datapackDir, Map<String, TdValue> meta) {
        this.save = Objects.requireNonNull(save, "world pack save must be non-null");
        this.datapackDir = Objects.requireNonNull(datapackDir, "world pack datapack dir must be non-null");
        this.meta = meta; // null tolerated as empty, mirrors WorldPack.export
    }

    @Override
    public String tdType() {
        return ExportKind.WORLD_PACK.form();
    }

    @Override
    public String exportTd() {
        return WorldPack.export(save, datapackDir, meta);
    }

    @Override
    public byte[] exportZd() {
        return ExportHub.zdOf(exportTd());
    }

    @Override
    public String rehydrateTd(String td) {
        return reexport(WorldPack.rehydrate(td));
    }

    @Override
    public byte[] rehydrateZd(byte[] zd) {
        return ExportHub.zdOf(reexport(WorldPack.rehydrate(ExportHub.tdOf(zd))));
    }

    /** Rebuilds the container from a rehydrated result and re-exports with the held dir/meta. */
    private String reexport(WorldPackResult rebuilt) {
        return WorldPack.export(rebuilt.save(), datapackDir, meta);
    }
}
