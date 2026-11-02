package net.tobiasfiller.himmelsstein.client;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.tobiasfiller.himmelsstein.Himmelsstein;

@Mod(value = Himmelsstein.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = Himmelsstein.MODID, value = Dist.CLIENT)
public class HimmelssteinClient {
    public HimmelssteinClient(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        Himmelsstein.LOGGER.info("Himmelsstein client setup (Minecraft {})",
                Minecraft.getInstance().getUser().getName());
    }
}
