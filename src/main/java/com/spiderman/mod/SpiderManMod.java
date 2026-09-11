package com.spiderman.mod;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

import net.minecraft.server.network.ServerPlayerEntity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.spiderman.mod.command.SpmCommands;
import com.spiderman.mod.config.SpiderConfig;
import com.spiderman.mod.net.ServerNetworking;
import com.spiderman.mod.server.JoinHandler;
import com.spiderman.mod.server.ServerTickHandler;
import com.spiderman.mod.server.TransformLogic;
import com.spiderman.mod.server.WebCleanup;
import com.spiderman.mod.state.PlayerPowers;
import com.spiderman.mod.state.SpiderState;

/**
 * Server/common entrypoint for the Spider-Man mod.
 *
 * <p>Owns: registries, payload types, commands, server lifecycle hooks and the
 * authoritative per-player powers state ({@link SpiderState}).
 */
public class SpiderManMod implements ModInitializer {
    public static final String MOD_ID = "spiderman";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("[spiderman] initializing (common)");
        SpiderConfig.load();
        ModEntities.register();
        ModItems.register();
        ModSounds.register();
        ServerNetworking.register();
        SpmCommands.register();

        CommandRegistrationCallback.EVENT.register((dispatcher, access, env) ->
                SpmCommands.build(dispatcher));

        ServerTickEvents.END_SERVER_TICK.register(ServerTickHandler::onEndTick);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity player = handler.player;
            if (player != null) {
                JoinHandler.onJoin(player);
            }
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                JoinHandler.onDisconnect(handler));
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            PlayerPowers powers = SpiderState.get(newPlayer.getUuid());
            powers.resetTransient();
            TransformLogic.applyStageAttributes(newPlayer, powers);
            ServerNetworking.sendPowers(newPlayer);
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            SpiderState.onServerStopped(server);
            WebCleanup.clearAll();
        });
        LOGGER.info("[spiderman] common init complete");
    }
}
