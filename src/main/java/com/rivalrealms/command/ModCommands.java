package com.rivalrealms.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.rivalrealms.RivalRealms;
import com.rivalrealms.entity.Archetype;
import com.rivalrealms.entity.ModEntities;
import com.rivalrealms.entity.SloopEntity;
import com.rivalrealms.entity.SurvivorEntity;
import com.rivalrealms.world.BuildStyle;
import com.rivalrealms.world.RealmEvents;
import com.rivalrealms.world.ContractEngine;
import com.rivalrealms.world.RealmState;
import com.rivalrealms.world.RealmState.ContractRecord;
import com.rivalrealms.world.SettlementRole;
import com.rivalrealms.world.SettlementVariant;
import com.rivalrealms.world.StructureBuilder;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

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
                .then(CommandManager.literal("landmark")
                        .then(CommandManager.argument("variant", StringArgumentType.word())
                                .suggests((context, builder) -> {
                                    for (SettlementVariant variant : SettlementVariant.values()) {
                                        builder.suggest(variant.name().toLowerCase());
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(context -> landmark(context.getSource(),
                                        StringArgumentType.getString(context, "variant")))))
                .then(CommandManager.literal("claim")
                        .executes(context -> claim(context.getSource())))
                .then(CommandManager.literal("bases")
                        .executes(context -> listBases(context.getSource())))
                .then(CommandManager.literal("locate")
                        .executes(context -> locate(context.getSource())))
                .then(CommandManager.literal("jobs")
                        .executes(context -> jobs(context.getSource())))
                .then(CommandManager.literal("rep")
                        .executes(context -> reputation(context.getSource()))
                        .then(CommandManager.literal("gift")
                                .then(CommandManager.argument("faction", StringArgumentType.word())
                                        .suggests((context, builder) -> {
                                            for (Archetype archetype : Archetype.values()) {
                                                builder.suggest(archetype.faction());
                                            }
                                            return builder.buildFuture();
                                        })
                                        .executes(context -> giftReputation(context.getSource(),
                                                StringArgumentType.getString(context, "faction"))))))
                .then(CommandManager.literal("assign")
                        .then(CommandManager.argument("role", StringArgumentType.word())
                                .suggests((context, builder) -> {
                                    for (SettlementRole role : SettlementRole.values()) {
                                        if (role != SettlementRole.NONE) {
                                            builder.suggest(role.id());
                                        }
                                    }
                                    return builder.buildFuture();
                                })
                                .then(CommandManager.argument("survivor", EntityArgumentType.entity())
                                        .executes(context -> assign(context.getSource(),
                                                StringArgumentType.getString(context, "role"),
                                                EntityArgumentType.getEntity(context, "survivor"))))))
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
                .then(CommandManager.literal("ship")
                        .executes(context -> spawnSloop(context.getSource())))
                .then(CommandManager.literal("convoy")
                        .executes(context -> spawnConvoy(context.getSource())))
                .then(CommandManager.literal("info")
                        .executes(context -> info(context.getSource())))
                .then(CommandManager.literal("contracts")
                        .executes(context -> contracts(context.getSource()))
                        .then(CommandManager.literal("accept")
                                .then(CommandManager.argument("id", StringArgumentType.word())
                                        .executes(context -> acceptContract(context.getSource(),
                                                StringArgumentType.getString(context, "id"))))))
                .then(CommandManager.literal("rebuild")
                        .executes(context -> rebuildSettlement(context.getSource())))
                .then(CommandManager.literal("chronicle")
                        .executes(context -> chronicle(context.getSource())))
                .then(CommandManager.literal("standing")
                        .executes(context -> standing(context.getSource()))));
    }

    /** Lists open work near you and what you already carry. */
    private static int contracts(ServerCommandSource source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayer();
        ServerWorld world = player.getServerWorld();
        RealmState state = RealmState.get(world);
        boolean any = false;
        for (RealmState.BaseRecord base : state.bases()) {
            if (base.abandoned() || !base.contains(player.getBlockPos())) {
                continue;
            }
            ContractEngine.printBoard(world, player, base);
            any = true;
            break;
        }
        for (RealmState.ContractRecord contract : state.contracts()) {
            if ("active".equals(contract.state()) && player.getUuid().equals(contract.taker())) {
                String line = "ACTIVE: " + ContractEngine.describe(state, contract);
                source.sendFeedback(() -> Text.literal(line).formatted(net.minecraft.util.Formatting.AQUA), false);
                any = true;
            }
        }
        if (!any) {
            source.sendFeedback(() -> Text.literal(
                    "No work posted here. Find a Notice Board, or come back when the roads stir."),
                    false);
        }
        return 1;
    }

    private static int acceptContract(ServerCommandSource source, String id) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayer();
        ContractEngine.accept(player.getServerWorld(), player, id);
        return 1;
    }

    /**
     * Tears a settlement down and raises it anew with the current builder:
     * the fix for places generated by older, clumsier drafts of the realm.
     */
    private static int rebuildSettlement(ServerCommandSource source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayer();
        ServerWorld world = player.getServerWorld();
        RealmState state = RealmState.get(world);
        RealmState.BaseRecord target = null;
        double best = 96.0 * 96.0;
        for (RealmState.BaseRecord base : state.bases()) {
            if (base.abandoned()) {
                continue;
            }
            double distance = base.center().getSquaredDistance(player.getBlockPos());
            if (distance < best) {
                best = distance;
                target = base;
            }
        }
        if (target == null) {
            source.sendFeedback(() -> Text.literal(
                    "Stand in or beside the settlement you want rebuilt.").formatted(
                    net.minecraft.util.Formatting.RED), false);
            return 0;
        }
        final RealmState.BaseRecord rebuilt = target;
        final BuildStyle style = BuildStyle.fromId(rebuilt.styleId());
        final SettlementVariant variant = parseVariant(rebuilt.name());
        BlockPos center = rebuilt.center();
        // The old residents step aside; recruited companions stay loyal.
        for (SurvivorEntity resident : com.rivalrealms.world.LivingRealm.population(world, target)) {
            resident.discard();
        }
        StructureBuilder.clearSite(world, center, 34, 24);
        StructureBuilder.buildScattered(world, center, style, variant);
        final int levels = Math.min(rebuilt.level(), 5);
        for (int level = 2; level <= levels; level++) {
            StructureBuilder.expand(world, center, style, level);
        }
        RealmEvents.populateSettlement(world, center, style, variant);
        state.chronicle(world.getTime(), rebuilt.name()
                + " was torn down and raised anew, truer to its founders' craft.", true);
        source.sendFeedback(() -> Text.literal(rebuilt.name() + " rebuilt from the ground up ("
                + style.displayName() + " · " + variant.name().toLowerCase(java.util.Locale.ROOT)
                + ", level " + levels + ").").formatted(net.minecraft.util.Formatting.GOLD), false);
        return 1;
    }

    /** "Knight · fortress" -> FORTRESS; anything odd becomes a farmstead. */
    private static SettlementVariant parseVariant(String name) {
        try {
            String tail = name.substring(name.lastIndexOf('·') + 1).trim();
            return SettlementVariant.valueOf(tail.toUpperCase(java.util.Locale.ROOT).replace(' ', '_'));
        } catch (RuntimeException exception) {
            return SettlementVariant.FARMSTEAD;
        }
    }

    /** The world's memory: the last events of the persistent chronicle. */
    private static int chronicle(ServerCommandSource source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayer();
        ServerWorld world = player.getServerWorld();
        RealmState state = RealmState.get(world);
        var entries = state.chronicle();
        if (entries.isEmpty()) {
            source.sendFeedback(() -> Text.literal("The chronicle is still blank. The realm has not made history yet."), false);
            return 0;
        }
        long today = world.getTime() / 24000L;
        source.sendFeedback(() -> Text.literal("==== THE REALM CHRONICLE - YEAR "
                + (today / 365 + 1) + ", DAY " + (today % 365 + 1) + " ====")
                .formatted(net.minecraft.util.Formatting.GOLD), false);
        int from = Math.max(0, entries.size() - 10);
        for (int i = from; i < entries.size(); i++) {
            RealmState.ChronicleEntry entry = entries.get(i);
            final String line = "Year " + (entry.day() / 365 + 1) + ", Day "
                    + (entry.day() % 365 + 1) + ": " + entry.text();
            source.sendFeedback(() -> Text.literal(line)
                    .formatted(entry.major() ? net.minecraft.util.Formatting.GOLD : net.minecraft.util.Formatting.GRAY), false);
        }
        return entries.size() - from;
    }

    /** Faction wealth and every standing the player holds, global and local. */
    private static int standing(ServerCommandSource source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayer();
        ServerWorld world = player.getServerWorld();
        RealmState state = RealmState.get(world);
        source.sendFeedback(() -> Text.literal("==== FACTIONS ====").formatted(net.minecraft.util.Formatting.GOLD), false);
        for (Archetype culture : Archetype.values()) {
            String faction = culture.faction();
            int rep = state.getReputation(player.getUuid(), faction);
            int wealth = state.wealth(faction);
            final String line = faction + " · wealth " + wealth + "/100 · your standing: "
                    + statusWord(rep) + " (" + rep + ")";
            source.sendFeedback(() -> Text.literal(line)
                    .formatted(rep <= -40 ? net.minecraft.util.Formatting.RED
                            : rep >= 30 ? net.minecraft.util.Formatting.GREEN
                            : net.minecraft.util.Formatting.GRAY), false);
        }
        // Local standing in the settlement you stand in.
        for (RealmState.BaseRecord base : state.bases()) {
            if (base.contains(player.getBlockPos())) {
                int local = base.localReputation(player.getUuid());
                final String line = "Local · " + base.name() + ": " + statusWord(local) + " (" + local + ")";
                source.sendFeedback(() -> Text.literal(line)
                        .formatted(net.minecraft.util.Formatting.AQUA), false);
                break;
            }
        }
        return 1;
    }

    private static String statusWord(int value) {
        if (value >= 60) return "Hero";
        if (value >= 30) return "Trusted";
        if (value >= 15) return "Friendly";
        if (value > -15) return "Neutral";
        if (value > -30) return "Distrusted";
        if (value > -40) return "Unwelcome";
        return "Hunted";
    }

    private static int spawn(ServerCommandSource source, String cultureId, int count) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayer();
        ServerWorld world = player.getServerWorld();
        if (!Archetype.isKnown(cultureId)) {
            source.sendError(Text.literal("Unknown culture. Use knight, pirate, outlaw, or sky_captain."));
            return 0;
        }
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
        if (!BuildStyle.isKnown(styleId)) {
            source.sendError(Text.literal("Unknown style. Use knight, pirate, western, or sky."));
            return 0;
        }
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

    private static int landmark(ServerCommandSource source, String variantId)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayer();
        SettlementVariant variant;
        try {
            variant = SettlementVariant.valueOf(variantId.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            source.sendError(Text.literal("Unknown landmark. Use fortress, citadel, town, royal_city, harbor, shipyard, skyport, airship_yard, outpost, mill, ruin, graveyard, farmstead, temple, hermitage, watchtower, pirate_cove, or anomaly."));
            return 0;
        }
        BuildStyle style = switch (variant) {
            case FORTRESS, CITADEL, ROYAL_CITY -> BuildStyle.KNIGHT;
            case TOWN, OUTPOST -> BuildStyle.WESTERN;
            case HARBOR, SHIPYARD -> BuildStyle.PIRATE;
            case SKYPORT, AIRSHIP_YARD -> BuildStyle.SKY;
            case MILL -> BuildStyle.KNIGHT;
            case RUIN, GRAVEYARD, FARMSTEAD, TEMPLE, HERMITAGE, ANOMALY -> BuildStyle.CUSTOM;
            case WATCHTOWER -> BuildStyle.KNIGHT;
            case PIRATE_COVE -> BuildStyle.PIRATE;
        };
        ServerWorld world = player.getServerWorld();
        BlockPos center = player.getBlockPos();
        RealmState realms = RealmState.get(world);
        if (!realms.canClaimBase(center)) {
            source.sendError(Text.literal("This landmark would overlap an existing settlement."));
            return 0;
        }
        try {
            StructureBuilder.buildScattered(world, center, style, variant);
            RealmState.BaseRecord base = realms.claimBase(center, player.getUuid(), style);
            if (base == null) {
                source.sendError(Text.literal("The landmark was built, but its claim could not be saved."));
                return 0;
            }
            source.sendFeedback(() -> Text.literal("Built and claimed a " + variant.name().toLowerCase()
                    + " settlement. Use /rivalrealms jobs to inspect its workforce."), true);
            return 1;
        } catch (RuntimeException exception) {
            RivalRealms.LOGGER.error("Landmark command failed at {}", center, exception);
            source.sendError(Text.literal("The landmark could not be built safely; check the server log."));
            return 0;
        }
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

    private static int locate(ServerCommandSource source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayer();
        RealmState.BaseRecord nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (RealmState.BaseRecord base : RealmState.get(player.getServerWorld()).bases()) {
            double distance = base.center().getSquaredDistance(player.getBlockPos());
            if (distance < nearestDistance) {
                nearest = base;
                nearestDistance = distance;
            }
        }
        if (nearest == null) {
            source.sendError(Text.literal("No persistent landmarks have been discovered yet. Explore new overworld chunks or use /rivalrealms landmark town."));
            return 0;
        }
        RealmState.BaseRecord located = nearest;
        int blocks = (int) Math.sqrt(nearestDistance);
        source.sendFeedback(() -> Text.literal("Nearest settlement: " + located.name() + " · "
                + located.center().toShortString() + " · " + blocks + " blocks away"), false);
        return 1;
    }

    private static int jobs(ServerCommandSource source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayer();
        ServerWorld world = player.getServerWorld();
        RealmState state = RealmState.get(world);
        RealmState.BaseRecord base = state.findBase(player.getBlockPos());
        if (base == null) {
            source.sendError(Text.literal("You must stand inside a claimed settlement to inspect its workforce."));
            return 0;
        }
        int[] counts = new int[SettlementRole.values().length];
        for (net.minecraft.entity.Entity entity : world.getOtherEntities(null,
                new net.minecraft.util.math.Box(base.center()).expand(base.radius()),
                candidate -> candidate instanceof SurvivorEntity worker
                        && (worker.isBaseGuard() || worker.isSettlementWorker())
                        && base.faction().equalsIgnoreCase(worker.effectiveFaction()))) {
            SurvivorEntity worker = (SurvivorEntity) entity;
            counts[worker.settlementRole().ordinal()]++;
        }
        source.sendFeedback(() -> Text.literal(base.name() + " · level " + base.level()
                + " · food " + base.food() + " · materials " + base.materials()
                + " · construction " + base.workProgress()), false);
        for (SettlementRole role : SettlementRole.values()) {
            int count = counts[role.ordinal()];
            if (count > 0) {
                source.sendFeedback(() -> Text.literal(role.displayName() + ": " + count), false);
            }
        }
        return 1;
    }

    private static int assign(ServerCommandSource source, String roleId, net.minecraft.entity.Entity entity)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayer();
        if (!(entity instanceof SurvivorEntity survivor)) {
            source.sendError(Text.literal("The target must be a survivor."));
            return 0;
        }
        SettlementRole role = SettlementRole.byId(roleId);
        if (role == SettlementRole.NONE) {
            source.sendError(Text.literal("Unknown job. Use guard, builder, farmer, trader, blacksmith, or scout."));
            return 0;
        }
        RealmState state = RealmState.get(player.getServerWorld());
        RealmState.BaseRecord base = state.findBase(survivor.getBlockPos());
        if (base == null || !base.owner().equals(player.getUuid())) {
            source.sendError(Text.literal("You can only assign survivors inside a settlement you own."));
            return 0;
        }
        if (role == SettlementRole.GUARD) {
            survivor.assignGuard(base.center(), player.getUuid(), base.faction());
        } else {
            survivor.assignWorker(base.center(), player.getUuid(), base.faction(), role);
        }
        source.sendFeedback(() -> Text.literal(survivor.getName().getString() + " is now a " + role.displayName() + "."), true);
        return 1;
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
        airship.setEnvelopeColor(world.random.nextInt(4));
        world.spawnEntity(airship);
        player.startRiding(airship);
        source.sendFeedback(() -> Text.literal("Airship launched. W/S thrust, A/D strafe, Space/Shift altitude; "
                + "the hull banks as you turn."), true);
        return 1;
    }

    private static int spawnSloop(ServerCommandSource source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayer();
        ServerWorld world = player.getServerWorld();
        BlockPos water = RealmEvents.findWater(world, player.getBlockPos(), 48);
        SloopEntity sloop = ModEntities.SLOOP.create(world);
        if (sloop == null) {
            return 0;
        }
        if (water != null) {
            sloop.refreshPositionAndAngles(water.getX() + 0.5, water.getY() + 0.6, water.getZ() + 0.5,
                    player.getYaw(), 0.0f);
        } else {
            sloop.refreshPositionAndAngles(player.getX(), player.getY() + 1.0, player.getZ(), player.getYaw(), 0.0f);
        }
        world.spawnEntity(sloop);
        boolean onWater = water != null;
        source.sendFeedback(() -> Text.literal(onWater
                ? "Sloop launched on the water. Right-click to board; W/S to sail, A/D to steer."
                : "Sloop launched on land. Carry it to water before sailing."), true);
        return 1;
    }

    private static int spawnConvoy(ServerCommandSource source)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayer();
        int ships = RealmEvents.spawnShowcaseConvoy(player.getServerWorld(), player.getBlockPos());
        if (ships == 0) {
            source.sendError(Text.literal("No loaded water was found nearby. Stand near an ocean, river, or harbour and try again."));
            return 0;
        }
        source.sendFeedback(() -> Text.literal("Spawned a jewelry merchant convoy with "
                + (ships > 1 ? "a pirate attack force." : "its merchant crew.")), true);
        return ships;
    }

    private static int info(ServerCommandSource source) {
        source.sendFeedback(() -> Text.literal("Rival Realms: /rivalrealms spawn <culture> [count], /rivalrealms build <style>, "
                + "/rivalrealms landmark <fortress|citadel|town|royal_city|harbor|shipyard|skyport|airship_yard|outpost>, /rivalrealms claim, "
                + "/rivalrealms bases, /rivalrealms locate, /rivalrealms jobs, /rivalrealms assign <role> <survivor>, "
                + "/rivalrealms diplomacy <a> <b> <value>, /rivalrealms airship, /rivalrealms ship, /rivalrealms convoy"), false);
        return 1;
    }

    private static final String[] FACTIONS = {"Crownlands", "Freebooters", "Dustwalkers", "Skybound"};

    private static int reputation(ServerCommandSource source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayer();
        com.rivalrealms.world.RealmState realms = com.rivalrealms.world.RealmState.get(player.getServerWorld());
        player.sendMessage(Text.literal("== Your standing in the realms ==").formatted(net.minecraft.util.Formatting.GOLD), false);
        for (String faction : FACTIONS) {
            int rep = realms.getReputation(player.getUuid(), faction);
            net.minecraft.util.Formatting color = rep <= -40 ? net.minecraft.util.Formatting.RED
                    : rep < 0 ? net.minecraft.util.Formatting.GRAY
                    : rep >= 40 ? net.minecraft.util.Formatting.GREEN
                    : net.minecraft.util.Formatting.WHITE;
            String tier = rep <= -40 ? "Hunted" : rep < 0 ? "Disliked"
                    : rep >= 40 ? "Trusted" : rep > 0 ? "Warming" : "Unknown";
            player.sendMessage(Text.literal("  " + faction + ": " + rep + " (" + tier + ")").formatted(color), false);
        }
        player.sendMessage(Text.literal("Hold royal coins and use /rivalrealms rep gift <faction> to mend ties."), true);
        return 1;
    }

    private static int giftReputation(ServerCommandSource source, String factionId)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayer();
        Archetype match = null;
        for (Archetype archetype : Archetype.values()) {
            if (archetype.faction().equalsIgnoreCase(factionId)) {
                match = archetype;
                break;
            }
        }
        if (match == null) {
            source.sendError(Text.literal("Unknown faction. Try Crownlands, Freebooters, Dustwalkers or Skybound."));
            return 0;
        }
        int coins = 0;
        for (ItemStack stack : player.getInventory().main) {
            if (stack.isOf(com.rivalrealms.item.ModItems.ROYAL_COIN)) {
                coins += stack.getCount();
            }
        }
        if (coins < 4) {
            source.sendError(Text.literal("A peace offering costs 4 royal coins."));
            return 0;
        }
        int left = 4;
        for (int i = 0; i < player.getInventory().main.size() && left > 0; i++) {
            ItemStack stack = player.getInventory().main.get(i);
            if (stack.isOf(com.rivalrealms.item.ModItems.ROYAL_COIN)) {
                int take = Math.min(left, stack.getCount());
                stack.decrement(take);
                left -= take;
            }
        }
        com.rivalrealms.world.RealmState realms = com.rivalrealms.world.RealmState.get(player.getServerWorld());
        realms.adjustReputation(player.getUuid(), match.faction(), 8);
        player.sendMessage(Text.literal("The " + match.faction() + " accept your gift. Reputation now "
                + realms.getReputation(player.getUuid(), match.faction()) + ".").formatted(net.minecraft.util.Formatting.GREEN), false);
        com.rivalrealms.sound.ModSounds.playVoice(player.getServerWorld(), player.getBlockPos(), "haggle");
        player.getServerWorld().playSound(null, player.getBlockPos(), net.minecraft.sound.SoundEvents.ENTITY_VILLAGER_YES,
                net.minecraft.sound.SoundCategory.NEUTRAL, 0.9f, 1.1f);
        return 1;
    }

}
