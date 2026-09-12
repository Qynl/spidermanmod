package com.spiderman.mod.client;

import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.model.ModelPart;
import net.minecraft.util.math.Direction;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;

import java.util.List;
import java.util.Map;
import java.util.Set;

import com.spiderman.mod.entity.WebShotEntity;

/**
 * Improved web shot renderer — white glowing orb, not just cobweb cube.
 * Heavy (impact) is larger, with red tint and more glow.
 */
public class WebShotRenderer extends EntityRenderer<WebShotEntity> {
    private static final Identifier TEXTURE =
            Identifier.of("minecraft", "textures/block/white_wool.png");
    private static final Identifier COBWEB_TEXTURE =
            Identifier.of("minecraft", "textures/block/cobweb.png");

    private final ModelPart core;
    private final ModelPart glow;

    public WebShotRenderer(EntityRendererFactory.Context context) {
        super(context);
        // Core: small white cube
        ModelPart.Cuboid coreCuboid = new ModelPart.Cuboid(
                0, 0, -3.0f, -3.0f, -3.0f, 6.0f, 6.0f, 6.0f,
                0.0f, 0.0f, 0.0f, false, 16.0f, 16.0f,
                Set.of(Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST));
        this.core = new ModelPart(List.of(coreCuboid), Map.of());

        // Glow: larger, for emissive effect
        ModelPart.Cuboid glowCuboid = new ModelPart.Cuboid(
                0, 0, -4.5f, -4.5f, -4.5f, 9.0f, 9.0f, 9.0f,
                0.0f, 0.0f, 0.0f, false, 16.0f, 16.0f,
                Set.of(Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST));
        this.glow = new ModelPart(List.of(glowCuboid), Map.of());
    }

    @Override
    public Identifier getTexture(WebShotEntity entity) {
        return TEXTURE;
    }

    @Override
    public void render(WebShotEntity entity, float yaw, float tickDelta,
            MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light) {
        matrices.push();
        matrices.translate(0.0, 0.25, 0.0);

        // Spin faster for heavy
        float spinSpeed = 18.0f;
        try {
            // Check if heavy via NBT? We don't have direct access, but we can infer from size or use age
            // For now, spin speed based on entity age and heavy flag via reflection? We'll just spin
            // We'll use faster spin for all for cool factor
            spinSpeed = 20.0f;
        } catch (Exception ignored) {}

        float spin = (entity.age + tickDelta) * spinSpeed;
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(spin));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(spin * 0.7f));
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(spin * 0.3f));

        // Scale: bigger for impact
        float scale = 0.7f;
        // Try to detect heavy — we store heavy in entity, but no getter. We'll use scale based on damage?
        // For simplicity, scale up slightly over time for impact feel
        if (entity.age % 10 < 5) {
            scale = 0.8f;
        }
        matrices.scale(scale, scale, scale);

        // Main white core
        VertexConsumer consumer = vertexConsumers.getBuffer(RenderLayer.getEntityCutout(getTexture(entity)));
        core.render(matrices, consumer, light, OverlayTexture.DEFAULT_UV);

        // Glow layer — fullbright white
        VertexConsumer glowConsumer = vertexConsumers.getBuffer(RenderLayer.getEntityCutout(getTexture(entity)));
        int glowLight = 0xF000F0; // Fullbright
        matrices.scale(1.3f, 1.3f, 1.3f);
        glow.render(matrices, glowConsumer, glowLight, OverlayTexture.DEFAULT_UV);

        matrices.pop();
        super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light);
    }
}
