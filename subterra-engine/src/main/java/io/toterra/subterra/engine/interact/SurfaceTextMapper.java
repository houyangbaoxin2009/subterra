package io.toterra.subterra.engine.interact;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 表面映射器（final 工具类）—— p.2.14.3 零 HUD 渲染规则 / 表面映射的核心：把一份 {@link InteractSpec} 与一份
 * {@link InteractState} 以<b>确定性</b>方式映射为一份 {@link List}&lt;{@link SurfaceText}&gt;，产出<b>纯数据</b>
 * （世界观内语言文本），渲染接 p.2.22 延后，本类不触碰任何渲染。映射顺序与短语表语义<b>写死</b>如下（无随机 /
 * 无时间戳 / 无常数可变）：按 spec 的<b>固定文档序</b>逐表面遍历；每个表面按 {@link InteractSurface#kind()} 走
 * 对应规则，产出行序固定；每一行的文本要么来自本类私有固定短语表，要么来自世界状态（确定性插值），绝不引入
 * 数值计数式 UI 文本。任何产出行在进入返回列表之前必经 {@link ZeroHudGuard#check(String)}：若命中系统气味词则
 * 抛受检 {@link InteractViolationException}（原因 {@link InteractViolationException#SYSTEM_SCENT}），并且
 * 映射器用于把这种"脏"文本拦截在原地，绝不带着系统气味继续 —— 语义即"表面映射不得产出系统气味文本"。同
 * (spec, state) 两次 {@link #map(InteractSpec, InteractState)} 逐字段一致（可回放）。
 * <p>
 * <b>每种类的固定映射（写死）</b>：
 * <pre>
 *   NPC          首行 = 按 attitude 固定的短语表（FRIENDLY / NEUTRAL / HOSTILE 各一条固定短语）；
 *                 若 state 含任取 "offered.&lt;item&gt;"（固定键序首遇），则再追加一行"物品回应行"
 *                 （固定模板，引用该物品名，无数字计数文本）。
 *   INSCRIPTION  文本取自（按此顺序定则，皆确定）：spec/state —— state["text.&lt;target&gt;"]
 *   SCROLL        若存在，则取其值；否则取固定表 FIXED_TEXTS[&lt;target&gt;] 若该 target 有记录；否则置固定缺省行。
 *   LETTER        三者同规则，产出行固定为单行。
 *   RELIC        首行固定（先言遗物静置）；若 state 含 "examined.&lt;target&gt;"，则再追加一行固定"端详回应行"。
 * </pre>
 * 每个 {@link SurfaceText} 的 {@code lines} 主串都通过了 {@link ZeroHudGuard#check(String)} 校验（干净），
 * 这是 <b>零 HUD 渲染规则</b>的落点：本体把"表面 → 世界内语言文本"的确定性映射数据当作白名单即可在渲染层
 * (p.2.22) 直接使用。
 * <p>
 * Surface mapper (a {@code final} utility class) — the heart of the p.2.14.3 zero-HUD rendering rules / surface
 * mapping: it maps an {@link InteractSpec} and an {@link InteractState} <b>deterministically</b> into a
 * {@link List}&lt;{@link SurfaceText}&gt; of <b>pure data</b> (in-world-language text); rendering is deferred to
 * p.2.22 and this class never touches any rendering. The mapping order and phrase-table semantics are <b>pinned</b>
 * as follows (no random / no timestamp / no mutable constant): it walks each surface in the spec's <b>fixed document
 * order</b>; each surface is handled by the rule for its {@link InteractSurface#kind()}, producing a fixed line
 * order; every line's text comes either from this class's private fixed phrase table or from the in-world state
 * (deterministic interpolation), never carrying numeric-counter UI text. Every produced line passes
 * {@link ZeroHudGuard#check(String)} before entering the returned list: a hit on a system-scent term raises the
 * checked {@link InteractViolationException} (reason {@link InteractViolationException#SYSTEM_SCENT}), and the
 * mapper thereby intercepts such "dirty" text in place — it never continues with system scent. The semantics are
 * "a surface mapping must never produce system-scented text." Two {@link #map(InteractSpec, InteractState)} calls on
 * the same (spec, state) are field-for-field identical (replayable).
 * <p>
 * <b>Fixed mapping per kind (pinned)</b>:
 * <pre>
 *   NPC          first line = the fixed phrase table by attitude (one fixed phrase for FRIENDLY / NEUTRAL /
 *                 HOSTILE); if state carries any "offered.&lt;item&gt;" (first hit in fixed key order), then a single
 *                 "item-reply line" is appended (fixed template referencing that item name, no numeric counter text).
 *   INSCRIPTION  the text comes from (in this fixed precedence, all deterministic): spec/state —
 *   SCROLL         state["text.&lt;target&gt;"] if present, else the fixed table FIXED_TEXTS[&lt;target&gt;] if that target
 *   LETTER         is recorded, else a fixed default line. All three share the same rule and produce a single line.
 *   RELIC        first line is fixed (the relic rests in stillness); if state carries "examined.&lt;target&gt;", then a
 *                 single fixed "examine-reply line" is appended.
 * </pre>
 * Every {@code lines} entry of each {@link SurfaceText} has passed {@link ZeroHudGuard#check(String)} (clean) — this
 * is the landing point of the <b>zero-HUD rendering rules</b>: the body may treat this deterministic "surface →
 * in-world-language text" mapping data as a whitelist directly usable at the rendering layer (p.2.22).
 */
public final class SurfaceTextMapper {

    private SurfaceTextMapper() {
    }

    /** attitude 命中的 NPC 首行（FRIENDLY / NEUTRAL / HOSTILE 各一固定短语）。NPC first line keyed by attitude (one fixed phrase each). */
    private static final Map<Attitude, String> NPC_FIRST_LINE = Map.of(
            Attitude.FRIENDLY, "The elder greets you with warmth, and the day is gentler for it.",
            Attitude.NEUTRAL, "They regard you with a measured, unhurried gaze.",
            Attitude.HOSTILE, "Their eyes hold you at a distance, and the air turns colder."
    );

    /** 已呈奉后的固定物品回应模板（{@code %s} 为物品名，仅来自世界状态，确定性插值）。Fixed item-reply template after an offering ({@code %s} is the item name, from the in-world state only, deterministic interpolation). */
    private static final String NPC_ITEM_REPLY = "They turn to what you laid before them and speak kindly of the %s.";

    /** 可读表面（inscription/scroll/letter）的固定缺省行，当 state 与固定表均无文本时使用。Fixed default line for a readable surface when neither state nor the fixed table has text. */
    private static final String DEFAULT_READABLE_LINE = "The marks are old, but their meaning holds.";

    /** 用 target 关键字查取可读表面的固定文本表。Fixed textual table for readable surfaces keyed by the target. */
    private static final Map<String, String> FIXED_TEXTS = Map.of(
            "stele of the first autumn", "Each carved stroke tells of a harvest long gone.",
            "the watchman's ledger roll", "A list of names, each a life that stood watch."
    );

    /** RELIC 固定首行（先言遗物静置）。Fixed first line for a relic (it rests in stillness). */
    private static final String RELIC_BASE_LINE = "An heirloom rests here, its shape worn smooth by many palms.";

    /** RELIC 已端详后的固定回应行。Fixed examine-reply line for a relic once examined. */
    private static final String RELIC_EXAMINED_LINE = "You have studied it closely and carry its memory.";

    /** 世界状态里"已被呈奉"的键前缀。Key prefix marking "already offered" in the in-world state. */
    private static final String OFFERED_PREFIX = "offered.";

    /** 世界状态里"已被端详"的键前缀。Key prefix marking "already examined" in the in-world state. */
    private static final String EXAMINED_PREFIX = "examined.";

    /** 世界状态里可读表面文本的键前缀（{@code text.&lt;target&gt;}）。Key prefix for a readable surface's text in the in-world state ({@code text.&lt;target&gt;}). */
    private static final String TEXT_PREFIX = "text.";

    /**
     * 确定性表面映射（纯数据，渲染延后）。Deterministic surface mapping (pure data, rendering deferred).
     *
     * @param spec  交互规范 / the interaction spec.
     * @param state 世界状态快照 / the in-world state snapshot.
     * @return 固定文档序的表面文本列表 / the surface-text list in fixed document order.
     * @throws InteractViolationException 某行命中系统气味词（固定原因 SYSTEM_SCENT）/ a line hit a system-scent term (reason SYSTEM_SCENT).
     * @throws IllegalArgumentException    任一参数为 null / any argument is null.
     */
    public static List<SurfaceText> map(InteractSpec spec, InteractState state)
            throws InteractViolationException {
        Objects.requireNonNull(spec, "spec must not be null");
        Objects.requireNonNull(state, "state must not be null");

        List<SurfaceText> out = new ArrayList<>();
        for (InteractSurface s : spec.surfaces()) {
            out.add(mapSurface(s, state));
        }
        return List.copyOf(out);
    }

    /** 逐表面确定性映射并做零 HUD 守卫校验。Maps one surface deterministically and applies the zero-HUD guard check. */
    private static SurfaceText mapSurface(InteractSurface s, InteractState state)
            throws InteractViolationException {
        List<String> lines;
        switch (s.kind()) {
            case NPC -> lines = mapNpc(s, state);
            case INSCRIPTION, SCROLL, LETTER -> lines = mapReadable(s, state);
            case RELIC -> lines = mapRelic(s, state);
            default -> lines = List.of(DEFAULT_READABLE_LINE); // 防御（枚举封闭，正常不可达）。
        }
        return new SurfaceText(s.target(), s.kind(), lines);
    }

    /** NPC：attitude 首行，按需追加物品回应行。NPC: attitude first line, item-reply appended when offered. */
    private static List<String> mapNpc(InteractSurface s, InteractState state) throws InteractViolationException {
        List<String> lines = new ArrayList<>();
        lines.add(NPC_FIRST_LINE.get(s.attitude()));
        String offeredItem = firstOfferedItem(state);
        if (offeredItem != null) {
            lines.add(String.format(NPC_ITEM_REPLY, offeredItem));
        }
        guard(lines, s.target(), s.kind());
        return lines;
    }

    /** 从状态固定键序中取首个 {@code offered.&lt;item&gt;} 的物品名；无则 null。First offered item name in fixed key order, or null. */
    private static String firstOfferedItem(InteractState state) {
        for (Map.Entry<String, String> e : state.values().entrySet()) {
            if (e.getKey().startsWith(OFFERED_PREFIX)) {
                return e.getKey().substring(OFFERED_PREFIX.length());
            }
        }
        return null;
    }

    /** 可读表面（inscription/scroll/letter）：state 文本 → 固定表 → 固定缺省行。Readable surface: state text → fixed table → default line. */
    private static List<String> mapReadable(InteractSurface s, InteractState state) throws InteractViolationException {
        String text = state.get(TEXT_PREFIX + s.target());
        if (text == null) {
            text = FIXED_TEXTS.get(s.target());
        }
        if (text == null) {
            text = DEFAULT_READABLE_LINE;
        }
        List<String> lines = List.of(text);
        guard(lines, s.target(), s.kind());
        return lines;
    }

    /** RELIC：固定首行，已端详时追加回应行。Relic: fixed base line, examine-reply appended once examined. */
    private static List<String> mapRelic(InteractSurface s, InteractState state) throws InteractViolationException {
        List<String> lines = new ArrayList<>();
        lines.add(RELIC_BASE_LINE);
        if (state.contains(EXAMINED_PREFIX + s.target())) {
            lines.add(RELIC_EXAMINED_LINE);
        }
        guard(lines, s.target(), s.kind());
        return lines;
    }

    /** 零 HUD 校验：某行命中系统气味词则抛受检异常（SYSTEM_SCENT）。Zero-HUD check: a system-scent hit raises the checked exception (SYSTEM_SCENT). */
    private static void guard(List<String> lines, String target, InteractKind kind)
            throws InteractViolationException {
        for (String line : lines) {
            String hit = ZeroHudGuard.check(line);
            if (!hit.isEmpty()) {
                throw new InteractViolationException(InteractViolationException.SYSTEM_SCENT,
                        "target=" + target + ", kind=" + kind + " :: " + hit);
            }
        }
    }
}
