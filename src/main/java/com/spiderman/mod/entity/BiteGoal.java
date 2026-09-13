package com.spiderman.mod.entity;

import com.spiderman.mod.util.SoundUtil;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;

import com.spiderman.mod.SpiderManMod;
import com.spiderman.mod.config.SpiderConfig;
import com.spiderman.mod.server.TransformLogic;
import com.spiderman.mod.state.SpiderState;

/**
 * ULTIMATE BITE GOAL - Epic, rare, unforgettable.
 * - Radioactive spider seeks player with purpose
 * - Epic bite effects
 * - Style and mastery
 */
public class BiteGoal extends MeleeAttackGoal {
    private final PathAwareEntity mob;
    private int biteCooldown = 0;
    private int stalkTicks = 0;

    public BiteGoal(PathAwareEntity mob) {
        super(mob, 1.4, true); // Even faster - radioactive urgency
        this.mob = mob;
    }

    @Override
    public boolean canStart() {
        if (biteCooldown > 0) {
            biteCooldown--;
            return false;
        }
        // Only target players without powers
        if (mob.getTarget() instanceof ServerPlayerEntity player) {
            try {
                if (SpiderState.get(player.getUuid()).hasPowers) {
                    return false;
                }
            } catch (Exception ignored) {}
        }
        return super.canStart();
    }

    @Override
    public void tick() {
        super.tick();
        // Stalking behavior - epic
        if (mob.getTarget() != null) {
            stalkTicks++;
            if (stalkTicks % 20 == 0 && mob.getWorld() instanceof ServerWorld sw) {
                try {
                    sw.spawnParticles(ParticleTypes.ELECTRIC_SPARK, mob.getX(), mob.getY() + 0.5, mob.getZ(), 2, 0.2, 0.2, 0.2, 0.02);
                } catch (Exception ignored) {}
            }
        } else {
            stalkTicks = 0;
        }
    }

    @Override
    public void attack(LivingEntity target) {
        super.attack(target);
        if (target == null || mob == null) return;
        if (!(target instanceof ServerPlayerEntity player)) {
            return;
        }
        if (player.getWorld() == null || player.getWorld().isClient) {
            return;
        }
        if (!player.isAlive() || player.isSpectator()) {
            return;
        }
        try {
            if (SpiderState.get(player.getUuid()).hasPowers) {
                return;
            }
        } catch (Exception ignored) {}

        double chance;
        try {
            chance = SpiderConfig.get().biteChance;
        } catch (Exception e) {
            chance = 1.0;
        }
        if (!Double.isFinite(chance)) chance = 1.0;
        if (Math.random() > chance) {
            return;
        }

        boolean fresh;
        try {
            fresh = TransformLogic.grantBite(player);
        } catch (Exception e) {
            SpiderManMod.LOGGER.warn("[spiderman] bite failed for {}", player.getGameProfile().getName(), e);
            return;
        }

        if (fresh) {
            biteCooldown = 120;
            SpiderManMod.LOGGER.info("[spiderman] ULTIMATE bite for {}", player.getGameProfile().getName());
            
            // Epic bite effects
            try {
                if (player.getWorld() instanceof ServerWorld sw) {
                    sw.spawnParticles(ParticleTypes.EXPLOSION, mob.getX(), mob.getY() + 0.5, mob.getZ(), 1, 0.1, 0.1, 0.1, 0.0);
                    sw.spawnParticles(ParticleTypes.ITEM_COBWEB, player.getX(), player.getY() + 1, player.getZ(), 30, 0.5, 0.5, 0.5, 0.2);
                    sw.spawnParticles(ParticleTypes.ELECTRIC_SPARK, player.getX(), player.getY() + 1, player.getZ(), 20, 0.4, 0.4, 0.4, 0.1);
                    sw.spawnParticles(ParticleTypes.GLOW, player.getX(), player.getY() + 1, player.getZ(), 10, 0.3, 0.3, 0.3, 0.05);
                }
                player.playSound(SoundUtil.unwrap(SoundEvents.ENTITY_SPIDER_HURT), 1.0f, 0.4f);
                player.playSound(SoundUtil.unwrap(SoundEvents.ENTITY_GENERIC_EXPLODE), 0.6f, 1.8f);
                mob.playSound(SoundUtil.unwrap(SoundEvents.ENTITY_SPIDER_DEATH), 1.0f, 1.5f);
            } catch (Exception ignored) {}
            
            boolean diesAfter;
            try {
                diesAfter = SpiderConfig.get().spiderDiesAfterBite;
            } catch (Exception e) {
                diesAfter = true;
            }
            if (diesAfter) {
                try {
                    if (mob.isAlive()) {
                        mob.kill();
                    }
                } catch (Exception ignored) {}
            }
        }
    }
}
