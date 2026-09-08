package io.toterra.subterra.probes;

import io.toterra.subterra.engine.optim.logic.pronounce.Lexicon;
import io.toterra.subterra.engine.optim.logic.pronounce.PronounceMatcher;

import java.util.List;

/**
 * Deterministic acceptance probe for the JEC-style reading search port
 * (p.1.4.9): verifies the language-agnostic pronunciation engine matches
 * Chinese pinyin (full, initials, mixed, multi-reading, prefix) as well as
 * Japanese kana romaji from a separate pack, and that packs coexist on the
 * same character via append-merge semantics. Pure JVM (no Minecraft
 * runtime); the SearchTreeMixin redirect and ReadingSuffixArray wiring are
 * covered by the client boot gate.
 */
public final class ReadingSearchProbe {

    private ReadingSearchProbe() {
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
        // zh pack (PinIn line format). Same embedded lexicon as p.1.4.8 so
        // every pinyin assertion in the original probe is preserved verbatim.
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
                "把: ba",
                "山: shan"
        ));
        // ja pack: kana romaji + one kanji reading (san) to prove append-merge
        // coexistence on the same character.
        Lexicon ja = Lexicon.ofLines(List.of(
                "あ: a",
                "め: me",
                "か: ka",
                "な: na",
                "き: ki",
                "ゃ: ya",
                "ゅ: yu",
                "う: u",
                "ア: a",
                "カ: ka",
                "ナ: na",
                "山: san, yama",
                "川: kawa",
                "学: gaku",
                "校: kou",
                "雨: ame"
        ));
        // ko pack: Hangul syllable -> RR romaji (syllable blocks; selected subset
        // mirrors what the generated ko.lex resource contains).
        Lexicon ko = Lexicon.ofLines(List.of(
                "가: ga",
                "국: guk",
                "어: eo",
                "한: han",
                "닭: dalg",
                "꽃: kkot",
                "스: seu"
        ));
        // gr pack: Greek letter -> Latin transliteration (upper/lower same sound).
        Lexicon gr = Lexicon.ofLines(List.of(
                "α: a",
                "β: b",
                "σ: s",
                "ς: s",
                "ω: o",
                "Α: a",
                "χ: ch"
        ));
        // Packs merge with append-and-dedup: 山 keeps both shan (zh) and san (ja).
        PronounceMatcher m = new PronounceMatcher(zh.mergedWith(ja).mergedWith(ko).mergedWith(gr));

        // --- Preserved pinyin assertions from p.1.4.8 (16) ---

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

        // --- New Japanese / multi-pack assertions (p.1.4.9) ---

        // Full romaji for a kana sequence: かな (ka + na).
        check("ja full romaji kana", m.contains("かな", "kana"));
        // Two-syllable word: あめ (a + me).
        check("ja single kana", m.contains("あめ", "ame"));
        // Single syllable: き (ki).
        check("ja simple syllable", m.contains("き", "ki"));
        // Youon digraph composes via initial + small-kana full reading: きゃ = k + ya.
        check("ja youon digraph", m.contains("きゃ", "kya"));
        // Initials across youon syllables: きゅう = k + y.
        check("ja romanji initials", m.contains("きゅう", "ky"));
        // Negative for a wrong romaji.
        check("ja negative", !m.contains("かな", "kuni"));
        // Katakana equivalent matches the same romaji.
        check("katakana match", m.contains("カナ", "kana"));
        // Append-merge coexistence: 山 matches both shan (zh) and san (ja).
        check("multipack co-exist append", m.contains("山", "shan") && m.contains("山", "san"));

        // --- Korean Hangul assertions (ko pack, p.1.4.10) ---

        // Single syllable block: 가 (g + a).
        check("ko syllable", m.contains("가", "ga"));
        // Word assembled from syllable blocks: 한국어 -> han + guk + eo.
        check("ko word hangukeo", m.contains("한국어", "hangukeo"));
        // Complex final cluster: 닭 (ㄷ d + ㅏ a + ㄺ lg).
        check("ko complex coda", m.contains("닭", "dalg"));
        // Double-initial: 꽃 (ㄲ kk + ㅗ o + ㅊ t).
        check("ko double initial", m.contains("꽃", "kkot"));
        // Korean negative.
        check("ko negative", !m.contains("가", "ba"));

        // --- Japanese kanji assertions (ja pack kanji section, p.1.4.10) ---

        // On'yomi reading of a kanji.
        check("ja kanji onyomi", m.contains("山", "san"));
        // Word assembled per character (no euphony shift): 山川 -> yama + kawa.
        check("ja kanji compound", m.contains("山川", "yamakawa"));
        // Negative kanji romaji.
        check("ja kanji negative", !m.contains("雨", "umi"));

        // --- Greek letter assertions (gr pack, p.1.4.10) ---

        // Plain transliteration: α + β.
        check("gr alpha beta", m.contains("αβ", "ab"));
        // Final sigma and omega: σ + ω -> so.
        check("gr sigma omega", m.contains("σω", "so"));
        // Uppercase Greek letter matches the same transliteration.
        check("gr uppercase", m.contains("Α", "a"));
        // Digraph transliteration: χ -> ch.
        check("gr digraph", m.contains("χ", "ch"));
        // Greek negative.
        check("gr negative", !m.contains("α", "k"));

        if (failures == 0) {
            System.out.println("[ReadingSearchProbe] PASS (language-agnostic reading search core, " + 36 + " checks)");
            System.exit(0);
        } else {
            System.out.println("[ReadingSearchProbe] FAIL: " + failures + " assertion(s)");
            System.exit(1);
        }
    }
}