package io.toterra.subterra.runtime.datapack;

import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import org.slf4j.Logger;

/**
 * Frozen datapack-registry injector (p.2.2 block 4). In 1.21.1 the datapack
 * registries (LOOT_TABLE / CONFIGURED_FEATURE / STRUCTURE / STRUCTURE_SET ...)
 * are created and frozen during the data reload — before any
 * {@code AddReloadListenerEvent} listener applies and long before
 * {@code ServerStarted}; {@code RegisterEvent} never fires for them
 * (javap-verified: {@code GameData.postRegisterEvents} only iterates the static
 * {@code BuiltInRegistries}, and the default datapack-registry builder consumer
 * is a no-op). The public {@link MappedRegistry#unfreeze() un-freeze →
 * register → freeze} window is the sanctioned injection surface, used by the
 * registrar only inside the load window (never on a hot path).
 */
final class DatapackRegistryInjector {

    private DatapackRegistryInjector() {
    }

    /**
     * Registers {@code value} under {@code key} in a frozen datapack registry.
     * Idempotent: an already-present key is a no-op success (a second server
     * lifecycle never double-registers). The registry is re-frozen in every path,
     * so the injection window never leaks.
     */
    static <T> boolean inject(Registry<T> registry, ResourceKey<T> key, T value,
                              Logger log, String marker) {
        if (registry.containsKey(key.location())) {
            log.info("{} {} already present; skip", marker, key.location());
            return true;
        }
        if (!(registry instanceof MappedRegistry<?> mapped)) {
            log.error("{} cannot inject {}: registry is not a MappedRegistry", marker, key.location());
            return false;
        }
        @SuppressWarnings("unchecked")
        MappedRegistry<T> mutable = (MappedRegistry<T>) mapped;
        mutable.unfreeze();
        try {
            mutable.register(key, value, RegistrationInfo.BUILT_IN);
            return true;
        } catch (Throwable t) {
            log.error("{} failed to inject {}: {}", marker, key.location(), t.toString());
            return false;
        } finally {
            mutable.freeze();
        }
    }
}
