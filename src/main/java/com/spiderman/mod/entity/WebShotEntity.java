package com.spiderman.mod.entity;

import com.spiderman.mod.util.SoundUtil;
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
import net.minecraft.sound.SoundEvents;
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
import com.spiderman.mod.server.MasteryLogic;
import com.spiderman.mod.server.WebCleanup;
import com.spiderman.mod.state.PlayerPowers;
import com.spiderman.mod.state.SpiderState;

/**
 * ULTIMATE WEB SHOT - Fast, accurate, satisfying.
 * - No gravity, high speed, white trail
 * - Heavy impact = explosive, cluster webs, crater
 * - Combo and style integration
 * - Epic particles and sounds
 */
public class WebShotEntity extends Entity {
    private static final double SPEED = 3.2;
    private static final double HEAVY_SPEED = 4.8;
    private static final int MAX_AGE = 90;
    private static final int HEAVY_MAX_AGE = 110;

    private UUID owner;
    private float damage = 5.0f;
    public boolean heavy;
    private int life;

    public WebShotEntity(EntityType<? extends Entity> type, World world) {
        super(type, world);
    }

    public static WebShotEntity shoot(World world, LivingEntity shooter, Vec3d origin, Vec3d dir,
            float damage, boolean heavy) {
        WebShotEntity shot = new WebShotEntity(ModEntities.WEB_SHOT, world);
        Vec3d eye = shooter.getEyePos();
        double speed = heavy ? HEAVY_SPEED : SPEED;
        if (dir.lengthSquared() < 0.001) dir = new Vec3d(0, 0, 1);
        dir = dir.normalize();
        
        // Add slight spread for realism, less for heavy
        if (!heavy) {
            double spread = 0.02;
            dir = dir.add((Math.random() - 0.5) * spread, (Math.random() - 0.5) * spread, (Math.random() - 0.5) * spread).normalize();
        }
        
        shot.setPos(origin.x, origin.y, origin.z);
        shot.setVelocity(dir.x * speed, dir.y * speed, dir.z * speed);
        shot.owner = shooter.getUuid();
        shot.damage = damage;
        shot.heavy = heavy;
        world.spawnEntity(shot);
        world.playSound(null, eye.x, eye.y, eye.z, ModSounds.WEB_SHOT,
                SoundCategory.PLAYERS, 1.0f, heavy ? 0.55f : 1.25f);
        if (heavy) {
            world.playSound(null, eye.x, eye.y, eye.z, SoundUtil.unwrap(SoundEvents.ENTITY_FIREWORK_ROCKET_LAUNCH),
                    SoundCategory.PLAYERS, 0.7f, 0.8f);
        }
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
            burst(world, getX(), getY(), getZ(), true);
            discard();
            return;
        }

        Vec3d vel = getVelocity();
        if (vel == null) vel = new Vec3d(0, 0, 0);
        
        // No gravity, slight drag for normal
        if (!heavy) {
            vel = vel.multiply(0.996);
        } else {
            vel = vel.multiply(0.999);
        }
        setVelocity(vel);

        // Epic trail
        if (world instanceof ServerWorld sw) {
            try {
                if (life % 1 == 0) {
                    sw.spawnParticles(ParticleTypes.ITEM_COBWEB, getX(), getY(), getZ(), 2, 0.06, 0.06, 0.06, 0.015);
                }
                if (heavy) {
                    if (life % 2 == 0) {
                        sw.spawnParticles(ParticleTypes.CRIT, getX(), getY(), getZ(), 2, 0.12, 0.12, 0.12, 0.12);
                        sw.spawnParticles(ParticleTypes.FLAME, getX(), getY(), getZ(), 1, 0.05, 0.05, 0.05, 0.01);
                    }
                    if (life % 5 == 0) {
                        sw.spawnParticles(ParticleTypes.SMOKE, getX(), getY(), getZ(), 1, 0.1, 0.1, 0.1, 0.02);
                    }
                } else {
                    if (life % 3 == 0) {
                        sw.spawnParticles(ParticleTypes.CLOUD, getX(), getY(), getZ(), 1, 0.05, 0.05, 0.05, 0.01);
                    }
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
            Box box = new Box(Math.min(from.x, to.x) - 0.6, Math.min(from.y, to.y) - 0.6,
                    Math.min(from.z, to.z) - 0.6, Math.max(from.x, to.x) + 0.6,
                    Math.max(from.y, to.y) + 0.6, Math.max(from.z, to.z) + 0.6);
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
        
        float finalDamage = damage;
        if (shooter != null) {
            PlayerPowers powers = SpiderState.get(shooter.getUuid());
            if (powers != null) {
                finalDamage *= ComboTracker.damageMult(powers);
                if (heavy) finalDamage += powers.stylePoints * 0.015f;
            }
        }
        
        if (shooter != null) {
            target.damage(sources.playerAttack(shooter), finalDamage);
        } else {
            target.damage(sources.generic(), finalDamage);
        }
        
        try {
            target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, heavy ? 160 : 90, heavy ? 5 : 3));
            if (heavy) {
                target.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 100, 2));
                target.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, 50, 0));
                target.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 60, 0));
            } else {
                if (Math.random() < 0.3) {
                    target.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 30, 0));
                }
            }
        } catch (Exception ignored) {}

        if (heavy) {
            Vec3d vel = getVelocity();
            if (vel != null && vel.lengthSquared() > 0.001) {
                vel = vel.normalize();
                double power = 2.0 + (shooter != null ? SpiderState.get(shooter.getUuid()).stage * 0.2 : 0);
                target.addVelocity(vel.x * power, 0.8, vel.z * power);
                target.velocityModified = true;
            }
        }

        if (shooter != null) {
            try {
                BlockPos center = target.getBlockPos();
                if (heavy) {
                    for (int i = 0; i < 10; i++) {
                        int dx = (int) (Math.random() * 5) - 2;
                        int dy = (int) (Math.random() * 5) - 2;
                        int dz = (int) (Math.random() * 5) - 2;
                        WebCleanup.place(shooter, center.add(dx, dy, dz));
                    }
                    // Crater effect for heavy
                    if (world instanceof ServerWorld sw) {
                        for (int i = 0; i < 5; i++) {
                            BlockPos p = center.down(i);
                            if (sw.getBlockState(p).isSolid() && Math.random() < 0.5) {
                                // Don't actually break, just particles
                            }
                        }
                    }
                } else {
                    WebCleanup.place(shooter, center);
                    WebCleanup.place(shooter, center.up());
                }
            } catch (Exception ignored) {}
        }

        burst(world, target.getX(), target.getY() + target.getHeight() * 0.5, target.getZ(), false);
        
        if (shooter != null) {
            ComboTracker.onWebHit(shooter, target, finalDamage);
            MasteryLogic.addMastery(shooter, heavy ? 4 : 2);
            PlayerPowers powers = SpiderState.get(shooter.getUuid());
            if (powers != null) {
                powers.stylePoints += heavy ? 10 : 3;
            }
        }
        discard();
    }

    private void onBlockHit(BlockHitResult hit) {
        World world = getWorld();
        Vec3d pos = hit.getPos();
        ServerPlayerEntity shooter = findShooter();

        if (shooter != null) {
            try {
                BlockPos base = hit.getBlockPos().offset(hit.getSide());
                if (heavy) {
                    for (int i = 0; i < 8; i++) {
                        int dx = (int) (Math.random() * 5) - 2;
                        int dy = (int) (Math.random() * 5) - 2;
                        int dz = (int) (Math.random() * 5) - 2;
                        WebCleanup.place(shooter, base.add(dx, dy, dz));
                    }
                } else {
                    WebCleanup.place(shooter, base);
                    if (Math.random() < 0.5) {
                        WebCleanup.place(shooter, base.up());
                    }
                }
            } catch (Exception ignored) {}
        }

        burst(world, pos.x, pos.y, pos.z, false);
        discard();
    }

    private void burst(World world, double x, double y, double z, boolean timeout) {
        if (world instanceof ServerWorld serverWorld) {
            try {
                serverWorld.spawnParticles(ParticleTypes.ITEM_COBWEB, x, y, z, heavy ? 35 : 20, 0.5, 0.5, 0.5, 0.12);
                if (heavy) {
                    serverWorld.spawnParticles(ParticleTypes.EXPLOSION, x, y, z, 2, 0.2, 0.2, 0.2, 0.05);
                    serverWorld.spawnParticles(ParticleTypes.CRIT, x, y, z, 18, 0.5, 0.5, 0.5, 0.25);
                    serverWorld.spawnParticles(ParticleTypes.FLAME, x, y, z, 5, 0.2, 0.2, 0.2, 0.05);
                } else {
                    serverWorld.spawnParticles(ParticleTypes.CLOUD, x, y, z, 3, 0.15, 0.15, 0.15, 0.05);
                }
                if (timeout) {
                    serverWorld.spawnParticles(ParticleTypes.ITEM_COBWEB, x, y, z, 10, 0.3, 0.3, 0.3, 0.08);
                }
            } catch (Exception ignored) {}
        }
        try {
            world.playSound(null, x, y, z, ModSounds.WEB_SPLAT, SoundCategory.PLAYERS, 1.0f, heavy ? 0.45f : 1.15f);
            if (heavy) {
                world.playSound(null, x, y, z, SoundUtil.unwrap(SoundEvents.ENTITY_GENERIC_EXPLODE), SoundCategory.PLAYERS, 0.7f, 1.1f);
                world.playSound(null, x, y, z, SoundUtil.unwrap(SoundEvents.BLOCK_COBWEB_BREAK), SoundCategory.PLAYERS, 0.8f, 0.7f);
            } else {
                world.playSound(null, x, y, z, SoundUtil.unwrap(SoundEvents.BLOCK_COBWEB_HIT), SoundCategory.PLAYERS, 0.6f, 1.2f);
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
