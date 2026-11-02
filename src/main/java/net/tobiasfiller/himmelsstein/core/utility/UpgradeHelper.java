package net.tobiasfiller.himmelsstein.core.utility;

import java.util.regex.Pattern;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.tobiasfiller.himmelsstein.Himmelsstein;
import net.tobiasfiller.himmelsstein.core.registry.ModDataComponents;
import net.tobiasfiller.himmelsstein.core.registry.ModTags;

/**
 * Single place for reading/writing the upgrade level and translating it into values.
 *
 * <p>Items are singletons, stacks are not: the level always lives in the stack's data component,
 * never in an item field.</p>
 */
public final class UpgradeHelper {
    public static final int MAX_LEVEL = UpgradeTables.MAX_LEVEL;

    public static final ResourceLocation ATTACK_DAMAGE_MODIFIER_ID =
            ResourceLocation.fromNamespaceAndPath(Himmelsstein.MODID, "upgrade_attack_damage");

    private static final Pattern UPGRADE_SUFFIX = Pattern.compile("\\s*\\+\\d+$");

    public enum Category {
        NONE,
        WEAPON,
        PROJECTILE
    }

    private UpgradeHelper() {}

    public static int clampLevel(int level) {
        return Math.max(0, Math.min(MAX_LEVEL, level));
    }

    public static int getLevel(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return 0;
        }
        Integer level = stack.get(ModDataComponents.UPGRADE_LEVEL.get());
        return level == null ? 0 : clampLevel(level);
    }

    /** Sets the level, clamped to 0-30. Level 0 removes the component entirely. */
    public static void setLevel(ItemStack stack, int level) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        int clamped = clampLevel(level);
        if (clamped == 0) {
            stack.remove(ModDataComponents.UPGRADE_LEVEL.get());
        } else {
            stack.set(ModDataComponents.UPGRADE_LEVEL.get(), clamped);
        }
    }

    /**
     * Clamps a stored value that came from disk/commands. Kept tolerant instead of failing the
     * whole item codec, and logs the correction once per loaded stack.
     */
    public static void normalize(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        Integer stored = stack.get(ModDataComponents.UPGRADE_LEVEL.get());
        if (stored == null) {
            return;
        }
        int clamped = clampLevel(stored);
        if (clamped != stored) {
            Himmelsstein.LOGGER.warn("Clamped invalid upgrade_level {} on {} to {}",
                    stored, stack.getItem(), clamped);
            setLevel(stack, clamped);
        }
    }

    public static Category getCategory(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return Category.NONE;
        }
        if (stack.is(ModTags.UPGRADABLE_PROJECTILE)) {
            return Category.PROJECTILE;
        }
        if (stack.is(ModTags.UPGRADABLE_WEAPON)) {
            return Category.WEAPON;
        }
        return Category.NONE;
    }

    public static boolean isUpgradable(ItemStack stack) {
        return getCategory(stack) != Category.NONE;
    }

    public static double getDamageBonus(int level) {
        return UpgradeTables.weaponDamage(level);
    }

    public static double getProjectileBonus(int level) {
        return UpgradeTables.projectileDamage(level);
    }

    /** Appends the plain "+N" suffix to a hover name, inheriting the name's color. Level 0 keeps the name untouched. */
    public static Component withUpgradeSuffix(Component name, int level) {
        if (level <= 0) {
            return name;
        }
        MutableComponent suffix = Component.literal("+" + level);
        return Component.empty().append(name).append(Component.literal(" ")).append(suffix);
    }

    /** Removes a typed "+N" suffix from a custom name so only the real level is rendered. */
    public static void stripSuffixFromCustomName(ItemStack stack) {
        Component customName = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_NAME);
        if (customName == null) {
            return;
        }
        String text = customName.getString();
        String cleaned = UPGRADE_SUFFIX.matcher(text).replaceFirst("");
        if (!cleaned.equals(text)) {
            stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal(cleaned));
        }
    }

    /** Display name without a rendered upgrade suffix, used in command feedback. */
    public static Component getDisplayNameWithoutSuffix(ItemStack stack) {
        Component hover = stack.getHoverName();
        String text = hover.getString();
        String cleaned = UPGRADE_SUFFIX.matcher(text).replaceFirst("");
        return cleaned.equals(text) ? hover : Component.literal(cleaned);
    }
}
