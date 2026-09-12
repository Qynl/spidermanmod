package com.spiderman.mod.entity;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.SpiderEntity;
import net.minecraft.world.World;

/**
 * The rare radioactive spider — now with custom model and distinct behavior.
 * Unlike vanilla spiders, this one is a unique entity with custom model
 * (RadioactiveSpiderModel, not vanilla spider) and special traits.
 */
public class RadioactiveSpiderEntity extends SpiderEntity {
    private int glowCooldown = 0;

    public RadioactiveSpiderEntity(EntityType<? extends SpiderEntity> type, World world) {
        super(type, world);
    }

    @Override
    public void initGoals() {
        super.initGoals();
        // Bite goal is highest priority — this spider's purpose is to grant powers
        this.goalSelector.add(0, new BiteGoal(this));
    }

    @Override
    public void tick() {
        super.tick();
        if (getWorld().isClient) return;

        // Occasional glowing to make it stand out in dark (radioactive)
        glowCooldown--;
        if (glowCooldown <= 0) {
            glowCooldown = 100 + (int) (Math.random() * 100);
            try {
                if (!hasStatusEffect(StatusEffects.GLOWING)) {
                    addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 60, 0));
                }
            } catch (Exception ignored) {}
        }

        // Slightly faster healing than vanilla spider (radioactive regeneration)
        if (age % 40 == 0) {
            try {
                if (getHealth() < getMaxHealth() && getHealth() > 0) {
                    heal(1.0f);
                }
            } catch (Exception ignored) {}
        }
    }

    @Override
    public boolean isClimbingWall() {
        // Always climbing-capable
        return true;
    }
}
