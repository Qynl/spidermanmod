package com.rivalrealms.entity;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * A small Freebooter sloop the player actually sails. Same steering feel as
 * the airship (the pilot's client simulates movement and streams it to the
 * server), but bound to the waterline: forward thrust only bites when the
 * hull floats, the bow swings with A/D, and the sail fills visually with
 * speed. Beach it and it grinds to a halt.
 */
public final class SloopEntity extends SailingShipEntity implements VehicleInput {
    private static final double[][] SEATS = {
            {0.0, 0.65, -0.75},
            {0.0, 0.65, 0.85}
    };

    private static final double THRUST = 0.042;
    private static final double MAX_WATER_SPEED = 0.36;
    private static final float TURN_RATE = 2.6f;

    private boolean inForward;
    private boolean inBack;
    private boolean inLeft;
    private boolean inRight;

    public SloopEntity(EntityType<? extends SloopEntity> type, World world) {
        super(type, world);
    }

    @Override
    protected double[][] seats() {
        return SEATS;
    }

    @Nullable
    @Override
    public LivingEntity getControllingPassenger() {
        return this.getFirstPassenger() instanceof PlayerEntity player ? player : null;
    }

    @Override
    public boolean isLogicalSideForUpdatingMovement() {
        return this.getControllingPassenger() != null;
    }

    @Override
    public void applyPilotInput(boolean forward, boolean back, boolean left, boolean right, boolean jump, boolean sneak) {
        this.inForward = forward;
        this.inBack = back;
        this.inLeft = left;
        this.inRight = right;
    }

    @Override
    public void tick() {
        super.tick();
        boolean piloted = this.getControllingPassenger() != null;
        if (this.getWorld().isClient) {
            if (piloted) {
                this.simulateSailing();
            }
            return;
        }
        // A piloted sloop is moved by the rider's client through vanilla
        // vehicle packets; the server only floats empty ships.
        if (!piloted) {
            this.idleSails();
            this.physicsTick();
        }
    }

    /** Runs on the pilot's client only. */
    private void simulateSailing() {
        boolean afloat = this.isAfloat();
        double drag = afloat ? 0.93 : 0.72;
        double thrust = afloat ? THRUST : THRUST * 0.15;

        double forward = (this.inForward ? 1.0 : 0.0) - (this.inBack ? 0.6 : 0.0);
        if (this.inLeft || this.inRight) {
            float turn = (this.inLeft ? 1.0f : 0.0f) - (this.inRight ? 1.0f : 0.0f);
            this.setYaw(this.getYaw() + turn * TURN_RATE * (afloat ? 1.0f : 0.3f));
        }

        Vec3d velocity = this.getVelocity();
        if (forward != 0.0) {
            double yawRad = Math.toRadians(this.getYaw());
            Vec3d heading = new Vec3d(-Math.sin(yawRad), 0.0, Math.cos(yawRad));
            velocity = velocity.add(heading.multiply(forward * thrust));
        }
        velocity = new Vec3d(velocity.x * drag, velocity.y, velocity.z * drag);
        if (afloat) {
            double horizontal = Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);
            if (horizontal > MAX_WATER_SPEED) {
                double scale = MAX_WATER_SPEED / horizontal;
                velocity = new Vec3d(velocity.x * scale, velocity.y, velocity.z * scale);
            }
        }
        this.setVelocity(velocity);
        this.move(MovementType.SELF, this.getVelocity());
        this.setSails(forward != 0.0);
    }

    @Override
    protected List<ItemStack> wreckLoot() {
        List<ItemStack> loot = new ArrayList<>();
        loot.add(planks(2));
        loot.add(new ItemStack(Items.STICK, 2));
        if (this.getWorld().random.nextFloat() < 0.25f) {
            loot.add(new ItemStack(Items.IRON_NUGGET, 2));
        }
        return loot;
    }

}
