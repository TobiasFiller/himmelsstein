package net.tobiasfiller.himmelsstein.core.gametest;

import java.util.List;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.tobiasfiller.himmelsstein.Himmelsstein;
import net.tobiasfiller.himmelsstein.core.event.MonsterDropReloadListener;
import net.tobiasfiller.himmelsstein.core.registry.ModItems;
import net.tobiasfiller.himmelsstein.core.utility.MonsterDropData;

/**
 * Monster drop behaviour: the default datapack rules, the max-health thresholds and the chance
 * boundaries.
 */
@GameTestHolder(Himmelsstein.MODID)
@PrefixGameTestTemplate(false)
public final class MonsterDropGameTests {
    private MonsterDropGameTests() {}

    private static LivingEntity zombieWithMaxHealth(GameTestHelper helper, double maxHealth) {
        LivingEntity entity = EntityType.ZOMBIE.create(helper.getLevel());
        if (entity == null) {
            throw new IllegalStateException("Could not create a zombie");
        }
        entity.getAttribute(Attributes.MAX_HEALTH).setBaseValue(maxHealth);
        return entity;
    }

    private static MonsterDropData.DropRule ruleFor(MonsterDropData data, Item item) {
        return data.drops().stream().filter(rule -> rule.item() == item).findFirst().orElse(null);
    }

    private static boolean contains(List<ItemStack> stacks, Item item) {
        return stacks.stream().anyMatch(stack -> stack.is(item));
    }

    private static void assertDrops(GameTestHelper helper, List<ItemStack> drops, boolean heaven, boolean luck) {
        helper.assertTrue(contains(drops, ModItems.HEAVEN_STONE.get()) == heaven,
                "Heaven stone drop must be " + heaven + ", got " + drops);
        helper.assertTrue(contains(drops, ModItems.LUCK_STONE.get()) == luck,
                "Luck stone drop must be " + luck + ", got " + drops);
    }

    // ---------------------------------------------------------------- datapack

    @GameTest(template = "empty")
    public static void defaultDropsLoad(GameTestHelper helper) {
        MonsterDropData data = MonsterDropReloadListener.current();
        helper.assertTrue(data.drops().size() == 2, "Default monster drops must contain two rules");

        MonsterDropData.DropRule heaven = ruleFor(data, ModItems.HEAVEN_STONE.get());
        helper.assertTrue(heaven != null, "The default drops must contain a heaven stone rule");
        helper.assertTrue(heaven.minHealth() == 20.0D, "Heaven stones must require 10 hearts");
        helper.assertTrue(heaven.chance() == 0.1D, "Heaven stones must drop with 10 %");

        MonsterDropData.DropRule luck = ruleFor(data, ModItems.LUCK_STONE.get());
        helper.assertTrue(luck != null, "The default drops must contain a luck stone rule");
        helper.assertTrue(luck.minHealth() == 100.0D, "Luck stones must require 50 hearts");
        helper.assertTrue(luck.chance() == 0.05D, "Luck stones must drop with 5 %");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void defaultDropsIgnoreLowHealthMonsters(GameTestHelper helper) {
        MonsterDropData data = MonsterDropReloadListener.current();
        LivingEntity mob = zombieWithMaxHealth(helper, 19.99D);
        helper.assertTrue(data.roll(mob, RandomSource.create(0L)).isEmpty(),
                "Below 20 max health no default drop may roll");

        mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(99.99D);
        helper.assertTrue(data.roll(mob, RandomSource.create(0L)).stream()
                        .noneMatch(stack -> stack.is(ModItems.LUCK_STONE.get())),
                "Below 100 max health no luck stone may roll");
        helper.succeed();
    }

    // ---------------------------------------------------------------- thresholds

    @GameTest(template = "empty")
    public static void healthThresholds(GameTestHelper helper) {
        MonsterDropData always = new MonsterDropData(List.of(
                new MonsterDropData.DropRule(ModItems.HEAVEN_STONE.get(), 20.0D, 1.0D),
                new MonsterDropData.DropRule(ModItems.LUCK_STONE.get(), 100.0D, 1.0D)));
        LivingEntity mob = zombieWithMaxHealth(helper, 19.99D);

        assertDrops(helper, always.roll(mob, RandomSource.create(0L)), false, false);

        mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(20.0D);
        assertDrops(helper, always.roll(mob, RandomSource.create(0L)), true, false);

        mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(99.99D);
        assertDrops(helper, always.roll(mob, RandomSource.create(0L)), true, false);

        mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100.0D);
        assertDrops(helper, always.roll(mob, RandomSource.create(0L)), true, true);

        mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(300.0D);
        assertDrops(helper, always.roll(mob, RandomSource.create(0L)), true, true);
        helper.succeed();
    }

    // ---------------------------------------------------------------- chance

    @GameTest(template = "empty")
    public static void chanceBoundaries(GameTestHelper helper) {
        LivingEntity mob = zombieWithMaxHealth(helper, 1000.0D);
        MonsterDropData always = new MonsterDropData(List.of(
                new MonsterDropData.DropRule(ModItems.HEAVEN_STONE.get(), 0.0D, 1.0D)));
        MonsterDropData never = new MonsterDropData(List.of(
                new MonsterDropData.DropRule(ModItems.HEAVEN_STONE.get(), 0.0D, 0.0D)));
        RandomSource random = RandomSource.create(42L);

        for (int i = 0; i < 200; i++) {
            helper.assertTrue(always.roll(mob, random).size() == 1, "Chance 1.0 must always drop");
            helper.assertTrue(never.roll(mob, random).isEmpty(), "Chance 0.0 must never drop");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void rulesRollIndependently(GameTestHelper helper) {
        LivingEntity mob = zombieWithMaxHealth(helper, 100.0D);
        MonsterDropData data = MonsterDropData.EMPTY;
        helper.assertTrue(data.roll(mob, RandomSource.create(0L)).isEmpty(), "No rules must drop nothing");

        data = new MonsterDropData(List.of(
                new MonsterDropData.DropRule(ModItems.HEAVEN_STONE.get(), 20.0D, 1.0D),
                new MonsterDropData.DropRule(ModItems.LUCK_STONE.get(), 100.0D, 1.0D)));
        List<ItemStack> drops = data.roll(mob, RandomSource.create(0L));
        helper.assertTrue(drops.size() == 2, "Both qualifying rules must drop one stack each, got " + drops);
        assertDrops(helper, drops, true, true);
        helper.succeed();
    }
}
