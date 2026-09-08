package io.toterra.subterra.probes;

import io.toterra.subterra.engine.optim.logic.pronounce.Lexicon;
import io.toterra.subterra.engine.optim.logic.pronounce.PronounceMatcher;

import java.util.List;

/**
 * Deterministic acceptance probe for the multi-language pronunciation engine
 * core (p.1.4.3, JEC-style pinyin search): lexicon parsing/merging, per-
 * character consuming match, multi-reading, extension-driven scripts
 * (kana romaji), case-insensitivity, negative cases. Pure JVM.
 */
public final class PronounceProbe {

    private PronounceProbe() {
    }

    private static int failures = 0;

    private static void check(String name, boolean ok) {
        if (ok) {
            System.out.println("[PASS] " + name);
        } else {
            failures++;
            System.out.println("[FAIL] " + name);
        }
    }

    public static void main(String[] args) {
        Lexicon zh = Lexicon.ofLines(List.of(
                "宝: bao3",
                "杯: bei1",
                "山: shan1",
                "水: shui3",
                "乐: yue4, le4",
                "// comment line ignored",
                "  "));
        PronounceMatcher m = new PronounceMatcher(zh);

        check("single char full query", m.contains("宝", "bao"));
        check("two char sequence", m.contains("宝杯", "baobei"));
        check("inner char match", m.contains("山水", "shui"));
        check("prefix query within char", m.contains("宝杯", "baob"));
        check("multi reading both match", m.contains("乐", "yue") && m.contains("乐", "le"));
        check("case insensitive", m.contains("山", "SHAN"));
        check("no match rejected", !m.contains("山", "shui"));
        check("empty query matches", m.contains("水", ""));
        check("empty haystack no match", !m.contains("", "shui"));

        // Multi-language extension: kana → romaji, merged in and isolated.
        Lexicon ja = Lexicon.ofLines(List.of(
                "か: ka",
                "く: ku",
                "ん: n"));
        check("extension unknown before merge", !new PronounceMatcher(zh).contains("か", "ka"));
        Lexicon merged = zh.withExtension(ja);
        PronounceMatcher mx = new PronounceMatcher(merged);
        check("extension matches after merge", mx.contains("かく", "kaku"));
        check("extension overrides nothing for zh", mx.contains("宝", "bao"));
        check("kana negative", !mx.contains("か", "ku"));
        check("legend merged keeps both", mx.contains("かくん", "kakun"));

        // Malformed lexicon line rejected.
        try {
            Lexicon.ofLines(List.of("badline-no-colon"));
            check("malformed line rejected", false);
        } catch (IllegalArgumentException expected) {
            check("malformed line rejected", true);
        }

        if (failures == 0) {
            System.out.println("[PronounceProbe] PASS (multilingual pronunciation engine)");
            System.exit(0);
        } else {
            System.out.println("[PronounceProbe] FAIL: " + failures + " assertion(s)");
            System.exit(1);
        }
    }
}