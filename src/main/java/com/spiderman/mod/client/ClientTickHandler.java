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
    private static int lastJumpTick = 0;
    private static final int JUMP_COOLDOWN = 12; // Prevent spam

    private ClientTickHandler() {
    }

    public static boolean isWheelDown() {
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc == null || mc.getWindow() == null) return false;
            long handle = mc.getWindow().getHandle();
            try {
                if (Keybinds.wheel.isPressed()) return true;
            } catch (Exception ignored) {}
            try {
                for (int code = 0; code < 512; code++) {
                    if (net.minecraft.client.util.InputUtil.isKeyPressed(handle, code)) {
                        if (Keybinds.wheel.matchesKey(code, 0)) {
                            return true;
                        }
                    }
                }
            } catch (Exception ignored) {
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
            while (Keybinds.wheel.wasPressed()) {}
        } else {
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
            int currentTick = ClientPowers.clientTick;
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
