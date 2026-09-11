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

import com.spiderman.mod.server.TransformLogic;
import com.spiderman.mod.net.ServerNetworking;
import com.spiderman.mod.state.PlayerPowers;
import com.spiderman.mod.state.SpiderState;

/**
 * Admin commands: {@code /spm give|reset|stage}. Everything else in the mod
 * is earned through play.
 */
public final class SpmCommands {
    private SpmCommands() {
    }

    public static void register() {
        // Registered via CommandRegistrationCallback in SpiderManMod.
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void build(CommandDispatcher<ServerCommandSource> dispatcher) {
        LiteralArgumentBuilder root = CommandManager.literal("spm");
        root.requires(src -> ((ServerCommandSource) src).hasPermissionLevel(2));

        LiteralArgumentBuilder give = CommandManager.literal("give");
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

        dispatcher.register(root);
    }

    private static void feedback(Object source, String message) {
        if (source instanceof ServerCommandSource src) {
            src.sendFeedback(() -> Text.literal("[spm] " + message), true);
        }
    }
}
