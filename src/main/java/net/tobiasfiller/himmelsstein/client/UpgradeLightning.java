package net.tobiasfiller.himmelsstein.client;

import java.util.List;

import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.util.RandomSource;
import net.tobiasfiller.himmelsstein.core.utility.UpgradeGlow;

/**
 * Jagged lightning around upgraded items.
 *
 * <p>Where the bolts go is described by a {@link Frame}:</p>
 * <ul>
 *   <li>baked melee weapons follow the vanilla sword layout (grip bottom left, tip top right),</li>
 *   <li>bows and crossbows circle the model centre and jump along the item outline,</li>
 *   <li>custom rendered models (trident, modded weapons) use their longest axis, with the grip at
 *       the end closer to the item origin.</li>
 * </ul>
 * <p>The shape is re-rolled every {@link #REROLL_MS} ms.</p>
 */
final class UpgradeLightning {
    private static final long REROLL_MS = 70L;
    private static final float ORBIT_PERIOD_MS = 1500.0F;
    private static final float CORE_HALF_WIDTH = 0.007F;
    private static final float OUTER_HALF_WIDTH = 0.024F;
    private static final float OUTER_ALPHA = 0.6F;

    private static final Vector3f Z = new Vector3f(0.0F, 0.0F, 1.0F);
    private static final Vector3f X = new Vector3f(1.0F, 0.0F, 0.0F);
    private static final Vector3f Y = new Vector3f(0.0F, 1.0F, 0.0F);
    private static final Vector3f BLADE_AXIS = new Vector3f(1.0F, 1.0F, 0.0F).normalize();
    private static final Vector3f BLADE_SIDE = new Vector3f(-1.0F, 1.0F, 0.0F).normalize();

    // Length of a vanilla sword blade in model space; custom models scale widths relative to it.
    private static final float REFERENCE_LENGTH = 1.1F;
    private static final float RING_POSITION = 0.25F;
    private static final float RING_RADIUS = 0.2F;
    private static final float BLADE_START = 0.3F;
    // Generated item models are 1/16 thick around z = 0.5; the up-jitter of a bolt is at most
    // +-0.0225, so 0.06 keeps the whole bolt outside the blade faces.
    private static final float BLADE_SURFACE_OFFSET = 0.06F;
    private static final int MAX_AXIS_SAMPLES = 4096;

    /**
     * @param bladeFrom start of the blade bolts, or {@code null} to jump along the outline instead
     * @param scale     multiplier for bolt widths and jitter
     */
    record Frame(Vector3f ringCenter, Vector3f ringU, Vector3f ringW, Vector3f axis, float ringRadius,
            Vector3f bladeFrom, Vector3f bladeTo, Vector3f side, Vector3f up, float scale) {
        static final Frame MELEE = new Frame(new Vector3f(0.36F, 0.36F, 0.5F), BLADE_SIDE, Z, BLADE_AXIS, 0.22F,
                new Vector3f(0.42F, 0.42F, 0.5F), new Vector3f(0.9F, 0.9F, 0.5F), BLADE_SIDE, Z, 1.0F);
        static final Frame PROJECTILE = new Frame(new Vector3f(0.5F, 0.5F, 0.5F), X, Y, Z, 0.4F,
                null, null, null, null, 1.0F);

        /** Frame along the principal axis of the given points, or {@code null} if they are degenerate. */
        static Frame fromPoints(List<Vector3f> points, Vector3f origin) {
            if (points.size() < 4) {
                return null;
            }
            int step = Math.max(1, points.size() / MAX_AXIS_SAMPLES);
            Vector3f centroid = new Vector3f();
            int count = 0;
            for (int i = 0; i < points.size(); i += step) {
                centroid.add(points.get(i));
                count++;
            }
            centroid.div(count);

            float xx = 0, xy = 0, xz = 0, yy = 0, yz = 0, zz = 0;
            for (int i = 0; i < points.size(); i += step) {
                Vector3f p = points.get(i);
                float dx = p.x - centroid.x, dy = p.y - centroid.y, dz = p.z - centroid.z;
                xx += dx * dx; xy += dx * dy; xz += dx * dz;
                yy += dy * dy; yz += dy * dz; zz += dz * dz;
            }
            // Power iteration for the eigenvector with the largest eigenvalue.
            Vector3f axis = new Vector3f(1.0F, 0.7F, 0.4F).normalize();
            for (int iteration = 0; iteration < 16; iteration++) {
                Vector3f next = new Vector3f(
                        xx * axis.x + xy * axis.y + xz * axis.z,
                        xy * axis.x + yy * axis.y + yz * axis.z,
                        xz * axis.x + yz * axis.y + zz * axis.z);
                if (next.lengthSquared() < 1.0E-12F) {
                    return null;
                }
                axis = next.normalize();
            }

            float min = Float.MAX_VALUE;
            float max = -Float.MAX_VALUE;
            for (int i = 0; i < points.size(); i += step) {
                float projection = new Vector3f(points.get(i)).sub(centroid).dot(axis);
                min = Math.min(min, projection);
                max = Math.max(max, projection);
            }
            float length = max - min;
            if (length < 1.0E-3F) {
                return null;
            }
            Vector3f lowEnd = new Vector3f(centroid).add(new Vector3f(axis).mul(min));
            Vector3f highEnd = new Vector3f(centroid).add(new Vector3f(axis).mul(max));
            Vector3f grip = lowEnd;
            Vector3f tip = highEnd;
            if (highEnd.distanceSquared(origin) < lowEnd.distanceSquared(origin)) {
                grip = highEnd;
                tip = lowEnd;
                axis.negate();
            }

            Vector3f u = new Vector3f(axis).cross(Math.abs(axis.y) < 0.9F ? Y : X).normalize();
            Vector3f w = new Vector3f(axis).cross(u).normalize();
            return new Frame(new Vector3f(grip).lerp(tip, RING_POSITION), u, w, axis, RING_RADIUS * length,
                    new Vector3f(grip).lerp(tip, BLADE_START), tip, u, w, length / REFERENCE_LENGTH);
        }
    }

    private UpgradeLightning() {}

    static void render(PoseStack.Pose pose, VertexConsumer bolt, VertexConsumer boltGlow, int level, int color,
            Frame frame, List<Vector3f> outline, long seed, long millis) {
        RandomSource random = RandomSource.create(seed ^ (millis / REROLL_MS) * 0x9E3779B97F4A7C15L);
        float red = (color >> 16 & 0xFF) / 255.0F;
        float green = (color >> 8 & 0xFF) / 255.0F;
        float blue = (color & 0xFF) / 255.0F;
        Painter painter = new Painter(pose, bolt, boltGlow, red, green, blue, frame.scale());

        float orbit = millis / ORBIT_PERIOD_MS * (float) (2.0D * Math.PI);
        orbitArcs(painter, random, frame, orbit);
        if (UpgradeGlow.hasBladeLightning(level)) {
            if (frame.bladeFrom() != null) {
                bladeBolts(painter, random, frame);
            } else {
                outlineBolts(painter, random, outline, frame.scale());
            }
        }
    }

    /** Three jagged arcs spinning around the ring centre. */
    private static void orbitArcs(Painter painter, RandomSource random, Frame frame, float orbit) {
        int points = 9;
        for (int arc = 0; arc < 3; arc++) {
            float start = orbit + arc * (float) (2.0D * Math.PI / 3.0D);
            Vector3f[] line = new Vector3f[points];
            for (int i = 0; i < points; i++) {
                float angle = start + 1.6F * i / (points - 1);
                float r = frame.ringRadius() * (1.0F + (random.nextFloat() - 0.5F) * 0.4F);
                float along = (random.nextFloat() - 0.5F) * 0.05F * frame.scale();
                line[i] = new Vector3f(frame.ringCenter())
                        .add(new Vector3f(frame.ringU()).mul((float) Math.cos(angle) * r))
                        .add(new Vector3f(frame.ringW()).mul((float) Math.sin(angle) * r))
                        .add(new Vector3f(frame.axis()).mul(along));
            }
            painter.polyline(line);
        }
    }

    /**
     * One or two bolts running along the blade towards the tip. Each bolt runs just in front of or
     * behind the blade, otherwise the blade faces would hide it (bolts are depth tested).
     */
    private static void bladeBolts(Painter painter, RandomSource random, Frame frame) {
        for (int b = 0; b < 2; b++) {
            if (random.nextFloat() > 0.8F) {
                continue;
            }
            float from = random.nextFloat() * 0.3F;
            float to = 0.7F + random.nextFloat() * 0.3F;
            Vector3f offset = new Vector3f(frame.up()).mul((b == 0 ? 1.0F : -1.0F) * BLADE_SURFACE_OFFSET * frame.scale());
            painter.polyline(jagged(random,
                    new Vector3f(frame.bladeFrom()).lerp(frame.bladeTo(), from).add(offset),
                    new Vector3f(frame.bladeFrom()).lerp(frame.bladeTo(), to).add(offset),
                    frame.side(), frame.up(), 9, 0.045F * frame.scale()));
        }
    }

    /** Bolts jumping between two points of the item outline. */
    private static void outlineBolts(Painter painter, RandomSource random, List<Vector3f> outline, float scale) {
        if (outline.size() < 2) {
            return;
        }
        for (int b = 0; b < 2; b++) {
            Vector3f from = outline.get(random.nextInt(outline.size()));
            Vector3f to = null;
            for (int attempt = 0; attempt < 8 && to == null; attempt++) {
                Vector3f candidate = outline.get(random.nextInt(outline.size()));
                float distance = candidate.distance(from);
                if (distance > 0.2F * scale && distance < 0.6F * scale) {
                    to = candidate;
                }
            }
            if (to != null) {
                Vector3f side = new Vector3f(to).sub(from).cross(Z).normalize();
                painter.polyline(jagged(random, from, to, side, Z, 8, 0.04F * scale));
            }
        }
    }

    private static Vector3f[] jagged(RandomSource random, Vector3f from, Vector3f to, Vector3f side, Vector3f up,
            int points, float amplitude) {
        Vector3f[] line = new Vector3f[points];
        for (int i = 0; i < points; i++) {
            float t = i / (float) (points - 1);
            float envelope = (float) Math.sin(Math.PI * t);
            line[i] = new Vector3f(from).lerp(to, t)
                    .add(new Vector3f(side).mul((random.nextFloat() - 0.5F) * 2.0F * amplitude * envelope))
                    .add(new Vector3f(up).mul((random.nextFloat() - 0.5F) * amplitude * envelope));
        }
        return line;
    }

    /** Draws polylines as crossed ribbons: band-coloured outer glow and a white core. */
    private record Painter(PoseStack.Pose pose, VertexConsumer bolt, VertexConsumer boltGlow,
            float red, float green, float blue, float scale) {
        void polyline(Vector3f[] points) {
            for (int i = 0; i + 1 < points.length; i++) {
                segment(points[i], points[i + 1]);
            }
        }

        private void segment(Vector3f from, Vector3f to) {
            Vector3f direction = new Vector3f(to).sub(from);
            if (direction.lengthSquared() < 1.0E-10F) {
                return;
            }
            direction.normalize();
            Vector3f first = new Vector3f(direction).cross(Z);
            if (first.lengthSquared() < 1.0E-6F) {
                first = new Vector3f(direction).cross(X);
            }
            first.normalize();
            Vector3f second = new Vector3f(direction).cross(first).normalize();

            for (Vector3f across : new Vector3f[] {first, second}) {
                ribbon(boltGlow, from, to, across, OUTER_HALF_WIDTH * scale, red, green, blue, 1.0F);
                ribbon(bolt, from, to, across, OUTER_HALF_WIDTH * scale, red, green, blue, OUTER_ALPHA);
                ribbon(bolt, from, to, across, CORE_HALF_WIDTH * scale, 1.0F, 1.0F, 1.0F, 1.0F);
            }
        }

        private void ribbon(VertexConsumer consumer, Vector3f from, Vector3f to, Vector3f across, float halfWidth,
                float r, float g, float b, float a) {
            float ox = across.x * halfWidth;
            float oy = across.y * halfWidth;
            float oz = across.z * halfWidth;
            consumer.addVertex(pose, from.x - ox, from.y - oy, from.z - oz).setColor(r, g, b, a);
            consumer.addVertex(pose, from.x + ox, from.y + oy, from.z + oz).setColor(r, g, b, a);
            consumer.addVertex(pose, to.x + ox, to.y + oy, to.z + oz).setColor(r, g, b, a);
            consumer.addVertex(pose, to.x - ox, to.y - oy, to.z - oz).setColor(r, g, b, a);
        }
    }
}
