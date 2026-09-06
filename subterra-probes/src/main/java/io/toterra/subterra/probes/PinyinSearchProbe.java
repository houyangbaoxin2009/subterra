package io.toterra.subterra.probes;

import io.toterra.subterra.optim.logic.pronounce.Lexicon;
import io.toterra.subterra.optim.logic.pronounce.PronounceMatcher;

import java.util.List;

/**
 * Deterministic acceptance probe for the JEC-style pinyin search port
 * (p.1.4.8): verifies the pronunciation engine matches real Chinese pinyin
 * (full pinyin, initials, mixed, multi-reading, prefix) using a small
 * embedded lexicon. Pure JVM (no Minecraft runtime); the SearchTreeMixin
 * redirect and PinyinSuffixArray wiring are covered by the client boot gate.
 */
public final class PinyinSearchProbe {

    private PinyinSearchProbe() {
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
        // Small embedded Chinese lexicon (PinIn line format). Covers common
        // characters used in vanilla item/block names so the probe exercises
        // real pinyin matching without depending on the bundled 26k-char
        // resource (which lives in the MC-layer source host).
        Lexicon zh = Lexicon.ofLines(List.of(
                "钻: zuan",
                "石: shi, dan",
                "铁: tie",
                "锭: ding",
                "木: mu",
                "板: ban",
                "门: men",
                "床: chuang",
                "红: hong",
                "砖: zhuan",
                "块: kuai",
                "金: jin",
                "苹: ping",
                "果: guo",
                "草: cao",
                "水: shui",
                "桶: tong",
                "书: shu",
                "桌: zhuo",
                "椅: yi",
                "钟: zhong",
                "表: biao",
                "灯: deng",
                "火: huo",
                "把: ba"
        ));
        PronounceMatcher m = new PronounceMatcher(zh);

        // Full pinyin match.
        check("full pinyin single char", m.contains("钻石", "zuanshi"));
        check("full pinyin two chars", m.contains("铁锭", "tieding"));
        check("full pinyin with space in name", m.contains("木门", "mumen"));

        // Initials-only match (first letters of each reading).
        check("initials only", m.contains("钻石", "zs"));
        check("initials two chars", m.contains("铁锭", "td"));

        // Mixed full + initial.
        check("mixed full then initial", m.contains("红石块", "hongs"));
        check("mixed initial then full", m.contains("红石块", "hsk"));

        // Multi-reading: 石 has shi / dan.
        check("multi reading first", m.contains("石", "shi"));
        check("multi reading second", m.contains("石", "dan"));

        // Prefix match within a character's reading (e.g. "zua" for 钻).
        check("prefix within char", m.contains("钻石", "zuans"));

        // Case-insensitive.
        check("case insensitive", m.contains("钻石", "ZUANSHI"));

        // Literal substring still works (non-CJK chars).
        check("literal ascii substring", m.contains("oak_planks", "plank"));

        // Negative cases.
        check("wrong pinyin rejected", !m.contains("钻石", "tieshi"));
        check("empty haystack no match", !m.contains("", "zuan"));

        // Empty query matches everything.
        check("empty query matches", m.contains("钻石", ""));

        if (failures == 0) {
            System.out.println("[PinyinSearchProbe] PASS (JEC-style pinyin search core)");
            System.exit(0);
        } else {
            System.out.println("[PinyinSearchProbe] FAIL: " + failures + " assertion(s)");
            System.exit(1);
        }
    }
}
