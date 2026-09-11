package io.toterra.subterra.engine.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Mod-list view data core (p.2.22.3): an immutable view over a mod collection,
 * sorted deterministically by {@code id} (lexicographic), with deterministic
 * pagination ({@link #page(int, int)}, out-of-range clamped) and fixed-order
 * view rows ({@link #rows()}). The mods-list interaction model follows ModMenu's
 * mods-list screen idea (github.com/TerraformersMC/ModMenu, MIT): a scrollable,
 * paged list of mod entries, each entry carrying its metadata. This is a borrow
 * of the interaction-model and metadata-API pattern ONLY — zero upstream code is
 * copied; the implementation is original deterministic Subterra code, fixed-order,
 * and the NeoForge wiring is self-developed and deferred to the runtime layer.
 * This view core only produces deterministic view data — no rendering; the panels
 * are admin/management views only, and player-side interaction goes through
 * p.2.14 (engine.interact, zero-HUD rule). Pure JDK; identical inputs yield
 * identical bits, with no randomness and no timing.
 *
 * <p>Ordering: mods are sorted by {@code id} lexicographically
 * ({@link String#compareTo}, locale-independent); the sort is total, so equal
 * {@code id}s (a caller-side duplicate) still yield a fixed, deterministic order.
 * The sorted list is immutable.
 *
 * <p>{@link #page(int, int)}: {@code pageSize} must be positive; {@code pageIndex}
 * is clamped — negative indexes clamp to {@code 0}, indexes past the last page
 * clamp to the last page, and the empty list yields an empty page. The returned
 * page is an immutable slice of the sorted list (fixed order).
 *
 * <p>{@link #rows()}: one view row per mod, in the sorted order — the fixed
 * template {@code "[id] name - version (license)"}. Deterministic: the same
 * metadata always yields the same bytes.
 *
 * <p>模组列表视图数据核心（p.2.22.3）：模组集合上的不可变视图，按 {@code id} 确定性排序
 * （字典序），提供确定性分页（{@link #page(int, int)}，越界 clamp）与固定序视图行
 * （{@link #rows()}）。模组列表交互模型遵循 ModMenu 模组列表屏思想
 * （github.com/TerraformersMC/ModMenu，MIT）：可滚动、分页的模组条目列表，每条目承载其
 * 元数据。此处仅为交互模型与元数据 API 模式的借鉴——零上游代码复制；实现为 Subterra 原创
 * 确定性代码，固定序，NeoForge 接线自研，留待 runtime 层。本视图核心只产出确定性视图
 * 数据——不渲染；面板仅作管理视图，玩家侧交互走 p.2.14（engine.interact，零 HUD 规则）。
 * 纯 JDK；同输入恒得同字节，无随机无时序。
 *
 * <p>排序：模组按 {@code id} 字典序排序（{@link String#compareTo}，与语言环境无关）；
 * 排序全序，故相等 {@code id}（调用方侧重复）仍得固定、确定的次序。排序后的列表不可变。
 *
 * <p>{@link #page(int, int)}：{@code pageSize} 必须为正；{@code pageIndex} 越界
 * clamp——负数 clamp 到 {@code 0}，超出末页 clamp 到末页，空列表恒得空页。返回页为排序
 * 列表的不可变切片（固定序）。
 *
 * <p>{@link #rows()}：每个模组一行视图行，按排序序——固定模板
 * {@code "[id] name - version (license)"}。确定性：同元数据恒得同字节。
 */
public final class ModListViewCore {

    private final List<ModMetadata> sorted;

    /**
     * Builds an immutable view over the given mods, sorted deterministically by
     * {@code id} (see class javadoc). 基于给定模组构建不可变视图，按 {@code id} 确定性
     * 排序（见类注释）。
     *
     * @param mods the mods to view, non-null elements. 待视图化模组，元素非空。
     */
    public ModListViewCore(List<ModMetadata> mods) {
        Objects.requireNonNull(mods, "mods must be non-null");
        List<ModMetadata> copy = new ArrayList<>(mods);
        for (ModMetadata mod : copy) {
            Objects.requireNonNull(mod, "mod must be non-null");
        }
        copy.sort((a, b) -> a.id().compareTo(b.id()));
        this.sorted = List.copyOf(copy);
    }

    /**
     * The full sorted mod list (immutable view, fixed order). 完整排序模组列表（不可变
     * 视图，固定序）。
     *
     * @return the mods in id-lexicographic order. 按 id 字典序排列的模组。
     */
    public List<ModMetadata> mods() {
        return sorted;
    }

    /**
     * Number of mods in the view. 视图中模组数。
     */
    public int size() {
        return sorted.size();
    }

    /**
     * The deterministic page at {@code pageIndex} for the given {@code pageSize}
     * (fixed order, out-of-range clamp — see class javadoc). 给定 {@code pageSize}
     * 下 {@code pageIndex} 页的确定性切片（固定序，越界 clamp——见类注释）。
     *
     * @param pageSize  the page size; must be positive. 页大小；必须为正。
     * @param pageIndex the zero-based page index; clamped on overflow/underflow.
     *                  零基页下标；越界时 clamp。
     * @return the immutable page slice, fixed order. 不可变页切片，固定序。
     */
    public List<ModMetadata> page(int pageSize, int pageIndex) {
        if (pageSize <= 0) {
            throw new IllegalArgumentException("pageSize must be positive: " + pageSize);
        }
        int size = sorted.size();
        if (size == 0) {
            return List.of();
        }
        int pages = (size + pageSize - 1) / pageSize;
        int idx = Math.max(0, Math.min(pageIndex, pages - 1));
        int from = idx * pageSize;
        int to = Math.min(from + pageSize, size);
        return List.copyOf(sorted.subList(from, to));
    }

    /**
     * The fixed-order view rows — one row per mod in the sorted order; template
     * {@code "[id] name - version (license)"} (see class javadoc). Immutable.
     * 固定序视图行——每个模组一行，按排序序；模板 {@code "[id] name - version (license)"}
     * （见类注释）。不可变。
     *
     * @return the view rows in the sorted order. 按排序序的视图行。
     */
    public List<String> rows() {
        List<String> out = new ArrayList<>(sorted.size());
        for (ModMetadata mod : sorted) {
            out.add("[" + mod.id() + "] " + mod.name() + " - " + mod.version() + " (" + mod.license() + ")");
        }
        return List.copyOf(out);
    }
}
