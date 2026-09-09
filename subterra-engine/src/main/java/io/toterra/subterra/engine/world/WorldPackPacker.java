package io.toterra.subterra.engine.world;

import io.toterra.subterra.engine.config.Td;
import io.toterra.subterra.engine.config.TdTable;
import io.toterra.subterra.engine.config.TdValue;
import io.toterra.subterra.engine.datapack.DatapackPack;
import io.toterra.subterra.engine.save.SaveContainer;
import io.toterra.subterra.engine.save.SaveSlot;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * p.2.9.3 world-work-tree packer — one-shot pack/unpack of a whole world work
 * tree into the {@link WorldPack} single td document (the movable/interchangeable
 * artifact, p.2.9.1). {@code saveRoot} holds one canonical document per
 * {@link SaveSlot}, the file name {@value #DOC}, under each slot's lowercase
 * directory ({@code world/}, {@code config/}, {@code ledger/}, {@code domain/},
 * {@code relic/}, {@code register/}); {@code datapackDir} is the datapack
 * directory. Packing wraps them into a {@link SaveContainer} and exports the
 * world pack; unpacking rehydrates the world pack and writes the six slot
 * documents back plus the datapack directory.
 *
 * <p>Work-tree shape (the documents are normal td docs, one per slot directory):
 * <pre>{@code
 * saveRoot/
 *   world/doc.td    config/doc.td   ledger/doc.td
 *   domain/doc.td   relic/doc.td    register/doc.td
 * datapackDir/ ... (a normal datapack directory)
 * }</pre>
 * Determinism: only the six fixed {@link SaveSlot} directories are scanned (no
 * directory-global walk, no O(n²)); slot iteration follows the {@link SaveSlot}
 * enum order; a slot directory that is absent or whose {@code doc.td} is missing
 * is skipped (its slot is simply not attached on pack, not written on unpack).
 *
 * <p>The packed world pack is a portable artifact: it can be moved wholesale to
 * another environment / instance and unpacked there (this is the save-archive
 * interchange loop, p.2.9.1 round-trip). On unpack, write-back traversal guards
 * mirror {@code DatapackPack} ({@code resolve + normalize + startsWith}). Downstream,
 * p.2.8 {@code engine.sim} consumes the result via {@code WorldPackResult.meta()},
 * which reports steady-state keys such as {@code world.seed} / {@code world.tick}.
 *
 * <p>p.2.9.3 世界工作树打包器——把整个世界工作树一次性打包/还原成 {@link WorldPack} 单一 td
 * 文档（可搬移/互导的产物，p.2.9.1）。{@code saveRoot} 下每个 {@link SaveSlot} 对应一份规范
 * 文档 {@value #DOC}，落在该槽小写目录（{@code world/}、{@code config/}、{@code ledger/}、
 * {@code domain/}、{@code relic/}、{@code register/}）；{@code datapackDir} 为数据包目录。
 * 打包把它们收进一个 {@link SaveContainer} 后导出世界包；还原先再水化世界包，再把六槽文档
 * 写回并还原数据包目录。
 *
 * <p>工作树形态（每槽目录一份普通 td 文档）：
 * <pre>{@code
 * saveRoot/
 *   world/doc.td    config/doc.td   ledger/doc.td
 *   domain/doc.td   relic/doc.td    register/doc.td
 * datapackDir/ ... （普通数据包目录）
 * }</pre>
 * 确定性：只扫描六个固定 {@link SaveSlot} 目录（无目录级全局遍历、无 O(n²)）；槽迭代按
 * {@link SaveSlot} 枚举序；目录缺失或 {@code doc.td} 缺失的槽会被跳过（打包时不挂载该槽，
 * 还原时不写该目录）。
 *
 * <p>打包出的世界包是可搬移产物：可整体搬到另一环境/实例再到彼处还原（这就是存档互导
 * 循环，p.2.9.1 往返）。还原写回路径穿越防护同 {@code DatapackPack}
 * （{@code resolve + normalize + startsWith}）。下游 p.2.8 {@code engine.sim} 经
 * {@code WorldPackResult.meta()} 消费结果，报出 {@code world.seed} / {@code world.tick}
 * 等稳态键。
 */
public final class WorldPackPacker {

    /** Canonical document file name inside each slot directory. */
    private static final String DOC = "doc.td";

    private WorldPackPacker() {
    }

    /**
     * Packs a world work tree into a single td {@link WorldPack} document:
     * reads each present slot's {@code doc.td} (in {@link SaveSlot} enum order),
     * attaches it to a fresh {@link SaveContainer}, then exports the world pack
     * with {@code datapackDir} and the sorted {@code meta}. Deterministic; the
     * slots are never mutated.
     *
     * 把世界工作树打包成单一 td {@link WorldPack} 文档：按 {@link SaveSlot} 枚举序读取各
     * 存在槽的 {@code doc.td}，挂进新 {@link SaveContainer}，再用 {@code datapackDir} 与
     * 排序后的 {@code meta} 导出世界包。确定性；槽不被改动。
     */
    public static String pack(Path saveRoot, Path datapackDir, Map<String, TdValue> meta) {
        Path root = saveRoot.toAbsolutePath().normalize();
        SaveContainer container = new SaveContainer();
        for (SaveSlot slot : SaveSlot.values()) {
            Path docFile = root.resolve(slot.dir()).resolve(DOC);
            if (Files.isRegularFile(docFile)) {
                container.attach(slot, Td.parse(readText(docFile)));
            }
        }
        return WorldPack.export(container, datapackDir, meta);
    }

    /**
     * Unpacks a {@link WorldPack} document back into a world work tree: writes
     * each mounted slot's document to {@code saveTarget}/{@code slot.dir()}/doc.td
     * (enum order, missing slots skipped) and restores the datapack directory to
     * {@code datapackTarget}. Returns the rehydrated {@link WorldPackResult}.
     * Write-back traversal uses the same {@code resolve+normalize+startsWith}
     * guard as {@code DatapackPack}.
     *
     * 把 {@link WorldPack} 文档还原回世界工作树：把每个已挂载槽的文档写到
     * {@code saveTarget}/{@code slot.dir()}/doc.td（枚举序，缺失槽跳写），并把数据包目录还原
     * 到 {@code datapackTarget}。返回再水化后的 {@link WorldPackResult}。写回路径穿越防护与
     * {@code DatapackPack} 相同（{@code resolve+normalize+startsWith}）。
     */
    public static WorldPackResult unpack(String source, Path saveTarget, Path datapackTarget) {
        WorldPackResult result = WorldPack.rehydrate(source);
        Path target = saveTarget.toAbsolutePath().normalize();
        for (SaveSlot slot : SaveSlot.values()) {
            TdTable doc = result.save().document(slot);
            if (doc == null) {
                continue; // missing slot — skip write
            }
            Path dir = target.resolve(slot.dir()).normalize();
            if (!dir.startsWith(target)) {
                throw new IllegalArgumentException("world pack save path escapes target: " + slot.dir());
            }
            Path docFile = dir.resolve(DOC);
            try {
                Files.createDirectories(dir);
                Files.writeString(docFile, Td.write(doc));
            } catch (IOException e) {
                throw new UncheckedIOException("world pack save write failed: " + docFile, e);
            }
        }
        DatapackPack.unpack(result.datapackDocument(), datapackTarget);
        return result;
    }

    // ---------- helpers ----------

    private static String readText(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException e) {
            throw new UncheckedIOException("world pack read failed: " + file, e);
        }
    }
}