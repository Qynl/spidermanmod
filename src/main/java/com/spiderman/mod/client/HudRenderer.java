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
 *
 * Bughunt + progression improvements:
 * - Shows mastery progress bar to next stage (time-based progression visible)
 * - Shows time-based hint for stage 0 players
 * - Safer null checks
 * - Doesn't render when in debug screen or paused
 */
public final class HudRenderer {
    private static final int[] DEFAULT_THRESHOLDS = {100, 300, 700, 1400};

    private HudRenderer() {
    }

    public static void onHudRender(DrawContext ctx, RenderTickCounter counter) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        if (player == null || !ClientPowers.has) {
            return;
        }
        try {
            if (client.isPaused()) return;
        } catch (Exception ignored) {}

        TextRenderer text = client.textRenderer;
        int w = ctx.getScaledWindowWidth();
        int h = ctx.getScaledWindowHeight();

        // Stage + mastery with progress bar
        int stage = ClientPowers.stage;
        int mastery = ClientPowers.mastery;
        int nextThreshold = getNextThreshold(stage);

        String stageName = getStageName(stage);
        ctx.drawTextWithShadow(text,
                Text.literal("§c[Spidey] §f" + stageName + " §7(Stage " + stage + ")"),
                10, 10, 0xFFFFFF);

        if (stage < 4) {
            int prevThreshold = stage == 0 ? 0 : DEFAULT_THRESHOLDS[stage - 1];
            int needed = nextThreshold - prevThreshold;
            int have = mastery - prevThreshold;
            if (have < 0) have = 0;
            if (needed < 1) needed = 1;
            float progress = Math.min(1.0f, (float) have / (float) needed);
            int barW = 100;
            int filled = (int) (barW * progress);

            // Background bar
            ctx.fill(10, 22, 10 + barW, 26, 0x88000000);
            // Filled progress — color based on stage
            int col = stage == 0 ? 0xFFAA0000 : stage == 1 ? 0xFFFF5500 : stage == 2 ? 0xFF00AAFF : 0xFF55FF55;
            ctx.fill(10, 22, 10 + filled, 26, col);
            // Border
            ctx.fill(10, 22, 10 + barW, 23, 0xFFFFFFFF);
            ctx.fill(10, 25, 10 + barW, 26, 0xFFFFFFFF);
            ctx.fill(10, 22, 11, 26, 0xFFFFFFFF);
            ctx.fill(10 + barW - 1, 22, 10 + barW, 26, 0xFFFFFFFF);

            ctx.drawTextWithShadow(text,
                    Text.literal("§7Mastery " + mastery + " / " + nextThreshold + " §8(" + (int)(progress*100) + "%)"),
                    10, 28, 0xFFFFFF);

            // Time-based hint for early stages
            if (stage == 0 && mastery < 50) {
                ctx.drawTextWithShadow(text,
                        Text.literal("§7Climb walls, move, live — powers grow with §etime§7!"),
                        10, 40, 0xFFFFFF);
            } else if (stage < 4 && progress < 0.3f) {
                ctx.drawTextWithShadow(text,
                        Text.literal("§7Use webs, swing, fight — mastery grows with time & action"),
                        10, 40, 0xFFFFFF);
            }
        } else {
            ctx.drawTextWithShadow(text,
                    Text.literal("§a§lMAX STAGE §7Mastery " + mastery),
                    10, 22, 0xFFFFFF);
        }

        if (AbilityIds.valid(ClientPowers.selected)) {
            String hint = ClientPowers.stage >= 2 ? "§7[R] use  §e[G] hold=wheel" : "§7[G] hold=wheel";
            ctx.drawCenteredTextWithShadow(text,
                    Text.literal("§e" + AbilityIds.NAMES[ClientPowers.selected] + " " + hint),
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

    private static int getNextThreshold(int stage) {
        if (stage < 0) return DEFAULT_THRESHOLDS[0];
        if (stage >= 4) return DEFAULT_THRESHOLDS[3];
        if (stage < DEFAULT_THRESHOLDS.length) return DEFAULT_THRESHOLDS[stage];
        return DEFAULT_THRESHOLDS[DEFAULT_THRESHOLDS.length - 1];
    }

    private static String getStageName(int stage) {
        switch (stage) {
            case 0: return "Latent Senses";
            case 1: return "Wall Crawler";
            case 2: return "Web Slinger";
            case 3: return "Sky Dancer";
            case 4: return "Spider Master";
            default: return "Unknown";
        }
    }

    private static void renderSense(DrawContext ctx, TextRenderer text,
            ClientPlayerEntity player, int w, int h) {
        if (ClientPowers.pings == null || ClientPowers.pings.isEmpty()) {
            return;
        }
        float yaw;
        try {
            yaw = player.getYaw();
        } catch (Exception e) {
            return;
        }
        for (double[] ping : ClientPowers.pings) {
            if (ping == null || ping.length < 4) continue;
            try {
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
            } catch (Exception ignored) {}
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
                    Text.literal("§7your senses sharpen... time will make you stronger"), w / 2, h / 2, 0xFFFFFF);
        } else if (ticks > 40) {
            ctx.drawCenteredTextWithShadow(text,
                    Text.literal("§7Hold §eG §7+ move mouse to choose webs — release to equip"), w / 2, h / 2 + 20, 0xFFFFFF);
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
