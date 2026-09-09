package io.toterra.subterra.engine.interact;

import java.util.List;

/**
 * 零 HUD 守卫（final 工具类）—— p.2.14.3 写死的"系统气味"黑名单守卫：把任何产出文本里出现的被禁词汇（多语言）
 * 标记出来，作为零 HUD 渲染规则（p.2.14.3）与 p.2.14.1 包级"无系统气味"约束的可执行对照。黑名单是<b>固定常量集</b>
 * （{@link List#of} 不可变）。{@link #check(String)} 按<b>固定黑名单序</b>对文本做单趟 {@code indexOf} 扫描，
 * 命中最先出现位置（同位置取黑名单序更前者的词）并返回固定原因，干净返回空串；空文本返回空串。每词一次
 * {@code indexOf}（线性），整体线性于（文本长度 × 黑名单长度），无 O(n²)；同输入两次结果逐字节一致，零随机 /
 * 零时间戳 / 零可变状态。
 * <p>
 * <b>触发语义</b>：{@link #check(String)} 返回固定原因形如 {@code "SYSTEM_SCENT:<被禁词>"}；空文本返回空串。
 * 本守卫只拦截标记、绝不改写文本、绝不猜测渲染，零 HUD 渲染由 p.2.22 延后承接。被禁词汇语义即 p.2.14.1
 * 包级"无系统气味"约束的枚举化：面板 / 进度条 / 数值计数 / 任务日志 / 等级图标 / 系统弹窗 / 系统提示 等。
 * <p>
 * Zero-HUD guard (a {@code final} utility class) — the pinned "system scent" blacklist guard of p.2.14.3: it
 * flags any forbidden (multi-language) term found in produced text, giving an enforceable counterpart to the
 * zero-HUD rendering rules (p.2.14.3) and the package-level "no system scent" constraint of p.2.14.1. The
 * blacklist is a <b>fixed constant set</b> (immutable via {@link List#of}). {@link #check(String)} does one
 * {@code indexOf} pass per term over the text in <b>fixed blacklist order</b>, reporting the earliest caused
 * position (ties resolved by the earlier order-in-blacklist term) as a fixed reason, and an empty string when
 * clean; empty text returns an empty string. Each term is one {@code indexOf} (linear), the whole scan is linear
 * in (text length × blacklist length), with no O(n²); the same input yields byte-identical results each call, with
 * zero random / zero timestamp / zero mutable state.
 * <p>
 * <b>Trigger semantics</b>: {@link #check(String)} returns a fixed reason of the form
 * {@code "SYSTEM_SCENT:<forbidden term>"}; empty text returns an empty string. The guard only flags and intercepts
 * — it never rewrites text and never guesses rendering; zero-HUD rendering is deferred to p.2.22. The forbidden
 * terms correspond exactly to the enumeration of the p.2.14.1 package-level "no system scent" constraint: panel /
 * progress / numeric counter / task log / level icon / system popup / system prompt, etc.
 */
public final class ZeroHudGuard {

    private ZeroHudGuard() {
    }

    /**
     * 固定系统气味黑名单（多语言，写死）—— 语义即 p.2.14.1 包级"无系统气味"约束的枚举化：面板 / 进度条 /
     * 数值计数 / 任务日志 / 等级图标 / 系统弹窗 / 系统提示 等。{@link #check(String)} 以此<b>固定序</b>扫描。
     * The fixed system-scent blacklist (multi-language, pinned) — an enumeration of the p.2.14.1 package-level
     * "no system scent" constraint: panel / progress / numeric counter / task log / level icon / system popup /
     * system prompt, etc. {@link #check(String)} scans by this <b>fixed order</b>.
     */
    public static final List<String> BLACKLIST = List.of(
            // English terms.
            "hud", "panel", "progress", "task", "log", "level", "badge",
            "popup", "notification", "achievement", "quest", "counter", "score",
            "hint", "prompt", "menu", "dialog", "tooltip",
            // Chinese terms.
            "系统", "面板", "进度条", "进度", "任务", "等级", "弹窗", "通知", "成就",
            "数值", "计数", "对话框", "提示", "气泡"
    );

    /**
     * 单趟检查固定序黑名单：命中 {@code [最早出现位置, 黑名单序]} 最小者时，以命中词返回固定原因；干净返回空串；
     * 空文本（null 或空串）返回空串。不引入随机/时间戳。Scans the fixed-order blacklist in a single pass: when the
     * {@code [earliest position, blacklist order]} minimum is hit, returns a fixed reason carrying the hit term;
     * an empty string is returned when clean; empty text (null or empty) returns an empty string. No random /
     * timestamp.
     *
     * @param text 待检查文本（null 视为空）/ the text to check (null treated as empty).
     * @return 固定原因或空串 / a fixed reason, or an empty string when clean.
     */
    public static String check(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        // 固定序单趟扫描：位置优先，同位置以黑名单序破平。
        int bestIdx = Integer.MAX_VALUE;
        String bestTerm = null;
        for (String term : BLACKLIST) {
            int idx = text.indexOf(term);
            if (idx >= 0 && idx < bestIdx) {
                bestIdx = idx;
                bestTerm = term;
            }
        }
        return bestTerm == null ? "" : "SYSTEM_SCENT:" + bestTerm;
    }
}
