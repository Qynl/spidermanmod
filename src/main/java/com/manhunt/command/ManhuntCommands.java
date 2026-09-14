package com.manhunt.command;

import com.manhunt.Manhunt;
import com.manhunt.entity.HunterEntity;
import com.manhunt.world.ManhuntState;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * {@code /manhunt spawn|start|stop|status} - the whole game in four verbs.
 */
public final class ManhuntCommands {

    private ManhuntCommands() {
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("manhunt")
                .then(CommandManager.literal("spawn")
                        .requires(source -> source.hasPermissionLevel(2))
                        .executes(ManhuntCommands::spawn))
                .then(CommandManager.literal("start")
                        .requires(source -> source.hasPermissionLevel(2))
                        .executes(ManhuntCommands::start))
                .then(CommandManager.literal("stop")
                        .requires(source -> source.hasPermissionLevel(2))
                        .executes(ManhuntCommands::stop))
                .then(CommandManager.literal("status")
                        .executes(ManhuntCommands::status)));
    }

    private static int spawn(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) {
            source.sendError(Text.translatable("manhunt.needs_player"));
            return 0;
        }
        if (Manhunt.currentHunter(source.getServer()) != null) {
            source.sendFeedback(() -> Text.translatable("manhunt.spawn.already")
                    .formatted(Formatting.GRAY), false);
            return 0;
        }
        ServerWorld world = player.getServerWorld();
        HunterEntity hunter = Manhunt.summon(world, player.getBlockPos().add(12, 0, 12));
        if (hunter == null) {
            source.sendError(Text.translatable("manhunt.spawn.failed"));
            return 0;
        }
        source.sendFeedback(() -> Text.translatable("manhunt.spawn.ok")
                .formatted(Formatting.DARK_RED), true);
        return Command.SINGLE_SUCCESS;
    }

    private static int start(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        ServerWorld world = source.getWorld();
        ManhuntState state = ManhuntState.get(world.getServer().getOverworld());
        state.started = true;
        state.markDirty();
        HunterEntity hunter = Manhunt.currentHunter(source.getServer());
        if (hunter == null) {
            ServerPlayerEntity player = source.getPlayer();
            if (player != null) {
                hunter = Manhunt.summon(player.getServerWorld(),
                        player.getBlockPos().add(16, 0, 16));
            }
        }
        HunterEntity finalHunter = hunter;
        source.sendFeedback(() -> Text.translatable("manhunt.start",
                        finalHunter == null ? Text.translatable("manhunt.the_hunter")
                                : finalHunter.getDisplayName())
                .formatted(Formatting.DARK_RED), true);
        return Command.SINGLE_SUCCESS;
    }

    private static int stop(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        ManhuntState state = ManhuntState.get(source.getServer().getOverworld());
        state.started = false;
        state.markDirty();
        source.sendFeedback(() -> Text.translatable("manhunt.stop")
                .formatted(Formatting.GREEN), true);
        return Command.SINGLE_SUCCESS;
    }

    private static int status(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        ManhuntState state = ManhuntState.get(source.getServer().getOverworld());
        HunterEntity hunter = Manhunt.currentHunter(source.getServer());
        if (hunter == null) {
            source.sendFeedback(() -> Text.translatable("manhunt.status.absent",
                    state.started ? Text.translatable("manhunt.status.hunting")
                            : Text.translatable("manhunt.status.living"))
                    .formatted(Formatting.GRAY), false);
            return 0;
        }
        ServerPlayerEntity player = source.getPlayer();
        long blocks = player == null ? -1
                : (long) Math.sqrt(player.squaredDistanceTo(hunter));
        source.sendFeedback(() -> Text.translatable("manhunt.status",
                        hunter.getDisplayName(),
                        Text.translatable("manhunt.phase." + hunter.phase().name().toLowerCase()),
                        blocks,
                        hunter.getWorld().getRegistryKey().getValue().getPath())
                .formatted(Formatting.GOLD), false);
        return Command.SINGLE_SUCCESS;
    }
}
