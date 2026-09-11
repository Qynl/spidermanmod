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

import com.spiderman.mod.entity.WebShotEntity;

/**
 * Renders a web glob as a small spinning cube skinned with the vanilla
 * cobweb texture.
 */
public class WebShotRenderer extends EntityRenderer<WebShotEntity> {
    private static final Identifier TEXTURE =
            Identifier.of("minecraft", "textures/block/cobweb.png");

    private final ModelPart core;

    public WebShotRenderer(EntityRendererFactory.Context context) {
        super(context);
        ModelPart.Cuboid cuboid = new ModelPart.Cuboid(
                0, 0, -3.0f, -3.0f, -3.0f, 6.0f, 6.0f, 6.0f,
                0.0f, 0.0f, 0.0f, false, 16.0f, 16.0f,
                java.util.Set.of(Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST));
        this.core = new ModelPart(List.of(cuboid), Map.of());
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
        float spin = (entity.age + tickDelta) * 12.0f;
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(spin));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(spin * 0.7f));
        matrices.scale(0.6f, 0.6f, 0.6f);
        VertexConsumer consumer =
                vertexConsumers.getBuffer(RenderLayer.getEntityCutout(getTexture(entity)));
        core.render(matrices, consumer, light, OverlayTexture.DEFAULT_UV);
        matrices.pop();
        super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light);
    }
}
