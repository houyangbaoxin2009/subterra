package io.toterra.subterra.engine.ui;

import java.util.List;
import java.util.Objects;

/**
 * A single data-driven tooltip rule (p.2.22.2): maps an item id to its fixed,
 * ordered tooltip line list. The shape is inspired by DataTip's
 * "data-driven custom item tooltip" concept (github.com/cooobird/DataTip,
 * GPL-3.0) — this is a clean-room reference of that idea only: zero upstream
 * code or assets are included, and the data form is td-ized (tie data, engine
 * {@code config.Td}) instead of upstream's JSON resource packs. As a canonical
 * record, {@code equals}/{@code hashCode} compare the item id and the line list
 * element-wise; {@code lines} is defensively copied, so the iteration order is
 * fixed and immutable — identical inputs yield identical bits, with no
 * randomness and no timing.
 *
 * <p>单条数据驱动 tooltip 规则（p.2.22.2）：将物品 id 映射为其固定有序的 tooltip 行列表。
 * 形态受 DataTip 的"数据驱动自定义物品 tooltip"概念启发
 * （github.com/cooobird/DataTip，GPL-3.0）——此处仅为该思想的 clean-room 参考：
 * 不含任何上游代码或资产，数据形态 td 化（tie data，engine {@code config.Td}），而非上游的
 * JSON 资源包。作为规范 record，{@code equals}/{@code hashCode} 逐项比较物品 id 与行列表；
 * {@code lines} 防御性拷贝，迭代序固定且不可变——同输入恒得同字节，无随机无时序。
 *
 * @param itemId the item id (e.g. {@code "minecraft:apple"}); non-blank.
 *               物品 id（如 {@code "minecraft:apple"}）；非空白。
 * @param lines  the fixed-order tooltip lines; non-null elements, immutable copy.
 *               固定序 tooltip 行；元素非空，不可变拷贝。
 */
public record TooltipRule(String itemId, List<String> lines) {

    public TooltipRule {
        Objects.requireNonNull(itemId, "itemId must be non-null");
        Objects.requireNonNull(lines, "lines must be non-null");
        if (itemId.isBlank()) {
            throw new IllegalArgumentException("itemId must be non-blank: " + itemId);
        }
        for (String line : lines) {
            Objects.requireNonNull(line, "line must be non-null: " + itemId);
        }
        lines = List.copyOf(lines);
    }
}
