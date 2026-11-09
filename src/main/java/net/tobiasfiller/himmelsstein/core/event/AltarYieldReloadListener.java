package net.tobiasfiller.himmelsstein.core.event;

import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.tobiasfiller.himmelsstein.Himmelsstein;
import net.tobiasfiller.himmelsstein.core.utility.AltarYieldData;

/**
 * Loads {@code data/himmelsstein/altar_yield/default.json}. The yield is only needed on the
 * server (scrapping is server-authoritative), so nothing is synced to clients.
 */
@EventBusSubscriber(modid = Himmelsstein.MODID)
public final class AltarYieldReloadListener extends SimpleJsonResourceReloadListener {
    public static final ResourceLocation DEFAULT_ID =
            ResourceLocation.fromNamespaceAndPath(Himmelsstein.MODID, "default");
    private static final Gson GSON = new GsonBuilder().create();

    private static volatile AltarYieldData current = AltarYieldData.EMPTY;

    private AltarYieldReloadListener() {
        super(GSON, "altar_yield");
    }

    public static AltarYieldData current() {
        return current;
    }

    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new AltarYieldReloadListener());
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> entries, ResourceManager resourceManager,
            ProfilerFiller profiler) {
        JsonElement json = entries.get(DEFAULT_ID);
        if (json == null) {
            Himmelsstein.LOGGER.warn("No altar yield at data/{}/altar_yield/{}.json; scrapping yields nothing",
                    DEFAULT_ID.getNamespace(), DEFAULT_ID.getPath());
            current = AltarYieldData.EMPTY;
            return;
        }
        current = AltarYieldData.CODEC.parse(JsonOps.INSTANCE, json)
                .resultOrPartial(error -> Himmelsstein.LOGGER.error("Invalid altar yield {}: {}", DEFAULT_ID, error))
                .orElse(AltarYieldData.EMPTY);
    }
}
