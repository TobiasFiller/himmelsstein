package net.tobiasfiller.himmelsstein.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.tobiasfiller.himmelsstein.common.block.StoneAltarBlock;
import net.tobiasfiller.himmelsstein.common.blockentity.StoneAltarBlockEntity;

/** Renders the item waiting to be sacrificed lying flat on the altar top. */
public class StoneAltarRenderer implements BlockEntityRenderer<StoneAltarBlockEntity> {
    /** Altar top surface (model height 14/16) plus a small offset against z-fighting. */
    private static final double TOP_Y = 14.0D / 16.0D + 0.01D;

    public StoneAltarRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(StoneAltarBlockEntity altar, float partialTick, PoseStack poseStack,
            MultiBufferSource buffer, int packedLight, int packedOverlay) {
        ItemStack stack = altar.getItem(StoneAltarBlockEntity.SLOT_INPUT);
        if (stack.isEmpty() || altar.getLevel() == null) {
            return;
        }
        float facing = altar.getBlockState().getValue(StoneAltarBlock.FACING).toYRot();
        int light = LevelRenderer.getLightColor(altar.getLevel(), altar.getBlockPos().above());

        poseStack.pushPose();
        poseStack.translate(0.5D, TOP_Y, 0.5D);
        poseStack.mulPose(Axis.YP.rotationDegrees(-facing));
        poseStack.mulPose(Axis.XP.rotationDegrees(90.0F));
        poseStack.mulPose(Axis.ZP.rotationDegrees(45.0F));
        poseStack.scale(0.55F, 0.55F, 0.55F);
        Minecraft.getInstance().getItemRenderer().renderStatic(stack, ItemDisplayContext.FIXED, light,
                OverlayTexture.NO_OVERLAY, poseStack, buffer, altar.getLevel(), (int) altar.getBlockPos().asLong());
        poseStack.popPose();
    }
}
