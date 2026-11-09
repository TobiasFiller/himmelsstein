package net.tobiasfiller.himmelsstein.core.event;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.items.wrapper.InvWrapper;
import net.neoforged.neoforge.items.wrapper.SidedInvWrapper;
import net.tobiasfiller.himmelsstein.Himmelsstein;
import net.tobiasfiller.himmelsstein.core.registry.ModBlockEntities;

/** Exposes the stone altar to hoppers and pipes: input from top/sides, output from below. */
@EventBusSubscriber(modid = Himmelsstein.MODID)
public final class ModCapabilityEvents {
    private ModCapabilityEvents() {}

    @SubscribeEvent
    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ModBlockEntities.STONE_ALTAR.get(),
                (altar, side) -> side == null ? new InvWrapper(altar) : new SidedInvWrapper(altar, side));
    }
}
