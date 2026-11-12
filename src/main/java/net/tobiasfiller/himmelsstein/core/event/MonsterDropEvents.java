package net.tobiasfiller.himmelsstein.core.event;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.tobiasfiller.himmelsstein.Himmelsstein;
import net.tobiasfiller.himmelsstein.core.utility.MonsterDropData;

/**
 * Rolls the data-driven monster drops on every hostile mob death, independent of what killed the
 * mob. The rules live in {@link MonsterDropReloadListener}.
 */
@EventBusSubscriber(modid = Himmelsstein.MODID)
public final class MonsterDropEvents {
    private MonsterDropEvents() {}

    @SubscribeEvent
    public static void onLivingDrops(LivingDropsEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide || entity.getType().getCategory() != MobCategory.MONSTER) {
            return;
        }
        // LivingDropsEvent fires even when the world disables mob loot; keep vanilla behaviour.
        if (!entity.level().getGameRules().getBoolean(GameRules.RULE_DOMOBLOOT)) {
            return;
        }
        MonsterDropData data = MonsterDropReloadListener.current();
        if (data.drops().isEmpty()) {
            return;
        }
        for (ItemStack stack : data.roll(entity, entity.getRandom())) {
            event.getDrops().add(new ItemEntity(entity.level(), entity.getX(), entity.getY(), entity.getZ(), stack));
        }
    }
}
