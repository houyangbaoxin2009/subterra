package io.toterra.subterra.engine.render;

/**
 * Vanilla-fidelity render-stage enum — p.2.27.1.2 the base stage set of the
 * clean-room surface model, mirroring the semantic span of the Minecraft 1.21.1
 * vanilla render layers ({@code net.minecraft.client.renderer.RenderType}, reference
 * only: official names and semantics as listed by the Yarn 1.21.1
 * {@code RenderLayer} mapping — {@code getSolid / getCutout / getCutoutMipped /
 * getTranslucent / getTripwire / getBeaconBeam / getClouds / getLightning /
 * getWaterMask / getTranslucentMovingBlock}). This enum is a clean-room data model:
 * only stage classification / buffer / material semantics are modeled as a fixed-order
 * deterministic set — no vanilla, Sodium or Embeddium source code is included. The
 * authoritative order (which is also the {@link #values()} order) is fixed and never
 * reordered; the enum is never extended dynamically. Each stage carries its lowercase
 * registration name {@link #form()}, its {@link #transparent()} boolean semantics
 * (whether the stage blends with what is already drawn — vanilla's translucent flag),
 * and its {@link #sortOnCpu()} boolean semantics (whether the vanilla pipeline sorts
 * the stage's geometry on the CPU by view distance before upload, e.g. the translucent
 * terrain layer). Deterministic: fixed order, no timing, no randomness. Pure JDK; no
 * MC, no OpenGL.
 *
 * <p>原版保真渲染阶段枚举——p.2.27.1.2，clean-room 渲染面模型的基础阶段集，覆盖 Minecraft 1.21.1
 * 原版渲染层（{@code net.minecraft.client.renderer.RenderType}，仅作对照参考：规范名与语义取自
 * Yarn 1.21.1 {@code RenderLayer} 映射——{@code getSolid / getCutout / getCutoutMipped /
 * getTranslucent / getTripwire / getBeaconBeam / getClouds / getLightning / getWaterMask /
 * getTranslucentMovingBlock}）的语义跨度。本枚举是 clean-room 数据模型：只以固定序确定性集合建模
 * 渲染面的分类/缓冲/材质语义——不包含任何原版、Sodium 或 Embeddium 源码。权威序（即
 * {@link #values()} 序）固定且绝不重排；枚举不做动态扩展。每阶段携带小写注册名 {@link #form()}、
 * {@link #transparent()} 布尔语义（该阶段是否与已绘制内容混合——对应原版 translucent 标志），以及
 * {@link #sortOnCpu()} 布尔语义（原版管线是否在 CPU 端按视距排序该阶段几何后再上传，例如半透明地形
 * 层）。确定性：固定序、无时序、无随机。纯 JDK；无 MC、无 OpenGL。
 */
public enum RenderStage {

    /** 不透明地形层（原版 {@code solid}）。Opaque terrain layer (vanilla {@code solid}). */
    SOLID("solid", false, false),
    /** 镂空层，硬 alpha 剔除（原版 {@code cutout}）。Cutout layer, hard alpha discard (vanilla {@code cutout}). */
    CUTOUT("cutout", false, false),
    /** 镂空 + mipmap 纹理层（原版 {@code cutout_mipped}）。Cutout with mipmapped texture (vanilla {@code cutout_mipped}). */
    CUTOUT_MIPPED("cutout_mipped", false, false),
    /** 半透明地形层，CPU 端按视距排序（原版 {@code translucent}）。Translucent terrain layer, CPU-sorted by view distance (vanilla {@code translucent}). */
    TRANSLUCENT("translucent", true, true),
    /** 绊线层（原版 {@code tripwire}）。Tripwire layer (vanilla {@code tripwire}). */
    TRIPWIRE("tripwire", false, false),
    /** 信标光束层，半透明混合、不排序（原版 {@code beacon_beam}）。Beacon beam layer, translucent blend, unsorted (vanilla {@code beacon_beam}). */
    BEACON_BEAM("beacon_beam", true, false),
    /** 云层，半透明、专用云渲染器（原版 {@code clouds}）。Clouds layer, translucent, dedicated cloud renderer (vanilla {@code clouds}). */
    CLOUDS("clouds", true, false),
    /** 闪电层，加色半透明混合（原版 {@code lightning}）。Lightning layer, additive translucent blend (vanilla {@code lightning}). */
    LIGHTNING("lightning", true, false),
    /**
     * 水面遮罩层。注意：1.21.1 规范中没有独立的 {@code water} 渲染层——水使用 {@link #TRANSLUCENT}
     * 地形层；{@code water_mask} 是渲染水面下方内容时的遮罩层（原版 {@code water_mask}）。
     * Water mask layer. Note: the 1.21.1 spec has no standalone {@code water} render
     * layer — water renders through the {@link #TRANSLUCENT} terrain layer;
     * {@code water_mask} is the mask layer drawn under the water surface (vanilla
     * {@code water_mask}).
     */
    WATER_MASK("water_mask", false, false),
    /** 活塞等移动中的半透明方块层（原版 {@code translucent_moving_block}）。Translucent blocks being moved (e.g. pistons) (vanilla {@code translucent_moving_block}). */
    TRANSLUCENT_MOVING_BLOCK("translucent_moving_block", true, true);

    private final String form;
    private final boolean transparent;
    private final boolean sortOnCpu;

    RenderStage(String form, boolean transparent, boolean sortOnCpu) {
        this.form = form;
        this.transparent = transparent;
        this.sortOnCpu = sortOnCpu;
    }

    /** 小写注册名（规范 form）。The lowercase registration name (canonical form). */
    public String form() {
        return form;
    }

    /**
     * 该阶段是否与已绘制内容混合（原版 translucent 语义）。Whether this stage blends with what is
     * already drawn (vanilla translucent semantics).
     */
    public boolean transparent() {
        return transparent;
    }

    /**
     * 该阶段是否需在 CPU 端按视距排序后提交（例如半透明地形层）。Whether this stage must be sorted
     * on the CPU by view distance before submission (e.g. the translucent terrain layer).
     */
    public boolean sortOnCpu() {
        return sortOnCpu;
    }

    /**
     * 由小写注册名解析阶段；未知文本返回 {@code null}。Resolves the stage from its lowercase
     * registration name; returns {@code null} for an unknown text.
     *
     * @param form 小写注册名 / the lowercase registration name.
     * @return 匹配的阶段或 null / the matching stage, or {@code null}.
     */
    public static RenderStage fromForm(String form) {
        for (RenderStage s : values()) {
            if (s.form.equals(form)) {
                return s;
            }
        }
        return null;
    }
}
