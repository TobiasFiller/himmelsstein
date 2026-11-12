package net.tobiasfiller.himmelsstein.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.tobiasfiller.himmelsstein.client.UpgradeGlowRenderer;

@Mixin(ItemRenderer.class)
public abstract class ItemRendererMixin {
    private static final String RENDER =
            "render(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IILnet/minecraft/client/resources/model/BakedModel;)V";
    private static final String RENDER_BY_ITEM =
            "Lnet/minecraft/client/renderer/BlockEntityWithoutLevelRenderer;renderByItem(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V";

    // The coloured upgrade glint replaces the purple enchantment glint on held upgraded items.
    @ModifyExpressionValue(method = RENDER,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;hasFoil()Z"))
    private boolean himmelsstein$hideFoilForUpgradeGlint(boolean hasFoil,
            @Local(argsOnly = true) ItemStack stack, @Local(argsOnly = true) ItemDisplayContext context) {
        return hasFoil && !UpgradeGlowRenderer.replacesFoil(stack, context);
    }

    // Injected before popPose: the pose still holds the hand transform, and the model argument slot
    // already holds the camera-transformed model that vanilla rendered.
    @Inject(method = RENDER, at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;popPose()V"))
    private void himmelsstein$renderUpgradeGlow(ItemStack stack, ItemDisplayContext context, boolean leftHand,
            PoseStack poseStack, MultiBufferSource bufferSource, int light, int overlay, BakedModel model,
            CallbackInfo callback) {
        UpgradeGlowRenderer.render(stack, context, poseStack, model, bufferSource);
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
