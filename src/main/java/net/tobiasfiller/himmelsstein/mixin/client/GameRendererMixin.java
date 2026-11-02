package net.tobiasfiller.himmelsstein.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import net.tobiasfiller.himmelsstein.client.UpgradeGlowRenderer;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    private static final String RENDER_HANDS =
            "Lnet/minecraft/client/renderer/ItemInHandRenderer;renderHandsWithItems(FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;Lnet/minecraft/client/player/LocalPlayer;I)V";

    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void himmelsstein$beginUpgradeGlow(DeltaTracker deltaTracker, CallbackInfo callback) {
        UpgradeGlowRenderer.beginFrame();
    }

    @Inject(method = "renderLevel", at = @At("TAIL"))
    private void himmelsstein$compositeUpgradeGlow(DeltaTracker deltaTracker, CallbackInfo callback) {
        UpgradeGlowRenderer.composite(deltaTracker.getGameTimeDeltaTicks());
    }

    @Inject(method = "renderItemInHand", at = @At(value = "INVOKE", target = RENDER_HANDS))
    private void himmelsstein$beginHandGlow(CallbackInfo callback) {
        UpgradeGlowRenderer.beginHand();
    }

    @Inject(method = "renderItemInHand", at = @At(value = "INVOKE", target = RENDER_HANDS, shift = At.Shift.AFTER))
    private void himmelsstein$flushHandGlow(CallbackInfo callback) {
        UpgradeGlowRenderer.flushHand();
    }
}
