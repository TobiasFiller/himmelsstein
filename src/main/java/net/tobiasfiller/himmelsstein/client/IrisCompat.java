package net.tobiasfiller.himmelsstein.client;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * Optional bridge to Iris/Oculus, resolved by reflection so neither mod is a build dependency.
 *
 * <p>Shader packs render the level a second time into their shadow map and run the vanilla item
 * renderer for held items there. That pass puts the shadow camera transform into the item pose, so
 * effect geometry recorded then would later be flushed with the main camera matrices and appear
 * somewhere in the world instead of on the item. The shadow flag tells the renderer to ignore that
 * pass.</p>
 */
final class IrisCompat {
    private static final Field[] SHADOW_PASS_FLAGS = findShadowPassFlags();

    private IrisCompat() {}

    /** Whether the current render pass is the shadow map pass of a shader pack. */
    static boolean isShadowPass() {
        for (Field flag : SHADOW_PASS_FLAGS) {
            try {
                if (flag.getBoolean(null)) {
                    return true;
                }
            } catch (IllegalAccessException ignored) {
                // The field is public at resolve time; nothing sensible to do if that changed.
            }
        }
        return false;
    }

    private static Field[] findShadowPassFlags() {
        // Iris / NeOculus on NeoForge, plus the package of the older Oculus fork.
        String[] renderers = {
                "net.irisshaders.iris.shadows.ShadowRenderer",
                "net.coderbot.iris.pipeline.ShadowRenderer"
        };
        List<Field> flags = new ArrayList<>(renderers.length);
        for (String renderer : renderers) {
            try {
                Field flag = Class.forName(renderer, false, IrisCompat.class.getClassLoader())
                        .getField("ACTIVE");
                if (flag.getType() == boolean.class) {
                    flags.add(flag);
                }
            } catch (ClassNotFoundException | NoSuchFieldException | RuntimeException ignored) {
                // Not installed, or a future version moved the flag: no shadow pass guard.
            }
        }
        return flags.toArray(Field[]::new);
    }
}
