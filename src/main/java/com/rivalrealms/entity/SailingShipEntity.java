package com.rivalrealms.entity;

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
import net.minecraft.registry.tag.FluidTags;
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
 * Base class for every Rival Realms sailing vessel. It owns everything wooden
 * ships share: buoyancy on water, hull damage with visible smoke, boarding,
 * deck seating, a sailing loop with wake particles and paddle sounds, and a
 * proper shipwreck when the hull finally gives out.
 *
 * <p>Subclasses provide the AI (merchant sailing, pirate hunting, player
 * piloting), loot tables and seat layouts. The geometry itself is built by the
 * client model — hull, fore and aft castles, mast, yard, sail and flag.</p>
 */
public abstract class SailingShipEntity extends Entity {
    private static final TrackedData<Float> HULL_FRACTION = DataTracker.registerData(
            SailingShipEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Boolean> SAILS_SET = DataTracker.registerData(
            SailingShipEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

    protected static final float MAX_HULL = 60.0f;

    private float hull = MAX_HULL;

    protected SailingShipEntity(EntityType<? extends SailingShipEntity> type, World world) {
        super(type, world);
        this.setNoGravity(true);
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        builder.add(HULL_FRACTION, 1.0f);
        builder.add(SAILS_SET, false);
    }

    /** Seat offsets in ship space [sideways, up, forward]; subclasses choose layouts. */
    protected abstract double[][] seats();

    /** Sound played while the ship is under sail. */
    protected void playSailingAmbience(ServerWorld world) {
        if (world.getTime() % 42L == 0) {
            world.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.ENTITY_BOAT_PADDLE_WATER,
                    SoundCategory.NEUTRAL, 0.7f, 0.75f + world.random.nextFloat() * 0.2f);
        }
    }

    // ---------------------------------------------------------------- buoyancy

    /** @return the Y coordinate the waterline wants the hull at, or -1 when beached. */
    protected double waterline() {
        World world = this.getWorld();
        BlockPos base = this.getBlockPos();
        for (int dy = 2; dy >= -3; dy--) {
            BlockPos probe = base.up(dy);
            if (world.getFluidState(probe).isIn(FluidTags.WATER)
                    && world.getFluidState(probe.up()).isEmpty()) {
                return probe.getY() + 0.55;
            }
        }
        return -1.0;
    }

    protected boolean isAfloat() {
        return this.waterline() > 0.0;
    }

    /** Shared physics: buoyancy, drag, gravity on land, then the actual move. */
    protected void physicsTick() {
        World world = this.getWorld();
        Vec3d velocity = this.getVelocity();
        double line = this.waterline();
        if (line > 0.0) {
            double depth = line - this.getY();
            velocity = velocity.add(0.0, MathHelper.clamp(depth * 0.08, -0.06, 0.05), 0.0);
            velocity = new Vec3d(velocity.x * 0.92, velocity.y * 0.85, velocity.z * 0.92);
        } else {
            velocity = new Vec3d(velocity.x * 0.82, Math.max(velocity.y - 0.04, -0.35), velocity.z * 0.82);
        }
        this.setVelocity(velocity);
        this.move(MovementType.SELF, this.getVelocity());

        if (world instanceof ServerWorld serverWorld) {
            if (Math.abs(this.getVelocity().x) + Math.abs(this.getVelocity().z) > 0.045) {
                serverWorld.spawnParticles(ParticleTypes.SPLASH,
                        this.getX() - this.getVelocity().x * 6.0, this.getY() + 0.15, this.getZ() - this.getVelocity().z * 6.0,
                        3, 0.7, 0.05, 0.7, 0.01);
            }
            if (this.hull <= MAX_HULL * 0.34f && world.getTime() % 14L == 0) {
                serverWorld.spawnParticles(ParticleTypes.SMOKE,
                        this.getX(), this.getY() + 1.6, this.getZ(), 3, 0.8, 0.3, 1.6, 0.012);
            }
        }
    }

    /**
     * Server-side sailing helper: accelerates toward {@code target}, eases the
     * bow around to face the motion and pushes the hull with the given speed.
     */
    protected void sailToward(Vec3d target, double speed) {
        double dx = target.x - this.getX();
        double dz = target.z - this.getZ();
        double distance = Math.sqrt(dx * dx + dz * dz);
        if (distance < 1.0e-3) {
            return;
        }
        Vec3d direction = new Vec3d(dx / distance, 0.0, dz / distance);
        this.setVelocity(this.getVelocity().add(direction.multiply(speed)));
        float targetYaw = (float) (Math.atan2(-dx, dz) * 180.0 / Math.PI);
        this.setYaw(MathHelper.lerpAngleDegrees(0.12f, this.getYaw(), targetYaw));
        this.setSails(true);
    }

    protected void setSails(boolean set) {
        if (this.dataTracker.get(SAILS_SET) != set) {
            this.dataTracker.set(SAILS_SET, set);
        }
    }

    protected void idleSails() {
        this.setSails(false);
    }

    public boolean sailsSet() {
        return this.dataTracker.get(SAILS_SET);
    }

    public float hullFraction() {
        return this.hull / MAX_HULL;
    }

    // ---------------------------------------------------------------- boarding

    @Override
    public ActionResult interact(PlayerEntity player, Hand hand) {
        if (player.shouldCancelInteraction()) {
            return ActionResult.PASS;
        }
        if (this.getWorld().isClient) {
            return ActionResult.SUCCESS;
        }
        onPlayerBoard(player);
        return player.startRiding(this) ? ActionResult.CONSUME : ActionResult.PASS;
    }

    /** Hook for subclasses; pirates eject their crew to repel boarders here. */
    protected void onPlayerBoard(PlayerEntity player) {
    }

    /** Seats every listed survivor on this ship in order. */
    public void boardCrew(List<SurvivorEntity> crew) {
        for (SurvivorEntity member : crew) {
            if (member != null && member.isAlive()) {
                member.startRiding(this, true);
            }
        }
    }

    /** Ejects every passenger outward, giving them a chance to swim or fight. */
    protected void ejectCrew(@Nullable LivingEntity cause) {
        for (Entity passenger : List.copyOf(this.getPassengerList())) {
            passenger.stopRiding();
            double angle = this.getWorld().random.nextFloat() * Math.PI * 2.0;
            passenger.setVelocity(Math.cos(angle) * 0.45, 0.45, Math.sin(angle) * 0.45);
            if (passenger instanceof SurvivorEntity survivor && cause instanceof PlayerEntity player) {
                survivor.setTarget(player);
            }
        }
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return this.getPassengerList().size() < seats().length;
    }

    @Override
    protected void updatePassengerPosition(Entity passenger, Entity.PositionUpdater positionUpdater) {
        if (!this.hasPassenger(passenger)) {
            return;
        }
        int index = Math.max(0, this.getPassengerList().indexOf(passenger));
        double[] seat = seats()[index % seats().length];
        double yawRad = Math.toRadians(this.getYaw());
        double cos = Math.cos(yawRad);
        double sin = Math.sin(yawRad);
        double offsetX = seat[0] * cos - seat[2] * sin;
        double offsetZ = seat[0] * sin + seat[2] * cos;
        positionUpdater.accept(passenger,
                this.getX() + offsetX,
                this.getY() + seat[1],
                this.getZ() + offsetZ);
    }

    // ---------------------------------------------------------------- damage

    @Override
    public boolean damage(DamageSource source, float amount) {
        if (this.getWorld().isClient || !this.isAlive()) {
            return false;
        }
        World world = this.getWorld();
        this.hull = Math.max(0.0f, this.hull - amount);
        this.dataTracker.set(HULL_FRACTION, this.hullFraction());
        world.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.BLOCK_WOOD_BREAK,
                SoundCategory.NEUTRAL, 0.9f, 0.6f + world.random.nextFloat() * 0.25f);
        if (world instanceof ServerWorld serverWorld) {
            serverWorld.spawnParticles(ParticleTypes.CRIT,
                    this.getX(), this.getY() + 1.0, this.getZ(), 8, 1.0, 0.5, 1.0, 0.1);
        }
        this.onDamaged(source, amount);
        if (this.hull <= 0.0f) {
            this.shipwreck(source.getAttacker() instanceof LivingEntity attacker ? attacker : null);
        }
        return true;
    }

    /** Subclass hook invoked on every hit (merchants remember raiders, pirates remember boarders). */
    protected void onDamaged(DamageSource source, float amount) {
    }

    /** Breaks the ship apart, scatters loot and ejects the crew. */
    protected void shipwreck(@Nullable LivingEntity cause) {
        World world = this.getWorld();
        if (!(world instanceof ServerWorld serverWorld)) {
            this.discard();
            return;
        }
        world.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.ENTITY_GENERIC_EXPLODE,
                SoundCategory.NEUTRAL, 1.0f, 0.8f);
        serverWorld.spawnParticles(ParticleTypes.EXPLOSION,
                this.getX(), this.getY() + 0.8, this.getZ(), 1, 0.2, 0.2, 0.2, 0.0);
        serverWorld.spawnParticles(ParticleTypes.LARGE_SMOKE,
                this.getX(), this.getY() + 1.0, this.getZ(), 18, 1.4, 0.6, 1.8, 0.03);
        for (ItemStack stack : wreckLoot()) {
            if (!stack.isEmpty()) {
                this.dropStack(stack);
            }
        }
        this.ejectCrew(cause);
        this.discard();
    }

    /** Subclass loot scattered on the water when the hull breaks. */
    protected abstract List<ItemStack> wreckLoot();

    // ---------------------------------------------------------------- misc

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
    public ItemStack getPickBlockStack() {
        return ItemStack.EMPTY;
    }

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
        this.hull = MathHelper.clamp(nbt.getFloat("Hull"), 1.0f, MAX_HULL);
        this.dataTracker.set(HULL_FRACTION, this.hullFraction());
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
        nbt.putFloat("Hull", this.hull);
    }

    /** Convenience for subclasses: a stack of ship plank drops. */
    protected static ItemStack planks(int count) {
        return new ItemStack(Items.SPRUCE_PLANKS, count);
    }
}
