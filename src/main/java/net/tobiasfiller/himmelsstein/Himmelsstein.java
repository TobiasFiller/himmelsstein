package net.tobiasfiller.himmelsstein;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.tobiasfiller.himmelsstein.core.registry.ModCreativeTabs;
import net.tobiasfiller.himmelsstein.core.registry.ModDataComponents;
import net.tobiasfiller.himmelsstein.core.registry.ModItems;
import net.tobiasfiller.himmelsstein.network.UpgradeNetwork;

@Mod(Himmelsstein.MODID)
public class Himmelsstein {
    public static final String MODID = "himmelsstein";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Himmelsstein(IEventBus modEventBus, ModContainer modContainer) {
        ModDataComponents.COMPONENTS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModCreativeTabs.TABS.register(modEventBus);

        modEventBus.addListener(UpgradeNetwork::register);

        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }
}
