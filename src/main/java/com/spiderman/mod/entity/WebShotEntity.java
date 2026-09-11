package com.spiderman.mod.entity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSources;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

import java.util.List;
import java.util.UUID;

import com.spiderman.mod.ModEntities;
import com.spiderman.mod.ModSounds;
import com.spiderman.mod.server.ComboTracker;

/**
 * A glob of organic webbing. Flies straight with slight gravity, splats on
 * blocks, and webs + damages living targets. The heavy variant (impact web)
 * flies faster, hits harder and knocks back.
 */
public class WebShotEntity extends Entity {
    private static final double SPEED = 1.7;
    private static final double HEAVY_SPEED = 2.4;
    private static final int MAX_AGE = 120;

    private UUID owner;
    private float damage = 4.0f;
    private boolean heavy;
    private int life;

    public WebShotEntity(EntityType<? extends Entity> type, World world) {
        super(type, world);
    }

    /** Fires a web glob from the given wrist origin along the given direction. */
    public static WebShotEntity shoot(World world, LivingEntity shooter, Vec3d origin, Vec3d dir,
            float damage, boolean heavy) {
        WebShotEntity shot = new WebShotEntity(ModEntities.WEB_SHOT, world);
        Vec3d eye = shooter.getEyePos();
        double speed = heavy ? HEAVY_SPEED : SPEED;
        shot.setPos(origin.x, origin.y, origin.z);
        shot.setVelocity(dir.x * speed, dir.y * speed, dir.z * speed);
        shot.owner = shooter.getUuid();
        shot.damage = damage;
        shot.heavy = heavy;
        world.spawnEntity(shot);
        world.playSound(null, eye.x, eye.y, eye.z, ModSounds.WEB_SHOT,
                SoundCategory.PLAYERS, 0.9f, heavy ? 0.7f : 1.1f);
        return shot;
    }

    @Override
    public void initDataTracker(DataTracker.Builder builder) {
        // No tracked data: position syncs via vanilla entity tracking.
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        if (nbt.contains("Owner")) {
            owner = nbt.getUuid("Owner");
        }
        damage = nbt.getFloat("Damage");
        heavy = nbt.getBoolean("Heavy");
        life = nbt.getInt("Age");
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        if (owner != null) {
            nbt.putUuid("Owner", owner);
        }
        nbt.putFloat("Damage", damage);
        nbt.putBoolean("Heavy", heavy);
        nbt.putInt("Age", life);
    }

    @Override
    public void tick() {
        super.tick();
        World world = getWorld();
        if (world.isClient) {
            return;
        }
        life++;
        if (life > MAX_AGE) {
            discard();
            return;
        }
        Vec3d vel = getVelocity();
        if (!heavy) {
            vel = vel.multiply(0.99);
            vel = vel.add(0.0, -0.02, 0.0);
        }
        setVelocity(vel);

        Vec3d pos = getPos();
        Vec3d next = pos.add(vel);
        BlockHitResult blockHit = world.raycast(new RaycastContext(pos, next,
                RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, this));
        boolean blocked = blockHit.getType() != HitResult.Type.MISS;
        Vec3d target = blocked ? blockHit.getPos() : next;

        LivingEntity victim = findVictim(pos, target);
        setPos(target.x, target.y, target.z);
        if (victim != null) {
            onEntityHit(victim);
        } else if (blocked) {
            onBlockHit(blockHit);
        }
    }

    private LivingEntity findVictim(Vec3d from, Vec3d to) {
        Box box = new Box(Math.min(from.x, to.x) - 0.4, Math.min(from.y, to.y) - 0.4,
                Math.min(from.z, to.z) - 0.4, Math.max(from.x, to.x) + 0.4,
                Math.max(from.y, to.y) + 0.4, Math.max(from.z, to.z) + 0.4);
        List<Entity> found = getWorld().getOtherEntities(this, box,
                e -> e instanceof LivingEntity && e.isAlive() && !isOwner(e));
        LivingEntity best = null;
        double bestDist = Double.MAX_VALUE;
        for (Entity e : found) {
            double d = e.squaredDistanceTo(from);
            if (d < bestDist) {
                bestDist = d;
                best = (LivingEntity) e;
            }
        }
        return best;
    }

    private boolean isOwner(Entity e) {
        return owner != null && owner.equals(e.getUuid());
    }

    private void onEntityHit(LivingEntity target) {
        World world = getWorld();
        DamageSources sources = world.getDamageSources();
        ServerPlayerEntity shooter = findShooter();
        if (shooter != null) {
            target.damage(sources.playerAttack(shooter), damage);
        } else {
            target.damage(sources.generic(), damage);
        }
        target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS,
                heavy ? 100 : 60, heavy ? 3 : 2));
        if (heavy) {
            Vec3d vel = getVelocity().normalize();
            target.addVelocity(vel.x * 1.2, 0.5, vel.z * 1.2);
        }
        burst(world, target.getX(), target.getY() + target.getHeight() * 0.5, target.getZ());
        if (shooter != null) {
            ComboTracker.onWebHit(shooter, target, damage);
        }
        discard();
    }

    private void onBlockHit(BlockHitResult hit) {
        World world = getWorld();
        Vec3d pos = hit.getPos();
        burst(world, pos.x, pos.y, pos.z);
        discard();
    }

    private void burst(World world, double x, double y, double z) {
        if (world instanceof ServerWorld serverWorld) {
            serverWorld.spawnParticles(ParticleTypes.ITEM_COBWEB, x, y, z, 14, 0.3, 0.3, 0.3, 0.08);
        }
        world.playSound(null, x, y, z, ModSounds.WEB_SPLAT,
                SoundCategory.PLAYERS, 0.8f, heavy ? 0.7f : 1.0f);
    }

    private ServerPlayerEntity findShooter() {
        if (owner == null) {
            return null;
        }
        PlayerEntity player = getWorld().getPlayerByUuid(owner);
        return player instanceof ServerPlayerEntity serverPlayer ? serverPlayer : null;
    }
}
