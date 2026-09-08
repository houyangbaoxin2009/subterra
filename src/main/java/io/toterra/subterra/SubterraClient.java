package io.toterra.subterra;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

// This class will not load on dedicated servers. Accessing client side code from here is safe.
@Mod(value = Subterra.MODID, dist = Dist.CLIENT)
// You can use EventBusSubscriber to automatically register all static methods in the class annotated with @SubscribeEvent
@EventBusSubscriber(modid = Subterra.MODID, value = Dist.CLIENT)
public class SubterraClient {
    public SubterraClient(ModContainer container) {
        // Allows NeoForge to create a config screen for this mod's configs.
        // The config screen is accessed by going to the Mods screen > clicking on your mod > clicking on config.
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);

        // JEC-style reading search (ported, MIT): language-agnostic — loads the
        // bundled pronunciation packs (zh pinyin, ja kana romaji, …); the
        // SearchTreeMixin redirects SearchTree.plainText to a reading-aware
        // SuffixArray so creative-inventory / name searches match readings.
        // Client-only (SearchTree is a client class).
        io.toterra.subterra.runtime.optim.client.search.ReadingSearch.bootstrap(container);
    }

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        // Client foundation hooks fire here later (rendering layer, etc.)
    }
}