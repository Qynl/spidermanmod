package com.spiderman.mod.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import com.spiderman.mod.net.AbilityUseC2S;
import com.spiderman.mod.net.WallJumpC2S;
import com.spiderman.mod.state.AbilityIds;
import com.spiderman.mod.state.ClientPowers;

/**
 * CLIENT TICK - Complete remake, no lag, no flicker, no random jumps.
 */
public final class ClientTickHandler {
    private static int handFlip;
    private static boolean wasSneaking = false;
    private static long lastJumpTick = 0;
    private static final long JUMP_COOLDOWN = 14;
    private static int wheelGrace = 0;
    private static boolean wheelWasOpen = false;

    private ClientTickHandler() {}

    // Ultimate wheel detection: works even when screen open, no invalid key spam
    public static boolean isWheelDown() {
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc == null || mc.getWindow() == null) return false;
            if (Keybinds.wheel == null) return false;
            long handle = mc.getWindow().getHandle();
            
            // Method 1: Direct GLFW check for G (most reliable, works when screen open)
            try {
                if (GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_G) == GLFW.GLFW_PRESS) {
                    return true;
                }
            } catch (Exception ignored) {}
            
            // Method 2: Check bound key via InputUtil (supports rebinding)
            try {
                String transKey = Keybinds.wheel.getBoundKeyTranslationKey();
                InputUtil.Key boundKey = InputUtil.fromTranslationKey(transKey);
                if (boundKey != null && boundKey.getCategory() == InputUtil.Type.KEYSYM) {
                    int code = boundKey.getCode();
                    if (code >= 0 && code < 350) {
                        if (InputUtil.isKeyPressed(handle, code)) {
                            return true;
                        }
                        // Also try GLFW direct for that code
                        if (GLFW.glfwGetKey(handle, code) == GLFW.GLFW_PRESS) {
                            return true;
                        }
                    }
                }
            } catch (Exception ignored) {}
            
            // Method 3: Fallback to isPressed
            try {
                if (Keybinds.wheel.isPressed()) return true;
            } catch (Exception ignored) {}
            
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    public static void onEndTick(MinecraftClient client) {
        ClientPowers.tick();
        ClientPowers.clientTick++;
        if (client.player == null || client.world == null || !client.player.isAlive()) {
            wheelGrace = 0;
            wheelWasOpen = false;
            return;
        }

        // WHEEL - Hold G, no flicker
        if (ClientPowers.has) {
            boolean rawHeld = isWheelDown();
            
            if (rawHeld) {
                wheelGrace = 8; // Even longer grace
                if (client.currentScreen == null) {
                    client.setScreen(new WheelScreen());
                    wheelWasOpen = true;
                }
            } else {
                if (wheelGrace > 0) wheelGrace--;
                if (wheelGrace <= 0 && wheelWasOpen) {
                    if (client.currentScreen instanceof WheelScreen wheel) {
                        wheel.confirmAndClose();
                        wheelWasOpen = false;
                    }
                }
            }
            while (Keybinds.wheel.wasPressed()) {}
        } else {
            wheelGrace = 0;
            wheelWasOpen = false;
            while (Keybinds.wheel.wasPressed()) {}
        }

        if (!ClientPowers.has) return;
        
        boolean canUseAbilities = client.currentScreen == null || client.currentScreen instanceof WheelScreen;
        if (canUseAbilities) {
            while (Keybinds.useAbility.wasPressed()) sendAbility(ClientPowers.selected);
            while (Keybinds.quickShot.wasPressed()) sendAbility(AbilityIds.SHOT);
            while (Keybinds.swing.wasPressed()) sendAbility(AbilityIds.SWING);
            while (Keybinds.zip.wasPressed()) sendAbility(AbilityIds.ZIP);
        }
        
        ClientPlayerEntity player = client.player;
        
        if (client.options.jumpKey.wasPressed() && !player.isOnGround() && canUseAbilities) {
            long currentTick = ClientPowers.clientTick;
            if (currentTick - lastJumpTick >= JUMP_COOLDOWN) {
                if (player.age > 10 && player.getVelocity().y < 0.3) {
                    ClientPlayNetworking.send(new WallJumpC2S());
                    lastJumpTick = currentTick;
                }
            }
        }
        
        wasSneaking = client.options.sneakKey.isPressed();
    }

    private static void sendAbility(int ability) {
        if (!AbilityIds.valid(ability)) return;
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
