package com.rivalrealms.entity;

import com.rivalrealms.item.ModItems;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A real Rival Realms airship: a hand-built zeppelin with an envelope, fins,
 * engine pods, spinning propellers and an open gondola. Nothing here is a
 * repainted vanilla boat.
 *
 * <h2>Piloting</h2>
 * The riding player is the controlling passenger. Like vanilla boats, the
 * pilot's client simulates the flight and streams position updates to the
 * server, so steering stays responsive on dedicated servers:
 * <ul>
 *   <li>W / S — thrust along the pilot's look direction</li>
 *   <li>A / D — lateral thrust (the hull banks into the turn)</li>
 *   <li>Space / Shift — climb / descend</li>
 *   <li>Right-click — board; sneak while aboard to disembark</li>
 * </ul>
 * An unmanned airship hovers where it was left; it never roams on its own.
 */
public class AirshipEntity extends Entity implements VehicleInput {
    private static final TrackedData<Boolean> POWERED = DataTracker.registerData(
            AirshipEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Integer> ENVELOPE_COLOR = DataTracker.registerData(
            AirshipEntity.class, TrackedDataHandlerRegistry.INTEGER);

    /** Seat offsets, world space: [sideways, up, forward] relative to hull yaw. */
    private static final double[][] SEATS = {
            {0.0, 0.35, 0.0},
            {0.0, 0.35, 0.42},
            {0.0, 0.35, -0.42}
    };

    private static final double THRUST = 0.052;
    private static final double LIFT = 0.062;
    private static final double MAX_SPEED = 0.62;
    private static final float MAX_BANK = 14.0f;
    private static final float HULL_STRENGTH = 28.0f;

    // Pilot input, pushed from the client tick handler every tick while piloting.
    private boolean inForward;
    private boolean inBack;
    private boolean inLeft;
    private boolean inRight;
    private boolean inUp;
    private boolean inDown;

    /** Accumulated hull stress; the envelope tears off when it exceeds HULL_STRENGTH. */
    private float hullStress;
    /** Smoothed bank angle used by the renderer. */
    private float bank;

    public AirshipEntity(EntityType<? extends AirshipEntity> type, World world) {
        super(type, world);
        this.setNoGravity(true);
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        builder.add(POWERED, false);
        builder.add(ENVELOPE_COLOR, 0);
    }

    public boolean isPowered() {
        return this.dataTracker.get(POWERED);
    }

    public int getEnvelopeColor() {
        return this.dataTracker.get(ENVELOPE_COLOR);
    }

    public void setEnvelopeColor(int color) {
        this.dataTracker.set(ENVELOPE_COLOR, Math.floorMod(color, 4));
    }

    /** Renderer-facing bank angle in degrees. */
    public float getBank() {
        return this.bank;
    }

    public float propellerSpin(float tickDelta) {
        float speed = this.isPowered() ? 0.85f : 0.12f;
        return (this.age + tickDelta) * speed;
    }

    @Override
    public void applyPilotInput(boolean forward, boolean back, boolean left, boolean right, boolean jump, boolean sneak) {
        this.inForward = forward;
        this.inBack = back;
        this.inLeft = left;
        this.inRight = right;
        this.inUp = jump;
        this.inDown = sneak;
    }

    @Nullable
    @Override
    public LivingEntity getControllingPassenger() {
        return this.getFirstPassenger() instanceof PlayerEntity player ? player : null;
    }

    @Override
    public boolean isLogicalSideForUpdatingMovement() {
        // Player-piloted: the rider's client owns the flight simulation,
        // exactly like vanilla boats in 1.21.1.
        return this.getControllingPassenger() != null;
    }

    @Override
    public void tick() {
        super.tick();
        boolean piloted = this.getControllingPassenger() != null;
        if (this.getWorld().isClient && piloted) {
            simulateFlight();
        } else if (!this.getWorld().isClient) {
            serverTick(piloted);
        }
    }

    /** Runs only on the pilot's client; results reach the server via vehicle-move packets. */
    private void simulateFlight() {
        LivingEntity pilot = this.getControllingPassenger();
        if (pilot == null) {
            return;
        }

        boolean powered = this.inForward || this.inBack || this.inLeft || this.inRight || this.inUp || this.inDown;
        if (this.dataTracker.get(POWERED) != powered) {
            this.dataTracker.set(POWERED, powered);
        }

        Vec3d look = pilot.getRotationVec(1.0f);
        Vec3d heading = new Vec3d(look.x, 0.0, look.z);
        heading = heading.lengthSquared() > 1.0e-4
                ? heading.normalize()
                : Vec3d.fromPolar(0.0f, this.getYaw());
        Vec3d right = new Vec3d(-heading.z, 0.0, heading.x);

        double forward = (this.inForward ? 1.0 : 0.0) - (this.inBack ? 1.0 : 0.0);
        double strafe = (this.inLeft ? 1.0 : 0.0) - (this.inRight ? 1.0 : 0.0);
        double lift = (this.inUp ? 1.0 : 0.0) - (this.inDown ? 1.0 : 0.0);

        Vec3d velocity = this.getVelocity();
        if (powered) {
            velocity = velocity.add(heading.multiply(forward * THRUST));
            velocity = velocity.add(right.multiply(strafe * THRUST * 0.7));
            velocity = velocity.add(0.0, lift * LIFT, 0.0);
        }

        // Envelope drag, slightly stronger vertically so a released ascent levels off.
        velocity = new Vec3d(velocity.x * 0.94, velocity.y * 0.90, velocity.z * 0.94);
        double horizontal = Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);
        if (horizontal > MAX_SPEED) {
            double scale = MAX_SPEED / horizontal;
            velocity = new Vec3d(velocity.x * scale, velocity.y, velocity.z * scale);
        }
        velocity = new Vec3d(velocity.x, MathHelper.clamp(velocity.y, -0.28, 0.24), velocity.z);

        // Water is buoyant: splash down and the envelope drags the ship back up.
        if (this.isTouchingWater()) {
            velocity = velocity.add(0.0, 0.05, 0.0);
        }

        this.setVelocity(velocity);
        this.move(MovementType.SELF, this.getVelocity());

        // Align the hull with the pilot's heading and bank into the turn.
        this.setYaw(MathHelper.lerpAngleDegrees(0.18f, this.getYaw(), pilot.getYaw()));
        float targetBank = (float) MathHelper.clamp(-strafe * MAX_BANK + horizontal * 8.0, -MAX_BANK, MAX_BANK);
        this.bank += (targetBank - this.bank) * 0.12f;
    }

    private void serverTick(boolean piloted) {
        World world = this.getWorld();
        if (piloted) {
            if (world.getTime() % 26L == 0) {
                world.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.ENTITY_FIREWORK_ROCKET_LAUNCH,
                        SoundCategory.NEUTRAL, 0.22f, 1.15f);
            }
            if (world instanceof ServerWorld serverWorld && world.getTime() % 10L == 0
                    && this.getVelocity().lengthSquared() > 0.004) {
                Vec3d vel = this.getVelocity();
                serverWorld.spawnParticles(ParticleTypes.CLOUD,
                        this.getX() - vel.x * 4.0, this.getY() + 1.1, this.getZ() - vel.z * 4.0,
                        1, 0.05, 0.02, 0.05, 0.004);
            }
        } else if (world.getTime() % 20L == 0) {
            // Moored: engines cold, hover position held.
            if (this.dataTracker.get(POWERED)) {
                this.dataTracker.set(POWERED, false);
            }
            if (this.getY() < world.getBottomY() + 2) {
                this.move(MovementType.SELF, new Vec3d(0.0, 0.05, 0.0));
            }
        }

        if (this.isTouchingWater() && world instanceof ServerWorld serverWorld && world.getTime() % 12L == 0) {
            serverWorld.spawnParticles(ParticleTypes.SPLASH,
                    this.getX(), this.getY() + 0.3, this.getZ(), 6, 0.9, 0.1, 0.9, 0.02);
        }
    }

    @Override
    protected void updatePassengerPosition(Entity passenger, Entity.PositionUpdater positionUpdater) {
        if (!this.hasPassenger(passenger)) {
            return;
        }
        int index = Math.max(0, this.getPassengerList().indexOf(passenger));
        double[] seat = SEATS[index % SEATS.length];
        double yawRad = Math.toRadians(this.getYaw());
        double cos = Math.cos(yawRad);
        double sin = Math.sin(yawRad);
        // yaw convention: forward = (-sin, cos), right = (cos, sin)
        double offsetX = seat[0] * cos - seat[2] * sin;
        double offsetZ = seat[0] * sin + seat[2] * cos;
        positionUpdater.accept(passenger,
                this.getX() + offsetX,
                this.getY() + seat[1],
                this.getZ() + offsetZ);
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return this.getPassengerList().size() < SEATS.length;
    }

    @Override
    public ActionResult interact(PlayerEntity player, Hand hand) {
        if (player.shouldCancelInteraction()) {
            return ActionResult.PASS;
        }
        if (!this.getWorld().isClient) {
            return player.startRiding(this) ? ActionResult.CONSUME : ActionResult.PASS;
        }
        return ActionResult.SUCCESS;
    }

    @Override
    public boolean damage(DamageSource source, float amount) {
        if (this.getWorld().isClient || !this.isAlive()) {
            return false;
        }
        World world = this.getWorld();
        world.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.BLOCK_CHAIN_HIT,
                SoundCategory.NEUTRAL, 0.9f, 0.7f + world.random.nextFloat() * 0.3f);
        if (world instanceof ServerWorld serverWorld) {
            serverWorld.spawnParticles(ParticleTypes.CRIT,
                    this.getX(), this.getY() + 1.4, this.getZ(), 6, 0.7, 0.5, 0.7, 0.08);
        }
        this.hullStress += amount;
        if (this.hullStress >= HULL_STRENGTH) {
            this.destroyAirship();
        }
        return true;
    }

    private void destroyAirship() {
        World world = this.getWorld();
        if (!(world instanceof ServerWorld serverWorld)) {
            this.discard();
            return;
        }
        world.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.ENTITY_GENERIC_EXPLODE,
                SoundCategory.NEUTRAL, 1.2f, 0.9f);
        serverWorld.spawnParticles(ParticleTypes.EXPLOSION,
                this.getX(), this.getY() + 1.2, this.getZ(), 1, 0.1, 0.1, 0.1, 0.0);
        serverWorld.spawnParticles(ParticleTypes.LARGE_SMOKE,
                this.getX(), this.getY() + 1.5, this.getZ(), 24, 1.2, 0.8, 1.2, 0.03);
        for (Entity passenger : List.copyOf(this.getPassengerList())) {
            passenger.stopRiding();
            passenger.setVelocity(passenger.getVelocity().add(0.0, 0.4, 0.0));
        }
        this.dropStack(new ItemStack(ModItems.AIRSHIP_METAL, 2));
        this.dropStack(new ItemStack(Items.IRON_INGOT, 3));
        this.dropStack(new ItemStack(Items.COPPER_INGOT, 2));
        this.discard();
    }

    @Override
    public boolean handleFallDamage(float fallDistance, float damageMultiplier, DamageSource damageSource) {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean canHit() {
        return this.isAlive();
    }

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
        this.setEnvelopeColor(nbt.getInt("EnvelopeColor"));
        this.hullStress = nbt.getFloat("HullStress");
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
        nbt.putInt("EnvelopeColor", this.getEnvelopeColor());
        nbt.putFloat("HullStress", this.hullStress);
    }

    @Override
    public ItemStack getPickBlockStack() {
        return new ItemStack(ModItems.AIRSHIP_KIT);
    }
}
