package net.tobiasfiller.himmelsstein.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.ClientHooks;
import net.tobiasfiller.himmelsstein.client.UpgradeGlowRenderer;

@Mixin(ItemRenderer.class)
public abstract class ItemRendererMixin {
    private static final String RENDER =
            "render(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IILnet/minecraft/client/resources/model/BakedModel;)V";
    private static final String RENDER_BY_ITEM =
            "Lnet/minecraft/client/renderer/BlockEntityWithoutLevelRenderer;renderByItem(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V";

    /**
     * Records the upgrade effects on a HEAD injection instead of at the end of the vanilla body.
     *
     * <p>Item models that implement Fabric's {@code FabricBakedModel} (e.g. Continuity's emissive
     * wrapper, which reaches NeoForge through Sinytra Connector) are rendered by Sodium's FRAPI
     * renderer, which cancels this method at HEAD. Hooking the end of the body would silently lose
     * every effect in such a pack, so the vanilla transform is reproduced here instead: the camera
     * transform of the model, followed by the half-block centring offset.</p>
     */
    @Inject(method = RENDER, at = @At("HEAD"))
    private void himmelsstein$renderUpgradeGlow(ItemStack stack, ItemDisplayContext context, boolean leftHand,
            PoseStack poseStack, MultiBufferSource bufferSource, int light, int overlay, BakedModel model,
            CallbackInfo callback) {
        if (stack.isEmpty() || !UpgradeGlowRenderer.hasEffects(stack, context, model)) {
            return;
        }
        poseStack.pushPose();
        BakedModel transformed = ClientHooks.handleCameraTransforms(poseStack, model, context, leftHand);
        poseStack.translate(-0.5F, -0.5F, -0.5F);
        UpgradeGlowRenderer.render(stack, context, poseStack, transformed, bufferSource);
        poseStack.popPose();
    }

    // Custom item renderers (trident, modded weapons): their geometry is captured while they draw.
    @ModifyArg(method = RENDER, at = @At(value = "INVOKE", target = RENDER_BY_ITEM), index = 3)
    private MultiBufferSource himmelsstein$captureCustomRenderer(ItemStack stack, ItemDisplayContext context,
            PoseStack poseStack, MultiBufferSource bufferSource, int light, int overlay) {
        return UpgradeGlowRenderer.wrapCustomRenderer(stack, context, poseStack, bufferSource);
    }

    @Inject(method = RENDER, at = @At(value = "INVOKE", target = RENDER_BY_ITEM, shift = At.Shift.AFTER))
    private void himmelsstein$finishCustomRenderer(CallbackInfo callback) {
        UpgradeGlowRenderer.finishCustomRenderer();
    }
}
