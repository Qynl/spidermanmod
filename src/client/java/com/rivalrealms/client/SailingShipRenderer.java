package com.rivalrealms.client;

import com.rivalrealms.entity.SailingShipEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

/**
 * Shared renderer for every custom sailing ship: bow-first rotation, wave
 * bobbing and rolling on the waterline, per-subclass texture and scale
 * (the sloop renders at a fraction of the cog geometry).
 */
public class SailingShipRenderer extends EntityRenderer<SailingShipEntity> {
    private final SailingShipEntityModel model;
    private final Identifier texture;
    private final float scale;

    public SailingShipRenderer(EntityRendererFactory.Context context, Identifier texture, float scale) {
        super(context);
        this.model = new SailingShipEntityModel(context.getPart(ModModelLayers.SAILING_SHIP));
        this.texture = texture;
        this.scale = scale;
    }

    @Override
    public void render(SailingShipEntity entity, float yaw, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers, int light) {
        matrices.push();
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0f - yaw));
        float time = entity.age + tickDelta;
        // Wave ride: gentle rise-and-fall plus a slow roll around the keel.
        matrices.translate(0.0, MathHelper.sin(time * 0.09f) * 0.05f + 0.05f, 0.0);
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(MathHelper.sin(time * 0.07f) * 2.4f));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(MathHelper.sin(time * 0.05f + 1.3f) * 1.6f));
        matrices.scale(scale, scale, scale);
        this.model.setAngles(entity, 0.0f, 0.0f, time, 0.0f, 0.0f);
        this.model.render(matrices, vertexConsumers.getBuffer(this.model.getLayer(this.texture)),
                light, OverlayTexture.DEFAULT_UV);
        matrices.pop();
        super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light);
    }

    @Override
    public Identifier getTexture(SailingShipEntity entity) {
        return this.texture;
    }
}
