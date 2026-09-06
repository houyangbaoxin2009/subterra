// C2ME (Concurrent Chunk Management Engine) is adopted as an optional peer,
// MIT (c) ishland: Subterra does not bundle or port it — the official NeoForge
// ver/1.21.1 jar runs stand-alone with its own c2me.toml. This coordinator only
// detects its presence and logs coexistence guidance on the shared control
// surfaces (view/simulation distance, chunk I/O threading).
package io.toterra.subterra.optim.worldgen.async;

import com.mojang.logging.LogUtils;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import org.slf4j.Logger;

/**
 * C2ME coexistence coordinator: detects the official C2ME NeoForge mod
 * ({@code c2me_base}) once at mod loading and, when present, logs guidance so
 * Subterra avoids double-governing the same surfaces (C2ME owns the async
 * chunk pipeline; Subterra's dynamic view-distance / worker tuning are
 * orthogonal but may be de-prioritized deliberately). Detection is a simple
 * {@link ModList#isLoaded(String)} lookup; C2ME's own config is untouched.
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
            LOGGER.info("[subterra_c2me] official C2ME (async chunk engine) detected; coexisting. "
                    + "C2ME owns the async chunk pipeline (config/c2me.toml); Subterra keeps its own "
                    + "dynamic view-distance / worker tuning orthogonal.");
        }
    }

    /** True when the official C2ME mod is loaded. */
    public static boolean present() {
        return present;
    }
}