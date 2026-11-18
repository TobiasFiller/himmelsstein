package net.tobiasfiller.himmelsstein.core.utility;

import java.util.Optional;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Unbreakable;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
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
    public static final int UNBREAKABLE_LEVEL = 24;

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
        setLevel(stack, level, null);
    }

    /**
     * Sets the level, clamped to 0-30, and synchronizes the deterministic durability effects
     * (Unbreaking / Unzerstörbar) derived from the level. Level 0 removes the level component.
     *
     * <p>The comparison uses the previously stored upgrade level: higher target = upgrade
     * ({@code max(current unbreaking, table bonus)}), lower target = downgrade (exact table value),
     * equal target = no durability change.</p>
     */
    public static void setLevel(ItemStack stack, int level, @Nullable HolderLookup.Provider registries) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        int oldLevel = getLevel(stack);
        int clamped = clampLevel(level);
        if (clamped == 0) {
            stack.remove(ModDataComponents.UPGRADE_LEVEL.get());
        } else {
            stack.set(ModDataComponents.UPGRADE_LEVEL.get(), clamped);
        }
        if (clamped != oldLevel) {
            syncDurabilityEffects(stack, oldLevel, clamped, registries);
        }
    }

    /**
     * Clamps a stored value that came from disk/commands. Kept tolerant instead of failing the
     * whole item codec, and logs the correction once per loaded stack. Existing upgraded items are
     * synchronized to the table value for their current level so durability effects stay consistent.
     */
    public static void normalize(ItemStack stack) {
        normalize(stack, null);
    }

    /** Same as {@link #normalize(ItemStack)} but with explicit registry access for effect sync. */
    public static void normalize(ItemStack stack, @Nullable HolderLookup.Provider registries) {
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
            if (clamped == 0) {
                stack.remove(ModDataComponents.UPGRADE_LEVEL.get());
            } else {
                stack.set(ModDataComponents.UPGRADE_LEVEL.get(), clamped);
            }
            // Treat as downgrade to the clamped (table) value.
            syncDurabilityEffects(stack, stored, clamped, registries);
            return;
        }
        // Migration: force-sync to the table value for the already valid level.
        syncDurabilityToTable(stack, clamped, registries);
    }

    public static int getUnbreakingBonus(int level) {
        if (level < 4) {
            return 0;
        }
        return Math.min(5, level / 4);
    }

    public static boolean isUnbreakableLevel(int level) {
        return level >= UNBREAKABLE_LEVEL;
    }

    private static void syncDurabilityEffects(ItemStack stack, int oldLevel, int newLevel,
            @Nullable HolderLookup.Provider registries) {
        if (newLevel == oldLevel) {
            return;
        }
        if (newLevel > oldLevel) {
            applyUnbreaking(stack, Math.max(getCurrentUnbreakingLevel(stack), getUnbreakingBonus(newLevel)), registries);
            applyUnbreakable(stack, stack.has(DataComponents.UNBREAKABLE) || isUnbreakableLevel(newLevel));
        } else {
            // Downgrade always enforces the exact table value.
            syncDurabilityToTable(stack, newLevel, registries);
        }
    }

    private static void syncDurabilityToTable(ItemStack stack, int level,
            @Nullable HolderLookup.Provider registries) {
        applyUnbreaking(stack, getUnbreakingBonus(level), registries);
        applyUnbreakable(stack, isUnbreakableLevel(level));
    }

    private static int getCurrentUnbreakingLevel(ItemStack stack) {
        ItemEnchantments enchantments = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        for (var entry : enchantments.entrySet()) {
            if (entry.getKey().is(Enchantments.UNBREAKING)) {
                return entry.getIntValue();
            }
        }
        return 0;
    }

    private static void applyUnbreaking(ItemStack stack, int targetLevel,
            @Nullable HolderLookup.Provider registries) {
        int current = getCurrentUnbreakingLevel(stack);
        if (current == targetLevel) {
            return;
        }

        ItemEnchantments existing = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        Holder<Enchantment> holder = findUnbreakingHolder(existing).orElse(null);
        if (holder == null && targetLevel > 0) {
            holder = resolveUnbreakingHolder(registries).orElse(null);
        }
        if (holder == null && targetLevel > 0) {
            // Cannot add Unbreaking without a registry holder (e.g. client-side migration without
            // access). Server-side call sites pass registries; best-effort otherwise.
            return;
        }

        ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(existing);
        if (holder != null) {
            mutable.set(holder, targetLevel);
        } else {
            // targetLevel == 0: remove by key even without a resolved holder instance.
            for (Holder<Enchantment> key : mutable.keySet()) {
                if (key.is(Enchantments.UNBREAKING)) {
                    mutable.set(key, 0);
                    break;
                }
            }
        }

        ItemEnchantments result = mutable.toImmutable();
        if (result.isEmpty()) {
            stack.remove(DataComponents.ENCHANTMENTS);
        } else {
            stack.set(DataComponents.ENCHANTMENTS, result);
        }
    }

    private static void applyUnbreakable(ItemStack stack, boolean unbreakable) {
        if (unbreakable) {
            Unbreakable current = stack.get(DataComponents.UNBREAKABLE);
            if (current == null || !current.showInTooltip()) {
                stack.set(DataComponents.UNBREAKABLE, new Unbreakable(true));
            }
        } else if (stack.has(DataComponents.UNBREAKABLE)) {
            stack.remove(DataComponents.UNBREAKABLE);
        }
    }

    private static Optional<Holder<Enchantment>> findUnbreakingHolder(ItemEnchantments enchantments) {
        for (Holder<Enchantment> holder : enchantments.keySet()) {
            if (holder.is(Enchantments.UNBREAKING)) {
                return Optional.of(holder);
            }
        }
        return Optional.empty();
    }

    private static Optional<Holder<Enchantment>> resolveUnbreakingHolder(
            @Nullable HolderLookup.Provider registries) {
        HolderLookup.Provider provider = registries;
        if (provider == null) {
            var server = ServerLifecycleHooks.getCurrentServer();
            if (server != null) {
                provider = server.registryAccess();
            }
        }
        if (provider == null) {
            return Optional.empty();
        }
        return provider.lookupOrThrow(Registries.ENCHANTMENT).get(Enchantments.UNBREAKING).map(ref -> (Holder<Enchantment>) ref);
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
        Component customName = stack.get(DataComponents.CUSTOM_NAME);
        if (customName == null) {
            return;
        }
        String text = customName.getString();
        String cleaned = UPGRADE_SUFFIX.matcher(text).replaceFirst("");
        if (!cleaned.equals(text)) {
            stack.set(DataComponents.CUSTOM_NAME, Component.literal(cleaned));
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
