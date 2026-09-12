package com.rivalrealms.client;

import com.rivalrealms.RivalRealms;
import com.rivalrealms.entity.GalleonEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

/**
 * Renders the Freebooter flagship: bow-first rotation, a heavier wave ride
 * than the smaller ships, and a slow ponderous roll that sells her tonnage.
 */
public class GalleonRenderer extends EntityRenderer<GalleonEntity> {
    private static final Identifier TEXTURE = RivalRealms.id("textures/entity/galleon.png");
    private final GalleonEntityModel model;

    public GalleonRenderer(EntityRendererFactory.Context context) {
        super(context);
        this.model = new GalleonEntityModel(context.getPart(ModModelLayers.GALLEON));
    }

    @Override
    public void render(GalleonEntity entity, float yaw, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers, int light) {
        matrices.push();
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0f - yaw));
        float time = entity.age + tickDelta;
        matrices.translate(0.0, MathHelper.sin(time * 0.07f) * 0.06f + 0.06f, 0.0);
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(MathHelper.sin(time * 0.055f) * 2.0f));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(MathHelper.sin(time * 0.045f + 1.1f) * 1.3f));
        this.model.setAngles(entity, 0.0f, 0.0f, time, 0.0f, 0.0f);
        this.model.render(matrices, vertexConsumers.getBuffer(this.model.getLayer(TEXTURE)),
                light, OverlayTexture.DEFAULT_UV);
        matrices.pop();
        super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light);
    }

    @Override
    public Identifier getTexture(GalleonEntity entity) {
        return TEXTURE;
    }
}
