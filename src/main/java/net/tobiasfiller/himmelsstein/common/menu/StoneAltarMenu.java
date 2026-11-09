package net.tobiasfiller.himmelsstein.common.menu;

import javax.annotation.Nullable;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.tobiasfiller.himmelsstein.common.blockentity.StoneAltarBlockEntity;
import net.tobiasfiller.himmelsstein.core.registry.ModMenuTypes;

/**
 * Menu of the stone altar: input slot on the left, the sacrificed item rendered large in the
 * centre (screen only), power and heaven stone outputs on the right.
 *
 * <p>Clicking the large item sends {@link #BUTTON_SCRAP} via the vanilla container button packet;
 * the server re-validates everything in {@link StoneAltarBlockEntity#startScrapping()}.</p>
 */
public class StoneAltarMenu extends AbstractContainerMenu {
    public static final int BUTTON_SCRAP = 0;

    // Slot positions inside the GUI texture (top-left of the 16x16 item area).
    public static final int INPUT_X = 26;
    public static final int INPUT_Y = 30;
    public static final int POWER_X = 134;
    public static final int POWER_Y = 21;
    public static final int HEAVEN_X = 134;
    public static final int HEAVEN_Y = 39;
    public static final int INVENTORY_X = 8;
    public static final int INVENTORY_Y = 84;

    private static final int MACHINE_SLOTS = StoneAltarBlockEntity.SLOT_COUNT;
    private static final int PLAYER_SLOTS_END = MACHINE_SLOTS + 36;

    private final Container container;
    private final ContainerData data;
    @Nullable
    private final StoneAltarBlockEntity altar;

    /** Client-side constructor used by the menu type. */
    public StoneAltarMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(containerId, inventory, new SimpleContainer(MACHINE_SLOTS), new SimpleContainerData(2), null);
    }

    public StoneAltarMenu(int containerId, Inventory inventory, StoneAltarBlockEntity altar, ContainerData data) {
        this(containerId, inventory, altar, data, altar);
    }

    private StoneAltarMenu(int containerId, Inventory inventory, Container container, ContainerData data,
            @Nullable StoneAltarBlockEntity altar) {
        super(ModMenuTypes.STONE_ALTAR.get(), containerId);
        checkContainerSize(container, MACHINE_SLOTS);
        checkContainerDataCount(data, 2);
        this.container = container;
        this.data = data;
        this.altar = altar;

        addSlot(new InputSlot(container, StoneAltarBlockEntity.SLOT_INPUT, INPUT_X, INPUT_Y));
        addSlot(new OutputSlot(container, StoneAltarBlockEntity.SLOT_POWER, POWER_X, POWER_Y));
        addSlot(new OutputSlot(container, StoneAltarBlockEntity.SLOT_HEAVEN, HEAVEN_X, HEAVEN_Y));

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, INVENTORY_X + col * 18, INVENTORY_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, INVENTORY_X + col * 18, INVENTORY_Y + 58));
        }
        addDataSlots(data);
    }

    public boolean isScrapping() {
        return data.get(0) > 0;
    }

    /** Scrap progress from 0 to 1. */
    public float getProgress() {
        int max = data.get(1);
        return max <= 0 ? 0.0F : Math.min(1.0F, data.get(0) / (float) max);
    }

    public ItemStack getInput() {
        return container.getItem(StoneAltarBlockEntity.SLOT_INPUT);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        return id == BUTTON_SCRAP && altar != null && altar.startScrapping();
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
        } else if (slots.get(StoneAltarBlockEntity.SLOT_INPUT).mayPlace(stack)) {
            if (!moveItemStackTo(stack, StoneAltarBlockEntity.SLOT_INPUT, StoneAltarBlockEntity.SLOT_INPUT + 1, false)) {
                return ItemStack.EMPTY;
            }
        } else if (index < MACHINE_SLOTS + 27) {
            if (!moveItemStackTo(stack, MACHINE_SLOTS + 27, PLAYER_SLOTS_END, false)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, MACHINE_SLOTS, MACHINE_SLOTS + 27, false)) {
            return ItemStack.EMPTY;
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

    private class InputSlot extends Slot {
        InputSlot(Container container, int slot, int x, int y) {
            super(container, slot, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return stack.isDamageableItem() && !isScrapping();
        }

        @Override
        public boolean mayPickup(Player player) {
            return !isScrapping();
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }
    }

    private static class OutputSlot extends Slot {
        OutputSlot(Container container, int slot, int x, int y) {
            super(container, slot, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }
    }
}
