package com.spiderman.mod.client;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import com.spiderman.mod.state.ClientPowers;

/**
 * REAL SPIDER-MAN WEB - Complete remake to look like actual Spider-Man thread.
 * - Ultra thin, realistic, not Minecraft boxes
 * - Catenary physics with tension and elasticity
 * - Slight transparency and shine
 * - No thick glow spam, just pure white thread
 */
public final class StrandRenderer {
    private StrandRenderer() {}

    public static void afterEntities(WorldRenderContext wrc) {
        if (!ClientPowers.swingActive) return;
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        if (player == null || client.world == null) return;

        float lifeFactor = 1.0f;
        if (ClientPowers.swingLife > 0) {
            lifeFactor = Math.min(1.0f, ClientPowers.swingLife / 15.0f);
            if (lifeFactor < 0.08f) return;
        }

        float delta = wrc.tickCounter().getTickDelta(true);
        double px = lerp(player.prevX, player.getX(), delta);
        double py = lerp(player.prevY, player.getY(), delta);
        double pz = lerp(player.prevZ, player.getZ(), delta);

        Vec3d look = player.getRotationVector();
        if (look == null || look.lengthSquared() < 0.001) look = new Vec3d(0, 0, 1);
        Vec3d right = look.crossProduct(new Vec3d(0, 1, 0));
        if (right.lengthSquared() < 0.001) right = new Vec3d(1, 0, 0);
        right = right.normalize();

        double side = ClientPowers.swingHand == 1 ? -0.32 : 0.32;
        double hx = px + right.x * side + look.x * 0.35;
        double hy = py + 1.32 + look.y * 0.35;
        double hz = pz + right.z * side + look.z * 0.35;

        double ax = ClientPowers.swingX;
        double ay = ClientPowers.swingY;
        double az = ClientPowers.swingZ;

        if (!Double.isFinite(ax)) return;

        Vec3d cam = wrc.camera().getPos();
        MatrixStack matrices = wrc.matrixStack();
        matrices.push();
        matrices.translate(-cam.x, -cam.y, -cam.z);

        // Use lines render layer - thin and clean
        VertexConsumer lines = wrc.consumers().getBuffer(RenderLayer.getLines());

        boolean isSwing = ClientPowers.swingLife == 0;
        int segments = isSwing ? 64 : 24; // More segments = smoother real thread

        // Real Spider-Man web color: pure white with very slight warm tint, not blue
        float r = 0.96f, g = 0.96f, b = 0.94f;
        float alpha = 0.95f * lifeFactor;

        // Calculate distance and tension for realistic sag
        double dx = ax - hx;
        double dy = ay - hy;
        double dz = az - hz;
        double dist = Math.sqrt(dx*dx + dy*dy + dz*dz);
        double horizDist = Math.sqrt(dx*dx + dz*dz);

        // Tension: when swinging fast or far, web is taut; when slow/close, more sag
        double speed = 0;
        try { speed = player.getVelocity().length(); } catch (Exception ignored) {}
        double tension = Math.min(1.0, (speed * 0.25 + dist * 0.02));
        double sagAmount = (1.0 - tension) * Math.min(3.5, horizDist * 0.22 + 0.8);
        if (!isSwing) sagAmount *= 0.4;

        // Elastic stretch: web stretches slightly under high speed
        double stretch = 1.0 + Math.min(0.08, speed * 0.015);

        Vec3d prev = new Vec3d(hx, hy, hz);
        for (int i = 1; i <= segments; i++) {
            double t = (double) i / (double) segments;
            // Apply stretch to t for elastic effect
            double tStretched = t * stretch;
            if (tStretched > 1.0) tStretched = 1.0;

            double x = hx + (ax - hx) * tStretched;
            double y = hy + (ay - hy) * tStretched;
            double z = hz + (az - hz) * tStretched;

            // Real catenary sag: sin curve with tension
            if (sagAmount > 0.01) {
                // Catenary: y = a*cosh((x-0.5)/a) - a, approximated with sin for performance
                double catenary = Math.sin(tStretched * Math.PI) * sagAmount;
                // Add slight pendulum sway based on swing phase
                if (isSwing) {
                    double swingPhase = Math.sin(ClientPowers.clientTick * 0.08 + tStretched * 2.5) * 0.04 * (1.0 - tension);
                    x += swingPhase;
                    double swayZ = Math.cos(ClientPowers.clientTick * 0.06 + tStretched * 3.0) * 0.03 * (1.0 - tension);
                    z += swayZ;
                }
                y -= catenary;
            }

            Vec3d curr = new Vec3d(x, y, z);

            // Draw ultra thin realistic thread - not boxes
            // Thickness varies: slightly thicker near hands (real web is thicker at origin)
            float thicknessFactor = (float)(1.0 - tStretched * 0.5); // Thicker at start
            float lineAlpha = alpha * (0.7f + thicknessFactor * 0.3f);

            // Main thread - pure white, thin
            drawRealisticLine(matrices, lines, prev, curr, r, g, b, lineAlpha);

            // Very subtle inner highlight for shine (only every 3rd segment to reduce overdraw)
            if (isSwing && i % 4 == 0) {
                drawRealisticLine(matrices, lines, prev, curr, 1.0f, 1.0f, 1.0f, lineAlpha * 0.35f);
            }

            prev = curr;
        }

        // Anchor - tiny, realistic, not big glowing box
        double anchorSize = 0.04 * lifeFactor;
        WorldRenderer.drawBox(matrices, lines,
                new Box(ax - anchorSize, ay - anchorSize, az - anchorSize,
                        ax + anchorSize, ay + anchorSize, az + anchorSize),
                1.0f, 1.0f, 1.0f, 0.9f * lifeFactor);

        // Wrist - tiny
        double wristSize = 0.025 * lifeFactor;
        WorldRenderer.drawBox(matrices, lines,
                new Box(hx - wristSize, hy - wristSize, hz - wristSize,
                        hx + wristSize, hy + wristSize, hz + wristSize),
                1.0f, 1.0f, 1.0f, 0.85f * lifeFactor);

        matrices.pop();
    }

    // Realistic thin line using proper line rendering, not boxes
    private static void drawRealisticLine(MatrixStack matrices, VertexConsumer consumer,
                                          Vec3d p1, Vec3d p2,
                                          float r, float g, float b, float a) {
        if (a < 0.05f) return;
        Matrix4f mat = matrices.peek().getPositionMatrix();
        
        // Clamp alpha
        if (a > 1.0f) a = 1.0f;
        if (a < 0.0f) return;

        // Draw as line - Minecraft's RenderLayer.getLines() expects line with normal
        // We calculate normal for proper line rendering
        float x1 = (float)p1.x, y1 = (float)p1.y, z1 = (float)p1.z;
        float x2 = (float)p2.x, y2 = (float)p2.y, z2 = (float)p2.z;

        // Calculate direction and normal for line
        float dx = x2 - x1;
        float dy = y2 - y1;
        float dz = z2 - z1;
        float len = (float)Math.sqrt(dx*dx + dy*dy + dz*dz);
        if (len < 0.001f) return;

        // Normal perpendicular to line (for line width)
        float nx = -dy;
        float ny = dx;
        float nz = 0;
        float nLen = (float)Math.sqrt(nx*nx + ny*ny + nz*nz);
        if (nLen < 0.001f) {
            nx = 0; ny = 0; nz = 1;
            nLen = 1;
        }
        nx /= nLen; ny /= nLen; nz /= nLen;

        // RenderLayer.getLines() uses vertex format with normal
        consumer.vertex(mat, x1, y1, z1).color(r, g, b, a).normal(nx, ny, nz);
        consumer.vertex(mat, x2, y2, z2).color(r, g, b, a).normal(nx, ny, nz);
    }

    private static double lerp(double a, double b, float t) {
        return a + (b - a) * t;
    }
}
