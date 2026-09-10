package io.toterra.subterra.engine.anim;

import java.util.Objects;

/**
 * An assembled animation (p.2.21.1): {@link AnimationData} bound to a target model
 * name (e.g. an entity / block / item model id). Immutable; the target model name is
 * a plain string so the engine stays pure JDK — model resolution is left to the
 * runtime layer.
 *
 * <p>组装后的动画（p.2.21.1）：{@link AnimationData} 绑定目标模型名（如实体/方块/物品模型
 * id）。不可变；目标模型名为普通字符串，引擎保持纯 JDK——模型解析留给 runtime 层。
 *
 * @param data        the animation data. 动画数据。
 * @param targetModel the target model binding name (non-blank). 目标模型绑定名（非空白）。
 */
public record Animation(AnimationData data, String targetModel) {

    public Animation {
        Objects.requireNonNull(data, "data must be non-null");
        if (targetModel == null || targetModel.isBlank()) {
            throw new IllegalArgumentException("targetModel must be non-null and non-blank");
        }
    }
}
