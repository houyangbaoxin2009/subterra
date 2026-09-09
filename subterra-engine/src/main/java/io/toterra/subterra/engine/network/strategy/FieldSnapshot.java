package io.toterra.subterra.engine.network.strategy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * 不可变的确定性有序字段快照（p.2.4.3）：对字段类型集合 {@link Value}（{long,double,String}）的命名值
 * 视图，键按字典序稳定排序。字段语义由业务层赋予，本类只保证确定性排序、不可变性、等值比较。
 * 构建线性（内部经 {@link TreeMap} 一次建树），快照大时无 O(n²) 操作。
 * <p>
 * Immutable, deterministically ordered field snapshot (p.2.4.3): a view of named values of type
 * {@link Value} ({long, double, String}), keys stably sorted lexicographically. Field semantics are
 * assigned by the domain layer; this class only guarantees deterministic ordering, immutability and
 * value equality. Construction is linear (a single {@link TreeMap} build); no O(n²) work for large
 * snapshots.
 */
public final class FieldSnapshot {

    /**
     * 字段值类型：{@link Num}(long)、{@link Real}(double)、{@link Text}(String) 三选一。值的缺失用
     * key 不在快照中表示（没有显式 null 值）。
     * <p>
     * Field value type: one of {@link Num}(long), {@link Real}(double), {@link Text}(String). Absence
     * is represented by the key not being present (there is no explicit null value).
     */
    public sealed interface Value {
        /** long 值字段。Long-valued field. */
        record Num(long value) implements Value {
        }

        /** double 值字段。Double-valued field. */
        record Real(double value) implements Value {
        }

        /** String 值字段。String-valued field. */
        record Text(String value) implements Value {
        }

        /** 便捷工厂。Convenience factory for a long value. */
        static Num num(long v) {
            return new Num(v);
        }

        /** 便捷工厂。Convenience factory for a double value. */
        static Real real(double v) {
            return new Real(v);
        }

        /** 便捷工厂。Convenience factory for a string value. */
        static Text text(String v) {
            return new Text(v);
        }
    }

    private static final String IAE_NULL_KEY = "field key must not be null";
    private static final String IAE_NULL_VALUE = "field value must not be null for key: ";

    private final Map<String, Value> map;
    private final List<Map.Entry<String, Value>> sortedEntries;
    private final List<String> sortedKeys;

    private FieldSnapshot(Map<String, Value> fields) {
        Map<String, Value> normalized = new TreeMap<>();
        for (Map.Entry<String, Value> e : fields.entrySet()) {
            String k = e.getKey();
            Value v = e.getValue();
            if (k == null) {
                throw new IllegalArgumentException(IAE_NULL_KEY);
            }
            if (v == null) {
                continue; // null 表示缺失 / null means absent
            }
            normalized.put(k, v);
        }
        this.map = Collections.unmodifiableMap(normalized);
        this.sortedEntries = List.copyOf(this.map.entrySet());
        this.sortedKeys = new ArrayList<>(this.map.keySet());
    }

    /**
     * 归一化构建：拷贝 + 按 key 稳定排序，跳过 null 值。Normalising factory: copies + sorts by key,
     * skipping null values.
     */
    public static FieldSnapshot normalize(Map<String, Value> fields) {
        Objects.requireNonNull(fields, "fields map must not be null");
        return new FieldSnapshot(fields);
    }

    /** 空快照。Empty snapshot. */
    public static FieldSnapshot empty() {
        return new FieldSnapshot(Collections.emptyMap());
    }

    /** 新建 builder。Create a new builder. */
    public static Builder builder() {
        return new Builder();
    }

    /** 字段数（仅含 present 的 key）。Number of present fields. */
    public int size() {
        return map.size();
    }

    /** 是否空。Whether empty. */
    public boolean isEmpty() {
        return map.isEmpty();
    }

    /** 键是否 present。Whether a key is present. */
    public boolean containsKey(String key) {
        return map.containsKey(key);
    }

    /** 取值；缺失返回 null。Value; null if absent. */
    public Value value(String key) {
        return map.get(key);
    }

    /**
     * 稳定字典序的键集合。Stably lexicographically ordered key view.
     */
    public List<String> sortedKeys() {
        return sortedKeys;
    }

    /**
     * 稳定字典序的条目视图（快照创建时计算一次）。Stably ordered entry view (computed once at creation).
     */
    public List<Map.Entry<String, Value>> entries() {
        return sortedEntries;
    }

    /** 快照间等值（按字段内容）。Value equality over field contents. */
    public static boolean same(FieldSnapshot a, FieldSnapshot b) {
        return a.equals(b);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof FieldSnapshot other)) {
            return false;
        }
        return map.equals(other.map);
    }

    @Override
    public int hashCode() {
        return map.hashCode();
    }

    @Override
    public String toString() {
        return "FieldSnapshot" + map;
    }

    /** 快照 builder（内部 TreeMap，天然确定序）。Snapshot builder (internally a TreeMap, naturally ordered). */
    public static final class Builder {
        private final Map<String, Value> m = new TreeMap<>();

        Builder() {
        }

        /** 写入字段；value 为 null 视为缺失。Put a field; null value treated as absent. */
        public Builder put(String key, Value value) {
            m.put(key, value);
            return this;
        }

        /** 写入 long 字段。Put a long field. */
        public Builder putNum(String key, long v) {
            return put(key, Value.num(v));
        }

        /** 写入 double 字段。Put a double field. */
        public Builder putReal(String key, double v) {
            return put(key, Value.real(v));
        }

        /** 写入 String 字段。Put a string field. */
        public Builder putText(String key, String v) {
            return put(key, Value.text(v));
        }

        /** 构建（线性，无序可确定性建成）。Build (linear, deterministic regardless of insertion order). */
        public FieldSnapshot build() {
            return new FieldSnapshot(m);
        }
    }
}