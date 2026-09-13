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
 * INSOMNIAC WEB SHOT - Real Spider-Man web projectile.
 * - Tiny white orb, not wool cube
 * - Braided silk texture, translucent glow
 * - Heavy impact is bigger, orange-red
 * - Realistic spin, pulse, and trail
 */
public class WebShotRenderer extends EntityRenderer<WebShotEntity> {
    private static final Identifier WEB_LINE = Identifier.of("spiderman", "textures/entity/web_line.png");
    private static final Identifier WHITE = Identifier.of("minecraft", "textures/block/white_wool.png");

    public WebShotRenderer(EntityRendererFactory.Context context) {
        super(context);
    }

    @Override
    public Identifier getTexture(WebShotEntity entity) {
        return WEB_LINE;
    }

    @Override
    public void render(WebShotEntity entity, float yaw, float tickDelta,
            MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light) {
        matrices.push();

        float age = entity.age + tickDelta;
        boolean isHeavy = false;
        try {
            java.lang.reflect.Field f = entity.getClass().getDeclaredField("heavy");
            f.setAccessible(true);
            isHeavy = f.getBoolean(entity);
        } catch (Exception ignored) {}

        matrices.translate(0.0, 0.18, 0.0);

        // Insomniac spin: natural, not crazy fast
        float spin = age * (isHeavy ? 12.0f : 18.0f);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(spin));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(spin * 0.55f));

        // Scale: realistic small web ball, like real spider silk
        float base = isHeavy ? 0.32f : 0.19f;
        float pulse = 1.0f + MathHelper.sin(age * 0.28f) * 0.07f;
        float scale = base * pulse;
        matrices.scale(scale, scale, scale);

        Matrix4f mat = matrices.peek().getPositionMatrix();
        int glowLight = 0xF000F0;

        float size = 0.55f;
        float r = isHeavy ? 0.92f : 0.96f;
        float g = isHeavy ? 0.78f : 0.96f;
        float b = isHeavy ? 0.65f : 0.94f;
        float a = 0.96f;

        // Core - using custom web texture for realistic braided look
        VertexConsumer core = vertexConsumers.getBuffer(RenderLayer.getEntityCutout(WEB_LINE));
        drawQuad(mat, core, glowLight, -size, -size, 0, size, size, r, g, b, a);

        // Glow layer - larger, translucent, Insomniac shine
        matrices.push();
        matrices.scale(1.55f, 1.55f, 1.55f);
        Matrix4f mat2 = matrices.peek().getPositionMatrix();
        VertexConsumer glow = vertexConsumers.getBuffer(RenderLayer.getEntityTranslucent(WEB_LINE));
        drawQuad(mat2, glow, glowLight, -size, -size, 0, size, size, r, g, b, 0.32f);
        matrices.pop();

        // Outer glow for heavy - even bigger
        if (isHeavy) {
            matrices.push();
            matrices.scale(2.1f, 2.1f, 2.1f);
            Matrix4f mat3 = matrices.peek().getPositionMatrix();
            VertexConsumer outer = vertexConsumers.getBuffer(RenderLayer.getEntityTranslucent(WHITE));
            drawQuad(mat3, outer, glowLight, -size, -size, 0, size, size, 1.0f, 0.85f, 0.6f, 0.18f);
            matrices.pop();
        }

        matrices.pop();
        super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light);
    }

    private void drawQuad(Matrix4f mat, VertexConsumer consumer, int light,
                          float x1, float y1, float z, float x2, float y2,
                          float r, float g, float b, float a) {
        consumer.vertex(mat, x1, y1, z).color(r, g, b, a).texture(0, 0).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(0, 0, 1);
        consumer.vertex(mat, x1, y2, z).color(r, g, b, a).texture(0, 1).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(0, 0, 1);
        consumer.vertex(mat, x2, y2, z).color(r, g, b, a).texture(1, 1).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(0, 0, 1);
        consumer.vertex(mat, x2, y1, z).color(r, g, b, a).texture(1, 0).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(0, 0, 1);
    }
}
