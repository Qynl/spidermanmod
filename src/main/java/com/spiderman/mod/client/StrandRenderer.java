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

import com.spiderman.mod.state.ClientPowers;

/**
 * ULTIMATE WEB RENDERER - Cinematic white lines.
 * - Thick, glowing, catenary sag for swing
 * - Particles along strand
 * - Anchor and wrist glow
 * - Style-based thickness
 */
public final class StrandRenderer {
    private StrandRenderer() {
    }

    public static void afterEntities(WorldRenderContext wrc) {
        if (!ClientPowers.swingActive) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        if (player == null || client.world == null) {
            return;
        }

        float lifeFactor = 1.0f;
        if (ClientPowers.swingLife > 0) {
            lifeFactor = Math.min(1.0f, ClientPowers.swingLife / 20.0f);
            if (lifeFactor < 0.05f) return;
        }

        float delta = wrc.tickCounter().getTickDelta(true);
        double px = lerp(player.prevX, player.getX(), delta);
        double py = lerp(player.prevY, player.getY(), delta);
        double pz = lerp(player.prevZ, player.getZ(), delta);

        Vec3d look = player.getRotationVector();
        if (look == null || look.lengthSquared() < 0.001) look = new Vec3d(0, 0, 1);
        Vec3d right = look.crossProduct(new Vec3d(0.0, 1.0, 0.0));
        if (right.lengthSquared() < 0.001) right = new Vec3d(1.0, 0.0, 0.0);
        right = right.normalize();

        double side = ClientPowers.swingHand == 1 ? -0.38 : 0.38;
        double hx = px + right.x * side + look.x * 0.45;
        double hy = py + 1.38 + look.y * 0.45;
        double hz = pz + right.z * side + look.z * 0.45;

        double ax = ClientPowers.swingX;
        double ay = ClientPowers.swingY;
        double az = ClientPowers.swingZ;

        if (!Double.isFinite(ax) || !Double.isFinite(ay) || !Double.isFinite(az)) return;

        Vec3d cam = wrc.camera().getPos();
        MatrixStack matrices = wrc.matrixStack();
        matrices.push();
        matrices.translate(-cam.x, -cam.y, -cam.z);

        VertexConsumer consumer = wrc.consumers().getBuffer(RenderLayer.getLines());
        VertexConsumer glowConsumer = wrc.consumers().getBuffer(RenderLayer.getLines());

        boolean isSwing = ClientPowers.swingLife == 0;
        int segments = isSwing ? 40 : 28;

        // Dynamic color based on ability and style
        float r = 1.0f, g = 1.0f, b = 1.0f;
        float alpha = 1.0f * lifeFactor;
        
        // Slight blue tint for epic swing
        if (isSwing) {
            r = 0.92f; g = 0.96f; b = 1.0f;
            // Style makes it more glowing
            if (ClientPowers.combo > 3) {
                r = 0.8f; g = 0.9f; b = 1.0f;
            }
        }

        // Render with catenary and thickness variation
        Vec3d prev = null;
        for (int i = 0; i <= segments; i++) {
            double t = (double) i / (double) segments;
            Vec3d p = interpolateWithSag(hx, hy, hz, ax, ay, az, t, isSwing, px, py, pz);
            
            if (prev != null) {
                double thickness = isSwing ? 0.08 : 0.06;
                if (isSwing) {
                    // Thicker near player, thinner at anchor - realistic
                    thickness = 0.09 - t * 0.03;
                    // Faster swing = thicker
                    double speed = 0;
                    try {
                        speed = player.getVelocity().length();
                    } catch (Exception ignored) {}
                    thickness += speed * 0.01;
                }
                thickness *= lifeFactor;
                
                // Main white line
                drawThickLine(matrices, consumer, prev, p, thickness, r, g, b, alpha);
                
                // Glow - outer layer
                if (i % 2 == 0) {
                    float glowAlpha = 0.35f * lifeFactor;
                    drawThickLine(matrices, glowConsumer, prev, p, thickness * 2.2, r * 0.7f, g * 0.85f, b, glowAlpha);
                }
                
                // Inner bright core for swing
                if (isSwing && i % 3 == 0) {
                    drawThickLine(matrices, consumer, prev, p, thickness * 0.4, 1.0f, 1.0f, 1.0f, alpha * 1.2f);
                }
            }
            prev = p;
        }

        // Epic anchor point
        double anchorSize = (0.15 + Math.sin(ClientPowers.clientTick * 0.3) * 0.03) * lifeFactor;
        WorldRenderer.drawBox(matrices, consumer,
                new Box(ax - anchorSize, ay - anchorSize, az - anchorSize,
                        ax + anchorSize, ay + anchorSize, az + anchorSize),
                1.0f, 1.0f, 1.0f, 1.0f * lifeFactor);
        // Anchor glow
        WorldRenderer.drawBox(matrices, glowConsumer,
                new Box(ax - anchorSize*1.8, ay - anchorSize*1.8, az - anchorSize*1.8,
                        ax + anchorSize*1.8, ay + anchorSize*1.8, az + anchorSize*1.8),
                0.7f, 0.85f, 1.0f, 0.4f * lifeFactor);

        // Wrist origin glow
        double wristSize = 0.09 * lifeFactor;
        WorldRenderer.drawBox(matrices, consumer,
                new Box(hx - wristSize, hy - wristSize, hz - wristSize,
                        hx + wristSize, hy + wristSize, hz + wristSize),
                1.0f, 1.0f, 1.0f, 1.0f * lifeFactor);

        matrices.pop();
    }

    private static Vec3d interpolateWithSag(double hx, double hy, double hz,
                                            double ax, double ay, double az,
                                            double t, boolean isSwing,
                                            double px, double py, double pz) {
        double x = hx + (ax - hx) * t;
        double y = hy + (ay - hy) * t;
        double z = hz + (az - hz) * t;

        if (isSwing) {
            // Catenary sag - more realistic with distance
            double dist = Math.sqrt((ax - hx) * (ax - hx) + (az - hz) * (az - hz) + (ay - hy)*(ay - hy));
            double sagAmount = Math.min(4.0, dist * 0.18);
            // Use catenary formula approximation: y = a*cosh(x/a) - a
            double catenary = Math.sin(t * Math.PI) * sagAmount;
            // Add swing dynamics - moving sag
            double swingPhase = Math.sin(ClientPowers.clientTick * 0.1 + t * 3.0) * 0.1;
            y -= catenary + swingPhase;
            
            // Add slight horizontal sway for realism
            double sway = Math.sin(t * Math.PI * 2 + ClientPowers.clientTick * 0.15) * 0.05;
            x += sway;
        } else {
            // For zip/pull, slight sag but less
            double dist = Math.sqrt((ax - hx)*(ax - hx) + (az - hz)*(az - hz));
            double sag = Math.sin(t * Math.PI) * Math.min(1.5, dist * 0.05);
            y -= sag;
        }

        return new Vec3d(x, y, z);
    }

    private static void drawThickLine(MatrixStack matrices, VertexConsumer consumer,
                                      Vec3d p1, Vec3d p2, double thickness,
                                      float r, float g, float b, float a) {
        double mx = (p1.x + p2.x) * 0.5;
        double my = (p1.y + p2.y) * 0.5;
        double mz = (p1.z + p2.z) * 0.5;
        double s = thickness;

        double segLen = p1.distanceTo(p2);
        int boxes = (int) Math.max(1, segLen / (thickness * 1.5));
        if (boxes > 4) boxes = 4;
        
        for (int i = 0; i < boxes; i++) {
            double tt = boxes == 1 ? 0.5 : (double) i / (double) (boxes - 1);
            double x = p1.x + (p2.x - p1.x) * tt;
            double y = p1.y + (p2.y - p1.y) * tt;
            double z = p1.z + (p2.z - p1.z) * tt;
            
            // Clamp alpha
            float alpha = a;
            if (alpha > 1.0f) alpha = 1.0f;
            if (alpha < 0.0f) alpha = 0.0f;
            
            WorldRenderer.drawBox(matrices, consumer,
                    new Box(x - s, y - s, z - s, x + s, y + s, z + s),
                    r, g, b, alpha);
        }
    }

    private static double lerp(double a, double b, float t) {
        return a + (b - a) * t;
    }
}
