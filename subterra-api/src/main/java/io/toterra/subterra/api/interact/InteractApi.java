package io.toterra.subterra.api.interact;

import java.util.List;
import java.util.Objects;

/**
 * p.2.33.7 对外交互域契约门面（final class、静态纯函数、纯 JDK）：确定性接口面，供上层/域模组按
 * 「交互受控词汇 + 规则引擎脚本化确定性 + 零 HUD 表面文本」使用 engine.interact 域的确定性语义。
 * 语义与 {@code engine.interact} 的 {@code InteractKind}/{@code InteractAction}/{@code Attitude}/
 * {@code InteractRuleEngine}/{@code ZeroHudGuard}（p.2.14.1/.2/.3）一致——本处为契约与数据面注入，
 * engine 为实现镜像（{@code engine.interact.InteractApiMirror}），api 不依赖 engine。所有常量/短语均从
 * engine 实际常量逐字对照落于此，禁止拍脑袋数值。
 * <p>确定性：{@link #kinds()} / {@link #actions()} / {@link #attitudes()} 返回固定序受控词汇；
 * {@link #zeroHudBlacklist()} 返回固定序系统气味黑名单；{@link #zeroHudCheck} 做单趟固定序扫描、
 * 命中 {@code [最早出现位置, 黑名单序]} 最小者并返回固定原因 {@code "SYSTEM_SCENT:<term>"}，干净返回空串
 * （与 {@code ZeroHudGuard.check} 同语义）；{@link #evaluate} 逐字复现 {@code InteractRuleEngine}
 * 的 P3 门禁 + P4 呈奉规则（对单目标、给定持有物品的世界内交互），同一 {@code (kind, action, attitude,
 * holdingItem)} 两次求值逐字段一致（可回放）。全部为纯函数、无随机、无墙钟、无迭代序依赖；无 O(n²)
 * （{@link #zeroHudCheck} 对每黑名单词一次 {@code indexOf}，线性于文本长 × 黑名单长）。无状态、无副作用。
 * <p>
 * p.2.33.7 the external interaction-domain contract facade (final class, static pure functions, pure JDK): a
 * deterministic interface surface for upper layers / domain mods to use the deterministic semantics of
 * {@code engine.interact}'s controlled vocabulary + scripted rule engine + zero-HUD surface text. Semantics
 * match {@code InteractKind}/{@code InteractAction}/{@code Attitude}/{@code InteractRuleEngine}/
 * {@code ZeroHudGuard} (p.2.14.1/.2/.3) — this is the contract and data-injection surface for the engine to
 * mirror as its implementation ({@code engine.interact.InteractApiMirror}), and the api does not depend on the
 * engine. Every constant/phrase here is pinned verbatim from the engine's actual constants — nothing is guessed.
 * <p>Deterministic: {@link #kinds()} / {@link #actions()} / {@link #attitudes()} return fixed-order controlled
 * vocabulary; {@link #zeroHudBlacklist()} returns the fixed-order system-scent blacklist; {@link #zeroHudCheck}
 * does a single fixed-order pass, reporting the {@code [earliest position, blacklist order]} minimum as the fixed
 * reason {@code "SYSTEM_SCENT:<term>"} and an empty string when clean (same semantics as {@code ZeroHudGuard.check});
 * {@link #evaluate} reproduces {@code InteractRuleEngine}'s P3 gate + P4 offer rules verbatim (for a single-target
 * in-world interaction with a given held item) — two evaluations of the same {@code (kind, action, attitude,
 * holdingItem)} are field-for-field identical (replayable). All pure functions, no randomness, no wall-clock, no
 * iteration-order dependence; no O(n²) ({@link #zeroHudCheck} runs one {@code indexOf} per blacklist term, linear
 * in text length × blacklist length). Stateless, side-effect free.
 */
public final class InteractApi {

    /** A single in-world interaction decision from {@link #evaluate}: allowed + the deterministic phrase.
     *  {@link #evaluate} 的单次世界内交互决策：是否放行 + 确定性短语。 */
    public record Decision(boolean allowed, String message) {

        /** Null-guards the message. / 空值防御 {@code message}。 */
        public Decision {
            Objects.requireNonNull(message, "decision message must not be null");
        }
    }

    private InteractApi() {
    }

    // ---- Fixed controlled vocabulary (pinned verbatim from the engine enums' tdName). ----

    /** The fixed-order in-world entity kinds ({@code npc/inscription/scroll/letter/relic}),
     *  mirroring {@code engine.interact.InteractKind} td-names. /
     *  世界内实体种类固定序（{@code npc/inscription/scroll/letter/relic}），镜像
     *  {@code engine.interact.InteractKind} 的 td 名。 */
    public static List<String> kinds() {
        return List.of("npc", "inscription", "scroll", "letter", "relic");
    }

    /** The fixed-order in-world action words ({@code converse/read/offer/examine}),
     *  mirroring {@code engine.interact.InteractAction} td-names. /
     *  世界内动作词固定序（{@code converse/read/offer/examine}），镜像
     *  {@code engine.interact.InteractAction} 的 td 名。 */
    public static List<String> actions() {
        return List.of("converse", "read", "offer", "examine");
    }

    /** The fixed-order attitudes ({@code friendly/neutral/hostile}), mirroring {@code engine.interact.Attitude}
     *  td-names. / 态度固定序（{@code friendly/neutral/hostile}），镜像 {@code engine.interact.Attitude} 的 td 名。 */
    public static List<String> attitudes() {
        return List.of("friendly", "neutral", "hostile");
    }

    // ---- Zero-HUD system-scent guard (mirroring ZeroHudGuard). ----

    /** The fixed-order system-scent blacklist, sourced verbatim from {@code engine.interact.ZeroHudGuard.BLACKLIST}.
     *  / 固定序系统气味黑名单，逐字源自 {@code engine.interact.ZeroHudGuard.BLACKLIST}。 */
    public static List<String> zeroHudBlacklist() {
        return List.of(
                "hud", "panel", "progress", "task", "log", "level", "badge",
                "popup", "notification", "achievement", "quest", "counter", "score",
                "hint", "prompt", "menu", "dialog", "tooltip",
                "系统", "面板", "进度条", "进度", "任务", "等级", "弹窗", "通知", "成就",
                "数值", "计数", "对话框", "提示", "气泡");
    }

    /**
     * Single-pass scan of the fixed-order blacklist, mirroring {@code engine.interact.ZeroHudGuard#check}: when
     * the {@code [earliest position, blacklist order]} minimum hits, returns the fixed reason
     * {@code "SYSTEM_SCENT:<term>"}; empty string when clean; empty text (null or empty) returns empty string.
     * Deterministic.
     * / 单趟固定序黑名单扫描，镜像 {@code engine.interact.ZeroHudGuard#check}：命中 {@code [最早出现位置, 黑名单序]}
     * 最小者时返回固定原因 {@code "SYSTEM_SCENT:<term>"}；干净返回空串；空文本（null 或空串）返回空串。确定性。
     *
     * @param text the text to check (null treated as empty).
     * @return the fixed reason, or an empty string when clean.
     */
    public static String zeroHudCheck(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        int bestIdx = Integer.MAX_VALUE;
        String bestTerm = null;
        for (String term : zeroHudBlacklist()) {
            int idx = text.indexOf(term);
            if (idx >= 0 && idx < bestIdx) {
                bestIdx = idx;
                bestTerm = term;
            }
        }
        return bestTerm == null ? "" : "SYSTEM_SCENT:" + bestTerm;
    }

    /**
     * Deterministically evaluates one in-world interaction on the {@code (kind, action, attitude)} triple with an
     * optional {@code holdingItem}, reproducing the P3 gate + P4 offer precedence of
     * {@code engine.interact.InteractRuleEngine#resolve} exactly for this single-target surface-less case
     * (P1 surface-ownership and P2 action-permission are outside this pure function). Fixed precedence, first hit
     * decides; messages come only from the fixed phrase table (zero HUD). Pure and deterministic: identical inputs
     * always yield the identical {@link Decision}.
     * / 确定性求值一次世界内交互（可选 {@code holdingItem}），逐字复现
     * {@code engine.interact.InteractRuleEngine#resolve} 的 P3 门禁 + P4 呈奉优先序在「单目标、无表面」情形
     * （P1 表面归属与 P2 动作许可不在本纯函数内）。固定优先序、第一命中即出结果；短语仅来自固定短语表（零 HUD）。
     * 纯函数且确定性：同输入恒得相同 {@link Decision}。
     *
     * @param kind        the entity kind td-name (one of {@link #kinds()}).
     * @param action      the action td-name (one of {@link #actions()}).
     * @param attitude    the attitude td-name (one of {@link #attitudes()}).
     * @param holdingItem the in-world item name carried in the world state, or {@code null} if none.
     * @return the immutable decision (allowed + deterministic phrase).
     * @throws IllegalArgumentException if {@code kind}/{@code action}/{@code attitude} is not one of the
     *     controlled vocabulary.
     */
    public static Decision evaluate(String kind, String action, String attitude, String holdingItem) {
        Objects.requireNonNull(kind, "kind must not be null");
        Objects.requireNonNull(action, "action must not be null");
        Objects.requireNonNull(attitude, "attitude must not be null");
        if (!kinds().contains(kind)) {
            throw new IllegalArgumentException("unknown interact kind: " + kind);
        }
        if (!actions().contains(action)) {
            throw new IllegalArgumentException("unknown interact action: " + action);
        }
        if (!attitudes().contains(attitude)) {
            throw new IllegalArgumentException("unknown interact attitude: " + attitude);
        }
        // P3.1 — EXAMINE is only for non-living entities.
        if ("examine".equals(action) && "npc".equals(kind)) {
            return new Decision(false, MSG_EXAMINE_NPC);
        }
        // P3.2 — EXAMINE on a non-living entity (inscription/scroll/letter/relic).
        if ("examine".equals(action)) {
            return new Decision(true, MSG_EXAMINE);
        }
        // P3.3 — HOSTILE: converse/read/offer all rejected.
        if ("hostile".equals(attitude)) {
            return new Decision(false, MSG_HOSTILE_REJECT);
        }
        // P3.4 — FRIENDLY: converse/read allowed, offer goes to P4.
        if ("friendly".equals(attitude)) {
            if ("offer".equals(action)) {
                if (holdingItem != null) {
                    return new Decision(true, String.format(MSG_OFFER_WITH_ITEM, holdingItem));
                }
                return new Decision(true, MSG_FRIENDLY_ACCEPT);
            }
            return new Decision(true, "read".equals(action) ? MSG_FRIENDLY_READ : MSG_FRIENDLY_CONVERSE);
        }
        // P3.5 — NEUTRAL: only offer is allowed (goes to P4); converse/read rejected.
        if ("neutral".equals(attitude)) {
            if ("offer".equals(action)) {
                if (holdingItem != null) {
                    return new Decision(true, String.format(MSG_OFFER_WITH_ITEM, holdingItem));
                }
                return new Decision(true, MSG_NEUTRAL_OFFER);
            }
            return new Decision(false, MSG_NEUTRAL_REJECT);
        }
        // Defensive fallback (enum closed; normally unreachable).
        return new Decision(true, MSG_DEFAULT_ALLOW);
    }

    // ---- Fixed phrase table (pinned verbatim from engine.interact.InteractRuleEngine's MSG_ constants). ----
    private static final String MSG_EXAMINE_NPC = "There is nothing written in a living being to study.";
    private static final String MSG_EXAMINE = "You study its form closely, taking in each detail.";
    private static final String MSG_HOSTILE_REJECT = "They turn from you, unreceptive to word or hand.";
    private static final String MSG_NEUTRAL_REJECT = "They keep a certain distance, uncommitted in word or gesture.";
    private static final String MSG_FRIENDLY_CONVERSE = "They answer you, and the exchange settles into understanding.";
    private static final String MSG_FRIENDLY_READ = "You read, and the words settle into understanding.";
    private static final String MSG_FRIENDLY_ACCEPT = "They receive your offering with good will.";
    private static final String MSG_NEUTRAL_OFFER = "They regard your offering without haste.";
    private static final String MSG_OFFER_WITH_ITEM = "You offer them the %s.";
    private static final String MSG_DEFAULT_ALLOW = "It is met openly.";
}