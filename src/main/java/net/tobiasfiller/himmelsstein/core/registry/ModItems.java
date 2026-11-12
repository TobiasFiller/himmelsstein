package net.tobiasfiller.himmelsstein.core.registry;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.tobiasfiller.himmelsstein.Himmelsstein;
import net.tobiasfiller.himmelsstein.common.item.ProtectionRuneItem;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Himmelsstein.MODID);

    /**
     * Synthetic test item that only lives in the mod's own common tag. It is used by the game
     * tests to verify that datapack/common-tag items are recognized without a vanilla tag.
     */
    public static final DeferredItem<Item> UPGRADE_TEST_ITEM = ITEMS.registerSimpleItem(
            "upgrade_test_item",
            new Item.Properties());

    // Upgrade Stones
    public static final DeferredItem<Item> HEAVEN_STONE = ITEMS.registerSimpleItem(
            "heaven_stone",
            new Item.Properties().rarity(Rarity.UNCOMMON));

    public static final DeferredItem<Item> POWER_STONE = ITEMS.registerSimpleItem(
            "power_stone",
            new Item.Properties());

    public static final DeferredItem<Item> LUCK_STONE = ITEMS.registerSimpleItem(
            "luck_stone",
            new Item.Properties().rarity(Rarity.RARE));

    // Protection Runes
    public static final DeferredItem<ProtectionRuneItem> PROTECTION_RUNE = ITEMS.registerItem(
            "protection_rune",
            ProtectionRuneItem::new,
            new Item.Properties().rarity(Rarity.COMMON));

    public static final DeferredItem<ProtectionRuneItem> IRON_PROTECTION_RUNE = ITEMS.registerItem(
            "iron_protection_rune",
            ProtectionRuneItem::new,
            new Item.Properties().rarity(Rarity.UNCOMMON));

    public static final DeferredItem<ProtectionRuneItem> GOLDEN_PROTECTION_RUNE = ITEMS.registerItem(
            "golden_protection_rune",
            ProtectionRuneItem::new,
            new Item.Properties().rarity(Rarity.RARE));

    public static final DeferredItem<ProtectionRuneItem> DIAMOND_PROTECTION_RUNE = ITEMS.registerItem(
            "diamond_protection_rune",
            ProtectionRuneItem::new,
            new Item.Properties().rarity(Rarity.EPIC));

    // Blocks
    public static final DeferredItem<BlockItem> STONE_ALTAR = ITEMS.registerSimpleBlockItem(ModBlocks.STONE_ALTAR);
    public static final DeferredItem<BlockItem> UPGRADE_TABLE = ITEMS.registerSimpleBlockItem(ModBlocks.UPGRADE_TABLE);

    private ModItems() {}
}
