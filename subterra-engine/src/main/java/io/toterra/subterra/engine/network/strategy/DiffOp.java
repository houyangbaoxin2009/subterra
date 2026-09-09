package io.toterra.subterra.engine.network.strategy;

import io.toterra.subterra.engine.network.strategy.FieldSnapshot.Value;

import java.util.Objects;

/**
 * 单键差分操作（p.2.4.3）：{@code SET} 携带新值，{@code UNSET} 移除该键（不携带值，
 * {@code value} 为 null）。作为不可变 record，天然满足确定性等值比较，是
 * {@link IncrementalDiff} 与 {@link ChangeStream} 的最小组成单元。
 * <p>
 * Single-key diff operation (p.2.4.3): {@link DiffOp.Kind#SET} carries a new value,
 * {@link DiffOp.Kind#UNSET} removes the key (no value, null). As an immutable record it provides
 * deterministic value equality and is the atomic unit of {@link IncrementalDiff} and
 * {@link ChangeStream}.
 */
public record DiffOp(String key, DiffOp.Kind kind, Value value) {

    /** 操作种类。Operation kind. */
    public enum Kind {
        /** 设置/变更该键为新值。Set / change the key to a new value. */
        SET,
        /** 移除该键。Remove the key. */
        UNSET
    }

    /** 构造校验：key 非空；SET 须带值，UNSET 须无值。Validate: non-null key; SET carries a value, UNSET none. */
    public DiffOp {
        Objects.requireNonNull(key, "diff op key must not be null");
        Objects.requireNonNull(kind, "diff op kind must not be null");
        if (kind == Kind.SET && value == null) {
            throw new IllegalArgumentException("SET diff op must carry a value for key: " + key);
        }
        if (kind == Kind.UNSET && value != null) {
            throw new IllegalArgumentException("UNSET diff op must not carry a value for key: " + key);
        }
    }

    /** SET 工厂。SET factory. */
    public static DiffOp set(String key, Value value) {
        return new DiffOp(key, Kind.SET, value);
    }

    /** UNSET 工厂。UNSET factory. */
    public static DiffOp unset(String key) {
        return new DiffOp(key, Kind.UNSET, null);
    }
}