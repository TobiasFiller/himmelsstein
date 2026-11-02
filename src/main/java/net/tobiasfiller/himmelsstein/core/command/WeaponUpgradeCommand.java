package net.tobiasfiller.himmelsstein.core.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.tobiasfiller.himmelsstein.core.utility.UpgradeHelper;

public final class WeaponUpgradeCommand {
    private WeaponUpgradeCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("weaponupgrade")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("level", IntegerArgumentType.integer())
                        .executes(context -> execute(context.getSource(), IntegerArgumentType.getInteger(context, "level")))));
    }

    /**
     * Sets the absolute level on the item in the main hand. Range is validated here instead of via
     * {@code integer(0, 30)} so the translated out_of_range message is used.
     */
    public static int execute(CommandSourceStack source, int level) {
        ItemStack stack = source.getPlayer() != null
                ? source.getPlayer().getMainHandItem()
                : ItemStack.EMPTY;

        if (stack.isEmpty()) {
            source.sendFailure(Component.translatable("command.himmelsstein.weapon_upgrade.no_item"));
            return 0;
        }

        Component name = UpgradeHelper.getDisplayNameWithoutSuffix(stack);

        if (!UpgradeHelper.isUpgradable(stack)) {
            source.sendFailure(Component.translatable(
                    "command.himmelsstein.weapon_upgrade.not_upgradable", name));
            return 0;
        }

        if (level < 0 || level > UpgradeHelper.MAX_LEVEL) {
            source.sendFailure(Component.translatable(
                    "command.himmelsstein.weapon_upgrade.out_of_range", UpgradeHelper.MAX_LEVEL));
            return 0;
        }

        UpgradeHelper.setLevel(stack, level);
        source.sendSuccess(() -> Component.translatable(
                "command.himmelsstein.weapon_upgrade.set", name, level), false);
        return 1;
    }
}
