package com.rivalrealms.block;

import com.rivalrealms.world.RealmState;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.entity.ShapeContext;

/** A visible, interactable claim marker for a player settlement. */
public final class RealmBannerBlock extends Block {
    private static final VoxelShape POLE = Block.createCuboidShape(7, 0, 7, 9, 16, 9);
    private static final VoxelShape FLAG = Block.createCuboidShape(8, 8, 8, 16, 15, 9);
    private static final VoxelShape OUTLINE = VoxelShapes.union(POLE, FLAG);

    public RealmBannerBlock(AbstractBlock.Settings settings) {
        super(settings);
    }

    @Override
    public VoxelShape getOutlineShape(net.minecraft.block.BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return OUTLINE;
    }

    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (!(world instanceof ServerWorld serverWorld)) {
            return ActionResult.SUCCESS;
        }

        RealmState realms = RealmState.get(serverWorld);
        RealmState.BaseRecord base = realms.findBase(pos);
        if (base == null) {
            base = realms.claimCustomBase(pos, player.getUuid(), "Independent", player.getName().getString() + "'s Settlement");
            if (base != null) {
                player.sendMessage(Text.literal("Settlement claimed: " + base.name()
                        + ". Guards and raids will now use this banner as a base."), false);
            } else {
                player.sendMessage(Text.literal("This banner is too close to another settlement."), true);
            }
        } else if (base.owner().equals(player.getUuid())) {
            player.sendMessage(Text.literal(base.name() + " · level " + base.level()
                    + " · faction: " + base.faction() + " · radius: " + base.radius()), true);
        } else {
            player.sendMessage(Text.literal("This settlement belongs to another ruler: " + base.name()), true);
        }
        return ActionResult.SUCCESS;
    }
}
