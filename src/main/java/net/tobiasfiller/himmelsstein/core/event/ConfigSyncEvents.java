package net.tobiasfiller.himmelsstein.core.event;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import net.tobiasfiller.himmelsstein.Himmelsstein;

/** Re-broadcasts the level tables to connected clients after a config reload. */
@EventBusSubscriber(modid = Himmelsstein.MODID)
public final class ConfigSyncEvents {
    private ConfigSyncEvents() {}

    @SubscribeEvent
    public static void onConfigReload(ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() != net.tobiasfiller.himmelsstein.Config.SPEC) {
            return;
        }
        if (ServerLifecycleHooks.getCurrentServer() != null) {
            PacketDistributor.sendToAllPlayers(UpgradeEvents.currentConfigPayload());
        }
    }
}
