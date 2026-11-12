package net.tobiasfiller.himmelsstein.core.utility;

import java.util.List;

import javax.annotation.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.tobiasfiller.himmelsstein.core.registry.ModDataMaps;

/**
 * Data-driven stats of one protection rune.
 *
 * <p>The values live in the {@code himmelsstein:protection_rune} data map
 * ({@code data/himmelsstein/data_maps/item/protection_rune.json}) and are attached to the rune
 * item, not to the stack. A rune turns a destroying failure into a level loss of
 * {@code level_loss_percent} of the current level (at least one level, never below +0). The
 * optional {@code success_levels} list replaces the flat +1 on a successful attempt; the diamond
 * rune rolls +1, +2 or +3 instead.</p>
 */
public record ProtectionRuneData(double levelLossPercent, List<Integer> successLevels) {
    public static final List<Integer> DEFAULT_SUCCESS_LEVELS = List.of(1);

    public static final Codec<ProtectionRuneData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.doubleRange(0.0D, 100.0D).fieldOf("level_loss_percent")
                    .forGetter(ProtectionRuneData::levelLossPercent),
            Codec.intRange(1, UpgradeTables.MAX_LEVEL).listOf()
                    .optionalFieldOf("success_levels", DEFAULT_SUCCESS_LEVELS)
                    .forGetter(ProtectionRuneData::successLevels)
    ).apply(instance, (percent, levels) ->
            new ProtectionRuneData(percent, List.copyOf(levels))));

    /**
     * Levels lost when the rune turns a destruction into a level loss.
     *
     * <p>Rounded down, but at least one level as long as the item is above +0 and the rune has a
     * positive percentage. A rune with {@code 0} percent never costs a level.</p>
     */
    public int levelLoss(int currentLevel) {
        if (currentLevel <= 0 || levelLossPercent <= 0.0D) {
            return 0;
        }
        int loss = (int) Math.floor(currentLevel * levelLossPercent / 100.0D);
        return Math.min(currentLevel, Math.max(1, loss));
    }

    /** Level gain of a successful attempt; picks uniformly from {@code success_levels}. */
    public int rollSuccessGain(RandomSource random) {
        if (successLevels.isEmpty()) {
            return 1;
        }
        return Math.max(1, successLevels.get(random.nextInt(successLevels.size())));
    }

    /** True when the rune can grant more than the usual +1 on a successful attempt. */
    public boolean hasSuccessBonus() {
        return successLevels.stream().anyMatch(level -> level != 1);
    }

    /** Data of the rune item, or {@code null} if it is not a protection rune. */
    @SuppressWarnings("deprecation") // builtInRegistryHolder is the holder that carries data maps
    public static @Nullable ProtectionRuneData getData(Item item) {
        if (item == null) {
            return null;
        }
        return item.builtInRegistryHolder().getData(ModDataMaps.PROTECTION_RUNE);
    }

    public static @Nullable ProtectionRuneData getData(ItemStack stack) {
        return stack.isEmpty() ? null : getData(stack.getItem());
    }

    public static boolean isProtectionRune(Item item) {
        return getData(item) != null;
    }
}
