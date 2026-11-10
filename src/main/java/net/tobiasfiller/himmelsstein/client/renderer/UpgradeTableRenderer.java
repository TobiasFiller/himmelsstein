package net.tobiasfiller.himmelsstein.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.tobiasfiller.himmelsstein.common.blockentity.UpgradeTableBlockEntity;

/**
 * Renders the item from the item slot floating above the table.
 *
 * <p>The item is billboarded: it always turns its front towards the camera, so it faces the
 * player no matter where they stand. {@link ItemDisplayContext#FIXED} turns the item's front
 * towards -Z, which the yaw below compensates for.</p>
 */
public class UpgradeTableRenderer implements BlockEntityRenderer<UpgradeTableBlockEntity> {
    /** Table top (model height 15/16) plus the requested 0.4 blocks of air below the item. */
    private static final double ITEM_Y = 15.0D / 16.0D + 0.4D;
    /** Vertical bobbing around {@link #ITEM_Y}: amplitude in blocks and speed in radians per tick. */
    private static final double BOB_HEIGHT = 0.06D;
    private static final float BOB_SPEED = 0.1F;
    /**
     * In-plane tilt so a diagonal item sprite (sword, tool) stands upright. The item's
     * {@code FIXED} transform mirrors the sprite on X, hence the negative angle.
     */
    private static final float ITEM_TILT = -45.0F;
    private static final float ITEM_SCALE = 0.5F;

    private final ItemRenderer itemRenderer;

    public UpgradeTableRenderer(BlockEntityRendererProvider.Context context) {
        this.itemRenderer = context.getItemRenderer();
    }

    @Override
    public void render(UpgradeTableBlockEntity table, float partialTick, PoseStack poseStack,
            MultiBufferSource buffer, int packedLight, int packedOverlay) {
        ItemStack stack = table.getItem(UpgradeTableBlockEntity.SLOT_ITEM);
        if (stack.isEmpty() || table.getLevel() == null) {
            return;
        }
        int light = LevelRenderer.getLightColor(table.getLevel(), table.getBlockPos().above());
        double bob = Mth.sin((table.getLevel().getGameTime() + partialTick) * BOB_SPEED) * BOB_HEIGHT;

        poseStack.pushPose();
        poseStack.translate(0.5D, ITEM_Y + bob, 0.5D);
        poseStack.mulPose(Axis.YP.rotationDegrees(billboardYaw(table)));
        poseStack.mulPose(Axis.ZP.rotationDegrees(ITEM_TILT));
        poseStack.scale(ITEM_SCALE, ITEM_SCALE, ITEM_SCALE);
        itemRenderer.renderStatic(stack, ItemDisplayContext.FIXED, light, OverlayTexture.NO_OVERLAY,
                poseStack, buffer, table.getLevel(), (int) table.getBlockPos().asLong());
        poseStack.popPose();
    }

    /** Rotation that turns the item's front (-Z in {@code FIXED}) towards the camera. */
    private static float billboardYaw(UpgradeTableBlockEntity table) {
        Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        double dx = camera.x() - (table.getBlockPos().getX() + 0.5D);
        double dz = camera.z() - (table.getBlockPos().getZ() + 0.5D);
        return (float) (Mth.atan2(dx, dz) * (180.0D / Math.PI)) + 180.0F;
    }
}
