package io.toterra.subterra.engine.ui;

import java.util.List;
import java.util.Objects;

/**
 * Mod detail view data core (p.2.22.3): the deterministic detail rows of a
 * single {@link ModMetadata}, in the fixed field order
 * {@code id -> name -> version -> license -> description} (one labeled row per
 * field). The mod-detail screen idea follows ModMenu's metadata API pattern
 * (github.com/TerraformersMC/ModMenu, MIT) — a mod detail view exposes the same
 * metadata fields as the list entries. This is a borrow of the
 * interaction-model and metadata-API pattern ONLY — zero upstream code is copied;
 * the implementation is original deterministic Subterra code, and the NeoForge
 * wiring is self-developed and deferred to the runtime layer. This view core
 * only produces deterministic view data — no rendering; the panels are
 * admin/management views only, and player-side interaction goes through p.2.14
 * (engine.interact, zero-HUD rule). Pure JDK; identical inputs yield identical
 * bits, with no randomness and no timing.
 *
 * <p>{@link #detailRows(ModMetadata)} returns exactly five rows, in this fixed
 * order, each on the fixed template {@code "field: value"}:
 * <ol>
 *   <li>{@code "id: " + id}</li>
 *   <li>{@code "name: " + name}</li>
 *   <li>{@code "version: " + version}</li>
 *   <li>{@code "license: " + license}</li>
 *   <li>{@code "description: " + description}</li>
 * </ol>
 * Deterministic: the same metadata always yields the same bytes.
 *
 * <p>模组详情视图数据核心（p.2.22.3）：单个 {@link ModMetadata} 的确定性详情行，固定字段序
 * {@code id -> name -> version -> license -> description}（每字段一行带标签行）。模组详情屏
 * 思想遵循 ModMenu 的元数据 API 模式（github.com/TerraformersMC/ModMenu，MIT）——详情视图与
 * 列表条目暴露相同的元数据字段。此处仅为交互模型与元数据 API 模式的借鉴——零上游代码复制；
 * 实现为 Subterra 原创确定性代码，NeoForge 接线自研，留待 runtime 层。本视图核心只产出
 * 确定性视图数据——不渲染；面板仅作管理视图，玩家侧交互走 p.2.14（engine.interact，零 HUD
 * 规则）。纯 JDK；同输入恒得同字节，无随机无时序。
 *
 * <p>{@link #detailRows(ModMetadata)} 返回恰好五行，按此固定序，各行为固定模板
 * {@code "field: value"}：
 * <ol>
 *   <li>{@code "id: " + id}</li>
 *   <li>{@code "name: " + name}</li>
 *   <li>{@code "version: " + version}</li>
 *   <li>{@code "license: " + license}</li>
 *   <li>{@code "description: " + description}</li>
 * </ol>
 * 确定性：同元数据恒得同字节。
 */
public final class ModDetailViewCore {

    private ModDetailViewCore() {
    }

    /**
     * The deterministic detail rows of the given mod — exactly five labeled rows
     * in the fixed field order {@code id -> name -> version -> license ->
     * description} (see class javadoc). Immutable. 给定模组的确定性详情行——恰好五行带标签
     * 行，固定字段序 {@code id -> name -> version -> license -> description}（见类注释）。
     * 不可变。
     *
     * @param metadata the mod metadata, non-null. 模组元数据，非空。
     * @return the five detail rows in fixed order. 固定序的五条详情行。
     */
    public static List<String> detailRows(ModMetadata metadata) {
        Objects.requireNonNull(metadata, "metadata must be non-null");
        return List.of(
                "id: " + metadata.id(),
                "name: " + metadata.name(),
                "version: " + metadata.version(),
                "license: " + metadata.license(),
                "description: " + metadata.description());
    }
}
