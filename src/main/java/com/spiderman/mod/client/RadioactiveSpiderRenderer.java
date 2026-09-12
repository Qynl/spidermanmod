package com.spiderman.mod.client;

import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;

import com.spiderman.mod.entity.RadioactiveSpiderEntity;

/**
 * Custom renderer for the radioactive spider — does NOT use vanilla spider model.
 * Uses {@link RadioactiveSpiderModel} which is a fully custom model with distinct
 * thorax, large radioactive abdomen, and 8 articulated legs.
 */
public class RadioactiveSpiderRenderer extends EntityRenderer<RadioactiveSpiderEntity> {
    private static final Identifier TEXTURE =
            Identifier.of("spiderman", "textures/entity/radioactive_spider.png");

    private final RadioactiveSpiderModel model;

    public RadioactiveSpiderRenderer(EntityRendererFactory.Context context) {
        super(context);
        this.model = new RadioactiveSpiderModel();
    }

    @Override
    public Identifier getTexture(RadioactiveSpiderEntity entity) {
        return TEXTURE;
    }

    @Override
    public void render(RadioactiveSpiderEntity entity, float yaw, float tickDelta,
                       MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light) {
        matrices.push();

        // Center model: entity's pos is at feet, we want model centered at 0,0,0
        // Spider height is 0.9, so lift model up by 0.5 + adjust
        matrices.translate(0.0, 0.6, 0.0);

        // Face the entity's yaw
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0f - yaw));

        // Slight scale to make it more imposing than vanilla spider, but not huge
        matrices.scale(1.1f, 1.1f, 1.1f);

        // Translate down so pivot is at body center
        matrices.translate(0.0, -0.3, 0.0);

        // Animate
        model.setAngles(entity, tickDelta);

        // Render with our custom texture
        model.render(matrices, vertexConsumers, light, OverlayTexture.DEFAULT_UV, TEXTURE);

        matrices.pop();

        // Call super to handle nameplate, etc.
        super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light);
    }
}
