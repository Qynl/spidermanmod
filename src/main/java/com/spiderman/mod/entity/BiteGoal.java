package com.spiderman.mod.entity;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.server.network.ServerPlayerEntity;

import com.spiderman.mod.config.SpiderConfig;
import com.spiderman.mod.server.TransformLogic;

/**
 * Melee attack that can infect the player with spider powers on a damaging
 * bite. Runs server-side only.
 */
public class BiteGoal extends MeleeAttackGoal {
    private final PathAwareEntity mob;

    public BiteGoal(PathAwareEntity mob) {
        super(mob, 1.0, true);
        this.mob = mob;
    }

    @Override
    public void attack(LivingEntity target) {
        super.attack(target);
        if (!(target instanceof ServerPlayerEntity player)) {
            return;
        }
        if (player.getWorld().isClient) {
            return;
        }
        if (Math.random() > SpiderConfig.get().biteChance) {
            return;
        }
        if (TransformLogic.grantBite(player) && SpiderConfig.get().spiderDiesAfterBite) {
            mob.kill();
        }
    }
}
