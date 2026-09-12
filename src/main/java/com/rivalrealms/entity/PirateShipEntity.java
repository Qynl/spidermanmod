package com.rivalrealms.entity;

import com.rivalrealms.item.ModItems;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A Freebooter raider: black sails, a real broadside cannon and a hungry crew.
 * It hunts jewelry traders across the waves, fires arcing cannonballs that
 * smash hulls for real, then closes in to plunder the cargo by hand. Board it
 * and the crew jumps over the rails to repel you.
 */
public class PirateShipEntity extends SailingShipEntity {
    private static final double[][] SEATS = {
            {0.0, 1.15, -1.35},
            {0.7, 0.80, -0.60},
            {-0.7, 0.80, -0.60},
            {0.7, 0.80, 0.40},
            {-0.7, 0.80, 0.40},
            {0.0, 1.15, 1.45}
    };

    private static final double CHASE_RANGE = 72.0;

    @Nullable
    private UUID targetUuid;
    private boolean plundered;
    protected int cannonCooldown = 60;
    private Vec3d patrolTarget;
    private long nextPatrolCheck;

    public PirateShipEntity(EntityType<? extends PirateShipEntity> type, World world) {
        super(type, world);
        setCustomName(Text.literal("Freebooter Raider"));
        setCustomNameVisible(true);
    }

    public void setTargetShip(MerchantShipEntity target) {
        this.targetUuid = target.getUuid();
    }

    @Nullable
    public UUID targetUuid() {
        return this.targetUuid;
    }

    public boolean hasPlundered() {
        return this.plundered;
    }

    @Override
    protected double[][] seats() {
        return SEATS;
    }

    @Override
    public void tick() {
        super.tick();
        World world = this.getWorld();
        if (world.isClient || !(world instanceof ServerWorld serverWorld)) {
            return;
        }

        if (this.cannonCooldown > 0) {
            this.cannonCooldown--;
        }

        SailingShipEntity prey = this.resolvePrey(serverWorld);
        if (prey != null) {
            if (prey instanceof MerchantShipEntity merchant) {
                hunt(serverWorld, merchant);
            } else {
                huntShip(serverWorld, prey);
            }
        } else if (!this.plundered) {
            // Scanning every tick would burn server time on empty oceans;
            // a five-second sweep is plenty for a 72-block horizon.
            if (world.getTime() % 100L == 0L) {
                this.scanForPrey(serverWorld);
            }
        }

        if (prey != null || this.plundered) {
            this.physicsTick();
            this.playSailingAmbience(serverWorld);
        } else {
            patrolTick(serverWorld);
            this.physicsTick();
            this.playSailingAmbience(serverWorld);
        }
    }

    /** Raiders never drift: with no prey they sail a slow hunting patrol. */
    private void patrolTick(ServerWorld world) {
        long now = world.getTime();
        boolean reached = this.patrolTarget != null
                && this.patrolTarget.squaredDistanceTo(this.getX(), this.getY(), this.getZ()) < 36.0;
        if (this.patrolTarget == null || reached || now >= this.nextPatrolCheck || !this.isAfloat()) {
            double angle = world.random.nextFloat() * Math.PI * 2.0;
            double reach = 40.0 + world.random.nextFloat() * 55.0;
            this.patrolTarget = new Vec3d(this.getX() + Math.cos(angle) * reach, this.getY(),
                    this.getZ() + Math.sin(angle) * reach);
            this.nextPatrolCheck = now + 1600L;
        }
        this.sailToward(this.patrolTarget, this.huntSpeed() * 0.7);
    }

    /** Speed used while running down prey; the flagship is heavier and slower. */
    protected double huntSpeed() {
        return 0.028;
    }

    protected double cannonRange() {
        return 16.0;
    }

    protected int cannonCooldownTicks() {
        return 70;
    }

    @Nullable
    private SailingShipEntity resolvePrey(ServerWorld world) {
        if (this.targetUuid == null) {
            return null;
        }
        if (world.getEntity(this.targetUuid) instanceof MerchantShipEntity merchant && merchant.isAlive()) {
            return merchant;
        }
        if (world.getEntity(this.targetUuid) instanceof SloopEntity sloop && sloop.isAlive()
                && !sloop.getPassengerList().isEmpty()) {
            return sloop;
        }
        this.targetUuid = null;
        return null;
    }

    private void scanForPrey(ServerWorld world) {
        // Merchants first: that is the classic prize.
        List<MerchantShipEntity> prey = new ArrayList<>();
        for (Entity candidate : world.getOtherEntities(this, this.getBoundingBox().expand(CHASE_RANGE),
                entity -> entity instanceof MerchantShipEntity merchant
                        && merchant.isAlive() && merchant.cargoCrates() > 0)) {
            prey.add((MerchantShipEntity) candidate);
        }
        if (!prey.isEmpty()) {
            MerchantShipEntity target = prey.get(world.random.nextInt(prey.size()));
            this.targetUuid = target.getUuid();
            target.markUnderAttack();
            return;
        }
        // Player crews are fair game too: an occupied sloop on the horizon
        // draws the raider's eye.
        List<SloopEntity> sloops = new ArrayList<>();
        for (Entity candidate : world.getOtherEntities(this, this.getBoundingBox().expand(CHASE_RANGE),
                entity -> entity instanceof SloopEntity sloop && sloop.isAlive()
                        && !sloop.getPassengerList().isEmpty())) {
            sloops.add((SloopEntity) candidate);
        }
        if (!sloops.isEmpty()) {
            this.targetUuid = sloops.get(world.random.nextInt(sloops.size())).getUuid();
        }
    }

    private void hunt(ServerWorld world, MerchantShipEntity target) {
        double dx = target.getX() - this.getX();
        double dz = target.getZ() - this.getZ();
        double distance = Math.sqrt(dx * dx + dz * dz);

        if (!this.plundered && target.cargoCrates() == 0) {
            // Nothing left worth taking; break off the chase.
            this.plundered = true;
            setCustomName(Text.literal("Freebooter Raider · NOTHING WORTH TAKING"));
            setCustomNameVisible(true);
            return;
        }
        boolean mercy = target.hullFraction() < 0.45;
        double desiredRange = this.plundered ? 40.0 : (mercy ? 12.0 : 10.0);

        if (distance > desiredRange) {
            this.sailToward(target.getPos(), this.plundered ? this.huntSpeed() * 0.7 : this.huntSpeed());
        } else {
            this.idleSails();
        }

        if (this.plundered || distance > cannonRange() || this.cannonCooldown > 0 || mercy) {
            if (!this.plundered && !mercy && distance <= 4.5 && target.cargoCrates() > 0) {
                // Grappling range: steal the cargo directly.
                target.raidCargo(world);
                this.plundered = true;
                setCustomName(Text.literal("Freebooter Raider · LOADED WITH PLUNDER"));
                setCustomNameVisible(true);
                world.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.ENTITY_VILLAGER_CELEBRATE,
                        SoundCategory.NEUTRAL, 1.0f, 0.8f);
            }
            return;
        }

        this.fireBroadside(world, target);
    }

    /** Player-sailor duel: run the sloop down and bombard it like any prize. */
    private void huntShip(ServerWorld world, SailingShipEntity prey) {
        double dx = prey.getX() - this.getX();
        double dz = prey.getZ() - this.getZ();
        double distance = Math.sqrt(dx * dx + dz * dz);
        boolean mercy = prey.hullFraction() < 0.30;
        if (distance > (mercy ? 26.0 : 11.0)) {
            this.sailToward(prey.getPos(), mercy ? this.huntSpeed() * 0.7 : this.huntSpeed());
        } else {
            this.idleSails();
        }
        if (distance > cannonRange() || this.cannonCooldown > 0 || mercy) {
            return;
        }
        this.fireBroadside(world, prey);
    }

    private void fireBroadside(ServerWorld world, Entity target) {
        this.cannonCooldown = cannonCooldownTicks();
        Vec3d muzzle = this.getPos().add(0.0, 1.6, 0.0);

        // Lead the moving target so the duel feels gunnery-real, not hitscan-cheap.
        double flightTicks = Math.sqrt(target.squaredDistanceTo(this)) / 0.75;
        Vec3d aim = target.getPos().add(0.0, 0.8, 0.0)
                .add(target.getVelocity().multiply(flightTicks))
                .subtract(muzzle);
        double reach = Math.sqrt(aim.x * aim.x + aim.z * aim.z);
        if (reach < 0.5) {
            reach = 0.5;
        }

        CannonballEntity ball = new CannonballEntity(ModEntities.CANNONBALL, this.getWorld(), this);
        ball.refreshPositionAndAngles(muzzle.x, muzzle.y, muzzle.z, this.getYaw(), 0.0f);
        Vec3d flat = new Vec3d(aim.x / reach, 0.0, aim.z / reach).multiply(0.78);
        double arc = Math.min(0.5, 0.10 + reach * 0.022);
        ball.setVelocity(flat.x, arc, flat.z);
        world.spawnEntity(ball);

        world.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.ENTITY_WITHER_SHOOT,
                SoundCategory.NEUTRAL, 1.1f, 0.75f);
        world.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.ENTITY_GENERIC_EXPLODE,
                SoundCategory.NEUTRAL, 0.45f, 1.4f);
        world.spawnParticles(ParticleTypes.LARGE_SMOKE,
                muzzle.x, muzzle.y, muzzle.z, 10, 0.25, 0.1, 0.25, 0.02);
        world.spawnParticles(ParticleTypes.FLAME,
                muzzle.x, muzzle.y, muzzle.z, 6, 0.2, 0.05, 0.2, 0.05);
    }

    @Override
    protected void onPlayerBoard(PlayerEntity player) {
        // Boarding action: the crew abandons ship duties and repels the intruder.
        for (Entity passenger : List.copyOf(this.getPassengerList())) {
            if (passenger instanceof SurvivorEntity pirate && pirate.effectiveFaction().equals("Freebooters")) {
                pirate.stopRiding();
                pirate.setTarget(player);
            }
        }
    }

    @Override
    protected void onDamaged(DamageSource source, float amount) {
        if (source.getAttacker() instanceof SurvivorEntity survivor
                && "Freebooters".equals(survivor.effectiveFaction())) {
            return; // friendly fire from its own ejected crew is ignored
        }
        if (source.getAttacker() instanceof LivingEntity attacker) {
            this.threatResponse(attacker);
        }
    }

    private void threatResponse(LivingEntity attacker) {
        // Crew already off the ship converge on the attacker.
        for (Entity passenger : List.copyOf(this.getPassengerList())) {
            if (passenger instanceof SurvivorEntity pirate) {
                pirate.setTarget(attacker);
            }
        }
    }

    @Override
    protected List<ItemStack> wreckLoot() {
        List<ItemStack> loot = new ArrayList<>();
        loot.add(new ItemStack(ModItems.ROYAL_COIN, 2 + this.getWorld().random.nextInt(3)));
        loot.add(new ItemStack(Items.GOLD_NUGGET, 4 + this.getWorld().random.nextInt(5)));
        if (this.plundered) {
            loot.add(new ItemStack(ModItems.ROYAL_JEWELRY, 2 + this.getWorld().random.nextInt(3)));
        }
        if (this.getWorld().random.nextFloat() < 0.35f) {
            loot.add(new ItemStack(ModItems.FLINTLOCK, 1));
        }
        loot.add(planks(4));
        return loot;
    }

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        if (nbt.containsUuid("TargetShip")) {
            this.targetUuid = nbt.getUuid("TargetShip");
        }
        this.plundered = nbt.getBoolean("Plundered");
        this.cannonCooldown = nbt.getInt("CannonCooldown");
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        if (this.targetUuid != null) {
            nbt.putUuid("TargetShip", this.targetUuid);
        }
        nbt.putBoolean("Plundered", this.plundered);
        nbt.putInt("CannonCooldown", this.cannonCooldown);
    }

    @Override
    public ItemStack getPickBlockStack() {
        return new ItemStack(ModItems.PIRATE_SHIP_SPAWN_EGG);
    }
}
