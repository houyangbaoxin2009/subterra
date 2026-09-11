package io.toterra.subterra.engine.ui;

import io.toterra.subterra.engine.config.Td;
import io.toterra.subterra.engine.config.TdTable;
import io.toterra.subterra.engine.config.TdValue;

import java.util.Objects;

/**
 * Mod metadata (p.2.22.3): the canonical five-field metadata record of a mod —
 * {@code id}, {@code name}, {@code description}, {@code version}, {@code license}
 * (fixed component order). The field set and the "mods-list metadata entry" idea
 * follow ModMenu's metadata API pattern (github.com/TerraformersMC/ModMenu, MIT):
 * a mod entry exposes id / name / description / version / license for the mods
 * list and detail views. This is a borrow of the interaction-model and
 * metadata-API pattern ONLY — zero upstream code is copied; the implementation
 * is original deterministic Subterra code, td-ized (tie data, engine
 * {@code config.Td}) and fixed-order; the NeoForge wiring is self-developed and
 * deferred to the runtime layer. The panels are admin/management views only —
 * player-side interaction goes through p.2.14 (engine.interact, zero-HUD rule).
 * Pure JDK; identical inputs yield identical bits, with no randomness and no
 * timing.
 *
 * <p>Document shape (td, fixed field order, no timestamps): the root table
 * carries a single named entry {@code mod = [...]} —
 * <pre>{@code
 * [
 *   mod = [
 *     id = "subterra:core",
 *     name = "Subterra Core",
 *     description = "Deterministic engine core.",
 *     version = "1.0.0",
 *     license = "MIT",
 *   ],
 * ]
 * }</pre>
 *
 * <p>{@link #fromTd(TdTable)} parses deterministically: a missing or non-table
 * {@code mod} raises {@link IllegalArgumentException}; the {@code id} must be
 * present and non-blank (else {@link IllegalArgumentException}); a missing
 * {@code name}/{@code description}/{@code version}/{@code license} defaults to
 * {@code ""}. Non-scalar field values are opaque on the string contract and read
 * as missing.
 *
 * <p>{@link #toTd(ModMetadata)} writes the canonical td text: a root {@code mod =
 * [...]} with {@code id}, {@code name}, {@code description}, {@code version},
 * then {@code license} (fixed field order). The output parses with
 * {@link Td#parse} and round-trips byte-identically through {@link #fromTd}:
 * same input, same bytes, run any number of times.
 *
 * <p>模组元数据（p.2.22.3）：模组的规范五字段元数据 record——{@code id}、{@code name}、
 * {@code description}、{@code version}、{@code license}（固定组件序）。字段集与"模组列表
 * 元数据条目"思想遵循 ModMenu 的元数据 API 模式（github.com/TerraformersMC/ModMenu，
 * MIT）：模组条目以 id / name / description / version / license 支撑模组列表与详情视图。
 * 此处仅为交互模型与元数据 API 模式的借鉴——零上游代码复制；实现为 Subterra 原创确定性
 * 代码，td 化（tie data，engine {@code config.Td}）且固定序；NeoForge 接线自研，留待
 * runtime 层。面板仅作管理视图——玩家侧交互走 p.2.14（engine.interact，零 HUD 规则）。
 * 纯 JDK；同输入恒得同字节，无随机无时序。
 *
 * <p>文档形态（td，固定字段序，禁时间戳）：根表含唯一命名条目 {@code mod = [...]}——
 * <pre>{@code
 * [
 *   mod = [
 *     id = "subterra:core",
 *     name = "Subterra Core",
 *     description = "Deterministic engine core.",
 *     version = "1.0.0",
 *     license = "MIT",
 *   ],
 * ]
 * }</pre>
 *
 * <p>{@link #fromTd(TdTable)} 确定性解析：{@code mod} 缺失或非表抛
 * {@link IllegalArgumentException}；{@code id} 必须存在且非空白（否则
 * {@link IllegalArgumentException}）；{@code name}/{@code description}/
 * {@code version}/{@code license} 缺失默认 {@code ""}。非标量字段值在字符串契约面上
 * 不透明，按缺失处理。
 *
 * <p>{@link #toTd(ModMetadata)} 写出规范 td 文本：根表 {@code mod = [...]}，字段序固定为
 * {@code id}、{@code name}、{@code description}、{@code version}、{@code license}。
 * 输出可用 {@link Td#parse} 解析，经 {@link #fromTd} 逐字节往返恒等：同输入、同字节、
 * 跑任意次。
 *
 * @param id          the unique mod id (e.g. {@code "subterra:core"}); non-blank.
 *                    唯一模组 id（如 {@code "subterra:core"}）；非空白。
 * @param name        the display name; non-null. 显示名；非空。
 * @param description the one-line description; non-null. 单行描述；非空。
 * @param version     the mod version string; non-null. 模组版本串；非空。
 * @param license     the license name; non-null. 许可名；非空。
 */
public record ModMetadata(String id, String name, String description, String version, String license) {

    public ModMetadata {
        Objects.requireNonNull(id, "id must be non-null");
        Objects.requireNonNull(name, "name must be non-null");
        Objects.requireNonNull(description, "description must be non-null");
        Objects.requireNonNull(version, "version must be non-null");
        Objects.requireNonNull(license, "license must be non-null");
        if (id.isBlank()) {
            throw new IllegalArgumentException("id must be non-blank: " + id);
        }
    }

    /**
     * The fixed license declaration line of this mod — the fixed template
     * {@code "This mod is licensed under " + license + "."}. Deterministic: the
     * same {@code license} always yields the same bytes.
     * 本模组的固定许可声明行——固定模板
     * {@code "This mod is licensed under " + license + "."}。确定性：同一
     * {@code license} 恒得同字节。
     *
     * @return the license declaration line. 许可声明行。
     */
    public String licenseNotice() {
        return "This mod is licensed under " + license + ".";
    }

    /**
     * Parses the {@code mod} document into a {@link ModMetadata} (see class
     * javadoc for validation and defaults). 将 {@code mod} 文档解析为
     * {@link ModMetadata}（校验与默认值见类注释）。
     *
     * @param doc the mod document root table. 模组文档根表。
     * @return the deterministic mod metadata. 确定性的模组元数据。
     */
    public static ModMetadata fromTd(TdTable doc) {
        if (doc == null) {
            throw new IllegalArgumentException("doc must be non-null");
        }
        if (!(doc.get("mod") instanceof TdTable mod)) {
            throw new IllegalArgumentException("missing mod table");
        }
        String id = scalarString(mod.get("id"));
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("mod id must be present and non-blank");
        }
        String name = scalarString(mod.get("name"));
        String description = scalarString(mod.get("description"));
        String version = scalarString(mod.get("version"));
        String license = scalarString(mod.get("license"));
        return new ModMetadata(id,
                name != null ? name : "",
                description != null ? description : "",
                version != null ? version : "",
                license != null ? license : "");
    }

    /**
     * Writes the canonical td text of the given mod metadata (see class javadoc:
     * fixed field order, no timestamps). 将给定模组元数据写出规范 td 文本（见类注释：
     * 固定字段序、禁时间戳）。
     *
     * @param metadata the mod metadata, non-null. 模组元数据，非空。
     * @return the canonical td text. 规范 td 文本。
     */
    public static String toTd(ModMetadata metadata) {
        if (metadata == null) {
            throw new IllegalArgumentException("metadata must be non-null");
        }
        TdTable root = TdTable.builder()
                .put("mod", TdTable.builder()
                        .put("id", metadata.id())
                        .put("name", metadata.name())
                        .put("description", metadata.description())
                        .put("version", metadata.version())
                        .put("license", metadata.license())
                        .build())
                .build();
        return Td.write(root);
    }

    /**
     * Canonical scalar rendering of a td value (same contract as the rules
     * document layer); {@code null} for missing or table values.
     * td 标量的规范字符串渲染（与规则文档层同契约）；缺失或表值返回 {@code null}。
     */
    private static String scalarString(TdValue v) {
        return v == null || v instanceof TdTable ? null : v.toString();
    }
}
