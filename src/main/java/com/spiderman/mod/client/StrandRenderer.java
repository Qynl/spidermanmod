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
 * Renders the active web strand as a thick white line from wrist to anchor.
 * Now with:
 * - Thick white line (not dotted)
 * - Catenary sag for swing
 * - Glow effect
 * - Works for all abilities: shot, swing, zip, pull, trap, line, burst, impact, platform
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

        // Fade based on life
        float lifeFactor = 1.0f;
        if (ClientPowers.swingLife > 0) {
            lifeFactor = Math.min(1.0f, ClientPowers.swingLife / 20.0f);
            // Don't render if almost dead
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

        double side = ClientPowers.swingHand == 1 ? -0.35 : 0.35;
        double hx = px + right.x * side + look.x * 0.4;
        double hy = py + 1.35 + look.y * 0.4;
        double hz = pz + right.z * side + look.z * 0.4;

        double ax = ClientPowers.swingX;
        double ay = ClientPowers.swingY;
        double az = ClientPowers.swingZ;

        // Validate anchor
        if (!Double.isFinite(ax) || !Double.isFinite(ay) || !Double.isFinite(az)) return;

        Vec3d cam = wrc.camera().getPos();
        MatrixStack matrices = wrc.matrixStack();
        matrices.push();
        matrices.translate(-cam.x, -cam.y, -cam.z);

        VertexConsumer consumer = wrc.consumers().getBuffer(RenderLayer.getLines());
        VertexConsumer glowConsumer = wrc.consumers().getBuffer(RenderLayer.getLines());

        // Determine if this is swing (long life 0) or short ability (zip/pull etc)
        boolean isSwing = ClientPowers.swingLife == 0;
        int segments = isSwing ? 32 : 24;

        // Color: bright white for web, with slight blue tint for swing, yellow for zip/pull
        float r = 0.95f, g = 0.95f, b = 1.0f;
        if (!isSwing) {
            // Zip/pull/trap etc — slightly brighter white with alpha based on life
            r = 1.0f; g = 1.0f; b = 1.0f;
        }

        // Draw main white line with catenary sag for swing
        for (int i = 0; i < segments; i++) {
            double t1 = (double) i / (double) segments;
            double t2 = (double) (i + 1) / (double) segments;

            Vec3d p1 = interpolateWithSag(hx, hy, hz, ax, ay, az, t1, isSwing);
            Vec3d p2 = interpolateWithSag(hx, hy, hz, ax, ay, az, t2, isSwing);

            // Draw segment as box for thickness — white line
            double thickness = isSwing ? 0.06 : 0.05;
            // For long swing, make line thicker near player
            if (isSwing) {
                thickness = 0.07 - t1 * 0.02;
            }
            thickness *= lifeFactor;

            // Draw line segment using boxes (thick white line approximation)
            // We draw a small box at p1 and a line to p2 via multiple boxes
            drawThickLine(matrices, consumer, p1, p2, thickness, r, g, b, 1.0f * lifeFactor);

            // Glow layer — slightly larger, lower alpha, for web glow effect
            if (i % 2 == 0) {
                drawThickLine(matrices, glowConsumer, p1, p2, thickness * 1.8, r * 0.8f, g * 0.8f, b, 0.3f * lifeFactor);
            }
        }

        // Draw anchor point as small white box for visibility
        double anchorSize = 0.12 * lifeFactor;
        WorldRenderer.drawBox(matrices, consumer,
                new Box(ax - anchorSize, ay - anchorSize, az - anchorSize,
                        ax + anchorSize, ay + anchorSize, az + anchorSize),
                1.0f, 1.0f, 1.0f, 1.0f * lifeFactor);

        // Draw wrist origin as small box
        double wristSize = 0.08 * lifeFactor;
        WorldRenderer.drawBox(matrices, consumer,
                new Box(hx - wristSize, hy - wristSize, hz - wristSize,
                        hx + wristSize, hy + wristSize, hz + wristSize),
                1.0f, 1.0f, 1.0f, 1.0f * lifeFactor);

        matrices.pop();
    }

    private static Vec3d interpolateWithSag(double hx, double hy, double hz,
                                            double ax, double ay, double az,
                                            double t, boolean isSwing) {
        double x = hx + (ax - hx) * t;
        double y = hy + (ay - hy) * t;
        double z = hz + (az - hz) * t;

        if (isSwing) {
            // Add catenary sag for swing — makes it look like real web with gravity
            double dist = Math.sqrt((ax - hx) * (ax - hx) + (az - hz) * (az - hz));
            double sag = Math.sin(t * Math.PI) * Math.min(3.0, dist * 0.15);
            y -= sag;
        }

        return new Vec3d(x, y, z);
    }

    private static void drawThickLine(MatrixStack matrices, VertexConsumer consumer,
                                      Vec3d p1, Vec3d p2, double thickness,
                                      float r, float g, float b, float a) {
        // Draw line as series of boxes for thickness
        // For simplicity, draw box at midpoint with size based on thickness and segment length
        double mx = (p1.x + p2.x) * 0.5;
        double my = (p1.y + p2.y) * 0.5;
        double mz = (p1.z + p2.z) * 0.5;
        double s = thickness;

        // If segment is long, draw multiple boxes along it for continuity
        double segLen = p1.distanceTo(p2);
        int boxes = (int) Math.max(1, segLen / (thickness * 2));
        for (int i = 0; i < boxes; i++) {
            double tt = (double) i / (double) boxes;
            double x = p1.x + (p2.x - p1.x) * tt;
            double y = p1.y + (p2.y - p1.y) * tt;
            double z = p1.z + (p2.z - p1.z) * tt;
            WorldRenderer.drawBox(matrices, consumer,
                    new Box(x - s, y - s, z - s, x + s, y + s, z + s),
                    r, g, b, a);
        }
    }

    private static double lerp(double a, double b, float t) {
        return a + (b - a) * t;
    }
}
