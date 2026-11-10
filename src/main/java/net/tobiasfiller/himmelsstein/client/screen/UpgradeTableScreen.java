package net.tobiasfiller.himmelsstein.client.screen;

import java.util.List;
import java.util.Locale;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.tobiasfiller.himmelsstein.Himmelsstein;
import net.tobiasfiller.himmelsstein.common.blockentity.UpgradeTableBlockEntity;
import net.tobiasfiller.himmelsstein.common.menu.UpgradeTableMenu;
import net.tobiasfiller.himmelsstein.core.utility.UpgradeHelper;
import net.tobiasfiller.himmelsstein.core.utility.UpgradeStoneData;
import net.tobiasfiller.himmelsstein.core.utility.UpgradeTableHelper;

/**
 * Upgrade table GUI. The small plaque at the top shows the success chance, the icon below the
 * slots starts the attempt. Details (chance, max level) are tooltips.
 */
public class UpgradeTableScreen extends AbstractContainerScreen<UpgradeTableMenu> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Himmelsstein.MODID, "textures/gui/upgrade_table.png");

    // Small chance plaque at the top centre of the texture.
    private static final int CHANCE_X = 80;
    private static final int CHANCE_Y = 11;
    private static final int CHANCE_WIDTH = 16;
    private static final int CHANCE_HEIGHT = 8;

    // 16x16 icon button below the slots.
    private static final int BUTTON_X = 80;
    private static final int BUTTON_Y = 63;
    private static final int BUTTON_SIZE = 16;

    public UpgradeTableScreen(UpgradeTableMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 166;
        inventoryLabelY = imageHeight - 94;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        if (!menu.getCarried().isEmpty()) {
            return;
        }

        if (isOverChance(mouseX, mouseY)) {
            graphics.renderComponentTooltip(font, chanceTooltip(), mouseX, mouseY);
        } else if (isOverButton(mouseX, mouseY)) {
            graphics.renderTooltip(font, buttonTooltip(), mouseX, mouseY);
        } else if (isOverRuneSlot() && !hoveredSlot.hasItem()) {
            graphics.renderTooltip(font,
                    Component.translatable("container." + Himmelsstein.MODID + ".upgrade_table.rune_slot"),
                    mouseX, mouseY);
        } else {
            renderTooltip(graphics, mouseX, mouseY);
        }
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight);
        renderChance(graphics);
        renderButtonState(graphics, mouseX, mouseY);
    }

    private void renderChance(GuiGraphics graphics) {
        UpgradeTableHelper.Preview preview = menu.getPreview();
        String text = preview.valid() ? Math.round(preview.chance()) + "%" : "--";
        int color = preview.valid() ? 0xFFFFFF : 0xAAAAAA;
        float scale = Math.min(1.0F, (CHANCE_WIDTH - 1) / (float) font.width(text));

        graphics.pose().pushPose();
        graphics.pose().translate(leftPos + CHANCE_X + CHANCE_WIDTH / 2.0F, topPos + CHANCE_Y + 1.0F, 0.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.drawString(font, text, -font.width(text) / 2, 0, color, false);
        graphics.pose().popPose();
    }

    private void renderButtonState(GuiGraphics graphics, int mouseX, int mouseY) {
        int x = leftPos + BUTTON_X;
        int y = topPos + BUTTON_Y;
        if (!menu.getPreview().valid()) {
            graphics.fill(x, y, x + BUTTON_SIZE, y + BUTTON_SIZE, 0x80000000);
        } else if (isOverButton(mouseX, mouseY)) {
            graphics.fill(x, y, x + BUTTON_SIZE, y + BUTTON_SIZE, 0x40FFFFFF);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && isOverButton(mouseX, mouseY) && menu.getPreview().valid()
                && minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, UpgradeTableMenu.BUTTON_UPGRADE);
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected List<Component> getTooltipFromContainerItem(ItemStack stack) {
        List<Component> tooltip = super.getTooltipFromContainerItem(stack);
        if (hoveredSlot == null || hoveredSlot.index != UpgradeTableBlockEntity.SLOT_STONE || stack.isEmpty()) {
            return tooltip;
        }

        UpgradeStoneData data = UpgradeStoneData.getData(stack.getItem());
        if (data == null) {
            return tooltip;
        }

        ItemStack item = menu.getSlot(UpgradeTableBlockEntity.SLOT_ITEM).getItem();
        int level = UpgradeHelper.getLevel(item);
        if (UpgradeHelper.isUpgradable(item) && level < data.maxLevel()) {
            tooltip.add(Component.translatable("tooltip." + Himmelsstein.MODID + ".upgrade_table.chance",
                    formatChance(data.getSuccessChanceForLevel(level))).withStyle(ChatFormatting.GRAY));
        }
        tooltip.add(Component.translatable("tooltip." + Himmelsstein.MODID + ".upgrade_table.max_level",
                data.maxLevel()).withStyle(ChatFormatting.GRAY));
        return tooltip;
    }

    private List<Component> chanceTooltip() {
        UpgradeTableHelper.Preview preview = menu.getPreview();
        if (!preview.valid()) {
            return List.of(reason(preview.reason()).copy().withStyle(ChatFormatting.GRAY));
        }

        return List.of(
                Component.translatable("tooltip." + Himmelsstein.MODID + ".upgrade_table.chance",
                        formatChance(preview.chance())).withStyle(ChatFormatting.GRAY),
                Component.translatable("tooltip." + Himmelsstein.MODID + ".upgrade_table.max_level",
                        preview.maxLevel()).withStyle(ChatFormatting.GRAY));
    }

    private Component buttonTooltip() {
        UpgradeTableHelper.Preview preview = menu.getPreview();
        return preview.valid()
                ? Component.translatable("container." + Himmelsstein.MODID + ".upgrade_table.button")
                : reason(preview.reason());
    }

    private static Component reason(UpgradeTableHelper.Reason reason) {
        String key = switch (reason) {
            case NO_ITEM -> "reason.no_item";
            case NOT_UPGRADABLE -> "reason.not_upgradable";
            case NO_STONE -> "reason.no_stone";
            case NOT_A_STONE -> "reason.not_a_stone";
            case MAX_LEVEL -> "reason.max_level";
            case RUNE_NOT_SUPPORTED -> "reason.rune_not_supported";
            case OK -> "button";
        };
        return Component.translatable("container." + Himmelsstein.MODID + ".upgrade_table." + key);
    }

    private static String formatChance(double chance) {
        if (Math.abs(chance - Math.rint(chance)) < 1.0E-6D) {
            return Long.toString(Math.round(chance));
        }
        return String.format(Locale.ROOT, "%.1f", chance);
    }

    private boolean isOverChance(double mouseX, double mouseY) {
        double x = mouseX - leftPos - CHANCE_X;
        double y = mouseY - topPos - CHANCE_Y;
        return x >= 0.0D && y >= 0.0D && x < CHANCE_WIDTH && y < CHANCE_HEIGHT;
    }

    private boolean isOverButton(double mouseX, double mouseY) {
        double x = mouseX - leftPos - BUTTON_X;
        double y = mouseY - topPos - BUTTON_Y;
        return x >= 0.0D && y >= 0.0D && x < BUTTON_SIZE && y < BUTTON_SIZE;
    }

    private boolean isOverRuneSlot() {
        return hoveredSlot != null && hoveredSlot.index == UpgradeTableBlockEntity.SLOT_RUNE;
    }
}
