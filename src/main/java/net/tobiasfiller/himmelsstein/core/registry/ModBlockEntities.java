package net.tobiasfiller.himmelsstein.core.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.tobiasfiller.himmelsstein.Himmelsstein;
import net.tobiasfiller.himmelsstein.common.blockentity.StoneAltarBlockEntity;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Himmelsstein.MODID);

    @SuppressWarnings("DataFlowIssue") // mod block entities have no data fixer type
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<StoneAltarBlockEntity>> STONE_ALTAR =
            BLOCK_ENTITIES.register("stone_altar", () -> BlockEntityType.Builder
                    .of(StoneAltarBlockEntity::new, ModBlocks.STONE_ALTAR.get())
                    .build(null));

    private ModBlockEntities() {}
}
