package net.tobiasfiller.himmelsstein.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.tobiasfiller.himmelsstein.Himmelsstein;
import net.tobiasfiller.himmelsstein.client.renderer.UpgradeTableRenderer;
import net.tobiasfiller.himmelsstein.client.screen.UpgradeTableScreen;
import net.tobiasfiller.himmelsstein.core.registry.ModBlockEntities;
import net.tobiasfiller.himmelsstein.core.registry.ModMenuTypes;

/** Client registration for the upgrade table (screen + floating item renderer). */
@EventBusSubscriber(modid = Himmelsstein.MODID, value = Dist.CLIENT)
public final class UpgradeTableClientEvents {
    private UpgradeTableClientEvents() {}

    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenuTypes.UPGRADE_TABLE.get(), UpgradeTableScreen::new);
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.UPGRADE_TABLE.get(), UpgradeTableRenderer::new);
    }
}
