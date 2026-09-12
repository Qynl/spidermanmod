package com.rivalrealms.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.rivalrealms.entity.Archetype;
import com.rivalrealms.entity.ModEntities;
import com.rivalrealms.entity.SurvivorEntity;
import com.rivalrealms.world.BuildStyle;
import com.rivalrealms.world.StructureBuilder;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.entity.SpawnReason;

public final class ModCommands {
    private ModCommands() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register(ModCommands::registerCommands);
    }

    private static void registerCommands(com.mojang.brigadier.CommandDispatcher<ServerCommandSource> dispatcher,
                                         CommandRegistryAccess registryAccess,
                                         net.minecraft.server.command.CommandManager.RegistrationEnvironment environment) {
        dispatcher.register(CommandManager.literal("rivalrealms")
                .requires(source -> source.hasPermissionLevel(2))
                .then(CommandManager.literal("spawn")
                        .then(CommandManager.argument("culture", StringArgumentType.word())
                                .suggests((context, builder) -> {
                                    for (Archetype archetype : Archetype.values()) {
                                        builder.suggest(archetype.id());
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(context -> spawn(context.getSource(),
                                        StringArgumentType.getString(context, "culture"), 1))
                                .then(CommandManager.argument("count", IntegerArgumentType.integer(1, 12))
                                        .executes(context -> spawn(context.getSource(),
                                                StringArgumentType.getString(context, "culture"),
                                                IntegerArgumentType.getInteger(context, "count"))))))
                .then(CommandManager.literal("build")
                        .then(CommandManager.argument("style", StringArgumentType.word())
                                .suggests((context, builder) -> {
                                    for (BuildStyle style : BuildStyle.values()) {
                                        builder.suggest(style.id());
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(context -> build(context.getSource(),
                                        StringArgumentType.getString(context, "style")))))
                .then(CommandManager.literal("airship")
                        .executes(context -> spawnAirship(context.getSource())))
                .then(CommandManager.literal("info")
                        .executes(context -> info(context.getSource()))));
    }

    private static int spawn(ServerCommandSource source, String cultureId, int count) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayer();
        ServerWorld world = player.getServerWorld();
        Archetype culture = Archetype.byId(cultureId);
        int spawned = 0;
        for (int i = 0; i < count; i++) {
            SurvivorEntity survivor = ModEntities.SURVIVOR.create(world, SpawnReason.COMMAND);
            if (survivor == null) {
                continue;
            }
            BlockPos pos = player.getBlockPos().add(2 + i % 3, 0, 2 + i / 3);
            survivor.refreshPositionAndAngles(pos, world.random.nextFloat() * 360.0f, 0.0f);
            survivor.setArchetype(culture);
            world.spawnEntity(survivor);
            spawned++;
        }
        int result = spawned;
        source.sendFeedback(() -> Text.literal("Spawned " + result + " " + culture.title() + " survivor(s)."), true);
        return spawned;
    }

    private static int build(ServerCommandSource source, String styleId) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayer();
        BuildStyle style = BuildStyle.fromId(styleId);
        StructureBuilder.build(player.getServerWorld(), player.getBlockPos(), style);
        source.sendFeedback(() -> Text.literal("Built " + style.displayName() + ". Its doors, loot, and defenses are yours to shape."), true);
        return 1;
    }

    private static int spawnAirship(ServerCommandSource source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayer();
        ServerWorld world = player.getServerWorld();
        var airship = ModEntities.AIRSHIP.create(world);
        if (airship == null) {
            return 0;
        }
        airship.refreshPositionAndAngles(player.getX(), player.getY() + 5.0, player.getZ(), player.getYaw(), 0.0f);
        world.spawnEntity(airship);
        player.startRiding(airship);
        source.sendFeedback(() -> Text.literal("Airship launched. Look where you want to travel; the vessel keeps moving while crewed."), true);
        return 1;
    }

    private static int info(ServerCommandSource source) {
        source.sendFeedback(() -> Text.literal("Rival Realms: /rivalrealms spawn <knight|pirate|outlaw|sky_captain> [count], "
                + "/rivalrealms build <knight|pirate|western|sky>, /rivalrealms airship"), false);
        return 1;
    }
}
