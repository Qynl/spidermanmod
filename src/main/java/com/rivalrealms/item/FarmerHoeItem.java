package com.rivalrealms.item;

import net.minecraft.block.Blocks;
import net.minecraft.item.HoeItem;
import net.minecraft.item.ToolMaterials;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * The farmhand's workhorse. Tills like an iron hoe, but a right-click swings
 * a whole patch: every grass or dirt block around the target that has open
 * sky above it gets turned in one stroke — real farmer efficiency, not
 * one-block-at-a-time villager fussing.
 */
public class FarmerHoeItem extends HoeItem {
    public FarmerHoeItem(Settings settings) {
        super(ToolMaterials.IRON, 1.0f, -2.0f, settings);
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        ActionResult result = super.useOnBlock(context);
        World world = context.getWorld();
        if (result.isAccepted() && !world.isClient) {
            BlockPos center = context.getBlockPos();
            int tilled = 0;
            for (BlockPos pos : BlockPos.iterate(center.add(-1, 0, -1), center.add(1, 0, 1))) {
                if (pos.equals(center)) {
                    continue;
                }
                if (canTill(world, pos)) {
                    world.setBlockState(pos, Blocks.FARMLAND.getDefaultState());
                    world.playSound(null, pos.getX(), pos.getY(), pos.getZ(), SoundEvents.ITEM_HOE_TILL,
                            SoundCategory.BLOCKS, 0.9f, 0.9f + world.random.nextFloat() * 0.15f);
                    tilled++;
                }
            }
            if (tilled > 0 && context.getPlayer() != null && !context.getPlayer().isCreative()) {
                context.getStack().damage(1, context.getPlayer(), p -> p.sendToolBreakStatus(context.getHand()));
            }
        }
        return result;
    }

    private static boolean canTill(World world, BlockPos pos) {
        return (world.getBlockState(pos).isOf(Blocks.GRASS_BLOCK)
                || world.getBlockState(pos).isOf(Blocks.DIRT))
                && world.getBlockState(pos.up()).isAir();
    }
}
