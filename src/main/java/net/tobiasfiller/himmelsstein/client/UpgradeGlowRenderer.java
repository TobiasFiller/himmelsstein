package net.tobiasfiller.himmelsstein.client;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Vector3f;

import com.google.gson.JsonSyntaxException;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexMultiConsumer;
import com.mojang.blaze3d.vertex.VertexSorting;

import net.minecraft.Util;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.tobiasfiller.himmelsstein.Himmelsstein;
import net.tobiasfiller.himmelsstein.core.utility.UpgradeGlow;
import net.tobiasfiller.himmelsstein.core.utility.UpgradeHelper;

/**
 * Visual effects of held upgraded items: coloured glint, soft halo, wide outward light and lightning.
 *
 * <p>Glint and lightning are drawn onto the screen. Halo and wide light are flat colour silhouettes
 * in two offscreen targets that a post chain blurs and adds onto the screen at the end of the frame.
 * World items (third person, other players, mobs) are collected during the level pass and flushed
 * after it, depth-tested against the world; first-person items are flushed right after the hands.</p>
 *
 * <p>Baked item models are re-emitted from their quads. Items with a custom renderer (trident,
 * modded weapons) are captured while they draw: their vertices are mirrored into the effect buffers.</p>
 */
public final class UpgradeGlowRenderer {
    private static final ResourceLocation POST_CHAIN_ID =
            ResourceLocation.fromNamespaceAndPath(Himmelsstein.MODID, "shaders/post/upgrade_glow.json");
    private static final String GLOW_TARGET = "glow";
    private static final String WIDE_TARGET = "wide";
    private static final String MIST_TARGET = "mist";
    private static final String MIST_RESULT = "swap";
    private static final String HALO_RESULT = "halo";

    // Halo width in pixels at 1080p for +1 and +30, scaled with the window height. The wide
    // light uses a multiple of this (RadiusMultiplier in the post chain).
    private static final float MIN_RADIUS = 4.0F;
    private static final float MAX_RADIUS = 16.0F;
    private static final float REFERENCE_HEIGHT = 1080.0F;

    // Breathing: +-15 % brightness, 2 s per cycle. Vertex colours cannot exceed 1, so silhouettes
    // are stored scaled down by the pulse peak and the post chain gain restores it.
    private static final float PULSE_AMPLITUDE = 0.15F;
    private static final float PULSE_PERIOD_MS = 2000.0F;

    private static final float GLINT_STRENGTH = 0.75F;

    private enum Phase {
        NONE,
        WORLD,
        HAND
    }

    /** Per-item effect values for the current frame. */
    private record Effect(int level, int color, float glowBrightness, float glintBrightness, boolean wide,
            boolean lightning, long millis) {
        static Effect of(int level) {
            long millis = Util.getMillis();
            float intensity = UpgradeGlow.intensity(level);
            float pulse = 1.0F + PULSE_AMPLITUDE * (float) Math.sin(millis / PULSE_PERIOD_MS * 2.0D * Math.PI);
            return new Effect(level, UpgradeGlow.color(level), intensity * pulse / (1.0F + PULSE_AMPLITUDE),
                    intensity * GLINT_STRENGTH, UpgradeGlow.hasWideAura(level), UpgradeGlow.hasHandleLightning(level),
                    millis);
        }

        float red() {
            return (color >> 16 & 0xFF) / 255.0F;
        }

        float green() {
            return (color >> 8 & 0xFF) / 255.0F;
        }

        float blue() {
            return (color & 0xFF) / 255.0F;
        }
    }

    private static final RandomSource RANDOM = RandomSource.create();
    private static final UpgradeEffectBuffers BUFFERS = new UpgradeEffectBuffers();

    private static PostChain postChain;
    private static boolean postChainFailed;
    private static int chainWidth = -1;
    private static int chainHeight = -1;

    private static Phase phase = Phase.NONE;
    private static boolean worldRecorded;
    private static boolean frameDirty;
    private static boolean targetsNeedClear;
    private static int maxLevelThisFrame;
    private static CustomCapture activeCapture;

    private UpgradeGlowRenderer() {}

    /** Post chain resources are reloaded lazily after a resource reload. */
    public static void onResourcesReloaded() {
        if (postChain != null) {
            postChain.close();
            postChain = null;
        }
        postChainFailed = false;
        chainWidth = -1;
        chainHeight = -1;
    }

    static RenderTarget glowTarget() {
        return postChain == null ? null : postChain.getTempTarget(GLOW_TARGET);
    }

    static RenderTarget wideTarget() {
        return postChain == null ? null : postChain.getTempTarget(WIDE_TARGET);
    }

    static RenderTarget mistTarget() {
        return postChain == null ? null : postChain.getTempTarget(MIST_TARGET);
    }

    // ---------------------------------------------------------------- frame lifecycle

    public static void beginFrame() {
        phase = Phase.NONE;
        worldRecorded = false;
        frameDirty = false;
        maxLevelThisFrame = 0;
        activeCapture = null;
        if (!UpgradeRenderTypes.shadersReady() || !ensurePostChain()) {
            return;
        }
        if (targetsNeedClear) {
            glowTarget().clear(Minecraft.ON_OSX);
            wideTarget().clear(Minecraft.ON_OSX);
            mistTarget().clear(Minecraft.ON_OSX);
            Minecraft.getInstance().getMainRenderTarget().bindWrite(false);
            targetsNeedClear = false;
        }
        phase = Phase.WORLD;
    }

    /** Called after the level rendered; restores the world matrices the vertices were recorded with. */
    public static void flushWorld(Matrix4f modelViewMatrix, Matrix4f projectionMatrix) {
        if (phase != Phase.WORLD) {
            return;
        }
        phase = Phase.NONE;
        if (!worldRecorded) {
            return;
        }
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        glowTarget().copyDepthFrom(main);
        wideTarget().copyDepthFrom(main);
        mistTarget().copyDepthFrom(main);
        main.bindWrite(false);

        RenderSystem.backupProjectionMatrix();
        RenderSystem.setProjectionMatrix(projectionMatrix, VertexSorting.DISTANCE_TO_ORIGIN);
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        modelView.mul(modelViewMatrix);
        RenderSystem.applyModelViewMatrix();

        BUFFERS.flush(false);

        modelView.popMatrix();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.restoreProjectionMatrix();
    }

    public static void beginHand() {
        if (postChain != null && UpgradeRenderTypes.shadersReady()) {
            phase = Phase.HAND;
        }
    }

    /** Called right after the hands rendered, while the hand projection is still active. */
    public static void flushHand() {
        if (phase != Phase.HAND) {
            return;
        }
        phase = Phase.NONE;
        BUFFERS.flush(true);
    }

    public static void composite(float partialTick) {
        phase = Phase.NONE;
        if (!frameDirty || postChain == null) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        int width = minecraft.getWindow().getWidth();
        int height = minecraft.getWindow().getHeight();

        float scale = Math.max(0.5F, height / REFERENCE_HEIGHT);
        float radius = (MIN_RADIUS + (MAX_RADIUS - MIN_RADIUS) * UpgradeGlow.progress(maxLevelThisFrame)) * scale;
        postChain.setUniform("Radius", Math.max(1.0F, radius));
        postChain.process(partialTick);

        RenderTarget main = minecraft.getMainRenderTarget();
        main.bindWrite(false);
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE);
        postChain.getTempTarget(HALO_RESULT).blitToScreen(width, height, false);
        if (UpgradeGlow.hasWideAura(maxLevelThisFrame)) {
            wideTarget().blitToScreen(width, height, false);
        }
        if (UpgradeGlow.hasHandAura(maxLevelThisFrame)) {
            // The mask pass writes the item-occluded mist into the swap target.
            postChain.getTempTarget(MIST_RESULT).blitToScreen(width, height, false);
        }
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        main.bindWrite(true);

        targetsNeedClear = true;
    }

    // ---------------------------------------------------------------- baked item models

    /** Whether the upgrade glint replaces the vanilla enchantment glint for this render call. */
    public static boolean replacesFoil(ItemStack stack, ItemDisplayContext context) {
        return (context == ItemDisplayContext.GUI || phaseTypesFor(context) != null)
                && UpgradeHelper.isUpgradable(stack) && UpgradeHelper.getLevel(stack) > 0;
    }

    public static void render(ItemStack stack, ItemDisplayContext context, PoseStack poseStack, BakedModel model,
            MultiBufferSource bufferSource) {
        if (context == ItemDisplayContext.GUI) {
            renderGui(stack, poseStack, model, bufferSource);
            return;
        }
        UpgradeRenderTypes.PhaseTypes types = phaseTypesFor(context);
        if (types == null || model.isCustomRenderer()) {
            return;
        }
        UpgradeHelper.Category category = UpgradeHelper.getCategory(stack);
        int level = UpgradeHelper.getLevel(stack);
        if (category == UpgradeHelper.Category.NONE || level <= 0) {
            return;
        }
        Effect effect = Effect.of(level);
        boolean hand = types.hand();
        boolean melee = category == UpgradeHelper.Category.WEAPON;

        VertexConsumer glow = BUFFERS.get(types.glow(), hand, UpgradeEffectBuffers.SILHOUETTE);
        VertexConsumer wideGlow = effect.wide() ? BUFFERS.get(types.wide(), hand, UpgradeEffectBuffers.SILHOUETTE) : null;
        VertexConsumer glint = BUFFERS.get(types.glint(), hand, UpgradeEffectBuffers.GLINT);
        List<Vector3f> outline = effect.lightning() && !melee ? new ArrayList<>() : null;

        PoseStack.Pose pose = poseStack.last();
        for (BakedModel pass : model.getRenderPasses(stack, true)) {
            for (Direction direction : Direction.values()) {
                for (BakedQuad quad : quads(pass, direction)) {
                    emit(quad, pose, glow, wideGlow, glint, effect, outline);
                }
            }
            for (BakedQuad quad : quads(pass, null)) {
                emit(quad, pose, glow, wideGlow, glint, effect, outline);
            }
        }

        if (UpgradeGlow.hasHandAura(level)) {
            renderHandAura(types, pose, effect, hand, category == UpgradeHelper.Category.PROJECTILE);
        }

        if (effect.lightning()) {
            renderLightning(types, pose, effect, melee ? UpgradeLightning.Frame.MELEE
                    : UpgradeLightning.Frame.PROJECTILE, outline == null ? List.of() : outline, stack);
        }
        markRecorded(level);
    }

    /**
     * Glint on an item icon in a GUI. It is written into the GUI's own buffer source, so it is
     * flushed together with the item and stays below later GUI layers (tooltips, dragged stack).
     */
    private static void renderGui(ItemStack stack, PoseStack poseStack, BakedModel model,
            MultiBufferSource bufferSource) {
        if (model.isCustomRenderer()) {
            return;
        }
        int level = UpgradeHelper.getLevel(stack);
        if (UpgradeHelper.getCategory(stack) == UpgradeHelper.Category.NONE || level <= 0) {
            return;
        }
        Effect effect = Effect.of(level);
        float scale = effect.glintBrightness();
        float red = effect.red() * scale;
        float green = effect.green() * scale;
        float blue = effect.blue() * scale;

        VertexConsumer glint = bufferSource.getBuffer(UpgradeRenderTypes.GUI_GLINT);
        PoseStack.Pose pose = poseStack.last();
        for (BakedModel pass : model.getRenderPasses(stack, true)) {
            for (Direction direction : Direction.values()) {
                for (BakedQuad quad : quads(pass, direction)) {
                    put(glint, pose, quad, red, green, blue);
                }
            }
            for (BakedQuad quad : quads(pass, null)) {
                put(glint, pose, quad, red, green, blue);
            }
        }
    }

    private static List<BakedQuad> quads(BakedModel model, Direction direction) {
        RANDOM.setSeed(42L);
        return model.getQuads(null, direction, RANDOM, ModelData.EMPTY, null);
    }

    private static void emit(BakedQuad quad, PoseStack.Pose pose, VertexConsumer glow, VertexConsumer wideGlow,
            VertexConsumer glint, Effect effect, List<Vector3f> outline) {
        float glowScale = effect.glowBrightness();
        float glintScale = effect.glintBrightness();
        put(glow, pose, quad, effect.red() * glowScale, effect.green() * glowScale, effect.blue() * glowScale);
        if (wideGlow != null) {
            put(wideGlow, pose, quad, effect.red() * glowScale, effect.green() * glowScale, effect.blue() * glowScale);
        }
        put(glint, pose, quad, effect.red() * glintScale, effect.green() * glintScale, effect.blue() * glintScale);

        // Side faces of generated item models trace the sprite outline.
        if (outline != null && quad.getDirection().getAxis() != Direction.Axis.Z) {
            outline.add(center(quad));
        }
    }

    private static void put(VertexConsumer consumer, PoseStack.Pose pose, BakedQuad quad, float red, float green,
            float blue) {
        consumer.putBulkData(pose, quad, red, green, blue, 1.0F, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
    }

    private static Vector3f center(BakedQuad quad) {
        int[] vertices = quad.getVertices();
        int stride = vertices.length / 4;
        Vector3f center = new Vector3f();
        for (int i = 0; i < 4; i++) {
            center.add(Float.intBitsToFloat(vertices[i * stride]), Float.intBitsToFloat(vertices[i * stride + 1]),
                    Float.intBitsToFloat(vertices[i * stride + 2]));
        }
        return center.mul(0.25F);
    }

    // ---------------------------------------------------------------- misty hand aura

    // Swords hold at the lower part of the model; bows/crossbows are gripped in the middle.
    private static final Vector3f GRIP_ANCHOR = new Vector3f(0.25F, 0.25F, 0.5F);
    private static final Vector3f MIDDLE_ANCHOR = new Vector3f(0.5F, 0.5F, 0.5F);

    /**
     * Soft, cloudy aura around the hand holding the item. Three overlapping quads are billboarded
     * towards the camera and drawn into the mist target, which the post chain blurs and adds on top.
     */
    private static void renderHandAura(UpgradeRenderTypes.PhaseTypes types, PoseStack.Pose pose, Effect effect,
            boolean hand, boolean middleAnchor) {
        float progress = UpgradeGlow.handAuraProgress(effect.level());
        float size = (1.2F + 1.0F * progress) / 3.0F;
        Vector3f anchor = middleAnchor ? MIDDLE_ANCHOR : GRIP_ANCHOR;
        Vector3f center = pose.pose().transformPosition(new Vector3f(anchor));
        Vector3f unit = pose.pose().transformPosition(new Vector3f(anchor.x + 1.0F, anchor.y, anchor.z)).sub(center);
        float half = Math.max(0.05F, unit.length() * size * 0.5F);
        renderHandAura(types, center, half, effect, hand);
    }

    private static void renderHandAura(UpgradeRenderTypes.PhaseTypes types, Vector3f center, float half,
            Effect effect, boolean hand) {
        float progress = UpgradeGlow.handAuraProgress(effect.level());
        float brightness = effect.glowBrightness() * (0.55F + 0.45F * progress) * 2.0F;
        int color = FastColor.ARGB32.color(255, Math.round(effect.red() * brightness * 255.0F),
                Math.round(effect.green() * brightness * 255.0F),
                Math.round(effect.blue() * brightness * 255.0F));

        // Both phases write into a pose space that the shader rotates by the camera view. The
        // first-person pose is pre-multiplied by the inverse camera rotation, so the world-space
        // camera axes are the correct billboard axes in both cases.
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        Vector3f right = new Vector3f(camera.getLeftVector()).negate();
        Vector3f up = new Vector3f(camera.getUpVector());

        VertexConsumer mist = BUFFERS.get(types.mist(), hand, UpgradeEffectBuffers.SILHOUETTE);
        long millis = effect.millis();
        for (int i = 0; i < 3; i++) {
            float angle = (float) (millis * 0.00018D + i * Math.PI / 3.0D);
            float scale = (0.72F + 0.18F * i) * half;
            float cos = (float) Math.cos(angle);
            float sin = (float) Math.sin(angle);

            Vector3f r = new Vector3f(right).mul(cos);
            r.add(new Vector3f(up).mul(sin));
            r.mul(scale);
            Vector3f u = new Vector3f(up).mul(cos);
            u.sub(new Vector3f(right).mul(sin));
            u.mul(scale);

            emitMistQuad(mist, center, r, u, color);
        }
    }

    private static void emitMistQuad(VertexConsumer consumer, Vector3f center, Vector3f r, Vector3f u, int color) {
        float x0 = center.x - r.x - u.x;
        float y0 = center.y - r.y - u.y;
        float z0 = center.z - r.z - u.z;
        float x1 = center.x + r.x - u.x;
        float y1 = center.y + r.y - u.y;
        float z1 = center.z + r.z - u.z;
        float x2 = center.x + r.x + u.x;
        float y2 = center.y + r.y + u.y;
        float z2 = center.z + r.z + u.z;
        float x3 = center.x - r.x + u.x;
        float y3 = center.y - r.y + u.y;
        float z3 = center.z - r.z + u.z;

        consumer.addVertex(x0, y0, z0, color, 0.0F, 1.0F, OverlayTexture.NO_OVERLAY, LightTexture.FULL_BRIGHT,
                0.0F, 0.0F, 1.0F);
        consumer.addVertex(x1, y1, z1, color, 1.0F, 1.0F, OverlayTexture.NO_OVERLAY, LightTexture.FULL_BRIGHT,
                0.0F, 0.0F, 1.0F);
        consumer.addVertex(x2, y2, z2, color, 1.0F, 0.0F, OverlayTexture.NO_OVERLAY, LightTexture.FULL_BRIGHT,
                0.0F, 0.0F, 1.0F);
        consumer.addVertex(x3, y3, z3, color, 0.0F, 0.0F, OverlayTexture.NO_OVERLAY, LightTexture.FULL_BRIGHT,
                0.0F, 0.0F, 1.0F);
    }

    // ---------------------------------------------------------------- custom item renderers

    /**
     * Wraps the buffer source handed to a custom item renderer, so its geometry also feeds the
     * effects. Returns the original source when the item gets no effects in this call.
     */
    public static MultiBufferSource wrapCustomRenderer(ItemStack stack, ItemDisplayContext context,
            PoseStack poseStack, MultiBufferSource original) {
        activeCapture = null;
        UpgradeRenderTypes.PhaseTypes types = phaseTypesFor(context);
        int level = UpgradeHelper.getLevel(stack);
        if (types == null || !UpgradeHelper.isUpgradable(stack) || level <= 0) {
            return original;
        }
        // The item origin, used to tell the grip end of the model from the tip.
        Vector3f origin = poseStack.last().pose().transformPosition(new Vector3f(0.5F, 0.5F, 0.5F));
        activeCapture = new CustomCapture(original, types, Effect.of(level), stack, origin);
        return activeCapture;
    }

    /** Called after the custom renderer finished; adds the lightning along the captured shape. */
    public static void finishCustomRenderer() {
        CustomCapture capture = activeCapture;
        activeCapture = null;
        if (capture == null || !capture.recorded) {
            return;
        }
        if (UpgradeGlow.hasHandAura(capture.effect.level())) {
            renderCapturedHandAura(capture);
        }
        if (capture.effect.lightning()) {
            UpgradeLightning.Frame frame = UpgradeLightning.Frame.fromPoints(capture.points, capture.origin);
            if (frame != null) {
                // Captured vertices are already in view space, so no extra pose is applied.
                renderLightning(capture.types, new PoseStack().last(), capture.effect, frame, List.of(),
                        capture.stack);
            }
        }
        markRecorded(capture.effect.level());
    }

    /** Mist anchored at the centre of the geometry a custom renderer produced. */
    private static void renderCapturedHandAura(CustomCapture capture) {
        if (capture.points.isEmpty()) {
            return;
        }
        Vector3f center = new Vector3f();
        for (Vector3f point : capture.points) {
            center.add(point);
        }
        center.mul(1.0F / capture.points.size());
        float radius = 0.0F;
        for (Vector3f point : capture.points) {
            radius = Math.max(radius, center.distance(point));
        }
        // The trident model is much larger than a sword, so its aura needs a smaller factor.
        float sizeFactor = capture.stack.is(Items.TRIDENT) ? 0.5F : 1.0F;
        renderHandAura(capture.types, center, Math.max(0.05F, radius * 1.6F / 3.0F * sizeFactor), capture.effect,
                capture.types.hand());
    }

    /** Buffer source that mirrors entity-format geometry into the glow, wide and glint buffers. */
    private static final class CustomCapture implements MultiBufferSource {
        private final MultiBufferSource delegate;
        private final UpgradeRenderTypes.PhaseTypes types;
        private final Effect effect;
        private final ItemStack stack;
        private final Vector3f origin;
        private final List<Vector3f> points = new ArrayList<>();
        private final Map<RenderType, VertexConsumer> wrapped = new HashMap<>();
        private boolean recorded;

        private CustomCapture(MultiBufferSource delegate, UpgradeRenderTypes.PhaseTypes types, Effect effect,
                ItemStack stack, Vector3f origin) {
            this.delegate = delegate;
            this.types = types;
            this.effect = effect;
            this.stack = stack;
            this.origin = origin;
        }

        @Override
        public VertexConsumer getBuffer(RenderType type) {
            if (UpgradeRenderTypes.isVanillaGlint(type)) {
                // The upgrade glint replaces the purple enchantment glint.
                return NoopConsumer.INSTANCE;
            }
            VertexConsumer base = delegate.getBuffer(type);
            if (type.format() != DefaultVertexFormat.NEW_ENTITY) {
                return base;
            }
            Optional<ResourceLocation> texture = UpgradeRenderTypes.textureOf(type);
            if (texture.isEmpty()) {
                return base;
            }
            VertexConsumer mirror = wrapped.computeIfAbsent(type, key -> createMirror(texture.get()));
            recorded = true;
            return VertexMultiConsumer.create(base, mirror);
        }

        private VertexConsumer createMirror(ResourceLocation texture) {
            boolean hand = types.hand();
            UpgradeRenderTypes.TexturedTypes textured = UpgradeRenderTypes.textured(hand, texture);
            float glow = effect.glowBrightness();
            float glint = effect.glintBrightness();
            List<VertexConsumer> mirrors = new ArrayList<>();
            mirrors.add(new RecolorConsumer(BUFFERS.get(textured.glow(), hand, UpgradeEffectBuffers.SILHOUETTE),
                    effect, glow, points));
            if (effect.wide()) {
                mirrors.add(new RecolorConsumer(BUFFERS.get(textured.wide(), hand, UpgradeEffectBuffers.SILHOUETTE),
                        effect, glow, null));
            }
            mirrors.add(new RecolorConsumer(BUFFERS.get(textured.glint(), hand, UpgradeEffectBuffers.GLINT),
                    effect, glint, null));
            return mirrors.size() == 1 ? mirrors.getFirst()
                    : VertexMultiConsumer.create(mirrors.toArray(VertexConsumer[]::new));
        }
    }

    /** Forwards vertices with a fixed colour and optionally records their positions. */
    private static final class RecolorConsumer implements VertexConsumer {
        private static final int MAX_POINTS = 16384;

        private final VertexConsumer delegate;
        private final int red;
        private final int green;
        private final int blue;
        private final List<Vector3f> points;

        private RecolorConsumer(VertexConsumer delegate, Effect effect, float brightness, List<Vector3f> points) {
            this.delegate = delegate;
            this.red = Math.round(effect.red() * brightness * 255.0F);
            this.green = Math.round(effect.green() * brightness * 255.0F);
            this.blue = Math.round(effect.blue() * brightness * 255.0F);
            this.points = points;
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            delegate.addVertex(x, y, z).setColor(red, green, blue, 255);
            record(x, y, z);
            return this;
        }

        @Override
        public void addVertex(float x, float y, float z, int color, float u, float v, int packedOverlay,
                int packedLight, float normalX, float normalY, float normalZ) {
            delegate.addVertex(x, y, z, FastColor.ARGB32.color(255, red, green, blue), u, v, packedOverlay,
                    packedLight, normalX, normalY, normalZ);
            record(x, y, z);
        }

        @Override
        public VertexConsumer setColor(int r, int g, int b, int a) {
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            delegate.setUv(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            delegate.setUv1(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            delegate.setUv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            delegate.setNormal(x, y, z);
            return this;
        }

        private void record(float x, float y, float z) {
            if (points != null && points.size() < MAX_POINTS) {
                points.add(new Vector3f(x, y, z));
            }
        }
    }

    private enum NoopConsumer implements VertexConsumer {
        INSTANCE;

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            return this;
        }

        @Override
        public VertexConsumer setColor(int r, int g, int b, int a) {
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            return this;
        }
    }

    // ---------------------------------------------------------------- shared

    private static void renderLightning(UpgradeRenderTypes.PhaseTypes types, PoseStack.Pose pose, Effect effect,
            UpgradeLightning.Frame frame, List<Vector3f> outline, ItemStack stack) {
        boolean hand = types.hand();
        UpgradeLightning.render(pose, BUFFERS.get(types.bolt(), hand, UpgradeEffectBuffers.BOLT),
                BUFFERS.get(types.boltGlow(), hand, UpgradeEffectBuffers.SILHOUETTE), effect.level(), effect.color(),
                frame, outline, System.identityHashCode(stack), effect.millis());
    }

    private static void markRecorded(int level) {
        if (phase == Phase.WORLD) {
            worldRecorded = true;
        }
        frameDirty = true;
        maxLevelThisFrame = Math.max(maxLevelThisFrame, level);
    }

    private static UpgradeRenderTypes.PhaseTypes phaseTypesFor(ItemDisplayContext context) {
        if (phase == Phase.WORLD && (context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND
                || context == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND)) {
            return UpgradeRenderTypes.WORLD;
        }
        if (phase == Phase.HAND && (context == ItemDisplayContext.FIRST_PERSON_LEFT_HAND
                || context == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND)) {
            return UpgradeRenderTypes.HAND;
        }
        return null;
    }

    private static boolean ensurePostChain() {
        Minecraft minecraft = Minecraft.getInstance();
        RenderTarget main = minecraft.getMainRenderTarget();
        if (postChain == null && !postChainFailed) {
            try {
                postChain = new PostChain(minecraft.getTextureManager(), minecraft.getResourceManager(), main,
                        POST_CHAIN_ID);
                chainWidth = -1;
                targetsNeedClear = true;
            } catch (IOException | JsonSyntaxException exception) {
                Himmelsstein.LOGGER.warn("Failed to load upgrade glow post chain {}", POST_CHAIN_ID, exception);
                postChainFailed = true;
                postChain = null;
            }
        }
        if (postChain == null) {
            return false;
        }
        if (chainWidth != main.width || chainHeight != main.height) {
            postChain.resize(main.width, main.height);
            chainWidth = main.width;
            chainHeight = main.height;
            targetsNeedClear = true;
        }
        return true;
    }
}
