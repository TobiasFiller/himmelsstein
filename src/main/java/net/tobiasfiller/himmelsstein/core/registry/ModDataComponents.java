package net.tobiasfiller.himmelsstein.core.registry;

import com.mojang.serialization.Codec;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.tobiasfiller.himmelsstein.Himmelsstein;

public final class ModDataComponents {
    public static final DeferredRegister<DataComponentType<?>> COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, Himmelsstein.MODID);

    /**
     * The upgrade level stored on an item stack. Persistent and network synchronized, because the
     * client needs it for the name suffix, tooltip and glow.
     *
     * <p>The persistent codec deliberately accepts any int (not only 0-30) so a save from another
     * modpack or a manual {@code /data merge} cannot make the whole {@link net.minecraft.world.item.ItemStack}
     * codec fail. Values are clamped when the stack is loaded and defensively on every read.</p>
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> UPGRADE_LEVEL = COMPONENTS.register(
            "upgrade_level",
            () -> DataComponentType.<Integer>builder()
                    .persistent(Codec.INT)
                    .networkSynchronized(ByteBufCodecs.VAR_INT)
                    .build());

    private ModDataComponents() {}
}
