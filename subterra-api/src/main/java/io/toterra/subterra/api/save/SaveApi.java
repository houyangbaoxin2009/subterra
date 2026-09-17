package io.toterra.subterra.api.save;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * p.2.33.9 对外存档域契约门面（final class、静态纯函数、纯 JDK）：确定性接口面，供上层/域模组按
 * 「SaveContainer 六槽固定序 + 文档形态常量 + 藏录 Ledger 条目注册契约」使用 engine.save 域的确定性
 * 语义。语义与 {@code engine.save} 的 {@code SaveSlot}/{@code SaveContainer}/{@code LedgerDoc}/
 * {@code ZdtTransfer}（p.2.3.1/.3/.4/.6）一致——本处为契约与数据面注入，engine 为实现镜像
 * （{@code engine.save.SaveApiMirror}），api 不依赖 engine。所有常量/语义均从 engine 实际行为逐字对照
 * 落于此，禁止拍脑袋数值。
 * <p>确定性：{@link #slotDirectories()} 返回六类类型化槽的小写目录名固定序（镜像 {@code SaveSlot}）；
 * {@link #isKnownSlotDir} / {@link #slotDocFileName} 提供槽目录成员的确定性判定与规范文档文件名
 * ({@code doc.data.tie})；{@link #ledgerVersion()} 返回藏录文档版本；{@link #sortLedgerEntries} 以确定性命名词法
 * {@code (k 词法序 → seq → when → note)} 归一化藏录条目序（镜像 {@code LedgerDoc} 的稳定排序，同输入逐字节
 * 一致、乱序输入产出相同序）；{@link #hybridDocKeys()} 返回 td/zd 混合文档 {@code version/meta/zd} 的固定
 * 键序（镜像 {@code ZdtTransfer} 的写序）。全部为纯函数：无随机、无墙钟、无迭代序依赖；{@link #sortLedgerEntries}
 * 单趟拷贝 + 稳定排序，无 O(n²)。无状态、无副作用。<b>诚实界限</b>：td/zd 与存档导出的 I/O 往返恒等由
 * engine 的 {@code ZdtTransfer}（p.2.3.3）/{@code SaveExportArchive}（p.2.3.6）在运行时断言（对其探针任务），
 * 本契约面只锁定其文档形态常量与条目排序语义，不复制 I/O 实现。
 * <p>
 * p.2.33.9 the external save-domain contract facade (final class, static pure functions, pure JDK): a
 * deterministic interface surface for upper layers / domain mods to use the deterministic semantics of
 * {@code engine.save}'s six-slot container order + document-shape constants + ledger entry registration
 * contract. Semantics match {@code SaveSlot}/{@code SaveContainer}/{@code LedgerDoc}/{@code ZdtTransfer}
 * (p.2.3.1/.3/.4/.6) — this is the contract and data-injection surface for the engine to mirror as its
 * implementation ({@code engine.save.SaveApiMirror}), and the api does not depend on the engine. Every
 * constant/semantic here is pinned verbatim from the engine's actual behaviour — nothing is guessed.
 * <p>Deterministic: {@link #slotDirectories()} returns the fixed-order lowercase directory names of the six typed
 * slots (mirrors {@code SaveSlot}); {@link #isKnownSlotDir} / {@link #slotDocFileName} give a deterministic
 * membership test over slot directories and the canonical document file name ({@code doc.data.tie});
 * {@link #ledgerVersion()} returns the ledger-document version; {@link #sortLedgerEntries} normalises the ledger
 * entry order by the deterministic naming grammar {@code (k lexicographic → seq → when → note)} (mirrors
 * {@code LedgerDoc}'s stable sort; same input yields identical order, shuffled input yields the same order);
 * {@link #hybridDocKeys()} returns the fixed key order {@code version/meta/zd} of a td/zd hybrid document
 * (mirrors {@code ZdtTransfer}'s write order). All pure functions: no randomness, no wall-clock, no
 * iteration-order dependence; {@link #sortLedgerEntries} is a single-pass copy + stable sort, no O(n²). Stateless,
 * side-effect free. <b>Honest boundary</b>: the td/zd and save-archive I/O round-trip identity is asserted at
 * runtime by the engine's {@code ZdtTransfer} (p.2.3.3) / {@code SaveExportArchive} (p.2.3.6) (for their probe
 * tasks); this contract surface only pins the document-shape constants and the entry-sort semantics and does not
 * reproduce the I/O implementation.
 */
public final class SaveApi {

    /** A ledger record, mirroring {@code engine.save.doc.LedgerDoc.LedgerEntry}. 
     *  一条藏录记录，镜像 {@code engine.save.doc.LedgerDoc.LedgerEntry}。 */
    public record LedgerEntry(String k, long seq, long when, String note) {

        /** Null-guards {@code k}. / 空值防御 {@code k}。 */
        public LedgerEntry {
            Objects.requireNonNull(k, "ledger entry k must not be null");
        }
    }

    private SaveApi() {
    }

    /** The fixed-order six typed save-slot directory names ({@code world/config/ledger/domain/relic/register}),
     *  mirroring {@code engine.save.SaveSlot}. / 六类类型化存档槽的小写目录名固定序
     *  （{@code world/config/ledger/domain/relic/register}），镜像 {@code engine.save.SaveSlot}。 */
    public static List<String> slotDirectories() {
        return List.of("world", "config", "ledger", "domain", "relic", "register");
    }

    /** Whether a directory name is one of the six known save-slot directories (mirrors the membership of
     *  {@code SaveSlot#dir} over the enum). / 目录名是否为六个已知存档槽目录之一（镜像 {@code SaveSlot} 枚举
     *  {@code dir} 的成员判定）。 */
    public static boolean isKnownSlotDir(String dir) {
        return slotDirectories().contains(dir);
    }

    /** The canonical per-slot document file name ({@code "doc.data.tie"}, mirroring {@code WorldPackPacker}'s
     *  <code>DOC</code>). / 每槽规范文档文件名（{@code "doc.data.tie"}，镜像 {@code WorldPackPacker} 的
     *  {@code DOC}）。 */
    public static String slotDocFileName() {
        return "doc.data.tie";
    }

    /** The ledger-document version ({@code 1}, mirroring {@code LedgerDoc#toTd}'s {@code version}). /
     *  藏录文档版本（{@code 1}，镜像 {@code LedgerDoc#toTd} 的 {@code version}）。 */
    public static long ledgerVersion() {
        return 1L;
    }

    /**
     * Normalises {@code entries} into the deterministic ledger registration order
     * {@code (k lexicographic → seq → when → note)}, mirroring {@code engine.save.doc.LedgerDoc}'s stable sort
     * (same input → identical order; shuffled input → the same order). Input is not mutated. Pure and deterministic.
     * / 将 {@code entries} 归一化为确定性藏录注册序 {@code (k 词法序 → seq → when → note)}，镜像
     * {@code engine.save.doc.LedgerDoc} 的稳定排序（同输入 → 相同序；乱序输入 → 相同序）。不改入参。纯函数且确定性。
     *
     * @param entries the ledger entries to sort (non-null).
     * @return a new, sorted immutable list.
     * @throws NullPointerException if {@code entries} is null.
     */
    public static List<LedgerEntry> sortLedgerEntries(List<LedgerEntry> entries) {
        Objects.requireNonNull(entries, "entries must not be null");
        List<LedgerEntry> copy = new ArrayList<>(entries);
        copy.sort(Comparator.comparing(LedgerEntry::k)
                .thenComparingLong(LedgerEntry::seq)
                .thenComparingLong(LedgerEntry::when)
                .thenComparing(LedgerEntry::note));
        return List.copyOf(copy);
    }

    /** The fixed key order of a td/zd hybrid document ({@code version → meta → zd}),
     *  mirroring {@code engine.save.migrate.ZdtTransfer}'s write shape. /
     *  td/zd 混合文档的固定键序（{@code version → meta → zd}），镜像
     *  {@code engine.save.migrate.ZdtTransfer} 的写序。 */
    public static List<String> hybridDocKeys() {
        return List.of("version", "meta", "zd");
    }
}