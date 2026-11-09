package net.tobiasfiller.himmelsstein.core.gametest;

import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;
import net.tobiasfiller.himmelsstein.Himmelsstein;
import net.tobiasfiller.himmelsstein.common.block.StoneAltarBlock;
import net.tobiasfiller.himmelsstein.common.blockentity.StoneAltarBlockEntity;
import net.tobiasfiller.himmelsstein.common.menu.StoneAltarMenu;
import net.tobiasfiller.himmelsstein.core.event.AltarYieldReloadListener;
import net.tobiasfiller.himmelsstein.core.registry.ModBlocks;
import net.tobiasfiller.himmelsstein.core.registry.ModItems;
import net.tobiasfiller.himmelsstein.core.utility.AltarYieldData;

/**
 * Stone altar behaviour: yield tiers from the default datapack file, scrapping, hopper sides,
 * comparator output and redstone trigger.
 */
@GameTestHolder(Himmelsstein.MODID)
@PrefixGameTestTemplate(false)
public final class StoneAltarGameTests {
    private static final BlockPos ALTAR = new BlockPos(1, 1, 1);
    private static final int AFTER_SCRAP = StoneAltarBlockEntity.SCRAP_TIME + 2;

    private StoneAltarGameTests() {}

    private static StoneAltarBlockEntity placeAltar(GameTestHelper helper) {
        helper.setBlock(ALTAR, ModBlocks.STONE_ALTAR.get());
        return helper.getBlockEntity(ALTAR);
    }

    /** Diamond sword with exactly {@code remaining} durability points left. */
    private static ItemStack swordWithRemaining(int remaining) {
        ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
        sword.setDamageValue(sword.getMaxDamage() - remaining);
        return sword;
    }

    // ---------------------------------------------------------------- placement

    @GameTest(template = "altar")
    public static void placementFacesPlayer(GameTestHelper helper) {
        ServerPlayer player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "himmelsstein-altar-placer"), ClientInformation.createDefault());
        BlockPos support = helper.absolutePos(ALTAR.below());
        // yRot 0 = looking south, 90 = west, 180 = north, 270 = east
        Direction[] looking = {Direction.SOUTH, Direction.WEST, Direction.NORTH, Direction.EAST};
        for (int i = 0; i < looking.length; i++) {
            helper.setBlock(ALTAR.below(), Blocks.STONE);
            helper.setBlock(ALTAR, Blocks.AIR);
            player.setYRot(i * 90.0F);
            player.setXRot(60.0F);
            ItemStack stack = new ItemStack(ModItems.STONE_ALTAR.get());
            player.setItemInHand(InteractionHand.MAIN_HAND, stack);
            BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(support).add(0.0D, 0.5D, 0.0D), Direction.UP,
                    support, false);
            ((BlockItem) stack.getItem()).place(new BlockPlaceContext(helper.getLevel(), player,
                    InteractionHand.MAIN_HAND, stack, hit));
            BlockState state = helper.getBlockState(ALTAR);
            helper.assertTrue(state.is(ModBlocks.STONE_ALTAR.get()), "Altar must be placed");
            helper.assertTrue(state.getValue(StoneAltarBlock.FACING) == looking[i].getOpposite(),
                    "Looking " + looking[i] + " must face " + looking[i].getOpposite() + ", got "
                            + state.getValue(StoneAltarBlock.FACING));
        }
        helper.succeed();
    }

    // ---------------------------------------------------------------- yield data

    @GameTest(template = "empty")
    public static void defaultYieldTiers(GameTestHelper helper) {
        AltarYieldData yield = AltarYieldReloadListener.current();
        int[][] power = {{0, 0}, {99, 0}, {100, 1}, {499, 1}, {500, 2}, {999, 2}, {1000, 3}, {2031, 3}};
        for (int[] row : power) {
            helper.assertTrue(yield.powerStonesFor(row[0]) == row[1],
                    row[0] + " remaining durability must give " + row[1] + " power stones");
        }
        double[][] heaven = {{249, 0.0D}, {250, 0.02D}, {1499, 0.02D}, {1500, 0.1D}, {1999, 0.1D},
                {2000, 0.2D}, {2031, 0.2D}};
        for (double[] row : heaven) {
            helper.assertTrue(yield.heavenChanceFor((int) row[0]) == row[1],
                    (int) row[0] + " remaining durability must give heaven chance " + row[1]);
        }
        helper.assertTrue(yield.maxPowerStones() == 3, "Default yield must cap at 3 power stones");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void remainingDurability(GameTestHelper helper) {
        helper.assertTrue(AltarYieldData.remainingDurability(new ItemStack(Items.DIRT)) == 0,
                "Non-damageable items have no durability");
        helper.assertTrue(AltarYieldData.remainingDurability(swordWithRemaining(1500)) == 1500,
                "Remaining durability is max damage minus damage");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void heavenRollBoundaries(GameTestHelper helper) {
        RandomSource random = RandomSource.create(42L);
        AltarYieldData always = new AltarYieldData(List.of(), List.of(new AltarYieldData.HeavenTier(0, 1.0D)));
        AltarYieldData never = new AltarYieldData(List.of(), List.of(new AltarYieldData.HeavenTier(0, 0.0D)));
        ItemStack sword = swordWithRemaining(10);
        for (int i = 0; i < 100; i++) {
            helper.assertTrue(always.roll(sword, random).heavenStone(), "Chance 1.0 must always roll a heaven stone");
            helper.assertFalse(never.roll(sword, random).heavenStone(), "Chance 0.0 must never roll a heaven stone");
        }
        helper.succeed();
    }

    // ---------------------------------------------------------------- scrapping

    @GameTest(template = "altar")
    public static void scrapProducesPowerStones(GameTestHelper helper) {
        StoneAltarBlockEntity altar = placeAltar(helper);
        altar.setItem(StoneAltarBlockEntity.SLOT_INPUT, swordWithRemaining(1200));
        helper.assertTrue(altar.startScrapping(), "Undamaged sword must start scrapping");
        helper.assertTrue(altar.isScrapping(), "Altar must report scrapping");
        helper.assertFalse(altar.canPlaceItem(StoneAltarBlockEntity.SLOT_INPUT, new ItemStack(Items.IRON_SWORD)),
                "Input must be locked while scrapping");

        helper.runAfterDelay(AFTER_SCRAP, () -> {
            helper.assertFalse(altar.isScrapping(), "Scrapping must finish after SCRAP_TIME ticks");
            helper.assertTrue(altar.getItem(StoneAltarBlockEntity.SLOT_INPUT).isEmpty(), "Input must be consumed");
            ItemStack power = altar.getItem(StoneAltarBlockEntity.SLOT_POWER);
            helper.assertTrue(power.is(ModItems.POWER_STONE.get()) && power.getCount() == 3,
                    "1200 remaining durability must give 3 power stones, got " + power);
            ItemStack heaven = altar.getItem(StoneAltarBlockEntity.SLOT_HEAVEN);
            helper.assertTrue(heaven.isEmpty() || heaven.is(ModItems.HEAVEN_STONE.get()) && heaven.getCount() == 1,
                    "Heaven slot may only hold a single heaven stone, got " + heaven);
            helper.succeed();
        });
    }

    @GameTest(template = "altar")
    public static void lowDurabilityYieldsNothing(GameTestHelper helper) {
        StoneAltarBlockEntity altar = placeAltar(helper);
        altar.setItem(StoneAltarBlockEntity.SLOT_INPUT, swordWithRemaining(50));
        helper.assertTrue(altar.startScrapping(), "Worn sword must still be sacrificable");

        helper.runAfterDelay(AFTER_SCRAP, () -> {
            helper.assertTrue(altar.getItem(StoneAltarBlockEntity.SLOT_INPUT).isEmpty(), "Worn item must be destroyed");
            helper.assertTrue(altar.getItem(StoneAltarBlockEntity.SLOT_POWER).isEmpty(), "No power stones below 100");
            helper.assertTrue(altar.getItem(StoneAltarBlockEntity.SLOT_HEAVEN).isEmpty(), "No heaven stone below 250");
            helper.succeed();
        });
    }

    @GameTest(template = "altar")
    public static void rejectsNonDamageableItems(GameTestHelper helper) {
        StoneAltarBlockEntity altar = placeAltar(helper);
        helper.assertFalse(altar.canPlaceItem(StoneAltarBlockEntity.SLOT_INPUT, new ItemStack(Items.DIRT)),
                "Dirt must not be accepted");
        helper.assertTrue(altar.canPlaceItem(StoneAltarBlockEntity.SLOT_INPUT, new ItemStack(Items.IRON_CHESTPLATE)),
                "Armour has durability and must be accepted");
        helper.assertFalse(altar.startScrapping(), "Empty altar must not start scrapping");
        altar.setItem(StoneAltarBlockEntity.SLOT_INPUT, new ItemStack(Items.DIRT));
        helper.assertFalse(altar.startScrapping(), "Non-damageable input must not start scrapping");
        helper.succeed();
    }

    @GameTest(template = "altar")
    public static void fullOutputBlocksScrapping(GameTestHelper helper) {
        StoneAltarBlockEntity altar = placeAltar(helper);
        altar.setItem(StoneAltarBlockEntity.SLOT_INPUT, swordWithRemaining(1200));
        altar.setItem(StoneAltarBlockEntity.SLOT_POWER, new ItemStack(ModItems.POWER_STONE.get(), 62));
        helper.assertFalse(altar.startScrapping(), "62 + 3 power stones do not fit, scrapping must not start");
        altar.setItem(StoneAltarBlockEntity.SLOT_POWER, new ItemStack(ModItems.POWER_STONE.get(), 61));
        helper.assertTrue(altar.startScrapping(), "61 + 3 power stones fit, scrapping must start");
        helper.succeed();
    }

    @GameTest(template = "altar")
    public static void menuButtonStartsScrapping(GameTestHelper helper) {
        StoneAltarBlockEntity altar = placeAltar(helper);
        altar.setItem(StoneAltarBlockEntity.SLOT_INPUT, swordWithRemaining(1200));
        ServerPlayer player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "himmelsstein-altar-player"), ClientInformation.createDefault());
        AbstractContainerMenu menu = altar.createMenu(1, player.getInventory(), player);
        helper.assertTrue(menu instanceof StoneAltarMenu, "Altar must open the stone altar menu");
        helper.assertFalse(menu.clickMenuButton(player, 7), "Unknown button ids must be ignored");
        helper.assertTrue(menu.clickMenuButton(player, StoneAltarMenu.BUTTON_SCRAP), "Scrap button must start scrapping");
        helper.assertTrue(altar.isScrapping(), "Altar must be scrapping after the button click");
        helper.succeed();
    }

    // ---------------------------------------------------------------- automation

    @GameTest(template = "altar")
    public static void hopperSides(GameTestHelper helper) {
        StoneAltarBlockEntity altar = placeAltar(helper);
        BlockPos pos = helper.absolutePos(ALTAR);
        IItemHandler top = helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, pos, Direction.UP);
        IItemHandler side = helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, pos, Direction.NORTH);
        IItemHandler bottom = helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, pos, Direction.DOWN);
        helper.assertTrue(top != null && side != null && bottom != null, "Altar must expose an item handler");

        helper.assertFalse(top.insertItem(0, new ItemStack(Items.DIRT), false).isEmpty(), "Top must reject dirt");
        helper.assertTrue(side.insertItem(0, new ItemStack(Items.IRON_SWORD), false).isEmpty(),
                "Side must accept a damageable item");
        helper.assertFalse(top.insertItem(0, new ItemStack(Items.IRON_SWORD), false).isEmpty(),
                "Occupied input must reject a second item");
        helper.assertTrue(top.extractItem(0, 1, false).isEmpty(), "Input must not be extractable from the top");
        for (int slot = 0; slot < bottom.getSlots(); slot++) {
            helper.assertFalse(bottom.insertItem(slot, new ItemStack(Items.IRON_SWORD), true).isEmpty(),
                    "Bottom must not accept items");
        }

        altar.setItem(StoneAltarBlockEntity.SLOT_POWER, new ItemStack(ModItems.POWER_STONE.get(), 3));
        ItemStack extracted = ItemStack.EMPTY;
        for (int slot = 0; slot < bottom.getSlots() && extracted.isEmpty(); slot++) {
            extracted = bottom.extractItem(slot, 64, false);
        }
        helper.assertTrue(extracted.is(ModItems.POWER_STONE.get()) && extracted.getCount() == 3,
                "Power stones must be extractable from below, got " + extracted);
        helper.assertTrue(altar.hasInput(), "Extracting from below must leave the input untouched");
        helper.succeed();
    }

    @GameTest(template = "altar")
    public static void comparatorReportsInput(GameTestHelper helper) {
        StoneAltarBlockEntity altar = placeAltar(helper);
        BlockPos pos = helper.absolutePos(ALTAR);
        BlockState state = helper.getLevel().getBlockState(pos);
        helper.assertTrue(state.getAnalogOutputSignal(helper.getLevel(), pos) == 0, "Empty altar must output 0");
        altar.setItem(StoneAltarBlockEntity.SLOT_INPUT, new ItemStack(Items.IRON_SWORD));
        helper.assertTrue(state.getAnalogOutputSignal(helper.getLevel(), pos) == 15, "Filled altar must output 15");
        helper.succeed();
    }

    @GameTest(template = "altar")
    public static void redstonePulseStartsScrapping(GameTestHelper helper) {
        StoneAltarBlockEntity altar = placeAltar(helper);
        altar.setItem(StoneAltarBlockEntity.SLOT_INPUT, swordWithRemaining(600));
        helper.setBlock(ALTAR.west(), Blocks.REDSTONE_BLOCK);
        helper.assertTrue(altar.isScrapping(), "Redstone pulse must start scrapping");

        helper.runAfterDelay(AFTER_SCRAP, () -> {
            helper.assertTrue(altar.getItem(StoneAltarBlockEntity.SLOT_INPUT).isEmpty(), "Input must be consumed");
            helper.assertTrue(altar.getItem(StoneAltarBlockEntity.SLOT_POWER).getCount() == 2,
                    "600 remaining durability must give 2 power stones");
            helper.succeed();
        });
    }

    @GameTest(template = "altar")
    public static void constantSignalScrapsEveryArrivingItem(GameTestHelper helper) {
        StoneAltarBlockEntity altar = placeAltar(helper);
        helper.setBlock(ALTAR.west(), Blocks.REDSTONE_BLOCK);
        helper.assertFalse(altar.isScrapping(), "Powered empty altar must idle");

        altar.setItem(StoneAltarBlockEntity.SLOT_INPUT, swordWithRemaining(600));
        helper.runAfterDelay(2, () -> helper.assertTrue(altar.isScrapping(),
                "Item arriving at a powered altar must be scrapped without a new pulse"));
        helper.runAfterDelay(AFTER_SCRAP + 1, () -> {
            helper.assertFalse(altar.hasInput(), "First item must be consumed");
            altar.setItem(StoneAltarBlockEntity.SLOT_INPUT, swordWithRemaining(600));
        });
        helper.runAfterDelay(2 * AFTER_SCRAP + 4, () -> {
            helper.assertFalse(altar.hasInput(), "Second item must be consumed while the signal stays on");
            helper.assertTrue(altar.getItem(StoneAltarBlockEntity.SLOT_POWER).getCount() == 4,
                    "Two items with 600 remaining durability must give 4 power stones");
            helper.succeed();
        });
    }

    @GameTest(template = "altar")
    public static void unpoweredAltarDoesNotAutoScrap(GameTestHelper helper) {
        StoneAltarBlockEntity altar = placeAltar(helper);
        altar.setItem(StoneAltarBlockEntity.SLOT_INPUT, swordWithRemaining(600));
        helper.runAfterDelay(5, () -> {
            helper.assertFalse(altar.isScrapping(), "Without redstone the altar must wait for a click");
            helper.succeed();
        });
    }

    @GameTest(template = "altar")
    public static void hopperBelowPullsStones(GameTestHelper helper) {
        helper.setBlock(ALTAR.below(), Blocks.HOPPER);
        StoneAltarBlockEntity altar = placeAltar(helper);
        altar.setItem(StoneAltarBlockEntity.SLOT_POWER, new ItemStack(ModItems.POWER_STONE.get(), 2));
        altar.setItem(StoneAltarBlockEntity.SLOT_HEAVEN, new ItemStack(ModItems.HEAVEN_STONE.get(), 1));
        altar.setItem(StoneAltarBlockEntity.SLOT_INPUT, new ItemStack(Items.IRON_SWORD));
        helper.runAfterDelay(60, () -> {
            HopperBlockEntity hopper = helper.getBlockEntity(ALTAR.below());
            int power = 0;
            int heaven = 0;
            int swords = 0;
            for (int i = 0; i < hopper.getContainerSize(); i++) {
                ItemStack stack = hopper.getItem(i);
                if (stack.is(ModItems.POWER_STONE.get())) power += stack.getCount();
                if (stack.is(ModItems.HEAVEN_STONE.get())) heaven += stack.getCount();
                if (stack.is(Items.IRON_SWORD)) swords += stack.getCount();
            }
            helper.assertTrue(power == 2 && heaven == 1,
                    "Hopper below must pull all stones, got power=" + power + " heaven=" + heaven);
            helper.assertTrue(swords == 0 && altar.hasInput(), "Hopper must not pull the input item");
            helper.succeed();
        });
    }
}
