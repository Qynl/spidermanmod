package com.rivalrealms.item;

import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

/**
 * A curved ox horn: one deep bellow rallies every ally in earshot with
 * Speed and Strength. Blown at the right moment it turns a losing brawl
 * into a rout. Long cooldown, no durability — purely a commander's tool.
 */
public class WarhornItem extends Item {
    public WarhornItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (!world.isClient && world instanceof ServerWorld serverWorld) {
            serverWorld.playSound(null, user.getX(), user.getY(), user.getZ(),
                    SoundEvents.ENTITY_RAVAGER_ROAR, SoundCategory.PLAYERS, 1.4f, 1.15f);
            serverWorld.spawnParticles(ParticleTypes.NOTE,
                    user.getX(), user.getY() + 2.2, user.getZ(), 8, 0.6, 0.4, 0.6, 1.0);
            int rallied = 0;
            for (var entity : world.getOtherEntities(user, user.getBoundingBox().expand(24.0),
                    e -> e instanceof com.rivalrealms.entity.SurvivorEntity ally && ally.isAlive())) {
                com.rivalrealms.entity.SurvivorEntity ally = (com.rivalrealms.entity.SurvivorEntity) entity;
                if (ally.isOwner(user) || (ally.isRecruited() && ally.getTrust() >= 40)
                        || ally.getTemperament() != com.rivalrealms.entity.Temperament.HOSTILE) {
                    ally.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 400, 1));
                    ally.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, 400, 0));
                    serverWorld.spawnParticles(ParticleTypes.HAPPY_VILLAGER,
                            ally.getX(), ally.getY() + 2.0, ally.getZ(), 4, 0.3, 0.3, 0.3, 0.0);
                    rallied++;
                }
            }
            // The blower feels it too.
            user.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 400, 0));
            if (rallied > 0 && user instanceof ServerPlayerEntity player) {
                player.sendMessage(Text.literal("The horn rings out - " + rallied
                        + " allies answer.").formatted(Formatting.GOLD), true);
            }
            user.getItemCooldownManager().set(this, 600);
        }
        return TypedActionResult.success(stack, world.isClient());
    }
}
