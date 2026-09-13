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

    /** Raw physical check if wheel key is still down, even when a Screen is open. */
    public static boolean isWheelDown() {
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc == null || mc.getWindow() == null) return false;
            // Use bound key code, so rebinding works
            var bound = Keybinds.wheel.getBoundKey();
            if (bound != null && bound.getCategory() == net.minecraft.client.util.InputUtil.Type.KEYSYM) {
                int code = bound.getCode();
                // -1 means unbound
                if (code < 0) return false;
                return net.minecraft.client.util.InputUtil.isKeyPressed(mc.getWindow().getHandle(), code);
            }
            // Fallback to KeyBinding's own isPressed
            return Keybinds.wheel.isPressed();
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
