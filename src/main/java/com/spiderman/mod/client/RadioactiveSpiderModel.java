package com.spiderman.mod.client;

import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;

import java.util.List;
import java.util.Map;
import java.util.Set;

import com.spiderman.mod.entity.RadioactiveSpiderEntity;

/**
 * ULTIMATE RADIOACTIVE SPIDER MODEL - Cinematic, terrifying, epic.
 * - Larger radioactive abdomen with pulsing glow
 * - Detailed head with fangs and glowing eyes
 * - 8 segmented legs with red tips and organic movement
 * - Thorax with muscular detail
 * - Custom animations: walk, idle, attack, climb
 */
public class RadioactiveSpiderModel {
    private static final Set<Direction> ALL_DIRS = Set.of(
            Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST);

    private final ModelPart head;
    private final ModelPart headGlow;
    private final ModelPart fangs;
    private final ModelPart eyes;
    private final ModelPart thorax;
    private final ModelPart abdomen;
    private final ModelPart abdomenGlow;
    private final ModelPart abdomenPattern;
    private final ModelPart[] legs = new ModelPart[8];
    private final ModelPart[] legTips = new ModelPart[8];

    private final float[] legBaseYaw = new float[8];
    private final float[] legBasePitch = new float[8];

    public RadioactiveSpiderModel() {
        // Head: 8x8x8 with fangs
        this.head = makePart(32, 4, -4.0f, -4.0f, -8.0f, 8.0f, 8.0f, 8.0f);
        this.headGlow = makePart(32, 4, -4.2f, -4.2f, -8.2f, 8.4f, 8.4f, 8.4f);
        this.fangs = makePart(0, 0, -1.5f, 0.0f, -10.0f, 3.0f, 3.0f, 4.0f);
        this.eyes = makePart(0, 0, -3.0f, -3.0f, -9.0f, 6.0f, 2.0f, 1.0f);

        // Thorax: muscular, compact
        this.thorax = makePart(0, 0, -3.5f, -2.5f, -3.5f, 7.0f, 5.0f, 7.0f);

        // Abdomen: large, radioactive, signature - 12x7x14
        this.abdomen = makePart(0, 12, -6.0f, -3.5f, 3.0f, 12.0f, 7.0f, 14.0f);
        this.abdomenGlow = makePart(0, 12, -6.3f, -3.8f, 2.7f, 12.6f, 7.6f, 14.6f);
        this.abdomenPattern = makePart(0, 12, -4.0f, -2.0f, 5.0f, 8.0f, 2.0f, 8.0f);

        // Legs: 8 legs with tips
        float[][] pivots = {
                {-3.5f, 0.5f, 5.0f}, {3.5f, 0.5f, 5.0f},
                {-3.5f, 0.5f, 1.5f}, {3.5f, 0.5f, 1.5f},
                {-3.5f, 0.5f, -1.5f}, {3.5f, 0.5f, -1.5f},
                {-3.5f, 0.5f, -5.0f}, {3.5f, 0.5f, -5.0f},
        };
        float[] yaws = {-45f, 45f, -28f, 28f, 28f, -28f, 45f, -45f};
        float[] pitches = {-10f, -10f, -5f, -5f, 5f, 5f, 10f, 10f};

        for (int i = 0; i < 8; i++) {
            boolean right = (i % 2 == 0);
            float x = right ? -13.0f : 0.0f;
            ModelPart leg = makePart(32, 16, x, -1.2f, -1.2f, 13.0f, 2.4f, 2.4f);
            leg.setPivot(pivots[i][0], pivots[i][1], pivots[i][2]);
            legs[i] = leg;
            
            // Red tips
            float tipX = right ? -15.0f : 13.0f;
            ModelPart tip = makePart(32, 16, tipX, -1.0f, -1.0f, 3.0f, 2.0f, 2.0f);
            tip.setPivot(pivots[i][0], pivots[i][1], pivots[i][2]);
            legTips[i] = tip;
            
            legBaseYaw[i] = yaws[i];
            legBasePitch[i] = pitches[i];
        }

        head.setPivot(0.0f, 0.5f, -3.5f);
        headGlow.setPivot(0.0f, 0.5f, -3.5f);
        fangs.setPivot(0.0f, 0.5f, -3.5f);
        eyes.setPivot(0.0f, 0.5f, -3.5f);
        thorax.setPivot(0.0f, 0.5f, 0.0f);
        abdomen.setPivot(0.0f, 0.5f, 0.0f);
        abdomenGlow.setPivot(0.0f, 0.5f, 0.0f);
        abdomenPattern.setPivot(0.0f, 0.5f, 0.0f);
    }

    private static ModelPart makePart(int u, int v, float x, float y, float z, float sx, float sy, float sz) {
        ModelPart.Cuboid cuboid = new ModelPart.Cuboid(
                u, v, x, y, z, sx, sy, sz, 0.0f, 0.0f, 0.0f, false, 64.0f, 32.0f, ALL_DIRS);
        return new ModelPart(List.of(cuboid), Map.of());
    }

    public void setAngles(RadioactiveSpiderEntity entity, float tickDelta, float limbAngle, float limbDistance) {
        float walkSpeed = Math.min(1.0f, limbDistance * 1.2f);
        float walkPos = limbAngle * 0.7f;
        float age = entity.age + tickDelta;

        // Head tracking with more life
        head.yaw = entity.getYaw() * 0.017453292f * 0.25f;
        head.pitch = entity.getPitch() * 0.017453292f * 0.12f;
        headGlow.yaw = head.yaw;
        headGlow.pitch = head.pitch;
        fangs.yaw = head.yaw;
        fangs.pitch = head.pitch;
        eyes.yaw = head.yaw;
        eyes.pitch = head.pitch;

        // Idle breathing
        float breathe = MathHelper.sin(age * 0.1f) * 0.03f;
        thorax.pitch = breathe;
        abdomen.pitch = MathHelper.sin(walkPos * 0.25f + breathe) * 0.12f * walkSpeed + breathe * 0.5f;
        abdomenGlow.pitch = abdomen.pitch;
        abdomenPattern.pitch = abdomen.pitch;
        
        // Abdomen pulse when radioactive
        float pulse = MathHelper.sin(age * 0.15f) * 0.02f;
        abdomenGlow.roll = pulse;

        // Leg animation - ultra realistic tripod gait
        for (int i = 0; i < 8; i++) {
            ModelPart leg = legs[i];
            ModelPart tip = legTips[i];
            boolean groupA = (i == 0 || i == 3 || i == 4 || i == 7);
            float phase = groupA ? 0.0f : (float) Math.PI;
            
            // Walk cycle
            float swing = MathHelper.sin(walkPos + phase) * 0.7f * walkSpeed;
            float lift = MathHelper.cos(walkPos + phase) * 0.4f * walkSpeed;
            float extend = MathHelper.sin(walkPos * 0.5f + phase) * 0.15f * walkSpeed;

            leg.yaw = (float) Math.toRadians(legBaseYaw[i] + swing * 30.0f);
            leg.pitch = (float) Math.toRadians(legBasePitch[i]) + lift * 0.8f + extend;
            leg.roll = groupA ? lift * 0.25f : -lift * 0.25f;

            tip.yaw = leg.yaw;
            tip.pitch = leg.pitch + lift * 0.3f;
            tip.roll = leg.roll;

            // Idle twitch
            if (walkSpeed < 0.05f) {
                float twitch = MathHelper.sin(age * 0.05f + i) * 0.02f;
                leg.yaw += twitch;
                tip.yaw += twitch;
            }
        }
        
        // Attack lunge
        if (entity.isAttacking()) {
            head.pitch += MathHelper.sin(age * 0.8f) * 0.2f;
            fangs.pitch += 0.3f;
        }
    }

    public void setAngles(RadioactiveSpiderEntity entity, float tickDelta) {
        float limbDistance = (float) entity.getVelocity().horizontalLength();
        float limbAngle = entity.age + tickDelta;
        limbDistance = Math.min(1.2f, limbDistance * 2.8f);
        setAngles(entity, tickDelta, limbAngle, limbDistance);
    }

    public void render(MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                       int light, int overlay, Identifier texture) {
        VertexConsumer consumer = vertexConsumers.getBuffer(RenderLayer.getEntityCutout(texture));

        // Body
        thorax.render(matrices, consumer, light, overlay);
        abdomen.render(matrices, consumer, light, overlay);
        head.render(matrices, consumer, light, overlay);
        fangs.render(matrices, consumer, light, overlay);

        // Legs
        for (int i = 0; i < 8; i++) {
            legs[i].render(matrices, consumer, light, overlay);
            legTips[i].render(matrices, consumer, light, overlay);
        }

        // Glow layers - fullbright for radioactive effect
        VertexConsumer glowConsumer = vertexConsumers.getBuffer(RenderLayer.getEntityCutout(texture));
        int glowLight = 0xF000F0;
        
        // Eyes glow red
        eyes.render(matrices, glowConsumer, glowLight, overlay);
        
        // Abdomen radioactive glow
        abdomenGlow.render(matrices, glowConsumer, glowLight, overlay);
        abdomenPattern.render(matrices, glowConsumer, glowLight, overlay);
        headGlow.render(matrices, glowConsumer, glowLight, overlay);
    }
}
