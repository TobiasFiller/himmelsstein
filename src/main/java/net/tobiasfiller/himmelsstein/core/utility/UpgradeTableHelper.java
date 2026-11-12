package net.tobiasfiller.himmelsstein.core.utility;

import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;

/**
 * Server-side evaluation of one upgrade table attempt.
 *
 * <p>{@link #preview} is shared by the GUI (to show the chance and disable the button) and by the
 * server (to validate the click). {@link #attempt} only rolls the dice; it never mutates a stack,
 * so the caller stays in control of consuming the stone and applying the result.</p>
 */
public final class UpgradeTableHelper {
    public enum Reason {
        OK,
        NO_ITEM,
        NOT_UPGRADABLE,
        NO_STONE,
        NOT_A_STONE,
        NOT_A_RUNE,
        MAX_LEVEL
    }

    public enum Outcome {
        SUCCESS,
        FAIL_KEEP,
        FAIL_LOSE_LEVEL,
        FAIL_DESTROY,
        FAIL_RUNE_PROTECT,
        INVALID
    }

    /** What the next attempt would do. {@code chance} is a percentage from 0 to 100. */
    public record Preview(boolean valid, Reason reason, double chance, int currentLevel, int targetLevel,
            int maxLevel) {
        public static Preview invalid(Reason reason) {
            return new Preview(false, reason, 0.0D, 0, 0, 0);
        }
    }

    /** Result of a roll. {@code newLevel} is the level after a success or a level loss. */
    public record Result(Outcome outcome, int newLevel) {
        public static Result invalid() {
            return new Result(Outcome.INVALID, 0);
        }
    }

    private UpgradeTableHelper() {}

    /**
     * Validates the three slots and resolves the chance for the current level.
     *
     * <p>A protection rune in the third slot is optional. It never changes the success chance; it
     * only turns a destroying failure into a level loss (see {@link #attempt}).</p>
     */
    public static Preview preview(ItemStack item, ItemStack stone, ItemStack rune) {
        if (item.isEmpty()) {
            return Preview.invalid(Reason.NO_ITEM);
        }
        if (!UpgradeHelper.isUpgradable(item)) {
            return Preview.invalid(Reason.NOT_UPGRADABLE);
        }
        if (!rune.isEmpty() && ProtectionRuneData.getData(rune) == null) {
            return Preview.invalid(Reason.NOT_A_RUNE);
        }
        if (stone.isEmpty()) {
            return Preview.invalid(Reason.NO_STONE);
        }

        UpgradeStoneData data = UpgradeStoneData.getData(stone.getItem());
        if (data == null) {
            return Preview.invalid(Reason.NOT_A_STONE);
        }

        int level = UpgradeHelper.getLevel(item);
        if (level >= data.maxLevel()) {
            return new Preview(false, Reason.MAX_LEVEL, 0.0D, level, level, data.maxLevel());
        }

        double chance = data.getSuccessChanceForLevel(level);
        return new Preview(true, Reason.OK, chance, level, level + 1, data.maxLevel());
    }

    /** Rolls one attempt. The caller consumes the stone and applies the outcome. */
    public static Result attempt(ItemStack item, ItemStack stone, ItemStack rune, RandomSource random) {
        Preview preview = preview(item, stone, rune);
        if (!preview.valid()) {
            return Result.invalid();
        }

        ProtectionRuneData runeData = ProtectionRuneData.getData(rune);

        // nextDouble() is in [0, 1), so 100 % always succeeds and 0 % always fails.
        if (random.nextDouble() * 100.0D < preview.chance()) {
            int gain = runeData == null ? 1 : runeData.rollSuccessGain(random);
            int maxLevel = Math.min(UpgradeHelper.MAX_LEVEL, preview.maxLevel());
            int newLevel = Math.min(maxLevel, preview.currentLevel() + gain);
            return new Result(Outcome.SUCCESS, newLevel);
        }

        UpgradeStoneData data = UpgradeStoneData.getData(stone.getItem());
        if (data == null) {
            return Result.invalid();
        }

        UpgradeStoneData.FailOutcomeType outcome =
                data.rollFailOutcome(preview.currentLevel(), random);
        if (outcome == UpgradeStoneData.FailOutcomeType.DESTROY && runeData != null) {
            int newLevel = preview.currentLevel() - runeData.levelLoss(preview.currentLevel());
            return new Result(Outcome.FAIL_RUNE_PROTECT, newLevel);
        }
        return failureResult(preview.currentLevel(), outcome);
    }

    /**
     * Applies one failure outcome. A level loss at +0 becomes a harmless failure, because the
     * item cannot go below +0.
     */
    public static Result failureResult(int currentLevel, UpgradeStoneData.FailOutcomeType outcome) {
        return switch (outcome) {
            case KEEP -> new Result(Outcome.FAIL_KEEP, currentLevel);
            case LOSE_LEVEL -> currentLevel <= 0
                    ? new Result(Outcome.FAIL_KEEP, currentLevel)
                    : new Result(Outcome.FAIL_LOSE_LEVEL, currentLevel - 1);
            case DESTROY -> new Result(Outcome.FAIL_DESTROY, currentLevel);
        };
    }
}
