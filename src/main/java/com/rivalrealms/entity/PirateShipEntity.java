package com.rivalrealms.entity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.UUID;

/** A pirate raider that sails toward a jewelry trader and plunders its cargo. */
public final class PirateShipEntity extends BoatEntity {
    private UUID targetUuid;
    private boolean plundered;

    public PirateShipEntity(EntityType<? extends PirateShipEntity> type, World world) {
        super(type, world);
        setVariant(Type.SPRUCE);
        setPersistent();
        setCustomName(Text.literal("Freebooter Raider"));
        setCustomNameVisible(true);
    }

    public void setTargetShip(MerchantShipEntity target) {
        targetUuid = target.getUuid();
    }

    public UUID targetUuid() {
        return targetUuid;
    }

    @Override
    public void tick() {
        super.tick();
        if (getWorld().isClient || targetUuid == null || plundered) {
            return;
        }
        Entity entity = getWorld().getEntity(targetUuid);
        if (!(entity instanceof MerchantShipEntity merchant) || !merchant.isAlive()) {
            targetUuid = null;
            return;
        }

        double dx = merchant.getX() - getX();
        double dz = merchant.getZ() - getZ();
        double distance = Math.sqrt(dx * dx + dz * dz);
        if (distance > 7.0) {
            Vec3d direction = new Vec3d(dx, 0.0, dz).normalize().multiply(0.075);
            setVelocity(direction.x, getVelocity().y, direction.z);
            setYaw((float) (Math.atan2(-dx, dz) * 180.0 / Math.PI));
        } else if (getWorld() instanceof ServerWorld serverWorld) {
            merchant.raidCargo(serverWorld);
            plundered = true;
            setCustomName(Text.literal("Freebooter Raider · PLUNDERED CARGO"));
            setCustomNameVisible(true);
            setVelocity(-dx * 0.01, getVelocity().y, -dz * 0.01);
        }
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        if (targetUuid != null) {
            nbt.putUuid("TargetShip", targetUuid);
        }
        nbt.putBoolean("Plundered", plundered);
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        targetUuid = nbt.containsUuid("TargetShip") ? nbt.getUuid("TargetShip") : null;
        plundered = nbt.getBoolean("Plundered");
    }
}
