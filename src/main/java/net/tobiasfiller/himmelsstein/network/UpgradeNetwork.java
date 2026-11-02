package net.tobiasfiller.himmelsstein.network;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public final class UpgradeNetwork {
    private UpgradeNetwork() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(
                UpgradeConfigPayload.TYPE,
                UpgradeConfigPayload.STREAM_CODEC,
                UpgradeConfigPayload::handle);
    }
}
