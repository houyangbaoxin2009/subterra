package io.toterra.subterra.engine.render.instancing;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Deterministic backend registry (p.2.27.1.1), following the engine's registry
 * pattern (cf. {@code engine.export.ExportHub} / {@code api.export.ExporterRegistry}):
 * fixed registration order (insertion order; deterministic, no timing, no
 * randomness), a second registration with the same {@code name()} rejected with
 * {@link IllegalArgumentException}, {@link #lookup(String)} by name returning
 * {@code null} when unregistered, and deterministic default selection via
 * {@link #defaultFor(InstanceFormat)} — the supported backend with the highest
 * {@link RenderBackend#priority()}, ties broken by registration order (earlier
 * registered wins). {@link #clear()} resets the registry for probe isolation. Pure
 * JDK; no MC, no OpenGL.
 *
 * <p>确定性后端注册表（p.2.27.1.1），沿用引擎注册表范式（参考 {@code engine.export.ExportHub} /
 * {@code api.export.ExporterRegistry}）：固定注册序（插入序；确定性、无时序、无随机）、同名
 * {@code name()} 二次注册以 {@link IllegalArgumentException} 拒绝、{@link #lookup(String)}
 * 按名查表未注册返回 {@code null}、{@link #defaultFor(InstanceFormat)} 确定性缺省选择——取
 * {@link RenderBackend#priority()} 最高且受支持者，平局按注册序（先注册者胜）。{@link #clear()}
 * 重置注册表供探针隔离。纯 JDK；无 MC、无 OpenGL。
 */
public final class BackendRegistry {

    private static final Map<String, RenderBackend> BACKENDS = new LinkedHashMap<>();

    private BackendRegistry() {
    }

    /**
     * Registers a backend in insertion order. A null backend, a null/blank name, or a
     * second registration with the same name is rejected with
     * {@link IllegalArgumentException}. Deterministic, no timing.
     *
     * 按插入序注册后端。后端为 null、名为 null/空白、或同名二次注册均以
     * {@link IllegalArgumentException} 拒绝。确定性、无时序。
     */
    public static void register(RenderBackend backend) {
        Objects.requireNonNull(backend, "backend must be non-null");
        String name = backend.name();
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("backend name must be non-null and non-empty");
        }
        if (BACKENDS.containsKey(name)) {
            throw new IllegalArgumentException("backend already registered: " + name);
        }
        BACKENDS.put(name, backend);
    }

    /**
     * Returns the backend registered under the given name, or {@code null} when none
     * is registered (or the name is {@code null}). 返回指定名注册的后端；未注册（或名为
     * {@code null}）时返回 {@code null}。
     */
    public static RenderBackend lookup(String name) {
        if (name == null) {
            return null;
        }
        return BACKENDS.get(name);
    }

    /**
     * All registered backends as a copy in registration order (deterministic).
     * 全部已注册后端，按注册序返回副本（确定性）。
     */
    public static List<RenderBackend> backends() {
        return List.copyOf(BACKENDS.values());
    }

    /**
     * All registered names as a copy in registration order (deterministic).
     * 全部已注册名，按注册序返回副本（确定性）。
     */
    public static List<String> names() {
        return List.copyOf(BACKENDS.keySet());
    }

    /**
     * Deterministic default backend for a format: the supported backend with the
     * highest {@link RenderBackend#priority()}; ties are broken by registration order
     * (earlier registered wins), and a {@code null} format is treated as "no format
     * constraint" (every backend is eligible). Returns {@code null} when no registered
     * backend qualifies. Fixed order, no timing, no randomness.
     *
     * 针对某格式的确定性缺省后端：取支持该格式且 {@link RenderBackend#priority()} 最高者；平局按
     * 注册序（先注册者胜）；{@code null} 格式视为"无格式约束"（所有后端均合格）。无合格者时返回
     * {@code null}。固定序、无时序、无随机。
     */
    public static RenderBackend defaultFor(InstanceFormat format) {
        RenderBackend best = null;
        for (RenderBackend backend : BACKENDS.values()) {
            if (format != null && !backend.supports(format)) {
                continue;
            }
            if (best == null || backend.priority() > best.priority()) {
                best = backend;
            }
        }
        return best;
    }

    /** Clears the registry (probe isolation). 清空注册表（探针隔离）。 */
    public static void clear() {
        BACKENDS.clear();
    }
}
