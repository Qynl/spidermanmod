package com.rivalrealms;

import com.rivalrealms.block.ModBlocks;
import com.rivalrealms.command.ModCommands;
import com.rivalrealms.entity.ModEntities;
import com.rivalrealms.item.ModItems;
import com.rivalrealms.world.RealmEvents;
import com.rivalrealms.world.WorldSettlementGenerator;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class RivalRealms implements ModInitializer {
    public static final String MOD_ID = "rivalrealms";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private RivalRealms() {
    }

    @Override
    public void onInitialize() {
        ModBlocks.register();
        ModItems.register();
        ModEntities.register();
        ModCommands.register();
        ServerLifecycleEvents.SERVER_STARTED.register(RealmEvents::onServerStarted);
        ServerChunkEvents.CHUNK_LOAD.register(WorldSettlementGenerator::onChunkLoad);
        ServerTickEvents.END_SERVER_TICK.register(RealmEvents::onServerTick);
        LOGGER.info("Rival Realms is ready: survivors, settlements, betrayals and frontiers await.");
    }

    public static Identifier id(String path) {
        return Identifier.of(MOD_ID, path);
    }
}
