package com.spiderman.mod.entity;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.SpiderEntity;
import net.minecraft.world.World;

/**
 * The rare radioactive spider. Its bite can grant the player spider powers
 * (see {@link BiteGoal}).
 */
public class RadioactiveSpiderEntity extends SpiderEntity {
    public RadioactiveSpiderEntity(EntityType<? extends SpiderEntity> type, World world) {
        super(type, world);
    }

    @Override
    public void initGoals() {
        super.initGoals();
        this.goalSelector.add(1, new BiteGoal(this));
    }
}
