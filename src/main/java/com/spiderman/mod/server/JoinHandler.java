package com.spiderman.mod.server;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import com.spiderman.mod.net.ServerNetworking;
import com.spiderman.mod.state.PlayerPowers;
import com.spiderman.mod.state.SpiderState;

/** Syncs powers on join, persists on disconnect. */
public final class JoinHandler {
    private JoinHandler() {
    }

    public static void onJoin(ServerPlayerEntity player) {
        MinecraftServer server = player.getServer();
        if (server != null) {
            SpiderState.ensureLoaded(server);
        }
        PlayerPowers powers = SpiderState.get(player.getUuid());
        powers.resetTransient();
        TransformLogic.applyStageAttributes(player, powers);
        ServerNetworking.sendPowers(player);
        if (!powers.hasPowers) {
            player.sendMessage(Text.literal(
                    "§7[Spider-Man] Rumor has it a §cradioactive spider§7 lurks in the dark..."), false);
        }
    }

    public static void onDisconnect(ServerPlayNetworkHandler handler) {
        ServerPlayerEntity player = handler.player;
        if (player == null) {
            return;
        }
        PlayerPowers powers = SpiderState.get(player.getUuid());
        powers.stopSwing();
        powers.zipTicks = 0;
        MinecraftServer server = player.getServer();
        if (server != null) {
            SpiderState.save(server);
        }
        SpiderState.drop(player.getUuid());
    }
}
