// p.2.14.4 deterministic interaction probe for p.2.14.1-.3 (InteractSpec / InteractSpecParser /
// InteractViolationException / InteractState / InteractResult / InteractRuleEngine / SurfaceText /
// SurfaceTextMapper / ZeroHudGuard). Pure JVM - no MC runtime, no timestamps / random / timing;
// exit 0 = PASS, 1 = FAIL. NOT shipped in the mod jar.
package io.toterra.subterra.probes;

import io.toterra.subterra.engine.interact.Attitude;
import io.toterra.subterra.engine.interact.InteractAction;
import io.toterra.subterra.engine.interact.InteractKind;
import io.toterra.subterra.engine.interact.InteractResult;
import io.toterra.subterra.engine.interact.InteractRuleEngine;
import io.toterra.subterra.engine.interact.InteractSpec;
import io.toterra.subterra.engine.interact.InteractSpecParser;
import io.toterra.subterra.engine.interact.InteractState;
import io.toterra.subterra.engine.interact.InteractSurface;
import io.toterra.subterra.engine.interact.InteractViolationException;
import io.toterra.subterra.engine.interact.SurfaceText;
import io.toterra.subterra.engine.interact.SurfaceTextMapper;
import io.toterra.subterra.engine.interact.ZeroHudGuard;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * p.2.14.4 确定性交互探针 —— 结构化断言、每断言一 check、退出码与参考模板（{@link PcgProbe}）一致。五节：
 * <ol>
 *   <li><b>spec 解析/往返确定性</b>：固定 spec td 文本（样例 A 覆盖全部实体种类与三种态度，样例 B 为第二
 *       样例）parse 成功；{@code parse(toTd(s))} 形态稳定；同输入两次解析结果相等；
 *       {@code parse(toTd(...))} 与 {@code toTd} 两趟逐字节一致（往返）；</li>
 *   <li><b>spec 违约</b>：缺 name → MISSING_NAME；未知 kind → UNKNOWN_KIND；未知 action → UNKNOWN_ACTION；
 *       未知 attitude → UNKNOWN_ATTITUDE；</li>
 *   <li><b>规则引擎脚本化</b>（固定样例 spec + 固定 state）：FRIENDLY NPC CONVERSE → allowed 且消息固定；
 *       FRIENDLY NPC OFFER（state 含 {@code held.herb}）→ allowed 且 nextState 含 {@code offered.herb}；
 *       HOSTILE NPC CONVERSE → rejected 且消息固定；NPC EXAMINE → rejected；INSCRIPTION EXAMINE → allowed；
 *       <b>可回放</b>：同参数两次 resolve 结果逐字段一致；</li>
 *   <li><b>表面映射</b>：map 同 (spec,state) 两次逐字段一致；所有产出 lines 经 {@link ZeroHudGuard#check}
 *       全空（干净）；每个 {@link SurfaceText} 行数≥1；RELIC/SCROLL 等样例命中固定行；</li>
 *   <li><b>零 HUD 守卫</b>：crafted 含系统气味词的文本（如 {@code "progress: 50%"}、{@code "任务日志"}、
 *       {@code "level icon"}）→ check 非空且含 {@code SYSTEM_SCENT}；干净文本→空串。</li>
 * </ol>
 * 全为确定性断言、禁时间戳/随机/时序；退出码 0 = PASS。
 *
 * <p>p.2.14.4 deterministic interaction probe with structured assertions, one check per assertion,
 * exit-code convention identical to the reference template ({@link PcgProbe}). Five sections:
 * <ol>
 *   <li><b>spec parse / round-trip determinism</b>: fixed spec-td text (sample A covers every entity kind
 *       and all three attitudes; sample B is a second sample) parses; {@code parse(toTd(s))} is shape-stable;
 *       two parses of the same input are equal; {@code parse(toTd(...))} is byte-identical to {@code toTd}
 *       across two passes (round-trip);</li>
 *   <li><b>spec violations</b>: missing name → MISSING_NAME; unknown kind → UNKNOWN_KIND; unknown action →
 *       UNKNOWN_ACTION; unknown attitude → UNKNOWN_ATTITUDE;</li>
 *   <li><b>scripted rule-engine scenarios</b> (fixed sample spec + fixed state): FRIENDLY NPC CONVERSE →
 *       allowed with a fixed message; FRIENDLY NPC OFFER (state holding {@code held.herb}) → allowed with
 *       {@code offered.herb} in nextState; HOSTILE NPC CONVERSE → rejected with a fixed message; NPC EXAMINE →
 *       rejected; INSCRIPTION EXAMINE → allowed; <b>replayable</b>: two resolves of the same arguments are
 *       field-for-field identical;</li>
 *   <li><b>surface mapping</b>: map of the same (spec, state) is field-for-field identical across two calls;
 *       every produced line is clean via {@link ZeroHudGuard#check}; every {@link SurfaceText} has ≥ 1 line;
 *       RELIC/SCROLL samples hit fixed lines;</li>
 *   <li><b>zero-HUD guard</b>: crafted system-scented text (e.g. {@code "progress: 50%"}, {@code "任务日志"},
 *       {@code "level icon"}) → check returns non-empty and contains {@code SYSTEM_SCENT}; clean text → empty.</li>
 * </ol>
 * All assertions deterministic, no timestamp / random / timing; exit 0 = PASS.
 */
public final class InteractProbe {

    private InteractProbe() {
    }

    // ---- fixture constants (the deterministic single source of truth) ----

    /** 固定 spec td 文本（样例 A）：覆盖全部五种实体与三种态度。Sample-A spec td covering every kind + all attitudes. */
    private static final String SPEC_A_TD =
            "[\n"
            + "name = \"the crossing tavern\",\n"
            + "surfaces = [\n"
            + "  [ target = \"the elder at the gate\", kind = \"npc\", actions = [ \"converse\", \"offer\", \"examine\" ], attitude = \"friendly\" ],\n"
            + "  [ target = \"a haggard guard\", kind = \"npc\", actions = [ \"converse\" ], attitude = \"hostile\" ],\n"
            + "  [ target = \"stele of the first autumn\", kind = \"inscription\", actions = [ \"read\", \"examine\" ], attitude = \"neutral\" ],\n"
            + "  [ target = \"the watchman's ledger roll\", kind = \"scroll\", actions = [ \"read\" ], attitude = \"neutral\" ],\n"
            + "  [ target = \"a sealed letter from the north\", kind = \"letter\", actions = [ \"read\", \"examine\" ], attitude = \"neutral\" ],\n"
            + "  [ target = \"a worn silver locket\", kind = \"relic\", actions = [ \"examine\", \"offer\" ], attitude = \"neutral\" ],\n"
            + "],\n"
            + "]";

    /** 固定 spec td 文本（样例 B，第二样例）：子集实体与两种态度。Sample-B spec td (second sample): subset kinds + two attitudes. */
    private static final String SPEC_B_TD =
            "[\n"
            + "name = \"the hermit's shelf\",\n"
            + "surfaces = [\n"
            + "  [ target = \"a gilt scroll of lineage\", kind = \"scroll\", actions = [ \"read\" ], attitude = \"friendly\" ],\n"
            + "  [ target = \"an oaken prayer letter\", kind = \"letter\", actions = [ \"read\", \"examine\" ], attitude = \"neutral\" ],\n"
            + "],\n"
            + "]";

    /** 固定交互场景的承载状态（OFFER 前提，TreeMap 固定键序）。State holding an item for the FRIENDLY-OFFER scenario. */
    private static final InteractState HELD_HERB = new InteractState(Map.of("held.herb", "three sprigs"));

    /** 固定表面映射状态（世界内既定事实，全部干净文本）。State for the surface-mapping section. */
    private static final InteractState MAP_STATE = new InteractState(Map.of(
            "text.stele of the first autumn", "Each carved stroke tells of a harvest long gone.",
            "offered.herb", "three sprigs",
            "examined.a worn silver locket", "held in care"));

    private static int failures = 0;
    private static int checks = 0;

    private static void check(String name, boolean ok) {
        checks++;
        if (ok) {
            System.out.println("[PASS] " + name);
        } else {
            failures++;
            System.out.println("[FAIL] " + name);
        }
    }

    /** 异常被抛且 reason 匹配；未抛则返回 false。True iff parse throws with the given reason. */
    private static boolean reasonOf(RunnableParse r, String reason) {
        try {
            r.run();
            return false;
        } catch (InteractViolationException e) {
            return reason.equals(e.reason());
        }
    }

    @FunctionalInterface
    private interface RunnableParse {
        void run() throws InteractViolationException;
    }

    /** 某 (spec, target, action, state) 的求值结果，用于字段级回放比较。Evaluates one scripted scenario. */
    private static InteractResult resolve(InteractSpec spec, String target, InteractAction action,
                                          InteractState state) {
        return InteractRuleEngine.resolve(spec,
                new InteractRuleEngine.InteractTargetRef(target), action, state);
    }

    /** 两次求值逐字段一致（allowed / message / nextState，nextState 按键值等价）。Field-for-field replay equality. */
    private static boolean replayEqual(InteractResult a, InteractResult b) {
        return a.allowed() == b.allowed()
                && a.message().equals(b.message())
                && a.nextState().equals(b.nextState());
    }

    /** 多趟 map 结果逐字段一致。Field-for-field equality of two mapping runs. */
    private static boolean mapSame(List<SurfaceText> a, List<SurfaceText> b) {
        if (a.size() != b.size()) {
            return false;
        }
        for (int i = 0; i < a.size(); i++) {
            SurfaceText x = a.get(i);
            SurfaceText y = b.get(i);
            if (!x.target().equals(y.target())
                    || x.kind() != y.kind()
                    || !x.lines().equals(y.lines())) {
                return false;
            }
        }
        return true;
    }

    /** 按 target 取映射结果中的 SurfaceText；无则 null。Finds a SurfaceText by target. */
    private static SurfaceText byTarget(List<SurfaceText> list, String target) {
        for (SurfaceText st : list) {
            if (st.target().equals(target)) {
                return st;
            }
        }
        return null;
    }

    public static void main(String[] args) {
        try {
            run();
        } catch (Exception e) {
            failures++;
            System.out.println("[FAIL] 探针异常: " + e);
            e.printStackTrace(System.out);
        }

        if (failures == 0) {
            System.out.println("InteractProbe: " + checks + " checks, 0 failures");
            System.exit(0);
        } else {
            System.out.println("InteractProbe: " + checks + " checks, " + failures + " failures");
            System.exit(1);
        }
    }

    private static void run() throws Exception {
        // ============ 1. spec 解析/往返确定性 / spec parse + round-trip determinism ============
        InteractSpec specA = InteractSpecParser.parse(SPEC_A_TD);
        InteractSpec specB = InteractSpecParser.parse(SPEC_B_TD);

        check("spec A: 固定 td 文本 parse 成功（样例含五种实体全部种类）", specA != null);

        Set<InteractKind> kinds = EnumSet.noneOf(InteractKind.class);
        for (InteractSurface s : specA.surfaces()) {
            kinds.add(s.kind());
        }
        check("spec A: 表面种类覆盖全部五种实体（NPC/INSCRIPTION/SCROLL/LETTER/RELIC）",
                kinds.equals(EnumSet.allOf(InteractKind.class)));

        Set<Attitude> attitudes = EnumSet.noneOf(Attitude.class);
        for (InteractSurface s : specA.surfaces()) {
            attitudes.add(s.attitude());
        }
        check("spec A: 态度覆盖 FRIENDLY 与 HOSTILE（两种态度）",
                attitudes.contains(Attitude.FRIENDLY) && attitudes.contains(Attitude.HOSTILE));

        check("spec A: 同输入两次解析结果相等（parse == parse）",
                InteractSpecParser.parse(SPEC_A_TD).equals(specA));

        String aTd1 = InteractSpecParser.toTd(specA);
        String aTd2 = InteractSpecParser.toTd(specA);
        check("spec A: 同 spec 两次 toTd 逐字节一致", aTd1.equals(aTd2));

        check("spec A: parse(toTd(s)) 与 s 恒等(往返)",
                InteractSpecParser.parse(aTd1).equals(specA));

        check("spec A: parse(toTd(...)) 与 toTd 两趟逐字节一致",
                InteractSpecParser.toTd(InteractSpecParser.parse(aTd1)).equals(aTd1));

        check("spec B: 第二次样例 parse 成功", specB != null);
        check("spec B: parse(toTd(s)) 与 s 恒等(往返)",
                InteractSpecParser.parse(InteractSpecParser.toTd(specB)).equals(specB));
        check("spec B: 两次 toTd 逐字节一致",
                InteractSpecParser.toTd(specB).equals(InteractSpecParser.toTd(specB)));

        // ============ 2. spec 违约 / spec violations ============
        String noName = "[\n"
                + "name = \"  \",\n"
                + "surfaces = [\n"
                + "  [ target = \"t\", kind = \"npc\", actions = [ \"converse\" ], attitude = \"friendly\" ],\n"
                + "],\n"
                + "]";
        check("违约: 缺 name(空) → MISSING_NAME",
                reasonOf(() -> InteractSpecParser.parse(noName), InteractViolationException.MISSING_NAME));

        String unkKind = "[\n"
                + "name = \"s\",\n"
                + "surfaces = [\n"
                + "  [ target = \"t\", kind = \"draconis\", actions = [ \"converse\" ], attitude = \"friendly\" ],\n"
                + "],\n"
                + "]";
        check("违约: 未知 kind → UNKNOWN_KIND",
                reasonOf(() -> InteractSpecParser.parse(unkKind), InteractViolationException.UNKNOWN_KIND));

        String unkAction = "[\n"
                + "name = \"s\",\n"
                + "surfaces = [\n"
                + "  [ target = \"t\", kind = \"npc\", actions = [ \"dance\" ], attitude = \"friendly\" ],\n"
                + "],\n"
                + "]";
        check("违约: 未知 action → UNKNOWN_ACTION",
                reasonOf(() -> InteractSpecParser.parse(unkAction), InteractViolationException.UNKNOWN_ACTION));

        String unkAtt = "[\n"
                + "name = \"s\",\n"
                + "surfaces = [\n"
                + "  [ target = \"t\", kind = \"npc\", actions = [ \"converse\" ], attitude = \"wary\" ],\n"
                + "],\n"
                + "]";
        check("违约: 未知 attitude → UNKNOWN_ATTITUDE",
                reasonOf(() -> InteractSpecParser.parse(unkAtt), InteractViolationException.UNKNOWN_ATTITUDE));

        // ============ 3. 规则引擎脚本化 / scripted rule-engine scenarios ============
        InteractResult friendlyConverse = resolve(specA, "the elder at the gate",
                InteractAction.CONVERSE, new InteractState());
        check("规则: FRIENDLY NPC CONVERSE → allowed",
                friendlyConverse.allowed()
                        && InteractRuleEngine.MSG_FRIENDLY_CONVERSE.equals(friendlyConverse.message()));

        InteractResult friendlyOffer = resolve(specA, "the elder at the gate",
                InteractAction.OFFER, HELD_HERB);
        check("规则: FRIENDLY NPC OFFER(state held.herb) → allowed 且 nextState 含 offered.herb",
                friendlyOffer.allowed()
                        && friendlyOffer.nextState().contains("offered.herb")
                        && "You offer them the herb.".equals(friendlyOffer.message()));

        InteractResult hostileConverse = resolve(specA, "a haggard guard",
                InteractAction.CONVERSE, new InteractState());
        check("规则: HOSTILE NPC CONVERSE → rejected 且消息固定",
                !hostileConverse.allowed()
                        && InteractRuleEngine.MSG_HOSTILE_REJECT.equals(hostileConverse.message()));

        InteractResult npcExamine = resolve(specA, "the elder at the gate",
                InteractAction.EXAMINE, new InteractState());
        check("规则: NPC EXAMINE → rejected",
                !npcExamine.allowed()
                        && InteractRuleEngine.MSG_EXAMINE_NPC.equals(npcExamine.message()));

        InteractResult inscExamine = resolve(specA, "stele of the first autumn",
                InteractAction.EXAMINE, new InteractState());
        check("规则: INSCRIPTION EXAMINE → allowed",
                inscExamine.allowed()
                        && InteractRuleEngine.MSG_EXAMINE.equals(inscExamine.message()));

        InteractResult rp1 = resolve(specA, "the elder at the gate", InteractAction.CONVERSE, new InteractState());
        InteractResult rp2 = resolve(specA, "the elder at the gate", InteractAction.CONVERSE, new InteractState());
        check("规则: 同参数两次 resolve 逐字段一致（CONVERSE 可回放）", replayEqual(rp1, rp2));

        InteractResult rp3 = resolve(specA, "the elder at the gate", InteractAction.OFFER, HELD_HERB);
        InteractResult rp4 = resolve(specA, "the elder at the gate", InteractAction.OFFER, HELD_HERB);
        check("规则: 同参数两次 resolve 逐字段一致（OFFER 可回放）", replayEqual(rp3, rp4));

        // ============ 4. 表面映射 / surface mapping ============
        List<SurfaceText> map1 = SurfaceTextMapper.map(specA, MAP_STATE);
        List<SurfaceText> map2 = SurfaceTextMapper.map(specA, MAP_STATE);
        check("映射: 同 (spec,state) 两次 map 逐字段一致（可回放）", mapSame(map1, map2));

        boolean allClean = true;
        boolean allNonEmpty = true;
        for (SurfaceText st : map1) {
            allNonEmpty = allNonEmpty && !st.lines().isEmpty();
            for (String line : st.lines()) {
                allClean = allClean && ZeroHudGuard.check(line).isEmpty();
            }
        }
        check("映射: 所有产出 lines 经 ZeroHudGuard.check 全空（干净）", allClean);
        check("映射: 每个 SurfaceText 行数≥1", allNonEmpty);

        SurfaceText scroll = byTarget(map1, "the watchman's ledger roll");
        check("映射: SCROLL 命中固定行",
                scroll != null
                        && scroll.lines().size() == 1
                        && "A list of names, each a life that stood watch.".equals(scroll.lines().get(0)));

        SurfaceText relic = byTarget(map1, "a worn silver locket");
        check("映射: RELIC 命中固定首行与(已端详)回应行",
                relic != null
                        && relic.lines().size() == 2
                        && "An heirloom rests here, its shape worn smooth by many palms.".equals(relic.lines().get(0))
                        && "You have studied it closely and carry its memory.".equals(relic.lines().get(1)));

        // ============ 5. 零 HUD 守卫 / zero-HUD guard ============
        check("守卫: 'progress: 50%' → SYSTEM_SCENT",
                ZeroHudGuard.check("progress: 50%").contains("SYSTEM_SCENT"));
        check("守卫: '任务日志：已更新' → SYSTEM_SCENT",
                ZeroHudGuard.check("任务日志：已更新").contains("SYSTEM_SCENT"));
        check("守卫: 'level icon' → SYSTEM_SCENT",
                ZeroHudGuard.check("level icon").contains("SYSTEM_SCENT"));
        check("守卫: 干净文本 → 空串",
                ZeroHudGuard.check("The elder greets you with warmth, and the day is gentler for it.").isEmpty());
    }
}