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
import net.tobiasfiller.himmelsstein.core.utility.MonsterDropData;

/**
 * Loads {@code data/himmelsstein/monster_drops/default.json}. Drops are rolled server-side only,
 * so nothing is synced to clients.
 */
@EventBusSubscriber(modid = Himmelsstein.MODID)
public final class MonsterDropReloadListener extends SimpleJsonResourceReloadListener {
    public static final ResourceLocation DEFAULT_ID =
            ResourceLocation.fromNamespaceAndPath(Himmelsstein.MODID, "default");
    private static final Gson GSON = new GsonBuilder().create();

    private static volatile MonsterDropData current = MonsterDropData.EMPTY;

    private MonsterDropReloadListener() {
        super(GSON, "monster_drops");
    }

    public static MonsterDropData current() {
        return current;
    }

    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new MonsterDropReloadListener());
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> entries, ResourceManager resourceManager,
            ProfilerFiller profiler) {
        JsonElement json = entries.get(DEFAULT_ID);
        if (json == null) {
            Himmelsstein.LOGGER.warn("No monster drops at data/{}/monster_drops/{}.json; monsters drop no stones",
                    DEFAULT_ID.getNamespace(), DEFAULT_ID.getPath());
            current = MonsterDropData.EMPTY;
            return;
        }
        current = MonsterDropData.CODEC.parse(JsonOps.INSTANCE, json)
                .resultOrPartial(error -> Himmelsstein.LOGGER.error("Invalid monster drops {}: {}", DEFAULT_ID, error))
                .orElse(MonsterDropData.EMPTY);
    }
}
