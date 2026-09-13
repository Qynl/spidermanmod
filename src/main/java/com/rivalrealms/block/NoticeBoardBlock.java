package com.rivalrealms.block;

import com.mojang.serialization.MapCodec;
import com.rivalrealms.world.ContractEngine;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * The settlement's notice board: bounties, deliveries, gather orders and
 * scout work, posted by whoever runs the place. Read it to see the work;
 * hand goods over at it to finish gather orders and deliveries.
 */
public class NoticeBoardBlock extends Block {
    public NoticeBoardBlock(Settings settings) {
        super(settings);
    }

    @Override
    public MapCodec<? extends Block> getCodec() {
        return createCodec(NoticeBoardBlock::new);
    }

    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos,
                                 PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (world instanceof ServerWorld serverWorld) {
            ContractEngine.tryTurnIn(serverWorld, (net.minecraft.server.network.ServerPlayerEntity) player,
                    pos, player.getStackInHand(hand));
        }
        return ActionResult.SUCCESS;
    }
}
