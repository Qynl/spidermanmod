package com.rivalrealms.client;

import com.rivalrealms.RivalRealms;
import com.rivalrealms.entity.AirshipEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;

/**
 * Renders the custom zeppelin: envelope first (it is the bulk of the model),
 * with pilot banking applied around the flight axis and propellers spun by
 * {@link AirshipEntityModel}.
 */
public final class AirshipRenderer extends EntityRenderer<AirshipEntity> {
    private static final Identifier TEXTURE = RivalRealms.id("textures/entity/airship.png");

    private final AirshipEntityModel model;

    public AirshipRenderer(EntityRendererFactory.Context context) {
        super(context);
        this.model = new AirshipEntityModel(context.getPart(ModModelLayers.AIRSHIP));
    }

    @Override
    public void render(AirshipEntity entity, float yaw, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers, int light) {
        matrices.push();
        // Standard entity-facing rotation; models are authored facing -Z.
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0f - yaw));
        this.model.setAngles(entity, 0.0f, 0.0f, entity.age + tickDelta, 0.0f, 0.0f);
        this.model.render(matrices, vertexConsumers.getBuffer(this.model.getLayer(TEXTURE)),
                light, OverlayTexture.DEFAULT_UV);
        matrices.pop();
        super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light);
    }

    @Override
    public Identifier getTexture(AirshipEntity entity) {
        return TEXTURE;
    }
}
