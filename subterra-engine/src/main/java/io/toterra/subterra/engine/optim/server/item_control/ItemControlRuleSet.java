// Ported from itemban (github.com/lnsanes/itemban), Apache-2.0 (c) lnsanes;
// adapted to Subterra's NeoForge 1.21.1 internal capability.
package io.toterra.subterra.engine.optim.server.item_control;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Pure-JDK ordered, deduplicated collection of {@link ItemControlRule}s
 * (item- and block-blacklist rule lists).
 *
 * <p>Guards the "no O(n²)" discipline for the blacklist: rules are
 * pre-parsed and collected once (on config load and on each command-driven
 * reindex), then {@link #contains} runs a single linear pass per item/block
 * — never a full-table regex rescan inside an event. Duplicate rules (same id
 * and same NBT-constraint string) are collapsed on build so redundant config
 * entries never cause redundant work.
 */
public final class ItemControlRuleSet {

    private static final ItemControlRuleSet EMPTY = new ItemControlRuleSet(List.of());

    private final List<ItemControlRule> rules;

    private ItemControlRuleSet(List<ItemControlRule> rules) {
        this.rules = rules;
    }

    /** Empty rule set: matches nothing. */
    public static ItemControlRuleSet empty() {
        return EMPTY;
    }

    /**
     * Builds an immutable set from the given rules, keeping first-occurrence
     * order and dropping nulls and exact duplicates (same id + NBT string).
     */
    public static ItemControlRuleSet of(List<ItemControlRule> rules) {
        if (rules == null || rules.isEmpty()) {
            return EMPTY;
        }
        Set<String> seen = new HashSet<>(rules.size(), 1.0f);
        List<ItemControlRule> unique = new ArrayList<>(rules.size());
        for (ItemControlRule rule : rules) {
            if (rule == null) {
                continue;
            }
            String nbt = rule.nbtSnbt() == null ? "" : rule.nbtSnbt();
            String signature = rule.id() + '\u0000' + nbt;
            if (seen.add(signature)) {
                unique.add(rule);
            }
        }
        return unique.isEmpty() ? EMPTY : new ItemControlRuleSet(List.copyOf(unique));
    }

    public boolean isEmpty() {
        return rules.isEmpty();
    }

    public int size() {
        return rules.size();
    }

    /** Immutable ordered view of the underlying rules. */
    public List<ItemControlRule> rules() {
        return rules;
    }

    /**
     * True when any rule matches the given id + NBT. The actual NBT match is
     * dispatched by each rule's {@link ItemControlRule.NbtMatcher}.
     */
    public boolean contains(String id, Object nbt) {
        for (ItemControlRule rule : rules) {
            if (rule.matches(id, nbt)) {
                return true;
            }
        }
        return false;
    }
}