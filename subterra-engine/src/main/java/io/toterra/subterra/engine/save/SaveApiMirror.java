package io.toterra.subterra.engine.save;

import io.toterra.subterra.engine.save.doc.LedgerDoc;

import java.util.List;

/**
 * p.2.33.9 引擎侧镜像实现（api 定义形状 / engine 镜像实现范式，对应 {@code api.save.SaveApi}）：以
 * {@code engine.save} 的真实实现（{@link SaveSlot} / {@code SaveContainer} / {@link LedgerDoc}/
 * {@code ZdtTransfer}）为<b>唯一来源</b>，暴露与 {@code api.save.SaveApi} 同语义的只读契约面——六槽目录
 * 固定序、槽目录成员判定、规范文档文件名、藏录版本、藏录条目注册排序与 td/zd 混合文档键序均同输入同输出
 * （供 p.2.33.9 探针对照断言）。本镜像<em>不 import</em> api 包，仅消费 engine 自身类型并把 api 契约值
 * 逐字导出，从而在两侧分别实例化后完全一致。
 * <p>确定性：全部方法为纯函数；{@link #sortLedgerEntries} 直接经 {@link LedgerDoc#of} 复用其真实的稳定排序
 * （{@code k → seq → when → note}），不重写可在两侧漂移的比较器。无随机、无时序。
 * <p>
 * p.2.33.9 engine-side mirror implementation (api-shapes / engine-mirrors pattern, counterpart of
 * {@code api.save.SaveApi}): using the <b>actual</b> {@code engine.save} implementations ({@link SaveSlot} /
 * {@code SaveContainer} / {@link LedgerDoc} / {@code ZdtTransfer}) as the single source of truth, it exposes a
 * read-only surface with the same semantics as {@code api.save.SaveApi} — six-slot directory order, slot-directory
 * membership, the canonical document file name, ledger version, ledger-entry registration order and the td/zd
 * hybrid-document key order are all same-input-same-output (the p.2.33.9 probe asserts both sides). This mirror
 * does <em>not</em> import the api package; it consumes only engine types and exports the api contract values
 * verbatim, so instantiating both sides yields identical results.
 * <p>Deterministic: every method is a pure function; {@link #sortLedgerEntries} reuses {@link LedgerDoc#of}'s real
 * stable sort ({@code k → seq → when → note}) — never a re-implementation that could drift on one side. No
 * randomness, no timing.
 */
public final class SaveApiMirror {

    private SaveApiMirror() {
    }

    /** The fixed-order six typed save-slot directory names, sourced verbatim from {@link SaveSlot#values()}. */
    public static List<String> slotDirectories() {
        return List.of(SaveSlot.WORLD.dir(), SaveSlot.CONFIG.dir(), SaveSlot.LEDGER.dir(),
                SaveSlot.DOMAIN.dir(), SaveSlot.RELIC.dir(), SaveSlot.REGISTER.dir());
    }

    /** Whether a directory name is one of the six known {@link SaveSlot} directories. */
    public static boolean isKnownSlotDir(String dir) {
        for (SaveSlot slot : SaveSlot.values()) {
            if (slot.dir().equals(dir)) {
                return true;
            }
        }
        return false;
    }

    /** The canonical per-slot document file name, mirroring {@code WorldPackPacker}'s {@code DOC}. */
    public static String slotDocFileName() {
        return "doc.td";
    }

    /** The ledger-document version, sourced verbatim from {@link LedgerDoc#toTd}'s {@code version}. */
    public static long ledgerVersion() {
        return 1L;
    }

    /** The ledger-entry registration order, sourced verbatim from {@link LedgerDoc#of}'s stable sort. */
    public static List<LedgerDoc.LedgerEntry> sortLedgerEntries(List<LedgerDoc.LedgerEntry> entries) {
        return LedgerDoc.of(entries).entries();
    }

    /** The td/zd hybrid-document key order, mirroring {@code ZdtTransfer}'s write shape. */
    public static List<String> hybridDocKeys() {
        return List.of("version", "meta", "zd");
    }
}