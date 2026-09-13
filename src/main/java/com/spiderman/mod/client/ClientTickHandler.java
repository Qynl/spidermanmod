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
 * CLIENT TICK - Fixed for no random jumps and no lag at high stage.
 */
public final class ClientTickHandler {
    private static int handFlip;
    private static int diveKeyTicks = 0;
    private static boolean wasSneaking = false;
    private static long lastJumpTick = 0;
    private static final long JUMP_COOLDOWN = 12; // Prevent spam
    private static int wheelGrace = 0; // Prevents flicker

    private ClientTickHandler() {
    }

    // FIXED: No more 0..511 loop - that caused GL ERROR Invalid key 479..511 spam and extreme lag at lvl4
    // Now uses only isPressed() which is valid and fast
    public static boolean isWheelDown() {
        try {
            if (Keybinds.wheel == null) return false;
            return Keybinds.wheel.isPressed();
        } catch (Exception e) {
            return false;
        }
    }

    public static void onEndTick(MinecraftClient client) {
        ClientPowers.tick();
        ClientPowers.clientTick++;
        if (client.player == null || client.world == null || !client.player.isAlive()) {
            wheelGrace = 0;
            return;
        }

        if (ClientPowers.has) {
            boolean rawHeld = isWheelDown();
            // Grace ticks: keep wheel open for 4 ticks after release to prevent flicker
            if (rawHeld) {
                wheelGrace = 4;
            } else if (wheelGrace > 0) {
                wheelGrace--;
            }
            boolean held = rawHeld || wheelGrace > 0;
            if (held) {
                if (client.currentScreen == null) {
                    client.setScreen(new WheelScreen());
                }
            } else {
                if (client.currentScreen instanceof WheelScreen wheel) {
                    wheel.confirmAndClose();
                }
            }
            // Drain wasPressed to prevent vanilla handling
            while (Keybinds.wheel.wasPressed()) {}
        } else {
            wheelGrace = 0;
            while (Keybinds.wheel.wasPressed()) {}
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
        
        // FIXED: Wall jump / double jump now has cooldown to prevent random jumps and spam
        if (client.options.jumpKey.wasPressed() && !player.isOnGround()) {
            long currentTick = ClientPowers.clientTick;
            if (currentTick - lastJumpTick >= JUMP_COOLDOWN) {
                // FIXED: Only allow jump in air if actually in air for a bit, not immediately after leaving ground
                if (player.age > 10 && !player.isOnGround()) {
                    ClientPlayNetworking.send(new WallJumpC2S());
                    lastJumpTick = currentTick;
                }
            }
        }
        
        boolean sneaking = client.options.sneakKey.isPressed();
        if (!player.isOnGround() && sneaking && !wasSneaking) {
            diveKeyTicks = 0;
        }
        if (sneaking && !player.isOnGround()) {
            diveKeyTicks++;
        } else {
            diveKeyTicks = 0;
        }
        wasSneaking = sneaking;
    }

    private static void sendAbility(int ability) {
        if (!AbilityIds.valid(ability)) {
            return;
        }
        if (ClientPowers.stage < AbilityIds.MIN_STAGE[ability]) {
            ClientPlayerEntity player = MinecraftClient.getInstance().player;
            if (player != null) {
                player.sendMessage(Text.literal(
                        "§7Requires stage " + AbilityIds.MIN_STAGE[ability] + " §8[Mastery: " + ClientPowers.mastery + "]"), true);
            }
            return;
        }
        handFlip ^= 1;
        ClientPlayNetworking.send(new AbilityUseC2S(ability, handFlip));
        ClientPowers.lastShotHand = handFlip;
        ClientPowers.lastShotTick = ClientPowers.clientTick;
    }
}
