package com.spiderman.mod.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;

import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import com.spiderman.mod.ModSounds;
import com.spiderman.mod.server.TransformLogic;
import com.spiderman.mod.net.ServerNetworking;
import com.spiderman.mod.state.PlayerPowers;
import com.spiderman.mod.state.SpiderState;

/**
 * Mod commands: {@code /spm give|reset|stage} for admins (permission level 2)
 * plus {@code /spm stageup} for testing, usable only in creative mode.
 */
public final class SpmCommands {
    private SpmCommands() {
    }

    public static void register() {
        // Registered via CommandRegistrationCallback in SpiderManMod.
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void build(CommandDispatcher<ServerCommandSource> dispatcher) {
        // NOTE: the root carries no requires() so each branch keeps its own
        // gate (brigadier hides children whose requirement fails): the admin
        // branches stay OP-only while stageup is creative-only.
        LiteralArgumentBuilder root = CommandManager.literal("spm");

        LiteralArgumentBuilder give = CommandManager.literal("give");
        give.requires(src -> ((ServerCommandSource) src).hasPermissionLevel(2));
        RequiredArgumentBuilder giveTarget =
                CommandManager.argument("target", EntityArgumentType.player());
        giveTarget.executes(ctx -> {
            ServerPlayerEntity target = EntityArgumentType.getPlayer(ctx, "target");
            boolean fresh = TransformLogic.grantBite(target);
            feedback(ctx.getSource(), fresh ? "Spider powers granted." : "Already powered.");
            return Command.SINGLE_SUCCESS;
        });
        give.then(giveTarget);
        root.then(give);

        LiteralArgumentBuilder reset = CommandManager.literal("reset");
        reset.requires(src -> ((ServerCommandSource) src).hasPermissionLevel(2));
        RequiredArgumentBuilder resetTarget =
                CommandManager.argument("target", EntityArgumentType.player());
        resetTarget.executes(ctx -> {
            ServerPlayerEntity target = EntityArgumentType.getPlayer(ctx, "target");
            TransformLogic.resetPowers(target);
            feedback(ctx.getSource(), "Spider powers removed.");
            return Command.SINGLE_SUCCESS;
        });
        reset.then(resetTarget);
        root.then(reset);

        LiteralArgumentBuilder stage = CommandManager.literal("stage");
        stage.requires(src -> ((ServerCommandSource) src).hasPermissionLevel(2));
        RequiredArgumentBuilder stageTarget =
                CommandManager.argument("target", EntityArgumentType.player());
        RequiredArgumentBuilder stageValue =
                CommandManager.argument("stage", IntegerArgumentType.integer(0, 4));
        stageValue.executes(ctx -> {
            ServerPlayerEntity target = EntityArgumentType.getPlayer(ctx, "target");
            int value = IntegerArgumentType.getInteger(ctx, "stage");
            PlayerPowers powers = SpiderState.get(target.getUuid());
            powers.hasPowers = true;
            powers.stage = value;
            TransformLogic.applyStageAttributes(target, powers);
            ServerNetworking.sendPowers(target);
            TransformLogic.grantAdvancement(target, "stage_" + value);
            feedback(ctx.getSource(), "Stage set to " + value + ".");
            return Command.SINGLE_SUCCESS;
        });
        stageTarget.then(stageValue);
        stage.then(stageTarget);
        root.then(stage);

        LiteralArgumentBuilder stageup = CommandManager.literal("stageup");
        stageup.requires(SpmCommands::isCreativePlayer);
        stageup.executes(ctx -> {
            if (!(ctx.getSource().getEntity() instanceof ServerPlayerEntity player)) {
                return 0;
            }
            PlayerPowers powers = SpiderState.get(player.getUuid());
            if (!powers.hasPowers) {
                TransformLogic.grantBite(player);
            }
            if (powers.stage >= 4) {
                feedback(ctx.getSource(), "Already at max stage (4).");
                return Command.SINGLE_SUCCESS;
            }
            powers.stage++;
            TransformLogic.applyStageAttributes(player, powers);
            player.playSound(ModSounds.STAGE_UP, 1.0f, 1.0f);
            ServerNetworking.sendPowers(player);
            TransformLogic.grantAdvancement(player, "stage_" + powers.stage);
            feedback(ctx.getSource(), "Stage set to " + powers.stage + ".");
            return Command.SINGLE_SUCCESS;
        });
        root.then(stageup);

        dispatcher.register(root);
    }

    private static boolean isCreativePlayer(Object src) {
        return src instanceof ServerCommandSource source
                && source.getEntity() instanceof ServerPlayerEntity player
                && player.isCreative();
    }

    private static void feedback(Object source, String message) {
        if (source instanceof ServerCommandSource src) {
            src.sendFeedback(() -> Text.literal("[spm] " + message), true);
        }
    }
}
