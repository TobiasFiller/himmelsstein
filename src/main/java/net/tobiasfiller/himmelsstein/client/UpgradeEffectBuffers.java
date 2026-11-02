package net.tobiasfiller.himmelsstein.client;

import java.util.LinkedHashMap;
import java.util.Map;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.RenderType;

/**
 * Batches the effect geometry per render type until its phase is flushed.
 *
 * <p>Unlike {@code MultiBufferSource.immediate}, render types can be added at any time (custom item
 * renderers bring their own textures), and nothing is drawn before {@link #flush(boolean)}.</p>
 */
final class UpgradeEffectBuffers {
    static final int SILHOUETTE = 0;
    static final int GLINT = 1;
    static final int BOLT = 2;
    private static final int LAYERS = 3;

    private static final class Entry {
        private final RenderType type;
        private final boolean hand;
        private final int layer;
        private final ByteBufferBuilder bytes;
        private BufferBuilder builder;

        private Entry(RenderType type, boolean hand, int layer) {
            this.type = type;
            this.hand = hand;
            this.layer = layer;
            this.bytes = new ByteBufferBuilder(type.bufferSize());
        }
    }

    private final Map<RenderType, Entry> entries = new LinkedHashMap<>();

    VertexConsumer get(RenderType type, boolean hand, int layer) {
        Entry entry = entries.computeIfAbsent(type, key -> new Entry(key, hand, layer));
        if (entry.builder == null) {
            entry.builder = new BufferBuilder(entry.bytes, type.mode(), type.format());
        }
        return entry.builder;
    }

    /** Draws everything recorded for one phase: silhouettes first, then glint, then lightning. */
    void flush(boolean hand) {
        for (int layer = 0; layer < LAYERS; layer++) {
            for (Entry entry : entries.values()) {
                if (entry.hand != hand || entry.layer != layer || entry.builder == null) {
                    continue;
                }
                MeshData mesh = entry.builder.build();
                entry.builder = null;
                if (mesh != null) {
                    entry.type.draw(mesh);
                }
            }
        }
    }
}
