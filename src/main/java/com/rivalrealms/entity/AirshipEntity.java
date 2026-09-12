package com.rivalrealms.entity;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * A lightweight flying boat. It intentionally keeps vanilla boat controls and
 * rendering, but removes water dependence so a sky captain's vessel is usable
 * above an airship dock as well as over oceans.
 */
public class AirshipEntity extends BoatEntity {
    public AirshipEntity(EntityType<? extends AirshipEntity> type, World world) {
        super(type, world);
        setBoatType(Type.SPRUCE);
        setNoGravity(true);
    }

    @Override
    public void tick() {
        setNoGravity(true);
        super.tick();
        setNoGravity(true);

        if (!getWorld().isClient && !getPassengerList().isEmpty()) {
            Vec3d forward = getRotationVector().normalize().multiply(0.08);
            setVelocity(forward.x, 0.015, forward.z);
        }
    }

    @Override
    protected boolean canAddPassenger(net.minecraft.entity.Entity passenger) {
        return passenger instanceof PlayerEntity && getPassengerList().isEmpty();
    }
}
