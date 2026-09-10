package io.toterra.subterra.engine.export;

import io.toterra.subterra.engine.config.Td;
import io.toterra.subterra.engine.world.WorldPack;
import io.toterra.subterra.engine.zd.ZdDocWriter;
import io.toterra.subterra.engine.zd.ZdVolume;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * p.2.9.5 export hub (p.2.18 landing point) — the static registry that binds an
 * {@link ExportKind} to its {@link ExportPort} and, since p.2.18.1, to a functional
 * {@link ExportProducer}. {@link #preload()} preserves the p.2.9.5 contract and
 * deterministically registers the two built-in descriptive ports (world-pack and
 * save-export forms); {@link #preloadFormal()} registers the complete p.2.18 formal
 * surface (all seven kinds, in enum order). Registration is deterministic and
 * idempotent per map: a second registration of the same kind (port or producer) is
 * rejected with {@link IllegalArgumentException}. Deterministic, orderless; no
 * timestamps, no randomness.
 *
 * <p>p.2.9.5 导出中枢（p.2.18 落点）——把 {@link ExportKind} 绑定到其 {@link ExportPort} 的静态
 * 注册表；p.2.18.1 起还绑定功能性 {@link ExportProducer}。{@link #preload()} 保留 p.2.9.5 契约，
 * 确定性注册两个内建描述性端口（世界包与存档导出档案形态）；{@link #preloadFormal()} 注册完整的
 * p.2.18 正式导出表面（全部七种类，按枚举序）。注册确定、每映射幂等：同一种类二次注册（端口或
 * 生产者）以 {@link IllegalArgumentException} 拒绝。确定性、无时序；无时间戳、无随机。
 */
public final class ExportHub {

    private static final Map<ExportKind, ExportPort> PORTS = new EnumMap<>(ExportKind.class);
    private static final Map<ExportKind, ExportProducer> PRODUCERS = new EnumMap<>(ExportKind.class);

    private ExportHub() {
    }

    /**
     * Registers a descriptive port for a kind. A second port registration for the same
     * kind (or a null kind/port) is rejected with {@link IllegalArgumentException}.
     * Deterministic, no timing.
     *
     * 为某种类注册一个描述性端口。同一种类端口二次注册（或 kind/port 为空）以
     * {@link IllegalArgumentException} 拒绝。确定性、无时序。
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
     * Registers a functional {@link ExportProducer} for a kind. The producer's
     * {@code tdType()} must equal the kind's {@code form()}; a null kind/producer, a
     * tdType/form mismatch, or a second producer registration for the same kind is
     * rejected with {@link IllegalArgumentException}. If the kind has no descriptive
     * port yet, a default one is registered alongside (so a kind with a producer is
     * always visible via {@link #port(ExportKind)} and {@link #kinds()}).
     * Deterministic, no timing.
     *
     * 为某种类注册一个功能性 {@link ExportProducer}。生产者的 {@code tdType()} 必须等于该种类的
     * {@code form()}；kind/producer 为空、tdType/form 不匹配、或同一种类生产者二次注册均以
     * {@link IllegalArgumentException} 拒绝。若该种类尚无描述性端口，则一并注册一个默认端口
     * （因此带生产者的种类恒可通过 {@link #port(ExportKind)} 与 {@link #kinds()} 可见）。
     * 确定性、无时序。
     */
    public static void register(ExportKind kind, ExportProducer producer) {
        if (kind == null || producer == null) {
            throw new IllegalArgumentException("export kind and producer must be non-null");
        }
        if (!producer.tdType().equals(kind.form())) {
            throw new IllegalArgumentException("producer tdType '" + producer.tdType()
                    + "' does not match kind form '" + kind.form() + "'");
        }
        if (PRODUCERS.containsKey(kind)) {
            throw new IllegalArgumentException("export kind producer already registered: " + kind.form());
        }
        PRODUCERS.put(kind, producer);
        PORTS.putIfAbsent(kind, defaultPort(kind, producer));
    }

    /**
     * Returns the port bound to a kind, or {@code null} when none is registered.
     */
    public static ExportPort port(ExportKind kind) {
        return PORTS.get(kind);
    }

    /**
     * Returns the functional producer bound to a kind, or {@code null} when none is
     * registered (descriptive-only / placeholder kinds return {@code null}; the four
     * new content forms land their producers in p.2.18.x).
     *
     * 返回绑定到某种类的功能性生产者；未注册时返回 {@code null}（描述性/占位种类返回
     * {@code null}；四个新内容形态的生产者由 p.2.18.x 补齐）。
     */
    public static ExportProducer producer(ExportKind kind) {
        return PRODUCERS.get(kind);
    }

    /**
     * All registered kinds in {@link ExportKind} enum order (deterministic).
     */
    public static List<ExportKind> kinds() {
        return List.copyOf(PORTS.keySet()); // EnumMap keySet follows enum order
    }

    /**
     * All registered forms in registration order (= {@link ExportKind} enum order,
     * deterministic) — the engine-side counterpart of
     * {@code api.export.ExporterRegistry.forms()}, provided for cross-checking.
     *
     * 全部已注册 form，按注册序（= {@link ExportKind} 枚举序，确定性）返回副本——api
     * {@code api.export.ExporterRegistry.forms()} 的 engine 侧对应物，供对照。
     */
    public static List<String> forms() {
        List<ExportKind> ks = kinds();
        String[] forms = new String[ks.size()];
        for (int i = 0; i < ks.size(); i++) {
            forms[i] = ks.get(i).form();
        }
        return List.of(forms);
    }

    /**
     * Deterministically registers the built-in export ports. Idempotent: calling it
     * again once anything is registered is a no-op. p.2.9.5 contract preserved — only
     * the world-pack form ({@link WorldPack}) and the save-export archive form
     * ({@code SaveExportArchive}) are described here, so
     * {@code kinds() == [WORLD_PACK, SAVE]} stays stable for existing callers. The
     * functional producers for these two forms (over {@link WorldPack} /
     * {@code SaveExportArchive}) are registered by the p.2.18 command layer via
     * {@link #register(ExportKind, ExportProducer)} with concrete sources; the
     * complete seven-kind formal surface is registered by {@link #preloadFormal()}.
     *
     * 确定性注册内建导出端口。幂等：任何已注册后再调用即为空操作。保留 p.2.9.5 契约——此处只描述
     * 世界包形态（{@link WorldPack}）与存档导出档案形态（{@code SaveExportArchive}），因此
     * {@code kinds() == [WORLD_PACK, SAVE]} 对既有调用方保持稳定。这两个形态的功能性生产者
     * （基于 {@link WorldPack} / {@code SaveExportArchive}）由 p.2.18 指令层经
     * {@link #register(ExportKind, ExportProducer)} 携带具体源物注册；完整七种类正式表面由
     * {@link #preloadFormal()} 注册。
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

    /**
     * Registers the complete p.2.18 formal export surface: all seven kinds in
     * {@link ExportKind} enum order (world / save / datapack / language_keys / config /
     * registries / migrate_maps), idempotent per kind (already-registered kinds are
     * skipped, never re-registered, never throws). The datapack form can carry the
     * functional {@code DatapackArchiveProducer} (over
     * {@code DatapackExportArchive}, rehydrate-identical); the four new content forms
     * ({@code language_keys} / {@code config} / {@code registries} / {@code
     * migrate_maps}) keep descriptive placeholder ports here — their functional
     * producers land in p.2.18.x.
     *
     * 注册完整的 p.2.18 正式导出表面：全部七种类按 {@link ExportKind} 枚举序（world / save /
     * datapack / language_keys / config / registries / migrate_maps），每种类幂等（已注册的跳过、
     * 绝不重复注册、绝不抛）。数据包形态可承载功能性 {@code DatapackArchiveProducer}（基于
     * {@code DatapackExportArchive}，回水化恒等）；四个新内容形态（{@code language_keys} /
     * {@code config} / {@code registries} / {@code migrate_maps}）此处保留描述性占位端口——其
     * 功能性生产者由 p.2.18.x 补齐。
     */
    public static void preloadFormal() {
        for (ExportKind kind : ExportKind.values()) {
            if (!PORTS.containsKey(kind)) {
                register(kind, descriptivePort(kind));
            }
        }
    }

    /**
     * Converts a td document text to its zd v2 binary variant (same content; the
     * {@code type tie<data>} header line is handled by {@code Td.parse}). Deterministic.
     *
     * 把 td 文档文本转为同一内容的 zd v2 二进制变体（{@code type tie<data>} 头由
     * {@code Td.parse} 处理）。确定性。
     */
    public static byte[] zdOf(String td) {
        return ZdDocWriter.writeTree(0, Td.parse(td));
    }

    /**
     * Converts a zd v2 binary payload back to its canonical td document text (with the
     * standard {@code type tie<data>} header). Deterministic.
     *
     * 把 zd v2 二进制载荷转回其规范 td 文档文本（带标准 {@code type tie<data>} 头）。确定性。
     */
    public static String tdOf(byte[] zd) {
        return "type tie<data>\n" + Td.write(ZdVolume.readTree(zd));
    }

    // ---------- helpers ----------

    /** Default descriptive port auto-registered alongside a functional producer. */
    private static ExportPort defaultPort(ExportKind kind, ExportProducer producer) {
        return new ExportPort() {
            @Override
            public ExportKind kind() {
                return kind;
            }

            @Override
            public String description() {
                return "Functional export producer for the " + producer.tdType()
                        + " form: deterministic td/zd dual format with rehydrate identity. "
                        + "功能性导出生产者（td/zd 双格式、回水化恒等）。";
            }
        };
    }

    /** Canned two-sentence descriptive port for the p.2.18 formal-surface kinds. */
    private static ExportPort descriptivePort(ExportKind kind) {
        return new ExportPort() {
            @Override
            public ExportKind kind() {
                return kind;
            }

            @Override
            public String description() {
                return switch (kind) {
                    case WORLD_PACK -> "The world-pack artifact form bundles a whole save container and a datapack "
                            + "directory as base64 payloads in a single td document. "
                            + "Rehydrating it yields the SaveContainer, the datapack document and metadata.";
                    case SAVE -> "The save export archive is a content-level serialization of a SaveContainer "
                            + "into a single td document. "
                            + "Rehydrating it reproduces the six typed save slots, their overlays and global rules.";
                    case DATAPACK -> "The datapack export archive is a content-level serialization of a Datapack "
                            + "into a single td document. "
                            + "Rehydrating it reproduces the registered entry content (canonical per-kind payloads, id-sorted). "
                            + "Its functional producer (DatapackArchiveProducer) is attached by the p.2.18 command layer.";
                    case LANGUAGE_KEYS -> "The language-keys document enumerates the framework's stable language keys "
                            + "as a single td document. "
                            + "Its functional producer is a p.2.18.x sub-item (this port stays descriptive).";
                    case CONFIG -> "The config document serializes the framework's configuration surface as a single "
                            + "td document. "
                            + "Its functional producer is a p.2.18.x sub-item (this port stays descriptive).";
                    case REGISTRIES -> "The registries document serializes the framework's registries surface as a single "
                            + "td document. "
                            + "Its functional producer is a p.2.18.x sub-item (this port stays descriptive).";
                    case MIGRATE_MAPS -> "The migrate-maps document serializes the framework's migration maps as a single "
                            + "td document. "
                            + "Its functional producer is a p.2.18.x sub-item (this port stays descriptive).";
                };
            }
        };
    }
}
