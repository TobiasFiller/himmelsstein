package net.tobiasfiller.himmelsstein.client.screen;

import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.tobiasfiller.himmelsstein.Himmelsstein;
import net.tobiasfiller.himmelsstein.common.menu.StoneAltarMenu;

/**
 * Stone altar GUI. The sacrificed item is rendered large in the centre; clicking it asks the
 * server to scrap the item.
 */
public class StoneAltarScreen extends AbstractContainerScreen<StoneAltarMenu> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Himmelsstein.MODID, "textures/gui/stone_altar.png");

    // Large item area (top-left corner + edge length), centred on the carpet (x 61-114, y 3-76).
    private static final int BIG_ITEM_X = 64;
    private static final int BIG_ITEM_Y = 16;
    private static final int BIG_ITEM_SIZE = 48;
    private static final float BIG_ITEM_SCALE = BIG_ITEM_SIZE / 16.0F;
    /** Extra size while the large item is hovered and clickable (+10 %). */
    private static final float HOVER_GROWTH = 0.10F;
    /** Time the hover grow/shrink animation takes. */
    private static final float HOVER_ANIMATION_MS = 150.0F;

    // Progress bar below the large item, still on the carpet.
    private static final int PROGRESS_X = 64;
    private static final int PROGRESS_Y = 68;
    private static final int PROGRESS_WIDTH = 48;
    private static final int PROGRESS_HEIGHT = 3;

    /** Hover animation state from 0 (normal size) to 1 (fully grown). */
    private float hoverProgress;
    private long lastFrameMillis = Util.getMillis();

    public StoneAltarScreen(StoneAltarMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 166;
        inventoryLabelY = imageHeight - 94;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        if (isOverBigItem(mouseX, mouseY) && !menu.getInput().isEmpty() && menu.getCarried().isEmpty()) {
            Component hint = menu.isScrapping()
                    ? Component.translatable("container." + Himmelsstein.MODID + ".stone_altar.scrapping")
                    : Component.translatable("container." + Himmelsstein.MODID + ".stone_altar.sacrifice");
            graphics.renderTooltip(font, hint, mouseX, mouseY);
        } else {
            renderTooltip(graphics, mouseX, mouseY);
        }
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight);

        ItemStack input = menu.getInput();
        updateHoverAnimation(isClickable(mouseX, mouseY));
        if (!input.isEmpty()) {
            float eased = hoverProgress * hoverProgress * (3.0F - 2.0F * hoverProgress); // smoothstep
            float scale = BIG_ITEM_SCALE * (1.0F + HOVER_GROWTH * eased);
            // Scale around the centre of the item area so it grows evenly in all directions.
            graphics.pose().pushPose();
            graphics.pose().translate(leftPos + BIG_ITEM_X + BIG_ITEM_SIZE / 2.0F,
                    topPos + BIG_ITEM_Y + BIG_ITEM_SIZE / 2.0F, 0.0F);
            graphics.pose().scale(scale, scale, 1.0F);
            graphics.renderItem(input, -8, -8);
            graphics.pose().popPose();
        }

        if (menu.isScrapping()) {
            int filled = Math.round(PROGRESS_WIDTH * menu.getProgress());
            int x = leftPos + PROGRESS_X;
            int y = topPos + PROGRESS_Y;
            graphics.fill(x, y, x + PROGRESS_WIDTH, y + PROGRESS_HEIGHT, 0xFF3A2A2A);
            graphics.fill(x, y, x + filled, y + PROGRESS_HEIGHT, 0xFFC0392B);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && isClickable(mouseX, mouseY) && minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, StoneAltarMenu.BUTTON_SCRAP);
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void updateHoverAnimation(boolean hovered) {
        long now = Util.getMillis();
        float step = (now - lastFrameMillis) / HOVER_ANIMATION_MS;
        lastFrameMillis = now;
        hoverProgress = Mth.clamp(hoverProgress + (hovered ? step : -step), 0.0F, 1.0F);
    }

    /** The large item can be clicked: it exists, nothing is carried and no scrap is running. */
    private boolean isClickable(double mouseX, double mouseY) {
        return isOverBigItem(mouseX, mouseY) && menu.getCarried().isEmpty() && !menu.getInput().isEmpty()
                && !menu.isScrapping();
    }

    private boolean isOverBigItem(double mouseX, double mouseY) {
        double x = mouseX - leftPos - BIG_ITEM_X;
        double y = mouseY - topPos - BIG_ITEM_Y;
        return x >= 0 && y >= 0 && x < BIG_ITEM_SIZE && y < BIG_ITEM_SIZE;
    }
}
