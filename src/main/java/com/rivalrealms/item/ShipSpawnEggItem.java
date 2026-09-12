package com.rivalrealms.item;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Textured deployable item for the two living-world ship types. */
public final class ShipSpawnEggItem extends Item {
    private final EntityType<? extends BoatEntity> shipType;

    public ShipSpawnEggItem(Settings settings, EntityType<? extends BoatEntity> shipType) {
        super(settings);
        this.shipType = shipType;
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        World world = context.getWorld();
        if (world.isClient) {
            return ActionResult.SUCCESS;
        }
        BlockPos spawnPos = context.getBlockPos().offset(context.getSide());
        BoatEntity ship = shipType.create(world);
        if (ship == null) {
            return ActionResult.FAIL;
        }
        ship.refreshPositionAndAngles(spawnPos.getX() + 0.5, spawnPos.getY(),
                spawnPos.getZ() + 0.5, context.getPlayerYaw(), 0.0f);
        ship.setVariant(BoatEntity.Type.SPRUCE);
        if (!world.spawnEntity(ship)) {
            return ActionResult.FAIL;
        }
        PlayerEntity player = context.getPlayer();
        if (player == null || !player.isCreative()) {
            context.getStack().decrement(1);
        }
        return ActionResult.SUCCESS;
    }
}
