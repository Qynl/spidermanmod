package com.spiderman.mod.entity;

import com.spiderman.mod.util.SoundUtil;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.SpiderEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * ULTIMATE RADIOACTIVE SPIDER - Rare, epic, unmistakable.
 * - Custom model (not vanilla)
 * - Glows, radioactive particles, faster, jumps higher
 * - Grants powers with style
 * - Rare spawn, but unmissable when it appears
 */
public class RadioactiveSpiderEntity extends SpiderEntity {
    private int glowCooldown = 0;
    private int particleCooldown = 0;
    private int jumpCooldown = 0;

    public RadioactiveSpiderEntity(EntityType<? extends SpiderEntity> type, World world) {
        super(type, world);
    }

    @Override
    public void initGoals() {
        super.initGoals();
        this.goalSelector.add(0, new BiteGoal(this));
    }

    @Override
    public void tick() {
        super.tick();
        
        // Epic radioactive effects
        if (getWorld().isClient) {
            // Client particles
            if (age % 3 == 0 && Math.random() < 0.6) {
                try {
                    getWorld().addParticle(ParticleTypes.ELECTRIC_SPARK,
                        getX() + (Math.random() - 0.5) * 0.8,
                        getY() + 0.5 + Math.random() * 0.5,
                        getZ() + (Math.random() - 0.5) * 0.8,
                        0, 0.05, 0);
                } catch (Exception ignored) {}
            }
            return;
        }

        glowCooldown--;
        if (glowCooldown <= 0) {
            glowCooldown = 80 + (int) (Math.random() * 80);
            try {
                addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 80, 0));
                addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 100, 1));
                addStatusEffect(new StatusEffectInstance(StatusEffects.JUMP_BOOST, 100, 2));
            } catch (Exception ignored) {}
        }

        particleCooldown--;
        if (particleCooldown <= 0) {
            particleCooldown = 10;
            try {
                if (getWorld() instanceof ServerWorld sw) {
                    sw.spawnParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY() + 0.5, getZ(), 3, 0.3, 0.3, 0.3, 0.02);
                    sw.spawnParticles(ParticleTypes.GLOW, getX(), getY() + 0.5, getZ(), 2, 0.2, 0.2, 0.2, 0.02);
                    if (Math.random() < 0.2) {
                        sw.spawnParticles(ParticleTypes.ITEM_COBWEB, getX(), getY() + 0.5, getZ(), 2, 0.2, 0.2, 0.2, 0.05);
                    }
                }
            } catch (Exception ignored) {}
        }

        // Super spider abilities
        if (age % 30 == 0) {
            try {
                if (getHealth() < getMaxHealth() && getHealth() > 0) {
                    heal(1.5f);
                }
                // Remove bad effects - radioactive resilience
                removeStatusEffect(StatusEffects.POISON);
                removeStatusEffect(StatusEffects.WITHER);
            } catch (Exception ignored) {}
        }
        
        // Epic jumps
        jumpCooldown--;
        if (jumpCooldown <= 0 && isOnGround() && getTarget() != null) {
            double dist = squaredDistanceTo(getTarget());
            if (dist > 9 && dist < 144) { // 3-12 blocks
                try {
                    Vec3d toTarget = getTarget().getPos().subtract(getPos()).normalize();
                    setVelocity(toTarget.x * 0.8, 0.7, toTarget.z * 0.8);
                    velocityModified = true;
                    playSound(SoundUtil.unwrap(SoundEvents.ENTITY_SPIDER_HURT), 0.8f, 1.8f);
                    jumpCooldown = 60 + (int)(Math.random() * 40);
                } catch (Exception ignored) {}
            }
        }
        
        // Always climbing
        if (horizontalCollision && !isOnGround()) {
            try {
                setVelocity(getVelocity().x * 0.7, 0.3, getVelocity().z * 0.7);
            } catch (Exception ignored) {}
        }
    }

    @Override
    public boolean isClimbingWall() {
        return true;
    }
    
    @Override
    public boolean isGlowing() {
        // Always slightly glowing - radioactive
        return super.isGlowing() || age % 40 < 20;
    }
    
    @Override
    public void onDeath(net.minecraft.entity.damage.DamageSource source) {
        super.onDeath(source);
        // Epic death effect
        try {
            if (getWorld() instanceof ServerWorld sw) {
                sw.spawnParticles(ParticleTypes.EXPLOSION, getX(), getY() + 0.5, getZ(), 1, 0.1, 0.1, 0.1, 0.0);
                sw.spawnParticles(ParticleTypes.ITEM_COBWEB, getX(), getY() + 0.5, getZ(), 20, 0.5, 0.5, 0.5, 0.15);
                sw.spawnParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY() + 0.5, getZ(), 15, 0.4, 0.4, 0.4, 0.1);
            }
            getWorld().playSound(null, getX(), getY(), getZ(), SoundUtil.unwrap(SoundEvents.ENTITY_SPIDER_DEATH), getSoundCategory(), 1.0f, 0.6f);
        } catch (Exception ignored) {}
    }
}
