package io.toterra.subterra.engine.worldgen.pipeline.dimension;

/**
 * Marker interface identifying a diffusion-model backend (design §5, Plan A,
 * Step 5): a local OpenVINO / tink-form component, per the decentralization
 * &amp; performance discipline (inference runs locally, off the hot path).
 * <p>
 * The backend is intentionally <em>not implemented</em> this batch — this is an
 * interface only, and {@link #available()} is {@code false}. Enabling a specific
 * model requires an explicit td entry such as {@code [model, path, …]}; until a
 * model is present, every dimension slot falls back to its default algorithm
 * ({@link DimAlgo#VANILLA} / {@link DimAlgo#CONSTANT} / {@link DimAlgo#FORMULA}),
 * so vanilla parity is preserved with no runtime dependency on any model.
 * <p>
 * 标记接口，标识一个扩散模型后端（设计 §5，Plan A，第 5 步）：本地 OpenVINO /
 * tink-form 组件，符合去中心化与性能纪律（推理本地进行、不占热路径）。
 * <p>
 * 本批次后端刻意<em>未实现</em>——这仅是一个接口，且 {@link #available()} 为
 * {@code false}。启用具体模型需要显式 td 配置（如 {@code [model, path, …]}）；
 * 在未提供模型前，每个维度槽都回退到其默认算法（{@link DimAlgo#VANILLA} /
 * {@link DimAlgo#CONSTANT} / {@link DimAlgo#FORMULA}），从而在不对任何模型产生运行时
 * 依赖的前提下保持原版对等性。
 */
public interface ModelBackend {

    /**
     * Whether a model backend is currently available. Always {@code false} this
     * batch: enabling a model requires an explicit td {@code [model, path, …]}
     * configuration.
     *
     * @return {@code false} (backend not implemented / not yet enabled)
     */
    static boolean available() {
        return false;
    }
}