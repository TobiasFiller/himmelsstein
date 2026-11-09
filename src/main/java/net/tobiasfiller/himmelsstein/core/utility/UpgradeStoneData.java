package net.tobiasfiller.himmelsstein.core.utility;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.tobiasfiller.himmelsstein.Himmelsstein;

/**
 * Data class for upgrade stone properties.
 * 
 * <p>This class holds the configuration data for each upgrade stone type.
 * In a future version, this data should be loaded from datapack JSON files,
 * but for now it's hardcoded based on the design document.</p>
 */
public final class UpgradeStoneData {
    
    public enum FailMode {
        KEEP,
        DESTROY
    }

    private final int maxLevel;
    private final List<Double> successChance;
    private final FailMode failMode;
    private final Integer destroyAboveLevel;

    public UpgradeStoneData(int maxLevel, List<Double> successChance, FailMode failMode, Integer destroyAboveLevel) {
        this.maxLevel = maxLevel;
        this.successChance = successChance;
        this.failMode = failMode;
        this.destroyAboveLevel = destroyAboveLevel;
    }

    public int getMaxLevel() {
        return maxLevel;
    }

    public List<Double> getSuccessChance() {
        return successChance;
    }

    public FailMode getFailMode() {
        return failMode;
    }

    public Integer getDestroyAboveLevel() {
        return destroyAboveLevel;
    }

    /**
     * Gets the effective fail mode for a given current level.
     * If destroyAboveLevel is set and currentLevel > destroyAboveLevel, returns DESTROY.
     * Otherwise returns the configured failMode.
     */
    public FailMode getEffectiveFailMode(int currentLevel) {
        if (destroyAboveLevel != null && currentLevel > destroyAboveLevel) {
            return FailMode.DESTROY;
        }
        return failMode;
    }

    /**
     * Gets the success chance for upgrading from the given current level.
     * Returns 0 if the level is out of bounds.
     */
    public double getSuccessChanceForLevel(int currentLevel) {
        if (currentLevel < 0 || currentLevel >= successChance.size()) {
            return 0.0;
        }
        return successChance.get(currentLevel);
    }

    // Pre-defined stone data based on the design document
    
    private static final UpgradeStoneData HEAVEN_STONE_DATA = new UpgradeStoneData(
            30,
            Arrays.asList(
                100.0, 100.0, 100.0, 75.0, 60.0, 50.0, 42.86, 37.5, 33.33, 30.0,
                27.27, 25.0, 23.08, 21.43, 20.0, 18.75, 17.65, 16.67, 15.79, 15.0,
                14.29, 13.64, 13.04, 12.5, 12.0, 11.54, 11.11, 10.71, 10.34, 10.0
            ),
            FailMode.KEEP,
            null
    );

    private static final UpgradeStoneData POWER_STONE_DATA = new UpgradeStoneData(
            8,
            Arrays.asList(100.0, 75.0, 50.0, 45.0, 40.0, 30.0, 25.0, 15.0),
            FailMode.KEEP,
            3  // Destroy on failure when current level > 3 (i.e., at level 4+)
    );

    private static final UpgradeStoneData LUCK_STONE_DATA = new UpgradeStoneData(
            10,
            Arrays.asList(
                100.0, 100.0, 100.0, 100.0, 100.0, 100.0, 100.0, 100.0, 100.0, 100.0
            ),
            FailMode.KEEP,
            null
    );

    private static final Map<ResourceLocation, UpgradeStoneData> STONE_DATA_MAP = new HashMap<>();

    static {
        STONE_DATA_MAP.put(
            ResourceLocation.fromNamespaceAndPath(Himmelsstein.MODID, "heaven_stone"),
            HEAVEN_STONE_DATA
        );
        STONE_DATA_MAP.put(
            ResourceLocation.fromNamespaceAndPath(Himmelsstein.MODID, "power_stone"),
            POWER_STONE_DATA
        );
        STONE_DATA_MAP.put(
            ResourceLocation.fromNamespaceAndPath(Himmelsstein.MODID, "luck_stone"),
            LUCK_STONE_DATA
        );
    }

    /**
     * Gets the upgrade stone data for a given item.
     */
    public static UpgradeStoneData getData(Item item) {
        ResourceLocation itemId = ResourceLocation.parse(item.getDescriptionId());
        // descriptionId is like "item.himmelsstein.heaven_stone"
        if (Himmelsstein.MODID.equals(itemId.getNamespace())) {
            ResourceLocation key = ResourceLocation.fromNamespaceAndPath(Himmelsstein.MODID, itemId.getPath());
            return STONE_DATA_MAP.get(key);
        }
        return null;
    }

    /**
     * Gets the upgrade stone data for a given item registry name.
     */
    public static UpgradeStoneData getData(ResourceLocation itemId) {
        return STONE_DATA_MAP.get(itemId);
    }

    /**
     * Checks if an item is an upgrade stone.
     */
    public static boolean isUpgradeStone(Item item) {
        return getData(item) != null;
    }
}
