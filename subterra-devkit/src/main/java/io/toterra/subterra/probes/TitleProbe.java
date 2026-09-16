package io.toterra.subterra.probes;

import io.toterra.subterra.engine.ui.title.TitleHintCore;

import java.util.List;

/**
 * p.2.32.1 确定性探针（纯 JVM）：标题提示数据面 —— 规格校验、文档重复匹配拒绝、
 * 首个命中者胜出、同键同值去重、离开后可复现、固定窗口、规范渲染逐字节、壳接线
 * 盘点（marker / 门控 class-bytes）。禁时序断言。
 *
 * <p>The p.2.32.1 deterministic probe (pure JVM): the title-hint data plane —
 * spec validation, duplicate-match rejection, first-match-wins, same-key+value
 * dedup, re-entrant after leave, the fixed window, byte-identical renders, and the
 * shell wiring inventory (marker / gate class bytes). No timing assertions.
 */
public final class TitleProbe {

    private static int checks;
    private static int failures;

    public static void main(String[] args) {
        hintValidation();
        docRules();
        sessionTrajectory();
        wiringInventory();
        System.out.println("[TitleProbe] " + (failures == 0 ? "PASS (" + checks + " checks)"
                : "FAIL (" + failures + " of " + checks + " checks failed)"));
        if (failures > 0) {
            System.exit(1);
        }
    }

    private static void hintValidation() {
        reject("blank match_key", () -> new TitleHintCore.Hint(" ", "v", "t", ""));
        reject("blank title_key", () -> new TitleHintCore.Hint("k", "v", " ", ""));
        TitleHintCore.Hint h = new TitleHintCore.Hint("k", "v", "t", null);
        check("hint: null subtitle normalised to empty", h.subtitleKey().isEmpty());
        check("hint: fixed window is 60 ticks", TitleHintCore.DISPLAY_TICKS == 60);
    }

    private static void docRules() {
        reject("doc: duplicate match rejected", () -> new TitleHintCore.HintDoc(List.of(
                new TitleHintCore.Hint("region", "a:b", "t1", ""),
                new TitleHintCore.Hint("region", "a:b", "t2", ""))));
        TitleHintCore.HintDoc doc = doc();
        check("doc: canonical render deterministic", doc.render().equals(doc.render()));
        check("doc: render carries size and keys", doc.render().startsWith("title hints=2; region=overturn:plains->"));
    }

    private static void sessionTrajectory() {
        TitleHintCore.Session s = new TitleHintCore.Session(doc());
        TitleHintCore.Display d1 = s.enter("region", "overturn:plains");
        check("session: first match wins (doc order)", d1 != null && d1.titleKey().equals("region.plains.title"));
        check("session: fixed window on display", d1 != null && d1.durationTicks() == TitleHintCore.DISPLAY_TICKS);
        check("session: active while entered", s.active());
        check("session: same key+value deduped", s.enter("region", "overturn:plains") == null);
        check("session: other value still matches", s.enter("region", "overturn:forest") != null);
        boolean left = s.leave("region");
        check("session: leave clears the active entry", left && !s.active());
        check("session: re-entrant after leave", s.enter("region", "overturn:plains") != null);
        check("session: leave of a foreign key is a no-op", !s.leave("biome"));
        check("session: unmatched key yields null", new TitleHintCore.Session(doc()).enter("biome", "x:y") == null);
        reject("session: null enter key rejected", () -> new TitleHintCore.Session(doc()).enter(null, "v"));
    }

    private static TitleHintCore.HintDoc doc() {
        return new TitleHintCore.HintDoc(List.of(
                new TitleHintCore.Hint("region", "overturn:plains", "region.plains.title", "region.plains.subtitle"),
                new TitleHintCore.Hint("region", "overturn:forest", "region.forest.title", "")));
    }

    private static void wiringInventory() {
        check("wiring: TitleRuntime present (load-only)",
                present("io.toterra.subterra.runtime.ui.title.TitleRuntime"));
        check("wiring: marker literal in runtime bytes",
                classBytesContain("io.toterra.subterra.runtime.ui.title.TitleRuntime", "[Subterra title]"));
        check("wiring: gate literal in runtime bytes",
                classBytesContain("io.toterra.subterra.runtime.ui.title.TitleRuntime", "subterra.probe.title"));
    }

    private static void reject(String name, Runnable r) {
        try {
            r.run();
            check("reject: " + name, false);
        } catch (IllegalArgumentException e) {
            check("reject: " + name, true);
        }
    }

    private static boolean present(String fqcn) {
        try {
            Class.forName(fqcn, false, TitleProbe.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }

    private static boolean classBytesContain(String fqcn, String literal) {
        try (java.io.InputStream in = TitleProbe.class.getResourceAsStream("/" + fqcn.replace('.', '/') + ".class")) {
            return in != null && new String(in.readAllBytes(), java.nio.charset.StandardCharsets.ISO_8859_1).contains(literal);
        } catch (java.io.IOException e) {
            return false;
        }
    }

    private static void check(String name, boolean ok) {
        checks++;
        if (ok) {
            System.out.println("[PASS] " + name);
        } else {
            failures++;
            System.out.println("[FAIL] " + name);
        }
    }
}
