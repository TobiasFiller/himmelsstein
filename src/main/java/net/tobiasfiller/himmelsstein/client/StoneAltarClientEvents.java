package net.tobiasfiller.himmelsstein.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.tobiasfiller.himmelsstein.Himmelsstein;
import net.tobiasfiller.himmelsstein.client.renderer.StoneAltarRenderer;
import net.tobiasfiller.himmelsstein.client.screen.StoneAltarScreen;
import net.tobiasfiller.himmelsstein.core.registry.ModBlockEntities;
import net.tobiasfiller.himmelsstein.core.registry.ModMenuTypes;

/** Client registration for the stone altar (screen + block entity renderer). */
@EventBusSubscriber(modid = Himmelsstein.MODID, value = Dist.CLIENT)
public final class StoneAltarClientEvents {
    private StoneAltarClientEvents() {}

    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenuTypes.STONE_ALTAR.get(), StoneAltarScreen::new);
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.STONE_ALTAR.get(), StoneAltarRenderer::new);
    }
}
