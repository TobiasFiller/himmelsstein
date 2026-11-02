package net.tobiasfiller.himmelsstein.core.event;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AnvilUpdateEvent;
import net.neoforged.neoforge.event.ItemAttributeModifierEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AnvilRepairEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.tobiasfiller.himmelsstein.Himmelsstein;
import net.tobiasfiller.himmelsstein.core.command.WeaponUpgradeCommand;
import net.tobiasfiller.himmelsstein.core.utility.UpgradeHelper;
import net.tobiasfiller.himmelsstein.network.UpgradeConfigPayload;

@EventBusSubscriber(modid = Himmelsstein.MODID)
public final class UpgradeEvents {
    private UpgradeEvents() {}

    @SubscribeEvent
    public static void onItemAttributeModifiers(ItemAttributeModifierEvent event) {
        ItemStack stack = event.getItemStack();
        if (UpgradeHelper.getCategory(stack) != UpgradeHelper.Category.WEAPON) {
            return;
        }
        int level = UpgradeHelper.getLevel(stack);
        if (level <= 0) {
            return;
        }
        double bonus = UpgradeHelper.getDamageBonus(level);
        if (bonus <= 0.0D) {
            return;
        }
        // Transiently computed from the level table; no modifier is ever stored on the stack.
        event.replaceModifier(
                Attributes.ATTACK_DAMAGE,
                new AttributeModifier(UpgradeHelper.ATTACK_DAMAGE_MODIFIER_ID, bonus,
                        AttributeModifier.Operation.ADD_VALUE),
                EquipmentSlotGroup.MAINHAND);
    }

    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent.Pre event) {
        float damage = event.getNewDamage();
        if (damage <= 0.0F) {
            return;
        }

        // Bow/crossbow: the weapon that fired the arrow decides, not the shooter's current hand.
        if (event.getSource().getDirectEntity() instanceof AbstractArrow arrow) {
            ItemStack weapon = arrow.getWeaponItem();
            if (weapon != null && UpgradeHelper.getCategory(weapon) == UpgradeHelper.Category.PROJECTILE) {
                int level = UpgradeHelper.getLevel(weapon);
                double bonus = UpgradeHelper.getProjectileBonus(level);
                if (bonus > 0.0D) {
                    damage *= (float) (1.0D + bonus);
                }
            }
        }

        event.setNewDamage(damage);
    }

    @SubscribeEvent
    public static void onAnvilUpdate(AnvilUpdateEvent event) {
        // Only plus + plus is blocked; books, repairs and combining a plus with a plain item stay vanilla.
        if (UpgradeHelper.getLevel(event.getLeft()) > 0 && UpgradeHelper.getLevel(event.getRight()) > 0) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onAnvilRepair(AnvilRepairEvent event) {
        // A player can type "+20" into the anvil name field. Strip it from the stored custom name
        // so only the real upgrade level is rendered.
        UpgradeHelper.stripSuffixFromCustomName(event.getOutput());
    }

    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        int level = UpgradeHelper.getLevel(event.getItemStack());
        if (level > 0) {
            event.getToolTip().add(Component.translatable("tooltip.himmelsstein.upgrade_level", level)
                    .withStyle(ChatFormatting.GRAY));
        }
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        WeaponUpgradeCommand.register(event.getDispatcher());
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PacketDistributor.sendToPlayer(player, UpgradeEvents.currentConfigPayload());
        }
    }

    public static UpgradeConfigPayload currentConfigPayload() {
        return new UpgradeConfigPayload(
                copy(ConfigAccess.weaponDamage()),
                copy(ConfigAccess.projectileDamage()));
    }

    private static List<Double> copy(List<? extends Double> values) {
        return new ArrayList<>(values);
    }

    /** Small indirection so the event class does not depend on unloaded config getters directly. */
    private static final class ConfigAccess {
        static List<? extends Double> weaponDamage() {
            return net.tobiasfiller.himmelsstein.Config.WEAPON_DAMAGE.get();
        }

        static List<? extends Double> projectileDamage() {
            return net.tobiasfiller.himmelsstein.Config.PROJECTILE_DAMAGE.get();
        }
    }
}
