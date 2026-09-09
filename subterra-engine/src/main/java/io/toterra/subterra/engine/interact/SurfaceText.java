package io.toterra.subterra.engine.interact;

import java.util.List;
import java.util.Objects;

/**
 * 不可变表面文本 —— p.2.14.3 零 HUD 表面映射的单一产物：描述某一 {@link InteractSurface} 应呈现的世界内语言
 * 文本。`target` 原样照抄（与表面 {@code target} 相等即命中）；`kind` 原样照抄表面种类；`lines` 承载<b>固定序</b>
 * 的世界内语言行（首行为立案性文本，后续行为按状态追加的回应行，均只来自固定短语表或世界状态，无任何系统层
 * 语言）。构造时对 `lines` 做防御拷贝，实例一经创建即不可变；同一输入同一表面两次映射逐字段一致。
 * <p>
 * Immutable surface text — the single product of the p.2.14.3 zero-HUD surface mapping: it describes the
 * in-world-language text a given {@link InteractSurface} should present. `target` is carried verbatim (it hits
 * when equal to the surface's {@code target}); `kind` is carried verbatim from the surface kind; `lines` carries
 * the <b>fixed-order</b> in-world-language lines (the first line is the establishment text, and following lines
 * are state-appended reply lines, all sourced from the fixed phrase table or the in-world state only, with no
 * system-layer language). The compact constructor defensively copies `lines`, so an instance is immutable once
 * created; the same input mapped twice for the same surface is field-for-field identical.
 *
 * @param target 承载该表面文本的世界内目标文本（原样照抄）/ the in-world target text carrying this surface text (verbatim).
 * @param kind   实体种类（原样照抄）/ the entity kind (verbatim).
 * @param lines  固定序世界内语言行（防御拷贝）/ the fixed-order in-world-language lines (defensively copied).
 */
public record SurfaceText(String target, InteractKind kind, List<String> lines) {

    /**
     * 紧凑构造：空值防御 + 行列表防御拷贝（保持固定序，返回不可变列表）。Compact constructor: null-guards and a
     * defensive copy of the line list (preserving the fixed order, returned as an immutable list).
     */
    public SurfaceText {
        Objects.requireNonNull(target, "surface-text target must not be null");
        Objects.requireNonNull(kind, "surface-text kind must not be null");
        Objects.requireNonNull(lines, "surface-text lines must not be null");
        lines = List.copyOf(lines);
    }
}
