package io.toterra.subterra.engine.interact;

import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * 不可变交互表面 —— p.2.14.1 世界内交互规范中被交互的“一个可交互面”：描述世界内某个实体的可交互面
 * （种类、承载它的目标文本、可用世界内动作、态度）。`target` 原样照抄不重写；`actions` 构造时放入
 * 固定枚举集（内部以 {@link TreeSet} 承载，按枚举 {@link #ordinal()} 升降序确定、与传入次序无关），
 * 保证遍历/序列化顺序确定性；`attitude` 固定。
 * <p>
 * Immutable interaction surface — “one interactable surface” within a p.2.14.1 in-world interaction
 * spec: it describes the interactable surface of an in-world entity (its kind, the target text carrying
 * it, the available in-world actions, and its attitude). `target` is carried verbatim without rewriting;
 * `actions` is copied into a fixed enum set at construction (internally a {@link TreeSet} ordered by enum
 * {@link #ordinal()} regardless of caller order) so traversal/serialisation order is deterministic;
 * `attitude` is fixed.
 *
 * @param kind     实体种类 / the entity kind.
 * @param target   承载该可交互面的目标文本（原样照抄）/ the target text carrying the surface (verbatim).
 * @param actions  可用世界内动作（固定枚举集，TreeSet 序）/ the available in-world actions (fixed enum set, TreeSet order).
 * @param attitude 态度（数据模型内部语义，非渲染标注）/ the attitude (data-model-internal semantics, not a rendering tag).
 */
public record InteractSurface(InteractKind kind, String target, Set<InteractAction> actions,
                              Attitude attitude) {

    /**
     * 紧凑构造：空值防御 + 把动作集放入固定枚举集（TreeSet 序，与传入次序无关）。Compact constructor:
     * null-guards and copies the actions into a fixed enum set (TreeSet order, independent of caller order).
     */
    public InteractSurface {
        Objects.requireNonNull(kind, "surface kind must not be null");
        Objects.requireNonNull(target, "surface target must not be null");
        Objects.requireNonNull(actions, "surface actions must not be null");
        Objects.requireNonNull(attitude, "surface attitude must not be null");
        actions = new TreeSet<>(actions);
    }
}
