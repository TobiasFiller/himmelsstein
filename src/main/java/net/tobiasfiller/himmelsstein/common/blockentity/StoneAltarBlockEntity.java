package net.tobiasfiller.himmelsstein.common.blockentity;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.tobiasfiller.himmelsstein.Himmelsstein;
import net.tobiasfiller.himmelsstein.common.block.StoneAltarBlock;
import net.tobiasfiller.himmelsstein.common.menu.StoneAltarMenu;
import net.tobiasfiller.himmelsstein.core.event.AltarYieldReloadListener;
import net.tobiasfiller.himmelsstein.core.registry.ModBlockEntities;
import net.tobiasfiller.himmelsstein.core.registry.ModItems;
import net.tobiasfiller.himmelsstein.core.utility.AltarYieldData;

/**
 * Inventory and scrap process of the stone altar.
 *
 * <p>Slot 0 holds the item to sacrifice, slots 1/2 collect power and heaven stones. Scrapping is
 * server-authoritative: it is started by a menu button click or by redstone, runs for
 * {@link #SCRAP_TIME} ticks and then rolls the yield from {@link AltarYieldReloadListener}. While
 * the altar is powered, every item that arrives in the input slot is scrapped automatically.</p>
 */
public class StoneAltarBlockEntity extends BaseContainerBlockEntity implements WorldlyContainer {
    public static final int SLOT_INPUT = 0;
    public static final int SLOT_POWER = 1;
    public static final int SLOT_HEAVEN = 2;
    public static final int SLOT_COUNT = 3;
    public static final int SCRAP_TIME = 40;

    private static final int[] SLOTS_INPUT = {SLOT_INPUT};
    private static final int[] SLOTS_OUTPUT = {SLOT_POWER, SLOT_HEAVEN};

    private NonNullList<ItemStack> items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
    private int progress;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return index == 0 ? progress : SCRAP_TIME;
        }

        @Override
        public void set(int index, int value) {
            if (index == 0) {
                progress = value;
            }
        }

        @Override
        public int getCount() {
            return 2;
        }
    };

    public StoneAltarBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.STONE_ALTAR.get(), pos, state);
    }

    // ---------------------------------------------------------------- scrapping

    public static void serverTick(Level level, BlockPos pos, BlockState state, StoneAltarBlockEntity altar) {
        if (altar.progress <= 0) {
            // A permanent redstone signal keeps the altar scrapping whatever arrives in the input.
            if (state.getValue(StoneAltarBlock.POWERED)) {
                altar.startScrapping();
            }
            return;
        }
        altar.progress++;
        if (altar.progress >= SCRAP_TIME) {
            altar.finishScrapping();
        }
    }

    public boolean isScrapping() {
        return progress > 0;
    }

    public int getProgress() {
        return progress;
    }

    public boolean hasInput() {
        return !items.get(SLOT_INPUT).isEmpty();
    }

    /** Whether the input can be sacrificed right now and the outputs have room for the best case. */
    public boolean canScrap() {
        ItemStack input = items.get(SLOT_INPUT);
        if (isScrapping() || input.isEmpty() || !input.isDamageableItem()) {
            return false;
        }
        AltarYieldData yield = AltarYieldReloadListener.current();
        return hasRoom(items.get(SLOT_POWER), ModItems.POWER_STONE.get(), yield.maxPowerStones())
                && hasRoom(items.get(SLOT_HEAVEN), ModItems.HEAVEN_STONE.get(), 1);
    }

    private static boolean hasRoom(ItemStack slot, Item item, int amount) {
        if (slot.isEmpty()) {
            return true;
        }
        return slot.is(item) && slot.getCount() + amount <= slot.getMaxStackSize();
    }

    /** Starts the scrap process. Returns {@code false} if nothing was started. */
    public boolean startScrapping() {
        if (level == null || level.isClientSide || !canScrap()) {
            return false;
        }
        progress = 1;
        level.playSound(null, worldPosition, SoundEvents.GRINDSTONE_USE, SoundSource.BLOCKS, 1.0F, 0.8F);
        setChanged();
        return true;
    }

    private void finishScrapping() {
        progress = 0;
        ItemStack input = items.get(SLOT_INPUT);
        if (level == null || input.isEmpty() || !input.isDamageableItem()) {
            setChanged();
            return;
        }

        AltarYieldData.Result result = AltarYieldReloadListener.current().roll(input, level.random);
        items.set(SLOT_INPUT, ItemStack.EMPTY);
        insertOutput(SLOT_POWER, ModItems.POWER_STONE.get(), result.powerStones());
        insertOutput(SLOT_HEAVEN, ModItems.HEAVEN_STONE.get(), result.heavenStone() ? 1 : 0);

        if (level instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, input),
                    worldPosition.getX() + 0.5D, worldPosition.getY() + 0.95D, worldPosition.getZ() + 0.5D,
                    16, 0.2D, 0.1D, 0.2D, 0.05D);
        }
        level.playSound(null, worldPosition, SoundEvents.ITEM_BREAK, SoundSource.BLOCKS, 1.0F, 0.9F);
        setChanged();
        syncToClient();
    }

    private void insertOutput(int slot, Item item, int amount) {
        if (amount <= 0) {
            return;
        }
        ItemStack current = items.get(slot);
        if (current.isEmpty()) {
            items.set(slot, new ItemStack(item, amount));
        } else {
            current.grow(amount);
        }
    }

    // ---------------------------------------------------------------- container

    @Override
    protected NonNullList<ItemStack> getItems() {
        return items;
    }

    @Override
    protected void setItems(NonNullList<ItemStack> items) {
        this.items = items;
    }

    @Override
    public int getContainerSize() {
        return SLOT_COUNT;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        super.setItem(slot, stack);
        if (slot == SLOT_INPUT) {
            syncToClient();
        }
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack removed = super.removeItem(slot, amount);
        if (slot == SLOT_INPUT && !removed.isEmpty()) {
            syncToClient();
        }
        return removed;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        ItemStack removed = super.removeItemNoUpdate(slot);
        if (slot == SLOT_INPUT && !removed.isEmpty()) {
            syncToClient();
        }
        return removed;
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return slot == SLOT_INPUT && !isScrapping() && items.get(SLOT_INPUT).isEmpty() && stack.isDamageableItem();
    }

    @Override
    public int[] getSlotsForFace(Direction side) {
        return side == Direction.DOWN ? SLOTS_OUTPUT : SLOTS_INPUT;
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) {
        return side != Direction.DOWN && canPlaceItem(slot, stack);
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return side == Direction.DOWN && slot != SLOT_INPUT;
    }

    // ---------------------------------------------------------------- menu

    @Override
    protected Component getDefaultName() {
        return Component.translatable("container." + Himmelsstein.MODID + ".stone_altar");
    }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory inventory) {
        return new StoneAltarMenu(containerId, inventory, this, data);
    }

    // ---------------------------------------------------------------- persistence + sync

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, items, registries);
        progress = tag.getInt("progress");
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, items, registries);
        tag.putInt("progress", progress);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /** Sends the input item to clients so the renderer can show it lying on the altar. */
    private void syncToClient() {
        if (level != null && !level.isClientSide) {
            BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, 3);
        }
    }
}
