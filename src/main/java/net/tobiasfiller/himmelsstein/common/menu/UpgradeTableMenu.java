package net.tobiasfiller.himmelsstein.common.menu;

import javax.annotation.Nullable;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.tobiasfiller.himmelsstein.common.blockentity.UpgradeTableBlockEntity;
import net.tobiasfiller.himmelsstein.core.registry.ModMenuTypes;
import net.tobiasfiller.himmelsstein.core.registry.ModTags;
import net.tobiasfiller.himmelsstein.core.utility.UpgradeHelper;
import net.tobiasfiller.himmelsstein.core.utility.UpgradeTableHelper;

/**
 * Menu of the upgrade table: stone on the left, item in the middle, rune slot on the right.
 *
 * <p>The screen only previews the chance. Clicking the button sends {@link #BUTTON_UPGRADE}; the
 * server re-validates the slots in {@link UpgradeTableBlockEntity#startUpgrade()}.</p>
 */
public class UpgradeTableMenu extends AbstractContainerMenu {
    public static final int BUTTON_UPGRADE = 0;

    // Slot positions inside the GUI texture (top-left of the 16x16 item area, not the 18x18 frame).
    public static final int STONE_X = 51;
    public static final int STONE_Y = 34;
    public static final int ITEM_X = 80;
    public static final int ITEM_Y = 34;
    public static final int RUNE_X = 109;
    public static final int RUNE_Y = 34;
    public static final int INVENTORY_X = 8;
    public static final int INVENTORY_Y = 84;

    private static final int MACHINE_SLOTS = UpgradeTableBlockEntity.SLOT_COUNT;
    private static final int PLAYER_SLOTS_END = MACHINE_SLOTS + 36;

    private final Container container;
    @Nullable
    private final UpgradeTableBlockEntity table;

    /** Client-side constructor used by the menu type. */
    public UpgradeTableMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(containerId, inventory, new SimpleContainer(MACHINE_SLOTS), null);
    }

    public UpgradeTableMenu(int containerId, Inventory inventory, UpgradeTableBlockEntity table) {
        this(containerId, inventory, table, table);
    }

    private UpgradeTableMenu(int containerId, Inventory inventory, Container container,
            @Nullable UpgradeTableBlockEntity table) {
        super(ModMenuTypes.UPGRADE_TABLE.get(), containerId);
        checkContainerSize(container, MACHINE_SLOTS);
        this.container = container;
        this.table = table;

        addSlot(new StoneSlot(container, UpgradeTableBlockEntity.SLOT_STONE, STONE_X, STONE_Y));
        addSlot(new ItemSlot(container, UpgradeTableBlockEntity.SLOT_ITEM, ITEM_X, ITEM_Y));
        addSlot(new RuneSlot(container, UpgradeTableBlockEntity.SLOT_RUNE, RUNE_X, RUNE_Y));

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, INVENTORY_X + col * 18, INVENTORY_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, INVENTORY_X + col * 18, INVENTORY_Y + 58));
        }
    }

    /** Current preview, shared by the chance display and the button state. */
    public UpgradeTableHelper.Preview getPreview() {
        return UpgradeTableHelper.preview(
                container.getItem(UpgradeTableBlockEntity.SLOT_ITEM),
                container.getItem(UpgradeTableBlockEntity.SLOT_STONE),
                container.getItem(UpgradeTableBlockEntity.SLOT_RUNE));
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        return id == BUTTON_UPGRADE && table != null && table.startUpgrade();
    }

    @Override
    public boolean stillValid(Player player) {
        return container.stillValid(player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();

        if (index < MACHINE_SLOTS) {
            if (!moveItemStackTo(stack, MACHINE_SLOTS, PLAYER_SLOTS_END, true)) {
                return ItemStack.EMPTY;
            }
        } else {
            boolean moved = false;
            if (UpgradeHelper.isUpgradable(stack)) {
                moved = moveItemStackTo(stack, UpgradeTableBlockEntity.SLOT_ITEM,
                        UpgradeTableBlockEntity.SLOT_ITEM + 1, false);
            } else if (stack.is(ModTags.UPGRADE_STONES)) {
                moved = moveItemStackTo(stack, UpgradeTableBlockEntity.SLOT_STONE,
                        UpgradeTableBlockEntity.SLOT_STONE + 1, false);
            } else if (stack.is(ModTags.PROTECTION_RUNES)) {
                moved = moveItemStackTo(stack, UpgradeTableBlockEntity.SLOT_RUNE,
                        UpgradeTableBlockEntity.SLOT_RUNE + 1, false);
            }

            if (!moved) {
                if (index < MACHINE_SLOTS + 27) {
                    if (!moveItemStackTo(stack, MACHINE_SLOTS + 27, PLAYER_SLOTS_END, false)) {
                        return ItemStack.EMPTY;
                    }
                } else if (!moveItemStackTo(stack, MACHINE_SLOTS, MACHINE_SLOTS + 27, false)) {
                    return ItemStack.EMPTY;
                }
            }
        }

        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        if (stack.getCount() == original.getCount()) {
            return ItemStack.EMPTY;
        }
        slot.onTake(player, stack);
        return original;
    }

    private static class MachineSlot extends Slot {
        MachineSlot(Container container, int slot, int x, int y) {
            super(container, slot, x, y);
        }
    }

    private static class StoneSlot extends MachineSlot {
        StoneSlot(Container container, int slot, int x, int y) {
            super(container, slot, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return stack.is(ModTags.UPGRADE_STONES);
        }
    }

    private static class ItemSlot extends MachineSlot {
        ItemSlot(Container container, int slot, int x, int y) {
            super(container, slot, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return UpgradeHelper.isUpgradable(stack);
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }
    }

    private static class RuneSlot extends MachineSlot {
        RuneSlot(Container container, int slot, int x, int y) {
            super(container, slot, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return stack.is(ModTags.PROTECTION_RUNES);
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }
    }
}
