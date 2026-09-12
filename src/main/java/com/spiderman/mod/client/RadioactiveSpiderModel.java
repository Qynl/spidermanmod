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
 * Custom radioactive spider model — does NOT use vanilla spider model.
 * Built from scratch with ModelPart cuboids, with a distinct silhouette:
 * larger radioactive abdomen, compact thorax, distinct head with fangs,
 * and 8 segmented legs with red tips.
 *
 * Texture: 64x32, custom layout (see assets/spiderman/textures/entity/radioactive_spider.png)
 */
public class RadioactiveSpiderModel {
    private static final Set<Direction> ALL_DIRS = Set.of(
            Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST);

    private final ModelPart head;
    private final ModelPart thorax;
    private final ModelPart abdomen;
    private final ModelPart abdomenGlow;
    private final ModelPart[] legs = new ModelPart[8];

    // Leg pivots for animation
    private final float[] legBaseYaw = new float[8];

    public RadioactiveSpiderModel() {
        // Head: 8x8x8, uv 32,4 (vanilla head area but we make it slightly smaller and with fangs)
        this.head = makePart(32, 4,
                -4.0f, -4.0f, -8.0f,
                8.0f, 8.0f, 8.0f);

        // Thorax (front body): 6x4x6, uv 0,0 — compact, muscular
        this.thorax = makePart(0, 0,
                -3.0f, -2.0f, -3.0f,
                6.0f, 4.0f, 6.0f);

        // Abdomen (rear body): 10x6x12, uv 0,12 — large, radioactive, the signature
        this.abdomen = makePart(0, 12,
                -5.0f, -3.0f, 3.0f,
                10.0f, 6.0f, 12.0f);

        // Abdomen glow overlay: slightly larger, for emissive look (rendered with fullbright later if wanted)
        this.abdomenGlow = makePart(0, 12,
                -5.2f, -3.2f, 2.8f,
                10.4f, 6.4f, 12.4f);

        // Legs: 8 legs, each 2x2x12, uv 32,16 — matches legs area in custom texture (black with red tips)
        // We create 8 distinct parts with pivots around thorax
        // Order: 0=rightHind,1=leftHind,2=rightMiddleHind,3=leftMiddleHind,4=rightMiddleFront,5=leftMiddleFront,6=rightFront,7=leftFront
        float[][] pivots = {
                {-3.0f, 0.0f, 4.0f},
                {3.0f, 0.0f, 4.0f},
                {-3.0f, 0.0f, 1.0f},
                {3.0f, 0.0f, 1.0f},
                {-3.0f, 0.0f, -1.0f},
                {3.0f, 0.0f, -1.0f},
                {-3.0f, 0.0f, -4.0f},
                {3.0f, 0.0f, -4.0f},
        };
        // Base yaw for each leg (spread out)
        float[] yaws = {-40f, 40f, -25f, 25f, 25f, -25f, 40f, -40f};

        for (int i = 0; i < 8; i++) {
            boolean right = (i % 2 == 0);
            // Right legs extend negative X, left positive X
            // Cuboid defined to extend outward from pivot
            float x = right ? -12.0f : 0.0f;
            float y = -1.0f;
            float z = -1.0f;
            float sx = 12.0f;
            float sy = 2.0f;
            float sz = 2.0f;

            ModelPart leg = makePart(32, 16, x, y, z, sx, sy, sz);
            leg.setPivot(pivots[i][0], pivots[i][1], pivots[i][2]);
            legs[i] = leg;
            legBaseYaw[i] = yaws[i];
        }

        // Set pivots for core parts (all centered at 0,0,0 for now, but we will offset in renderer)
        head.setPivot(0.0f, 0.0f, -3.0f);
        thorax.setPivot(0.0f, 0.0f, 0.0f);
        abdomen.setPivot(0.0f, 0.0f, 0.0f);
        abdomenGlow.setPivot(0.0f, 0.0f, 0.0f);
    }

    private static ModelPart makePart(int u, int v,
                                      float x, float y, float z,
                                      float sx, float sy, float sz) {
        ModelPart.Cuboid cuboid = new ModelPart.Cuboid(
                u, v,
                x, y, z,
                sx, sy, sz,
                0.0f, 0.0f, 0.0f,
                false,
                64.0f, 32.0f,
                ALL_DIRS);
        return new ModelPart(List.of(cuboid), Map.of());
    }

    /**
     * Animate legs based on movement.
     */
    public void setAngles(RadioactiveSpiderEntity entity, float tickDelta, float limbAngle, float limbDistance) {
        // limbAngle/limbDistance are passed from renderer, but we can also compute from entity velocity if needed
        float walkSpeed = limbDistance;
        float walkPos = limbAngle;

        // Head tracking: look at target? Use entity yaw/pitch
        head.yaw = entity.getYaw() * 0.017453292f * 0.2f; // small head turn
        head.pitch = entity.getPitch() * 0.017453292f * 0.1f;

        // Abdomen slight wobble
        abdomen.pitch = MathHelper.sin(walkPos * 0.3f) * 0.1f * walkSpeed;
        abdomenGlow.pitch = abdomen.pitch;

        // Leg animation: alternating tripod gait like real spiders
        for (int i = 0; i < 8; i++) {
            ModelPart leg = legs[i];
            // Base yaw
            float baseYawDeg = legBaseYaw[i];
            // Tripod groups: 0,3,4,7 vs 1,2,5,6
            boolean groupA = (i == 0 || i == 3 || i == 4 || i == 7);
            float phase = groupA ? 0.0f : (float) Math.PI;
            float swing = MathHelper.sin(walkPos * 0.8f + phase) * 0.6f * walkSpeed;
            float lift = MathHelper.cos(walkPos * 0.8f + phase) * 0.3f * walkSpeed;

            // Yaw: spread + swing
            leg.yaw = (float) Math.toRadians(baseYawDeg + swing * 25.0f);
            // Pitch: lift leg when stepping
            leg.pitch = lift;
            // Roll: slight
            leg.roll = groupA ? lift * 0.2f : -lift * 0.2f;
        }
    }

    public void setAngles(RadioactiveSpiderEntity entity, float tickDelta) {
        // Compute limb swing from velocity if not provided
        float limbDistance = (float) entity.getVelocity().horizontalLength();
        float limbAngle = entity.age + tickDelta;
        // Scale distance to 0-1 range
        limbDistance = Math.min(1.0f, limbDistance * 2.5f);
        setAngles(entity, tickDelta, limbAngle, limbDistance);
    }

    public void render(MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                       int light, int overlay, Identifier texture) {
        VertexConsumer consumer = vertexConsumers.getBuffer(RenderLayer.getEntityCutout(texture));

        // Render core body parts
        head.render(matrices, consumer, light, overlay);
        thorax.render(matrices, consumer, light, overlay);
        abdomen.render(matrices, consumer, light, overlay);

        // Legs
        for (ModelPart leg : legs) {
            leg.render(matrices, consumer, light, overlay);
        }

        // Optional glow layer for abdomen (fullbright) — makes radioactive symbol pop
        // Render with max light to simulate emissive
        VertexConsumer glowConsumer = vertexConsumers.getBuffer(RenderLayer.getEntityCutout(texture));
        int glowLight = 0xF000F0; // fullbright
        abdomenGlow.render(matrices, glowConsumer, glowLight, overlay);
    }
}
