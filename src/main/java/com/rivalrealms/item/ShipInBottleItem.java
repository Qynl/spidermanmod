package com.rivalrealms.item;

import com.rivalrealms.entity.SailingShipEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * A corked bottle holding a folded Freebooter sloop. Use it on water (or any
 * block) to unfurl a full-size, player-sailable sloop with a puff of sea
 * spray. The bottle is only the shipping container — the ship is real.
 */
public final class ShipInBottleItem extends Item {
    private final EntityType<? extends SailingShipEntity> shipType;

    public ShipInBottleItem(Settings settings, EntityType<? extends SailingShipEntity> shipType) {
        super(settings);
        this.shipType = shipType;
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        World world = context.getWorld();
        if (world.isClient) {
            return ActionResult.SUCCESS;
        }
        BlockPos spawnPos = context.getBlockPos().offset(context.getSide()).up();
        SailingShipEntity ship = shipType.create(world);
        if (ship == null) {
            return ActionResult.FAIL;
        }
        ship.refreshPositionAndAngles(spawnPos.getX() + 0.5, spawnPos.getY() + 0.2,
                spawnPos.getZ() + 0.5, context.getPlayerYaw(), 0.0f);
        if (!world.spawnEntity(ship)) {
            return ActionResult.FAIL;
        }
        world.playSound(null, ship.getX(), ship.getY(), ship.getZ(), SoundEvents.ENTITY_GENERIC_SPLASH,
                SoundCategory.NEUTRAL, 1.0f, 0.9f);
        if (world instanceof net.minecraft.server.world.ServerWorld serverWorld) {
            serverWorld.spawnParticles(net.minecraft.particle.ParticleTypes.SPLASH,
                    ship.getX(), ship.getY() + 0.6, ship.getZ(), 16, 0.7, 0.3, 0.7, 0.05);
        }
        PlayerEntity player = context.getPlayer();
        if (player == null || !player.isCreative()) {
            context.getStack().decrement(1);
        }
        return ActionResult.SUCCESS;
    }
}
