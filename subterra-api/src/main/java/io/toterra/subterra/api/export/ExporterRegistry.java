package io.toterra.subterra.api.export;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 确定性 Exporter 注册表契约（p.2.16.3）：把 {@link ExportKindSpec} 绑定到其
 * {@link ExportPortSpec} 的静态注册表，作为 p.2.18 export hub 的契约前哨。注册序固定为
 * 注册先后（确定性，无时序、无随机、无时间戳）；同 {@code form()} 二次注册以
 * {@link IllegalArgumentException} 拒绝，null（kind/port/form）拒绝；
 * {@link #port(ExportKindSpec)} 按 form 查表，未注册返回 {@code null}；{@link #kinds()}
 * 按注册序返回副本。引擎侧实现参考为 {@code io.toterra.subterra.engine.export.ExportHub}
 * （p.2.9.5 预留）。
 *
 * <p>Deterministic Exporter registry contract (p.2.16.3): the static registry binding
 * an {@link ExportKindSpec} to its {@link ExportPortSpec} — the contract precursor of
 * the p.2.18 export hub. Registration order is fixed (insertion order; deterministic,
 * no timing, no randomness, no timestamps); a second registration with the same
 * {@code form()} is rejected with {@link IllegalArgumentException}, as are null
 * kind/port/form; {@link #port(ExportKindSpec)} looks up by form and returns
 * {@code null} when unregistered; {@link #kinds()} returns a copy in registration
 * order. The engine-side implementation reference is
 * {@code io.toterra.subterra.engine.export.ExportHub} (p.2.9.5 placeholder).
 */
public final class ExporterRegistry {

    private static final Map<String, ExportKindSpec> KINDS = new LinkedHashMap<>();
    private static final Map<String, ExportPortSpec> PORTS = new LinkedHashMap<>();

    private ExporterRegistry() {
    }

    /**
     * 为某种类注册一个端口，注册序即注册先后。同 {@code form()} 二次注册（或 kind/port/form
     * 为空）以 {@link IllegalArgumentException} 拒绝。确定性、无时序。
     *
     * Registers a port for a kind; the registration order is the insertion order. A
     * second registration with the same {@code form()} (or a null kind/port/form) is
     * rejected with {@link IllegalArgumentException}. Deterministic, no timing.
     */
    public static void register(ExportKindSpec kind, ExportPortSpec port) {
        if (kind == null || port == null) {
            throw new IllegalArgumentException("export kind and port must be non-null");
        }
        String form = kind.form();
        if (form == null) {
            throw new IllegalArgumentException("export kind form must be non-null");
        }
        if (KINDS.containsKey(form)) {
            throw new IllegalArgumentException("export kind already registered: " + form);
        }
        KINDS.put(form, kind);
        PORTS.put(form, port);
    }

    /**
     * 返回绑定到该种类的端口，未注册（或 kind 为空）时返回 {@code null}。按 form 查表。
     * Returns the port bound to a kind, or {@code null} when none is registered (or
     * the kind is null). Lookup keyed by form.
     */
    public static ExportPortSpec port(ExportKindSpec kind) {
        if (kind == null) {
            return null;
        }
        return PORTS.get(kind.form());
    }

    /**
     * 全部已注册种类，按注册序返回副本（确定性）。All registered kinds as a copy in
     * registration order (deterministic).
     */
    public static List<ExportKindSpec> kinds() {
        return List.copyOf(KINDS.values());
    }

    /**
     * 全部已注册 form，按注册序返回副本（确定性）。All registered forms as a copy in
     * registration order (deterministic).
     */
    public static List<String> forms() {
        return List.copyOf(KINDS.keySet());
    }

    /** 清空注册表（供探针隔离）。Clears the registry (probe isolation). */
    public static void clear() {
        KINDS.clear();
        PORTS.clear();
    }
}
