package net.tobiasfiller.himmelsstein.core.datagen;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.data.event.GatherDataEvent;
import net.tobiasfiller.himmelsstein.Himmelsstein;

/** Datagen entry point; {@code gradlew runData} writes into {@code src/generated/resources}. */
@EventBusSubscriber(modid = Himmelsstein.MODID)
public final class DataGenerators {
    private DataGenerators() {}

    @SubscribeEvent
    public static void gatherData(GatherDataEvent event) {
        if (!event.includeServer()) {
            return;
        }
        ModBlockTagsProvider blockTags = event.createProvider((output, lookupProvider) ->
                new ModBlockTagsProvider(output, lookupProvider, event.getExistingFileHelper()));
        event.addProvider(new ModItemTagsProvider(event.getGenerator().getPackOutput(),
                event.getLookupProvider(), blockTags.contentsGetter(), event.getExistingFileHelper()));
    }
}
