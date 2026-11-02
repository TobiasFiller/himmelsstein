package net.tobiasfiller.himmelsstein.network;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.tobiasfiller.himmelsstein.Himmelsstein;
import net.tobiasfiller.himmelsstein.core.utility.UpgradeTables;

/** Server -> client snapshot of the two level tables. */
public record UpgradeConfigPayload(List<Double> weaponDamage, List<Double> projectileDamage)
        implements CustomPacketPayload {
    public static final Type<UpgradeConfigPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Himmelsstein.MODID, "upgrade_config"));

    public static final StreamCodec<RegistryFriendlyByteBuf, UpgradeConfigPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public UpgradeConfigPayload decode(RegistryFriendlyByteBuf buffer) {
            return new UpgradeConfigPayload(readList(buffer), readList(buffer));
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer, UpgradeConfigPayload payload) {
            writeList(buffer, payload.weaponDamage);
            writeList(buffer, payload.projectileDamage);
        }
    };

    @Override
    public Type<UpgradeConfigPayload> type() {
        return TYPE;
    }

    public static void handle(UpgradeConfigPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> UpgradeTables.applySynced(
                payload.weaponDamage, payload.projectileDamage));
    }

    private static List<Double> readList(RegistryFriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        List<Double> values = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            values.add(buffer.readDouble());
        }
        return values;
    }

    private static void writeList(RegistryFriendlyByteBuf buffer, List<Double> values) {
        buffer.writeVarInt(values.size());
        for (double value : values) {
            buffer.writeDouble(value);
        }
    }
}
