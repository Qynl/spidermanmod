package com.spiderman.mod.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;

import com.spiderman.mod.net.AbilityUseC2S;
import com.spiderman.mod.net.WallJumpC2S;
import com.spiderman.mod.state.AbilityIds;
import com.spiderman.mod.state.ClientPowers;

/** Client tick: key polling, ability requests and the powers-mirror clock. */
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
        while (Keybinds.wheel.wasPressed()) {
            if (ClientPowers.has && client.currentScreen == null) {
                client.setScreen(new WheelScreen());
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
