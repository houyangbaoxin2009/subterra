// Async dispatch design derives from C2ME (MIT, Copyright (c) ishland, https://github.com/ishland/C2ME);
// implementation is original Subterra code.
package io.toterra.subterra.engine.parallel;

/**
 * The carrier of a deterministically merged result: one computed result bound to
 * its key. Its natural order is exactly the key's natural order, so the merged
 * list's order is a pure function of the keys — independent of bank timing,
 * thread scheduling, or submission order.
 *
 * <p>p.2.7.3 确定性键序归并的载体：一个计算结果与它的键绑定。自然序即 key 的自然序，
 * 因此归并列表的顺序只是键的纯函数——与 bank 时序、线程调度或提交顺序无关。
 *
 * @param key    the task key (the sort-order source).
 * @param result the computed result.
 */
public record TaskResult<K extends Comparable<K>, R>(K key, R result) implements Comparable<TaskResult<K, R>> {

    @Override
    public int compareTo(TaskResult<K, R> other) {
        return this.key.compareTo(other.key);
    }

    @Override
    public String toString() {
        // "key=result" shape for diagnostics.
        return key + "=" + result;
    }
}
