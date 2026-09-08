package io.toterra.subterra.probes;

import io.toterra.subterra.engine.optim.server.item_control.ItemControlRule;
import io.toterra.subterra.engine.optim.server.item_control.ItemControlRuleSet;

import java.util.List;

/**
 * Deterministic acceptance probe for the itemban-port pure core (p.4.x,
 * item/block blacklist rules). Exercises the pure-JDK {@link ItemControlRule}
 * / {@link ItemControlRuleSet} matching, wildcard semantics, exact-vs-NBT
 * behaviour, case/whitespace robustness, empty sets and duplicate relations.
 * NBT deep-match stays in the MC-layer shell, so it is not asserted here.
 * Pure JVM, no timing assertions.
 */
public final class ItemControlProbe {

    private ItemControlProbe() {
    }

    private static int failures = 0;
    private static int checks = 0;

    private static void check(String name, boolean ok) {
        checks++;
        if (ok) {
            System.out.println("[PASS] " + name);
        } else {
            failures++;
            System.out.println("[FAIL] " + name);
        }
    }

    /** NbtMatcher that requires a non-null NBT payload. */
    private static ItemControlRule.NbtMatcher requireNbt = o -> o != null;
    /** NbtMatcher that fails only when the payload is exactly "denied". */
    private static ItemControlRule.NbtMatcher linkTag = o -> !"denied".equals(o);

    public static void main(String[] args) {
        // ---- exact id matching ----
        ItemControlRule diamond = ItemControlRule.of("minecraft:diamond");
        check("exact id matches itself", diamond.matches("minecraft:diamond", null));
        check("different id does not match", !diamond.matches("minecraft:gold", null));

        // ---- wildcard rule (no NBT constraint) matches any NBT ----
        check("wildcard rule matches null-nbt",
                ItemControlRule.of("minecraft:diamond").matches("minecraft:diamond", null));
        check("wildcard rule matches non-null-nbt",
                ItemControlRule.of("minecraft:diamond").matches("minecraft:diamond", "some-tag"));

        // ---- multiple rules: one hit suffices ----
        ItemControlRuleSet multi = ItemControlRuleSet.of(List.of(
                ItemControlRule.of("minecraft:gold_ingot"),
                ItemControlRule.of("minecraft:diamond")));
        check("second rule of the set matches", multi.contains("minecraft:diamond", null));
        check("unlisted id not matched", !multi.contains("minecraft:netherite", null));

        // ---- exact (NBT-constrained) vs wildcard ----
        ItemControlRule constrainedPass = ItemControlRule.of("minecraft:paper", "{count:1}", requireNbt);
        check("constrained rule passes when NBT present",
                constrainedPass.matches("minecraft:paper", "x"));
        check("constrained rule fails when NBT absent",
                !constrainedPass.matches("minecraft:paper", null));
        check("constrained rule still requires id",
                !constrainedPass.matches("minecraft:paper2", "x"));
        check("same id wildcard still matches without NBT",
                ItemControlRule.of("minecraft:paper").matches("minecraft:paper", null));

        // ---- case / whitespace robustness ----
        ItemControlRule cased = ItemControlRule.of("minecraft:diamond");
        check("matching is case-sensitive (registry keys lower-case)",
                !cased.matches("Minecraft:Diamond", null));
        check("leading/trailing whitespace in rule id is trimmed",
                ItemControlRule.of("  minecraft:diamond  ").matches("minecraft:diamond", null));

        // ---- empty set ----
        ItemControlRuleSet empty = ItemControlRuleSet.empty();
        check("empty set matches nothing", !empty.contains("minecraft:diamond", null));
        check("empty set of() is empty", ItemControlRuleSet.of(List.of()).isEmpty());
        check("empty set has size zero", ItemControlRuleSet.of(null).size() == 0);

        // ---- validation ----
        check("blank id rejected", rejectsId("   "));
        check("null id rejected", rejectsId(null));

        // ---- duplicate id dedup ----
        ItemControlRuleSet dedup = ItemControlRuleSet.of(List.of(
                ItemControlRule.of("minecraft:diamond"),
                ItemControlRule.of("minecraft:diamond")));
        check("duplicate id collapses to a single rule", dedup.size() == 1);
        check("duplicate still matches", dedup.contains("minecraft:diamond", null));

        ItemControlRuleSet distinctNbt = ItemControlRuleSet.of(List.of(
                ItemControlRule.of("minecraft:paper", "{a:1}", linkTag),
                ItemControlRule.of("minecraft:paper")));
        check("rules differing by NBT constraint are kept separately",
                distinctNbt.size() == 2);

        if (failures == 0) {
            System.out.println("[ItemControlProbe] PASS (item-control pure core, " + checks + " checks)");
            System.exit(0);
        } else {
            System.out.println("[ItemControlProbe] FAIL: " + failures + " assertion(s)");
            System.exit(1);
        }
    }

    private static boolean rejectsId(String id) {
        try {
            ItemControlRule.of(id);
            return false;
        } catch (IllegalArgumentException e) {
            return true;
        }
    }
}