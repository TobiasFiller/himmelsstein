package net.tobiasfiller.himmelsstein.core.utility;

/**
 * Aura colour and intensity per upgrade level.
 *
 * <p>Pure math without client classes, so it can be covered by GameTests on the server.</p>
 */
public final class UpgradeGlow {
    public static final int LEVELS_PER_BAND = 5;

    /** Wide outward light from +5, lightning around the handle from +15, along the blade from +20. */
    public static final int WIDE_AURA_LEVEL = 5;
    public static final int HANDLE_LIGHTNING_LEVEL = 15;
    public static final int BLADE_LIGHTNING_LEVEL = 20;

    /** Colour anchors at the start of each band (+1, +6, +11, +16, +21, +26); levels in between blend. */
    private static final int[] ANCHOR_COLORS = {
            0xFFFFFF, // +1:  WHITE
            0xFFFF55, // +6:  YELLOW
            0xFFAA00, // +11: GOLD (renders orange)
            0xFF5555, // +16: RED
            0xFF55FF, // +21: LIGHT_PURPLE
            0xFFD700  // +26: gold, constant up to +30
    };

    private static final float MIN_INTENSITY = 0.02F;
    private static final float MAX_INTENSITY = 0.20F;
    /** Intensity stops rising here and stays at {@link #MAX_INTENSITY} up to +30. */
    public static final int INTENSITY_PEAK_LEVEL = 15;

    private UpgradeGlow() {}

    /** RGB colour (0xRRGGBB), blended linearly between the band anchors; 0 for +0. */
    public static int color(int level) {
        int clamped = UpgradeHelper.clampLevel(level);
        if (clamped <= 0) {
            return 0;
        }
        int anchor = (clamped - 1) / LEVELS_PER_BAND;
        if (anchor >= ANCHOR_COLORS.length - 1) {
            return ANCHOR_COLORS[ANCHOR_COLORS.length - 1];
        }
        float t = ((clamped - 1) % LEVELS_PER_BAND) / (float) LEVELS_PER_BAND;
        return lerpColor(ANCHOR_COLORS[anchor], ANCHOR_COLORS[anchor + 1], t);
    }

    /** Rises evenly from 2% at +1 to 20% at +15, then stays at 20%; 0 for +0. */
    public static float intensity(int level) {
        int clamped = UpgradeHelper.clampLevel(level);
        if (clamped <= 0) {
            return 0.0F;
        }
        float rise = (Math.min(clamped, INTENSITY_PEAK_LEVEL) - 1) / (float) (INTENSITY_PEAK_LEVEL - 1);
        return MIN_INTENSITY + (MAX_INTENSITY - MIN_INTENSITY) * rise;
    }

    /** Linear 0..1 position of the level between +1 and +30, used for the aura width. */
    public static float progress(int level) {
        int clamped = UpgradeHelper.clampLevel(level);
        if (clamped <= 0) {
            return 0.0F;
        }
        return (clamped - 1) / (float) (UpgradeHelper.MAX_LEVEL - 1);
    }

    public static boolean hasWideAura(int level) {
        return UpgradeHelper.clampLevel(level) >= WIDE_AURA_LEVEL;
    }

    public static boolean hasHandleLightning(int level) {
        return UpgradeHelper.clampLevel(level) >= HANDLE_LIGHTNING_LEVEL;
    }

    public static boolean hasBladeLightning(int level) {
        return UpgradeHelper.clampLevel(level) >= BLADE_LIGHTNING_LEVEL;
    }

    private static int lerpColor(int from, int to, float t) {
        int red = Math.round((from >> 16 & 0xFF) + ((to >> 16 & 0xFF) - (from >> 16 & 0xFF)) * t);
        int green = Math.round((from >> 8 & 0xFF) + ((to >> 8 & 0xFF) - (from >> 8 & 0xFF)) * t);
        int blue = Math.round((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * t);
        return red << 16 | green << 8 | blue;
    }
}
