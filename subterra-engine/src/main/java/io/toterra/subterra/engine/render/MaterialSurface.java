package io.toterra.subterra.engine.render;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Material surface — p.2.27.1.2: an immutable record identifying a material surface
 * as a fixed-order triple of {@link RenderStage} + texture layer + buffer kind,
 * modeling the vanilla-fidelity material semantics (a stage, which texture the
 * surface samples, and which buffer / draw-call kind the surface is submitted
 * through). Deterministic: the component order is fixed, record equality is
 * component-wise, and {@link #form()} is the canonical fixed-order concatenation
 * {@code stage/buffer/texture} — byte-identical for identical components. All three
 * components are validated non-null / non-blank at construction. Pure JDK; no MC, no
 * OpenGL.
 *
 * <p>材质面——p.2.27.1.2：不可变 record，以固定序三元组 {@link RenderStage} + 纹理层 + 缓冲种类
 * 标识一个材质面，建模原版保真材质语义（阶段、表面采样的纹理、表面经由哪种缓冲/绘制调用种类提交）。
 * 确定性：组件序固定，record 相等性逐组件，且 {@link #form()} 为规范固定序拼接
 * {@code stage/buffer/texture}——相同组件逐字节一致。三个组件在构造时均校验非空/非空白。纯 JDK；
 * 无 MC、无 OpenGL。
 *
 * @param stage       渲染阶段 / the render stage.
 * @param textureLayer 纹理层名（材质采样的纹理层）/ the texture-layer name the surface samples.
 * @param bufferKind  缓冲种类（surface 提交经由的缓冲）/ the buffer kind the surface is submitted through.
 */
public record MaterialSurface(RenderStage stage, String textureLayer, String bufferKind) {

    /**
     * Compact constructor: rejects a null stage and null/blank names with
     * {@link NullPointerException} / {@link IllegalArgumentException} (deterministic
     * rejection). 紧凑构造器：以 {@link NullPointerException} / {@link IllegalArgumentException}
     * 拒绝 null 阶段与 null/空白名称（确定性拒绝）。
     */
    public MaterialSurface {
        Objects.requireNonNull(stage, "stage must be non-null");
        if (textureLayer == null || textureLayer.isBlank()) {
            throw new IllegalArgumentException("texture layer must be non-null and non-blank");
        }
        if (bufferKind == null || bufferKind.isBlank()) {
            throw new IllegalArgumentException("buffer kind must be non-null and non-blank");
        }
    }

    /**
     * Canonical fixed-order form {@code stage/buffer/texture} — the deterministic
     * string identity of the material surface (same components, same bytes).
     *
     * 规范固定序形式 {@code stage/buffer/texture}——材质面的确定性字符串标识（相同组件、相同字节）。
     */
    public String form() {
        return stage.form() + "/" + bufferKind + "/" + textureLayer;
    }

    /**
     * Canonical byte encoding of {@link #form()} (UTF-8). Same components, same bytes.
     * {@link #form()} 的规范字节编码（UTF-8）。相同组件、相同字节。
     */
    public byte[] formBytes() {
        return form().getBytes(StandardCharsets.UTF_8);
    }

    /** {@link #toString()} 恒返回规范 form。{@link #toString()} always returns the canonical form. */
    @Override
    public String toString() {
        return form();
    }
}
