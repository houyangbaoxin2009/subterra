// Ported from Inventory Advancement Accelerator (github.com/vicuna-main/InventoryAdvancementAccelerator), MIT (c) vicuna.
package io.toterra.subterra.optim.server.advancement.index;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

public final class IdentitySet {
    private IdentitySet() {}

    public static <T> Set<T> create() {
        return Collections.newSetFromMap(new IdentityHashMap<>());
    }
}
