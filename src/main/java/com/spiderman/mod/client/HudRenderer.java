package com.spiderman.mod.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;

import com.spiderman.mod.state.AbilityIds;
import com.spiderman.mod.state.ClientPowers;

/**
 * HUD: stage/mastery readout, selected ability, combo counter, directional
 * spider-sense markers and the transformation cinematic overlay.
 */
public final class HudRenderer {
    private HudRenderer() {
    }

    public static void onHudRender(DrawContext ctx, RenderTickCounter counter) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        if (player == null || !ClientPowers.has || client.isPaused()) {
            return;
        }
        TextRenderer text = client.textRenderer;
        int w = ctx.getScaledWindowWidth();
        int h = ctx.getScaledWindowHeight();

        ctx.drawTextWithShadow(text,
                Text.literal("§c[Spidey] §fStage " + ClientPowers.stage
                        + " §7Mastery " + ClientPowers.mastery),
                10, 10, 0xFFFFFF);

        if (AbilityIds.valid(ClientPowers.selected)) {
            ctx.drawCenteredTextWithShadow(text,
                    Text.literal("§e" + AbilityIds.NAMES[ClientPowers.selected] + " §7[R]  [G]=wheel"),
                    w / 2, h - 58, 0xFFFFFF);
        }
        if (ClientPowers.combo > 1) {
            ctx.drawCenteredTextWithShadow(text,
                    Text.literal("§6§lCOMBO x" + ClientPowers.combo),
                    w / 2, h / 2 + 44, 0xFFFFFF);
        }
        renderSense(ctx, text, player, w, h);
        renderCinematic(ctx, text, w, h);
    }

    private static void renderSense(DrawContext ctx, TextRenderer text,
            ClientPlayerEntity player, int w, int h) {
        if (ClientPowers.pings.isEmpty()) {
            return;
        }
        float yaw = player.getYaw();
        for (double[] ping : ClientPowers.pings) {
            double dx = ping[0] - player.getX();
            double dz = ping[2] - player.getZ();
            double threatYaw = Math.toDegrees(Math.atan2(-dx, dz));
            double rel = wrapDegrees(threatYaw - yaw);
            double rad = Math.toRadians(rel);
            int x = (int) (w / 2.0 + Math.sin(rad) * 72.0);
            int y = (int) (h / 2.0 - Math.cos(rad) * 52.0);
            int kind = (int) ping[3];
            String color = kind == 1 ? "§6" : kind == 2 ? "§e" : "§c";
            ctx.drawCenteredTextWithShadow(text, Text.literal(color + "!"), x, y, 0xFFFFFF);
        }
    }

    private static void renderCinematic(DrawContext ctx, TextRenderer text, int w, int h) {
        int ticks = ClientPowers.cinematicTicks;
        if (ticks <= 0) {
            return;
        }
        int alpha = 70 + (int) (50.0 * Math.sin(ticks * 0.25));
        if (alpha < 0) {
            alpha = 0;
        }
        if (alpha > 140) {
            alpha = 140;
        }
        ctx.fill(0, 0, w, h, (alpha << 24) | 0x550000);
        if (ticks > 100) {
            ctx.drawCenteredTextWithShadow(text,
                    Text.literal("§c§lYOU FEEL DIFFERENT"), w / 2, h / 2 - 20, 0xFFFFFF);
            ctx.drawCenteredTextWithShadow(text,
                    Text.literal("§7your senses sharpen..."), w / 2, h / 2, 0xFFFFFF);
        }
    }

    private static double wrapDegrees(double degrees) {
        double d = degrees % 360.0;
        if (d >= 180.0) {
            d -= 360.0;
        }
        if (d < -180.0) {
            d += 360.0;
        }
        return d;
    }
}
