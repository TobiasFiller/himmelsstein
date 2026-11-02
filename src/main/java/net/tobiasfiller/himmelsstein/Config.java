package net.tobiasfiller.himmelsstein;

import java.util.ArrayList;
import java.util.List;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Common config of the upgrade system.
 *
 * <p>The two tables are indexed by upgrade level (0-30) and are the single source of truth for
 * the scaled values. The server is authoritative and syncs these tables to the client on join and
 * after a config reload, so name, tooltip and the weapon attribute line use the same numbers.</p>
 */
public final class Config {
    public static final int TABLE_SIZE = 31;

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.ConfigValue<List<? extends Double>> WEAPON_DAMAGE = BUILDER
            .comment("Flat attack damage added per upgrade level (index = level, 0-30).",
                    "Default: +0.25 per level, so +10 = +2.5 and +30 = +7.5.")
            .defineListAllowEmpty("weapon_damage", defaults(0.25D), () -> 0.0D, Config::isFiniteNumber);

    public static final ModConfigSpec.ConfigValue<List<? extends Double>> PROJECTILE_DAMAGE = BUILDER
            .comment("Projectile damage bonus as a factor (index = level, 0-30).",
                    "Default: +0.05 per level, so +10 = +50% and +30 = +150%.",
                    "This is a placeholder until the bow balance question (O14) is decided.")
            .defineListAllowEmpty("projectile_damage", defaults(0.05D), () -> 0.0D, Config::isFiniteNumber);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private Config() {}

    private static boolean isFiniteNumber(Object value) {
        return value instanceof Number number && Double.isFinite(number.doubleValue());
    }

    private static List<Double> defaults(double perLevel) {
        List<Double> values = new ArrayList<>(TABLE_SIZE);
        for (int level = 0; level < TABLE_SIZE; level++) {
            values.add(level * perLevel);
        }
        return values;
    }
}
