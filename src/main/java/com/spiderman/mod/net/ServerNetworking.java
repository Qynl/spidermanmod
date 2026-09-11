package com.spiderman.mod.net;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;

import com.spiderman.mod.server.AbilityExecutor;
import com.spiderman.mod.server.ClimbLogic;
import com.spiderman.mod.state.AbilityIds;
import com.spiderman.mod.state.PlayerPowers;
import com.spiderman.mod.state.SpiderState;

/** Server-side payload registration and send helpers. */
public final class ServerNetworking {
    private ServerNetworking() {
    }

    public static void register() {
        PayloadTypeRegistry.playC2S().register(AbilityUseC2S.ID, AbilityUseC2S.CODEC);
        PayloadTypeRegistry.playC2S().register(WheelSelectC2S.ID, WheelSelectC2S.CODEC);
        PayloadTypeRegistry.playC2S().register(WallJumpC2S.ID, WallJumpC2S.CODEC);
        PayloadTypeRegistry.playS2C().register(PowersSyncS2C.ID, PowersSyncS2C.CODEC);
        PayloadTypeRegistry.playS2C().register(SwingStateS2C.ID, SwingStateS2C.CODEC);
        PayloadTypeRegistry.playS2C().register(SensePingS2C.ID, SensePingS2C.CODEC);
        PayloadTypeRegistry.playS2C().register(ComboS2C.ID, ComboS2C.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(AbilityUseC2S.ID, (payload, context) ->
                context.server().execute(() -> {
                    ServerPlayerEntity player = context.player();
                    if (player != null) {
                        AbilityExecutor.use(player, payload.ability(), payload.hand());
                    }
                }));
        ServerPlayNetworking.registerGlobalReceiver(WallJumpC2S.ID, (payload, context) ->
                context.server().execute(() -> {
                    ServerPlayerEntity player = context.player();
                    if (player != null) {
                        ClimbLogic.tryAirAction(player, SpiderState.get(player.getUuid()));
                    }
                }));
        ServerPlayNetworking.registerGlobalReceiver(WheelSelectC2S.ID, (payload, context) ->
                context.server().execute(() -> {
                    ServerPlayerEntity player = context.player();
                    if (player == null || !AbilityIds.valid(payload.ability())) {
                        return;
                    }
                    SpiderState.get(player.getUuid()).selected = payload.ability();
                    sendPowers(player);
                }));
    }

    public static void sendPowers(ServerPlayerEntity player) {
        PlayerPowers powers = SpiderState.get(player.getUuid());
        ServerPlayNetworking.send(player,
                new PowersSyncS2C(powers.hasPowers, powers.stage, powers.mastery, powers.selected));
    }

    public static void sendSwing(ServerPlayerEntity player, boolean active,
            double x, double y, double z, int hand, int life) {
        ServerPlayNetworking.send(player, new SwingStateS2C(active, x, y, z, hand, life));
    }

    public static void sendSwingOff(ServerPlayerEntity player) {
        ServerPlayNetworking.send(player, new SwingStateS2C(false, 0, 0, 0, 0, 0));
    }

    public static void sendSense(ServerPlayerEntity player, double x, double y, double z, int kind) {
        ServerPlayNetworking.send(player, new SensePingS2C(x, y, z, kind));
    }

    public static void sendCombo(ServerPlayerEntity player, int combo) {
        ServerPlayNetworking.send(player, new ComboS2C(combo));
    }
}
