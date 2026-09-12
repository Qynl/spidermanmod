package com.rivalrealms.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.rivalrealms.entity.Archetype;
import com.rivalrealms.entity.ModEntities;
import com.rivalrealms.entity.SurvivorEntity;
import com.rivalrealms.world.BuildStyle;
import com.rivalrealms.world.RealmState;
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
                                        if (style != BuildStyle.CUSTOM) {
                                            builder.suggest(style.id());
                                        }
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(context -> build(context.getSource(),
                                        StringArgumentType.getString(context, "style")))))
                .then(CommandManager.literal("claim")
                        .executes(context -> claim(context.getSource())))
                .then(CommandManager.literal("bases")
                        .executes(context -> listBases(context.getSource())))
                .then(CommandManager.literal("diplomacy")
                        .then(CommandManager.argument("first", StringArgumentType.word())
                                .then(CommandManager.argument("second", StringArgumentType.word())
                                        .then(CommandManager.argument("value", IntegerArgumentType.integer(-100, 100))
                                                .executes(context -> setDiplomacy(context.getSource(),
                                                        StringArgumentType.getString(context, "first"),
                                                        StringArgumentType.getString(context, "second"),
                                                        IntegerArgumentType.getInteger(context, "value")))))))
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
            SurvivorEntity survivor = ModEntities.SURVIVOR.create(world);
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
        ServerWorld world = player.getServerWorld();
        StructureBuilder.build(world, player.getBlockPos(), style);
        BlockPos center = player.getBlockPos().add(4, 0, 4);
        RealmState.BaseRecord base = RealmState.get(world).claimBase(center, player.getUuid(), style);
        if (base == null) {
            source.sendFeedback(() -> Text.literal("Built the structure, but it overlaps an existing settlement claim."), true);
        } else {
            source.sendFeedback(() -> Text.literal("Built and claimed " + base.name()
                    + ". Hostile factions can now organize raids against it."), true);
        }
        return 1;
    }

    private static int claim(ServerCommandSource source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayer();
        RealmState.BaseRecord base = RealmState.get(player.getServerWorld()).claimCustomBase(
                player.getBlockPos(), player.getUuid(), "Independent", player.getName().getString() + "'s Settlement");
        if (base == null) {
            source.sendError(Text.literal("This area is too close to another settlement."));
            return 0;
        }
        source.sendFeedback(() -> Text.literal("Claimed " + base.name() + ". Place a Realm Banner here to make the claim visible."), true);
        return 1;
    }

    private static int listBases(ServerCommandSource source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayer();
        var bases = RealmState.get(player.getServerWorld()).bases();
        if (bases.isEmpty()) {
            source.sendFeedback(() -> Text.literal("No settlements are claimed in this dimension."), false);
            return 0;
        }
        for (RealmState.BaseRecord base : bases) {
            source.sendFeedback(() -> Text.literal(base.name() + " · " + base.faction()
                    + " · center " + base.center().toShortString() + " · level " + base.level()), false);
        }
        return bases.size();
    }

    private static int setDiplomacy(ServerCommandSource source, String first, String second, int value) {
        RealmState realms = RealmState.get(source.getWorld());
        realms.setRelation(first, second, value);
        source.sendFeedback(() -> Text.literal("Diplomacy changed: " + first + " ↔ " + second + " = " + value), true);
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
        source.sendFeedback(() -> Text.literal("Rival Realms: /rivalrealms spawn <culture> [count], /rivalrealms build <style>, "
                + "/rivalrealms claim, /rivalrealms bases, /rivalrealms diplomacy <a> <b> <value>, /rivalrealms airship"), false);
        return 1;
    }
}
