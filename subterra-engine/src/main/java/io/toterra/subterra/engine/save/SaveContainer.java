package io.toterra.subterra.engine.save;

import io.toterra.subterra.engine.config.TdTable;
import io.toterra.subterra.engine.config.TdValue;
import io.toterra.subterra.engine.datapack.DatapackRules;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 存档统一持有者骨架（p.2.3.1）：按类型化 {@link SaveSlot} 挂载每个槽的 td 文档，并为规则槽
 * 提供「文档内 rules 表 + 槽级 overlay」叠加（复用 {@link DatapackRules} 语义：
 * doc 自带 rules → overlay 覆盖胜出）。叠加顺序 = {@code LinkedHashMap putAll} 语义。
 * 双层配置（全局 + 存档覆盖）在本子项只做语义占位（{@link #globalRules()} /
 * {@link #readGlobalRules(TdTable)} 雏形），p.2.3.5 扩展为正式两级。
 * <p>
 * Unified save-container skeleton (p.2.3.1): mounts each slot's td document under a
 * typed {@link SaveSlot}, and for rule slots stacks the document-embedded {@code rules}
 * table with a slot-level overlay, reusing {@link DatapackRules} semantics (doc rules →
 * overlay wins). Overlay order = {@code LinkedHashMap putAll} semantics. Double-layer
 * config (global + save override) is only a semantic placeholder here
 * ({@link #globalRules()} / {@link #readGlobalRules(TdTable)} stubs); p.2.3.5 grows it
 * into the formal two levels.
 */
public final class SaveContainer {

    private final Map<SaveSlot, TdTable> documents = new EnumMap<>(SaveSlot.class);
    private final Map<SaveSlot, Map<String, TdValue>> ruleOverlays = new EnumMap<>(SaveSlot.class);
    private Map<String, TdValue> globalRules = Map.of();

    /**
     * 挂载/覆盖某槽的文档（槽位唯一，可覆盖）。链式返回 {@code this}。Mounts/replaces the
     * document for a slot (one document per slot, replacing the previous). Chainable.
     */
    public SaveContainer attach(SaveSlot slot, TdTable document) {
        documents.put(slot, document);
        return this;
    }

    /**
     * 取某槽的文档；未挂载 → {@code null}。注意不用 Optional，保持简单。Returns the document
     * for a slot, or {@code null} when none is mounted. Plain-null by design.
     */
    public TdTable document(SaveSlot slot) {
        return documents.get(slot);
    }

    /**
     * 为某槽叠加规则覆盖（overlay，胜出）。链式返回 {@code this}。Stacks a rule overlay for a
     * slot (wins over the document-embedded rules). Chainable.
     */
    public SaveContainer overlay(SaveSlot slot, Map<String, TdValue> rules) {
        ruleOverlays.put(slot, new LinkedHashMap<>(rules));
        return this;
    }

    /**
     * 读取某槽文档内嵌的 rules 表（形状仿 {@code DatapackRules.fromManifest}：
     * {@code rules = [ [ k = ..., v = ... ], ... ]}）；无 rules / 形状不符 → 空 map。
     * Reads the rules table embedded in a slot's document (shape mirrors
     * {@code DatapackRules.fromManifest}: {@code rules = [ [ k = ..., v = ... ], ... ]});
     * absent or malformed → empty map.
     */
    public Map<String, TdValue> rules(SaveSlot slot) {
        TdTable doc = documents.get(slot);
        return doc == null ? Map.of() : DatapackRules.fromManifest(doc);
    }

    /**
     * 某槽的有效规则：文档内嵌 rules → 槽级 overlay 覆盖胜出，确定性、保序 map。Effective
     * rules for a slot: document-embedded rules, then the slot overlay wins on conflict;
     * deterministic, insertion-ordered.
     */
    public Map<String, TdValue> effectiveRules(SaveSlot slot) {
        Map<String, TdValue> out = new LinkedHashMap<>();
        out.putAll(rules(slot));
        Map<String, TdValue> overlay = ruleOverlays.get(slot);
        if (overlay != null) {
            out.putAll(overlay);
        }
        return out;
    }

    /**
     * 某槽规则集的确定性 marker 文本（经 {@link DatapackRules#render}：key 排序、
     * {@code k=v; } 拼接；空 → {@code ""}）。Deterministic marker text for a slot's rules
     * (via {@link DatapackRules#render}: keys sorted, {@code k=v; } joined; empty → {@code ""}).
     */
    public String renderRules(SaveSlot slot) {
        return DatapackRules.render(effectiveRules(slot));
    }

    /**
     * 全部槽位，按 {@link SaveSlot} 枚举序（确定性）。All mounted slots in
     * {@link SaveSlot} enum order (deterministic).
     */
    public List<SaveSlot> slots() {
        List<SaveSlot> present = new ArrayList<>();
        for (SaveSlot slot : SaveSlot.values()) {
            if (documents.containsKey(slot)) {
                present.add(slot);
            }
        }
        return present;
    }

    // ---- p.2.3.5 双层配置占位 / double-layer config placeholder (p.2.3.5) ----

    /**
     * 读取全局规则文档（形状同 {@code DatapackRules.fromManifest}）作为全局层，覆盖当前
     * {@link #globalRules()}。链式返回 {@code this}。Reads a global-rules document
     * (shape as in {@code DatapackRules.fromManifest}) as the global layer, replacing
     * {@link #globalRules()}. Chainable.
     */
    public SaveContainer readGlobalRules(TdTable source) {
        this.globalRules = DatapackRules.fromManifest(source);
        return this;
    }

    /**
     * 全局层规则（只读视图）；默认空。p.2.3.5 将其与存档覆盖组成正式两级。The global-layer
     * rules (live, read-only view); empty by default. p.2.3.5 composes it with the save
     * override into the formal two levels.
     */
    public Map<String, TdValue> globalRules() {
        return globalRules;
    }
}