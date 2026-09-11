package com.spiderman.mod.server;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import com.spiderman.mod.state.PlayerPowers;
import com.spiderman.mod.state.SpiderState;

/** Per-tick server driver: persistence, cleanup and per-player power ticks. */
public final class ServerTickHandler {
    private static int tickCount;

    private ServerTickHandler() {
    }

    public static void onEndTick(MinecraftServer server) {
        SpiderState.ensureLoaded(server);
        tickCount++;
        if (tickCount % 6000 == 0) {
            SpiderState.save(server);
        }
        WebCleanup.tick();
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            tickPlayer(player);
        }
    }

    private static void tickPlayer(ServerPlayerEntity player) {
        PlayerPowers powers = SpiderState.get(player.getUuid());
        if (!powers.hasPowers) {
            return;
        }
        ComboTracker.tick(player, powers);
        if (powers.swinging) {
            SwingPhysics.tick(player, powers);
        }
        if (powers.zipTicks > 0) {
            SwingPhysics.tickZip(player, powers);
        }
        ClimbLogic.tick(player, powers);
        if (player.isOnGround()) {
            powers.doubleJumpUsed = false;
            powers.focusTicks = 0;
        }
        if (player.age % 10 == 0) {
            SenseLogic.tick(player, powers);
        }
        if (powers.swinging && player.age % 20 == 0) {
            MasteryLogic.addMastery(player, 2);
        }
    }
}
