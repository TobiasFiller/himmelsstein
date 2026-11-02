package net.tobiasfiller.himmelsstein.core.datagen;

import java.util.concurrent.CompletableFuture;

import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.common.data.BlockTagsProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import net.tobiasfiller.himmelsstein.Himmelsstein;

/**
 * The item tag provider needs a block tag lookup to copy block tags into item tags. Himmelsstein
 * has no blocks yet, so this provider intentionally adds no tags.
 */
public final class ModBlockTagsProvider extends BlockTagsProvider {
    public ModBlockTagsProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider,
            ExistingFileHelper existingFileHelper) {
        super(output, lookupProvider, Himmelsstein.MODID, existingFileHelper);
    }

    @Override
    protected void addTags(HolderLookup.Provider provider) {
    }
}
