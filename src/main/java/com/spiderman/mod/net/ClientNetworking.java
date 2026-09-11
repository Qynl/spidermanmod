package com.spiderman.mod.net;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.network.ClientPlayerEntity;

import com.spiderman.mod.ModSounds;
import com.spiderman.mod.state.ClientPowers;

/** Client-side S2C receivers feeding the {@link ClientPowers} mirror. */
public final class ClientNetworking {
    private ClientNetworking() {
    }

    public static void register() {
        // Payload types are registered once in ServerNetworking (main entrypoint,
        // which runs on both sides). Registering them here too crashes the
        // client with "already registered", since Fabric runs main, then client.
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
