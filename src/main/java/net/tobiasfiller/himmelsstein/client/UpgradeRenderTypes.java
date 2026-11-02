package net.tobiasfiller.himmelsstein.client;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import org.joml.Matrix4f;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.tobiasfiller.himmelsstein.Himmelsstein;
import net.tobiasfiller.himmelsstein.mixin.client.CompositeRenderTypeAccessor;
import net.tobiasfiller.himmelsstein.mixin.client.CompositeStateAccessor;
import net.tobiasfiller.himmelsstein.mixin.client.EmptyTextureStateShardAccessor;

/** Render types of the upgrade effects, one set per phase (world or first-person hand). */
public final class UpgradeRenderTypes {
    public static final ResourceLocation GLOW_SHADER_ID =
            ResourceLocation.fromNamespaceAndPath(Himmelsstein.MODID, "upgrade_glow");
    public static final ResourceLocation GLINT_SHADER_ID =
            ResourceLocation.fromNamespaceAndPath(Himmelsstein.MODID, "upgrade_glint");
    private static final ResourceLocation GLINT_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Himmelsstein.MODID, "textures/misc/upgrade_glint_item.png");

    // Item sprites cover a tiny part of the block atlas, entity textures span the whole UV range,
    // so custom models need a much smaller glint scale for the same vein size.
    private static final float ATLAS_GLINT_SCALE = 8.0F;
    private static final float CUSTOM_GLINT_SCALE = 0.5F;

    private static ShaderInstance glowShader;
    private static ShaderInstance glintShader;

    /** Render types for baked item models (block atlas) within one phase. */
    public record PhaseTypes(boolean hand, RenderType glow, RenderType wide, RenderType boltGlow, RenderType glint,
            RenderType bolt) {}

    /** Render types for geometry of custom item renderers that use their own texture. */
    public record TexturedTypes(RenderType glow, RenderType wide, RenderType glint) {}

    private static final RenderStateShard.TransparencyStateShard ADDITIVE = new RenderStateShard.TransparencyStateShard(
            "himmelsstein_additive",
            () -> {
                RenderSystem.enableBlend();
                RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE);
            },
            () -> {
                RenderSystem.disableBlend();
                RenderSystem.defaultBlendFunc();
            });

    private static final RenderStateShard.ShaderStateShard GLOW_SHADER_STATE =
            new RenderStateShard.ShaderStateShard(() -> glowShader);
    private static final RenderStateShard.ShaderStateShard GLINT_SHADER_STATE =
            new RenderStateShard.ShaderStateShard(() -> glintShader);

    public static final PhaseTypes WORLD = createPhase(false);
    public static final PhaseTypes HAND = createPhase(true);

    private static final Map<ResourceLocation, TexturedTypes> WORLD_TEXTURED = new HashMap<>();
    private static final Map<ResourceLocation, TexturedTypes> HAND_TEXTURED = new HashMap<>();

    private UpgradeRenderTypes() {}

    public static void setGlowShader(ShaderInstance shader) {
        glowShader = shader;
    }

    public static void setGlintShader(ShaderInstance shader) {
        glintShader = shader;
    }

    public static boolean shadersReady() {
        return glowShader != null && glintShader != null;
    }

    public static TexturedTypes textured(boolean hand, ResourceLocation texture) {
        Map<ResourceLocation, TexturedTypes> cache = hand ? HAND_TEXTURED : WORLD_TEXTURED;
        return cache.computeIfAbsent(texture, key -> {
            String suffix = (hand ? "hand_" : "world_") + key.getNamespace() + "_" + key.getPath().replace('/', '_');
            return new TexturedTypes(
                    silhouette("glow_" + suffix, key, offscreenDepth(hand), glowOutput(hand)),
                    silhouette("wide_" + suffix, key, offscreenDepth(hand), wideOutput(hand)),
                    glint("glint_" + suffix, key, CUSTOM_GLINT_SCALE));
        });
    }

    /** The texture a render type samples for its cutout, if it can be determined. */
    public static Optional<ResourceLocation> textureOf(RenderType type) {
        if (type instanceof CompositeRenderTypeAccessor composite) {
            Object state = composite.himmelsstein$state();
            Object texture = ((CompositeStateAccessor) state).himmelsstein$textureState();
            return ((EmptyTextureStateShardAccessor) texture).himmelsstein$cutoutTexture();
        }
        return Optional.empty();
    }

    public static boolean isVanillaGlint(RenderType type) {
        return type == RenderType.glint() || type == RenderType.glintTranslucent() || type == RenderType.entityGlint()
                || type == RenderType.entityGlintDirect() || type == RenderType.armorEntityGlint();
    }

    // ---------------------------------------------------------------- factories

    private static PhaseTypes createPhase(boolean hand) {
        String phase = hand ? "hand" : "world";
        RenderType boltGlow = RenderType.create(name("bolt_glow_" + phase), DefaultVertexFormat.POSITION_COLOR,
                VertexFormat.Mode.QUADS, 1536, false, false,
                RenderType.CompositeState.builder()
                        .setShaderState(RenderStateShard.RENDERTYPE_LIGHTNING_SHADER)
                        .setTransparencyState(RenderStateShard.NO_TRANSPARENCY)
                        .setDepthTestState(offscreenDepth(hand))
                        .setCullState(RenderStateShard.NO_CULL)
                        .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                        .setOutputState(glowOutput(hand))
                        .createCompositeState(false));
        RenderType bolt = RenderType.create(name("bolt_" + phase), DefaultVertexFormat.POSITION_COLOR,
                VertexFormat.Mode.QUADS, 1536, false, false,
                RenderType.CompositeState.builder()
                        .setShaderState(RenderStateShard.RENDERTYPE_LIGHTNING_SHADER)
                        .setTransparencyState(RenderStateShard.LIGHTNING_TRANSPARENCY)
                        .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                        .setCullState(RenderStateShard.NO_CULL)
                        .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                        .createCompositeState(false));
        return new PhaseTypes(hand,
                silhouette("glow_" + phase, InventoryMenu.BLOCK_ATLAS, offscreenDepth(hand), glowOutput(hand)),
                silhouette("wide_" + phase, InventoryMenu.BLOCK_ATLAS, offscreenDepth(hand), wideOutput(hand)),
                boltGlow,
                glint("glint_" + phase, InventoryMenu.BLOCK_ATLAS, ATLAS_GLINT_SCALE),
                bolt);
    }

    /** World glow is tested against the copied world depth; first-person glow is never occluded. */
    private static RenderStateShard.DepthTestStateShard offscreenDepth(boolean hand) {
        return hand ? RenderStateShard.NO_DEPTH_TEST : RenderStateShard.LEQUAL_DEPTH_TEST;
    }

    private static RenderStateShard.OutputStateShard glowOutput(boolean hand) {
        return output("glow_" + (hand ? "hand" : "world"), UpgradeGlowRenderer::glowTarget);
    }

    private static RenderStateShard.OutputStateShard wideOutput(boolean hand) {
        return output("wide_" + (hand ? "hand" : "world"), UpgradeGlowRenderer::wideTarget);
    }

    private static RenderType silhouette(String suffix, ResourceLocation texture,
            RenderStateShard.DepthTestStateShard depthTest, RenderStateShard.OutputStateShard output) {
        return RenderType.create(name(suffix), DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 1536, false,
                false,
                RenderType.CompositeState.builder()
                        .setShaderState(GLOW_SHADER_STATE)
                        .setTextureState(new RenderStateShard.TextureStateShard(texture, false, false))
                        .setTransparencyState(RenderStateShard.NO_TRANSPARENCY)
                        .setDepthTestState(depthTest)
                        .setCullState(RenderStateShard.NO_CULL)
                        .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                        .setOutputState(output)
                        .createCompositeState(false));
    }

    private static RenderType glint(String suffix, ResourceLocation texture, float scale) {
        // Two overlaid glint layers, running against the vanilla glint direction at different angles.
        RenderStateShard.TexturingStateShard texturing = new RenderStateShard.TexturingStateShard(
                name(suffix + "_texturing"), () -> setupGlintTexturing(scale), () -> {});
        return RenderType.create(name(suffix), DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 1536, false,
                false,
                RenderType.CompositeState.builder()
                        .setShaderState(GLINT_SHADER_STATE)
                        .setTextureState(RenderStateShard.MultiTextureStateShard.builder()
                                .add(texture, false, false)
                                .add(GLINT_TEXTURE, true, false)
                                .build())
                        .setTexturingState(texturing)
                        .setTransparencyState(ADDITIVE)
                        .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                        .setCullState(RenderStateShard.NO_CULL)
                        .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                        .createCompositeState(false));
    }

    private static RenderStateShard.OutputStateShard output(String suffix, Supplier<RenderTarget> target) {
        return new RenderStateShard.OutputStateShard(
                name(suffix + "_target"),
                () -> {
                    RenderTarget renderTarget = target.get();
                    if (renderTarget != null) {
                        renderTarget.bindWrite(false);
                    }
                },
                () -> Minecraft.getInstance().getMainRenderTarget().bindWrite(false));
    }

    private static String name(String suffix) {
        return Himmelsstein.MODID + "_upgrade_" + suffix;
    }

    private static void setupGlintTexturing(float scale) {
        if (glintShader == null) {
            return;
        }
        long time = (long) (Util.getMillis() * Minecraft.getInstance().options.glintSpeed().get() * 8.0D);
        float a = (time % 110000L) / 110000.0F;
        float b = (time % 30000L) / 30000.0F;
        float c = (time % 70000L) / 70000.0F;
        float d = (time % 45000L) / 45000.0F;
        Matrix4f first = new Matrix4f().translation(a, -b, 0.0F).rotateZ((float) (-Math.PI / 18)).scale(scale);
        Matrix4f second = new Matrix4f().translation(-d, -c, 0.0F).rotateZ((float) (-5 * Math.PI / 18))
                .scale(scale * 0.75F);
        glintShader.safeGetUniform("GlintMat1").set(first);
        glintShader.safeGetUniform("GlintMat2").set(second);
    }
}
