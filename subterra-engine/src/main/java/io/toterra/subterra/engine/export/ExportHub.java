package io.toterra.subterra.engine.export;

import io.toterra.subterra.engine.world.WorldPack;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * p.2.9.5 export hub (p.2.18 landing point) — the static registry that binds an
 * {@link ExportKind} to its {@link ExportPort}. {@link #preload()} deterministically
 * registers the built-in ports (world-pack and save-export forms). Registration is
 * deterministic and idempotent per kind: a second registration of the same kind is
 * rejected with {@link IllegalArgumentException}. Deterministic, orderless; no
 * timestamps, no randomness.
 *
 * <p>p.2.9.5 导出中枢（p.2.18 落点）——把 {@link ExportKind} 绑定到其 {@link ExportPort} 的静态
 * 注册表。{@link #preload()} 确定性注册内建端口（世界包与存档导出档案形态）。注册确定、
 * 每种类幂等：同一种类二次注册以 {@link IllegalArgumentException} 拒绝。确定性、无时序；无时间戳、
 * 无随机。
 */
public final class ExportHub {

    private static final Map<ExportKind, ExportPort> PORTS = new EnumMap<>(ExportKind.class);

    private ExportHub() {
    }

    /**
     * Registers a port for a kind. A second registration for the same kind (or a
     * null kind/port) is rejected with {@link IllegalArgumentException}. Deterministic,
     * no timing.
     *
     * 为某种类注册一个端口。同一种类二次注册（或 kind/port 为空）以 {@link IllegalArgumentException}
     * 拒绝。确定性、无时序。
     */
    public static void register(ExportKind kind, ExportPort port) {
        if (kind == null || port == null) {
            throw new IllegalArgumentException("export kind and port must be non-null");
        }
        if (PORTS.containsKey(kind)) {
            throw new IllegalArgumentException("export kind already registered: " + kind.form());
        }
        PORTS.put(kind, port);
    }

    /**
     * Returns the port bound to a kind, or {@code null} when none is registered.
     */
    public static ExportPort port(ExportKind kind) {
        return PORTS.get(kind);
    }

    /**
     * All registered kinds in {@link ExportKind} enum order (deterministic).
     */
    public static List<ExportKind> kinds() {
        return List.copyOf(PORTS.keySet()); // EnumMap keySet follows enum order
    }

    /**
     * Deterministically registers the built-in export ports. Idempotent: calling it
     * again once anything is registered is a no-op. Here the world-pack form
     * ({@link WorldPack}) and the save-export archive form ({@code SaveExportArchive})
     * are described (the datapack form awaits its port in p.2.18).
     *
     * 确定性注册内建导出端口。幂等：任何已注册后再调用即为空操作。此处描述了世界包形态
     * （{@link WorldPack}）与存档导出档案形态（{@code SaveExportArchive}）（数据包形态的端口
     * 留待 p.2.18）。
     */
    public static void preload() {
        if (!PORTS.isEmpty()) {
            return;
        }
        register(ExportKind.WORLD_PACK, new ExportPort() {
            @Override
            public ExportKind kind() {
                return ExportKind.WORLD_PACK;
            }

            @Override
            public String description() {
                return "The world-pack artifact form bundles a whole save container and a datapack "
                        + "directory as base64 payloads in a single td document. "
                        + "Rehydrating it yields the SaveContainer, the datapack document and metadata.";
            }
        });
        register(ExportKind.SAVE, new ExportPort() {
            @Override
            public ExportKind kind() {
                return ExportKind.SAVE;
            }

            @Override
            public String description() {
                return "The save export archive is a content-level serialization of a SaveContainer "
                        + "into a single td document. "
                        + "Rehydrating it reproduces the six typed save slots, their overlays and global rules.";
            }
        });
    }
}