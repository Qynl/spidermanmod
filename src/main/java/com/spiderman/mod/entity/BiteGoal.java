package com.spiderman.mod.entity;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.server.network.ServerPlayerEntity;

import com.spiderman.mod.SpiderManMod;
import com.spiderman.mod.config.SpiderConfig;
import com.spiderman.mod.server.TransformLogic;
import com.spiderman.mod.state.SpiderState;

/**
 * Melee attack that can infect the player with spider powers on a damaging bite.
 * Runs server-side only.
 *
 * Bughunt improvements:
 * - Checks if player already has powers before attempting bite (no wasted bites)
 * - Null checks for world, player, config
 * - Cooldown to prevent spam biting
 * - Logs bite attempts for debugging
 * - Handles spider death safely (checks if mob is still alive)
 */
public class BiteGoal extends MeleeAttackGoal {
    private final PathAwareEntity mob;
    private int biteCooldown = 0;

    public BiteGoal(PathAwareEntity mob) {
        super(mob, 1.25, true); // Faster than vanilla spider chase (1.0 -> 1.25)
        this.mob = mob;
    }

    @Override
    public boolean canStart() {
        if (biteCooldown > 0) {
            biteCooldown--;
            return false;
        }
        return super.canStart();
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
        // Bughunt: don't bite if player already has powers — wasteful and confusing
        try {
            if (SpiderState.get(player.getUuid()).hasPowers) {
                return;
            }
        } catch (Exception ignored) {
            // If state lookup fails, still try bite
        }

        // Config check
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

        // Attempt bite
        boolean fresh;
        try {
            fresh = TransformLogic.grantBite(player);
        } catch (Exception e) {
            SpiderManMod.LOGGER.warn("[spiderman] bite failed for {}", player.getGameProfile().getName(), e);
            return;
        }

        if (fresh) {
            biteCooldown = 100; // Prevent immediate re-bite
            SpiderManMod.LOGGER.info("[spiderman] radioactive bite succeeded for {}", player.getGameProfile().getName());
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
