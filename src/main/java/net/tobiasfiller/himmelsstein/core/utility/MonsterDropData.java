package net.tobiasfiller.himmelsstein.core.utility;

import java.util.ArrayList;
import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Data-driven monster drops, loaded from {@code data/himmelsstein/monster_drops/default.json}.
 *
 * <p>Every rule drops one item when the killed monster's <em>maximum</em> health reaches
 * {@code min_health} and the {@code chance} roll succeeds. Rules are independent, so a monster can
 * drop several configured items from one kill.</p>
 */
public record MonsterDropData(List<DropRule> drops) {
    public static final MonsterDropData EMPTY = new MonsterDropData(List.of());

    public record DropRule(Item item, double minHealth, double chance) {
        public static final Codec<DropRule> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                BuiltInRegistries.ITEM.byNameCodec().fieldOf("item").forGetter(DropRule::item),
                Codec.doubleRange(0.0D, 1.0E9D).fieldOf("min_health").forGetter(DropRule::minHealth),
                Codec.doubleRange(0.0D, 1.0D).fieldOf("chance").forGetter(DropRule::chance)
        ).apply(instance, DropRule::new));

        public boolean appliesTo(LivingEntity entity) {
            return entity.getMaxHealth() >= minHealth;
        }
    }

    public static final Codec<MonsterDropData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            DropRule.CODEC.listOf().optionalFieldOf("drops", List.of()).forGetter(MonsterDropData::drops)
    ).apply(instance, drops -> new MonsterDropData(List.copyOf(drops))));

    /** One item stack per successful rule roll; empty if the entity does not qualify. */
    public List<ItemStack> roll(LivingEntity entity, RandomSource random) {
        List<ItemStack> result = new ArrayList<>();
        for (DropRule rule : drops) {
            if (!rule.appliesTo(entity) || random.nextDouble() >= rule.chance()) {
                continue;
            }
            ItemStack stack = new ItemStack(rule.item());
            if (!stack.isEmpty()) {
                result.add(stack);
            }
        }
        return result;
    }
}
