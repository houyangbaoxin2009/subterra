// C2ME (Concurrent Chunk Management Engine) is a supported compatible peer,
// MIT (c) ishland: Subterra bundles its own derived async chunk engine
// (engine.worldgen.async) whose design follows C2ME's concurrency model (MIT
// attributed in the root NOTICE). The official NeoForge ver/1.21.1 jar remains
// a fully supported peer running stand-alone with its own c2me.toml. This
// coordinator detects its presence and logs coexistence guidance on the shared
// control surfaces (view/simulation distance, chunk I/O threading).
package io.toterra.subterra.runtime.worldgen.async;

import com.mojang.logging.LogUtils;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import org.slf4j.Logger;

/**
 * C2ME coexistence coordinator: detects the official C2ME NeoForge mod
 * ({@code c2me_base}) once at mod loading and, when present, logs guidance so
 * Subterra avoids double-governing the same surfaces. Subterra bundles its own
 * derived async chunk engine (engine.worldgen.async — the design follows C2ME's
 * concurrency model, MIT-attributed in the root NOTICE; original code, not a
 * verbatim copy). The official C2ME mod remains a supported compatible peer;
 * its own config is untouched. Detection is a simple
 * {@link ModList#isLoaded(String)} lookup.
 */
public final class C2meCoexistence {

    /** Official C2ME NeoForge umbrella module id. */
    public static final String C2ME_MODID = "c2me_base";

    private static final Logger LOGGER = LogUtils.getLogger();

    private static volatile boolean checked;
    private static volatile boolean present;

    private C2meCoexistence() {
    }

    /** Detects C2ME once; safe to call on both physical sides. */
    public static void bootstrap(ModContainer container) {
        if (checked) {
            return;
        }
        present = ModList.get().isLoaded(C2ME_MODID);
        checked = true;
        if (present) {
            LOGGER.info("[subterra_c2me] official C2ME (MIT compatible peer) detected; coexisting. "
                    + "Subterra bundles its own derived async chunk engine (engine.worldgen.async, "
                    + "design follows C2ME's concurrency model, MIT attributed in NOTICE); the official "
                    + "C2ME keeps its own config/c2me.toml and Subterra's dynamic view-distance / worker "
                    + "tuning stays orthogonal.");
        }
    }

    /** True when the official C2ME mod is loaded. */
    public static boolean present() {
        return present;
    }
}