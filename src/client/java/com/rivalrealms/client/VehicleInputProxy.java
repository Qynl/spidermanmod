package com.rivalrealms.client;

import com.rivalrealms.entity.AirshipEntity;
import com.rivalrealms.entity.SloopEntity;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.input.Input;
import net.minecraft.entity.Entity;

/**
 * Bridges client-only key state into the piloted vehicles. Minecraft 1.21.1
 * has no server-safe input channel for custom vehicles, so the standard
 * boat-style approach applies: the riding client simulates movement from this
 * input and vanilla streams the resulting vehicle position to the server.
 */
public final class VehicleInputProxy {
    private VehicleInputProxy() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            ClientPlayerEntity player = client.player;
            if (player == null) {
                return;
            }
            Entity vehicle = player.getVehicle();
            if (vehicle instanceof AirshipEntity airship) {
                Input input = player.input;
                airship.applyPilotInput(input.pressingForward, input.pressingBack,
                        input.pressingLeft, input.pressingRight, input.jumping, input.sneaking);
            } else if (vehicle instanceof SloopEntity sloop) {
                Input input = player.input;
                sloop.applyPilotInput(input.pressingForward, input.pressingBack,
                        input.pressingLeft, input.pressingRight, false, false);
            }
        });
    }
}
