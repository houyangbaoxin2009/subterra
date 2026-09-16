package io.toterra.subterra.engine.ui.title;

import java.util.List;

/**
 * p.2.32.1 标题提示数据面（clean-room，Traveler 系列仅思想参考、零代码包含）。
 * 纯 JDK、确定性：区域/生物群系「进入→提示、离开→重置」的去重会话（tick 驱动，
 * 无墙钟），文档内首个命中者胜出；同键同值在未离开前不重复显示（世界观内表达：
 * 提示文本走语言键，无 HUD 系统感元素）。
 *
 * <p>The p.2.32.1 title-hint data plane (clean-room; the Traveler pack is
 * reference-of-ideas only, zero code inclusion). Pure JDK, deterministic: a
 * per-viewer enter/leave dedup session (tick-driven, no wall clock); the first
 * matching hint in document order wins; the same key+value does not repeat until
 * the viewer leaves. In-world expression: hint text is language keys, no HUD
 * system-feel elements.
 */
public final class TitleHintCore {

    /** 固定显示窗口（游戏 tick）。 / The fixed display window (game ticks). */
    public static final int DISPLAY_TICKS = 60;

    /** 单条提示规则（构造期确定性校验）。 / One hint rule (validated at construction). */
    public record Hint(String matchKey, String matchValue, String titleKey, String subtitleKey) {
        public Hint {
            if (matchKey == null || matchKey.isBlank()) {
                throw new IllegalArgumentException("match_key must not be blank");
            }
            if (matchValue == null || matchValue.isBlank()) {
                throw new IllegalArgumentException("match_value must not be blank");
            }
            if (titleKey == null || titleKey.isBlank()) {
                throw new IllegalArgumentException("title_key must not be blank");
            }
            subtitleKey = subtitleKey == null ? "" : subtitleKey;
        }
    }

    /** 提示文档：文档序即优先序；重复 (matchKey, matchValue) 确定性拒绝。 /
     *  The hint document: document order is priority; duplicate (matchKey, matchValue) rejected. */
    public record HintDoc(List<Hint> hints, String render) {
        public HintDoc {
            if (hints == null) {
                throw new IllegalArgumentException("hints must not be null");
            }
            hints = List.copyOf(hints);
            java.util.Set<String> seen = new java.util.HashSet<>();
            for (Hint h : hints) {
                if (!seen.add(h.matchKey() + "\u0000" + h.matchValue())) {
                    throw new IllegalArgumentException("duplicate hint match: " + h.matchKey() + "=" + h.matchValue());
                }
            }
            StringBuilder sb = new StringBuilder("title hints=").append(hints.size());
            for (Hint h : hints) {
                sb.append("; ").append(h.matchKey()).append('=').append(h.matchValue()).append("->").append(h.titleKey());
            }
            render = sb.toString();
        }

        /** 便捷构造：渲染由紧凑构造器派生。 / Convenience ctor: the render is derived by the compact one. */
        public HintDoc(List<Hint> hints) {
            this(hints, "");
        }
    }

    /** 展示计划（语言键 + 固定窗口）。 / The display plan (language keys + fixed window). */
    public record Display(String titleKey, String subtitleKey, int durationTicks) {
    }

    /** 观看者去重会话。 / The per-viewer dedup session. */
    public static final class Session {
        private final HintDoc doc;
        private String activeKey;
        private String activeValue;

        public Session(HintDoc doc) {
            if (doc == null) {
                throw new IllegalArgumentException("doc must not be null");
            }
            this.doc = doc;
        }

        /** 进入：首个命中者胜出；与当前活动项同键同值 → null（不重复）。 /
         *  Enter: first match wins; same key+value as the active entry → null (no repeat). */
        public Display enter(String key, String value) {
            if (key == null || value == null) {
                throw new IllegalArgumentException("enter key/value must not be null");
            }
            if (key.equals(activeKey) && value.equals(activeValue)) {
                return null;
            }
            for (Hint h : doc.hints()) {
                if (h.matchKey().equals(key) && h.matchValue().equals(value)) {
                    activeKey = key;
                    activeValue = value;
                    return new Display(h.titleKey(), h.subtitleKey(), DISPLAY_TICKS);
                }
            }
            return null;
        }

        /** 离开：清除同键活动项（离开后才可再次提示）。 / Leave: clears the active entry for that key. */
        public boolean leave(String key) {
            if (key != null && key.equals(activeKey)) {
                activeKey = null;
                activeValue = null;
                return true;
            }
            return false;
        }

        public boolean active() {
            return activeKey != null;
        }
    }

    private TitleHintCore() {
    }
}
