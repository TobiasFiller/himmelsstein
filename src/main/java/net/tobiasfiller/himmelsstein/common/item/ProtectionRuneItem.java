package net.tobiasfiller.himmelsstein.common.item;

import java.util.List;
import java.util.Locale;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.tobiasfiller.himmelsstein.core.utility.ProtectionRuneData;

/**
 * Protection rune. All stats come from the {@code himmelsstein:protection_rune} data map, so the
 * tooltip and the upgrade table stay in sync with the datapack values.
 */
public class ProtectionRuneItem extends Item {
    public ProtectionRuneItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip,
            TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);

        ProtectionRuneData data = ProtectionRuneData.getData(stack);
        if (data == null) {
            return;
        }

        tooltip.add(Component.translatable("tooltip.himmelsstein.protection_rune.desc")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.himmelsstein.protection_rune.level_loss",
                        formatPercent(data.levelLossPercent()))
                .withStyle(ChatFormatting.GRAY));
        if (data.hasSuccessBonus()) {
            tooltip.add(Component.translatable("tooltip.himmelsstein.protection_rune.success_levels",
                            formatLevels(data.successLevels()))
                    .withStyle(ChatFormatting.GRAY));
        }
        tooltip.add(Component.translatable("tooltip.himmelsstein.protection_rune.consumed")
                .withStyle(ChatFormatting.DARK_GRAY));
    }

    private static String formatPercent(double percent) {
        if (Math.abs(percent - Math.rint(percent)) < 1.0E-6D) {
            return Long.toString(Math.round(percent));
        }
        return String.format(Locale.ROOT, "%.1f", percent);
    }

    private static String formatLevels(List<Integer> levels) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < levels.size(); i++) {
            if (i > 0) {
                builder.append(" / ");
            }
            builder.append('+').append(levels.get(i));
        }
        return builder.toString();
    }
}
