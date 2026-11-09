package net.tobiasfiller.himmelsstein.core.utility;

import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;

/**
 * Scrap yield of the stone altar, loaded from {@code data/himmelsstein/altar_yield/default.json}.
 *
 * <p>All thresholds refer to the <em>remaining</em> durability points of the sacrificed item
 * ({@code maxDamage - damage}). For each output the tier with the highest {@code min_durability}
 * that is still reached applies; below the lowest tier the output is empty.</p>
 */
public record AltarYieldData(List<PowerTier> powerStone, List<HeavenTier> heavenStone) {
    public static final AltarYieldData EMPTY = new AltarYieldData(List.of(), List.of());

    public record PowerTier(int minDurability, int count) {
        public static final Codec<PowerTier> CODEC = RecordCodecBuilder.create(i -> i.group(
                ExtraCodecs.NON_NEGATIVE_INT.fieldOf("min_durability").forGetter(PowerTier::minDurability),
                Codec.intRange(0, 64).fieldOf("count").forGetter(PowerTier::count)
        ).apply(i, PowerTier::new));
    }

    public record HeavenTier(int minDurability, double chance) {
        public static final Codec<HeavenTier> CODEC = RecordCodecBuilder.create(i -> i.group(
                ExtraCodecs.NON_NEGATIVE_INT.fieldOf("min_durability").forGetter(HeavenTier::minDurability),
                Codec.doubleRange(0.0D, 1.0D).fieldOf("chance").forGetter(HeavenTier::chance)
        ).apply(i, HeavenTier::new));
    }

    public static final Codec<AltarYieldData> CODEC = RecordCodecBuilder.create(i -> i.group(
            PowerTier.CODEC.listOf().optionalFieldOf("power_stone", List.of()).forGetter(AltarYieldData::powerStone),
            HeavenTier.CODEC.listOf().optionalFieldOf("heaven_stone", List.of()).forGetter(AltarYieldData::heavenStone)
    ).apply(i, AltarYieldData::new));

    /** Result of one scrap roll. */
    public record Result(int powerStones, boolean heavenStone) {}

    /** Remaining durability points; 0 for items that cannot take damage. */
    public static int remainingDurability(ItemStack stack) {
        if (!stack.isDamageableItem()) {
            return 0;
        }
        return Math.max(0, stack.getMaxDamage() - stack.getDamageValue());
    }

    public int powerStonesFor(int remainingDurability) {
        int count = 0;
        int best = -1;
        for (PowerTier tier : powerStone) {
            if (remainingDurability >= tier.minDurability() && tier.minDurability() > best) {
                best = tier.minDurability();
                count = tier.count();
            }
        }
        return count;
    }

    public double heavenChanceFor(int remainingDurability) {
        double chance = 0.0D;
        int best = -1;
        for (HeavenTier tier : heavenStone) {
            if (remainingDurability >= tier.minDurability() && tier.minDurability() > best) {
                best = tier.minDurability();
                chance = tier.chance();
            }
        }
        return chance;
    }

    /** Largest number of power stones a single scrap can produce (used for the output space check). */
    public int maxPowerStones() {
        int max = 0;
        for (PowerTier tier : powerStone) {
            max = Math.max(max, tier.count());
        }
        return max;
    }

    public Result roll(ItemStack sacrificed, RandomSource random) {
        int remaining = remainingDurability(sacrificed);
        double chance = heavenChanceFor(remaining);
        boolean heaven = chance > 0.0D && random.nextDouble() < chance;
        return new Result(powerStonesFor(remaining), heaven);
    }
}
