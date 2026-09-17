package io.toterra.subterra.engine.ui.kit;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * One node of the Subterra UI component tree (p.2.37.1). Immutable, pure JDK, deterministic:
 * an id (unique within the whole tree, enforced by the layout engine), a fixed-order
 * {@link WidgetKind}, lowercase-normalized string props (fixed insertion order carried in a
 * {@link java.util.LinkedHashMap}), the fixed-order children list and the {@link BoxStyle}.
 * Text-bearing widgets hold a translation key prop ({@code text_key}); multilingual resolution
 * lives in {@link LocaleTable}, never in the node.
 *
 * <p>Subterra UI 组件树的单个节点（p.2.37.1）。不可变、纯 JDK、确定：id（全树唯一，由布局引擎
 * 强制）、固定序 {@link WidgetKind}、小写规范化的字符串 props（{@link java.util.LinkedHashMap}
 * 保持固定插入序）、固定序 children 与 {@link BoxStyle}。文本类控件持 translation key prop
 * （{@code text_key}）；多语言解析在 {@link LocaleTable}，节点不持最终语言文本。
 */
public record UiNode(
        String id,
        WidgetKind kind,
        Map<String, String> props,
        List<UiNode> children,
        BoxStyle box) {

    public UiNode {
        Objects.requireNonNull(id, "node id must not be null");
        if (id.isBlank()) {
            throw new IllegalArgumentException("node id must not be blank");
        }
        Objects.requireNonNull(kind, "node kind must not be null");
        Objects.requireNonNull(props, "node props must not be null");
        Objects.requireNonNull(children, "node children must not be null");
        Objects.requireNonNull(box, "node box must not be null");
        // Normalize prop keys to lowercase; preserve insertion order (deterministic).
        // Map.copyOf would drop insertion order — use an unmodifiable wrapper instead.
        // prop 键规范化为小写；保持插入序（确定性）。Map.copyOf 会丢插入序——用不可变包装。
        Map<String, String> normalized = new java.util.LinkedHashMap<>();
        for (Map.Entry<String, String> e : props.entrySet()) {
            Objects.requireNonNull(e.getValue(), "prop value must not be null: " + e.getKey());
            String key = e.getKey() == null ? null : e.getKey().trim().toLowerCase(Locale.ROOT);
            if (key == null || key.isBlank()) {
                throw new IllegalArgumentException("prop key must not be blank");
            }
            if (normalized.putIfAbsent(key, e.getValue()) != null) {
                throw new IllegalArgumentException("duplicate prop key: " + key);
            }
        }
        props = java.util.Collections.unmodifiableMap(normalized);
        children = List.copyOf(children);
    }

    /** A childless node with the default box. / 无子节点、缺省盒样式的便捷构造。 */
    public static UiNode leaf(String id, WidgetKind kind, Map<String, String> props) {
        return new UiNode(id, kind, props, List.of(), BoxStyle.DEFAULT);
    }

    /** A container node with the default box. / 缺省盒样式的容器便捷构造。 */
    public static UiNode container(String id, WidgetKind kind, Map<String, String> props,
                                   List<UiNode> children) {
        return new UiNode(id, kind, props, children, BoxStyle.DEFAULT);
    }

    /** The integer prop value, or the fallback when absent/malformed (deterministic). /
     *  整型 prop 值，缺失/畸形时回退（确定）。 */
    public int intProp(String key, int fallback) {
        String v = props.get(key);
        if (v == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /** Whether the prop is set to {@code true} (case-insensitive). / prop 是否为 {@code true}。 */
    public boolean boolProp(String key) {
        return Boolean.parseBoolean(props.get(key));
    }

    /** Whether the prop is present at all. / prop 是否存在。 */
    public boolean hasProp(String key) {
        return props.containsKey(key);
    }
}
