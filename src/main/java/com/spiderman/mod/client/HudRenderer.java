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
 * ULTIMATE HUD - Comic book Spider-Man style.
 * - Animated web pattern
 * - Ability wheel with cooldowns
 * - Style meter
 * - Combo with flair
 * - Sense indicators
 * - Cinematic transformation
 */
public final class HudRenderer {
    private static final int[] DEFAULT_THRESHOLDS = {80, 250, 600, 1200};

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

        // Top left - Stage with comic style
        renderStageHud(ctx, text);

        // Center bottom - Selected ability with style
        if (AbilityIds.valid(ClientPowers.selected)) {
            renderAbilityHud(ctx, text, w, h);
        }
        
        // Combo - epic display
        if (ClientPowers.combo > 1) {
            renderComboHud(ctx, text, w, h);
        }
        
        // Style meter - new!
        renderStyleHud(ctx, text, w, h);
        
        // Sense - directional
        renderSense(ctx, text, player, w, h);
        
        // Cinematic
        renderCinematic(ctx, text, w, h);
        
        // Air tricks / movement hints
        renderMovementHints(ctx, text, w, h);
    }

    private static void renderStageHud(DrawContext ctx, TextRenderer text) {
        int stage = ClientPowers.stage;
        int mastery = ClientPowers.mastery;
        int nextThreshold = getNextThreshold(stage);
        String stageName = getStageName(stage);

        // Comic book style background
        ctx.fill(8, 8, 160, 32, 0xCC000000);
        ctx.fill(8, 8, 160, 10, 0xFFCC0000); // Red top bar
        ctx.fill(8, 30, 160, 32, 0xFFCC0000); // Red bottom bar
        
        // Web pattern corners
        ctx.fill(8, 8, 10, 32, 0xFFFFFFFF);
        ctx.fill(158, 8, 160, 32, 0xFFFFFFFF);

        ctx.drawTextWithShadow(text,
                Text.literal("§l§cSPIDER-MAN §f" + stageName + " §7[" + stage + "]"),
                14, 12, 0xFFFFFF);

        if (stage < 4) {
            int prevThreshold = stage == 0 ? 0 : DEFAULT_THRESHOLDS[stage - 1];
            int needed = nextThreshold - prevThreshold;
            int have = mastery - prevThreshold;
            if (have < 0) have = 0;
            if (needed < 1) needed = 1;
            float progress = Math.min(1.0f, (float) have / (float) needed);
            int barW = 140;
            int filled = (int) (barW * progress);

            // Progress bar with comic style
            ctx.fill(12, 22, 12 + barW, 28, 0x88000000);
            int col = stage == 0 ? 0xFFAA0000 : stage == 1 ? 0xFFFF5500 : stage == 2 ? 0xFF00AAFF : 0xFF55FF55;
            // Animated fill
            int animOffset = (int)(System.currentTimeMillis() / 50 % 10);
            ctx.fill(12, 22, 12 + filled, 28, col);
            // Shine effect
            ctx.fill(12, 22, 12 + filled, 24, 0x66FFFFFF);
            // Border
            ctx.fill(12, 22, 12 + barW, 23, 0xFFFFFFFF);
            ctx.fill(12, 27, 12 + barW, 28, 0xFFFFFFFF);

            ctx.drawTextWithShadow(text,
                    Text.literal("§7" + mastery + "§8/§7" + nextThreshold + " §8" + (int)(progress*100) + "%"),
                    14, 36, 0xFFFFFF);

            if (stage == 0 && mastery < 40) {
                ctx.drawTextWithShadow(text,
                        Text.literal("§e§lTIP: §7Live, climb, move! Powers grow with §etime!"),
                        10, 48, 0xFFFFFF);
            }
        } else {
            ctx.drawTextWithShadow(text,
                    Text.literal("§6§lMAX §eStyle: " + getStyleRank(ClientPowers.combo)),
                    14, 36, 0xFFFFFF);
        }
    }

    private static void renderAbilityHud(DrawContext ctx, TextRenderer text, int w, int h) {
        String name = AbilityIds.NAMES[ClientPowers.selected].toUpperCase();
        String[] icons = {"◉", "↗", "⚡", "⇄", "✦", "═", "✸", "●", "⬔"};
        String icon = ClientPowers.selected < icons.length ? icons[ClientPowers.selected] : "•";
        
        // Center bottom with comic style
        int cx = w / 2;
        int y = h - 62;
        
        // Background
        ctx.fill(cx - 80, y - 6, cx + 80, y + 14, 0xCC000000);
        ctx.fill(cx - 80, y - 6, cx + 80, y - 4, 0xFF00AAFF);
        ctx.fill(cx - 80, y + 12, cx + 80, y + 14, 0xFF00AAFF);
        
        String hint = ClientPowers.stage >= 2 ? "§8[R] §7Use §8[G]§7Wheel" : "§8[G]§7Wheel";
        ctx.drawCenteredTextWithShadow(text,
                Text.literal("§f§l" + icon + " " + name + " §7" + hint),
                cx, y, 0xFFFFFF);
                
        // Cooldown indicators
        if (ClientPowers.swingActive) {
            ctx.drawCenteredTextWithShadow(text,
                    Text.literal("§a§lSWINGING! §7Look up=fast §8| §7Down=arc §8| §7Sprint=boost"),
                    cx, y + 12, 0xFFFFFF);
        }
    }

    private static void renderComboHud(DrawContext ctx, TextRenderer text, int w, int h) {
        int combo = ClientPowers.combo;
        String rank = getStyleRank(combo);
        int color = combo < 3 ? 0xFFFFAA : combo < 5 ? 0xFF55FF : 0xFFFF55;
        
        // Epic combo display
        int y = h / 2 + 30;
        float scale = 1.0f + (combo * 0.05f);
        if (scale > 1.5f) scale = 1.5f;
        
        // Background pulse
        int pulse = (int)(Math.sin(System.currentTimeMillis() / 100.0) * 10 + 15);
        ctx.fill(w/2 - 60, y - 4, w/2 + 60, y + 18, (pulse << 24) | 0x000000);
        
        ctx.drawCenteredTextWithShadow(text,
                Text.literal("§6§lCOMBO §e§l×" + combo + " §7" + rank),
                w / 2, y, color);
                
        if (combo >= 5) {
            ctx.drawCenteredTextWithShadow(text,
                    Text.literal("§e§lSTYLE! §fKeep it up!"),
                    w / 2, y + 12, 0xFFFFFF);
        }
    }

    private static void renderStyleHud(DrawContext ctx, TextRenderer text, int w, int h) {
        // Style points in top right
        int style = 0;
        try {
            // We don't have direct access to stylePoints on client, use combo as proxy
            style = ClientPowers.combo * 10;
        } catch (Exception ignored) {}
        
        if (style > 0 || ClientPowers.combo > 2) {
            String styleText = "§7Style: §e" + style;
            ctx.drawTextWithShadow(text,
                    Text.literal(styleText),
                    w - 100, 10, 0xFFFFFF);
        }
    }

    private static String getStyleRank(int combo) {
        if (combo >= 10) return "§6§lULTIMATE!";
        if (combo >= 8) return "§c§lSPECTACULAR!";
        if (combo >= 6) return "§5§lAMAZING!";
        if (combo >= 4) return "§a§lGREAT!";
        if (combo >= 2) return "§eNice!";
        return "";
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
                int x = (int) (w / 2.0 + Math.sin(rad) * 85.0);
                int y = (int) (h / 2.0 - Math.cos(rad) * 60.0);
                int kind = (int) ping[3];
                
                String symbol = "!";
                String color = "§c";
                if (kind == 1) { symbol = "⚠"; color = "§6"; }
                else if (kind == 2) { symbol = "⬇"; color = "§e"; }
                
                // Pulse effect
                double pulse = Math.sin(System.currentTimeMillis() / 150.0) * 0.2 + 1.0;
                int px = (int)(x * pulse);
                
                // Background
                ctx.fill(x - 8, y - 8, x + 8, y + 8, 0x88000000);
                ctx.fill(x - 8, y - 8, x + 8, y - 6, 0xFFFF0000);
                
                ctx.drawCenteredTextWithShadow(text, Text.literal(color + "§l" + symbol), x, y - 4, 0xFFFFFF);
            } catch (Exception ignored) {}
        }
    }

    private static void renderCinematic(DrawContext ctx, TextRenderer text, int w, int h) {
        int ticks = ClientPowers.cinematicTicks;
        if (ticks <= 0) {
            return;
        }
        int alpha = 80 + (int) (60.0 * Math.sin(ticks * 0.3));
        if (alpha < 0) alpha = 0;
        if (alpha > 160) alpha = 160;
        
        // Red vignette
        ctx.fill(0, 0, w, h, (alpha << 24) | 0x660000);
        // Web pattern overlay
        if (ticks % 10 < 5) {
            ctx.fill(0, h/2 - 2, w, h/2 + 2, 0x88FFFFFF);
        }
        
        if (ticks > 120) {
            ctx.drawCenteredTextWithShadow(text,
                    Text.literal("§c§l§nWITH GREAT POWER..."), w / 2, h / 2 - 30, 0xFFFFFF);
            ctx.drawCenteredTextWithShadow(text,
                    Text.literal("§f§lCOMES GREAT RESPONSIBILITY"), w / 2, h / 2 - 10, 0xFFFFFF);
        } else if (ticks > 80) {
            ctx.drawCenteredTextWithShadow(text,
                    Text.literal("§c§lYOU FEEL DIFFERENT"), w / 2, h / 2 - 20, 0xFFFFFF);
            ctx.drawCenteredTextWithShadow(text,
                    Text.literal("§7Your spider-sense tingles... powers awaken"), w / 2, h / 2, 0xFFFFFF);
        } else if (ticks > 40) {
            ctx.drawCenteredTextWithShadow(text,
                    Text.literal("§e§lHold G §f+ §eMove Mouse §f→ §aRelease to equip webs"), w / 2, h / 2 + 10, 0xFFFFFF);
            ctx.drawCenteredTextWithShadow(text,
                    Text.literal("§7Climb walls automatically §8| §7Sprint to wall-run §8| §7Look up while swinging to go fast!"), w / 2, h / 2 + 24, 0xFFFFFF);
        } else {
            ctx.drawCenteredTextWithShadow(text,
                    Text.literal("§a§lGO! §fSwing, climb, be amazing!"), w / 2, h / 2 + 20, 0xFFFFFF);
        }
    }

    private static void renderMovementHints(DrawContext ctx, TextRenderer text, int w, int h) {
        // Show hints based on state
        try {
            if (ClientPowers.swingActive) {
                // Already handled in ability hud
                return;
            }
            // Could add wall run hint, dive hint, etc based on player state
            // For now keep clean
        } catch (Exception ignored) {}
    }

    private static int getNextThreshold(int stage) {
        if (stage < 0) return DEFAULT_THRESHOLDS[0];
        if (stage >= 4) return DEFAULT_THRESHOLDS[3];
        if (stage < DEFAULT_THRESHOLDS.length) return DEFAULT_THRESHOLDS[stage];
        return DEFAULT_THRESHOLDS[DEFAULT_THRESHOLDS.length - 1];
    }

    private static String getStageName(int stage) {
        switch (stage) {
            case 0: return "Latent";
            case 1: return "Wall Crawler";
            case 2: return "Web Slinger";
            case 3: return "Sky Dancer";
            case 4: return "Spider Master";
            default: return "Unknown";
        }
    }

    private static double wrapDegrees(double degrees) {
        double d = degrees % 360.0;
        if (d >= 180.0) d -= 360.0;
        if (d < -180.0) d += 360.0;
        return d;
    }
}
