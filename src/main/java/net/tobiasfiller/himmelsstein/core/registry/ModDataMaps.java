package net.tobiasfiller.himmelsstein.core.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.datamaps.DataMapType;
import net.neoforged.neoforge.registries.datamaps.RegisterDataMapTypesEvent;
import net.tobiasfiller.himmelsstein.Himmelsstein;
import net.tobiasfiller.himmelsstein.core.utility.UpgradeStoneData;

/** Registry data maps of the mod. */
public final class ModDataMaps {
    /**
     * Per-item upgrade stone stats, loaded from
     * {@code data/himmelsstein/data_maps/item/upgrade_stone.json}.
     *
     * <p>The map is synced to clients so the upgrade table can show the same chances the server
     * rolls against.</p>
     */
    public static final DataMapType<Item, UpgradeStoneData> UPGRADE_STONE = DataMapType
            .builder(ResourceLocation.fromNamespaceAndPath(Himmelsstein.MODID, "upgrade_stone"),
                    Registries.ITEM, UpgradeStoneData.CODEC)
            .synced(UpgradeStoneData.CODEC, false)
            .build();

    private ModDataMaps() {}

    public static void register(RegisterDataMapTypesEvent event) {
        event.register(UPGRADE_STONE);
    }
}
