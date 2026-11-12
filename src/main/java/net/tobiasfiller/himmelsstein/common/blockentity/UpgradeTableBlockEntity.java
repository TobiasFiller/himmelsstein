package net.tobiasfiller.himmelsstein.common.blockentity;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.tobiasfiller.himmelsstein.Himmelsstein;
import net.tobiasfiller.himmelsstein.common.menu.UpgradeTableMenu;
import net.tobiasfiller.himmelsstein.core.registry.ModBlockEntities;
import net.tobiasfiller.himmelsstein.core.registry.ModTags;
import net.tobiasfiller.himmelsstein.core.utility.UpgradeHelper;
import net.tobiasfiller.himmelsstein.core.utility.UpgradeTableHelper;

/**
 * Inventory and upgrade process of the upgrade table.
 *
 * <p>Slot 0 holds the stone, slot 1 the item to upgrade and slot 2 an optional protection rune.
 * The roll happens only on the server, either through the menu button or through
 * {@link #performUpgrade(RandomSource)} (also used by the game tests).</p>
 */
public class UpgradeTableBlockEntity extends BaseContainerBlockEntity {
    public static final int SLOT_STONE = 0;
    public static final int SLOT_ITEM = 1;
    public static final int SLOT_RUNE = 2;
    public static final int SLOT_COUNT = 3;

    private NonNullList<ItemStack> items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);

    public UpgradeTableBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.UPGRADE_TABLE.get(), pos, state);
    }

    // ---------------------------------------------------------------- upgrade

    /** Menu entry point: rolls with the level's random source. */
    public boolean startUpgrade() {
        if (level == null || level.isClientSide) {
            return false;
        }
        return performUpgrade(level.random);
    }

    /**
     * Rolls and applies one attempt. Returns {@code false} if the slots do not form a valid
     * attempt, in which case nothing is consumed.
     */
    public boolean performUpgrade(RandomSource random) {
        if (level == null || level.isClientSide) {
            return false;
        }

        ItemStack item = items.get(SLOT_ITEM);
        ItemStack stone = items.get(SLOT_STONE);
        ItemStack rune = items.get(SLOT_RUNE);

        UpgradeTableHelper.Result result = UpgradeTableHelper.attempt(item, stone, rune, random);
        if (result.outcome() == UpgradeTableHelper.Outcome.INVALID) {
            return false;
        }

        // The stone is always consumed. The rune is only consumed when it actually
        // prevented destruction; successes and harmless failures leave it in the slot.
        stone.shrink(1);
        if (stone.isEmpty()) {
            items.set(SLOT_STONE, ItemStack.EMPTY);
        }
        if (result.outcome() == UpgradeTableHelper.Outcome.FAIL_RUNE_PROTECT && !rune.isEmpty()) {
            rune.shrink(1);
            if (rune.isEmpty()) {
                items.set(SLOT_RUNE, ItemStack.EMPTY);
            }
        }

        switch (result.outcome()) {
            case SUCCESS, FAIL_LOSE_LEVEL, FAIL_RUNE_PROTECT -> UpgradeHelper.setLevel(item, result.newLevel());
            case FAIL_KEEP -> {
                // The item stays untouched.
            }
            case FAIL_DESTROY -> items.set(SLOT_ITEM, ItemStack.EMPTY);
            default -> {
                return false;
            }
        }

        setChanged();
        playFeedback(result.outcome());
        return true;
    }

    private void playFeedback(UpgradeTableHelper.Outcome outcome) {
        if (level == null) {
            return;
        }
        double x = worldPosition.getX() + 0.5D;
        double y = worldPosition.getY() + 1.0D;
        double z = worldPosition.getZ() + 0.5D;

        switch (outcome) {
            case SUCCESS -> {
                level.playSound(null, worldPosition, SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.BLOCKS,
                        1.0F, 1.2F);
                if (level instanceof ServerLevel serverLevel) {
                    serverLevel.sendParticles(ParticleTypes.HAPPY_VILLAGER, x, y, z, 12,
                            0.3D, 0.3D, 0.3D, 0.02D);
                }
            }
            case FAIL_KEEP -> {
                level.playSound(null, worldPosition, SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 0.6F, 0.7F);
                if (level instanceof ServerLevel serverLevel) {
                    serverLevel.sendParticles(ParticleTypes.SMOKE, x, y, z, 8,
                            0.2D, 0.2D, 0.2D, 0.01D);
                }
            }
            case FAIL_LOSE_LEVEL -> {
                level.playSound(null, worldPosition, SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 0.7F, 0.5F);
                if (level instanceof ServerLevel serverLevel) {
                    serverLevel.sendParticles(ParticleTypes.SMOKE, x, y, z, 10,
                            0.2D, 0.2D, 0.2D, 0.01D);
                }
            }
            case FAIL_DESTROY -> {
                level.playSound(null, worldPosition, SoundEvents.ITEM_BREAK, SoundSource.BLOCKS, 1.0F, 0.8F);
                if (level instanceof ServerLevel serverLevel) {
                    serverLevel.sendParticles(ParticleTypes.LARGE_SMOKE, x, y, z, 12,
                            0.2D, 0.2D, 0.2D, 0.01D);
                }
            }
            case FAIL_RUNE_PROTECT -> {
                level.playSound(null, worldPosition, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS,
                        0.9F, 1.4F);
                if (level instanceof ServerLevel serverLevel) {
                    serverLevel.sendParticles(ParticleTypes.ENCHANT, x, y, z, 16,
                            0.3D, 0.3D, 0.3D, 0.05D);
                }
            }
            default -> {
                // No feedback for invalid attempts.
            }
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
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return switch (slot) {
            case SLOT_STONE -> stack.is(ModTags.UPGRADE_STONES);
            case SLOT_ITEM -> UpgradeHelper.isUpgradable(stack);
            case SLOT_RUNE -> stack.is(ModTags.PROTECTION_RUNES);
            default -> false;
        };
    }

    // ---------------------------------------------------------------- menu

    @Override
    protected Component getDefaultName() {
        return Component.translatable("container." + Himmelsstein.MODID + ".upgrade_table");
    }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory inventory) {
        return new UpgradeTableMenu(containerId, inventory, this);
    }

    // ---------------------------------------------------------------- persistence

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, items, registries);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, items, registries);
    }

    // ---------------------------------------------------------------- client sync

    /**
     * The client needs the slot contents to render the floating item, so the block entity
     * synchronises itself instead of only living on the server.
     */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void setChanged() {
        super.setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
        }
    }
}
