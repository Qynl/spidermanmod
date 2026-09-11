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
 * Renders the active web strand (swing line, zip/pull/shot tracer) as a
 * dotted line from the firing wrist to the anchor point.
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
        float delta = wrc.tickCounter().getTickDelta(true);
        double px = lerp(player.prevX, player.getX(), delta);
        double py = lerp(player.prevY, player.getY(), delta);
        double pz = lerp(player.prevZ, player.getZ(), delta);

        Vec3d look = player.getRotationVector();
        Vec3d right = look.crossProduct(new Vec3d(0.0, 1.0, 0.0));
        if (right.lengthSquared() < 0.001) {
            right = new Vec3d(1.0, 0.0, 0.0);
        }
        right = right.normalize();
        double side = ClientPowers.swingHand == 1 ? -0.35 : 0.35;
        double hx = px + right.x * side + look.x * 0.4;
        double hy = py + 1.35 + look.y * 0.4;
        double hz = pz + right.z * side + look.z * 0.4;

        Vec3d cam = wrc.camera().getPos();
        MatrixStack matrices = wrc.matrixStack();
        matrices.push();
        matrices.translate(-cam.x, -cam.y, -cam.z);
        VertexConsumer consumer = wrc.consumers().getBuffer(RenderLayer.getLines());
        int dots = 12;
        for (int i = 0; i <= dots; i++) {
            double t = i / (double) dots;
            double x = hx + (ClientPowers.swingX - hx) * t;
            double y = hy + (ClientPowers.swingY - hy) * t;
            double z = hz + (ClientPowers.swingZ - hz) * t;
            double s = 0.035;
            WorldRenderer.drawBox(matrices, consumer,
                    new Box(x - s, y - s, z - s, x + s, y + s, z + s),
                    0.92f, 0.92f, 0.95f, 1.0f);
        }
        matrices.pop();
    }

    private static double lerp(double a, double b, float t) {
        return a + (b - a) * t;
    }
}
