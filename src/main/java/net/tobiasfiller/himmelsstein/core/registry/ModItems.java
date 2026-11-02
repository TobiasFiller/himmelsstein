package net.tobiasfiller.himmelsstein.core.registry;

import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.tobiasfiller.himmelsstein.Himmelsstein;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Himmelsstein.MODID);

    /**
     * Synthetic test item that only lives in the mod's own common tag. It is used by the game
     * tests to verify that datapack/common-tag items are recognized without a vanilla tag.
     */
    public static final DeferredItem<Item> UPGRADE_TEST_ITEM = ITEMS.registerSimpleItem(
            "upgrade_test_item",
            new Item.Properties());

    private ModItems() {}
}
