package net.tobiasfiller.himmelsstein.core.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
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
                    .icon(() -> new ItemStack(ModItems.HEAVEN_STONE.get()))
                    .displayItems((parameters, output) -> {
                        output.accept(ModItems.HEAVEN_STONE.get());
                        output.accept(ModItems.POWER_STONE.get());
                        output.accept(ModItems.LUCK_STONE.get());
                        output.accept(ModItems.PROTECTION_RUNE.get());
                        output.accept(ModItems.IRON_PROTECTION_RUNE.get());
                        output.accept(ModItems.GOLDEN_PROTECTION_RUNE.get());
                        output.accept(ModItems.DIAMOND_PROTECTION_RUNE.get());
                        output.accept(ModItems.STONE_ALTAR.get());
                        output.accept(ModItems.UPGRADE_TABLE.get());
                    })
                    .build());

    private ModCreativeTabs() {}
}
