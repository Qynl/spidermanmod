package com.spiderman.mod.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;

import com.spiderman.mod.net.AbilityUseC2S;
import com.spiderman.mod.net.WallJumpC2S;
import com.spiderman.mod.state.AbilityIds;
import com.spiderman.mod.state.ClientPowers;

/**
 * Client tick: key polling, ability requests and the powers-mirror clock.
 * <p>
 * Wheel handling is hold-to-open (fixed flicker):
 * - Hold G => opens wheel if not already open (raw GLFW check, works while screen open)
 * - While holding, mouse moves selects sector (handled in WheelScreen.render)
 * - Release G => equips hovered sector and closes wheel
 * - wasPressed queue is drained every tick to prevent queued reopen causing flicker
 */
public final class ClientTickHandler {
    private static int handFlip;

    private ClientTickHandler() {
    }

    /** Raw physical check if wheel key is still down, even when a Screen is open.
     * Uses InputUtil.isKeyPressed + matchesKey loop so rebinds work and it works while a Screen is open.
     * Avoids getBoundKey() which doesn't exist in 1.21.1 yarn. */
    public static boolean isWheelDown() {
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc == null || mc.getWindow() == null) return false;
            long handle = mc.getWindow().getHandle();
            // Fast path: if KeyBinding reports pressed, trust it
            try {
                if (Keybinds.wheel.isPressed()) return true;
            } catch (Exception ignored) {}
            // Raw check: iterate key codes and see if any pressed key matches the binding
            // This works even when a Screen is open and isPressed() returns false,
            // and supports rebinding.
            try {
                for (int code = 0; code < 512; code++) {
                    if (net.minecraft.client.util.InputUtil.isKeyPressed(handle, code)) {
                        if (Keybinds.wheel.matchesKey(code, 0)) {
                            return true;
                        }
                    }
                }
            } catch (Exception ignored) {
                // Fallback to G key if matchesKey fails
                try {
                    if (net.minecraft.client.util.InputUtil.isKeyPressed(handle, org.lwjgl.glfw.GLFW.GLFW_KEY_G)) {
                        return true;
                    }
                } catch (Exception ignored2) {}
            }
            return false;
        } catch (Exception e) {
            try {
                return Keybinds.wheel.isPressed();
            } catch (Exception e2) {
                return false;
            }
        }
    }

    public static void onEndTick(MinecraftClient client) {
        ClientPowers.tick();
        ClientPowers.clientTick++;
        if (client.player == null || client.world == null || !client.player.isAlive()) {
            return;
        }

        // --- Radial wheel: hold G logic (flicker-free) ---
        if (ClientPowers.has) {
            boolean held = isWheelDown();

            if (held) {
                if (client.currentScreen == null) {
                    client.setScreen(new WheelScreen());
                }
            } else {
                if (client.currentScreen instanceof WheelScreen wheel) {
                    wheel.confirmAndClose();
                }
            }
            // Drain the wasPressed queue every tick – prevents the old queued press
            // from reopening the wheel immediately after release (the flicker bug)
            while (Keybinds.wheel.wasPressed()) {
                // consumed
            }
        } else {
            while (Keybinds.wheel.wasPressed()) {
                // No-op, prevent stuck
            }
        }

        if (!ClientPowers.has) {
            return;
        }
        while (Keybinds.useAbility.wasPressed()) {
            sendAbility(ClientPowers.selected);
        }
        while (Keybinds.quickShot.wasPressed()) {
            sendAbility(AbilityIds.SHOT);
        }
        while (Keybinds.swing.wasPressed()) {
            sendAbility(AbilityIds.SWING);
        }
        while (Keybinds.zip.wasPressed()) {
            sendAbility(AbilityIds.ZIP);
        }
        ClientPlayerEntity player = client.player;
        if (client.options.jumpKey.wasPressed() && !player.isOnGround()) {
            ClientPlayNetworking.send(new WallJumpC2S());
        }
    }

    private static void sendAbility(int ability) {
        if (!AbilityIds.valid(ability)) {
            return;
        }
        if (ClientPowers.stage < AbilityIds.MIN_STAGE[ability]) {
            ClientPlayerEntity player = MinecraftClient.getInstance().player;
            if (player != null) {
                player.sendMessage(Text.literal(
                        "§7Requires stage " + AbilityIds.MIN_STAGE[ability]), true);
            }
            return;
        }
        handFlip ^= 1;
        ClientPlayNetworking.send(new AbilityUseC2S(ability, handFlip));
        ClientPowers.lastShotHand = handFlip;
        ClientPowers.lastShotTick = ClientPowers.clientTick;
    }
}
