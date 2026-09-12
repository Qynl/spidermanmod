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
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

import java.util.List;
import java.util.UUID;

import com.spiderman.mod.ModEntities;
import com.spiderman.mod.ModSounds;
import com.spiderman.mod.server.ComboTracker;
import com.spiderman.mod.server.WebCleanup;

/**
 * Improved web shot — faster, no gravity, white trail, places webs on hit.
 * Heavy (impact) variant is even faster, explosive, with cluster webs.
 */
public class WebShotEntity extends Entity {
    private static final double SPEED = 2.8; // Was 1.7 — now snappy
    private static final double HEAVY_SPEED = 4.0; // Was 2.4 — heavy feels powerful
    private static final int MAX_AGE = 80;
    private static final int HEAVY_MAX_AGE = 100;

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
        if (dir.lengthSquared() < 0.001) dir = new Vec3d(0, 0, 1);
        dir = dir.normalize();
        shot.setPos(origin.x, origin.y, origin.z);
        shot.setVelocity(dir.x * speed, dir.y * speed, dir.z * speed);
        shot.owner = shooter.getUuid();
        shot.damage = damage;
        shot.heavy = heavy;
        world.spawnEntity(shot);
        world.playSound(null, eye.x, eye.y, eye.z, ModSounds.WEB_SHOT,
                SoundCategory.PLAYERS, 0.9f, heavy ? 0.6f : 1.2f);
        return shot;
    }

    @Override
    public void initDataTracker(DataTracker.Builder builder) {}

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        try {
            if (nbt.contains("Owner")) owner = nbt.getUuid("Owner");
            damage = nbt.getFloat("Damage");
            heavy = nbt.getBoolean("Heavy");
            life = nbt.getInt("Age");
        } catch (RuntimeException e) {
            owner = null;
        }
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        if (owner != null) nbt.putUuid("Owner", owner);
        nbt.putFloat("Damage", damage);
        nbt.putBoolean("Heavy", heavy);
        nbt.putInt("Age", life);
    }

    @Override
    public void tick() {
        super.tick();
        World world = getWorld();
        if (world.isClient) return;

        life++;
        int max = heavy ? HEAVY_MAX_AGE : MAX_AGE;
        if (life > max) {
            burst(world, getX(), getY(), getZ());
            discard();
            return;
        }

        Vec3d vel = getVelocity();
        if (vel == null) vel = new Vec3d(0, 0, 0);
        // No gravity for both now — straight white line feel
        // Slight drag for normal shot to feel organic, none for heavy
        if (!heavy) {
            vel = vel.multiply(0.995);
        }
        setVelocity(vel);

        // Trail particles — white cobweb trail
        if (world instanceof ServerWorld sw) {
            try {
                if (life % 2 == 0) {
                    sw.spawnParticles(ParticleTypes.ITEM_COBWEB, getX(), getY(), getZ(), 2, 0.05, 0.05, 0.05, 0.01);
                }
                if (heavy && life % 3 == 0) {
                    sw.spawnParticles(ParticleTypes.CRIT, getX(), getY(), getZ(), 1, 0.1, 0.1, 0.1, 0.1);
                }
            } catch (Exception ignored) {}
        }

        Vec3d pos = getPos();
        Vec3d next = pos.add(vel);
        BlockHitResult blockHit = null;
        try {
            blockHit = world.raycast(new RaycastContext(pos, next,
                    RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, this));
        } catch (Exception ignored) {}
        boolean blocked = blockHit != null && blockHit.getType() != HitResult.Type.MISS;
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
        try {
            Box box = new Box(Math.min(from.x, to.x) - 0.5, Math.min(from.y, to.y) - 0.5,
                    Math.min(from.z, to.z) - 0.5, Math.max(from.x, to.x) + 0.5,
                    Math.max(from.y, to.y) + 0.5, Math.max(from.z, to.z) + 0.5);
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
        } catch (Exception e) {
            return null;
        }
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
        try {
            target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, heavy ? 140 : 80, heavy ? 4 : 2));
            if (heavy) {
                target.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 80, 1));
                target.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, 40, 0));
            }
        } catch (Exception ignored) {}

        if (heavy) {
            Vec3d vel = getVelocity();
            if (vel != null && vel.lengthSquared() > 0.001) {
                vel = vel.normalize();
                target.addVelocity(vel.x * 1.8, 0.7, vel.z * 1.8);
            }
        }

        // Place webs on entity hit — cluster for heavy
        if (shooter != null) {
            try {
                BlockPos center = target.getBlockPos();
                if (heavy) {
                    for (int i = 0; i < 8; i++) {
                        int dx = (int) (Math.random() * 3) - 1;
                        int dy = (int) (Math.random() * 3) - 1;
                        int dz = (int) (Math.random() * 3) - 1;
                        WebCleanup.place(shooter, center.add(dx, dy, dz));
                    }
                } else {
                    WebCleanup.place(shooter, center);
                }
            } catch (Exception ignored) {}
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
        ServerPlayerEntity shooter = findShooter();

        // Place webs on block hit — more for heavy
        if (shooter != null) {
            try {
                BlockPos base = hit.getBlockPos().offset(hit.getSide());
                if (heavy) {
                    for (int i = 0; i < 6; i++) {
                        int dx = (int) (Math.random() * 3) - 1;
                        int dy = (int) (Math.random() * 3) - 1;
                        int dz = (int) (Math.random() * 3) - 1;
                        WebCleanup.place(shooter, base.add(dx, dy, dz));
                    }
                } else {
                    WebCleanup.place(shooter, base);
                }
            } catch (Exception ignored) {}
        }

        burst(world, pos.x, pos.y, pos.z);
        discard();
    }

    private void burst(World world, double x, double y, double z) {
        if (world instanceof ServerWorld serverWorld) {
            try {
                serverWorld.spawnParticles(ParticleTypes.ITEM_COBWEB, x, y, z, heavy ? 30 : 18, 0.4, 0.4, 0.4, 0.1);
                if (heavy) {
                    serverWorld.spawnParticles(ParticleTypes.EXPLOSION, x, y, z, 1, 0.1, 0.1, 0.1, 0.0);
                    serverWorld.spawnParticles(ParticleTypes.CRIT, x, y, z, 12, 0.4, 0.4, 0.4, 0.2);
                }
            } catch (Exception ignored) {}
        }
        try {
            world.playSound(null, x, y, z, ModSounds.WEB_SPLAT, SoundCategory.PLAYERS, 1.0f, heavy ? 0.5f : 1.1f);
            if (heavy) {
                world.playSound(null, x, y, z, net.minecraft.sound.SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.PLAYERS, 0.6f, 1.2f);
            }
        } catch (Exception ignored) {}
    }

    private ServerPlayerEntity findShooter() {
        if (owner == null) return null;
        try {
            PlayerEntity player = getWorld().getPlayerByUuid(owner);
            return player instanceof ServerPlayerEntity serverPlayer ? serverPlayer : null;
        } catch (Exception e) {
            return null;
        }
    }
}
