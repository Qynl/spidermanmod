package com.spiderman.mod.net;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.network.ClientPlayerEntity;

import com.spiderman.mod.ModSounds;
import com.spiderman.mod.state.ClientPowers;

/** Client-side S2C receivers feeding the {@link ClientPowers} mirror. */
public final class ClientNetworking {
    private ClientNetworking() {
    }

    public static void register() {
        PayloadTypeRegistry.playC2S().register(AbilityUseC2S.ID, AbilityUseC2S.CODEC);
        PayloadTypeRegistry.playC2S().register(WheelSelectC2S.ID, WheelSelectC2S.CODEC);
        PayloadTypeRegistry.playC2S().register(WallJumpC2S.ID, WallJumpC2S.CODEC);
        PayloadTypeRegistry.playS2C().register(PowersSyncS2C.ID, PowersSyncS2C.CODEC);
        PayloadTypeRegistry.playS2C().register(SwingStateS2C.ID, SwingStateS2C.CODEC);
        PayloadTypeRegistry.playS2C().register(SensePingS2C.ID, SensePingS2C.CODEC);
        PayloadTypeRegistry.playS2C().register(ComboS2C.ID, ComboS2C.CODEC);

        ClientPlayNetworking.registerGlobalReceiver(PowersSyncS2C.ID, (payload, context) ->
                context.client().execute(() -> {
                    boolean had = ClientPowers.has;
                    ClientPowers.has = payload.has();
                    ClientPowers.stage = payload.stage();
                    ClientPowers.mastery = payload.mastery();
                    ClientPowers.selected = payload.selected();
                    if (payload.has() && !had) {
                        ClientPowers.cinematicTicks = 200;
                    }
                }));
        ClientPlayNetworking.registerGlobalReceiver(SwingStateS2C.ID, (payload, context) ->
                context.client().execute(() -> {
                    ClientPowers.swingActive = payload.active();
                    ClientPowers.swingX = payload.x();
                    ClientPowers.swingY = payload.y();
                    ClientPowers.swingZ = payload.z();
                    ClientPowers.swingHand = payload.hand();
                    ClientPowers.swingLife = payload.life();
                }));
        ClientPlayNetworking.registerGlobalReceiver(SensePingS2C.ID, (payload, context) ->
                context.client().execute(() -> {
                    boolean fresh = ClientPowers.pings.isEmpty();
                    ClientPowers.addPing(payload.x(), payload.y(), payload.z(), payload.kind());
                    ClientPlayerEntity player = context.player();
                    if (player != null && fresh) {
                        player.playSound(ModSounds.SENSE_TINGLE, 0.8f, 1.2f);
                    }
                }));
        ClientPlayNetworking.registerGlobalReceiver(ComboS2C.ID, (payload, context) ->
                context.client().execute(() ->
                        ClientPowers.combo = payload.combo()));
    }
}
