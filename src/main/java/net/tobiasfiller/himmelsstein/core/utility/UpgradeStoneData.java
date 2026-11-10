package net.tobiasfiller.himmelsstein.core.utility;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javax.annotation.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.tobiasfiller.himmelsstein.core.registry.ModDataMaps;

/**
 * Data-driven stats of one upgrade stone.
 *
 * <p>The values live in the {@code himmelsstein:upgrade_stone} data map
 * ({@code data/himmelsstein/data_maps/item/upgrade_stone.json}) and are attached to the stone
 * item, not to the stack. {@code success_chance} is indexed by the current upgrade level
 * (0 .. {@code max_level - 1}). {@code fail_outcomes} is a weighted list of what happens on a
 * failed roll; entries can be gated with {@code min_level}/{@code max_level}, and the remaining
 * weights are normalized per level.</p>
 */
public record UpgradeStoneData(int maxLevel, List<Double> successChance, List<FailOutcome> failOutcomes) {
    public enum FailOutcomeType implements StringRepresentable {
        KEEP("keep"),
        LOSE_LEVEL("lose_level"),
        DESTROY("destroy");

        public static final Codec<FailOutcomeType> CODEC = StringRepresentable.fromEnum(FailOutcomeType::values);

        private final String name;

        FailOutcomeType(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    /** One weighted failure result, optionally limited to a level range. */
    public record FailOutcome(FailOutcomeType outcome, double weight, @Nullable Integer minLevel,
            @Nullable Integer maxLevel) {
        public static final Codec<FailOutcome> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                FailOutcomeType.CODEC.fieldOf("outcome").forGetter(FailOutcome::outcome),
                Codec.doubleRange(0.0D, 1.0E9D).fieldOf("weight").forGetter(FailOutcome::weight),
                Codec.INT.optionalFieldOf("min_level").forGetter(outcome -> Optional.ofNullable(outcome.minLevel())),
                Codec.INT.optionalFieldOf("max_level").forGetter(outcome -> Optional.ofNullable(outcome.maxLevel()))
        ).apply(instance, (outcome, weight, min, max) ->
                new FailOutcome(outcome, weight, min.orElse(null), max.orElse(null))));

        public boolean isAvailable(int currentLevel) {
            return (minLevel == null || currentLevel >= minLevel)
                    && (maxLevel == null || currentLevel <= maxLevel);
        }
    }

    public static final List<FailOutcome> DEFAULT_FAIL_OUTCOMES =
            List.of(new FailOutcome(FailOutcomeType.KEEP, 1.0D, null, null));

    public static final Codec<UpgradeStoneData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.intRange(0, UpgradeTables.MAX_LEVEL).fieldOf("max_level").forGetter(UpgradeStoneData::maxLevel),
            Codec.doubleRange(0.0D, 100.0D).listOf().fieldOf("success_chance")
                    .forGetter(UpgradeStoneData::successChance),
            FailOutcome.CODEC.listOf().optionalFieldOf("fail_outcomes", DEFAULT_FAIL_OUTCOMES)
                    .forGetter(UpgradeStoneData::failOutcomes)
    ).apply(instance, (maxLevel, chances, outcomes) ->
            new UpgradeStoneData(maxLevel, List.copyOf(chances), List.copyOf(outcomes))));

    /** Success chance in percent for the current level; 0 for levels outside the table. */
    public double getSuccessChanceForLevel(int currentLevel) {
        if (currentLevel < 0 || currentLevel >= successChance.size()) {
            return 0.0D;
        }
        return successChance.get(currentLevel);
    }

    /** Outcomes that are allowed at the current level and have a positive weight. */
    public List<FailOutcome> availableFailOutcomes(int currentLevel) {
        List<FailOutcome> available = new ArrayList<>();
        for (FailOutcome outcome : failOutcomes) {
            if (outcome.weight() > 0.0D && outcome.isAvailable(currentLevel)) {
                available.add(outcome);
            }
        }
        return available;
    }

    /**
     * Picks a failure outcome from the available ones. {@code roll} is in [0, 1); the available
     * weights are normalized, so gated outcomes do not leave a gap.
     */
    public FailOutcomeType selectFailOutcome(int currentLevel, double roll) {
        List<FailOutcome> available = availableFailOutcomes(currentLevel);
        if (available.isEmpty()) {
            return FailOutcomeType.KEEP;
        }
        double total = 0.0D;
        for (FailOutcome outcome : available) {
            total += outcome.weight();
        }
        if (total <= 0.0D) {
            return FailOutcomeType.KEEP;
        }

        double target = Math.max(0.0D, Math.min(1.0D, roll)) * total;
        double cumulative = 0.0D;
        for (FailOutcome outcome : available) {
            cumulative += outcome.weight();
            if (target < cumulative) {
                return outcome.outcome();
            }
        }
        return available.get(available.size() - 1).outcome();
    }

    public FailOutcomeType rollFailOutcome(int currentLevel, RandomSource random) {
        return selectFailOutcome(currentLevel, random.nextDouble());
    }

    /** Data of the stone item, or {@code null} if it is not an upgrade stone. */
    @SuppressWarnings("deprecation") // builtInRegistryHolder is the holder that carries data maps
    public static @Nullable UpgradeStoneData getData(Item item) {
        if (item == null) {
            return null;
        }
        return item.builtInRegistryHolder().getData(ModDataMaps.UPGRADE_STONE);
    }

    public static @Nullable UpgradeStoneData getData(ItemStack stack) {
        return stack.isEmpty() ? null : getData(stack.getItem());
    }

    public static boolean isUpgradeStone(Item item) {
        return getData(item) != null;
    }
}
