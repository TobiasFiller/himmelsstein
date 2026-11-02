package net.tobiasfiller.himmelsstein.client;

import java.io.IOException;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;

import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.tobiasfiller.himmelsstein.Himmelsstein;

@EventBusSubscriber(modid = Himmelsstein.MODID, value = Dist.CLIENT)
public final class UpgradeGlowEvents {
    private UpgradeGlowEvents() {}

    @SubscribeEvent
    static void onRegisterShaders(RegisterShadersEvent event) throws IOException {
        event.registerShader(new ShaderInstance(event.getResourceProvider(), UpgradeRenderTypes.GLOW_SHADER_ID,
                DefaultVertexFormat.NEW_ENTITY), UpgradeRenderTypes::setGlowShader);
        event.registerShader(new ShaderInstance(event.getResourceProvider(), UpgradeRenderTypes.GLINT_SHADER_ID,
                DefaultVertexFormat.NEW_ENTITY), UpgradeRenderTypes::setGlintShader);
    }

    @SubscribeEvent
    static void onRegisterReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) manager -> UpgradeGlowRenderer.onResourcesReloaded());
    }

    @SubscribeEvent
    static void onRenderLevelStage(RenderLevelStageEvent event) {
        // After the whole level, so held items (also the translucent / Fabulous item pass) are already
        // on screen and the glint lands on top of them.
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            UpgradeGlowRenderer.flushWorld(event.getModelViewMatrix(), event.getProjectionMatrix());
        }
    }
}
