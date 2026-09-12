package com.rivalrealms.item;

import com.rivalrealms.entity.AirshipEntity;
import com.rivalrealms.entity.ModEntities;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** A creative-friendly deployable airship egg; airships are vehicles, not mobs. */
public final class AirshipSpawnEggItem extends Item {
    public AirshipSpawnEggItem(Settings settings) {
        super(settings);
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        World world = context.getWorld();
        if (world.isClient) {
            return ActionResult.SUCCESS;
        }
        BlockPos spawnPos = context.getBlockPos().offset(context.getSide());
        AirshipEntity airship = ModEntities.AIRSHIP.create(world);
        if (airship == null) {
            return ActionResult.FAIL;
        }
        airship.refreshPositionAndAngles(spawnPos.getX() + 0.5, spawnPos.getY(),
                spawnPos.getZ() + 0.5, context.getPlayerYaw(), 0.0f);
        if (!world.spawnEntity(airship)) {
            return ActionResult.FAIL;
        }
        PlayerEntity player = context.getPlayer();
        if (player == null || !player.isCreative()) {
            context.getStack().decrement(1);
        }
        return ActionResult.SUCCESS;
    }
}
