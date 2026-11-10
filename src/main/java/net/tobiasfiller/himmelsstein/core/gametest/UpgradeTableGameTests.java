package net.tobiasfiller.himmelsstein.core.gametest;

import java.util.UUID;

import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.tobiasfiller.himmelsstein.Himmelsstein;
import net.tobiasfiller.himmelsstein.common.blockentity.UpgradeTableBlockEntity;
import net.tobiasfiller.himmelsstein.common.menu.UpgradeTableMenu;
import net.tobiasfiller.himmelsstein.core.registry.ModBlocks;
import net.tobiasfiller.himmelsstein.core.registry.ModItems;
import net.tobiasfiller.himmelsstein.core.utility.UpgradeHelper;
import net.tobiasfiller.himmelsstein.core.utility.UpgradeStoneData;
import net.tobiasfiller.himmelsstein.core.utility.UpgradeTableHelper;

/**
 * Upgrade table behaviour: data map values, preview validation, rolls, stone consumption,
 * failure outcomes and the menu button.
 */
@GameTestHolder(Himmelsstein.MODID)
@PrefixGameTestTemplate(false)
public final class UpgradeTableGameTests {
    private static final BlockPos TABLE = new BlockPos(1, 1, 1);
    private static final UpgradeStoneData.FailOutcomeType KEEP = UpgradeStoneData.FailOutcomeType.KEEP;
    private static final UpgradeStoneData.FailOutcomeType LOSE_LEVEL = UpgradeStoneData.FailOutcomeType.LOSE_LEVEL;
    private static final UpgradeStoneData.FailOutcomeType DESTROY = UpgradeStoneData.FailOutcomeType.DESTROY;

    private UpgradeTableGameTests() {}

    private static UpgradeTableBlockEntity placeTable(GameTestHelper helper) {
        helper.setBlock(TABLE, ModBlocks.UPGRADE_TABLE.get());
        return helper.getBlockEntity(TABLE);
    }

    private static ItemStack swordAt(int level) {
        ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
        UpgradeHelper.setLevel(sword, level);
        return sword;
    }

    /**
     * A random source whose first {@link RandomSource#nextDouble()} makes the roll fail. The
     * helper is re-created with the same seed, so the attempt sees the same first value.
     */
    private static RandomSource randomFailing(double chance) {
        for (long seed = 0; seed < 10_000L; seed++) {
            RandomSource probe = RandomSource.create(seed);
            if (probe.nextDouble() * 100.0D >= chance) {
                return RandomSource.create(seed);
            }
        }
        throw new IllegalStateException("No failing seed for chance " + chance);
    }

    private static RandomSource randomSucceeding(double chance) {
        for (long seed = 0; seed < 10_000L; seed++) {
            RandomSource probe = RandomSource.create(seed);
            if (probe.nextDouble() * 100.0D < chance) {
                return RandomSource.create(seed);
            }
        }
        throw new IllegalStateException("No succeeding seed for chance " + chance);
    }

    /**
     * A random source whose first draw fails and whose second draw selects the requested heaven
     * stone failure outcome.
     */
    private static RandomSource randomFailingWithOutcome(double chance, int level,
            UpgradeStoneData.FailOutcomeType expected) {
        UpgradeStoneData heaven = UpgradeStoneData.getData(ModItems.HEAVEN_STONE.get());
        if (heaven == null) {
            throw new IllegalStateException("Heaven stone data is missing");
        }
        for (long seed = 0; seed < 100_000L; seed++) {
            RandomSource probe = RandomSource.create(seed);
            if (probe.nextDouble() * 100.0D < chance) {
                continue;
            }
            double roll = probe.nextDouble();
            if (heaven.selectFailOutcome(level, roll) == expected) {
                return RandomSource.create(seed);
            }
        }
        throw new IllegalStateException("No failing seed for chance " + chance + " and outcome " + expected);
    }

    // ---------------------------------------------------------------- data map

    @GameTest(template = "empty")
    public static void dataMapLoadsStoneStats(GameTestHelper helper) {
        UpgradeStoneData heaven = UpgradeStoneData.getData(ModItems.HEAVEN_STONE.get());
        helper.assertTrue(heaven != null, "Heaven stone must have data map values");
        helper.assertTrue(heaven.maxLevel() == 30, "Heaven stone must be usable up to +30");
        helper.assertTrue(heaven.getSuccessChanceForLevel(0) == 100.0D,
                "Heaven stone must start at 100 %");
        helper.assertTrue(heaven.getSuccessChanceForLevel(29) == 10.0D,
                "Heaven stone must be at 10 % for +29");
        helper.assertTrue(heaven.failOutcomes().size() == 3,
                "Heaven stone must define three failure outcomes");
        helper.assertTrue(heaven.availableFailOutcomes(3).size() == 2,
                "At +3 only keep and lose_level must be available");
        helper.assertTrue(heaven.availableFailOutcomes(3).stream().noneMatch(outcome -> outcome.outcome() == DESTROY),
                "Destruction must not be available at +3");
        helper.assertTrue(heaven.availableFailOutcomes(4).size() == 3,
                "At +4 all three failure outcomes must be available");

        UpgradeStoneData power = UpgradeStoneData.getData(ModItems.POWER_STONE.get());
        helper.assertTrue(power != null && power.maxLevel() == 15, "Power stone must be usable up to +15");
        helper.assertTrue(power.getSuccessChanceForLevel(7) == 34.0D,
                "Power stone must be at 34 % for +7");
        helper.assertTrue(power.availableFailOutcomes(3).size() == 1
                        && power.availableFailOutcomes(3).get(0).outcome() == KEEP,
                "Power stone at +3 must only keep the item");
        helper.assertTrue(power.availableFailOutcomes(4).size() == 1
                        && power.availableFailOutcomes(4).get(0).outcome() == DESTROY,
                "Power stone at +4 must only destroy the item");

        UpgradeStoneData luck = UpgradeStoneData.getData(ModItems.LUCK_STONE.get());
        helper.assertTrue(luck != null && luck.maxLevel() == 10, "Luck stone must be usable up to +10");
        helper.assertTrue(luck.getSuccessChanceForLevel(9) == 100.0D,
                "Luck stone must be at 100 % for +9");
        helper.assertTrue(luck.availableFailOutcomes(0).size() == 1
                        && luck.availableFailOutcomes(0).get(0).outcome() == KEEP,
                "Luck stone failure must keep the item");

        helper.assertTrue(UpgradeStoneData.getData(Items.DIAMOND_SWORD) == null,
                "A sword must not have upgrade stone data");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void failOutcomeSelection(GameTestHelper helper) {
        UpgradeStoneData heaven = UpgradeStoneData.getData(ModItems.HEAVEN_STONE.get());
        helper.assertTrue(heaven != null, "Heaven stone must have data map values");

        helper.assertTrue(heaven.selectFailOutcome(3, 0.0D) == KEEP,
                "The first third at +3 must be keep");
        helper.assertTrue(heaven.selectFailOutcome(3, 0.5D) == LOSE_LEVEL,
                "The second half at +3 must be lose_level");
        helper.assertTrue(heaven.selectFailOutcome(3, 0.99D) == LOSE_LEVEL,
                "Destruction must be unreachable at +3");

        helper.assertTrue(heaven.selectFailOutcome(4, 0.0D) == KEEP,
                "The first third at +4 must be keep");
        helper.assertTrue(heaven.selectFailOutcome(4, 0.4D) == LOSE_LEVEL,
                "The middle third at +4 must be lose_level");
        helper.assertTrue(heaven.selectFailOutcome(4, 0.9D) == DESTROY,
                "The last third at +4 must be destroy");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void powerFailOutcomeSelection(GameTestHelper helper) {
        UpgradeStoneData power = UpgradeStoneData.getData(ModItems.POWER_STONE.get());
        helper.assertTrue(power != null, "Power stone must have data map values");

        helper.assertTrue(power.selectFailOutcome(3, 0.0D) == KEEP
                        && power.selectFailOutcome(3, 0.99D) == KEEP,
                "Power stone failure at +3 must always keep the item");
        helper.assertTrue(power.selectFailOutcome(4, 0.0D) == DESTROY
                        && power.selectFailOutcome(4, 0.99D) == DESTROY,
                "Power stone failure at +4 must always destroy the item");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void loseLevelAtZeroBecomesKeep(GameTestHelper helper) {
        UpgradeTableHelper.Result result = UpgradeTableHelper.failureResult(0, LOSE_LEVEL);
        helper.assertTrue(result.outcome() == UpgradeTableHelper.Outcome.FAIL_KEEP,
                "A level loss at +0 must become a harmless failure");
        helper.assertTrue(result.newLevel() == 0, "A level loss at +0 must not go below 0");
        helper.succeed();
    }

    // ---------------------------------------------------------------- preview

    @GameTest(template = "empty")
    public static void previewResolvesChanceAndMaxLevel(GameTestHelper helper) {
        ItemStack heaven = new ItemStack(ModItems.HEAVEN_STONE.get());
        ItemStack power = new ItemStack(ModItems.POWER_STONE.get());
        ItemStack luck = new ItemStack(ModItems.LUCK_STONE.get());

        UpgradeTableHelper.Preview atTen = UpgradeTableHelper.preview(swordAt(10), heaven, ItemStack.EMPTY);
        helper.assertTrue(atTen.valid(), "A +10 sword with a heaven stone must be a valid attempt");
        helper.assertTrue(atTen.chance() == 27.27D, "The chance at +10 must be 27.27 %");
        helper.assertTrue(atTen.currentLevel() == 10 && atTen.targetLevel() == 11,
                "The preview must report +10 -> +11");
        helper.assertTrue(atTen.maxLevel() == 30, "The preview must report the stone's max level");

        UpgradeTableHelper.Preview atThree = UpgradeTableHelper.preview(swordAt(3), heaven, ItemStack.EMPTY);
        helper.assertTrue(atThree.chance() == 75.0D, "The chance at +3 must be 75 %");

        UpgradeTableHelper.Preview powerLow = UpgradeTableHelper.preview(swordAt(2), power, ItemStack.EMPTY);
        helper.assertTrue(powerLow.chance() == 46.0D, "The power stone chance at +2 must be 46 %");

        UpgradeTableHelper.Preview powerHigh = UpgradeTableHelper.preview(swordAt(4), power, ItemStack.EMPTY);
        helper.assertTrue(powerHigh.chance() == 42.0D, "The power stone chance at +4 must be 42 %");

        UpgradeTableHelper.Preview luckAtNine = UpgradeTableHelper.preview(swordAt(9), luck, ItemStack.EMPTY);
        helper.assertTrue(luckAtNine.valid() && luckAtNine.chance() == 100.0D
                        && luckAtNine.targetLevel() == 10,
                "A +9 luck stone attempt must be a guaranteed +10");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void previewRejectsInvalidInputs(GameTestHelper helper) {
        ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
        ItemStack heaven = new ItemStack(ModItems.HEAVEN_STONE.get());
        ItemStack power = new ItemStack(ModItems.POWER_STONE.get());
        ItemStack luck = new ItemStack(ModItems.LUCK_STONE.get());

        assertReason(helper, UpgradeTableHelper.preview(ItemStack.EMPTY, heaven, ItemStack.EMPTY),
                UpgradeTableHelper.Reason.NO_ITEM, "empty item slot");
        assertReason(helper, UpgradeTableHelper.preview(new ItemStack(Items.DIRT), heaven, ItemStack.EMPTY),
                UpgradeTableHelper.Reason.NOT_UPGRADABLE, "dirt");
        assertReason(helper, UpgradeTableHelper.preview(sword, ItemStack.EMPTY, ItemStack.EMPTY),
                UpgradeTableHelper.Reason.NO_STONE, "empty stone slot");
        assertReason(helper, UpgradeTableHelper.preview(sword, new ItemStack(Items.DIRT), ItemStack.EMPTY),
                UpgradeTableHelper.Reason.NOT_A_STONE, "dirt as stone");
        assertReason(helper, UpgradeTableHelper.preview(sword, heaven, new ItemStack(Items.DIAMOND)),
                UpgradeTableHelper.Reason.RUNE_NOT_SUPPORTED, "rune slot filled");
        assertReason(helper, UpgradeTableHelper.preview(swordAt(30), heaven, ItemStack.EMPTY),
                UpgradeTableHelper.Reason.MAX_LEVEL, "heaven stone at +30");
        assertReason(helper, UpgradeTableHelper.preview(swordAt(15), power, ItemStack.EMPTY),
                UpgradeTableHelper.Reason.MAX_LEVEL, "power stone at +15");
        assertReason(helper, UpgradeTableHelper.preview(swordAt(10), luck, ItemStack.EMPTY),
                UpgradeTableHelper.Reason.MAX_LEVEL, "luck stone at +10");
        helper.succeed();
    }

    // ---------------------------------------------------------------- rolls

    @GameTest(template = "altar")
    public static void successIncreasesLevelAndConsumesStone(GameTestHelper helper) {
        UpgradeTableBlockEntity table = placeTable(helper);
        table.setItem(UpgradeTableBlockEntity.SLOT_ITEM, new ItemStack(Items.DIAMOND_SWORD));
        table.setItem(UpgradeTableBlockEntity.SLOT_STONE, new ItemStack(ModItems.HEAVEN_STONE.get()));

        helper.assertTrue(table.performUpgrade(randomSucceeding(100.0D)),
                "A +0 heaven stone attempt must be valid");
        helper.assertTrue(UpgradeHelper.getLevel(table.getItem(UpgradeTableBlockEntity.SLOT_ITEM)) == 1,
                "A success must raise the item to +1");
        helper.assertTrue(table.getItem(UpgradeTableBlockEntity.SLOT_STONE).isEmpty(),
                "A success must consume the stone");
        helper.succeed();
    }

    @GameTest(template = "altar")
    public static void failureAtLowLevelKeepsItem(GameTestHelper helper) {
        UpgradeTableBlockEntity table = placeTable(helper);
        table.setItem(UpgradeTableBlockEntity.SLOT_ITEM, swordAt(3));
        table.setItem(UpgradeTableBlockEntity.SLOT_STONE, new ItemStack(ModItems.HEAVEN_STONE.get()));

        helper.assertTrue(table.performUpgrade(randomFailingWithOutcome(75.0D, 3, KEEP)),
                "A +3 heaven stone attempt must be valid");
        helper.assertTrue(UpgradeHelper.getLevel(table.getItem(UpgradeTableBlockEntity.SLOT_ITEM)) == 3,
                "A keep failure at +3 must leave the item at +3");
        helper.assertTrue(table.getItem(UpgradeTableBlockEntity.SLOT_STONE).isEmpty(),
                "A failure must consume the stone");
        helper.succeed();
    }

    @GameTest(template = "altar")
    public static void heavenFailureAtThreeCanLoseLevel(GameTestHelper helper) {
        UpgradeTableBlockEntity table = placeTable(helper);
        table.setItem(UpgradeTableBlockEntity.SLOT_ITEM, swordAt(3));
        table.setItem(UpgradeTableBlockEntity.SLOT_STONE, new ItemStack(ModItems.HEAVEN_STONE.get()));

        helper.assertTrue(table.performUpgrade(randomFailingWithOutcome(75.0D, 3, LOSE_LEVEL)),
                "A +3 heaven stone attempt must be valid");
        helper.assertTrue(UpgradeHelper.getLevel(table.getItem(UpgradeTableBlockEntity.SLOT_ITEM)) == 2,
                "A level-loss failure at +3 must lower the item to +2");
        helper.assertTrue(table.getItem(UpgradeTableBlockEntity.SLOT_STONE).isEmpty(),
                "A level-loss failure must consume the stone");
        helper.succeed();
    }

    @GameTest(template = "altar")
    public static void heavenFailureAtFourCanKeepItem(GameTestHelper helper) {
        UpgradeTableBlockEntity table = placeTable(helper);
        table.setItem(UpgradeTableBlockEntity.SLOT_ITEM, swordAt(4));
        table.setItem(UpgradeTableBlockEntity.SLOT_STONE, new ItemStack(ModItems.HEAVEN_STONE.get()));

        helper.assertTrue(table.performUpgrade(randomFailingWithOutcome(60.0D, 4, KEEP)),
                "A +4 heaven stone attempt must be valid");
        helper.assertTrue(UpgradeHelper.getLevel(table.getItem(UpgradeTableBlockEntity.SLOT_ITEM)) == 4,
                "A keep failure at +4 must leave the item at +4");
        helper.assertTrue(table.getItem(UpgradeTableBlockEntity.SLOT_STONE).isEmpty(),
                "A failure must consume the stone");
        helper.succeed();
    }

    @GameTest(template = "altar")
    public static void heavenFailureAtFourCanLoseLevel(GameTestHelper helper) {
        UpgradeTableBlockEntity table = placeTable(helper);
        table.setItem(UpgradeTableBlockEntity.SLOT_ITEM, swordAt(4));
        table.setItem(UpgradeTableBlockEntity.SLOT_STONE, new ItemStack(ModItems.HEAVEN_STONE.get()));

        helper.assertTrue(table.performUpgrade(randomFailingWithOutcome(60.0D, 4, LOSE_LEVEL)),
                "A +4 heaven stone attempt must be valid");
        helper.assertTrue(UpgradeHelper.getLevel(table.getItem(UpgradeTableBlockEntity.SLOT_ITEM)) == 3,
                "A level-loss failure at +4 must lower the item to +3");
        helper.assertTrue(table.getItem(UpgradeTableBlockEntity.SLOT_STONE).isEmpty(),
                "A level-loss failure must consume the stone");
        helper.succeed();
    }

    @GameTest(template = "altar")
    public static void heavenFailureAtFourCanDestroyItem(GameTestHelper helper) {
        UpgradeTableBlockEntity table = placeTable(helper);
        table.setItem(UpgradeTableBlockEntity.SLOT_ITEM, swordAt(4));
        table.setItem(UpgradeTableBlockEntity.SLOT_STONE, new ItemStack(ModItems.HEAVEN_STONE.get()));

        helper.assertTrue(table.performUpgrade(randomFailingWithOutcome(60.0D, 4, DESTROY)),
                "A +4 heaven stone attempt must be valid");
        helper.assertTrue(table.getItem(UpgradeTableBlockEntity.SLOT_ITEM).isEmpty(),
                "A destroying failure at +4 must remove the item");
        helper.assertTrue(table.getItem(UpgradeTableBlockEntity.SLOT_STONE).isEmpty(),
                "A destroying failure must consume the stone");
        helper.succeed();
    }

    @GameTest(template = "altar")
    public static void stoneStackConsumedOnePerAttempt(GameTestHelper helper) {
        UpgradeTableBlockEntity table = placeTable(helper);
        table.setItem(UpgradeTableBlockEntity.SLOT_ITEM, new ItemStack(Items.DIAMOND_SWORD));
        table.setItem(UpgradeTableBlockEntity.SLOT_STONE, new ItemStack(ModItems.HEAVEN_STONE.get(), 3));

        helper.assertTrue(table.performUpgrade(randomSucceeding(100.0D)),
                "The first attempt must be valid");
        helper.assertTrue(UpgradeHelper.getLevel(table.getItem(UpgradeTableBlockEntity.SLOT_ITEM)) == 1,
                "The first success must raise the item to +1");
        helper.assertTrue(table.getItem(UpgradeTableBlockEntity.SLOT_STONE).getCount() == 2,
                "The first attempt must consume exactly one stone");

        helper.assertTrue(table.performUpgrade(randomSucceeding(100.0D)),
                "The second attempt must be valid");
        helper.assertTrue(UpgradeHelper.getLevel(table.getItem(UpgradeTableBlockEntity.SLOT_ITEM)) == 2,
                "The second success must raise the item to +2");
        helper.assertTrue(table.getItem(UpgradeTableBlockEntity.SLOT_STONE).getCount() == 1,
                "The second attempt must consume exactly one stone");

        helper.assertTrue(table.performUpgrade(randomSucceeding(100.0D)),
                "The third attempt must be valid");
        helper.assertTrue(UpgradeHelper.getLevel(table.getItem(UpgradeTableBlockEntity.SLOT_ITEM)) == 3,
                "The third success must raise the item to +3");
        helper.assertTrue(table.getItem(UpgradeTableBlockEntity.SLOT_STONE).isEmpty(),
                "The last stone must empty the slot");
        helper.succeed();
    }

    @GameTest(template = "altar")
    public static void powerStoneRules(GameTestHelper helper) {
        UpgradeTableBlockEntity table = placeTable(helper);

        table.setItem(UpgradeTableBlockEntity.SLOT_ITEM, swordAt(2));
        table.setItem(UpgradeTableBlockEntity.SLOT_STONE, new ItemStack(ModItems.POWER_STONE.get()));
        helper.assertTrue(table.performUpgrade(randomFailing(46.0D)),
                "A +2 power stone attempt must be valid");
        helper.assertTrue(UpgradeHelper.getLevel(table.getItem(UpgradeTableBlockEntity.SLOT_ITEM)) == 2,
                "A +2 power stone failure must keep the item");

        table.setItem(UpgradeTableBlockEntity.SLOT_ITEM, swordAt(4));
        table.setItem(UpgradeTableBlockEntity.SLOT_STONE, new ItemStack(ModItems.POWER_STONE.get()));
        helper.assertTrue(table.performUpgrade(randomFailing(42.0D)),
                "A +4 power stone attempt must be valid");
        helper.assertTrue(table.getItem(UpgradeTableBlockEntity.SLOT_ITEM).isEmpty(),
                "A +4 power stone failure must destroy the item");

        table.setItem(UpgradeTableBlockEntity.SLOT_ITEM, swordAt(15));
        table.setItem(UpgradeTableBlockEntity.SLOT_STONE, new ItemStack(ModItems.POWER_STONE.get()));
        helper.assertFalse(table.performUpgrade(RandomSource.create(0L)),
                "A +15 power stone attempt must be rejected");
        helper.assertTrue(UpgradeHelper.getLevel(table.getItem(UpgradeTableBlockEntity.SLOT_ITEM)) == 15,
                "A rejected attempt must not change the item");
        helper.assertTrue(table.getItem(UpgradeTableBlockEntity.SLOT_STONE).is(ModItems.POWER_STONE.get()),
                "A rejected attempt must not consume the stone");
        helper.succeed();
    }

    @GameTest(template = "altar")
    public static void luckStoneAlwaysSucceedsUntilTen(GameTestHelper helper) {
        UpgradeTableBlockEntity table = placeTable(helper);

        table.setItem(UpgradeTableBlockEntity.SLOT_ITEM, swordAt(9));
        table.setItem(UpgradeTableBlockEntity.SLOT_STONE, new ItemStack(ModItems.LUCK_STONE.get()));
        helper.assertTrue(table.performUpgrade(RandomSource.create(0L)),
                "A +9 luck stone attempt must be valid");
        helper.assertTrue(UpgradeHelper.getLevel(table.getItem(UpgradeTableBlockEntity.SLOT_ITEM)) == 10,
                "A luck stone must raise +9 to +10");
        helper.assertTrue(table.getItem(UpgradeTableBlockEntity.SLOT_STONE).isEmpty(),
                "A successful luck stone must be consumed");

        table.setItem(UpgradeTableBlockEntity.SLOT_ITEM, swordAt(10));
        table.setItem(UpgradeTableBlockEntity.SLOT_STONE, new ItemStack(ModItems.LUCK_STONE.get()));
        helper.assertFalse(table.performUpgrade(RandomSource.create(0L)),
                "A +10 luck stone attempt must be rejected");
        helper.assertTrue(table.getItem(UpgradeTableBlockEntity.SLOT_STONE).is(ModItems.LUCK_STONE.get()),
                "A rejected luck stone must not be consumed");
        helper.succeed();
    }

    // ---------------------------------------------------------------- menu

    @GameTest(template = "altar")
    public static void menuButtonStartsUpgrade(GameTestHelper helper) {
        UpgradeTableBlockEntity table = placeTable(helper);
        table.setItem(UpgradeTableBlockEntity.SLOT_ITEM, new ItemStack(Items.DIAMOND_SWORD));
        table.setItem(UpgradeTableBlockEntity.SLOT_STONE, new ItemStack(ModItems.HEAVEN_STONE.get()));

        ServerPlayer player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "himmelsstein-upgrade-table-player"),
                ClientInformation.createDefault());
        AbstractContainerMenu menu = table.createMenu(1, player.getInventory(), player);
        helper.assertTrue(menu instanceof UpgradeTableMenu, "The table must open the upgrade table menu");
        helper.assertFalse(menu.clickMenuButton(player, 7), "Unknown button ids must be ignored");
        helper.assertTrue(menu.clickMenuButton(player, UpgradeTableMenu.BUTTON_UPGRADE),
                "The upgrade button must start a +0 heaven stone attempt");
        helper.assertTrue(UpgradeHelper.getLevel(table.getItem(UpgradeTableBlockEntity.SLOT_ITEM)) == 1,
                "The menu button must raise the item to +1");
        helper.succeed();
    }

    @GameTest(template = "altar")
    public static void stoneSlotAcceptsStacks(GameTestHelper helper) {
        UpgradeTableBlockEntity table = placeTable(helper);
        ServerPlayer player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "himmelsstein-upgrade-table-stacks"),
                ClientInformation.createDefault());
        AbstractContainerMenu menu = table.createMenu(1, player.getInventory(), player);

        helper.assertTrue(menu.getSlot(UpgradeTableBlockEntity.SLOT_STONE)
                        .getMaxStackSize(new ItemStack(ModItems.HEAVEN_STONE.get())) == 64,
                "The stone slot must accept a full stone stack, got "
                        + menu.getSlot(UpgradeTableBlockEntity.SLOT_STONE)
                                .getMaxStackSize(new ItemStack(ModItems.HEAVEN_STONE.get())));
        helper.assertTrue(menu.getSlot(UpgradeTableBlockEntity.SLOT_ITEM).getMaxStackSize() == 1,
                "The item slot must stay limited to one item");
        helper.assertTrue(menu.getSlot(UpgradeTableBlockEntity.SLOT_RUNE).getMaxStackSize() == 1,
                "The rune slot must stay limited to one rune");
        helper.succeed();
    }

    @GameTest(template = "altar")
    public static void slotRules(GameTestHelper helper) {
        UpgradeTableBlockEntity table = placeTable(helper);
        ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
        ItemStack heaven = new ItemStack(ModItems.HEAVEN_STONE.get());

        helper.assertTrue(table.canPlaceItem(UpgradeTableBlockEntity.SLOT_STONE, heaven),
                "The stone slot must accept upgrade stones");
        helper.assertFalse(table.canPlaceItem(UpgradeTableBlockEntity.SLOT_STONE, sword),
                "The stone slot must reject weapons");
        helper.assertTrue(table.canPlaceItem(UpgradeTableBlockEntity.SLOT_ITEM, sword),
                "The item slot must accept upgradable weapons");
        helper.assertFalse(table.canPlaceItem(UpgradeTableBlockEntity.SLOT_ITEM, heaven),
                "The item slot must reject stones");
        helper.assertFalse(table.canPlaceItem(UpgradeTableBlockEntity.SLOT_RUNE, new ItemStack(Items.DIAMOND)),
                "The rune slot must stay empty until runes exist");
        helper.succeed();
    }

    @GameTest(template = "altar")
    public static void updateTagSyncsSlotContents(GameTestHelper helper) {
        UpgradeTableBlockEntity table = placeTable(helper);
        table.setItem(UpgradeTableBlockEntity.SLOT_ITEM, swordAt(5));

        CompoundTag tag = table.getUpdateTag(helper.getLevel().registryAccess());
        helper.assertTrue(tag.contains("Items"), "The update tag must carry the slot contents");
        helper.assertTrue(table.getUpdatePacket() != null, "The table must send block updates to clients");
        helper.succeed();
    }

    // ---------------------------------------------------------------- helpers

    private static void assertReason(GameTestHelper helper, UpgradeTableHelper.Preview preview,
            UpgradeTableHelper.Reason expected, String label) {
        helper.assertFalse(preview.valid(), label + " must not be a valid attempt");
        helper.assertTrue(preview.reason() == expected,
                label + " must be " + expected + " but was " + preview.reason());
    }
}
