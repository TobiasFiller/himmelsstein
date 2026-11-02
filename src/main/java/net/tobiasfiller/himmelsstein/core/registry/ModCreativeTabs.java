package net.tobiasfiller.himmelsstein.core.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.tobiasfiller.himmelsstein.Himmelsstein;

public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Himmelsstein.MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> HIMMELSSTEIN = TABS.register(
            "himmelsstein",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.himmelsstein"))
                    .withTabsBefore(CreativeModeTabs.COMBAT)
                    .icon(() -> new ItemStack(Items.NETHER_STAR))
                    .displayItems((parameters, output) -> {
                        // Real content lands with the stone/upgrade implementation. The synthetic
                        // test item stays out of the creative menu on purpose.
                    })
                    .build());

    private ModCreativeTabs() {}
}
