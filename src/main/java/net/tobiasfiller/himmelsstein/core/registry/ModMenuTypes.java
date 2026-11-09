package net.tobiasfiller.himmelsstein.core.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.tobiasfiller.himmelsstein.Himmelsstein;
import net.tobiasfiller.himmelsstein.common.menu.StoneAltarMenu;

public final class ModMenuTypes {
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, Himmelsstein.MODID);

    public static final DeferredHolder<MenuType<?>, MenuType<StoneAltarMenu>> STONE_ALTAR =
            MENUS.register("stone_altar", () -> IMenuTypeExtension.create(StoneAltarMenu::new));

    private ModMenuTypes() {}
}
