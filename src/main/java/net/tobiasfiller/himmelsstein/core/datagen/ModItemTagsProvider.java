package net.tobiasfiller.himmelsstein.core.datagen;

import java.util.concurrent.CompletableFuture;

import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.tags.ItemTagsProvider;
import net.minecraft.data.tags.TagsProvider;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import net.tobiasfiller.himmelsstein.Himmelsstein;
import net.tobiasfiller.himmelsstein.core.registry.ModItems;
import net.tobiasfiller.himmelsstein.core.registry.ModTags;

/**
 * Item tags that decide which items can carry an upgrade level.
 *
 * <p>Common and vanilla tags are added in parallel: mod items that maintain the common tags come
 * along automatically, and gaps in one source are covered by the other. Additional items are meant
 * to be added through a datapack, never by sniffing item attributes.</p>
 */
public final class ModItemTagsProvider extends ItemTagsProvider {
    public ModItemTagsProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider,
            CompletableFuture<TagsProvider.TagLookup<Block>> blockTags, ExistingFileHelper existingFileHelper) {
        super(output, lookupProvider, blockTags, Himmelsstein.MODID, existingFileHelper);
    }

    @Override
    protected void addTags(HolderLookup.Provider provider) {
        // Projectile takes priority over weapon in UpgradeHelper#getCategory.
        // #c:tools/ranged_weapon is deliberately not used: it contains the trident, which must stay
        // a melee weapon even though ThrownTrident extends AbstractArrow.
        tag(ModTags.UPGRADABLE_PROJECTILE)
                .add(Items.BOW, Items.CROSSBOW)
                .addOptionalTags(Tags.Items.TOOLS_BOW, Tags.Items.TOOLS_CROSSBOW);

        tag(ModTags.UPGRADABLE_WEAPON)
                .add(Items.TRIDENT, Items.MACE, ModItems.UPGRADE_TEST_ITEM.get())
                .addTags(ItemTags.SWORDS, ItemTags.AXES)
                .addOptionalTag(Tags.Items.MELEE_WEAPON_TOOLS);

        tag(ModTags.UPGRADE_STONES)
                .add(ModItems.HEAVEN_STONE.get(), ModItems.POWER_STONE.get(), ModItems.LUCK_STONE.get());
    }
}
