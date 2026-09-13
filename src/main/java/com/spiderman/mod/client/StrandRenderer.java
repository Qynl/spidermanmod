package com.spiderman.mod.client;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import com.spiderman.mod.state.ClientPowers;

/**
 * INSOMNIAC REAL WEB - Looks like actual Spider-Man thread, not Minecraft boxes.
 * - Uses custom braided white silk texture
 * - Ultra thin, realistic catenary with tension and elasticity
 * - Shine and slight transparency
 * - Tiny anchor/wrist, no big glowing boxes
 */
public final class StrandRenderer {
    private static final Identifier WEB_LINE = Identifier.of("spiderman", "textures/entity/web_line.png");
    private static final Identifier WHITE_WOOL = Identifier.of("minecraft", "textures/block/white_wool.png");

    private StrandRenderer() {}

    public static void afterEntities(WorldRenderContext wrc) {
        if (!ClientPowers.swingActive) return;
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        if (player == null || client.world == null) return;

        float lifeFactor = 1.0f;
        if (ClientPowers.swingLife > 0) {
            lifeFactor = Math.min(1.0f, ClientPowers.swingLife / 14.0f);
            if (lifeFactor < 0.06f) return;
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

        double side = ClientPowers.swingHand == 1 ? -0.30 : 0.30;
        double hx = px + right.x * side + look.x * 0.32;
        double hy = py + 1.30 + look.y * 0.32;
        double hz = pz + right.z * side + look.z * 0.32;

        double ax = ClientPowers.swingX;
        double ay = ClientPowers.swingY;
        double az = ClientPowers.swingZ;

        if (!Double.isFinite(ax)) return;

        Vec3d cam = wrc.camera().getPos();
        MatrixStack matrices = wrc.matrixStack();
        matrices.push();
        matrices.translate(-cam.x, -cam.y, -cam.z);

        VertexConsumer lines = wrc.consumers().getBuffer(RenderLayer.getLines());
        VertexConsumer translucent = wrc.consumers().getBuffer(RenderLayer.getEntityTranslucent(WEB_LINE));

        boolean isSwing = ClientPowers.swingLife == 0;
        int segments = isSwing ? 72 : 28; // More segments = smoother like real thread

        // Real Spider-Man web: pure white with slight warm, not blue
        float r = 0.97f, g = 0.97f, b = 0.95f;
        float alpha = 0.96f * lifeFactor;

        double dx = ax - hx;
        double dy = ay - hy;
        double dz = az - hz;
        double dist = Math.sqrt(dx*dx + dy*dy + dz*dz);
        double horizDist = Math.sqrt(dx*dx + dz*dz);

        double speed = 0;
        try { speed = player.getVelocity().length(); } catch (Exception ignored) {}
        double tension = Math.min(1.0, (speed * 0.28 + dist * 0.022));
        double sagAmount = (1.0 - tension) * Math.min(3.2, horizDist * 0.20 + 0.7);
        if (!isSwing) sagAmount *= 0.35;

        double stretch = 1.0 + Math.min(0.06, speed * 0.012);

        Vec3d prev = new Vec3d(hx, hy, hz);
        for (int i = 1; i <= segments; i++) {
            double t = (double) i / (double) segments;
            double tStretched = t * stretch;
            if (tStretched > 1.0) tStretched = 1.0;

            double x = hx + (ax - hx) * tStretched;
            double y = hy + (ay - hy) * tStretched;
            double z = hz + (az - hz) * tStretched;

            if (sagAmount > 0.01) {
                double catenary = Math.sin(tStretched * Math.PI) * sagAmount;
                if (isSwing) {
                    double swingPhase = Math.sin(ClientPowers.clientTick * 0.07 + tStretched * 2.2) * 0.035 * (1.0 - tension);
                    x += swingPhase;
                    double swayZ = Math.cos(ClientPowers.clientTick * 0.05 + tStretched * 2.8) * 0.025 * (1.0 - tension);
                    z += swayZ;
                }
                y -= catenary;
            }

            Vec3d curr = new Vec3d(x, y, z);

            float thickFactor = (float)(1.0 - tStretched * 0.45);
            float lineAlpha = alpha * (0.75f + thickFactor * 0.25f);

            // Main thread - ultra thin realistic white
            drawRealisticLine(matrices, lines, prev, curr, r, g, b, lineAlpha);

            // Subtle shine every 4 segments for Insomniac glow
            if (isSwing && i % 5 == 0) {
                drawRealisticLine(matrices, lines, prev, curr, 1.0f, 1.0f, 1.0f, lineAlpha * 0.30f);
            }

            prev = curr;
        }

        // Tiny realistic anchor and wrist - not big boxes
        double anchorSize = 0.035 * lifeFactor;
        WorldRenderer.drawBox(matrices, lines,
                new Box(ax - anchorSize, ay - anchorSize, az - anchorSize,
                        ax + anchorSize, ay + anchorSize, az + anchorSize),
                1.0f, 1.0f, 1.0f, 0.92f * lifeFactor);

        double wristSize = 0.022 * lifeFactor;
        WorldRenderer.drawBox(matrices, lines,
                new Box(hx - wristSize, hy - wristSize, hz - wristSize,
                        hx + wristSize, hy + wristSize, hz + wristSize),
                1.0f, 1.0f, 1.0f, 0.88f * lifeFactor);

        matrices.pop();
    }

    private static void drawRealisticLine(MatrixStack matrices, VertexConsumer consumer,
                                          Vec3d p1, Vec3d p2,
                                          float r, float g, float b, float a) {
        if (a < 0.04f) return;
        Matrix4f mat = matrices.peek().getPositionMatrix();

        if (a > 1.0f) a = 1.0f;
        if (a < 0.0f) return;

        float x1 = (float)p1.x, y1 = (float)p1.y, z1 = (float)p1.z;
        float x2 = (float)p2.x, y2 = (float)p2.y, z2 = (float)p2.z;

        float dx = x2 - x1;
        float dy = y2 - y1;
        float dz = z2 - z1;
        float len = (float)Math.sqrt(dx*dx + dy*dy + dz*dz);
        if (len < 0.001f) return;

        float nx = -dy;
        float ny = dx;
        float nz = 0;
        float nLen = (float)Math.sqrt(nx*nx + ny*ny + nz*nz);
        if (nLen < 0.001f) {
            nx = 0; ny = 0; nz = 1;
            nLen = 1;
        }
        nx /= nLen; ny /= nLen; nz /= nLen;

        consumer.vertex(mat, x1, y1, z1).color(r, g, b, a).normal(nx, ny, nz);
        consumer.vertex(mat, x2, y2, z2).color(r, g, b, a).normal(nx, ny, nz);
    }

    private static double lerp(double a, double b, float t) {
        return a + (b - a) * t;
    }
}
