// Ported from Inventory Advancement Accelerator (github.com/vicuna-main/InventoryAdvancementAccelerator), MIT (c) vicuna.
package io.toterra.subterra.runtime.optim.server.advancement.index;

import java.util.Set;

/** Immutable, player-independent portion of an inventory criterion index plan. */
public record CompiledPlanTemplate(
        Set<Integer> rawItemIds,
        boolean alwaysCheck,
        boolean wildcard,
        boolean slotSensitive,
        boolean indexSafe) {
}
