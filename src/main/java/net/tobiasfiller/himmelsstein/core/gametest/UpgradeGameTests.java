package net.tobiasfiller.himmelsstein.core.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.AnvilUpdateEvent;
import net.neoforged.neoforge.event.ItemAttributeModifierEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AnvilRepairEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.tobiasfiller.himmelsstein.Himmelsstein;
import net.tobiasfiller.himmelsstein.core.command.WeaponUpgradeCommand;
import net.tobiasfiller.himmelsstein.core.event.UpgradeEvents;
import net.tobiasfiller.himmelsstein.core.registry.ModDataComponents;
import net.tobiasfiller.himmelsstein.core.registry.ModItems;
import net.tobiasfiller.himmelsstein.core.utility.UpgradeHelper;

/**
 * Behavioural tests for the upgrade core (M1-M5 and M7).
 *
 * <p>All tests use the tiny {@code himmelsstein:empty} structure, so they do not need a visible
 * build area. Most behaviour is exercised by calling the real event handlers directly; that keeps
 * the assertions deterministic and independent of random damage rolls.</p>
 */
@GameTestHolder(Himmelsstein.MODID)
@PrefixGameTestTemplate(false)
public final class UpgradeGameTests {
    private static final double EPSILON = 1.0E-4D;

    private UpgradeGameTests() {}

    // ---------------------------------------------------------------- M1 data model

    @GameTest(template = "empty")
    public static void levelSetGetRemove(GameTestHelper helper) {
        ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
        helper.assertTrue(UpgradeHelper.getLevel(sword) == 0, "Fresh stack must start at +0");

        UpgradeHelper.setLevel(sword, 10);
        helper.assertTrue(UpgradeHelper.getLevel(sword) == 10, "setLevel(10) must store 10");
        helper.assertTrue(sword.has(ModDataComponents.UPGRADE_LEVEL.get()), "Level 10 must be present");

        UpgradeHelper.setLevel(sword, 0);
        helper.assertFalse(sword.has(ModDataComponents.UPGRADE_LEVEL.get()),
                "Level 0 must remove the component");

        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void levelIsClamped(GameTestHelper helper) {
        ItemStack high = new ItemStack(Items.DIAMOND_SWORD);
        high.set(ModDataComponents.UPGRADE_LEVEL.get(), 99);
        high.applyComponents(high.getComponents());
        helper.assertTrue(UpgradeHelper.getLevel(high) == UpgradeHelper.MAX_LEVEL,
                "Stored 99 must be clamped to 30 on load");

        ItemStack low = new ItemStack(Items.DIAMOND_SWORD);
        low.set(ModDataComponents.UPGRADE_LEVEL.get(), -5);
        low.applyComponents(low.getComponents());
        helper.assertFalse(low.has(ModDataComponents.UPGRADE_LEVEL.get()),
                "Stored -5 must be clamped to 0 and removed on load");

        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void levelSurvivesSerialization(GameTestHelper helper) {
        ItemStack original = new ItemStack(Items.DIAMOND_SWORD);
        UpgradeHelper.setLevel(original, 17);

        Tag saved = original.save(helper.getLevel().registryAccess());
        ItemStack parsed = ItemStack.parse(helper.getLevel().registryAccess(), saved)
                .orElseThrow(() -> new IllegalStateException("ItemStack did not round-trip"));

        helper.assertTrue(UpgradeHelper.getLevel(parsed) == 17,
                "The level must survive ItemStack save/parse");
        helper.succeed();
    }

    // ---------------------------------------------------------------- M2 tags

    @GameTest(template = "empty")
    public static void categoriesAreDetected(GameTestHelper helper) {
        assertCategory(helper, new ItemStack(Items.DIAMOND_SWORD), UpgradeHelper.Category.WEAPON, "sword");
        assertCategory(helper, new ItemStack(Items.DIAMOND_CHESTPLATE), UpgradeHelper.Category.NONE,
                "chestplate (armor is no longer upgradable)");
        assertCategory(helper, new ItemStack(Items.BOW), UpgradeHelper.Category.PROJECTILE, "bow");
        assertCategory(helper, new ItemStack(Items.CROSSBOW), UpgradeHelper.Category.PROJECTILE, "crossbow");
        assertCategory(helper, new ItemStack(Items.TRIDENT), UpgradeHelper.Category.WEAPON, "trident");
        assertCategory(helper, new ItemStack(ModItems.UPGRADE_TEST_ITEM.get()),
                UpgradeHelper.Category.WEAPON, "synthetic test item");
        assertCategory(helper, new ItemStack(Items.STICK), UpgradeHelper.Category.NONE, "stick");
        helper.succeed();
    }

    // ---------------------------------------------------------------- M5 weapon attribute

    @GameTest(template = "empty")
    public static void weaponModifierMatchesLevel(GameTestHelper helper) {
        ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
        UpgradeHelper.setLevel(sword, 10);

        ItemAttributeModifiers atTen = modifiersFor(sword);
        double bonusAtTen = UpgradeHelper.getDamageBonus(10);
        helper.assertTrue(bonusAtTen > 0.0D, "The weapon table must define a bonus at +10");
        assertSingleUpgradeModifier(helper, atTen, bonusAtTen);

        UpgradeHelper.setLevel(sword, 30);
        ItemAttributeModifiers atThirty = modifiersFor(sword);
        double bonusAtThirty = UpgradeHelper.getDamageBonus(30);
        assertSingleUpgradeModifier(helper, atThirty, bonusAtThirty);
        helper.assertTrue(bonusAtThirty > bonusAtTen, "+30 must add more damage than +10");

        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void weaponModifierIsNotStackedAndDisappears(GameTestHelper helper) {
        ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
        UpgradeHelper.setLevel(sword, 10);
        double bonus = UpgradeHelper.getDamageBonus(10);

        ItemAttributeModifiers stale = ItemAttributeModifiers.builder()
                .add(Attributes.ATTACK_DAMAGE,
                        new AttributeModifier(UpgradeHelper.ATTACK_DAMAGE_MODIFIER_ID, 1.0D,
                                AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .build();
        ItemAttributeModifierEvent event = new ItemAttributeModifierEvent(sword, stale);
        UpgradeEvents.onItemAttributeModifiers(event);
        assertSingleUpgradeModifier(helper, event.build(), bonus);

        UpgradeHelper.setLevel(sword, 0);
        helper.assertFalse(hasUpgradeModifier(modifiersFor(sword)),
                "Level 0 must not add an attack damage modifier");

        // Leaving the tag keeps the stored level but disables the effect.
        ItemStack stick = new ItemStack(Items.STICK);
        UpgradeHelper.setLevel(stick, 30);
        helper.assertTrue(UpgradeHelper.getLevel(stick) == 30, "The level must survive leaving the tag");
        helper.assertFalse(hasUpgradeModifier(modifiersFor(stick)),
                "An item outside the tag must not get a modifier");

        helper.succeed();
    }

    // ---------------------------------------------------------------- M5 projectiles

    @GameTest(template = "empty")
    public static void projectileBonusUsesTheFiringWeapon(GameTestHelper helper) {
        Zombie target = new Zombie(helper.getLevel());

        ItemStack bow = new ItemStack(Items.BOW);
        UpgradeHelper.setLevel(bow, 30);
        Arrow arrow = new Arrow(helper.getLevel(), 0.0D, 0.0D, 0.0D,
                new ItemStack(Items.ARROW), bow);

        double bonus = UpgradeHelper.getProjectileBonus(30);
        helper.assertTrue(bonus > 0.0D, "The projectile table must define a bonus at +30");

        float boosted = applyDamage(helper, target, 10.0F,
                helper.getLevel().damageSources().arrow(arrow, arrow));
        helper.assertTrue(close(boosted, 10.0D * (1.0D + bonus)),
                "A +30 bow must multiply arrow damage by 1 + table value");

        ItemStack plainBow = new ItemStack(Items.BOW);
        Arrow plainArrow = new Arrow(helper.getLevel(), 0.0D, 0.0D, 0.0D,
                new ItemStack(Items.ARROW), plainBow);
        float plain = applyDamage(helper, target, 10.0F,
                helper.getLevel().damageSources().arrow(plainArrow, plainArrow));
        helper.assertTrue(close(plain, 10.0F), "An unupgraded bow must not change arrow damage");

        // A trident is a weapon, never a projectile, even when thrown.
        ItemStack trident = new ItemStack(Items.TRIDENT);
        UpgradeHelper.setLevel(trident, 30);
        Arrow tridentArrow = new Arrow(helper.getLevel(), 0.0D, 0.0D, 0.0D,
                new ItemStack(Items.ARROW), trident);
        float thrownTrident = applyDamage(helper, target, 10.0F,
                helper.getLevel().damageSources().arrow(tridentArrow, tridentArrow));
        helper.assertTrue(close(thrownTrident, 10.0F),
                "A trident must not get the projectile bonus");

        helper.succeed();
    }

    // ---------------------------------------------------------------- M3 command

    @GameTest(template = "empty")
    public static void commandSetsAndRemovesLevel(GameTestHelper helper) {
        // CommandSourceStack#getPlayer only answers for a real ServerPlayer, so a mock player
        // would make the command look like it had no main-hand item at all.
        ServerPlayer player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "himmelsstein-test-player"),
                ClientInformation.createDefault());
        CommandSourceStack source = player.createCommandSourceStack().withSuppressedOutput();

        ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
        player.setItemSlot(EquipmentSlot.MAINHAND, sword);

        helper.assertTrue(WeaponUpgradeCommand.execute(source, 10) == 1,
                "Setting +10 must succeed");
        helper.assertTrue(UpgradeHelper.getLevel(player.getMainHandItem()) == 10,
                "The command must set the level on the main-hand stack");

        helper.assertTrue(WeaponUpgradeCommand.execute(source, 0) == 1,
                "Setting +0 must succeed");
        helper.assertFalse(player.getMainHandItem().has(ModDataComponents.UPGRADE_LEVEL.get()),
                "The command 0 must remove the component");

        helper.assertTrue(WeaponUpgradeCommand.execute(source, 31) == 0,
                "Level 31 must be rejected");
        helper.assertTrue(WeaponUpgradeCommand.execute(source, -1) == 0,
                "Level -1 must be rejected");

        player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STICK));
        helper.assertTrue(WeaponUpgradeCommand.execute(source, 10) == 0,
                "A non-upgradable item must be rejected");

        helper.succeed();
    }

    // ---------------------------------------------------------------- M4 name and tooltip

    @GameTest(template = "empty")
    public static void hoverNameGetsLevelSuffix(GameTestHelper helper) {
        ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
        UpgradeHelper.setLevel(sword, 10);
        helper.assertTrue(sword.getHoverName().getString().endsWith(" +10"),
                "A +10 item must render a +10 suffix");
        helper.assertTrue(sword.getHoverName().getSiblings().stream()
                        .noneMatch(part -> part.getStyle().getColor() != null),
                "The suffix must inherit the name color instead of forcing its own");

        UpgradeHelper.setLevel(sword, 0);
        helper.assertFalse(sword.getHoverName().getString().endsWith(" +10"),
                "A +0 item must not render a suffix");

        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void anvilRepairStripsTypedSuffix(GameTestHelper helper) {
        ItemStack output = new ItemStack(Items.DIAMOND_SWORD);
        UpgradeHelper.setLevel(output, 10);
        output.set(DataComponents.CUSTOM_NAME, Component.literal("Diamantschwert +20"));

        AnvilRepairEvent event = new AnvilRepairEvent(helper.makeMockPlayer(GameType.CREATIVE),
                new ItemStack(Items.DIAMOND_SWORD), ItemStack.EMPTY, output);
        UpgradeEvents.onAnvilRepair(event);

        Component customName = output.get(DataComponents.CUSTOM_NAME);
        helper.assertTrue(customName != null && customName.getString().equals("Diamantschwert"),
                "A typed +20 must be stripped from the stored custom name");
        helper.assertTrue(UpgradeHelper.getLevel(output) == 10,
                "Stripping the name must not change the real level");

        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void tooltipLineOnlyExistsWithLevel(GameTestHelper helper) {
        ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
        Item.TooltipContext context = Item.TooltipContext.of(helper.getLevel());

        List<Component> plain = new ArrayList<>();
        UpgradeEvents.onItemTooltip(new ItemTooltipEvent(sword, null, plain, TooltipFlag.NORMAL, context));
        helper.assertTrue(plain.isEmpty(), "A +0 item must not get a tooltip line");

        UpgradeHelper.setLevel(sword, 10);
        List<Component> upgraded = new ArrayList<>();
        UpgradeEvents.onItemTooltip(new ItemTooltipEvent(sword, null, upgraded, TooltipFlag.NORMAL, context));
        helper.assertTrue(upgraded.size() == 1, "A +10 item must get exactly one tooltip line");
        helper.assertTrue(upgraded.get(0).getContents() instanceof TranslatableContents translatable
                        && translatable.getKey().equals("tooltip.himmelsstein.upgrade_level"),
                "The tooltip line must use the translatable upgrade key");
        helper.assertTrue(upgraded.get(0).getStyle().getColor() != null
                        && upgraded.get(0).getStyle().getColor().getValue() == 0xAAAAAA,
                "The tooltip line must be gray");

        helper.succeed();
    }

    // ---------------------------------------------------------------- M7 anvil

    @GameTest(template = "empty")
    public static void anvilBlocksOnlyPlusOnBothSides(GameTestHelper helper) {
        ItemStack plusSword = new ItemStack(Items.DIAMOND_SWORD);
        UpgradeHelper.setLevel(plusSword, 10);
        ItemStack plainSword = new ItemStack(Items.DIAMOND_SWORD);

        AnvilUpdateEvent plusPlus = new AnvilUpdateEvent(plusSword, plusSword.copy(), "x", 1L,
                helper.makeMockPlayer(GameType.CREATIVE));
        UpgradeEvents.onAnvilUpdate(plusPlus);
        helper.assertTrue(plusPlus.isCanceled(), "Plus + plus must be blocked");

        AnvilUpdateEvent plusBook = new AnvilUpdateEvent(plusSword, new ItemStack(Items.ENCHANTED_BOOK),
                "x", 1L, helper.makeMockPlayer(GameType.CREATIVE));
        UpgradeEvents.onAnvilUpdate(plusBook);
        helper.assertFalse(plusBook.isCanceled(), "A book on a plus item must stay allowed");

        AnvilUpdateEvent plusPlain = new AnvilUpdateEvent(plusSword, plainSword, "x", 1L,
                helper.makeMockPlayer(GameType.CREATIVE));
        UpgradeEvents.onAnvilUpdate(plusPlain);
        helper.assertFalse(plusPlain.isCanceled(), "A plain item on a plus item must stay allowed");

        helper.succeed();
    }

    // ---------------------------------------------------------------- helpers

    private static void assertCategory(GameTestHelper helper, ItemStack stack,
            UpgradeHelper.Category expected, String label) {
        helper.assertTrue(UpgradeHelper.getCategory(stack) == expected,
                label + " must be " + expected + " but was " + UpgradeHelper.getCategory(stack));
    }

    private static ItemAttributeModifiers modifiersFor(ItemStack stack) {
        ItemAttributeModifierEvent event = new ItemAttributeModifierEvent(stack, ItemAttributeModifiers.EMPTY);
        UpgradeEvents.onItemAttributeModifiers(event);
        return event.build();
    }

    private static boolean hasUpgradeModifier(ItemAttributeModifiers modifiers) {
        return modifiers.modifiers().stream().anyMatch(entry ->
                entry.attribute().equals(Attributes.ATTACK_DAMAGE)
                        && entry.modifier().id().equals(UpgradeHelper.ATTACK_DAMAGE_MODIFIER_ID));
    }

    private static void assertSingleUpgradeModifier(GameTestHelper helper,
            ItemAttributeModifiers modifiers, double expectedAmount) {
        List<ItemAttributeModifiers.Entry> matches = modifiers.modifiers().stream()
                .filter(entry -> entry.attribute().equals(Attributes.ATTACK_DAMAGE)
                        && entry.modifier().id().equals(UpgradeHelper.ATTACK_DAMAGE_MODIFIER_ID))
                .toList();
        helper.assertTrue(matches.size() == 1,
                "Expected exactly one upgrade modifier but found " + matches.size());
        ItemAttributeModifiers.Entry entry = matches.get(0);
        helper.assertTrue(entry.slot() == EquipmentSlotGroup.MAINHAND,
                "The upgrade modifier must only apply to the main hand");
        helper.assertTrue(entry.modifier().operation() == AttributeModifier.Operation.ADD_VALUE,
                "The upgrade modifier must be ADDITION");
        helper.assertTrue(close(entry.modifier().amount(), expectedAmount),
                "Expected modifier amount " + expectedAmount + " but was " + entry.modifier().amount());
    }

    private static float applyDamage(GameTestHelper helper, Zombie target, float amount,
            DamageSource source) {
        DamageContainer container = new DamageContainer(source, amount);
        LivingDamageEvent.Pre event = new LivingDamageEvent.Pre(target, container);
        UpgradeEvents.onLivingDamage(event);
        return event.getNewDamage();
    }

    private static boolean close(double actual, double expected) {
        return Math.abs(actual - expected) < EPSILON;
    }
}
