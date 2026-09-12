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
 * Radial ability wheel — hold G, move mouse, release to equip.
 * Now 9 abilities (double removed). White web lines, thick nodes, real Spider-Man feel.
 */
public class WheelScreen extends Screen {
    private static final String[] DESCRIPTIONS = {
        "Quick web glob — fast white line shot",
        "Momentum swing — long white line, real swinging",
        "Dash to a point — white line zip with launch",
        "Yank a target — hold to pull, white line",
        "Web snare — cluster webs at point + line",
        "Fast traverse — web bridge line you can walk",
        "Nova blast — cool explosion with webs",
        "Heavy ball — powerful impact, webs, knockback",
        "Web floor — standable platform, spiders ignore"
    };

    private static final String[] ICONS = {
        "o", ">", "z", "<>", "#", "<->", "*", "O", "^"
    };

    private int hovered = -1;

    public WheelScreen() {
        super(Text.literal("Web Abilities"));
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
        ctx.fill(0, 0, width, height, 0x66000000);

        int cx = width / 2;
        int cy = height / 2;
        hovered = sectorAt(mouseX - cx, mouseY - cy);

        drawWebBackground(ctx, cx, cy);

        ctx.drawCenteredTextWithShadow(textRenderer,
                Text.literal("§e§lWEB ABILITIES §7(" + AbilityIds.COUNT + ")"), cx, cy - 118, 0xFFFFFF);

        String centerLabel;
        if (hovered >= 0 && ClientPowers.stage >= AbilityIds.MIN_STAGE[hovered]) {
            centerLabel = "§e§l" + AbilityIds.NAMES[hovered].toUpperCase();
        } else if (AbilityIds.valid(ClientPowers.selected)) {
            centerLabel = "§7" + AbilityIds.NAMES[ClientPowers.selected];
        } else {
            centerLabel = "§8...";
        }
        ctx.fill(cx - 36, cy - 10, cx + 36, cy + 10, 0xAA000000);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(centerLabel), cx, cy - 4, 0xFFFFFF);

        int radius = 88;
        float sectorAngle = 360.0f / AbilityIds.COUNT;

        for (int i = 0; i < AbilityIds.COUNT; i++) {
            double angleDeg = -90.0 + i * sectorAngle;
            double angle = Math.toRadians(angleDeg);
            int x = cx + (int) (Math.cos(angle) * radius);
            int y = cy + (int) (Math.sin(angle) * radius);

            boolean locked = ClientPowers.stage < AbilityIds.MIN_STAGE[i];
            boolean isHovered = (i == hovered);
            boolean isSelected = (i == ClientPowers.selected);

            // White web line from center to node — thick when hovered
            drawLine(ctx, cx, cy, x, y, isHovered ? 0xAAFFFF55 : 0x77FFFFFF);

            int bgColor;
            if (locked) bgColor = 0x66000000;
            else if (isHovered) bgColor = 0xAAFFAA00;
            else if (isSelected) bgColor = 0xAA00AA00;
            else bgColor = 0x88000000;

            String icon = i < ICONS.length ? ICONS[i] : "?";
            String name = AbilityIds.NAMES[i];

            int nodeW = textRenderer.getWidth(name) + 20;
            ctx.fill(x - nodeW / 2, y - 11, x + nodeW / 2, y + 11, bgColor);
            // White border for node when hovered
            if (isHovered) {
                ctx.fill(x - nodeW / 2, y - 11, x + nodeW / 2, y - 10, 0xFFFFFFFF);
                ctx.fill(x - nodeW / 2, y + 10, x + nodeW / 2, y + 11, 0xFFFFFFFF);
            }

            String label;
            if (locked) {
                label = "§8" + icon + " " + name + " §7[" + AbilityIds.MIN_STAGE[i] + "]";
            } else if (isHovered) {
                label = "§e§l> " + icon + " " + name + " <";
            } else if (isSelected) {
                label = "§a" + icon + " " + name + " §7✔";
            } else {
                label = "§f" + icon + " " + name;
            }
            ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(label), x, y - 4, 0xFFFFFF);
        }

        if (hovered >= 0) {
            boolean locked = ClientPowers.stage < AbilityIds.MIN_STAGE[hovered];
            String desc = locked ? "§cLocked — stage " + AbilityIds.MIN_STAGE[hovered]
                    : "§7" + DESCRIPTIONS[hovered] + " §8— release G";
            ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(desc), cx, cy + 116, 0xFFFFFF);
        } else {
            ctx.drawCenteredTextWithShadow(textRenderer,
                    Text.literal("§7Hold §eG §7+ move mouse — release to equip — §8Esc cancels — §fWhite lines = webs"),
                    cx, cy + 116, 0xFFFFFF);
        }

        ctx.drawCenteredTextWithShadow(textRenderer,
                Text.literal("§7Stage " + ClientPowers.stage + " §8| §7Mastery " + ClientPowers.mastery + " §8| §fAll webs white lines, platforms standable"),
                cx, cy + 130, 0xFFFFFF);
    }

    private void drawWebBackground(DrawContext ctx, int cx, int cy) {
        int[] rings = {28, 56, 88, 118};
        for (int r : rings) {
            drawCircle(ctx, cx, cy, r, 0x33FFFFFF);
        }
        float sectorAngle = 360.0f / AbilityIds.COUNT;
        for (int i = 0; i < AbilityIds.COUNT; i++) {
            double angle = Math.toRadians(-90.0 + i * sectorAngle + sectorAngle / 2.0);
            int x = cx + (int) (Math.cos(angle) * 130);
            int y = cy + (int) (Math.sin(angle) * 130);
            drawLine(ctx, cx, cy, x, y, 0x22FFFFFF);
        }
    }

    private void drawCircle(DrawContext ctx, int cx, int cy, int radius, int color) {
        for (int i = 0; i < 36; i++) {
            double a1 = Math.toRadians(i * 10.0);
            double a2 = Math.toRadians((i + 1) * 10.0);
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
        for (int i = 0; i <= steps; i++) {
            float t = (float) i / (float) steps;
            int x = (int) (x0 + (x1 - x0) * t);
            int y = (int) (y0 + (y1 - y0) * t);
            ctx.fill(x, y, x + 1, y + 1, color);
        }
    }

    static int sectorAt(int dx, int dy) {
        double dist = Math.sqrt(dx * dx + dy * dy);
        if (dist < 24.0 || dist > 135.0) return -1;
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
        // For 9 abilities: 1->0, 2->1, ..., 9->8, 0->8 (last)
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
            if (player != null) player.playSound(ModSounds.WHEEL_TICK, 0.9f, 1.2f);
        }
        close();
    }

    private void select(int id) {
        if (ClientPowers.stage < AbilityIds.MIN_STAGE[id]) return;
        ClientPowers.selected = id;
        ClientPlayNetworking.send(new WheelSelectC2S(id));
        ClientPlayerEntity player = client.player;
        if (player != null) player.playSound(ModSounds.WHEEL_TICK, 0.9f, 1.0f);
        close();
    }
}
