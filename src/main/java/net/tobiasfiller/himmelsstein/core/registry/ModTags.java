package net.tobiasfiller.himmelsstein.core.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.tobiasfiller.himmelsstein.Himmelsstein;

public final class ModTags {
    public static final TagKey<Item> UPGRADABLE_WEAPON = tag("upgradable/weapon");
    public static final TagKey<Item> UPGRADABLE_PROJECTILE = tag("upgradable/projectile");

    private static TagKey<Item> tag(String path) {
        return TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath(Himmelsstein.MODID, path));
    }

    private ModTags() {}
}
