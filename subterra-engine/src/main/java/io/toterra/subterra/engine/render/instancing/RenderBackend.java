package io.toterra.subterra.engine.render.instancing;

/**
 * Backend SPI (p.2.27.1.1), modeled on Flywheel 1.0.x {@code api.backend.Backend}
 * (fetched from the {@code mc1.21.1/v1.0.5} tag of the Flywheel line that ships
 * 1.0.6 — see {@code META-INF/third-party/flywheel-1.0.6}): a backend is identified
 * by a stable {@link #name()}, declares which {@link InstanceFormat}s it
 * {@link #supports(InstanceFormat)s}, carries a {@link #description()}, and exposes a
 * {@link #priority()} used for the deterministic default selection (highest priority;
 * ties broken by registration order — see {@link BackendRegistry#defaultFor}). This
 * sub-item only models the SPI: no Minecraft, no OpenGL; actual GPU work is left to
 * the runtime layer.
 *
 * <p>后端 SPI（p.2.27.1.1），以 Flywheel 1.0.x {@code api.backend.Backend} 为形态参考（抓取自
 * 承载 1.0.6 的 Flywheel 线的 {@code mc1.21.1/v1.0.5} tag——见
 * {@code META-INF/third-party/flywheel-1.0.6}）：后端以稳定的 {@link #name()} 标识，声明其
 * {@link #supports(InstanceFormat)} 的 {@link InstanceFormat}、携带 {@link #description()}，
 * 并暴露用于确定性缺省选择的 {@link #priority()}（最高优先级；平局按注册序——见
 * {@link BackendRegistry#defaultFor}）。本子项仅建模 SPI：无 MC、无 OpenGL；实际 GPU 调用留给
 * runtime 层。
 */
public interface RenderBackend {

    /**
     * Stable unique backend name (registry key). 稳定唯一的后端名（注册表键）。
     */
    String name();

    /**
     * Whether this backend supports the given format. {@code null} is not supported
     * and returns {@code false}. 该后端是否支持给定格式。{@code null} 视为不支持并返回
     * {@code false}。
     */
    boolean supports(InstanceFormat format);

    /**
     * Human-readable description of the backend. 后端的人类可读描述。
     */
    String description();

    /**
     * Priority used by the deterministic default selection in
     * {@link BackendRegistry#defaultFor(InstanceFormat)}: the supported backend with
     * the highest priority is selected; ties are broken by registration order
     * (earlier registered wins). Defaults to 0.
     *
     * 供 {@link BackendRegistry#defaultFor(InstanceFormat)} 确定性缺省选择使用的优先级：
     * 选择受支持且优先级最高者；平局按注册序（先注册者胜）。默认为 0。
     */
    default int priority() {
        return 0;
    }
}
