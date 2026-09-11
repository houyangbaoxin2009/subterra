package io.toterra.subterra.engine.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * License view data core (p.2.22.3): the fixed-order license lines of a single
 * mod ({@link #licenseLines(ModMetadata)}) and the deterministic license summary
 * of a mod collection ({@link #licenseSummary(List)}, aggregated by license name
 * in lexicographic order). The license surface follows ModMenu's mods-list
 * license display idea (github.com/TerraformersMC/ModMenu, MIT) — each mod entry
 * carries a license name shown alongside its metadata. This is a borrow of the
 * interaction-model and metadata-API pattern ONLY — zero upstream code is copied;
 * the implementation is original deterministic Subterra code, and the NeoForge
 * wiring is self-developed and deferred to the runtime layer. This view core
 * only produces deterministic view data — no rendering; the panels are
 * admin/management views only, and player-side interaction goes through p.2.14
 * (engine.interact, zero-HUD rule). Pure JDK; identical inputs yield identical
 * bits, with no randomness and no timing.
 *
 * <p>{@link #licenseLines(ModMetadata)} returns exactly two lines in this fixed
 * order: the license-name line {@code "license: " + license}, then the license
 * declaration line {@link ModMetadata#licenseNotice()}.
 *
 * <p>{@link #licenseSummary(List)} aggregates the mods by license name: the
 * returned rows are in lexicographic license-name order (locale-independent),
 * each row on the fixed template {@code "license: " + license + " (" + count +
 * " mod(s))"}. The empty list yields the empty row list; the aggregation is
 * O(n log n) (a {@link TreeMap}) — deterministic regardless of the input order.
 * An empty-string license name is preserved and sorted like any other name.
 *
 * <p>许可视图数据核心（p.2.22.3）：单个模组的固定序许可行
 * （{@link #licenseLines(ModMetadata)}）与模组集合的确定性许可汇总
 * （{@link #licenseSummary(List)}，按许可名聚合、字典序）。许可展示遵循 ModMenu 模组列表
 * 许可显示思想（github.com/TerraformersMC/ModMenu，MIT）——每个模组条目承载随元数据一同
 * 展示的许可名。此处仅为交互模型与元数据 API 模式的借鉴——零上游代码复制；实现为 Subterra
 * 原创确定性代码，NeoForge 接线自研，留待 runtime 层。本视图核心只产出确定性视图数据——
 * 不渲染；面板仅作管理视图，玩家侧交互走 p.2.14（engine.interact，零 HUD 规则）。纯 JDK；
 * 同输入恒得同字节，无随机无时序。
 *
 * <p>{@link #licenseLines(ModMetadata)} 返回恰好两行，固定序：许可名行
 * {@code "license: " + license}，随后是许可声明行 {@link ModMetadata#licenseNotice()}。
 *
 * <p>{@link #licenseSummary(List)} 按许可名聚合模组：返回行按许可名字典序（与语言环境
 * 无关），每行为固定模板 {@code "license: " + license + " (" + count + " mod(s))"}。
 * 空列表 → 空行列表；聚合为 O(n log n)（{@link TreeMap}）——与输入顺序无关地确定。空串
 * 许可名原样保留并按普通名字参与排序。
 */
public final class LicenseViewCore {

    private LicenseViewCore() {
    }

    /**
     * The fixed-order license lines of the given mod — the license-name line then
     * the declaration line (see class javadoc). Immutable.
     * 给定模组的固定序许可行——许可名行后接声明行（见类注释）。不可变。
     *
     * @param metadata the mod metadata, non-null. 模组元数据，非空。
     * @return the two license lines in fixed order. 固定序的两条许可行。
     */
    public static List<String> licenseLines(ModMetadata metadata) {
        Objects.requireNonNull(metadata, "metadata must be non-null");
        return List.of("license: " + metadata.license(), metadata.licenseNotice());
    }

    /**
     * The deterministic license summary of the given mods — one row per license
     * name in lexicographic order (see class javadoc). Immutable.
     * 给定模组的确定性许可汇总——每个许可名一行，字典序（见类注释）。不可变。
     *
     * @param mods the mods to summarize, non-null elements. 待汇总模组，元素非空。
     * @return the summary rows in lexicographic license-name order. 按许可名字典序的汇总行。
     */
    public static List<String> licenseSummary(List<ModMetadata> mods) {
        Objects.requireNonNull(mods, "mods must be non-null");
        Map<String, Integer> counts = new TreeMap<>();
        for (ModMetadata mod : mods) {
            Objects.requireNonNull(mod, "mod must be non-null");
            counts.merge(mod.license(), 1, Integer::sum);
        }
        List<String> out = new ArrayList<>(counts.size());
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            out.add("license: " + entry.getKey() + " (" + entry.getValue() + " mod(s))");
        }
        return List.copyOf(out);
    }
}
