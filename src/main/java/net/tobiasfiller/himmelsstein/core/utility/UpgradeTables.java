package net.tobiasfiller.himmelsstein.core.utility;

import java.util.List;

import net.neoforged.neoforge.common.ModConfigSpec;
import net.tobiasfiller.himmelsstein.Config;

/**
 * Resolves the level -> value tables.
 *
 * <p>On the server the values come from the common config. The server sends the tables to the
 * client when a player joins (and after a config reload); the received values are stored here and
 * take precedence over the local config, so tooltips and attribute lines show the server's numbers.
 * On a dedicated server nothing is ever synced, so the config stays authoritative.</p>
 */
public final class UpgradeTables {
    public static final int MAX_LEVEL = 30;

    private static volatile List<Double> syncedWeaponDamage;
    private static volatile List<Double> syncedProjectileDamage;

    private UpgradeTables() {}

    public static void applySynced(List<Double> weaponDamage, List<Double> projectileDamage) {
        syncedWeaponDamage = List.copyOf(weaponDamage);
        syncedProjectileDamage = List.copyOf(projectileDamage);
    }

    public static double weaponDamage(int level) {
        return entry(syncedWeaponDamage, Config.WEAPON_DAMAGE, level);
    }

    public static double projectileDamage(int level) {
        return entry(syncedProjectileDamage, Config.PROJECTILE_DAMAGE, level);
    }

    private static double entry(List<Double> synced, ModConfigSpec.ConfigValue<List<? extends Double>> config, int level) {
        if (level <= 0 || level > MAX_LEVEL) {
            return 0.0D;
        }

        List<? extends Double> table = synced;
        if (table == null) {
            try {
                table = config.get();
            } catch (IllegalStateException notLoadedYet) {
                return 0.0D;
            }
        }

        if (table == null || level >= table.size()) {
            return 0.0D;
        }

        Double value = table.get(level);
        return value == null || !Double.isFinite(value) ? 0.0D : value;
    }
}
