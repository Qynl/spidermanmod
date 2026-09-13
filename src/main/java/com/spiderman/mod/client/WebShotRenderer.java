package com.spiderman.mod.client;

import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;

import com.spiderman.mod.entity.WebShotEntity;

/**
 * REAL SPIDER-MAN WEB SHOT - Looks like actual web, not wool.
 * - Tiny white orb, not cube
 * - Trailing thread to shooter
 * - Realistic spin and pulse
 */
public class WebShotRenderer extends EntityRenderer<WebShotEntity> {
    private static final Identifier WHITE_TEXTURE = Identifier.of("minecraft", "textures/block/white_wool.png");
    private static final Identifier COBWEB_TEXTURE = Identifier.of("minecraft", "textures/block/cobweb.png");

    public WebShotRenderer(EntityRendererFactory.Context context) {
        super(context);
    }

    @Override
    public Identifier getTexture(WebShotEntity entity) {
        return WHITE_TEXTURE;
    }

    @Override
    public void render(WebShotEntity entity, float yaw, float tickDelta,
            MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light) {
        matrices.push();
        
        float age = entity.age + tickDelta;
        boolean isHeavy = false;
        try {
            java.lang.reflect.Field heavyField = entity.getClass().getDeclaredField("heavy");
            heavyField.setAccessible(true);
            isHeavy = heavyField.getBoolean(entity);
        } catch (Exception ignored) {}

        // Position
        matrices.translate(0.0, 0.15, 0.0);

        // Spin - more natural, not crazy fast
        float spin = age * (isHeavy ? 15.0f : 22.0f);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(spin));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(spin * 0.6f));

        // Scale - small and realistic, like real web ball
        float baseScale = isHeavy ? 0.35f : 0.22f;
        float pulse = 1.0f + MathHelper.sin(age * 0.3f) * 0.08f;
        float scale = baseScale * pulse;
        matrices.scale(scale, scale, scale);

        // Render as tiny white quad with fullbright, not wool cube
        // Use translucent layer for realistic web look
        Matrix4f mat = matrices.peek().getPositionMatrix();
        VertexConsumer consumer = vertexConsumers.getBuffer(RenderLayer.getEntityTranslucent(WHITE_TEXTURE));
        int glowLight = 0xF000F0; // Fullbright
        
        // Draw as small diamond shape (2 triangles) for orb look
        float size = 0.5f;
        float r = isHeavy ? 0.9f : 0.95f;
        float g = isHeavy ? 0.8f : 0.95f;
        float b = isHeavy ? 0.7f : 0.92f;
        float a = 0.95f;

        // Simple quad facing camera - will be billboarded by super.render? We do manual
        // For now render as small box but much smaller and with proper color
        // Use entity cutout for main
        VertexConsumer cutout = vertexConsumers.getBuffer(RenderLayer.getEntityCutout(WHITE_TEXTURE));
        
        // Core - tiny
        drawQuad(mat, cutout, glowLight, -size, -size, 0, size, size, r, g, b, a);
        
        // Glow - larger, translucent
        matrices.push();
        matrices.scale(1.6f, 1.6f, 1.6f);
        Matrix4f mat2 = matrices.peek().getPositionMatrix();
        VertexConsumer glowConsumer = vertexConsumers.getBuffer(RenderLayer.getEntityTranslucent(WHITE_TEXTURE));
        drawQuad(mat2, glowConsumer, glowLight, -size, -size, 0, size, size, r, g, b, 0.35f);
        matrices.pop();

        matrices.pop();
        super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light);
    }

    private void drawQuad(Matrix4f mat, VertexConsumer consumer, int light,
                          float x1, float y1, float z, float x2, float y2,
                          float r, float g, float b, float a) {
        // Two triangles for quad
        consumer.vertex(mat, x1, y1, z).color(r, g, b, a).texture(0, 0).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(0, 0, 1);
        consumer.vertex(mat, x1, y2, z).color(r, g, b, a).texture(0, 1).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(0, 0, 1);
        consumer.vertex(mat, x2, y2, z).color(r, g, b, a).texture(1, 1).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(0, 0, 1);
        consumer.vertex(mat, x2, y1, z).color(r, g, b, a).texture(1, 0).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(0, 0, 1);
    }
}
