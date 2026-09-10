package io.toterra.subterra.engine.render;

import java.util.Objects;

/**
 * Render-surface identifier — p.2.27.1.2: an immutable record pairing a
 * {@link RenderStage} with a stage-local surface name, identifying one render
 * surface of the vanilla-fidelity model. Deterministic: the same {@code stage} and
 * {@code name} always produce the same instance semantics (record equality), the
 * components are fixed-order, and the record's {@link #toString()} is a fixed
 * canonical form. Both components are validated non-null / non-blank at construction
 * (deterministic rejection). Pure JDK; no MC, no OpenGL.
 *
 * <p>渲染面标识——p.2.27.1.2：不可变 record，将 {@link RenderStage} 与该阶段内的表面名配对，标识
 * 原版保真模型中的一个渲染面。确定性：相同的 {@code stage} 与 {@code name} 恒产生相同的实例语义
 * （record 相等性），组件固定序，且 record 的 {@link #toString()} 为固定规范形。两个组件在构造时
 * 校验非空/非空白（确定性拒绝）。纯 JDK；无 MC、无 OpenGL。
 *
 * @param stage 渲染阶段 / the render stage.
 * @param name  阶段内表面名 / the surface name within the stage.
 */
public record RenderSurface(RenderStage stage, String name) {

    /**
     * Compact constructor: rejects a null stage and a null/blank name with
     * {@link NullPointerException} / {@link IllegalArgumentException} (deterministic
     * rejection). 紧凑构造器：以 {@link NullPointerException} / {@link IllegalArgumentException}
     * 拒绝 null 阶段与 null/空白名称（确定性拒绝）。
     */
    public RenderSurface {
        Objects.requireNonNull(stage, "stage must be non-null");
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("surface name must be non-null and non-blank");
        }
    }

    /**
     * Canonical fixed-order form {@code stage/name} (byte-deterministic: same
     * components, same text). 规范固定序形式 {@code stage/name}（逐字节确定：相同组件、相同文本）。
     */
    @Override
    public String toString() {
        return stage.form() + "/" + name;
    }
}
