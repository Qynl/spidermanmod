package com.spiderman.mod.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import com.spiderman.mod.ModSounds;
import com.spiderman.mod.net.WheelSelectC2S;
import com.spiderman.mod.state.AbilityIds;
import com.spiderman.mod.state.ClientPowers;

/**
 * ULTIMATE WHEEL - Comic book Spider-Man style, flicker-free, epic visuals.
 * - 9 abilities, 40° sectors
 * - White web lines, glowing nodes
 * - Cooldown indicators, style, descriptions
 * - Smooth hover and select
 */
public class WheelScreen extends Screen {
    private static final String[] DESCRIPTIONS = {
        "§fQuick Shot §7- Fast white line, instant web glob, combo starter",
        "§aSwing §7- Long white line, real pendulum, look up=fast, sprint=boost",
        "§bZip §7- Dash to point, white line, epic launch, chainable",
        "§ePull §7- Yank target or self, hold to pull, white line, damage",
        "§2Trap §7- Cluster webs, slow+weakness, 16 webs, glowing",
        "§9Line §7- Web bridge, walkable, 30 blocks, high-speed traverse",
        "§cBurst §7- Nova explosion, 6.5 radius, knockback, webs everywhere",
        "§4Impact §7- Heavy ball, 14 damage, crater, explosion, style",
        "§aPlatform §7- 5x5 standable webs, bounce, spiders ignore, safe"
    };

    private static final String[] ICONS = {
        "◉", "↗", "⚡", "⇄", "✦", "═", "✸", "●", "⬔"
    };
    
    private static final String[] SHORT_NAMES = {
        "SHOT", "SWING", "ZIP", "PULL", "TRAP", "LINE", "BURST", "IMPACT", "PLATFORM"
    };

    private int hovered = -1;
    private int ticksOpen = 0;
    private long openTime = 0;

    public WheelScreen() {
        super(Text.literal("Web Abilities"));
        this.openTime = System.currentTimeMillis();
    }

    @Override
    public void tick() {
        super.tick();
        ticksOpen++;
        // FIXED: Don't auto-close here, let ClientTickHandler handle it with robust GLFW check
        // This prevents interval closing
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }

    public int getHovered() {
        return hovered;
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        // Dark with red tint - Spider-Man style
        ctx.fill(0, 0, width, height, 0x88000000);
        ctx.fill(0, 0, width, 4, 0xFFCC0000);
        ctx.fill(0, height - 4, width, height, 0xFFCC0000);

        int cx = width / 2;
        int cy = height / 2;
        hovered = sectorAt(mouseX - cx, mouseY - cy);

        drawWebBackground(ctx, cx, cy);

        // Title with comic style
        long time = System.currentTimeMillis();
        int pulse = (int)(Math.sin(time / 200.0) * 20 + 30);
        ctx.fill(cx - 100, cy - 132, cx + 100, cy - 104, 0xCC000000);
        ctx.fill(cx - 100, cy - 132, cx + 100, cy - 130, 0xFFCC0000);
        ctx.drawCenteredTextWithShadow(textRenderer,
                Text.literal("§c§l◈ §f§lWEB ABILITIES §7[" + AbilityIds.COUNT + "] §c§l◈"), cx, cy - 126, 0xFFFFFF);

        // Center - selected or hovered
        String centerLabel;
        String centerSub = "";
        if (hovered >= 0 && ClientPowers.stage >= AbilityIds.MIN_STAGE[hovered]) {
            centerLabel = "§f§l" + SHORT_NAMES[hovered];
            centerSub = "§7Stage " + AbilityIds.MIN_STAGE[hovered] + " §8| §eReady";
        } else if (AbilityIds.valid(ClientPowers.selected)) {
            centerLabel = "§7" + SHORT_NAMES[ClientPowers.selected];
            centerSub = "§8Equipped";
        } else {
            centerLabel = "§8...";
            centerSub = "§7Select";
        }
        
        // Center box with web pattern
        ctx.fill(cx - 45, cy - 18, cx + 45, cy + 18, 0xDD000000);
        ctx.fill(cx - 45, cy - 18, cx + 45, cy - 16, 0xFF00AAFF);
        ctx.fill(cx - 45, cy + 16, cx + 45, cy + 18, 0xFF00AAFF);
        ctx.fill(cx - 45, cy - 18, cx - 43, cy + 18, 0xFFFFFFFF);
        ctx.fill(cx + 43, cy - 18, cx + 45, cy + 18, 0xFFFFFFFF);
        
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(centerLabel), cx, cy - 8, 0xFFFFFF);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(centerSub), cx, cy + 4, 0xFFFFFF);

        int radius = 92;
        float sectorAngle = 360.0f / AbilityIds.COUNT;

        for (int i = 0; i < AbilityIds.COUNT; i++) {
            double angleDeg = -90.0 + i * sectorAngle;
            double angle = Math.toRadians(angleDeg);
            int x = cx + (int) (Math.cos(angle) * radius);
            int y = cy + (int) (Math.sin(angle) * radius);

            boolean locked = ClientPowers.stage < AbilityIds.MIN_STAGE[i];
            boolean isHovered = (i == hovered);
            boolean isSelected = (i == ClientPowers.selected);

            // White web line from center to node
            int lineColor = isHovered ? 0xFFFFFF55 : isSelected ? 0xFF55FF55 : 0x88FFFFFF;
            drawLine(ctx, cx, cy, x, y, lineColor);
            if (isHovered) {
                drawLine(ctx, cx, cy, x, y, 0x44FFFF55);
            }

            int bgColor;
            if (locked) bgColor = 0x66000000;
            else if (isHovered) bgColor = 0xFFAA0000;
            else if (isSelected) bgColor = 0xFF00AA00;
            else bgColor = 0xAA000000;

            String icon = i < ICONS.length ? ICONS[i] : "?";
            String name = SHORT_NAMES[i];

            int nodeW = textRenderer.getWidth(name) + 28;
            int nodeH = 20;
            
            // Node background with border
            ctx.fill(x - nodeW / 2, y - nodeH/2, x + nodeW / 2, y + nodeH/2, bgColor);
            if (isHovered) {
                ctx.fill(x - nodeW / 2, y - nodeH/2, x + nodeW / 2, y - nodeH/2 + 2, 0xFFFFFFFF);
                ctx.fill(x - nodeW / 2, y + nodeH/2 - 2, x + nodeW / 2, y + nodeH/2, 0xFFFFFFFF);
                ctx.fill(x - nodeW / 2, y - nodeH/2, x - nodeW / 2 + 2, y + nodeH/2, 0xFFFFFFFF);
                ctx.fill(x + nodeW / 2 - 2, y - nodeH/2, x + nodeW / 2, y + nodeH/2, 0xFFFFFFFF);
                // Glow
                ctx.fill(x - nodeW / 2 - 2, y - nodeH/2 - 2, x + nodeW / 2 + 2, y + nodeH/2 + 2, 0x44FF0000);
            } else if (isSelected) {
                ctx.fill(x - nodeW / 2, y - nodeH/2, x + nodeW / 2, y - nodeH/2 + 1, 0xFF00FF00);
            }

            String label;
            if (locked) {
                label = "§8" + icon + " " + name + " §7[" + AbilityIds.MIN_STAGE[i] + "]";
            } else if (isHovered) {
                label = "§f§l" + icon + " " + name;
            } else if (isSelected) {
                label = "§a" + icon + " " + name + " §2✔";
            } else {
                label = "§f" + icon + " " + name;
            }
            ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(label), x, y - 4, 0xFFFFFF);
            
            // Number key hint
            String numHint = isHovered ? "§e[" + (i+1) + "]" : "§8" + (i+1);
            ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(numHint), x, y + 10, 0xFFFFFF);
        }

        // Description with epic styling
        if (hovered >= 0) {
            boolean locked = ClientPowers.stage < AbilityIds.MIN_STAGE[hovered];
            String desc = locked ? "§c§lLOCKED §7- Requires Stage " + AbilityIds.MIN_STAGE[hovered] + " §8(Mastery: " + ClientPowers.mastery + ")"
                    : DESCRIPTIONS[hovered] + " §8- §eRelease G to equip";
            
            // Desc background
            int descW = textRenderer.getWidth(desc);
            ctx.fill(cx - descW/2 - 8, cy + 108, cx + descW/2 + 8, cy + 124, 0xCC000000);
            ctx.fill(cx - descW/2 - 8, cy + 108, cx + descW/2 + 8, cy + 110, locked ? 0xFFAA0000 : 0xFF00AAFF);
            
            ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(desc), cx, cy + 112, 0xFFFFFF);
        } else {
            String hint = "§7§lHold §c§lG §7§l+ Move Mouse §7- §eRelease to Equip §8| §7Esc to Cancel §8| §fWhite Lines = Webs";
            ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(hint), cx, cy + 112, 0xFFFFFF);
        }

        // Bottom stats with style
        String stats = "§cStage " + ClientPowers.stage + " §8| §bMastery " + ClientPowers.mastery + " §8| §eCombo x" + ClientPowers.combo + " §8| §fStyle: Epic Web-Slinger";
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(stats), cx, cy + 132, 0xFFFFFF);
        
        // Controls
        ctx.drawCenteredTextWithShadow(textRenderer,
                Text.literal("§8[R] Use §8| §8[F] Quick Shot §8| §8[V] Swing §8| §8[B] Zip §8| §7Sprint=Boost §8| §7Sneak in Air=Dive"),
                cx, cy + 144, 0xAAAAAA);
    }

    private void drawWebBackground(DrawContext ctx, int cx, int cy) {
        // Epic web background
        int[] rings = {26, 54, 92, 122, 145};
        for (int i = 0; i < rings.length; i++) {
            int r = rings[i];
            int alpha = 60 - i * 8;
            if (alpha < 15) alpha = 15;
            int color = (alpha << 24) | 0xFFFFFF;
            drawCircle(ctx, cx, cy, r, color);
        }
        
        // Radial lines with web pattern
        float sectorAngle = 360.0f / AbilityIds.COUNT;
        for (int i = 0; i < AbilityIds.COUNT; i++) {
            double angle = Math.toRadians(-90.0 + i * sectorAngle + sectorAngle / 2.0);
            int x = cx + (int) (Math.cos(angle) * 155);
            int y = cy + (int) (Math.sin(angle) * 155);
            int alpha = 35;
            drawLine(ctx, cx, cy, x, y, (alpha << 24) | 0xFFFFFF);
        }
        
        // Cross web pattern
        for (int i = 0; i < 8; i++) {
            double ang = i * Math.PI * 2 / 8;
            int x1 = cx + (int)(Math.cos(ang) * 26);
            int y1 = cy + (int)(Math.sin(ang) * 26);
            int x2 = cx + (int)(Math.cos(ang) * 54);
            int y2 = cy + (int)(Math.sin(ang) * 54);
            drawLine(ctx, x1, y1, x2, y2, 0x22FFFFFF);
        }
    }

    private void drawCircle(DrawContext ctx, int cx, int cy, int radius, int color) {
        for (int i = 0; i < 48; i++) {
            double a1 = Math.toRadians(i * 7.5);
            double a2 = Math.toRadians((i + 1) * 7.5);
            int x1 = cx + (int) (Math.cos(a1) * radius);
            int y1 = cy + (int) (Math.sin(a1) * radius);
            int x2 = cx + (int) (Math.cos(a2) * radius);
            int y2 = cy + (int) (Math.sin(a2) * radius);
            drawLine(ctx, x1, y1, x2, y2, color);
        }
    }

    private void drawLine(DrawContext ctx, int x0, int y0, int x1, int y1, int color) {
        int dx = Math.abs(x1 - x0);
        int dy = Math.abs(y1 - y0);
        int steps = Math.max(dx, dy);
        if (steps == 0) {
            ctx.fill(x0, y0, x0 + 1, y0 + 1, color);
            return;
        }
        // Thicker line for better visibility
        for (int thick = -1; thick <= 1; thick++) {
            for (int i = 0; i <= steps; i++) {
                float t = (float) i / (float) steps;
                int x = (int) (x0 + (x1 - x0) * t);
                int y = (int) (y0 + (y1 - y0) * t);
                if (thick == 0) {
                    ctx.fill(x, y, x + 1, y + 1, color);
                } else if (steps > 20) {
                    // Only thick for long lines
                    ctx.fill(x + thick, y, x + thick + 1, y + 1, color & 0x44FFFFFF);
                }
            }
        }
    }

    static int sectorAt(int dx, int dy) {
        double dist = Math.sqrt(dx * dx + dy * dy);
        if (dist < 22.0 || dist > 145.0) return -1;
        float sectorAngle = 360.0f / AbilityIds.COUNT;
        double angle = Math.toDegrees(Math.atan2(dy, dx)) + 90.0 + sectorAngle / 2.0;
        while (angle < 0.0) angle += 360.0;
        return ((int) (angle / sectorAngle)) % AbilityIds.COUNT;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && hovered >= 0) {
            select(hovered);
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) {
            close();
            return true;
        }
        int num = numberKey(keyCode);
        if (num >= 0) {
            select(num);
            return true;
        }
        return false;
    }

    static int numberKey(int keyCode) {
        if (keyCode == GLFW.GLFW_KEY_0) {
            return AbilityIds.COUNT - 1;
        }
        if (keyCode >= GLFW.GLFW_KEY_1 && keyCode <= GLFW.GLFW_KEY_9) {
            int idx = keyCode - GLFW.GLFW_KEY_1;
            if (idx < AbilityIds.COUNT) return idx;
        }
        return -1;
    }

    public void confirmAndClose() {
        if (hovered >= 0 && ClientPowers.stage >= AbilityIds.MIN_STAGE[hovered]) {
            ClientPowers.selected = hovered;
            ClientPlayNetworking.send(new WheelSelectC2S(hovered));
            ClientPlayerEntity player = client.player;
            if (player != null) {
                player.playSound(ModSounds.WHEEL_TICK, 1.0f, 1.3f);
                player.playSound(ModSounds.WEB_SHOT, 0.6f, 1.5f);
            }
        }
        close();
    }

    private void select(int id) {
        if (ClientPowers.stage < AbilityIds.MIN_STAGE[id]) return;
        ClientPowers.selected = id;
        ClientPlayNetworking.send(new WheelSelectC2S(id));
        ClientPlayerEntity player = client.player;
        if (player != null) {
            player.playSound(ModSounds.WHEEL_TICK, 1.0f, 1.1f);
            player.playSound(ModSounds.WEB_SHOT, 0.5f, 1.4f);
        }
        close();
    }
}
