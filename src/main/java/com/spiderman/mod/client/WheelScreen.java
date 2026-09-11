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
 * Radial ability wheel (G). Pauses singleplayer while open (focus moment),
 * mouse or number keys pick one of the 10 web abilities.
 */
public class WheelScreen extends Screen {
    private static final String[] DESCRIPTIONS = {
        "Quick web glob", "Momentum swing", "Dash to a point", "Yank a target",
        "Web snare", "Fast traverse", "Nova blast", "Heavy ball",
        "Web floor", "Twin shot"
    };

    private int hovered = -1;

    public WheelScreen() {
        super(Text.literal("Web Abilities"));
    }

    @Override
    public boolean shouldPause() {
        return true;
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, width, height, 0x88000000);
        int cx = width / 2;
        int cy = height / 2;
        hovered = sectorAt(mouseX - cx, mouseY - cy);

        ctx.drawCenteredTextWithShadow(textRenderer,
                Text.literal("§e§lWEB ABILITIES"), cx, cy - 108, 0xFFFFFF);

        int radius = 72;
        for (int i = 0; i < AbilityIds.COUNT; i++) {
            double angle = Math.toRadians(-90.0 + i * 36.0);
            int x = cx + (int) (Math.cos(angle) * radius);
            int y = cy + (int) (Math.sin(angle) * radius);
            boolean locked = ClientPowers.stage < AbilityIds.MIN_STAGE[i];
            String label;
            if (locked) {
                label = "§8" + AbilityIds.NAMES[i] + " §7St" + AbilityIds.MIN_STAGE[i];
            } else if (i == hovered) {
                label = "§e§l> " + AbilityIds.NAMES[i] + " <";
            } else if (i == ClientPowers.selected) {
                label = "§a" + AbilityIds.NAMES[i];
            } else {
                label = "§f" + AbilityIds.NAMES[i];
            }
            ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(label), x, y, 0xFFFFFF);
        }

        if (hovered >= 0) {
            boolean locked = ClientPowers.stage < AbilityIds.MIN_STAGE[hovered];
            ctx.drawCenteredTextWithShadow(textRenderer,
                    Text.literal(locked ? "§cUnlocks at stage " + AbilityIds.MIN_STAGE[hovered]
                            : "§7" + DESCRIPTIONS[hovered]),
                    cx, cy + 104, 0xFFFFFF);
        } else {
            ctx.drawCenteredTextWithShadow(textRenderer,
                    Text.literal("§7click or press 1-0 to select - G/Esc closes"),
                    cx, cy + 104, 0xFFFFFF);
        }
    }

    // Package-visible for unit tests (WheelMathTest).
    static int sectorAt(int dx, int dy) {
        double dist = Math.sqrt(dx * dx + dy * dy);
        if (dist < 24.0 || dist > 130.0) {
            return -1;
        }
        double angle = Math.toDegrees(Math.atan2(dy, dx)) + 90.0 + 18.0;
        while (angle < 0.0) {
            angle += 360.0;
        }
        return ((int) (angle / 36.0)) % AbilityIds.COUNT;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && hovered >= 0) {
            select(hovered);
            return true;
        }
        // Screen does not implement mouseClicked itself; Element's default
        // (which a childless screen inherits) just returns false.
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Closes on Esc or the (rebindable) wheel key itself.
        if (keyCode == 256 || Keybinds.wheel.matchesKey(keyCode, scanCode)) {
            close();
            return true;
        }
        int num = numberKey(keyCode);
        if (num >= 0) {
            select(num);
            return true;
        }
        // See mouseClicked: Element's default just returns false.
        return false;
    }

    // Package-visible for unit tests (WheelMathTest).
    static int numberKey(int keyCode) {
        if (keyCode == GLFW.GLFW_KEY_0) {
            return 9;
        }
        if (keyCode >= GLFW.GLFW_KEY_1 && keyCode <= GLFW.GLFW_KEY_9) {
            return keyCode - GLFW.GLFW_KEY_1;
        }
        return -1;
    }

    private void select(int id) {
        if (ClientPowers.stage < AbilityIds.MIN_STAGE[id]) {
            return;
        }
        ClientPowers.selected = id;
        ClientPlayNetworking.send(new WheelSelectC2S(id));
        ClientPlayerEntity player = client.player;
        if (player != null) {
            player.playSound(ModSounds.WHEEL_TICK, 0.9f, 1.0f);
        }
        close();
    }
}
