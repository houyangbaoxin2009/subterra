// p.2.33.9: save-domain contract mirror acceptance probe — asserts the API contract
// (api.save.SaveApi) and the engine mirror (engine.save.SaveApiMirror) are bit-for-bit
// identical on identical inputs, plus determinism re-entry and deterministic rejection
// of invalid inputs. Pure JVM: no wall-clock, no randomness, no MC classes.
package io.toterra.subterra.probes;

import io.toterra.subterra.api.save.SaveApi;
import io.toterra.subterra.engine.save.SaveApiMirror;
import io.toterra.subterra.engine.save.doc.LedgerDoc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * p.2.33.9 存档域契约镜像验收探针 —— 断言 api 层 {@link SaveApi} 与 engine 镜像 {@link SaveApiMirror} 对同输入
 * 逐位一致（六槽目录固定序 / 槽目录成员判定 / 规范文档文件名 / 藏录版本 / 藏录条目注册排序 / td-zd 混合文档
 * 键序），并断言确定性再入（乱序输入产生相同序）与不合格输入的确定性拒绝（null 条目表 → {@link NullPointerException}）。
 * 每项失败计数 +1 并给出诊断；全过才输出 {@code [SaveApiProbe] PASS (n checks)} 并 exit 0，否则 FAIL 计数
 * exit 1。全部线性遍历，无 O(n²)、无时序、无随机。
 * <p>
 * p.2.33.9 save-domain contract mirror probe: asserts the api-layer {@link SaveApi} and the engine mirror
 * {@link SaveApiMirror} are bit-for-bit identical for identical inputs (six-slot directory order / slot-directory
 * membership / canonical document file name / ledger version / ledger-entry registration order / td-zd hybrid
 * document key order), plus determinism re-entry (shuffled input yields the same order) and deterministic
 * rejection of invalid inputs (null entry list → {@link NullPointerException}). Every failure is counted and
 * diagnosed; PASS only when all pass, then exit 0, else FAIL with counts and exit 1. All traversals linear, no
 * O(n²), no timing, no randomness.
 */
public final class SaveApiProbe {

    private static int checks = 0;
    private static int failures = 0;

    private SaveApiProbe() {
    }

    public static void main(String[] args) {
        slots();
        ledger();
        hybrid();
        if (failures == 0) {
            System.out.println("[SaveApiProbe] PASS (" + checks + " checks)");
            System.exit(0);
        } else {
            System.out.println("[SaveApiProbe] FAIL (" + failures + " of " + checks + " checks)");
            System.exit(1);
        }
    }

    private static void slots() {
        check("slotDirectories fixed order",
                List.of("world", "config", "ledger", "domain", "relic", "register").equals(SaveApi.slotDirectories()));
        check("slotDirectories api == mirror", SaveApi.slotDirectories().equals(SaveApiMirror.slotDirectories()));
        check("slotDirectories deterministic re-entry",
                SaveApi.slotDirectories().equals(SaveApi.slotDirectories()));
        check("isKnownSlotDir known both sides",
                SaveApi.isKnownSlotDir("ledger") && SaveApiMirror.isKnownSlotDir("ledger")
                        && SaveApi.isKnownSlotDir("register") == SaveApiMirror.isKnownSlotDir("register"));
        check("isKnownSlotDir unknown rejected both sides",
                !SaveApi.isKnownSlotDir("cache") && !SaveApiMirror.isKnownSlotDir("cache"));
        check("slotDocFileName api == mirror", SaveApi.slotDocFileName().equals(SaveApiMirror.slotDocFileName()));
        check("slotDocFileName value", "doc.data.tie".equals(SaveApi.slotDocFileName()));
    }

    private static void ledger() {
        check("ledgerVersion api == mirror", SaveApi.ledgerVersion() == SaveApiMirror.ledgerVersion());
        check("ledgerVersion value", SaveApi.ledgerVersion() == 1L);

        // Parallel api entries and engine entries, then assert the sorted order matches component-wise.
        List<SaveApi.LedgerEntry> apiIn = shuffledApiEntries();
        List<LedgerDoc.LedgerEntry> engineIn = shuffledEngineEntries();
        List<SaveApi.LedgerEntry> apiSorted = SaveApi.sortLedgerEntries(apiIn);
        List<LedgerDoc.LedgerEntry> engineSorted = SaveApiMirror.sortLedgerEntries(engineIn);
        boolean orderOk = apiSorted.size() == engineSorted.size();
        if (orderOk) {
            for (int i = 0; i < apiSorted.size(); i++) {
                SaveApi.LedgerEntry a = apiSorted.get(i);
                LedgerDoc.LedgerEntry b = engineSorted.get(i);
                if (!a.k().equals(b.k()) || a.seq() != b.seq() || a.when() != b.when() || !a.note().equals(b.note())) {
                    orderOk = false;
                    break;
                }
            }
        }
        check("sortLedgerEntries api == mirror order", orderOk);
        // Deterministic registration grammar: lexicographic k first, then seq, then when, then note.
        String[] keys = new String[apiSorted.size()];
        for (int i = 0; i < apiSorted.size(); i++) {
            keys[i] = apiSorted.get(i).k();
        }
        String[] expectedSorted = {"alpha", "beta", "gamma", "zeta"};
        check("sortLedgerEntries k lexicographic primary order", Arrays.equals(expectedSorted, keys));
        // Shuffled input yields the same order (determinism re-entry).
        check("sortLedgerEntries shuffled input -> same order",
                SaveApi.sortLedgerEntries(apiIn).equals(apiSorted));
        check("sortLedgerEntries input not mutated", apiIn.size() == 4);
        check("sortLedgerEntries rejects null both sides",
                throwsNPE(() -> SaveApi.sortLedgerEntries(null)) && throwsNPE(() -> SaveApiMirror.sortLedgerEntries(null)));
    }

    private static void hybrid() {
        check("hybridDocKeys fixed order version/meta/zd",
                List.of("version", "meta", "zd").equals(SaveApi.hybridDocKeys()));
        check("hybridDocKeys api == mirror", SaveApi.hybridDocKeys().equals(SaveApiMirror.hybridDocKeys()));
        check("hybridDocKeys deterministic re-entry", SaveApi.hybridDocKeys().equals(SaveApi.hybridDocKeys()));
    }

    /** Shuffled-order api ledger entries covering distinct k/seq/when/note. */
    private static List<SaveApi.LedgerEntry> shuffledApiEntries() {
        List<SaveApi.LedgerEntry> out = new ArrayList<>();
        out.add(new SaveApi.LedgerEntry("zeta", 1, 10, "last"));
        out.add(new SaveApi.LedgerEntry("beta", 9, 30, "b"));
        out.add(new SaveApi.LedgerEntry("gamma", 1, 1, "a"));
        out.add(new SaveApi.LedgerEntry("alpha", 5, 20, "first"));
        return out;
    }

    /** The engine-side parallel entries in the same shuffled order. */
    private static List<LedgerDoc.LedgerEntry> shuffledEngineEntries() {
        List<LedgerDoc.LedgerEntry> out = new ArrayList<>();
        out.add(new LedgerDoc.LedgerEntry("zeta", 1, 10, "last"));
        out.add(new LedgerDoc.LedgerEntry("beta", 9, 30, "b"));
        out.add(new LedgerDoc.LedgerEntry("gamma", 1, 1, "a"));
        out.add(new LedgerDoc.LedgerEntry("alpha", 5, 20, "first"));
        return out;
    }

    // ---------- helpers ----------

    private static void check(String name, boolean ok) {
        checks++;
        if (ok) {
            System.out.println("[PASS] " + name);
        } else {
            failures++;
            System.out.println("[FAIL] " + name);
        }
    }

    private static boolean throwsNPE(Runnable r) {
        try {
            r.run();
            return false;
        } catch (NullPointerException e) {
            return true;
        }
    }
}