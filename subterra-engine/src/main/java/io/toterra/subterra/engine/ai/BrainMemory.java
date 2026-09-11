package io.toterra.subterra.engine.ai;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Deterministic typed memory slots (p.2.24.1): the brain's memory holds named
 * slots written by {@link BrainSensor sensors} and read by
 * {@link BrainBehavior behaviors} / {@link BrainTask tasks}. Keys iterate in
 * fixed insertion order — {@link #keys()} returns an unmodifiable copy and
 * {@code put} overwrites in place without moving the key's position. A missing
 * key yields {@code null} from {@link #get} and {@code false} from
 * {@link #has}. Null keys and null values are rejected with
 * {@link IllegalArgumentException}, so {@code get(k) == null} holds exactly
 * when {@code !has(k)}. No randomness, no timing, fixed order — identical
 * inputs yield identical outputs.
 *
 * <p>确定性类型化记忆槽（p.2.24.1）：大脑记忆持有具名槽位，由
 * {@link BrainSensor 感知器}写入、由 {@link BrainBehavior 行为} /
 * {@link BrainTask 任务}读取。键按固定插入序迭代——{@link #keys()} 返回不可变副本，
 * {@code put} 原位覆盖且不改变键的位置。缺键时 {@link #get} 返回 {@code null}、
 * {@link #has} 返回 {@code false}。null 键与 null 值均以
 * {@link IllegalArgumentException} 拒绝，从而 {@code get(k) == null} 当且仅当
 * {@code !has(k)}。无随机、无时序、固定序——同输入恒得同输出。
 *
 * <p>Model reference only (clean-room, zero code included): the "typed memory
 * slot" layering idea of the vanilla {@code net.minecraft.world.entity.ai.Brain}
 * and SmartBrainLib (MPL-2.0); this implementation is original deterministic
 * Subterra code. Pure JDK, no Minecraft code touched.
 * <p>模型参考（clean-room，零代码包含）：原版 {@code net.minecraft.world.entity.ai.Brain}
 * 与 SmartBrainLib（MPL-2.0）的「类型化 memory 槽」分层思想；本实现为 Subterra 原创确定性
 * 代码。纯 JDK，不触碰任何 MC 代码。
 */
public final class BrainMemory {

    private final Map<String, Object> slots = new LinkedHashMap<>();

    /** Creates an empty memory. 创建空记忆。 */
    public BrainMemory() {
    }

    /**
     * Writes (or overwrites in place) the slot {@code key} with {@code value},
     * preserving the key's position in the fixed iteration order. A null/blank
     * key or a null value is rejected with {@link IllegalArgumentException}.
     * Deterministic.
     *
     * 将槽位 {@code key} 写入（或原位覆盖）为 {@code value}，保持键在固定迭代序中的位置。
     * null/空白键或 null 值以 {@link IllegalArgumentException} 拒绝。确定性。
     *
     * @param key   the slot key. 槽位键。
     * @param value the slot value (non-null). 槽位值（非 null）。
     * @return {@code this}, for chaining. {@code this}，便于链式调用。
     */
    public BrainMemory put(String key, Object value) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("memory key must be non-null and non-blank");
        }
        if (value == null) {
            throw new IllegalArgumentException("memory value must be non-null: " + key);
        }
        slots.put(key, value);
        return this;
    }

    /**
     * The value bound to {@code key}, or {@code null} when the key is absent.
     * 键 {@code key} 绑定的值；键缺失时为 {@code null}。
     */
    public Object get(String key) {
        return slots.get(key);
    }

    /**
     * Whether a slot with key {@code key} is present. 键 {@code key} 的槽位是否存在。
     */
    public boolean has(String key) {
        return slots.containsKey(key);
    }

    /**
     * All keys as an unmodifiable copy in fixed insertion order (deterministic).
     * 全部键，按固定插入序返回不可变副本（确定性）。
     */
    public List<String> keys() {
        return List.copyOf(slots.keySet());
    }
}
