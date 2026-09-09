package io.toterra.subterra.engine.interact;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * 不可变世界状态快照 —— p.2.14.2 确定性交互规则引擎的求值输入/输出载体。它以 {@link String} 键值对承载
 * "世界内的既定事实"（界内语境，如持有何物、已向谁呈奉），而非任何系统层状态（等级/计数/日志/进度等都不属
 * 世界内语言，不在此建模 —— 语义见 p.2.14.1 {@code package-info}）。内部以 {@link TreeMap} 承载，保证<b>固定
 * 键序</b>；所有构造与追加都做防御拷贝，实例一经创建即不可变，同输入两次序列化/遍历结果逐字节一致（确定性快照）。
 * <p>
 * 确定性追加为本类的核心契约：{@link #with(String, String)} 返回一个在键序上确定性追加（或覆盖）给定键后的
 * <b>新</b>快照，原实例不变；{@link #snapshot()} 返回当前内容的等值不可变副本。全类无随机/时间戳/时序依赖。
 * <p>
 * Immutable in-world state snapshot — the input/output carrier of the p.2.14.2 deterministic interaction-rule
 * engine. It carries {@link String} key-value pairs of "established facts in the world" (in-world context,
 * e.g. what is carried, what has been offered to whom), not any system-layer state (levels / counters / logs /
 * progress are not in-world language and are not modelled here — see the p.2.14.1 {@code package-info}). It is
 * backed by a {@link TreeMap} for <b>fixed key order</b>; every construction and append is a defensive copy, so
 * an instance is immutable once created and obtained/serialised from identical input it is byte-identical
 * (a deterministic snapshot).
 * <p>
 * Deterministic appending is this class's core contract: {@link #with(String, String)} returns a <b>new</b>
 * snapshot that deterministically appends (or overwrites, on fixed key order) the given key, leaving this
 * instance unchanged; {@link #snapshot()} returns an equal immutable copy of the current content. No random /
 * timestamp / timing dependency anywhere in the class.
 */
public final class InteractState {

    private final TreeMap<String, String> values;

    /** 空状态快照（空 {@code TreeMap}）。Constructs an empty state snapshot (an empty {@code TreeMap}). */
    public InteractState() {
        this.values = new TreeMap<>();
    }

    /**
     * 以给定键值对构造状态快照（防御拷入 {@code TreeMap}，固定键序）。Constructs a state snapshot from the
     * given entries (defensively copied into a fixed-key-order {@code TreeMap}).
     *
     * @param values 初始键值对，必非 null / the initial entries, must not be null.
     */
    public InteractState(Map<String, String> values) {
        Objects.requireNonNull(values, "state values must not be null");
        this.values = new TreeMap<>(values);
    }

    /**
     * 返回当前内容的一个等值不可变副本（确定性快照）。Returns an equal immutable copy of the current content
     * (a deterministic snapshot).
     *
     * @return 等值的新快照 / an equal new snapshot.
     */
    public InteractState snapshot() {
        return new InteractState(this.values);
    }

    /**
     * 确定性追加：返回在固定键序上追加（或覆盖）{@code (key, value)} 之后的<b>新</b>快照，本实例不变。
     * Deterministically appends: returns a <b>new</b> snapshot with {@code (key, value)} appended (or overwritten)
     * on fixed key order; this instance is unchanged.
     *
     * @param key   键 / the key.
     * @param value 值 / the value.
     * @return 追加后的新快照 / the appended new snapshot.
     */
    public InteractState with(String key, String value) {
        Objects.requireNonNull(key, "state key must not be null");
        Objects.requireNonNull(value, "state value must not be null");
        TreeMap<String, String> copy = new TreeMap<>(this.values);
        copy.put(key, value);
        return new InteractState(copy);
    }

    /**
     * 读取某键的值；不存在返回 {@code null}。Reads the value of a key; returns {@code null} if absent.
     *
     * @param key 键 / the key.
     * @return 对应值或 null / the corresponding value, or {@code null}.
     */
    public String get(String key) {
        return values.get(key);
    }

    /**
     * 是否含某键。Whether a key is present.
     *
     * @param key 键 / the key.
     * @return 含则 true / true if present.
     */
    public boolean contains(String key) {
        return values.containsKey(key);
    }

    /**
     * 以固定键序返回只读快照视图（防御拷贝，不泄露内部可变结构）。Returns a read-only, fixed-key-order view of the
     * snapshot (a defensive copy that never exposes internal mutable structure).
     *
     * @return 只读、固定键序的视图 / a read-only, fixed-key-order view.
     */
    public SortedMap<String, String> values() {
        return Collections.unmodifiableSortedMap(new TreeMap<>(this.values));
    }

    /**
     * 键值等价（与 TreeMap 序无关）。Entry-based equality (independent of the TreeMap order).
     *
     * @param o 对比对象 / the object to compare.
     * @return 逐键值相等则 true / true when entry-wise equal.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof InteractState that)) {
            return false;
        }
        return this.values.equals(that.values);
    }

    /** 与 {@code values} 一致的哈希码。Hash code consistent with {@code values}. */
    @Override
    public int hashCode() {
        return values.hashCode();
    }

    /**
     * 确定性字符串表达（固定键序）。Deterministic string representation (fixed key order).
     *
     * @return 表示文本 / the representation text.
     */
    @Override
    public String toString() {
        return values.toString();
    }
}