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
 * Wheel handling is hold-to-open:
 * - Hold G => opens wheel if not already open
 * - While holding, mouse moves selects sector (handled in WheelScreen.render)
 * - Release G => equips hovered sector and closes wheel
 * This matches real Spider-Man games.
 */
public final class ClientTickHandler {
    private static int handFlip;

    private ClientTickHandler() {
    }

    public static void onEndTick(MinecraftClient client) {
        ClientPowers.tick();
        ClientPowers.clientTick++;
        if (client.player == null || client.world == null || !client.player.isAlive()) {
            return;
        }

        // --- Radial wheel: hold G logic ---
        if (ClientPowers.has) {
            boolean wheelHeld = false;
            try {
                wheelHeld = Keybinds.wheel.isPressed();
            } catch (Exception ignored) {
                // Fallback: if isPressed not available, use wasPressed logic
                wheelHeld = false;
            }

            if (wheelHeld) {
                if (client.currentScreen == null) {
                    client.setScreen(new WheelScreen());
                }
                // If screen is already WheelScreen, keep it open — hovered is updated in render
            } else {
                // G released: if wheel is open, confirm selection and close
                if (client.currentScreen instanceof WheelScreen wheel) {
                    wheel.confirmAndClose();
                } else {
                    // Fallback for old press-to-open behavior: also handle wasPressed for edge cases
                    while (Keybinds.wheel.wasPressed()) {
                        if (client.currentScreen == null) {
                            client.setScreen(new WheelScreen());
                        }
                    }
                }
            }
        } else {
            // No powers: still consume wasPressed to prevent stuck
            while (Keybinds.wheel.wasPressed()) {
                // No-op
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
